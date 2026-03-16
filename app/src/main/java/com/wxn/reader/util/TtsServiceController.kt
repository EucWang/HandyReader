package com.wxn.reader.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextPage
import com.wxn.reader.service.TtsError
import com.wxn.reader.service.TtsPlaybackService
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_PAUSE
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_PLAY
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_RESUME
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_SET_LANGUAGE
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_SET_PITCH
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_SET_SPEAK_DATA
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_SET_SPEED
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_STOP
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_TO_NEXT
import com.wxn.reader.service.TtsPlaybackService.Companion.ACTION_TO_PREV
import com.wxn.reader.service.TtsPlaybackService.Companion.EXTRA_LANG
import com.wxn.reader.service.TtsPlaybackService.Companion.EXTRA_PITCH
import com.wxn.reader.service.TtsPlaybackService.Companion.EXTRA_SPEED
import com.wxn.reader.service.TtsStateHolder
import com.wxn.reader.util.tts.ITtsService
import com.wxn.reader.util.tts.TtsNavigator
import kotlinx.coroutines.launch
import net.gotev.speech.Speech
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsServiceController @Inject constructor(
    private val context: Context,
    private val stateHolder: TtsStateHolder
) : ITtsService {

    init {
        Speech.init(context)
    }

    private val scope = Coroutines.scope()

    @OptIn(UnstableApi::class)
    fun startService(context: Context) {
        Logger.i("TtsServiceController: 启动TTS服务")
        stateHolder.updateServiceState(bound = false, starting = true)
        try {
            val intent = Intent(context, TtsPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            // 假设服务启动成功，实际应由服务连接回调更新状态
            scope.launch {
                // 模拟服务启动延迟
                kotlinx.coroutines.delay(1000)
                stateHolder.updateServiceState(bound = true, starting = false)
            }
        } catch (e: Exception) {
            Logger.e("启动TTS服务失败: ${e.message}")
            stateHolder.reportError(TtsError.ServiceNotStarted, context)
            stateHolder.updateServiceState(bound = false, starting = false)
        }
    }

    @OptIn(UnstableApi::class)
    fun stopService(context: Context) {
        Logger.i("TtsServiceController: 停止TTS服务")
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        stateHolder.stopPlaying()
    }

    @OptIn(UnstableApi::class)
    override fun skipToPreviousUtterance(context: Context): Boolean {
        Logger.i("TtsServiceController: 上一句")

        if (!stateHolder.state.value.canSkipPrev) {
            return false
        }

        sendCommand(context, ACTION_TO_PREV)
        return true
    }

    @OptIn(UnstableApi::class)
    override fun skipToNextUtterance(context: Context): Boolean {
        Logger.i("TtsServiceController: 下一句")
        if (!stateHolder.state.value.canSkipNext) {
            return false
        }
        sendCommand(context, ACTION_TO_NEXT)
        return true
    }

    @OptIn(UnstableApi::class)
    override fun pause(context: Context) {
        Logger.i("TtsServiceController: 暂停")
        sendCommand(context, ACTION_PAUSE)
        stateHolder.pausePlaying()
    }

    @OptIn(UnstableApi::class)
    override fun resume(context: Context) {
        Logger.i("TtsServiceController: 恢复播放")

        sendCommand(context, ACTION_RESUME)
        stateHolder.startPlaying()
    }

    @OptIn(UnstableApi::class)
    override fun play(context: Context) {
        Logger.i("TtsServiceController: 播放")
        if (!stateHolder.canPlay()) {
            Logger.w("当前无法播放")
            return
        }
        sendCommand(context, ACTION_PLAY)
        stateHolder.startPlaying()
    }


    @OptIn(UnstableApi::class)
    override fun stop(context: Context) {
        Logger.i("TtsServiceController: 停止")
        sendCommand(context, ACTION_STOP)
        stateHolder.stopPlaying()
    }

    @OptIn(UnstableApi::class)
    override fun setSpeakStartChapterAndPage(
        context: Context,
        chapter: TextChapter?,
        page: TextPage?,
        bookTitle: String,
        chapterTitle: String,
        bookCover: String?,
        chapterSize: Int
    ): Boolean {
        Logger.i("TtsServiceController: 设置播放章节和页面")
        if (chapter == null) {
            Logger.e("章节不能为空")
            stateHolder.reportError(TtsError.ChapterLoadFailed(-1), context)
            return false
        }

        val language = stateHolder.state.value.language
        val (initStartSentenceIndex, totalSentences) = TtsPlaybackService.calcSpeakSentences(chapter, page, language) ?: return false
        Logger.i("${this.javaClass.name}:setSpeakStartChapterAndPage:totalSentences.size=${totalSentences.size},initStartSentenceIndex=$initStartSentenceIndex")
        if (totalSentences.isEmpty()) {
            Logger.e("设置播放数据失败: 句子列表为空")
            return false
        }
        // 更新状态
        stateHolder.update { it.copy(
            speakingSentences = totalSentences,
            currentSentenceIndex = initStartSentenceIndex,
            currentChapterIndex = chapter.position,
            bookTitle = bookTitle,
            chapterTitle = chapterTitle,
            bookCover = bookCover,
            chapterSize = chapterSize
        ) }

        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = ACTION_SET_SPEAK_DATA
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: Exception) {
            Logger.e("设置播放数据失败: ${e.message}")
            stateHolder.reportError(TtsError.ChapterLoadFailed(chapter.position), context)
            false
        }
    }

    @OptIn(UnstableApi::class)
    override fun setSpeed(context: Context, speed: Float) {
        setSpeed(context, speed) { success ->
            if (!success) {
                Logger.w("设置语速失败")
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun setSpeed(context: Context, speed: Float, onComplete: (Boolean) -> Unit) {
        Logger.i("TtsServiceController: 设置语速: $speed")
        // 验证参数
        val validSpeed = speed.coerceIn(0.25f, 4.0f)
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            action = ACTION_SET_SPEED
            putExtra(EXTRA_SPEED, validSpeed)
        }
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)

                } else {
                    context.startService(intent)
                }
                // 立即更新状态（乐观更新）
                stateHolder.update { it.copy(speed = validSpeed) }
                onComplete(true)
            } catch (e: Exception) {
                Logger.e("设置语速失败: ${e.message}")
                onComplete(false)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun setPitch(context: Context, pitch: Float) {
        setPitch(context, pitch) {
            if (!it) {
                Logger.w("设置语速失败")
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun setPitch(
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
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                // 立即更新状态（乐观更新）
                stateHolder.update { it.copy(pitch = validPitch) }
                onComplete(true)
            } catch (e: Exception) {
                Logger.e("设置语速失败: ${e.message}")
                onComplete(false)
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun getSupportedLanguage(context: Context) : Set<Locale> {
        Logger.i("TtsServiceController: 获取支持的语言")
        return try {
            TtsPlaybackService.getSupportedTtsLanguage(context)
        } catch (e: Exception) {
            Logger.e("获取支持的语言失败: ${e.message}")
            setOf(Locale.getDefault())
        }
    }

    override fun isServiceRunning(context: Context): Boolean {
        return stateHolder.state.value.serviceBound ||
                stateHolder.state.value.serviceStarting
    }

    @OptIn(UnstableApi::class)
    override fun setLanguage(context: Context, newlocale: Locale): Boolean {
        Logger.i("${this.javaClass.name}:setLanguage(newLocale=$newlocale)")
        setLanguage(context, newlocale) {
            if (!it) {
                Logger.w("setLanguage failed")
            }
        }
        return true
    }

    @OptIn(UnstableApi::class)
    override fun setPlayTime(context: Context, playTime: Float) {
        Logger.i("${this.javaClass.name}:setPlayTime(playTime=$playTime")
        // 立即更新状态（乐观更新）
        stateHolder.update { it.copy(
            timeStart = if (playTime > 0) System.currentTimeMillis() else 0L,
            timeDuration = (playTime * 3600).toLong() * 1000L) }
    }

    @OptIn(UnstableApi::class)
    override fun setLanguage(
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
        scope.launch {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                // 立即更新状态（乐观更新）
                stateHolder.update { it.copy(language = newlocale) }
                onComplete(true)
            } catch (e: Exception) {
                Logger.e("设置语言失败: ${e.message}")
                onComplete(false)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun sendCommand(context: Context, action: String) {
        val intent = Intent(context, TtsPlaybackService::class.java).apply {
            this.action = action
        }
        // ... 启动服务
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Logger.e("发送命令失败 [$action]: ${e.message}")
            stateHolder.reportError(TtsError.ServiceNotStarted, context)
        }
    }
}