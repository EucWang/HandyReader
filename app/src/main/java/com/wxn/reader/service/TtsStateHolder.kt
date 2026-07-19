package com.wxn.reader.service

import com.wxn.base.bean.Locator
import com.wxn.base.bean.SpeakSentence
import com.wxn.base.bean.TTSEngineType
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.base.bean.TtsPlaybackStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * 全局TTS状态管理器（单例）
 * 使用Hilt注入，确保生命周期正确
 */
@Singleton
class TtsStateHolder @Inject constructor(
    private val preferencesUtil: TtsPreferencesUtil
) {

    private val scope = Coroutines.scope()

    // 私有状态，通过StateFlow暴露只读版本
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    init {
        // 监听偏好设置变化并自动更新状态
        preferencesUtil.ttsPreferencesFlow
            .distinctUntilChanged()
            .onEach { preferences ->
                update { current ->
                    val changedEngineModel = (preferences.selectedTTSModel != current.engineModel)
                    val newStatus =
                        if (current.ttsEngineStatus == TtsEngineStatus.READY && changedEngineModel) {
                            TtsEngineStatus.IDLE   //已经初始化了,但是改变了engineModel
                        } else {
                            current.ttsEngineStatus
                        }

                    current.copy(
                        speed = preferences.speed,
                        pitch = preferences.pitch,
                        language = Locale.forLanguageTag(preferences.localeCode),
                        engineType = if (preferences.ttsEngineType == TTSEngineType.SYSTEM) 0 else 1,
                        engineModel = preferences.selectedTTSModel.orEmpty(),
                        modelSpeaker = preferences.selectedSpeaker,
                        ttsEngineStatus = newStatus
                    )
                }
            }
            .launchIn(scope)
    }

    // === 状态更新方法 ===

    /**
     * 线程安全的原子更新
     */
    fun update(transform: (TtsState) -> TtsState) {
        _state.update(transform)
    }

    /**
     * 开始播放
     */
    fun startPlaying(locator: Locator? = null) {
        update {
            it.copy(
                ttsPlayerStatus = TtsPlaybackStatus.PENDING_PLAYING,
                currentLocator = locator ?: it.currentLocator,
                error = null
            )
        }
    }

    /**
     * 执行 暂停播放
     */
    fun pausePlaying() {
        update {
            it.copy(
                ttsPlayerStatus = TtsPlaybackStatus.PENDING_PAUSE,
                error = null
            )
        }
    }

    fun pausedPlay() {
        update {
            it.copy(
                ttsPlayerStatus = TtsPlaybackStatus.PAUSED,
                error = null
            )
        }
    }

    /**
     * 停止播放
     */
    fun stopPlaying() {
        Logger.i("TtsStateHolder: stopPlaying:reset ttsPlayerStatus")
        update {
            it.copy(
                ttsPlayerStatus = TtsPlaybackStatus.IDLE,
                currentLocator = null,
                currentSentenceIndex = 0,
                speakingSentences = emptyList(),
                currentChapterIndex = 0,
                bookTitle = "",
                chapterTitle = "",
                bookCover = null,
                bookUri = null,
                error = null,
                chapterSize = 0,
                timePlayedMs = 0,
                timeDuration = 0,
                playSessionStartMs = 0
            )
        }
    }

    /**
     * 更新播放进度, 并且设置播放状态为播放中
     */
    fun updateProgress(locator: Locator, sentenceIndex: Int) {
        update {
            it.copy(
                currentLocator = locator,
                currentSentenceIndex = sentenceIndex,
                ttsPlayerStatus = TtsPlaybackStatus.PLAYING
            )
        }
    }

    fun updateLanguages(locales: Set<Locale>) {
        update {
            it.copy(
                supportedLanguages = locales
            )
        }
    }

    /**
     * 报告错误
     */
    fun reportError(error: TtsError, context: android.content.Context) {
        update {
            it.copy(
                error = error,
                ttsPlayerStatus = TtsPlaybackStatus.PAUSED
            )
        }

        // 自动记录错误
        error.logError(context)
    }

    /**
     * 清除错误
     */
    fun clearError() {
        update { it.copy(error = null) }
    }

    fun updateEngineInitState(engineStatus: TtsEngineStatus) {
        update {
            it.copy(
                ttsEngineStatus = engineStatus,
                ttsPlayerStatus = if (engineStatus == TtsEngineStatus.INITIALIZING) TtsPlaybackStatus.PENDING_PLAYING else it.ttsPlayerStatus
            )
        }
    }

    /**
     * 获取当前播放位置
     */
    fun getCurrentPosition(): Pair<Locator?, Int> {
        val current = _state.value
        return current.currentLocator to current.currentSentenceIndex
    }

    fun getSentences(): List<SpeakSentence> {
        val current = _state.value
        return current.speakingSentences
    }

    fun getServiceStatus(): TtsServiceStatus {
        return this.state.value.serviceStatus
    }

    fun updateServiceStatus(serviceStatus: TtsServiceStatus, error: TtsError? = null) {
        Logger.d("TtsStateHolder: 服务状态更新: $serviceStatus, error: $error")
        update {
            it.copy(
                serviceStatus = serviceStatus,
                error = when {
                    error != null -> {
                        Logger.w("TtsStateHolder: 服务错误: $error")
                        error
                    }

                    serviceStatus.isError -> {
                        // 错误状态必须有错误信息
                        val defaultError = serviceStatus.toTtsError()
                        Logger.w("TtsStateHolder: 服务错误(默认): $defaultError")
                        defaultError
                    }

                    else -> {
                        // 正常状态，清除旧错误
                        null
                    }
                }
            )
        }

        if (serviceStatus == TtsServiceStatus.STARTING && error == null) {
            val oldDeferred = servDeferredRef.get()
            // 尝试取消旧的初始化（如果还在进行中）
            if (!oldDeferred.isCompleted) {
                oldDeferred.completeExceptionally(
                    CancellationException("Service starting")
                )
            }
            val newDeferred = CompletableDeferred<Unit>()
            servDeferredRef.set(newDeferred)
        } else if (serviceStatus == TtsServiceStatus.DISCONNECTED ||
            serviceStatus == TtsServiceStatus.FAILED
        ) {
            servDeferredRef.get().completeExceptionally(
                RuntimeException("TTS service connect failed")
            )
        } else if (serviceStatus == TtsServiceStatus.CONNECTED) {
            servDeferredRef.get().complete(Unit)
        }
    }

    /**
     * 等待服务连接完成
     * @param timeoutMs 超时时间（毫秒），默认5秒
     * @return 是否成功连接且引擎已初始化
     */
    suspend fun waitForServiceConnected(timeoutMs: Long = 5000L): Boolean {
        val currentStatus = _state.value.serviceStatus
        return when (currentStatus) {
            TtsServiceStatus.CONNECTED -> {
                Logger.d("TtsStateHolder: 服务已连接")
                true
            }

            TtsServiceStatus.DISCONNECTED -> {
                Logger.d("TtsStateHolder: 服务未连接")
                false
            }

            TtsServiceStatus.TIMEOUT -> {
                Logger.d("TtsStateHolder: 服务连接超时")
                false
            }

            TtsServiceStatus.IDLE -> {
                Logger.d("TtsStateHolder: 服务处于空闲状态")
                false
            }

            TtsServiceStatus.FAILED -> {
                Logger.d("TtsStateHolder: 服务连接失败")
                false
            }

            TtsServiceStatus.STARTING -> {
                Logger.d("TtsStateHolder: 服务连接中..等待连接..")
                val start = System.currentTimeMillis()
                try {
                    waitForConnected()
                    Logger.d("TtsStateHolder: 服务连接中..连接成功:[${System.currentTimeMillis() - start}ms]")
                    true
                } catch (ex: Exception) {
                    Logger.d("TtsStateHolder: 服务连接中..连接失败:[${System.currentTimeMillis() - start}ms]: ${ex.message}")
                    false
                }
            }
        }
    }

    private val servDeferredRef = AtomicReference(CompletableDeferred<Unit>())

    private suspend fun waitForConnected(timeoutMs: Long = 5000L) {
        val deferred = servDeferredRef.get()
        try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            Logger.e("TTS initialization timeout after ${timeoutMs}ms")
            throw RuntimeException("TTS initialization timeout", e)
        } catch (e: CancellationException) {
            Logger.w("TTS initialization cancelled: ${e.message}")
            throw e
        }
    }

    /***
     * 设置当前播放会话的起始时间戳
     */
    fun startTimerSession() {
        update {
            if (it.timeDuration > 0 && it.playSessionStartMs == 0L) {
                it.copy(playSessionStartMs = System.currentTimeMillis())
            } else {
                it
            }
        }
    }

    /****
     * 暂停时, 起始播放会话时间戳,来计算本次的播放时长
     * 并重置起始播放会话时间戳
     */
    fun pauseTimerSession() {
        update {
            if (it.playSessionStartMs > 0) {
                val elapsed = it.timePlayedMs + (System.currentTimeMillis() - it.playSessionStartMs)
                it.copy(
                    timePlayedMs = elapsed,
                    playSessionStartMs = 0L
                )
            } else {
                it
            }
        }
    }

    /****
     * 重置 起始播放会话时间戳 以及 累计播放时间
     */
    fun expiredTimer() {
        update {
            it.copy(
                timeDuration = 0,
                timePlayedMs = 0L,
                playSessionStartMs = 0L,
                timerExpired = true
            )
        }
    }

    /****
     * 重置 起始播放会话时间戳 以及 累计播放时间
     */
    fun resetTimerElapsed() {
        update {
            it.copy(
                timeDuration = 0,
                timePlayedMs = 0L,
                playSessionStartMs = 0L,
                timerExpired = false
            )
        }
    }
}