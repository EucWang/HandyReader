package com.wxn.reader.data.repository

import android.util.LruCache
import androidx.paging.PagingSource
import com.wxn.base.bean.Book
import com.wxn.base.bean.Bookmark
import com.wxn.base.util.Logger
import com.wxn.reader.data.backup.StableIdResolver
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.model.SortOption
import com.wxn.reader.data.remote.sync.canonical.SyncScope
import com.wxn.reader.data.source.local.SyncPreferencesUtil
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.SyncQueueDao
import com.wxn.reader.data.source.local.dao.FileTypeHashProjection
import com.wxn.reader.data.dto.SyncQueueEntity
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.model.ReadingActive
import com.wxn.reader.domain.repository.BooksRepository
import com.wxn.reader.util.sync.HybridLogicalClock
import com.wxn.base.util.withIO
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/**
 * ★ 同步装饰器(v2.6 §4.8 一期改造为 HLC-only 短路)。
 *
 * 设计要点:
 * - **HLC 推进无条件**(★ v1.5 G2):每次本地写都推进 HLC,不依赖 contentHash 是否就位。
 *   book 级 HLC 列(syncHlc*\/userHlc*\/metaHlc*)与 stableId 无关。
 * - **sync_queue 入队才看 isSyncEnabled + stableId**:一期 `isSyncEnabled()` 恒 false → 短路;
 *   二期激活时才需 stableId,届时 contentHash 应已就位。
 * - **子表 per-row HLC**(★ v1.5 C2):annotations/notes/bookmarks 每条独立 syncHlc(uuid LWW),
 *   走 `updateXxxHlcById(id, ...)`,不是 book 级 markDirty。
 *
 * 实施红线(★ 一般-D + v1.4 一般-F5):本接口的【所有】suspend 写方法必须在此 override,
 * 新增写方法时同步补 override。单测反射扫描本接口(谓词 `isSuspend && returnType != Flow`)自动失败提示。
 */
@Singleton
class SyncableBooksRepository @Inject constructor(
    private val delegate: BooksRepository,
    private val bookDao: BookDao,
    private val annotationDao: AnnotationDao,
    private val noteDao: NoteDao,
    private val bookmarkDao: BookmarkDao,
    private val syncQueueDao: SyncQueueDao,
    private val syncPrefs: SyncPreferencesUtil,
    private val hlc: HybridLogicalClock,
    private val stableIdResolver: StableIdResolver,
) : BooksRepository by delegate {

    /** ★ v1.3 一般-5:bookId → stableId 缓存,翻页高频写入免每次 SELECT contentHash。 */
    private val stableIdCache = LruCache<Long, String>(64)

    /**
     * ★ v1.5 C1 + G2:book 级 HLC 入口(meta/user/reading 三档)。
     *
     * 设计原则:
     * - HLC 推进无条件:不管 isSyncEnabled / contentHash 是否就位,book 级 syncHlc* 列【必须写】。
     * - sync_queue 入队才看 stableId + isSyncEnabled:一期 isSyncEnabled 恒 false → 短路。
     *
     * ANNOTATION/SHELF 档【不走此方法】(子表 per-row HLC,见末尾 addAnnotation 等示例 + §3.2.4)。
     */
    private suspend fun markDirty(bookId: Long, scope: SyncScope) {
        // (1) 始终:推进 HLC + 写 books 表对应档 syncHlc* 列(★ G2:不依赖 stableId)
        val ts = hlc.now()
        when (scope) {
            SyncScope.READING -> bookDao.updateReadingHlc(bookId, ts.l, ts.c, ts.deviceId)
            SyncScope.USER -> bookDao.updateUserHlc(bookId, ts.l, ts.c, ts.deviceId)
            SyncScope.META -> bookDao.updateMetaHlc(bookId, ts.l, ts.c, ts.deviceId)
            SyncScope.ALL -> {
                bookDao.updateReadingHlc(bookId, ts.l, ts.c, ts.deviceId)
                bookDao.updateUserHlc(bookId, ts.l, ts.c, ts.deviceId)
                bookDao.updateMetaHlc(bookId, ts.l, ts.c, ts.deviceId)
            }
            SyncScope.ANNOTATION, SyncScope.SHELF -> {
                Logger.w("SyncableRepo:markDirty(bookId, $scope) 应走子表装饰器,忽略")
            }
        }

        // (2) 仅 syncEnabled 时:写 sync_queue(一期恒 false,短路)。stableId 此处才需要。
        if (syncPrefs.isSyncEnabled()) {
            // ★ 失败占位行(importStatus != 0)不参与同步,与备份侧过滤、日常查询语义一致。
            //   HLC 推进仍无条件(上面已写),仅 sync_queue 入队跳过(与 contentHash 未就位同等处理)。
            val importStatus = bookDao.getImportStatusById(bookId)
            if (importStatus != null && importStatus != 0) {
                Logger.w("SyncableRepo:markDirty enqueue skip: importStatus=$importStatus (bookId=$bookId)")
                return
            }
            val stableId = stableIdCache.get(bookId) ?: resolveStableId(bookId)
            if (stableId == null) {
                Logger.w("SyncableRepo:markDirty enqueue skip: contentHash 未就位 (bookId=$bookId)")
                return
            }
            try {
                syncQueueDao.upsert(
                    SyncQueueEntity(
                        stableId = stableId,
                        scope = scope.name,
                        op = "UPSERT",
                    )
                )
            } catch (e: Exception) {
                Logger.w("SyncableRepo:markDirty enqueue failed:$e")
            }
        }
    }

    private suspend fun markDirtyAll(bookId: Long) {
        markDirty(bookId, SyncScope.ALL)
    }

    private suspend fun resolveStableId(bookId: Long): String? {
//        val proj: FileTypeHashProjection = withIO { bookDao.getContentHashAndFileType(bookId) } ?: return null
        val proj: FileTypeHashProjection = bookDao.getContentHashAndFileType(bookId) ?: return null
        val sid = stableIdResolver.stableId(proj.fileType, proj.contentHash)
        if (sid != null) stableIdCache.put(bookId, sid)
        return sid
    }

    // ===== book 级写方法覆盖(整行/字段写 → markDirty 对应档)=====

    override suspend fun insertBook(book: Book): Long {
        val id = delegate.insertBook(book)
        markDirtyAll(id)
        return id
    }

    override suspend fun insertBooks(books: List<Book>): Int {
        val n = delegate.insertBooks(books)
        books.forEach { markDirtyAll(it.id) }
        return n
    }

    override suspend fun updateBook(book: Book) {
        delegate.updateBook(book)
        markDirtyAll(book.id)
        stableIdCache.remove(book.id) // contentHash 可能随文件变更,失效缓存
    }

    override suspend fun deleteBook(book: Book) {
        delegate.deleteBook(book)
        markDirtyAll(book.id)
    }

    override suspend fun deleteBookByUri(bookUri: String) {
        delegate.deleteBookByUri(bookUri)
    }

    override suspend fun setReadingProgress(bookId: Long, locator: String, progression: Float) {
        delegate.setReadingProgress(bookId, locator, progression)
        markDirty(bookId, SyncScope.READING)
    }

    override suspend fun setReadingStatus(bookId: Long, status: ReadingStatus) {
        delegate.setReadingStatus(bookId, status)
        markDirty(bookId, SyncScope.USER)
    }

    override suspend fun incrementReadingTime(bookId: Long, delta: Long): Int {
        val n = delegate.incrementReadingTime(bookId, delta)
        markDirty(bookId, SyncScope.READING)
        return n
    }

    override suspend fun incrementReadingActivityTime(date: Long, delta: Long) {
        delegate.incrementReadingActivityTime(date, delta)
        // reading_activities 不挂在 book 级 HLC(自身有 deviceId 列作复合 PK);无需 markDirty
    }

    // ===== 选择性更新方法覆盖(返回 Int 的字段级写)=====

    override suspend fun updateProgressFields(
        bookId: Long, lastOpened: Long, scrollIndex: Int, scrollOffset: Int, progression: Float,
    ): Int {
        val n = delegate.updateProgressFields(bookId, lastOpened, scrollIndex, scrollOffset, progression)
        markDirty(bookId, SyncScope.READING)
        return n
    }

    override suspend fun updateReadingStatus(bookId: Long, status: ReadingStatus): Int {
        val n = delegate.updateReadingStatus(bookId, status)
        markDirty(bookId, SyncScope.USER)
        return n
    }

    override suspend fun updateStartReadingDate(bookId: Long, startDate: Long): Int {
        val n = delegate.updateStartReadingDate(bookId, startDate)
        markDirty(bookId, SyncScope.USER)
        return n
    }

    override suspend fun updateEndReadingDateAndStatus(
        bookId: Long, endDate: Long, status: ReadingStatus,
    ): Int {
        val n = delegate.updateEndReadingDateAndStatus(bookId, endDate, status)
        markDirty(bookId, SyncScope.USER)
        return n
    }

    override suspend fun updateWordCount(bookId: Long, wordCount: Long): Int {
        val n = delegate.updateWordCount(bookId, wordCount)
        markDirty(bookId, SyncScope.META)
        return n
    }

    override suspend fun updatePdfProgressFields(
        bookId: Long, locator: String, progression: Float,
        readingStatus: ReadingStatus, endReadingDate: Long?,
    ): Int {
        val n = delegate.updatePdfProgressFields(bookId, locator, progression, readingStatus, endReadingDate)
        markDirtyAll(bookId) // locator/progression=reading 档,readingStatus=user 档;保守三档
        return n
    }

    override suspend fun updateDeletedFlag(bookId: Long, deleted: Boolean): Int {
        val n = delegate.updateDeletedFlag(bookId, deleted)
        markDirtyAll(bookId)
        return n
    }

    override suspend fun updateLastOpened(bookId: Long, lastOpened: Long): Int {
        val n = delegate.updateLastOpened(bookId, lastOpened)
        markDirty(bookId, SyncScope.READING)
        return n
    }

    override suspend fun updateRating(bookId: Long, rating: Float): Int {
        val n = delegate.updateRating(bookId, rating)
        markDirty(bookId, SyncScope.USER)
        return n
    }

    override suspend fun updateStartReadingDateOnly(bookId: Long, startDate: Long?): Int {
        val n = delegate.updateStartReadingDateOnly(bookId, startDate)
        markDirty(bookId, SyncScope.USER)
        return n
    }

    override suspend fun updateEndReadingDateOnly(bookId: Long, endDate: Long?): Int {
        val n = delegate.updateEndReadingDateOnly(bookId, endDate)
        markDirty(bookId, SyncScope.USER)
        return n
    }

    override suspend fun updateReadingStatusFull(
        bookId: Long, status: ReadingStatus,
        startDate: Long?, endDate: Long?,
        readingTime: Long, progression: Float,
    ): Int {
        val n = delegate.updateReadingStatusFull(bookId, status, startDate, endDate, readingTime, progression)
        markDirtyAll(bookId)
        return n
    }

    override suspend fun updateFavorite(bookId: Long, isFavorite: Boolean, favoriteDate: Long?) {
        delegate.updateFavorite(bookId, isFavorite, favoriteDate)
        markDirty(bookId, SyncScope.USER)
    }

    override suspend fun insertOrUpdateReadingActivity(readingActivity: ReadingActive) {
        delegate.insertOrUpdateReadingActivity(readingActivity)
        // reading_activities 不挂在 book 级 HLC
    }

    // ===== ★ v1.5 C2:子表(annotations/notes/bookmarks)per-row HLC 写方法 =====
    //   mergeAnnotations/mergeNotes/mergeBookmarks 按 uuid 并集 + 同 uuid LWW 工作,
    //   每条标注需独立 syncHlc(不能用按 bookId 批量,否则同本书多条标注拿相同 HLC 无法决胜)。

    override suspend fun addAnnotation(annotation: BookAnnotation): Long {
        val newId = delegate.addAnnotation(annotation)
        val ts = hlc.now()
        annotationDao.updateAnnotationHlcById(newId, ts.l, ts.c, ts.deviceId)
        return newId
    }

    override suspend fun updateAnnotation(annotation: BookAnnotation) {
        delegate.updateAnnotation(annotation)
        if (annotation.id > 0) {
            val ts = hlc.now()
            annotationDao.updateAnnotationHlcById(annotation.id, ts.l, ts.c, ts.deviceId)
        }
    }

    override suspend fun deleteAnnotation(annotation: BookAnnotation) {
        delegate.deleteAnnotation(annotation)
        if (annotation.id > 0) {
            val ts = hlc.now()
            annotationDao.updateAnnotationDeletedHlcById(annotation.id, ts.l, ts.c, ts.deviceId)
        }
    }

    override suspend fun addNote(note: Note): Long {
        val newId = delegate.addNote(note)
        val ts = hlc.now()
        noteDao.updateNoteHlcById(newId, ts.l, ts.c, ts.deviceId)
        return newId
    }

    override suspend fun updateNote(note: Note) {
        delegate.updateNote(note)
        if (note.id > 0) {
            val ts = hlc.now()
            noteDao.updateNoteHlcById(note.id, ts.l, ts.c, ts.deviceId)
        }
    }

    override suspend fun deleteNote(note: Note) {
        delegate.deleteNote(note)
        if (note.id > 0) {
            val ts = hlc.now()
            noteDao.updateNoteDeletedHlcById(note.id, ts.l, ts.c, ts.deviceId)
        }
    }

    override suspend fun addBookmark(bookmark: Bookmark): Long {
        val newId = delegate.addBookmark(bookmark)
        val ts = hlc.now()
        bookmarkDao.updateBookmarkHlcById(newId, ts.l, ts.c, ts.deviceId)
        return newId
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        delegate.updateBookmark(bookmark)
        if (bookmark.id > 0) {
            val ts = hlc.now()
            bookmarkDao.updateBookmarkHlcById(bookmark.id, ts.l, ts.c, ts.deviceId)
        }
    }

    override suspend fun deleteBookmark(bookmark: Bookmark) {
        delegate.deleteBookmark(bookmark)
        if (bookmark.id > 0) {
            val ts = hlc.now()
            bookmarkDao.updateBookmarkDeletedHlcById(bookmark.id, ts.l, ts.c, ts.deviceId)
        }
    }
}
