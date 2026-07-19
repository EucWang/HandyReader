package com.wxn.reader.service

enum class TtsEngineStatus {


    IDLE,           // 未初始化（默认状态）
    INITIALIZING,   // 初始化中
    READY,          // 初始化完成，可以播放
    NEED_MODEL,     // 需要配置模型
    FAILED;         // 初始化失败

    val isReady: Boolean
        get() = this == READY

    val isError: Boolean
        get() = this == FAILED

    val isPending: Boolean
        get() = this == INITIALIZING


}