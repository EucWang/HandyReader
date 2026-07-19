package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_vocabulary",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["word", "lang"]),
        Index(value = ["createdAt"]),
        Index(value = ["status"]),
        Index(
            value = ["bookId", "word", "lang", "chapterIndex",
                     "startParagraphIndex", "startTextOffset"],
            unique = true
        ),
        Index(value = ["uuid"], unique = true)
    ]
)
data class BookVocabularyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val lang: String,
    val word: String,
    val status: Int = 0,
    val sentenceText: String,
    val chapterIndex: Int = 0,
    val startParagraphIndex: Int = 0,
    val startTextOffset: Int = 0,
    val locator: String,
    val createdAt: Long = System.currentTimeMillis(),

    // ===== ★ 同步方案 v2.6 §2.7.3 + v1.3 严重-4 一期新增 =====
    // ★ v1.3 严重-4:不加 `deleted` 列,保留既有 `status=-1` 软删(禁止两套软删并存)。
    //   合并引擎 Mapper 负责 `status=-1 ↔ deleted=true` + `deletedHlc* ↔ record.hlc` 双向转换。
    /** 跨设备稳定 UUID。 */
    val uuid: String? = null,
    // 更新事件 HLC(active 行)
    val syncHlcL: Long = 0L,
    val syncHlcC: Int = 0,
    val syncHlcDevice: String = "",
    // 删除事件 HLC(status=-1 行),独立跟踪删除时刻
    val deletedHlcL: Long = 0L,
    val deletedHlcC: Int = 0,
    val deletedHlcDevice: String = "",
)