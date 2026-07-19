package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/**
 * Canonical 笔记 Record(用户在选中文字旁加的文字批注)。
 *
 * ★ v1.4 严重-F3 + v1.5 C5(b):本期【新增类型】(非 v2.6 §2.4 复用)。
 *   v2.6 §2.4 用单一 CanonicalAnnotation + motivation 枚举区分,无独立 Note 类型。
 *   本期拆独立类型是为 ZIP JSON 可读性 + 合并引擎入参类型清晰。
 *   合并算法与 [CanonicalAnnotation] 完全同构(uuid 并集 + LWW + 墓碑)。
 *
 * ★ 同步方案 v2.6 §2.4 / 一期 §3.3 mergeNotes。
 */
@Serializable
data class CanonicalNote(
    override val uuid: String,
    override val hlc: HlcTs,
    override val deleted: Boolean,
    override val schemaVersion: Int = 2,
    val locator: String,
    val selectedText: String = "",
    val note: String,
    val color: String,
    val createdAt: Long? = null,
) : SyncRecord
