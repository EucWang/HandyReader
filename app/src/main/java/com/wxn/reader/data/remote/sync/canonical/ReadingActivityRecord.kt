package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/**
 * 当日阅读活动 Record。
 *
 * 复合 PK (date, deviceId):同 date+deviceId 取最新(LWW),跨 deviceId 求和。
 *
 * ★ 同步方案 v2.6 §2.6.2 / 一期 §3.3.1 mergeReadingActivities。
 *   本机还原(forceOverwrite=true)时【保留今天数据】(§3.3.1 一般-E)。
 */
@Serializable
data class ReadingActivityRecord(
    val date: Long,
    val deviceId: String,
    val readingTime: Long,
) {
    val schemaVersion: Int get() = 2
}
