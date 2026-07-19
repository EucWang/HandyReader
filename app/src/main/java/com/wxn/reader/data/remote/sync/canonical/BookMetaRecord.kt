package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/**
 * 书籍元数据档:标题/作者/封面/语言等"非用户行为"信息。
 *
 * 合并语义:per-field LWW + 非空优先(空值不覆盖非空,见 mergeMeta)。
 *
 * ★ 同步方案 v2.6 §2.5.3 / 一期 §3.3 mergeMeta。
 */
@Serializable
data class BookMetaRecord(
    val identity: BookIdentity,
    val title: String,
    val authors: String,
    val description: String? = null,
    val publishDate: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val numberOfPages: Int? = null,
    val wordCount: Long = 0L,
    val subjects: String? = null,
    val coverPath: String? = null,
    val duration: Long? = null,
    val narrator: String? = null,
    val hlc: HlcTs,
    val deleted: Boolean = false,
) {
    val schemaVersion: Int get() = 2
}

/** HlcTimestamp 在 app/remote/sync 包内的别名引用,避免每个文件都 import base 路径。 */
typealias HlcTs = com.wxn.base.bean.sync.HlcTimestamp
