package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "book_shelf",
    primaryKeys = ["bookId", "shelfId"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ShelfEntity::class,
            parentColumns = ["id"],
            childColumns = ["shelfId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["bookId"]),
        Index(value = ["shelfId"]),
        Index(value = ["uuid"], unique = true)
    ]
)
data class BookShelfEntity(
    val bookId: Long,
    val shelfId: Long,

    // ===== ★ 同步方案 v2.6 §2.7.3 / Q12 改回软删:uuid + deleted + 3 HLC =====
    /** 跨设备稳定 UUID。 */
    val uuid: String? = null,
    /** 是否已软删(Q12 改回软删列,与 shelves 表一致)。 */
    val deleted: Boolean = false,
    val syncHlcL: Long = 0L,
    val syncHlcC: Int = 0,
    val syncHlcDevice: String = "",
    val deletedHlcL: Long = 0L,
    val deletedHlcC: Int = 0,
    val deletedHlcDevice: String = "",
)