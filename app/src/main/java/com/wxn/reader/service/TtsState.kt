package com.wxn.reader.service

import com.wxn.base.bean.Locator
import com.wxn.base.bean.SpeakSentence
import com.wxn.base.bean.TtsPlaybackStatus
import java.util.Locale

/**
 * TTS全局状态，使用不可变数据类
 * 所有属性都有默认值，避免null检查
 */
data class TtsState(
    // 播放控制状态
    val ttsEngineStatus: TtsEngineStatus = TtsEngineStatus.IDLE,

    val ttsPlayerStatus: TtsPlaybackStatus = TtsPlaybackStatus.IDLE,

    // 播放配置
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val language: Locale = Locale.getDefault(),

    val engineType: Int = 0,
    val engineModel: String = "",
    val modelType: String = "",
    val baseModelDatas: List<Triple<String, String, String>> = emptyList(),

    val speakerNum: Int = 0,
    val modelSpeaker: Int = 0,

    // 播放进度
    val currentLocator: Locator? = null,
    val currentSentenceIndex: Int = 0,
    val currentChapterIndex: Int = 0,

    val speakingSentences: List<SpeakSentence> = emptyList(),

    // 通知显示信息
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val bookCover: String? = null,
    val bookLocale: Locale? = null,
    val bookUri: String? = null,

    val chapterSize: Int = 0,

    // 错误信息
    val error: TtsError? = null,

    // 服务状态
//    val serviceBound: Boolean = false,
//    val serviceStarting: Boolean = false,
    val serviceStatus: TtsServiceStatus = TtsServiceStatus.IDLE,

    //TTS 播放时长限制
    val playSessionStartMs: Long = 0, //当前播放会话的起始时间戳(epoch ms), 0 标识未开始
    val timeDuration : Long = 0, //用户设置的播放时长上限,
    val timePlayedMs: Long = 0, //累计已经播放时间(ms),暂停时累计,恢复时继续
    val timerExpired: Boolean = false, //计时器倒计时结束时,设置未true,通知界面显示通知

    //系统默认支持的语言
    val supportedLanguages: Set<Locale> = emptySet()
) {
    // 计算属性：综合播放状态
    val canSkipNext: Boolean get() = isPlaying && currentSentenceIndex < speakingSentences.size - 1
    val canSkipPrev: Boolean get() = isPlaying && currentSentenceIndex > 0
    val isServiceAvailable: Boolean
        get() = serviceStatus.isAvailable

    val isEngineAvailable: Boolean
        get() = ttsEngineStatus.isReady

    val isPlaying: Boolean
        get() = ttsPlayerStatus.isPlaying
}