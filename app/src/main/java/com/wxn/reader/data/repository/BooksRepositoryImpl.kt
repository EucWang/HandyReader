package com.wxn.reader.data.repository

import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.room.getQueryDispatcher
import androidx.room.paging.util.INITIAL_ITEM_COUNT
import androidx.room.paging.util.ThreadSafeInvalidationObserver
import androidx.room.paging.util.getClippedRefreshKey
import com.wxn.bookparser.domain.book.Book
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.mapper.annotation.BookAnnotationMapper
import com.wxn.reader.data.mapper.book.BookMapper
import com.wxn.reader.data.mapper.bookmark.BookmarkMapper
import com.wxn.reader.data.mapper.bookshelf.BookShelfMapper
import com.wxn.reader.data.mapper.note.NoteMapper
import com.wxn.reader.data.mapper.readingactive.ReadingActiveMapper
import com.wxn.reader.data.mapper.shelf.ShelfMapper
import com.wxn.reader.data.model.SortOption
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadingActivityDao
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.model.Bookmark
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.model.ReadingActive
import com.wxn.reader.domain.repository.BooksRepository
import com.wxn.reader.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BooksRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
    private val annotationDao: AnnotationDao,
    private val noteDao: NoteDao,
    private val bookmarkDao: BookmarkDao,
    private val readingActivityDao: ReadingActivityDao,

    private val bookMapper: BookMapper,
    private val annotaionMapper: BookAnnotationMapper,
    private val bookmarkMapper: BookmarkMapper,
    private val noteMapper: NoteMapper,
    private val readingActiveMapper: ReadingActiveMapper,
    private val shelfMapper: ShelfMapper,
    private val bookShelfMapper: BookShelfMapper
) : BooksRepository {
    override fun getAllBooks(): Flow<List<Book>> {
        return bookDao.getAllBooks()
            .map { entities ->
                entities.map { entity ->
                    bookMapper.toBook(entity)
                }
            }
    }

    override fun getAllBooks(
        sortOption: SortOption,
        isAscending: Boolean,
        readingStatuses: Set<ReadingStatus>,
        fileTypes: Set<FileType>,
    ): PagingSource<Int, Book> {
//        return bookDao.getAllBooksSorted(
//            sortOption.name.lowercase(),
//            isAscending,
//            readingStatuses = readingStatuses.toList().takeIf { it.isNotEmpty() },
//            fileTypes = fileTypes.toList().takeIf { it.isNotEmpty() }
//        )


        /**
         * Returns the key for [PagingSource] for a non-initial REFRESH load.
         *
         * To prevent a negative key, key is clipped to 0 when the number of items available before
         * anchorPosition is less than the requested amount of initialLoadSize / 2.
         */
        fun <Value : Any> PagingState<Int, Value>.getClippedRefreshKey(): Int? {
            return when (val anchorPosition = anchorPosition) {
                null -> null
                /**
                 *  It is unknown whether anchorPosition represents the item at the top of the screen or item at
                 *  the bottom of the screen. To ensure the number of items loaded is enough to fill up the
                 *  screen, half of loadSize is loaded before the anchorPosition and the other half is
                 *  loaded after the anchorPosition -- anchorPosition becomes the middle item.
                 */
                else -> maxOf(0, anchorPosition - (config.initialLoadSize / 2))
            }
        }

        return object : PagingSource<Int, Book>() {

            override val jumpingSupported: Boolean
                get() = true

            override fun getRefreshKey(state: PagingState<Int, Book>): Int? {
//                return state.getClippedRefreshKey()
                return state.anchorPosition?.let {
                    state.closestPageToPosition(it)?.prevKey?.plus(1)
                        ?: state.closestPageToPosition(it)?.nextKey?.minus(1)
                }
            }

            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
                Logger.d("BookRepositoryImpl::load=${params}")
                return try {
                    val pageNumber = params.key ?: 0
                    Logger.d(
                        "BookRepositoryImpl::pageNumber=${pageNumber}, sortOption=${sortOption.name.lowercase()}," +
                            "isAscending=$isAscending,readingStatuses=${readingStatuses.toList().takeIf { it.isNotEmpty() }}," +
                            "fileType=${fileTypes.toList().takeIf { it.isNotEmpty() }}"
                    )

                    if (pageNumber == 0) {
                        val items = bookDao.getBooksSorted(
                            sortOption.name.lowercase(),
                            isAscending,
                            readingStatuses = readingStatuses.toList().takeIf { it.isNotEmpty() },
                            fileTypes = fileTypes.toList().takeIf { it.isNotEmpty() }
                        ).firstOrNull().orEmpty().map { entity ->
                            bookMapper.toBook(entity)
                        }
                        Logger.d("BookRepositoryImpl::items.count=${items.size}")
                        val prevKey = if (pageNumber > 0) pageNumber - 1 else null
                        val nextKey = if (items.isNotEmpty()) pageNumber + 1 else null
                        Logger.d("BookRepositoryImpl::prevKey=${prevKey}, nextKey=$nextKey")
                        LoadResult.Page(
                            data = items,
                            prevKey = prevKey,
                            nextKey = nextKey
                        )
                    } else {
                        LoadResult.Invalid()
                    }
                } catch (ex: Exception) {
                    LoadResult.Error(ex)
                }
            }
        }
    }

    override fun getDeletedBooks(): Flow<List<Book>> {
        return bookDao.getDeletedBooks().map { entities ->
            entities.map { entity ->
                bookMapper.toBook(entity)
            }
        }
    }


    override suspend fun getAllBookUris(): List<String> = withContext(Dispatchers.IO) {
        bookDao.getAllBookUris()
    }

    override suspend fun getBookById(bookId: Long): Book? = withContext(Dispatchers.IO) {
        bookDao.getBookById(bookId)?.let {
            bookMapper.toBook(it)
        }
    }

    override suspend fun insertBook(book: Book) = withContext(Dispatchers.IO) {
        bookDao.insertBook(bookMapper.toBookEntity(book))
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

    override suspend fun addNote(note: Note) {
        noteDao.insert(noteMapper.toNoteEntity(note))
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

    override suspend fun addBookmark(bookmark: Bookmark) {
        bookmarkDao.insert(bookmarkMapper.toBookmarkEntity(bookmark))
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarkDao.update(bookmarkMapper.toBookmarkEntity(bookmark))
    }

    override suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.delete(bookmarkMapper.toBookmarkEntity(bookmark))
    }


    // Reading Activity
    override suspend fun insertOrUpdateReadingActivity(readingActives: ReadingActive) {
        readingActivityDao.insertOrUpdate(readingActiveMapper.toReadingActiveEntity(readingActives))
    }

    override suspend fun getReadingActivityByDate(date: Long): ReadingActive? {
        return readingActivityDao.getReadingActivityByDate(date)?.let {
            readingActiveMapper.toReadingActive(it)
        }
    }

    override suspend fun getTotalReadingTime(): Long? {
        return readingActivityDao.getTotalReadingTime()
    }

    override suspend fun getAllReadingActivities(): Flow<List<ReadingActive>> {
        return readingActivityDao.getAllReadingActivities().map { entities ->
            entities.map { entity ->
                readingActiveMapper.toReadingActive(entity)
            }
        }
    }
}


/**

/**
 * An implementation of [PagingSource] to perform a LIMIT OFFSET query
 *
 * This class is used for Paging3 to perform Query and RawQuery in Room to return a PagingSource
 * for Pager's consumption. Registers observers on tables lazily and automatically invalidates
 * itself when data changes.
*/
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class LimitOffsetPagingSource<Value : Any>(
private val sourceQuery: RoomSQLiteQuery,
private val db: RoomDatabase,
vararg tables: String,
) : PagingSource<Int, Value>() {

constructor(
supportSQLiteQuery: SupportSQLiteQuery,
db: RoomDatabase,
vararg tables: String,
) : this(
sourceQuery = RoomSQLiteQuery.copyFrom(supportSQLiteQuery),
db = db,
tables = tables,
)

internal val itemCount: AtomicInteger = AtomicInteger(INITIAL_ITEM_COUNT)

private val observer = ThreadSafeInvalidationObserver(
tables = tables,
onInvalidated = ::invalidate
)

override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Value> {
return withContext(db.getQueryDispatcher()) {
observer.registerIfNecessary(db)
val tempCount = itemCount.get()
// if itemCount is < 0, then it is initial load
try {
if (tempCount == INITIAL_ITEM_COUNT) {
initialLoad(params)
} else {
nonInitialLoad(params, tempCount)
}
} catch (e: Exception) {
LoadResult.Error(e)
}
}
}

/**
 *  For the very first time that this PagingSource's [load] is called. Executes the count
 *  query (initializes [itemCount]) and db query within a transaction to ensure initial load's
 *  data integrity.
 *
 *  For example, if the database gets updated after the count query but before the db query
 *  completes, the paging source may not invalidate in time, but this method will return
 *  data based on the original database that the count was performed on to ensure a valid
 *  initial load.
*/
private suspend fun initialLoad(params: LoadParams<Int>): LoadResult<Int, Value> {
return db.withTransaction {
val tempCount = queryItemCount(sourceQuery, db)
itemCount.set(tempCount)
queryDatabase(
params = params,
sourceQuery = sourceQuery,
db = db,
itemCount = tempCount,
convertRows = ::convertRows
)
}
}

private suspend fun nonInitialLoad(
params: LoadParams<Int>,
tempCount: Int,
): LoadResult<Int, Value> {
val loadResult = queryDatabase(
params = params,
sourceQuery = sourceQuery,
db = db,
itemCount = tempCount,
convertRows = ::convertRows
)
// manually check if database has been updated. If so, the observer's
// invalidation callback will invalidate this paging source
db.invalidationTracker.refreshVersionsSync()
@Suppress("UNCHECKED_CAST")
return if (invalid) INVALID as LoadResult.Invalid<Int, Value> else loadResult
}

@NonNull
protected abstract fun convertRows(cursor: Cursor): List<Value>

override fun getRefreshKey(state: PagingState<Int, Value>): Int? {
return state.getClippedRefreshKey()
}

override val jumpingSupported: Boolean
get() = true
}
 */

//class BookLimitOffsetPagingSource<Value: Any>() : PagingSource<Int, Value>(){
//    /**
//     * Returns the key for [PagingSource] for a non-initial REFRESH load.
//     *
//     * To prevent a negative key, key is clipped to 0 when the number of items available before
//     * anchorPosition is less than the requested amount of initialLoadSize / 2.
//     */
//    fun <Value : Any> PagingState<Int, Value>.getClippedRefreshKey(): Int? {
//        return when (val anchorPosition = anchorPosition) {
//            null -> null
//            /**
//             *  It is unknown whether anchorPosition represents the item at the top of the screen or item at
//             *  the bottom of the screen. To ensure the number of items loaded is enough to fill up the
//             *  screen, half of loadSize is loaded before the anchorPosition and the other half is
//             *  loaded after the anchorPosition -- anchorPosition becomes the middle item.
//             */
//            else -> maxOf(0, anchorPosition - (config.initialLoadSize / 2))
//        }
//    }
//
//    internal val itemCount: AtomicInteger = AtomicInteger(-1)
//
////    private val observer = ThreadSafeInvalidationObserver(
////        tables = tables,
////        onInvalidated = ::invalidate
////    )
//
//    override fun getRefreshKey(state: PagingState<Int, Value>): Int? {
//        return state.getClippedRefreshKey()
//    }
//
//    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Value> {
//
//            val tempCount = itemCount.get()
//            // if itemCount is < 0, then it is initial load
//        return    try {
//                if (tempCount == -1) {
//                    initialLoad(params)
//                } else {
//                    nonInitialLoad(params, tempCount)
//                }
//            } catch (e: Exception) {
//                LoadResult.Error(e)
//            }
//    }
//
//    override val jumpingSupported: Boolean
//        get() = true
//}