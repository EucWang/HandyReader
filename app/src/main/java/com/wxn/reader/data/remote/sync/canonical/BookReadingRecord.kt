package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/**
 * 阅读进度档:locator/progression/scrollIndex 等。
 *
 * 合并语义:progression → hlc → deviceId 决胜;locator 同源;readingTime 文本类 per-device SUM / 有声类跟随 progression。
 *
 * ★ 同步方案 v2.6 §2.5.3 / §6.4.2 mergeReading。
 */
@Serializable
data class BookReadingRecord(
    val locator: String,
    val progression: Float = 0f,
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
    /** 累计阅读时长(本地派生自 book_reading_time 的 SUM)。 */
    val readingTime: Long = 0L,
    val lastOpened: Long? = null,
    val startReadingDate: Long? = null,
    val endReadingDate: Long? = null,
    val hlc: HlcTs,
) {
    val schemaVersion: Int get() = 2
}
