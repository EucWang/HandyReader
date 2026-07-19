package com.wxn.reader.domain.repository

import com.wxn.base.bean.Locator
import com.wxn.reader.data.dto.BookVocabularyEntity
import kotlinx.coroutines.flow.Flow

/**
 * ★ 同步红线:本接口的所有写方法(suspend fun 返回 Int/Long/Unit 且非 Flow)
 * 必须在 [com.wxn.reader.data.repository.SyncableVocabularyRepository] 中 override 并推 HLC,
 * 否则单词本改动 HLC 不推进,备份/还原会丢数据。
 *
 * ★ v1.3 严重-4:本地软删走既有 `status=-1`(不加 deleted 列),装饰器 `softDelete` 必须覆盖,
 *   推 deletedHlc*(独立跟踪删除时刻),合并引擎 Mapper 负责 `status=-1 ↔ deleted=true` 双向转换。
 */
interface VocabularyRepository {
    suspend fun saveEntry(
        bookId: Long,
        word: String,
        lang: String,
        locator: Locator,
        sentenceText: String
    ): Long

    suspend fun softDelete(id: Long)

    fun getEntries(
        bookId: Long?,
        lang: String?,
        sortBy: String,
        isAsc: Boolean
    ): Flow<List<BookVocabularyEntity>>

    fun getDistinctBookIds(): Flow<List<Long>>

    fun getDistinctLangs(): Flow<List<String>>

    fun getActiveCount(): Flow<Int>
}
