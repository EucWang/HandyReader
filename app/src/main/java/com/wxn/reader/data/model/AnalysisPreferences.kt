package com.wxn.reader.data.model

/**
 * 用户行为分析相关偏好（与通用 AppPreferences 隔离，便于后续扩展）。
 */
data class AnalysisPreferences(
    /**
     * 首次启动应用的时间戳（epoch 毫秒）。永不持久化为 0L。
     * - 新用户：System.currentTimeMillis()（用户首次打开应用时刻）
     * - 老用户升级：PackageInfo.firstInstallTime（系统记录的首次安装时间）
     * - 未初始化（瞬时状态）：每次访问返回当前时间（仅用于 Flow 兜底，不持久化）
     */
    val firstLaunchTimestamp: Long = System.currentTimeMillis()
)
