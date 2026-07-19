package com.wxn.base.bean

enum class TtsPlaybackStatus {

    IDLE,             // 未播放/已停止（默认）
    PENDING_PLAYING,  // 准备启动播放（过渡状态，UI显示loading）
    PLAYING,          // 正在播放
    PENDING_PAUSE,    // 正在暂停（过渡状态，UI显示loading）
    PAUSED;           // 已暂停

    val isActive: Boolean
        get() = this == PLAYING

    val isPending: Boolean
        get() = this == PENDING_PLAYING || this == PENDING_PAUSE

    val isPlaying: Boolean
        get() = this == PLAYING

    val isPaused: Boolean
        get() = this == PAUSED
}