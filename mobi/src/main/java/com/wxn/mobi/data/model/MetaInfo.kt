package com.wxn.mobi.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MetaInfo(
    val title: String = "",
    val author: String = "",
    val contributor: String = "",
    val subject: String = "",
    val publisher: String = "",
    val date: String = "",
    val description: String = "",
    val review: String = "",
    val imprint: String = "",
    val copyright: String = "",
    val isbn: String = "",
    val asin: String = "",
    val language: String = "",
    val isEncrypted: Boolean = false,
    val coverPath: String = "",
    val crc: Int = 0,
    // ★ 2026-07-07 方案 A+:native 层 compute_file_crc_and_hash 同一次 IO 算出,
    //   供上层 FileParserImpl 直接写入 Book.contentHash,避免 Java 层二次全文件读算 SHA-256。
    //   详见 docs/plans/2026-07-07-扫描导入同书去重.md §四-A+
    val contentHash: String = ""
)
