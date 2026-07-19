package com.wxn.reader.data.repository

import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.DocumentsContract
import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.wxn.base.bean.Book
import com.wxn.reader.data.dto.BookListItemEntity
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.ReadingActiveEntity
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.mapper.annotation.BookAnnotationMapper
import com.wxn.reader.data.mapper.book.BookMapper
import com.wxn.reader.data.mapper.bookmark.BookmarkMapper
import com.wxn.reader.data.mapper.bookshelf.BookShelfMapper
import com.wxn.reader.data.mapper.note.NoteMapper
import com.wxn.reader.data.mapper.readingactive.ReadingActiveMapper
import com.wxn.reader.data.mapper.shelf.ShelfMapper
import com.wxn.reader.data.model.SortOption
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadingActivityDao
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.base.bean.Bookmark
import com.wxn.base.util.Logger
import com.wxn.base.util.DateUtil
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.model.ReadingActive
import com.wxn.reader.domain.repository.BooksRepository
import com.wxn.reader.domain.util.ConsecutiveDaysCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile
import javax.inject.Inject
import javax.inject.Singleton

private val legacyDocIdCacheLock = Mutex()

@Singleton
class BooksRepositoryImpl @Inject constructor(
    private val appDb: AppDatabase,
    private val bookDao: BookDao,
    private val annotationDao: AnnotationDao,
    private val noteDao: NoteDao,
    private val bookmarkDao: BookmarkDao,
    private val readingActivityDao: ReadingActivityDao,
    private val bookReadingTimeDao: com.wxn.reader.data.source.local.dao.BookReadingTimeDao,
    private val deviceLocalStore: com.wxn.reader.data.source.local.DeviceLocalStore,

    private val bookMapper: BookMapper,
    private val annotaionMapper: BookAnnotationMapper,
    private val bookmarkMapper: BookmarkMapper,
    private val noteMapper: NoteMapper,
    private val readingActiveMapper: ReadingActiveMapper,
    private val shelfMapper: ShelfMapper,
    private val bookShelfMapper: BookShelfMapper
) : BooksRepository {

    private val insertMutex = Mutex()
    override fun getAllBooks(): Flow<List<Book>> {
        return bookDao.getAllBooks()
            .map { entities ->
                entities.map { entity ->
                    bookMapper.toBook(entity)
                }
            }
    }



    override fun getSortedBooks(
        sortOption: SortOption,
        isAscending: Boolean,
        readingStatuses: Set<ReadingStatus>,
        fileTypes: Set<FileType>
    ): Flow<List<Book>> {
        val status = readingStatuses.toList().takeIf { it.isNotEmpty() }
        val storageList = fileTypes.flatMap { it.storageValues() }
        val result = bookDao.getBooksSortedLite(
            sortOption.name.lowercase(),
            isAscending,
            readingStatuses = status,
            allStatus = if (status.isNullOrEmpty()) { 1 } else { 0 },
            fileTypes = storageList.takeIf { it.isNotEmpty() },
            allFileTypes = if (storageList.isEmpty()) { 1 } else { 0 }
        ).map { entities ->
            entities.map { entity -> bookListItemToBook(entity) }
        }
        return result
    }

    override fun getAllBooks(
        sortOption: SortOption,
        isAscending: Boolean,
        readingStatuses: Set<ReadingStatus>,
        fileTypes: Set<FileType>,
    ): PagingSource<Int, Book> {
        return BookPagingSource(
            bookDao,
            bookMapper,
            sortOption,
            isAscending,
            readingStatuses,
            fileTypes
        )
    }

    override fun getDeletedBooks(): Flow<List<Book>> {
        return bookDao.getDeletedBooks().map { entities ->
            entities.map { entity ->
                bookMapper.toBook(entity)
            }
        }
    }

    override fun getBookByIdFlow(bookId: Long): Flow<Book?> {
        return bookDao.getBookByIdFlow(bookId).map { entity ->
            entity?.let { bookMapper.toBook(it) }
        }
    }

    override fun getRecentlyReadBooks(limit: Int): Flow<List<Book>> {
        return bookDao.getRecentlyReadBooksLite(limit).map { entities ->
            entities.map { entity -> bookListItemToBook(entity) }
        }
    }

    override fun getRecentlyAddedBooks(limit: Int): Flow<List<Book>> {
        return bookDao.getRecentlyAddedBooksLite(limit).map { entities ->
            entities.map { entity -> bookListItemToBook(entity) }
        }
    }

    override fun getFavoriteBooks(limit: Int): Flow<List<Book>> {
        return bookDao.getFavoriteBooksLite(limit).map { entities ->
            entities.map { entity -> bookListItemToBook(entity) }
        }
    }


    override suspend fun getAllBookUris(): List<String> = withContext(Dispatchers.IO) {
        bookDao.getAllBookUris()
    }

    override suspend fun getBookById(bookId: Long): Book? =
        bookDao.getBookById(bookId)?.let {
            bookMapper.toBook(it)
        }

    override suspend fun getBookByUri(uri: String): Book? = withContext(Dispatchers.IO) {
        val entity = bookDao.getBookByUri(uri) ?: return@withContext null
        bookMapper.toBook(entity)
    }

    override suspend fun insertBook(book: Book): Long = withContext(Dispatchers.IO) {
        insertMutex.withLock {
            val entity = bookMapper.toBookEntity(book)

            if (entity.documentId != null) {
                val existingByDocId = bookDao.getBookByDocumentId(entity.documentId)
                if (existingByDocId != null) return@withLock existingByDocId.id

                val matchedLegacy = matchLegacyBookByDocId(entity.documentId, bookDao)
                if (matchedLegacy != null) {
                    bookDao.updateDocumentId(matchedLegacy.id, entity.documentId)
                    return@withLock matchedLegacy.id
                }
            }

            // ★ orphan 提升:contentHash 主匹配 → crc+fileType 兜底(旧备份无 hash)。
            //   contentHash (SHA-256)由 FileParserImpl 对所有格式计算,导入侧恒可用,
            //   与还原侧(BackupImporter.resolveLocalBookId)的匹配键一致。
            //   CRC32 兜底仅用于极旧备份 orphan(无 contentHash),碰撞概率约 2^-32。
            var orphanId: Long? = null
            if (!entity.contentHash.isNullOrBlank()) {
                orphanId = bookDao.getOrphanBookIdByContentHash(entity.contentHash!!)
            }
            if (orphanId == null && entity.crc != 0) {
                orphanId = bookDao.getOrphanBookIdByCrcFileType(entity.crc, entity.fileType)
            }
            if (orphanId != null) {
                val existingOrphan = bookDao.getBookByIdIncludeDeleted(orphanId)
                val effectiveCoverPath = if (!existingOrphan?.coverPath.isNullOrEmpty()) {
                    existingOrphan.coverPath  // orphan 已有封面（来自还原/同步），保留
                } else {
                    entity.coverPath          // orphan 无封面，用解析值（可能为空）
                }

                bookDao.promoteOrphanBook(
                    id = orphanId,
                    uri = entity.uri,
                    source = entity.source,
                    documentId = entity.documentId,
                    fileType = entity.fileType,
                    coverPath = effectiveCoverPath,
                    contentHash = entity.contentHash,
                    crc = entity.crc,
                    partialMd5 = entity.partialMd5,
                )
                return@withLock orphanId
            }

            val existing = bookDao.getBookByUri(entity.uri)
            if (existing != null) {
                existing.id
            } else {
                bookDao.insertBook(entity)
            }
        }
    }

    override suspend fun getBookByCrc(crc: Int): Book? = withContext(Dispatchers.IO) {
        val entity = bookDao.getBookByCrc(crc) ?: return@withContext null
        bookMapper.toBook(entity)
    }

    override suspend fun hasActiveBookWithHash(contentHash: String): Boolean = withContext(Dispatchers.IO) {
        bookDao.getActiveBookIdsByContentHash(contentHash).isNotEmpty()
    }

    override suspend fun insertBooks(books: List<Book>) : Int = withContext(Dispatchers.IO) {
        val entities = books.map { bookMapper.toBookEntity(it) }
        if (entities.size > 0) {
            val insertedIds = bookDao.insertBooksIgnoreConflict(entities)
            insertedIds.count { it >= 0 }
        } else {
            0
        }
    }

    override suspend fun updateBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.update(bookMapper.toBookEntity(book))
    }

    override suspend fun deleteBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.delete(bookMapper.toBookEntity(book))
    }

    override suspend fun deleteBookByUri(bookUri: String) = withContext(Dispatchers.IO) {
        bookDao.deleteBookByUri(bookUri)
    }


    override suspend fun getReadingProgress(bookId: Long): String = withContext(Dispatchers.IO) {
        bookDao.getReadingProgress(bookId)
    }

    override suspend fun setReadingStatus(bookId: Long, status: ReadingStatus) {
        bookDao.setReadingStatus(bookId, status)
    }

    override suspend fun setReadingProgress(bookId: Long, locator: String, progression: Float) = withContext(Dispatchers.IO) {
        bookDao.setReadingProgress(bookId, locator, progression)
    }

    override suspend fun incrementReadingTime(bookId: Long, delta: Long): Int = withContext(Dispatchers.IO) {
        // ★ 2026-07-07 清理 3:改用轻量 getFileType(原 getContentHashAndFileType 取了 hash 但不用)
        val fileType = bookDao.getFileType(bookId)
        if (fileType == null) {
            // 书不存在(罕见),回退原位更新避免 FK 约束失败
            bookDao.incrementReadingTime(bookId, delta)
        } else {
            if (fileType.lowercase() in AppDatabase.AUDIO_FILE_TYPES) {
                // 音频书:readingTime = 播放位置,保持原增量行为
                bookDao.incrementReadingTime(bookId, delta)
            } else {
                val deviceId = deviceLocalStore.getOrCreateLocalDeviceId()
                val now = System.currentTimeMillis()
                appDb.withTransaction {
                    bookReadingTimeDao.incrementPerDevice(bookId, deviceId, delta, now)
                    val sum = bookReadingTimeDao.sumByBookId(bookId)
                    bookDao.updateReadingTime(bookId, sum)
                }
            }
        }
    }

    override suspend fun incrementReadingActivityTime(date: Long, delta: Long) {
        try {
            // ★ v9 同步方案:复合 PK (date, deviceId),按本机 deviceId 累加本机行
            val deviceId = deviceLocalStore.getOrCreateLocalDeviceId()
            readingActivityDao.upsertReadingTime(date, deviceId, delta)
        } catch (ex: SQLiteException) {
            Logger.e("BooksRepositoryImpl:incrementReadingActivityTime:date:$date,delta:$delta:$ex")
        }
    }

    // ============ 选择性更新方法实现 ============

    override suspend fun updateProgressFields(
        bookId: Long, 
        lastOpened: Long, 
        scrollIndex: Int, 
        scrollOffset: Int, 
        progression: Float
    ): Int = withContext(Dispatchers.IO) {
        bookDao.updateProgressFields(bookId, lastOpened, scrollIndex, scrollOffset, progression)
    }

    override suspend fun updateReadingStatus(bookId: Long, status: ReadingStatus): Int = withContext(Dispatchers.IO) {
        bookDao.updateReadingStatus(bookId, status)
    }

    override suspend fun updateStartReadingDate(bookId: Long, startDate: Long): Int = withContext(Dispatchers.IO) {
        bookDao.updateStartReadingDate(bookId, startDate)
    }

    override suspend fun updateEndReadingDateAndStatus(
        bookId: Long, 
        endDate: Long, 
        status: ReadingStatus
    ): Int = withContext(Dispatchers.IO) {
        bookDao.updateEndReadingDateAndStatus(bookId, endDate, status)
    }

    override suspend fun updateWordCount(bookId: Long, wordCount: Long): Int = withContext(Dispatchers.IO) {
        bookDao.updateWordCount(bookId, wordCount)
    }

    override suspend fun updatePdfProgressFields(
        bookId: Long,
        locator: String,
        progression: Float,
        readingStatus: ReadingStatus,
        endReadingDate: Long?
    ): Int = withContext(Dispatchers.IO) {
        bookDao.updatePdfProgressFields(bookId, locator, progression, readingStatus, endReadingDate)
    }

    override suspend fun updateDeletedFlag(bookId: Long, deleted: Boolean): Int = withContext(Dispatchers.IO) {
        bookDao.updateDeletedFlag(bookId, deleted)
    }

    override suspend fun updateLastOpened(bookId: Long, lastOpened: Long): Int = withContext(Dispatchers.IO) {
        bookDao.updateLastOpened(bookId, lastOpened)
    }

    override suspend fun updateRating(bookId: Long, rating: Float): Int = withContext(Dispatchers.IO) {
        bookDao.updateRating(bookId, rating)
    }

    override suspend fun updateStartReadingDateOnly(bookId: Long, startDate: Long?): Int = withContext(Dispatchers.IO) {
        bookDao.updateStartReadingDateOnly(bookId, startDate)
    }

    override suspend fun updateEndReadingDateOnly(bookId: Long, endDate: Long?): Int = withContext(Dispatchers.IO) {
        bookDao.updateEndReadingDateOnly(bookId, endDate)
    }

    override suspend fun updateReadingStatusFull(
        bookId: Long, status: ReadingStatus,
        startDate: Long?, endDate: Long?,
        readingTime: Long, progression: Float
    ): Int = withContext(Dispatchers.IO) {
        bookDao.updateReadingStatusFull(bookId, status, startDate, endDate, readingTime, progression)
    }

    override suspend fun updateFavorite(bookId: Long, isFavorite: Boolean, favoriteDate: Long?) = withContext(Dispatchers.IO) {
        bookDao.updateFavorite(bookId, isFavorite, favoriteDate)
    }

    override suspend fun getAllAnnotations(): Flow<List<BookAnnotation>> = withContext(Dispatchers.IO) {
        annotationDao.getAllAnnotations().map { entities ->
            entities.map { entity ->
                annotaionMapper.toBookAnnotation(entity)
            }
        }
    }

    override suspend fun getAnnotations(bookId: Long): Flow<List<BookAnnotation>> = withContext(Dispatchers.IO) {
        annotationDao.getAnnotationsForBook(bookId).map { entities ->
            entities.map { entity ->
                annotaionMapper.toBookAnnotation(entity)
            }
        }
    }

    override suspend fun addAnnotation(annotation: BookAnnotation): Long {
        return annotationDao.insert(annotaionMapper.toBookAnnotationEntity(annotation))
    }

    override suspend fun updateAnnotation(annotation: BookAnnotation) {
        annotationDao.update(annotaionMapper.toBookAnnotationEntity(annotation))
    }

    override suspend fun deleteAnnotation(annotation: BookAnnotation) {
        annotationDao.delete(annotaionMapper.toBookAnnotationEntity(annotation))
    }


    override suspend fun getAllNotes(): Flow<List<Note>> = withContext(Dispatchers.IO) {
        noteDao.getAllNotes().map { entities ->
            entities.map { entity ->
                noteMapper.toNote(entity)
            }
        }
    }

    override suspend fun getNotesForBook(bookId: Long): Flow<List<Note>> = withContext(Dispatchers.IO) {
        noteDao.getNotesForBook(bookId).map { entities ->
            entities.map { entity ->
                noteMapper.toNote(entity)
            }
        }
    }

    override suspend fun addNote(note: Note) :Long {
        return noteDao.insert(noteMapper.toNoteEntity(note))
    }

    override suspend fun updateNote(note: Note) {
        noteDao.update(noteMapper.toNoteEntity(note))
    }

    override suspend fun deleteNote(note: Note) {
        noteDao.delete(noteMapper.toNoteEntity(note))
    }


    override suspend fun getAllBookmarks(): Flow<List<Bookmark>> = withContext(Dispatchers.IO) {
        bookmarkDao.getAllBookmarks().map { entities ->
            entities.map { entity ->
                bookmarkMapper.toBookmark(entity)
            }
        }
    }

    override suspend fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>> = withContext(Dispatchers.IO) {
        bookmarkDao.getBookmarksForBook(bookId).map { entities ->
            entities.map { entity ->
                bookmarkMapper.toBookmark(entity)
            }
        }
    }

    override suspend fun addBookmark(bookmark: Bookmark) : Long {
        return bookmarkDao.insert(bookmarkMapper.toBookmarkEntity(bookmark))
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarkDao.update(bookmarkMapper.toBookmarkEntity(bookmark))
    }

    override suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.delete(bookmarkMapper.toBookmarkEntity(bookmark))
    }


    // Reading Activity
    override suspend fun insertOrUpdateReadingActivity(readingActives: ReadingActive) {
        // ★ v9 同步方案:Entity 现含 deviceId(复合 PK),本机写入用 localDeviceId
        val deviceId = deviceLocalStore.getOrCreateLocalDeviceId()
        readingActivityDao.insertOrUpdate(
            ReadingActiveEntity(
                date = readingActives.date,
                deviceId = deviceId,
                readingTime = readingActives.readingTime,
            )
        )
    }

    override suspend fun getReadingActivityByDate(date: Long): ReadingActive? {
        // ★ v9 起按 deviceId 分行,旧 API 取本机行(deviceId = localDeviceId)以保持单设备语义
        val deviceId = deviceLocalStore.getOrCreateLocalDeviceId()
        return readingActivityDao.getReadingActivityByDateAndDevice(date, deviceId)?.let {
            readingActiveMapper.toReadingActive(it)
        }
    }

    override suspend fun getConsecutiveReadingDays(minMillisPerDay: Long): Int {
        val sinceDate = DateUtil.startOfDay(System.currentTimeMillis()) - 7L * DateUtil.DAY_MS
        val entities = readingActivityDao.getReadingActivitiesSince(sinceDate).first()
        val activities = entities.map { readingActiveMapper.toReadingActive(it) }
        return ConsecutiveDaysCalculator.calc(activities, minMillisPerDay)
    }

    override suspend fun getAllReadingActivities(): Flow<List<ReadingActive>> {
        return readingActivityDao.getAllReadingActivities().map { entities ->
            entities.map { entity ->
                readingActiveMapper.toReadingActive(entity)
            }
        }
    }

    override fun getReadingActivitiesSince(sinceTimestamp: Long): Flow<List<ReadingActive>> {
        return readingActivityDao.getReadingActivitiesSince(sinceTimestamp).map { entities ->
            entities.map { entity ->
                readingActiveMapper.toReadingActive(entity)
            }
        }
    }

    @Volatile
    private var legacyDocIdCache: ConcurrentHashMap<String, Long>? = null

    private suspend fun matchLegacyBookByDocId(docId: String, bookDao: com.wxn.reader.data.source.local.dao.BookDao): com.wxn.reader.data.dto.BookEntity? {
        val cache = legacyDocIdCache ?: legacyDocIdCacheLock.withLock {
            legacyDocIdCache ?: buildLegacyDocIdCache(bookDao).also { legacyDocIdCache = it }
        }
        val matchedId = cache.remove(docId)
        if (matchedId != null) {
            return bookDao.getBookById(matchedId)
        }
        return null
    }

    private suspend fun buildLegacyDocIdCache(bookDao: com.wxn.reader.data.source.local.dao.BookDao): ConcurrentHashMap<String, Long> {
        val books = bookDao.getBooksWithNullDocumentId()
        val cache = ConcurrentHashMap<String, Long>()
        for (entity in books) {
            val docId = extractDocumentId(entity.uri)
            if (docId != null) {
                cache[docId] = entity.id
            }
        }
        return cache
    }

    private fun extractDocumentId(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "content") DocumentsContract.getDocumentId(uri) else null
        } catch (e: Exception) { null }
    }

    private fun bookListItemToBook(entity: BookListItemEntity): Book {
        return Book(
            id = entity.id,
            title = entity.title,
            author = entity.authors,
            description = null,
            filePath = entity.uri,
            coverImage = entity.coverPath,
            scrollIndex = 0,
            scrollOffset = 0,
            progress = entity.progression,
            lastOpened = entity.lastOpened,
            category = entity.subjects,
            fileType = entity.fileType,
            publishDate = entity.publishDate,
            publisher = entity.publisher,
            language = entity.language,
            numberOfPages = entity.numberOfPages,
            wordCount = 0,
            locator = "",
            deleted = entity.deleted,
            rating = entity.rating,
            isFavorite = entity.isFavorite,
            readingStatus = entity.readingStatus?.ordinal,
            readingTime = entity.readingTime,
            startReadingDate = entity.startReadingDate,
            endReadingDate = entity.endReadingDate,
            review = null,
            duration = entity.duration,
            narrator = entity.narrator,
            crc = 0,
            cachedDir = null,
            importStatus = entity.importStatus,
            source = entity.source,
            documentId = entity.documentId,
            favoriteDate = entity.favoriteDate,
        )
    }
}