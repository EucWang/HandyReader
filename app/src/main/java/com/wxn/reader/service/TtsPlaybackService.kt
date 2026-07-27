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
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import com.wxn.base.bean.EngineModelConfig
import com.wxn.base.bean.Locator
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.SpeakSentence
import com.wxn.base.bean.TtsConfig
import com.wxn.base.util.BreakParagraphUtil
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.base.util.launchMain
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.reader.MainActivity
import com.wxn.reader.R
import com.wxn.reader.ui.theme.stringResource
import com.wxn.reader.util.tts.TtsNavigator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class TtsPlaybackService : MediaSessionService() {

    private var notification: Notification? = null

    private var wakeLock: PowerManager.WakeLock? = null

    inner class TtsBinder : Binder() {
        fun getService(): TtsPlaybackService = this@TtsPlaybackService

        // 暴露核心方法（可逐步添加）
        fun pause(): Boolean = pauseTts().let { true }
        fun stop(): Boolean = stopTts().let { true }
        fun skipNext(): Boolean = skipNextTts().let { true }
        fun skipPrev(): Boolean = skipPrevTts().let { true }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "tts_playback_channel_v2"
        const val NOTIFICATION_ID = 1007

        const val ACTION_PAUSE = "com.wxn.reader.action.PAUSE_TTS"

        const val ACTION_RESUME = "com.wxn.reader.action.RESUME_TTS"
        const val ACTION_STOP = "com.wxn.reader.action.STOP_TTS"

        const val ACTION_TO_PREV = "com.wxn.reader.action.SKIP_TO_PREV"

        const val ACTION_TO_NEXT = "com.wxn.reader.action.SKIP_TO_NEXT"

        const val ACTION_SET_SPEAK_DATA = "com.wxn.reader.action.SET_SPEAK_DATA"

        const val ACTION_SET_SPEED = "com.wxn.reader.action.SET_SPEED"

        const val ACTION_SET_PITCH = "com.wxn.reader.action.SET_PITCH"

        const val ACTION_SET_LANGUAGE = "com.wxn.reader.action.SET_LANGUAGE"

        const val ACTION_SET_SPEAKER = "com.wxn.reader.action.SET_SPEAKER"

        const val ACTION_SET_CONFIG = "com.wxn.reader.action.SET_CONFIG"

        const val EXTRA_SPEED = "extra_speed"

        const val EXTRA_PITCH = "extra_pitch"

        const val EXTRA_LANG = "extra_lang"

        const val EXTRA_SPEAKER_INDEX = "extra_speaker_index"

        const val EXTRA_CONFIG = "extra_config"

//        fun getSupportedTtsLanguage(context: Context): Set<Locale> {
//            return Speech.init(context, null).supportedTtsLanguages?.map { locale ->
//                Locale.forLanguageTag(locale.language)
//            }?.distinct()?.toSet() ?: emptySet()
//        }

        fun calcSpeakSentences(
            theChapter: TextChapter?,
            thePage: TextPage?,
            language: Locale
        ): Pair<Int, List<SpeakSentence>>? {
            if (theChapter == null) {
                Logger.e("PageViewController:readPage:theChapter is null")
                return null
            }
            val startChapterIndex = theChapter.position
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
                    totalSentences.addAll(
                        BreakParagraphUtil.breakParagraph(
                            paragraphText,
                            language,
                            startChapterIndex, pIndex
                        )
                    )
                }
            }

            var initStartSentenceIndex = 0
            if (thePage != null && thePage.text.isNotEmpty() && thePage.index > 0) {
                Logger.d("TtsPlaybackService:calcSpeakSentences:startPageIndex=${thePage.index}")
                var pageStartParagraph = 0
                var pageStartOffsetInParagraph = 0
                for (tline in thePage.textLines) {
                    if (!tline.isImage && !tline.isLine && tline.text.isNotEmpty()) {
                        pageStartParagraph = tline.paragraphIndex
                        pageStartOffsetInParagraph = tline.charStartOffset
                        break
                    }
                }
                for ((sIndex, sentence) in totalSentences.withIndex()) {
                    if (sentence.locator.startParagraphIndex == pageStartParagraph &&
                        sentence.locator.startTextOffset >= pageStartOffsetInParagraph
                    ) {
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

    private val scope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.Main +
                CoroutineExceptionHandler { _, throwable ->
                    Logger.e(throwable)
                }
    )

    private var mediaSession: MediaSession? = null

    private var ttsNavigator: TtsNavigator? = null

    private var notificationManager: NotificationManager? = null

    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioFocusChangeListener: AudioManager.OnAudioFocusChangeListener? = null

    // True when the current pause was triggered by a transient audio-focus loss
    // (e.g. an incoming call). Lets us auto-resume on focus gain without resuming
    // a pause the user requested manually.
    private var pausedByFocusLoss = false

    private var lastCoverPath: String? = null
    private var lastArtworkData: ByteArray? = null

    // 简化的回调实现
    private val serviceCallback = object : SimpleTtsCallback {
        override fun checkTimer(): Boolean {
            val state = ttsStateHolder.state.value
            val duration = state.timeDuration
            if (duration <= 0) return true

            if (state.playSessionStartMs == 0L) {
                ttsStateHolder.startTimerSession() //设置本次播放会话的起始时间戳
            }

            val currentState = ttsStateHolder.state.value
            val playedMs = currentState.timePlayedMs + if (currentState.playSessionStartMs > 0) {
                System.currentTimeMillis() - currentState.playSessionStartMs
            } else {
                0
            }
            Logger.d("TtsPlaybackService:checkTimer:playedMs = $playedMs, duration = $duration")
            return playedMs < duration
        }

        override fun onTimerExpired() {
            Logger.i("TtsPlaybackService:onTimerExpired: 定时器到期，停止播放")
            ttsStateHolder.expiredTimer()
            stopTts()
        }

        override fun onSentenceComplete(locator: Locator, sentenceIndex: Int) {
            // 更新全局状态
            ttsStateHolder.updateProgress(
                locator = locator,
                sentenceIndex = sentenceIndex
            )
        }

        override fun loadNextChapter(currentChapterIndex: Int): TextChapter? {
            Logger.i("TtsPlaybackService: 需要加载下一章: currentChapterIndex=$currentChapterIndex")
            // 更新状态以请求下一章
            val nextChapterIndex = currentChapterIndex + 1
            ttsStateHolder.update { it.copy(currentChapterIndex = nextChapterIndex) }
            // UI层应该监听状态变化，然后通过ACTION_SET_SPEAK_DATA发送章节
            return null
        }

        override fun onPlaybackComplete(success: Boolean, errorMessage: String?) {
            Logger.i("TtsPlaybackService: 播放完成: success=$success, error=$errorMessage")
            if (success) {
                stopTts()
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

        // 请求音频焦点
        initAudioFocus()

        updateState(ttsStateHolder.state.value)
    }

    val binder = TtsBinder()
    override fun onBind(intent: Intent?): IBinder {
        super.onBind(intent)
        return binder
    }

    // 在 TtsPlaybackService 中状态变化时更新显示
    fun updateState(state: TtsState) {
        Logger.d("TtsPlaybackService: 收到状态更新")
        updatePlayerMetadata(state)
        updateNotification(state)

        if (state.isPlaying) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (state.isPlaying) {
                    audioManager.requestAudioFocus(request)
                }
                // Do NOT abandon focus while merely paused: a transient loss (e.g. an
                // incoming call) requires us to keep the request so the framework
                // delivers AUDIOFOCUS_GAIN when the call ends and we can auto-resume.
                // Focus is released for good in stopTts()/releaseAudioFocus().
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

    // Explicitly acquire audio focus when playback (re)starts. This must NOT rely on
    // the TtsState.isPlaying snapshot: at the point playback is kicked off the status is
    // still PENDING_PLAYING (PLAYING is only set later from sentence-progress callbacks),
    // so requesting focus off isPlaying would silently never happen and we would never
    // receive focus-loss callbacks for incoming calls.
    private fun acquireAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.requestAudioFocus(request)
            }
        }
    }

    private fun initAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    // Permanent loss (another app took focus for good): pause and do
                    // not arm auto-resume — no AUDIOFOCUS_GAIN will follow.
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        pausedByFocusLoss = false
                        pauseTts()
                    }
                    // Transient loss such as an incoming call. We also treat the
                    // "can duck" case as a full pause: spoken content is useless at a
                    // ducked volume, and setWillPauseWhenDucked(true) normally turns
                    // ducking into a plain transient loss anyway. Only arm auto-resume
                    // if we were actually playing, so a user pause is left untouched.
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        if (ttsStateHolder.state.value.isPlaying) {
                            pausedByFocusLoss = true
                            pauseTts()
                        }
                    }
                    // Focus regained (e.g. the call ended): resume only if we are the
                    // ones who paused because of the focus loss.
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        if (pausedByFocusLoss) {
                            pausedByFocusLoss = false
                            resumeTts()
                        }
                    }
                }
            }
            audioFocusChangeListener = listener
            audioFocusRequest =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    // Speech must not be ducked to a low volume; ask the framework to
                    // send us a transient loss (so we pause) instead of auto-ducking.
                    .setWillPauseWhenDucked(true)
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

    fun pauseTts() {
        Logger.i("TtsPlaybackService: pauseTts")
        scope.launchMain {
            ttsStateHolder.pauseTimerSession()
            ttsNavigator?.pause()
            mediaSession?.player?.pause()
            ttsStateHolder.pausedPlay()
            updateState(ttsStateHolder.state.value)
        }
    }

    fun resumeTts() {
        Logger.i("TtsPlaybackService: resumeTts")
        // Once playback resumes there is no pending focus-loss pause to recover from.
        pausedByFocusLoss = false
        acquireAudioFocus()
        scope.launchMain {
            ttsStateHolder.startPlaying()
            ttsStateHolder.startTimerSession()
            ttsNavigator?.resume()
            mediaSession?.player?.play()
            updateState(ttsStateHolder.state.value)
        }
    }

    /****
     * 停止TTS播放
     * 停止引擎的运行
     * 停止TTS服务, 停止通知栏通知
     */
    fun stopTts() {
        Logger.i("TtsPlaybackService: stopTts")
        pausedByFocusLoss = false
        ttsStateHolder.stopPlaying()
        ttsNavigator?.stop()
        mediaSession?.player?.stop()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        //--------------------
        notificationManager?.cancel(NOTIFICATION_ID)
        scope.cancel()
        ttsNavigator?.shutdown()
        releaseWakeLock()
        releaseAudioFocus()
        mediaSession?.let {
            it.player.stop()
            it.player.release()
            it.release()
        }
        mediaSession = null
        ttsNavigator = null
        //---------------------

        stopSelf()
    }

    fun skipPrevTts() {
        Logger.i("TtsPlaybackService: skipPrevTts")
        scope.launchMain {
            val success = ttsNavigator?.skipToPreviousUtterance() ?: false
            if (success) {
                ttsStateHolder.update { it.copy(currentSentenceIndex = it.currentSentenceIndex - 1) }
            }
            updateState(ttsStateHolder.state.value)
        }
    }

    fun skipNextTts() {
        Logger.i("TtsPlaybackService: skipNextTts")
        scope.launchMain {
            val success = ttsNavigator?.skipToNextUtterance() ?: false
            if (success) {
                ttsStateHolder.update { it.copy(currentSentenceIndex = it.currentSentenceIndex + 1) }
            }
            updateState(ttsStateHolder.state.value)
        }
    }

    private fun handleCommand(intent: Intent) {
        val navigator = ttsNavigator ?: run {
            Logger.w("TtsPlaybackService: TTSNavigator未初始化")
            return
        }
        when (intent.action) {

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
                // New chapter data (e.g. an auto-advance to the next chapter) has just updated
                // bookTitle/chapterTitle in the state. Refresh the MediaSession metadata + the
                // notification so external surfaces (lock screen, Bluetooth player) show the new
                // chapter instead of the one that was playing when playback started.
                updateState(ttsStateHolder.state.value)
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
                    val success = navigator.setLanguage(locale) { success ->
                        if (success) {
                            ttsStateHolder.update { it.copy(language = locale) }
                        } else {
                            ttsStateHolder.reportError(
                                TtsError.LanguageNotSupported(locale),
                                applicationContext
                            )
                        }
                    }
                }
            }

            ACTION_SET_SPEAKER -> {
                val speakerIndex = intent.getIntExtra(EXTRA_SPEAKER_INDEX, 0)
                Logger.i("处理命令: SET_SPEAKER = $speakerIndex")
                navigator.setSpeakerIndex(speakerIndex)
            }

            ACTION_SET_CONFIG -> {
                val speakSentences = ttsStateHolder.getSentences()
                val startIndex = ttsStateHolder.getCurrentPosition().second
                navigator.setSpeakSentences(speakSentences, startIndex)

                intent.getParcelableExtra<TtsConfig>(EXTRA_CONFIG)?.let { config ->
                    navigator.setSpeakCallback(navigatorCallback)

                    ttsStateHolder.updateEngineInitState(TtsEngineStatus.INITIALIZING)
                    navigator.setEngineInfo(
                        config.engineType,
                        if (config.engineType != 0) {
                            EngineModelConfig(
                                engineModel = config.engineModel,
                                modelType = config.modelType,
                                baseDatas = config.baseDatas,
                                speakerNum = config.speakerNum,
                                speaker = config.speaker,
                                language = config.language,
                                modelDir = config.modelDir
                            )
                        } else null,
                        config.speed,
                        config.pitch,
                        Locale.forLanguageTag(config.language),
                        config.speaker
                    ) { initStatus ->
                        ttsStateHolder.updateEngineInitState(initStatus)

                        if (initStatus == TtsEngineStatus.READY) {
                            Logger.d("TtsPlaybackService::initStatus=$initStatus,then invoke play")
                            if (config.engineType == 0) {
                                // Query supported languages only AFTER the engine is READY.
                                // Previously this ran concurrently with setEngineInfo(): both paths
                                // called into engine init, each opened a TextToSpeech connection to
                                // the system engine, and the orphaned one was never shut down -
                                // keeping the engine process bound forever (the orea leak).
                                navigator.getSupportedLanguage { languages ->
                                    if (languages.isNotEmpty()) {
                                        ttsStateHolder.updateLanguages(languages)
                                    }
                                }
                            }
                            scope.launchMain {
                                ttsStateHolder.startTimerSession()
                                acquireAudioFocus()
                                navigator.play()
                                mediaSession?.player?.play()
                                updateState(ttsStateHolder.state.value)
                            }
                        }
                    }
                }
            }
        }
    }

    private val navigatorCallback = object : TtsNavigator.SuspendSpeakCallback {
        override fun onTimer(): Boolean {
            return serviceCallback.checkTimer()
        }

        override fun onSpeakSentence(
            locator: Locator,
            sentenceIndex: Int
        ) {
            serviceCallback.onSentenceComplete(locator, sentenceIndex)
        }

        override fun onSpeakNextChapter(nextChapterIndex: Int): Boolean {
            val current = ttsStateHolder.state.value
            val chapterSize = current.chapterSize
            return if (nextChapterIndex >= chapterSize) {
                false
            } else {
                serviceCallback.loadNextChapter(nextChapterIndex - 1)
                true
            }
        }

        override fun onFinished(status: Int) {
            when (status) {
                TtsNavigator.STATUS_NORMAL_FINISH -> {
                    serviceCallback.onPlaybackComplete(true, null)
                }

                TtsNavigator.STATUS_TIMER_EXPIRED -> {
                    serviceCallback.onTimerExpired()
                }

                else -> {
                    serviceCallback.onPlaybackComplete(
                        false,
                        stringResource(R.string.play_error, status)
                    )
                }
            }
        }
    }

    private fun initTtsNavigator() {
        Logger.i("初始化TtsNavigator")
        ttsNavigator = TtsNavigator(
            context = applicationContext,
            ttsPreferencesUtil = TtsPreferencesUtil(context = applicationContext)
        ).apply {
            setSpeakCallback(navigatorCallback)
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
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // 关键点：添加一个虚拟 MediaItem，否则系统可能不显示 Skip 按钮
        val silenceUri = Uri.fromFile(PathUtil.getDummyWavFile(applicationContext))
        val dummyItem = MediaItem.Builder()
            .setMediaId("tts_session_item")
            .setUri(silenceUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("TTS Playback")
                    .setDurationMs(24 * 60 * 60 * 1000L)  // 24小时
                    .build()
            )
            .build()
        player.setMediaItems(listOf(dummyItem, dummyItem))
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()

        val forwardingPlayer = TtsForwardingPlayer(player)
        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setId("tts_playback_session")
            .setCallback(TtsMediaSessionCallback(applicationContext, ttsStateHolder, this))
            .build()
    }

    private fun createAction(
        action: String,
        iconRes: Int,
        title: String
    ): NotificationCompat.Action {
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
        Logger.d("TtsPlaybackService:buildNotification:isPlaying=${state.isPlaying}")

        val nextAction = createAction(
            ACTION_TO_NEXT,
            R.drawable.ic_media_next, getString(R.string.tts_action_next)
        )
        val prevAction = createAction(
            ACTION_TO_PREV,
            R.drawable.ic_media_previous, getString(R.string.tts_action_prev)
        )

        val playPauseAction = if (state.isPlaying) {
            createAction(
                ACTION_PAUSE,
                R.drawable.ic_media_pause,
                getString(R.string.tts_action_pause)
            )
        } else {
            createAction(
                ACTION_RESUME,
                R.drawable.ic_media_play,
                getString(R.string.tts_action_play)
            )
        }

        val stopAction =
            createAction(ACTION_STOP, R.drawable.ic_media_stop, getString(R.string.tts_action_stop))

        val title = if (state.bookTitle.isNotEmpty() && state.chapterTitle.isNotEmpty()) {
            "${state.bookTitle} - ${state.chapterTitle}"
        } else {
            getString(R.string.tts_notification_title)
        }

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts_notification)
            .setLargeIcon(getLargeIcon(state.bookCover))
            .setContentTitle(title)
            .setContentText(state.chapterTitle)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(stopAction)
            .setContentIntent(createContentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        mediaSession?.let { session ->
            builder.setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        }

        return builder.build()
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

        if (state.bookCover != lastCoverPath) {
            lastCoverPath = state.bookCover
            lastArtworkData = state.bookCover?.let { path ->
                getLargeIcon(path)?.let { bitmap ->
                    try {
                        ByteArrayOutputStream().use { stream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                            stream.toByteArray()
                        }
                    } catch (e: Exception) {
                        Logger.e("Failed to compress artwork: ${e.message}")
                        null
                    }
                }
            }
        }

        // 构建新的 MediaMetadata
        val metadata = MediaMetadata.Builder()
            .setTitle(state.bookTitle)
            .setArtist(state.chapterTitle)
            .setDescription(state.currentLocator?.text.orEmpty())
            .apply {
                lastArtworkData?.let {
                    setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
            }
            .setDurationMs(24 * 60 * 60 * 1000L)
            .build()

        // 获取当前 MediaItem，或在没有时创建一个带静音 URI 的基础 item
        val currentItem = player.currentMediaItem
        val silenceUri = Uri.fromFile(PathUtil.getDummyWavFile(applicationContext))
        val newItem = (currentItem?.buildUpon() ?: MediaItem.Builder()
            .setMediaId("tts_playback")
            .setUri(silenceUri))
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

        mediaSession?.let {
            it.player.stop()
            it.player.release()
            it.release()
        }
        mediaSession = null
        ttsNavigator?.stop()
        ttsNavigator?.shutdown()
        ttsNavigator = null

        ttsStateHolder.stopPlaying()

        releaseWakeLock()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.tts_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.tts_notification_channel_description)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HandyReader::TtsWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(3 * 60 * 60 * 1000L)
        }
        Logger.d("TtsPlaybackService: WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Logger.d("TtsPlaybackService: WakeLock released")
            }
        }
        wakeLock = null
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
