package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "shelves",
    indices = [Index(value = ["uuid"], unique = true)]
)
data class ShelfEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val order: Int,

    // ===== ★ 同步方案 v2.6 §2.7.3 一期新增(uuid + deleted + 3 HLC)=====
    /** 跨设备稳定 UUID。 */
    val uuid: String? = null,
    /** 是否已软删。 */
    val deleted: Boolean = false,
    val syncHlcL: Long = 0L,
    val syncHlcC: Int = 0,
    val syncHlcDevice: String = "",
    val deletedHlcL: Long = 0L,
    val deletedHlcC: Int = 0,
    val deletedHlcDevice: String = "",
)