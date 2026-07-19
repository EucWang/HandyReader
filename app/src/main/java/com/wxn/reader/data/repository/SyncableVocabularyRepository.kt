package com.wxn.reader.data.repository

import com.wxn.base.bean.Locator
import com.wxn.base.util.Logger
import com.wxn.reader.data.dto.BookVocabularyEntity
import com.wxn.reader.data.dto.SyncQueueEntity
import com.wxn.reader.data.source.local.SyncPreferencesUtil
import com.wxn.reader.data.source.local.dao.BookVocabularyDao
import com.wxn.reader.data.source.local.dao.SyncQueueDao
import com.wxn.reader.domain.repository.VocabularyRepository
import com.wxn.reader.util.sync.HybridLogicalClock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ★ 同步装饰器(v2.6 §4.8 / 一期 §3.2.4)。
 *
 * ★ v1.3 严重-4:必须覆盖 [softDelete] 并推 deletedHlc*(不加 deleted 列,
 *   软删走既有 status=-1;合并引擎 Mapper 负责 status=-1 ↔ deleted=true + deletedHlc* ↔ record.hlc 双向转换)。
 */
@Singleton
class SyncableVocabularyRepository @Inject constructor(
    private val delegate: VocabularyRepository,
    private val vocabularyDao: BookVocabularyDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncPrefs: SyncPreferencesUtil,
    private val hlc: HybridLogicalClock,
) : VocabularyRepository by delegate {

    private suspend fun enqueue(id: Long, op: String) {
        if (!syncPrefs.isSyncEnabled()) return
        try {
            syncQueueDao.upsert(SyncQueueEntity(stableId = "vocab:$id", scope = "ANNOTATION", op = op))
        } catch (e: Exception) {
            Logger.w("SyncVocab:enqueue failed,$e")
        }
    }

    override suspend fun saveEntry(
        bookId: Long, word: String, lang: String, locator: Locator, sentenceText: String,
    ): Long {
        val id = delegate.saveEntry(bookId, word, lang, locator, sentenceText)
        val ts = hlc.now()
        vocabularyDao.updateSyncHlcById(id, ts.l, ts.c, ts.deviceId)
        enqueue(id, "UPSERT")
        return id
    }

    /** ★ v1.3 严重-4:必须覆盖,推 deletedHlc(否则删除操作不传播)。 */
    override suspend fun softDelete(id: Long) {
        delegate.softDelete(id) // 走既有 status=-1 路径,不加 deleted 列
        val ts = hlc.now()
        vocabularyDao.updateDeletedHlcById(id, ts.l, ts.c, ts.deviceId)
        enqueue(id, "DELETE")
    }
}
