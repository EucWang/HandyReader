package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/**
 * Canonical 书签 Record。
 *
 * ★ v1.4 严重-F3 + v1.5 C5(b):本期【新增类型】(非 v2.6 §2.4 复用)。
 *   合并算法与 [CanonicalAnnotation] 完全同构(uuid 并集 + LWW + 墓碑)。
 *
 * ★ 同步方案 v2.6 §2.4 / 一期 §3.3 mergeBookmarks。
 */
@Serializable
data class CanonicalBookmark(
    override val uuid: String,
    override val hlc: HlcTs,
    override val deleted: Boolean,
    override val schemaVersion: Int = 2,
    val locator: String,
    val chapterIndex: Int,
    val title: String? = null,
    val color: String? = null,
    val dateAndTime: Long = 0L,
) : SyncRecord
