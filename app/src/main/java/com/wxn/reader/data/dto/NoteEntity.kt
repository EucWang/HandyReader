package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val locator: String,
    val selectedText: String,
    val note: String,
    val color: String,
    val bookId: Long,
    val createdAt: Long? = null,

    // ===== ★ 同步方案 v2.6 §2.7.3 一期新增(uuid + deleted + 3 HLC)=====
    /** 跨设备稳定 UUID,运行时 `UUID.randomUUID().toString()` 生成;Migration 回填历史行。 */
    val uuid: String? = null,
    /** 是否已软删(墓碑),合并时按 LWW 传播。 */
    val deleted: Boolean = false,
    // 更新事件 HLC
    val syncHlcL: Long = 0L,
    val syncHlcC: Int = 0,
    val syncHlcDevice: String = "",
    // 删除事件 HLC(独立跟踪,与更新分开)
    val deletedHlcL: Long = 0L,
    val deletedHlcC: Int = 0,
    val deletedHlcDevice: String = "",
)