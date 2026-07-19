package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/**
 * 书籍稳定身份标识(跨设备匹配用)。
 *
 * stableId 由 [fileType] + [contentHash] 派生([com.wxn.reader.data.backup.StableIdResolver]),
 * 是 ZIP 内 books/{stableId}.json 文件名来源,也是跨设备匹配的主键。
 *
 * ★ 同步方案 v2.6 §2.2.1 / 一期 §2.1。
 */
@Serializable
data class BookIdentity(
    val contentHash: String,
    val fileSize: Long,
    val partialMd5: String? = null,
    /** ★ schema 4:书籍文件 CRC32,用于 orphan 提升去重(导入侧已同步算好,零额外成本)。 */
    val crc: Int = 0,
    val fileType: String,
    val title: String,
    val authors: String,
) {
    val stableId: String get() = "$fileType:$contentHash"
}
