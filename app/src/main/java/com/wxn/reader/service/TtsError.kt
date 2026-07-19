package com.wxn.reader.service

import android.content.Context
import com.wxn.base.util.Logger
import com.wxn.reader.R
import java.util.Locale


/**
 * TTS错误类型，使用密封类穷举所有可能错误
 * 便于when表达式处理
 */
sealed class TtsError(
    val code: Int,
    val recoverable: Boolean = false
) {
    // 初始化错误
    data object ServiceNotStarted : TtsError(1001)
    data object EngineNotReady : TtsError(1002, true)

    // 播放错误
    data class PlaybackFailed(
        val sentence: String? = null,
        val reason: String? = null
    ) : TtsError(2001, true)

    // 语言错误
    data class LanguageNotSupported(
        val locale: Locale,
        val available: Set<Locale> = emptySet()
    ) : TtsError(3001, true)

    // 章节加载错误
    data class ChapterLoadFailed(
        val chapterIndex: Int,
        val bookId: Long? = null
    ) : TtsError(4001)

    // 网络错误（如果使用在线TTS）
    data class NetworkError(
        val url: String? = null
    ) : TtsError(5001, true)
}

/**
 * 获取本地化错误消息
 */
fun TtsError.toLocalizedString(context: Context): String {
    return when (this) {
        is TtsError.ServiceNotStarted ->
            context.getString(R.string.tts_error_service_not_started)
        is TtsError.EngineNotReady ->
            context.getString(R.string.tts_error_engine_not_ready)
        is TtsError.ChapterLoadFailed ->
            context.getString(R.string.tts_error_chapter_load_failed, chapterIndex)
        is TtsError.LanguageNotSupported ->
            context.getString(R.string.tts_error_language_not_supported, locale.displayName)
        is TtsError.PlaybackFailed -> {
            val reasonText = reason ?: context.getString(R.string.tts_error_unknown_reason)
            context.getString(R.string.tts_error_playback_failed, reasonText)
        }
        is TtsError.NetworkError ->
            context.getString(R.string.tts_error_network_error)
    }
}

// 扩展函数：错误处理
fun TtsError.logError(context: Context) {
    Logger.e("TTS Error[$code]: ${toLocalizedString(context)}")
}