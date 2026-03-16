package com.wxn.reader.service

import com.wxn.base.bean.Locator
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.SpeakSentence
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局TTS状态管理器（单例）
 * 使用Hilt注入，确保生命周期正确
 */
@Singleton
class TtsStateHolder @Inject constructor(
    private val preferencesUtil: TtsPreferencesUtil
) {
    // 私有状态，通过StateFlow暴露只读版本
    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    // 组合流：状态 + 偏好设置
    val combinedState: StateFlow<TtsState> = combine(
        _state,
        preferencesUtil.ttsPreferencesFlow
    ) { state, preferences ->
        state.copy(
            speed = preferences.speed,
            pitch = preferences.pitch,
            language = Locale.forLanguageTag(preferences.localeCode)
        )
    }.stateIn(
        scope = Coroutines.scope(),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TtsState()
    )

    private val scope = Coroutines.scope()

    init {
        // 监听偏好设置变化并自动更新状态
        preferencesUtil.ttsPreferencesFlow
            .distinctUntilChanged()
            .onEach { preferences ->
                update { current ->
                    current.copy(
                        speed = preferences.speed,
                        pitch = preferences.pitch,
                        language = Locale.forLanguageTag(preferences.localeCode)
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
        update { it.copy(
            isPlaying = true,
            isPaused = false,
            currentLocator = locator ?: it.currentLocator,
            error = null
        ) }
    }

    /**
     * 暂停播放
     */
    fun pausePlaying() {
        update { it.copy(
            isPlaying = true,
            isPaused = true,
            error = null
        ) }
    }

    /**
     * 停止播放
     */
    fun stopPlaying() {
        update { it.copy(
            isPlaying = false,
            isPaused = false,
            currentLocator = null,
            currentSentenceIndex = 0,
            speakingSentences = emptyList(),
            currentChapterIndex = 0,
            bookTitle = "",
            chapterTitle = "",
            bookCover = null,
            error = null,
            chapterSize = 0,
            timeStart = 0,
            timeDuration = 0
        ) }
    }

    /**
     * 更新播放进度
     */
    fun updateProgress(locator: Locator, sentenceIndex: Int) {
        update { it.copy(
            currentLocator = locator,
            currentSentenceIndex = sentenceIndex
        ) }
    }

    /**
     * 报告错误
     */
    fun reportError(error: TtsError, context: android.content.Context) {
        update { it.copy(
            error = error,
            isPlaying = false,
            isPaused = false
        ) }

        // 自动记录错误
        error.logError(context)
    }

    /**
     * 清除错误
     */
    fun clearError() {
        update { it.copy(error = null) }
    }

    /**
     * 更新服务状态
     */
    fun updateServiceState(bound: Boolean, starting: Boolean = false) {
        update { it.copy(
            serviceBound = bound,
            serviceStarting = starting
        ) }
    }

    // === 查询方法 ===

    /**
     * 当前是否可播放
     */
    fun canPlay(): Boolean {
        val current = _state.value
        val ret = current.serviceStarting &&
                current.speakingSentences.isNotEmpty() &&
                current.currentSentenceIndex >= 0 &&
                current.currentSentenceIndex < current.speakingSentences.size &&
                current.currentChapterIndex >= 0 &&
                current.error == null &&
                current.chapterSize > 0
        if (!ret) {
            Logger.w("${this.javaClass.name}:canPlay:serviceStarting=${current.serviceStarting}, speakingSentences.size=${current.speakingSentences.size},currentSentenceIndex=${current.currentSentenceIndex},error=${current.error}")
        }
        return ret
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
}

