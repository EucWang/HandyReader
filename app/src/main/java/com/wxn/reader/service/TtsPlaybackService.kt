package com.wxn.reader.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wxn.base.bean.Locator
import com.wxn.base.bean.ReaderText
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.SpeakSentence
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.reader.MainActivity
import com.wxn.reader.R
import com.wxn.reader.util.BreakParagraphUtil
import com.wxn.reader.util.tts.TtsNavigator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.cancel
import net.gotev.speech.Speech
import java.io.File
import java.util.Locale
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class TtsPlaybackService : MediaSessionService() {

    private var notification: Notification? = null

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "tts_playback_channel"
        const val NOTIFICATION_ID = 1007

        const val ACTION_PLAY = "com.wxn.reader.action.PLAY_TTS"
        const val ACTION_PAUSE = "com.wxn.reader.action.PAUSE_TTS"

        const val ACTION_RESUME = "com.wxn.reader.action.RESUME_TTS"
        const val ACTION_STOP = "com.wxn.reader.action.STOP_TTS"

        const val ACTION_TO_PREV = "com.wxn.reader.action.SKIP_TO_PREV"

        const val ACTION_TO_NEXT = "com.wxn.reader.action.SKIP_TO_NEXT"

        const val ACTION_SET_SPEAK_DATA = "com.wxn.reader.action.SET_SPEAK_DATA"

        const val ACTION_SET_SPEED = "com.wxn.reader.action.SET_SPEED"

        const val ACTION_SET_PITCH = "com.wxn.reader.action.SET_PITCH"

        const val ACTION_SET_LANGUAGE = "com.wxn.reader.action.SET_LANGUAGE"

        const val EXTRA_SPEED = "extra_speed"

        const val EXTRA_PITCH = "extra_pitch"

        const val EXTRA_LANG = "extra_lang"

        fun getSupportedTtsLanguage(context: Context): Set<Locale> {
            return Speech.init(context).supportedTtsLanguages?.map { locale ->
                Locale.forLanguageTag(locale.language)
            }?.distinct()?.toSet() ?: emptySet()
        }

        fun calcSpeakSentences(theChapter: TextChapter?, thePage: TextPage?, language: Locale): Pair<Int, List<SpeakSentence>>? {
            if (theChapter == null) {
                Logger.e("PageViewController:readPage:theChapter is null")
                return null
            }
            val startChapterIndex =  theChapter.position
            Logger.d("TtsPlaybackService:calcSpeakSentences:startChapterIndex=$startChapterIndex")

            val totalSentences = arrayListOf<SpeakSentence>()
            for ((pIndex, paragraph) in theChapter.readerTexts.withIndex()) {
                if (paragraph is ReaderText.Chapter || paragraph is ReaderText.Text) {
                    val paragraphText = when (paragraph) {
                        is ReaderText.Chapter -> {
                            paragraph.title
                        }
                        is ReaderText.Text -> {
                            paragraph.line
                        }
                        else -> {
                            return null
                        }
                    }
                    totalSentences.addAll(BreakParagraphUtil.breakParagraph(paragraphText,
                        language,
                        startChapterIndex, pIndex))
                }
            }

            var initStartSentenceIndex = 0
            if (thePage != null && thePage.text.isNotEmpty() && thePage.index > 0) {
                Logger.d("TtsPlaybackService:calcSpeakSentences:startPageIndex=${thePage.index}")
                var pageStartParagraph = 0
                var pageStartOffsetInParagraph = 0
                for(tline in thePage.textLines) {
                    if (!tline.isImage && !tline.isLine && tline.text.isNotEmpty()) {
                        pageStartParagraph = tline.paragraphIndex
                        pageStartOffsetInParagraph = tline.charStartOffset
                        break
                    }
                }
                for((sIndex, sentence) in totalSentences.withIndex()) {
                    if (sentence.locator.startParagraphIndex == pageStartParagraph &&
                        sentence.locator.startTextOffset >= pageStartOffsetInParagraph) {
                        initStartSentenceIndex = sIndex
                        break
                    } else if (sentence.locator.startParagraphIndex > pageStartParagraph) {
                        initStartSentenceIndex = sIndex
                        break
                    } else {
                        continue
                    }
                }
            }
            Logger.d("TtsPlaybackService:calcSpeakSentences:initStartSentenceIndex=${initStartSentenceIndex}")

            return initStartSentenceIndex to totalSentences
        }
    }

    @Inject
    lateinit var ttsStateHolder: TtsStateHolder

    @Inject
    lateinit var ttsPreferencesUtil: TtsPreferencesUtil

    private val scope = Coroutines.scope()

    private var mediaSession: MediaSession? = null

    private var ttsNavigator: TtsNavigator? = null

    private var notificationManager: NotificationManager? = null

    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    // 简化的回调实现
    private val serviceCallback = object : SimpleTtsCallback {
        override suspend fun onSentenceComplete(locator: Locator, sentenceIndex: Int): Boolean {
            Logger.d("TtsPlaybackService: 句子完成: $locator, index=$sentenceIndex")
            val state = ttsStateHolder.state.value
            val start = state.timeStart
            val duration = state.timeDuration
            if (start > 0 && duration > 0 && System.currentTimeMillis() - start >= duration) {
                Logger.d("TtsPlaybackService:onSentenceComplete:start=$start, duration=$duration")
                return false //超过播放时长限制
            }

            // 更新全局状态
            ttsStateHolder.updateProgress(
                locator = locator,
                sentenceIndex = sentenceIndex)
            return true// 总是继续播放（错误通过其他机制处理）
        }

        override suspend fun loadNextChapter(currentChapterIndex: Int): TextChapter? {
            Logger.i("TtsPlaybackService: 需要加载下一章: currentChapterIndex=$currentChapterIndex")
            // 更新状态以请求下一章
            val nextChapterIndex = currentChapterIndex + 1
            ttsStateHolder.update { it.copy(currentChapterIndex = nextChapterIndex) }
            // UI层应该监听状态变化，然后通过ACTION_SET_SPEAK_DATA发送章节
            return null
        }

        override suspend fun onPlaybackComplete(success: Boolean, errorMessage: String?) {
            Logger.i("TtsPlaybackService: 播放完成: success=$success, error=$errorMessage")
            if (success) {
                ttsStateHolder.stopPlaying()
            } else {
                ttsStateHolder.reportError(
                    TtsError.PlaybackFailed(reason = errorMessage), applicationContext
                )
            }
            // 如果没有播放内容，停止服务
            stopServiceIfIdle()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.i("TtsPlaybackService: 创建")
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        createNotificationChannel()

        // 初始化TTS导航器
        initTtsNavigator()

        // 初始化媒体会话
        initMediaSession()
        // 必须在onCreate中调用startForeground，以防止ForegroundServiceDidNotStartInTimeException
        // 即使没有通知权限，也应该调用，系统会处理（通知不显示，但服务能正常启动）
        ensureForeground()

        // 更新服务状态
        ttsStateHolder.updateServiceState(bound = true, starting = true)

        // 请求音频焦点
        initAudioFocus()

        updateState(ttsStateHolder.state.value)
    }

    // 在 TtsPlaybackService 中状态变化时更新显示
    fun updateState(state: TtsState) {
        Logger.d("TtsPlaybackService: 收到状态更新")
        updatePlayerMetadata(state)
        updateNotification(state)  // 直接传状态，避免重复读取

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (state.isPlaying && !state.isPaused) {
                    audioManager.requestAudioFocus(request)
                } else {
                    audioManager.abandonAudioFocusRequest(request)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Logger.i("TtsPlaybackService: 收到命令")
        
        // 再次确保前台状态，因为startForegroundService可能被多次调用
        ensureForeground()
        
        intent?.let { handleCommand(it) }

        return START_STICKY
    }

    private fun initAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> pauseTts()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseTts()
                    AudioManager.AUDIOFOCUS_GAIN -> resumeTts()
                }
            }
            audioFocusChangeListener = listener
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(listener)
                .build()
        }
    }

    private fun ensureForeground() {
        val notif = buildNotification()
        notification = notif

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    fun playTts() {
        Logger.i("TtsPlaybackService: playTts")
        ttsNavigator?.play()
        mediaSession?.player?.play()
        ttsStateHolder.startPlaying()
        updateState(ttsStateHolder.state.value)
    }

    fun pauseTts() {
        Logger.i("TtsPlaybackService: pauseTts")
        ttsNavigator?.pause()
        mediaSession?.player?.pause()
        ttsStateHolder.pausePlaying()
        updateState(ttsStateHolder.state.value)
    }

    fun resumeTts() {
        Logger.i("TtsPlaybackService: resumeTts")
        ttsNavigator?.resume()
        mediaSession?.player?.play()
        ttsStateHolder.startPlaying()
        updateState(ttsStateHolder.state.value)
    }

    fun stopTts() {
        Logger.i("TtsPlaybackService: stopTts")
        ttsNavigator?.stop()
        mediaSession?.player?.stop()
        mediaSession?.player?.prepare()
        ttsStateHolder.stopPlaying()
        updateState(ttsStateHolder.state.value)
        stopSelf()
    }

    fun skipPrevTts() {
        Logger.i("TtsPlaybackService: skipPrevTts")
        val success = ttsNavigator?.skipToPreviousUtterance() ?: false
        if (success) {
            ttsStateHolder.update { it.copy(currentSentenceIndex = it.currentSentenceIndex - 1) }
        }
        updateState(ttsStateHolder.state.value)
    }

    fun skipNextTts() {
        Logger.i("TtsPlaybackService: skipNextTts")
        val success = ttsNavigator?.skipToNextUtterance() ?: false
        if (success) {
            ttsStateHolder.update { it.copy(currentSentenceIndex = it.currentSentenceIndex + 1) }
        }
        updateState(ttsStateHolder.state.value)
    }

    private fun handleCommand(intent: Intent) {
        val navigator = ttsNavigator ?: run {
            Logger.w("TtsPlaybackService: TTSNavigator未初始化")
            return
        }
        when (intent.action) {
            ACTION_PLAY -> {
                playTts()
            }
            ACTION_RESUME -> {
                resumeTts()
            }
            ACTION_PAUSE -> {
                pauseTts()
            }
            ACTION_STOP -> {
                stopTts()
            }
            ACTION_TO_PREV -> {
                skipPrevTts()
            }
            ACTION_TO_NEXT -> {
                skipNextTts()
            }
            ACTION_SET_SPEAK_DATA -> {
                Logger.i("处理命令: SET_SPEAK_DATA")
                val speakSentences = ttsStateHolder.getSentences()
                val startIndex = ttsStateHolder.getCurrentPosition().second
                navigator.setSpeakSentences(speakSentences, startIndex)
            }
            ACTION_SET_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                Logger.i("处理命令: SET_SPEED = $speed")
                navigator.setSpeed(speed)
                ttsStateHolder.update { it.copy(speed = speed) }
            }
            ACTION_SET_PITCH -> {
                val pitch = intent.getFloatExtra(EXTRA_PITCH, 1.0f)
                Logger.i("处理命令: SET_PITCH = $pitch")
                navigator.setPitch(pitch)
                ttsStateHolder.update { it.copy(pitch = pitch) }
            }
            ACTION_SET_LANGUAGE -> {
                val locale = intent.getSerializableExtra(EXTRA_LANG) as? Locale
                Logger.i("处理命令: SET_LANGUAGE = $locale")
                if (locale != null) {
                    val success = navigator.setLanguage(locale)
                    if (success) {
                        ttsStateHolder.update { it.copy(language = locale) }
                    } else {
                        ttsStateHolder.reportError(TtsError.LanguageNotSupported(locale), applicationContext)
                    }
                }
            }
        }
    }

    private fun initTtsNavigator() {
        Logger.i("初始化TtsNavigator")
        ttsNavigator = TtsNavigator(
            context = applicationContext,
            ttsPreferencesUtil = ttsPreferencesUtil
        ).apply {
            // 设置简化的回调
            setSpeakCallback(object : TtsNavigator.SuspendSpeakCallback {
                override suspend fun onSpeakSentence(locator: Locator, sentenceIndex: Int): Boolean {
                    return serviceCallback.onSentenceComplete(locator, sentenceIndex)
                }
                override suspend fun onSpeakNextChapter(nextChapterIndex: Int): Boolean {
                    val current = ttsStateHolder.state.value
                    val chapterSize = current.chapterSize
                    return if (nextChapterIndex >= chapterSize) {
                        false
                    } else {
                        serviceCallback.loadNextChapter(nextChapterIndex - 1)
                        true
                    }
                }
                override suspend fun onFinished(status: Int) {
                    val success = status == 0 || status == 1
                    val errorMsg = if (!success) "播放错误: status=$status" else null
                    serviceCallback.onPlaybackComplete(success, errorMsg)
                }
            })
        }
    }

    private fun initMediaSession() {
        Logger.i("初始化MediaSession")
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(), false // 关键：禁止自动请求焦点
            )
            .build()

        // 关键点：添加一个虚拟 MediaItem，否则系统可能不显示 Skip 按钮
        val dummyItem = MediaItem.Builder()
            .setMediaId("tts_session_item")
            .setUri("asset:///dummy.mp3".toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("TTS Playback")
                    .setDurationMs(24 * 60 * 60 * 1000L)  // 24小时
                    .build()
            )
            .build()
        player.setMediaItem(dummyItem)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()

        mediaSession = MediaSession.Builder(this, player)
            .setId("tts_playback_session")
            .setCallback(TtsMediaSessionCallback(applicationContext, ttsStateHolder, this))
            .build()
    }

    private fun createAction(action: String, iconRes: Int, title: String): NotificationCompat.Action {
        val intent = Intent(this, TtsPlaybackService::class.java).apply {
            this.action = action
        }

        val pendingIntent = PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(iconRes, title, pendingIntent).build()
    }

    private fun getLargeIcon(coverPath: String?): Bitmap? {
        if (coverPath.isNullOrEmpty()) {
            return null
        }

        val file = File(coverPath)
        if (!file.exists()) {
            Logger.w("Book cover file not found: $coverPath")
            return null
        }

        return try {
            BitmapFactory.decodeFile(coverPath)
        } catch (e: Exception) {
            Logger.e("Error loading book cover: ${e.message}")
            null
        }
    }

    private fun createContentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotification(newState: TtsState? = null): Notification {
        val state = newState ?: ttsStateHolder.state.value
        Logger.d("TtsPlaybackService:buildNotification:isPlaying=${state.isPlaying},isPaused=${state.isPaused}")

        val playPauseAction = if (state.isPlaying && !state.isPaused) {
            createAction(ACTION_PAUSE, R.drawable.ic_media_pause, getString(R.string.tts_action_pause))
        } else {
            createAction(ACTION_PLAY, R.drawable.ic_media_play, getString(R.string.tts_action_play))
        }

        val stopAction = createAction(ACTION_STOP, R.drawable.ic_media_stop, getString(R.string.tts_action_stop))

        val title = if (state.bookTitle.isNotEmpty() && state.chapterTitle.isNotEmpty()) {
            "${state.bookTitle} - ${state.chapterTitle}"
        } else {
            getString(R.string.tts_notification_title)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts_notification)
            .setLargeIcon(getLargeIcon(state.bookCover))
            .setContentTitle(title)
            .setContentText(state.chapterTitle)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setContentIntent(createContentIntent())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(state.isPlaying && !state.isPaused)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(state: TtsState) {
        try {
            notificationManager?.notify(NOTIFICATION_ID, buildNotification(state))
        } catch (e: Exception) {
            Logger.e("更新通知失败: ${e.message}")
        }
    }

    private fun updatePlayerMetadata(state: TtsState) {
        val player = mediaSession?.player ?: return

        // 构建新的 MediaMetadata
        val metadata = MediaMetadata.Builder()
            .setTitle(state.bookTitle)
            .setArtist(state.chapterTitle)
            .setArtworkUri(state.bookCover?.let { Uri.fromFile(File(it)) })
            .setDurationMs(24 * 60 * 60 * 1000L)
            .build()

        // 获取当前 MediaItem，或在没有时创建一个带静音 URI 的基础 item
        val currentItem = player.currentMediaItem
        val newItem = (currentItem?.buildUpon() ?: MediaItem.Builder()
            .setMediaId("tts_playback")
            .setUri(Uri.parse("asset:///dummy.mp3")))
            .setMediaMetadata(metadata)
            .build()

        // 替换当前 MediaItem（假设 playlist 只有这一个）
        val currentIndex = player.currentMediaItemIndex
        player.replaceMediaItems(currentIndex, currentIndex + 1, listOf(newItem))
    }

    override fun onDestroy() {
        Logger.i("TtsPlaybackService: 销毁")

        notificationManager?.cancel(NOTIFICATION_ID)
        
        scope.cancel()
        mediaSession?.player?.release()
        mediaSession?.release()
        ttsNavigator?.stop()
        ttsNavigator = null
        
        ttsStateHolder.updateServiceState(bound = false)
        ttsStateHolder.stopPlaying()
        
        releaseAudioFocus()
        super.onDestroy()
    }

    // Add helper method for self-stopping
    fun stopServiceIfIdle() {
        ttsNavigator?.let { navigator ->
            if (!navigator.isPlaying()) {
                stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun createNotificationChannel() {
        // Android 8.0+ 需要通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.tts_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.tts_notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val manager = getSystemService(NotificationManager::class.java)
            // 检查是否已存在
            if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun releaseAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                val result = audioManager.abandonAudioFocusRequest(request)
                Logger.i("Audio focus abandon result (API >= O): $result")
                audioFocusRequest = null
            }
        } else {
            audioFocusChangeListener?.let { listener ->
                val result = audioManager.abandonAudioFocus(listener)
                Logger.i("Audio focus abandon result (API < O): $result")
                audioFocusChangeListener = null
            }
        }
    }

    private fun shouldShowNotification(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
}
