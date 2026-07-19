package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/**
 * 书架-书籍关系 Record(软删 Q12)。
 *
 * ★ 关系行用 bookIdentity.contentHash 标识书,shelfUuid 标识书架(跨设备稳定);
 *   合并时先匹配本地 bookId / shelfId(由 Importer 解析)。
 *
 * 合并语义:同 uuid 并集 + LWW(关系行)。
 *
 * ★ 同步方案 v2.6 §6.8 / 一期 §3.3 mergeBookShelfRelations。
 */
@Serializable
data class BookShelfRelationRecord(
    override val uuid: String,
    override val hlc: HlcTs,
    override val deleted: Boolean,
    override val schemaVersion: Int = 2,
    val bookContentHash: String,
    val shelfUuid: String,
) : SyncRecord
