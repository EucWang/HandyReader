package com.wxn.reader.service

import com.wxn.base.bean.Locator
import com.wxn.bookread.data.model.SpeakSentence
import java.util.Locale


/**
 * TTS全局状态，使用不可变数据类
 * 所有属性都有默认值，避免null检查
 */
data class TtsState(
    // 播放控制状态
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,

    // 播放配置
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val language: Locale = Locale.getDefault(),

    // 播放进度
    val currentLocator: Locator? = null,
    val currentSentenceIndex: Int = 0,
    val currentChapterIndex: Int = 0,

    val speakingSentences: List<SpeakSentence> = emptyList(),

    // 通知显示信息
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val bookCover: String? = null,

    val chapterSize: Int = 0,

    // 错误信息
    val error: TtsError? = null,

    // 服务状态
    val serviceBound: Boolean = false,
    val serviceStarting: Boolean = false,

    //TTS 播放时长限制
    val timeStart: Long = 0, //开始计时
    val timeDuration : Long = 0 //开始计时后多长时间结束
) {
    // 计算属性：综合播放状态
    val canSkipNext: Boolean get() = isPlaying && !isPaused && currentSentenceIndex < speakingSentences.size - 1
    val canSkipPrev: Boolean get() = isPlaying && !isPaused && currentSentenceIndex > 0
}