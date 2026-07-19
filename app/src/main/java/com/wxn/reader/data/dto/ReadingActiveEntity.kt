package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.Index

/**
 * reading_activities 表(★ 同步方案 v2.6 §2.6.2 + 一期 v8→v9 重建)。
 *
 * v8 之前:单列 PK `date`,无 deviceId。
 * v9 起:复合 PK `(date, deviceId)`,支持多设备阅读时长合并(同 date+deviceId 取最新,跨 deviceId 求和)。
 * Migration_8_9 重建表并回填老行 `deviceId = 本机 UUID`(§2.2.4)。
 */
@Entity(
    tableName = "reading_activities",
    primaryKeys = ["date", "deviceId"],
    indices = [Index(value = ["deviceId"])]
)
data class ReadingActiveEntity(
    /** 当天 0 点时间戳(本机时区),毫秒。 */
    val date: Long,
    /** 本条记录所属设备 UUID。 */
    val deviceId: String,
    /** 当天累计阅读时长,毫秒。 */
    val readingTime: Long,
)