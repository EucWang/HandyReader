package com.wxn.reader.data.repository

import androidx.paging.PagingSource
import com.wxn.base.bean.Book
import com.wxn.reader.data.dto.BookEntity
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
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadingActivityDao
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.base.bean.Bookmark
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.model.ReadingActive
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BooksRepositoryImpl @Inject constructor(
    private val appDb: AppDatabase,
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



    override fun getSortedBooks(
        sortOption: SortOption,
        isAscending: Boolean,
        readingStatuses: Set<ReadingStatus>,
        fileTypes: Set<FileType>
    ): Flow<List<Book>> {
        val status = readingStatuses.toList().takeIf { it.isNotEmpty() }
        val result = bookDao.getBooksSorted(
            sortOption.name.lowercase(),
            isAscending,
            readingStatuses = status,
            allStatus = if (status.isNullOrEmpty()) { 1 } else { 0 },
            fileTypes = fileTypes.map { item -> item.typeName() }.toList().takeIf { it.isNotEmpty() }
        ).map { entities ->
            entities.map { entity ->
                bookMapper.toBook(entity)
            }
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


    override suspend fun getAllBookUris(): List<String> = withContext(Dispatchers.IO) {
        bookDao.getAllBookUris()
    }

    override suspend fun getBookById(bookId: Long): Book? = withContext(Dispatchers.IO) {
        bookDao.getBookById(bookId)?.let {
            bookMapper.toBook(it)
        }
    }

    override suspend fun insertBook(book: Book) : Int = withContext(Dispatchers.IO) {
        val entity = bookMapper.toBookEntity(book)
        if (!getAllBookUris().toSet().contains(entity.uri)){
            bookDao.insertBook(entity)
            1
        } else {
            0
        }
    }

    override suspend fun insertBooks(books: List<Book>) : Int = withContext(Dispatchers.IO) {
        val entities = arrayListOf<BookEntity>()
        val uris = getAllBookUris().toSet()
        for(book in books) {
            val entity = bookMapper.toBookEntity(book)
            if (!uris.contains(entity.uri)) {
                entities.add(entity)
            }
        }
        if (entities.size > 0) {
            bookDao.insertBooks(entities)
            entities.size
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