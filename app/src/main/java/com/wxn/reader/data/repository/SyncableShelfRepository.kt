package com.wxn.reader.data.repository

import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
import com.wxn.reader.data.dto.SyncQueueEntity
import com.wxn.reader.data.source.local.SyncPreferencesUtil
import com.wxn.reader.data.source.local.dao.BookShelfDao
import com.wxn.reader.data.source.local.dao.ShelfDao
import com.wxn.reader.data.source.local.dao.SyncQueueDao
import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.domain.repository.ShelfRepository
import com.wxn.reader.util.sync.HybridLogicalClock
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ★ 同步装饰器(v2.6 §4.8 / 一期 §3.2.4 严重-F4 重写)。
 *
 * 双 markDirty 轨:
 * - [markShelfDirty]:shelves 表的 HLC(单 shelf 自身)。
 * - [markBookShelfRelationDirty]:book_shelf 关系行的 HLC(与 shelf 本身分开)。
 *
 * ★ v1.4 严重-F4:HLC 更新【无条件】,只有 sync_queue 写入才看 isSyncEnabled()。
 *   千万不要把 hlc.now() 放在 `if (isSyncEnabled())` 块内 —— 那样一期短路 sync_queue 时
 *   会连带跳过 HLC 推进,导致 shelf 改动备份不出来。
 */
@Singleton
class SyncableShelfRepository @Inject constructor(
    private val delegate: ShelfRepository,
    private val shelfDao: ShelfDao,
    private val bookShelfDao: BookShelfDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncPrefs: SyncPreferencesUtil,
    private val hlc: HybridLogicalClock,
) : ShelfRepository by delegate {

    /** shelves 表 HLC(单 shelf 自身)。 */
    private suspend fun markShelfDirty(shelfId: Long) {
        val ts = hlc.now()
        shelfDao.updateShelfHlcById(shelfId, ts.l, ts.c, ts.deviceId)
        if (syncPrefs.isSyncEnabled()) {
            try {
                syncQueueDao.upsert(SyncQueueEntity(stableId = "shelf:$shelfId", scope = "SHELF", op = "UPSERT"))
            } catch (e: Exception) {
                Logger.w("SyncShelf:enqueue failed,$e")
            }
        }
    }

    /** book_shelf 关系行 HLC(与 shelf 本身分开)。 */
    private suspend fun markBookShelfRelationDirty(bookId: Long, shelfId: Long) {
        val ts = hlc.now()
        bookShelfDao.updateHlcByBookAndShelf(bookId, shelfId, ts.l, ts.c, ts.deviceId)
        if (syncPrefs.isSyncEnabled()) {
            try {
                syncQueueDao.upsert(
                    SyncQueueEntity(stableId = "book_shelf:$bookId:$shelfId", scope = "SHELF", op = "UPSERT")
                )
            } catch (e: Exception) {
                Logger.w("SyncShelf:enqueue relation failed,$e")
            }
        }
    }

    override suspend fun addShelf(shelf: Shelf): Long {
        val newId = delegate.addShelf(shelf)
        markShelfDirty(newId)
        return newId
    }

    override suspend fun updateShelf(shelf: Shelf) {
        delegate.updateShelf(shelf)
        markShelfDirty(shelf.id)
    }

    override suspend fun deleteShelf(shelf: Shelf) {
        delegate.deleteShelf(shelf)
        markShelfDirty(shelf.id)
    }

    override suspend fun addBookToShelf(bookId: Long, shelfId: Long) {
        delegate.addBookToShelf(bookId, shelfId)
        markBookShelfRelationDirty(bookId, shelfId)
    }

    override suspend fun removeBookFromShelf(bookId: Long, shelfId: Long) {
        delegate.removeBookFromShelf(bookId, shelfId)
        markBookShelfRelationDirty(bookId, shelfId)
    }
}
