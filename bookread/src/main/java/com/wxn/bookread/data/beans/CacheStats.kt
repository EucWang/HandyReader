package com.wxn.bookread.data.beans

/**
 * 缓存统计数据
 */
data class CacheStats(
    val hitCount: Int,
    val missCount: Int,
    val hitRate: Float,
    val memoryUsage: Int,
    val maxSize: Int
)