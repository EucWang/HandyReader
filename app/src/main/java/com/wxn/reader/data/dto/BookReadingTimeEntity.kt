package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * book_reading_time 表:per-book × per-device 的精确阅读时长(毫秒)。
 *
 * 设计目的:`books.readingTime` 是单值,跨设备合并时只能取"最大值"或"求和",易重复累计。
 * 拆为 per-device 行后,合并引擎对"同 deviceId"取最新、"跨 deviceId"求和,语义精确无重复。
 * `books.readingTime` 派生自 `SELECT SUM(readingTimeMs) FROM book_reading_time WHERE bookId = ?`,
 * 由 [com.wxn.reader.data.repository.BooksRepositoryImpl.refreshReadingTimeFromPerDevice] 维护。
 *
 * ★ 同步方案文档 v2.6 §6.4.2.2;一期 P0 必做(合并引擎依赖)。
 */
@Entity(
    tableName = "book_reading_time",
    primaryKeys = ["bookId", "deviceId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["deviceId"])
    ]
)
data class BookReadingTimeEntity(
    val bookId: Long,
    val deviceId: String,
    val readingTimeMs: Long = 0L,
    val lastUpdated: Long = 0L,
)
