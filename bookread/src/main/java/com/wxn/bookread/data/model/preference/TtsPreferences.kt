package com.wxn.bookread.data.model.preference

import com.wxn.base.bean.TTSEngineType

data class TtsPreferences constructor(
    val localeCode: String,
    val speed: Float,
    val pitch: Float,

    val ttsEngineType: TTSEngineType = TTSEngineType.SYSTEM,
    val selectedTTSModel: String? = null,      // 模型名称，仅 AI TTS 使用
    val selectedSpeaker: Int = 0,          // 说话人 ID，仅 AI TTS 使用
    val isFirstAiTtsSelection: Boolean = true,
) {

}