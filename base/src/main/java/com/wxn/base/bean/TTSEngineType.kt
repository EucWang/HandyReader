package com.wxn.base.bean

enum class TTSEngineType {

    SYSTEM,           // 系统默认 TTS
    OFFLINE_NEURAL_AI; // Offline Neural AI TTS (sherpa-onnx)


    companion object {
        fun fromString(value: String?): TTSEngineType {
            return values().find { it.name == value } ?: SYSTEM
        }
    }
}