package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/**
 * 单词本 Record。
 *
 * ★ v1.3 严重-4:[deleted] 由 Mapper 从本地 `status=-1` 派生:
 *   - status=0 → VocabularyRecord(deleted=false, hlc=HlcTimestamp(syncHlcL/C/Device))
 *   - status=-1 → VocabularyRecord(deleted=true, hlc=HlcTimestamp(deletedHlcL/C/Device))
 *
 * 合并语义:同 [CanonicalAnnotation] 的 uuid 并集 + LWW + 墓碑。
 *
 * ★ 同步方案 v2.6 §2.6.3 / 一期 §3.3 mergeVocabulary。
 */
@Serializable
data class VocabularyRecord(
    override val uuid: String,
    override val hlc: HlcTs,
    override val deleted: Boolean,
    override val schemaVersion: Int = 2,
    val word: String,
    val lang: String,
    val sentenceText: String,
    val locator: String,
    val chapterIndex: Int = 0,
    val startParagraphIndex: Int = 0,
    val startTextOffset: Int = 0,
    val createdAt: Long = 0L,
) : SyncRecord
