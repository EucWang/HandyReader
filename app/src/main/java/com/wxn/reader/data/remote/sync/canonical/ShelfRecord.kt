package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/**
 * 书架 Record。
 *
 * 合并语义:uuid 并集 + LWW;Shelf.order 不传(Q7:跨设备 order 无意义,各设备自管顺序)。
 *
 * ★ 同步方案 v2.6 §2.6.1 / 一期 §3.3 mergeShelf。
 */
@Serializable
data class ShelfRecord(
    override val uuid: String,
    override val hlc: HlcTs,
    override val deleted: Boolean,
    override val schemaVersion: Int = 2,
    val name: String,
) : SyncRecord
