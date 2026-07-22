package com.wxn.reader.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.wxn.base.bean.TtsConfig
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.base.util.launchMain
import com.wxn.base.util.withMain
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextPage
import com.wxn.reader.service.TtsError
import com.wxn.reader.service.TtsPlaybackService
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_PAUSE
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_RESUME
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_SET_CONFIG
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_SET_LANGUAGE
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_SET_PITCH
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_SET_SPEAKER
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_SET_SPEAK_DATA
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_SET_SPEED
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_STOP
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_TO_NEXT
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_TO_PREV
import com.wxn.reader.service.TtsPlaybackService.Companion.EXTRA_CONFIG
import com.wxn.reader.service.TtsPlaybackService.Companion.EXTRA_LANG
import com.wxn.reader.service.TtsPlaybackService.Companion.EXTRA_PITCH
import com.wxn.reader.service.TtsPlaybackService.Companion.EXTRA_SPEAKER_INDEX
import com.wxn.reader.service.TtsPlaybackService.Companion.EXTRA_SPEED
import com.wxn.reader.service.TtsServiceStatus
import com.wxn.reader.service.TtsStateHolder
import com.wxn.reader.util.tts.ITtsService
import com.wxn.reader.util.tts.TtsNavigator
import com.wxn.base.bean.TtsPlaybackStatus
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class TtsServiceController @Inject constructor(
    private val context: Context,
    private val stateHolder: TtsStateHolder
) : ITtsService, ServiceConnection {

    private var service: TtsPlaybackService? = null

    private val scope = Coroutines.scope()

    @OptIn(UnstableApi::class)
    fun startService(context: Context) {
        Logger.i("TtsServiceController: 启动TTS服务")
        val servStatus = stateHolder.getServiceStatus()
        if (servStatus == TtsServiceStatus.CONNECTED || servStatus == TtsServiceStatus.STARTING) {
            return
        }
        stateHolder.updateServiceStatus(TtsServiceStatus.STARTING)
        scope.launchMain {
            try {
                startAndBindService(context)
            } catch (e: Exception) {
                Logger.w("启动TTS服务失败: ${e.message}")
                stateHolder.reportError(TtsError.ServiceNotStarted, context)
                stateHolder.updateServiceStatus(TtsServiceStatus.FAILED)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun skipToPreviousUtterance(context: Context) {
        Logger.i("TtsServiceController: 上一句")
        executeOnServiceReady {
            if (!stateHolder.state.value.canSkipPrev) {
                return@executeOnServiceReady
            }
            service?.skipPrevTts()?.let {
                return@executeOnServiceReady
            }
            sendCommand(context, ACTION_TO_PREV)
        }
    }

    @OptIn(UnstableApi::class)
    override fun skipToNextUtterance(context: Context) {
        Logger.i("TtsServiceController: 下一句")
        executeOnServiceReady {
            if (!stateHolder.state.value.canSkipNext) {
                return@executeOnServiceReady
            }

            service?.skipNextTts()?.let {
                return@executeOnServiceReady
            }

            sendCommand(context, ACTION_TO_NEXT)
            return@executeOnServiceReady
        }
    }

    @OptIn(UnstableApi::class)
    override fun pause(context: Context) {
        Logger.i("TtsServiceController: 暂停")
        stateHolder.pausePlaying()
        executeOnServiceReady {
            service?.pauseTts()?.let {
                return@executeOnServiceReady
            }
            sendCommand(context, ACTION_PAUSE)
        }
    }

    @OptIn(UnstableApi::class)
    override fun resume(context: Context) {
        Logger.i("TtsServiceController: 恢复播放")
        stateHolder.startPlaying()  //pending playing status
        executeOnServiceReady {
            service?.resumeTts()?.let {
                return@executeOnServiceReady
            }
            sendCommand(context, ACTION_RESUME)
        }
    }


    @OptIn(UnstableApi::class)
    override fun stop(context: Context) {
        Logger.i("TtsServiceController: 停止")
        executeOnServiceReady {
            service?.stopTts()?.let {
                unbindService(context)
                return@executeOnServiceReady
            }
            sendCommand(context, ACTION_STOP)
            unbindService(context)
        }
    }

    @OptIn(UnstableApi::class)
      override suspend fun setSpeakStartChapterAndPage(
        context: Context,
        chapter: TextChapter?,
        page: TextPage?,
        bookTitle: String,
        chapterTitle: String,
        bookCover: String?,
        bookUri: String,
        chapterSize: Int
    ): Boolean {
        Logger.i("TtsServiceController: 设置播放章节和页面")
        if (chapter == null) {
            Logger.w("章节不能为空")
            stateHolder.reportError(TtsError.ChapterLoadFailed(-1), context)
            return false
        }

        val language = stateHolder.state.value.bookLocale ?: stateHolder.state.value.language
        val (initStartSentenceIndex, totalSentences) = TtsPlaybackService.calcSpeakSentences(
            chapter,
            page,
            language
        ) ?: return false
        Logger.i("${this.javaClass.name}:setSpeakStartChapterAndPage:totalSentences.size=${totalSentences.size},initStartSentenceIndex=$initStartSentenceIndex")
        if (totalSentences.isEmpty()) {
            Logger.w("设置播放数据失败: 句子列表为空")
            return false
        }

        if (!stateHolder.waitForServiceConnected()) { //可能阻塞等待
            return false
        }
        // 更新状态
        stateHolder.update {
            it.copy(
                speakingSentences = totalSentences,
                currentSentenceIndex = initStartSentenceIndex,
                currentChapterIndex = chapter.position,
                bookTitle = bookTitle,
                chapterTitle = chapterTitle,
                bookCover = bookCover,
                bookUri = bookUri,
                chapterSize = chapterSize
            )
        }

        return sendCommand(context, ACTION_SET_SPEAK_DATA, null, TtsError.ChapterLoadFailed(chapter.position))
    }

    //代替了的play
    @OptIn(UnstableApi::class)
    override suspend fun setSpeakConfigsAndPlay(
        context: Context,
        chapter: TextChapter?,
        page: TextPage?,
        bookTitle: String,
        chapterTitle: String,
        bookCover: String?,
        bookUri: String,
        chapterSize: Int,
        ttsConfig: TtsConfig
    ): Boolean {
        Logger.i("TtsServiceController: 设置播放章节和页面")
        if (chapter == null) {
            Logger.w("章节不能为空")
            stateHolder.reportError(TtsError.ChapterLoadFailed(-1), context)
            return false
        }

        val language = stateHolder.state.value.bookLocale ?: stateHolder.state.value.language
        val (initStartSentenceIndex, totalSentences) = TtsPlaybackService.calcSpeakSentences(
            chapter,
            page,
            language
        ) ?: return false
        Logger.i("${this.javaClass.name}:setSpeakStartChapterAndPage:totalSentences.size=${totalSentences.size},initStartSentenceIndex=$initStartSentenceIndex")
        if (totalSentences.isEmpty()) {
            Logger.w("设置播放数据失败: 句子列表为空")
            return false
        }

        if (!stateHolder.waitForServiceConnected()) { //可能阻塞等待
            return false
        }
        // 更新状态
        stateHolder.update {
            it.copy(
                //TTS 播放数据
                speakingSentences = totalSentences,
                currentSentenceIndex = initStartSentenceIndex,
                currentChapterIndex = chapter.position,
                bookTitle = bookTitle,
                chapterTitle = chapterTitle,
                bookCover = bookCover,
                bookUri = bookUri,
                chapterSize = chapterSize,

                //TTS播放控制参数
                speed = ttsConfig.speed,
                pitch = ttsConfig.pitch,
                language = Locale.forLanguageTag(ttsConfig.language),

                engineType = ttsConfig.engineType,
                engineModel = ttsConfig.engineModel,
                modelType = ttsConfig.modelType,
                baseModelDatas = ttsConfig.baseDatas,

                speakerNum = ttsConfig.speakerNum,
                modelSpeaker = ttsConfig.speaker,

                //播放状态
                ttsPlayerStatus = TtsPlaybackStatus.PENDING_PLAYING, //: 准备启动播放
            )
        }

        return sendCommand(context, ACTION_SET_CONFIG,
            { intent->
                intent.putExtra(EXTRA_CONFIG, ttsConfig)
            },
            TtsError.ChapterLoadFailed(chapter.position)
        )
    }

    @OptIn(UnstableApi::class)
    override fun setSpeed(context: Context, speed: Float) {
        executeOnServiceReady {
            setSpeed(context, speed) { success ->
                if (!success) {
                    Logger.w("设置语速失败")
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun setSpeed(context: Context, speed: Float, onComplete: (Boolean) -> Unit) {
        Logger.i("TtsServiceController: 设置语速: $speed")
        // 验证参数
        val validSpeed = speed.coerceIn(0.25f, 4.0f)
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = ACTION_SET_SPEED
            putExtra(EXTRA_SPEED, validSpeed)
        }
        try {
            launchServiceIntent(context, intent)
            // 立即更新状态（乐观更新）
            stateHolder.update { it.copy(speed = validSpeed) }
            onComplete(true)
        } catch (e: Exception) {
            Logger.w("设置语速失败: ${e.message}")
            onComplete(false)
        }
    }

    @OptIn(UnstableApi::class)
    override fun setPitch(context: Context, pitch: Float) {
        executeOnServiceReady {
            setPitch(context, pitch) {
                if (!it) {
                    Logger.w("设置语速失败")
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun setPitch(
        context: Context,
        pitch: Float,
        onComplete: (Boolean) -> Unit
    ) {
        Logger.i("TtsServiceController: 设置语调: $pitch")
        // 验证参数
        val validPitch = pitch.coerceIn(TtsNavigator.TTS_MIN_PITCH, TtsNavigator.TTS_MAX_PITCH)
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = ACTION_SET_PITCH
            putExtra(EXTRA_PITCH, validPitch)
        }
        try {
            launchServiceIntent(context, intent)
            // 立即更新状态（乐观更新）
            stateHolder.update { it.copy(pitch = validPitch) }
            onComplete(true)
        } catch (e: Exception) {
            Logger.w("设置语调失败: ${e.message}")
            onComplete(false)
        }
    }

    override fun isServiceRunning(context: Context): Boolean {
        return stateHolder.state.value.isServiceAvailable &&
                stateHolder.state.value.isEngineAvailable
    }

    /****
     * 获取运行时使用的model
     */
    override fun getRunningModel(context: Context): String {
        return if (isServiceRunning(context)) {
            stateHolder.state.value.engineModel
        } else {
            ""
        }
    }



    @OptIn(UnstableApi::class)
    override fun setLanguage(context: Context, newlocale: Locale) {
        Logger.i("${this.javaClass.name}:setLanguage(newLocale=$newlocale)")
        executeOnServiceReady {
            setLanguage(context, newlocale) {
                if (!it) {
                    Logger.w("setLanguage failed")
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun setPlayTime(context: Context, playTime: Float) {
        Logger.i("${this.javaClass.name}:setPlayTime(playTime=$playTime")
        // 立即更新状态（乐观更新）
        stateHolder.update {
            it.copy(
                playSessionStartMs = 0L,
                timePlayedMs = 0L,
                timeDuration = (playTime * 3600).toLong() * 1000L
            )
        }
    }

    @OptIn(UnstableApi::class)
    private fun setSpeakerIndex(
        context: Context,
        speakerIndex: Int,
        onComplete: (Boolean) -> Unit
    ) {
        Logger.i("${this.javaClass.name}:setSpeakerIndex(speakerIndex=$speakerIndex)")

        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = ACTION_SET_SPEAKER
            putExtra(EXTRA_SPEAKER_INDEX, speakerIndex)
        }
        try {
            launchServiceIntent(context, intent)
            // 立即更新状态（乐观更新）
            stateHolder.update { it.copy(modelSpeaker = speakerIndex) }
            onComplete(true)
        } catch (e: Exception) {
            Logger.w("设置语音失败: ${e.message}")
            onComplete(false)
        }
    }

    @OptIn(UnstableApi::class)
    private fun setLanguage(
        context: Context,
        newlocale: Locale,
        onComplete: (Boolean) -> Unit
    ) {
        Logger.i("TtsServiceController: 设置语言: $newlocale")

        // 验证参数
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = ACTION_SET_LANGUAGE
            putExtra(EXTRA_LANG, newlocale)
        }
        try {
            launchServiceIntent(context, intent)
            // 立即更新状态（乐观更新）
            stateHolder.update { it.copy(language = newlocale) }
            onComplete(true)
        } catch (e: Exception) {
            Logger.w("设置语言失败: ${e.message}")
            onComplete(false)
        }
    }

    override fun setSpeakerIndex(context: Context, speakerIndex: Int) {
        executeOnServiceReady {
            setSpeakerIndex(context, speakerIndex) { status ->
                Logger.w("TtsServiceController: setSpeakerIndex success: $status")
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun sendCommand(context: Context, action: String, applyIntent: ((Intent)->Intent)? = null, error: TtsError = TtsError.ServiceNotStarted): Boolean {
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            this.action = action
        }

        applyIntent?.let { applier->
            applier(intent)
        }

        // ... 启动服务
        return try {
            launchServiceIntent(context, intent)
            true
        } catch (e: Exception) {
            Logger.w("发送命令失败 [$action]: ${e.message}")
            stateHolder.reportError(error, context)
            false
        }
    }

    private fun launchServiceIntent(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        service = (binder as? TtsPlaybackService.TtsBinder?)?.getService() ?: return
        stateHolder.updateServiceStatus(TtsServiceStatus.CONNECTED)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
        stateHolder.updateServiceStatus(TtsServiceStatus.DISCONNECTED)
    }

    private fun startAndBindService(context: Context) {
        val intent = Intent(context, TtsPlaybackService::class.java)
        launchServiceIntent(context, intent)
        context.bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    private fun unbindService(context: Context) {
        context.unbindService(this)
        service = null
    }

    private inline fun executeOnServiceReady(
        crossinline action: suspend () -> Unit
    ) {
        scope.launchIO {
            if (!stateHolder.waitForServiceConnected()) {
                return@launchIO
            }
            withMain { action() }
        }
    }
}