package com.wxn.reader.service

enum class TtsServiceStatus {
    IDLE,           // 未启动
    STARTING,       // 已发送启动Intent
    CONNECTED,      // 收到服务反馈或超时后假设已连接
    FAILED,         // 启动失败
    DISCONNECTED,   // 服务已停止
    TIMEOUT;         // 启动超时


    /**
     * 是否为可用状态
     */
    val isAvailable: Boolean
        get() = this == CONNECTED

    /**
     * 是否为失败状态
     */
    val isError: Boolean
        get() = this == FAILED ||
                this == DISCONNECTED ||
                this == TIMEOUT

    /**
     * 是否为进行中状态
     */
    val isPending: Boolean
        get() = this == STARTING

    /**
     * 获取对应的错误信息（如果是错误状态）
     */
    fun toTtsError(): TtsError? = when (this) {
        FAILED -> TtsError.ServiceNotStarted
        DISCONNECTED -> TtsError.ServiceNotStarted
        TIMEOUT -> TtsError.ServiceNotStarted
        else -> null
    }


}