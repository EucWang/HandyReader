package com.wxn.reader.domain.repository

import androidx.paging.PagingSource
import com.wxn.base.bean.Book
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.model.SortOption
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.base.bean.Bookmark
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.model.ReadingActive
import kotlinx.coroutines.flow.Flow

interface BooksRepository {

    //book
    fun getAllBooks(): Flow<List<Book>>

    fun getAllBooks(
        sortOption: SortOption,
        isAscending: Boolean,
        readingStatuses: Set<ReadingStatus>,
        fileTypes: Set<FileType>
    ): PagingSource<Int, Book>

    fun getSortedBooks(
        sortOption: SortOption,
        isAscending: Boolean,
        readingStatuses: Set<ReadingStatus>,
        fileTypes: Set<FileType>
    ): Flow<List<Book>>

    fun getDeletedBooks(): Flow<List<Book>>
    suspend fun getAllBookUris(): List<String>
    suspend fun getBookById(bookId: Long): Book?
    suspend fun insertBook(book: Book): Int
    suspend fun insertBooks(books: List<Book>): Int

    suspend fun updateBook(book: Book)
    suspend fun deleteBook(book: Book)
    suspend fun deleteBookByUri(bookUri: String)

    suspend fun getReadingProgress(bookId: Long): String
    suspend fun setReadingProgress(bookId: Long, locator: String, progression: Float)
    suspend fun setReadingStatus(bookId: Long, status: ReadingStatus)


    // annotation (Highlights / Underlines)
    suspend fun getAllAnnotations(): Flow<List<BookAnnotation>>
    suspend fun getAnnotations(bookId: Long): Flow<List<BookAnnotation>>
    suspend fun addAnnotation(annotation: BookAnnotation): Long
    suspend fun updateAnnotation(annotation: BookAnnotation)
    suspend fun deleteAnnotation(annotation: BookAnnotation)

    // Notes
    suspend fun getAllNotes(): Flow<List<Note>>
    suspend fun getNotesForBook(bookId: Long): Flow<List<Note>>
    suspend fun addNote(note: Note): Long
    suspend fun updateNote(note: Note)
    suspend fun deleteNote(note: Note)

    // Bookmarks
    suspend fun getAllBookmarks(): Flow<List<Bookmark>>
    suspend fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark) : Long
    suspend fun updateBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(bookmark: Bookmark)


    // Reading Active
    suspend fun insertOrUpdateReadingActivity(readingActivity: ReadingActive)
    suspend fun getReadingActivityByDate(date: Long): ReadingActive?
    suspend fun getTotalReadingTime(): Long?
    suspend fun getAllReadingActivities(): Flow<List<ReadingActive>>


    // Atomic increment operations
    suspend fun incrementReadingTime(bookId: Long, delta: Long): Int
    suspend fun incrementReadingActivityTime(date: Long, delta: Long): Int

    // ============ 选择性更新方法 ============

    /**
     * 只更新阅读进度相关字段，不覆盖 readingTime
     */
    suspend fun updateProgressFields(
        bookId: Long, 
        lastOpened: Long, 
        scrollIndex: Int, 
        scrollOffset: Int, 
        progression: Float
    ): Int

    /**
     * 只更新阅读状态
     */
    suspend fun updateReadingStatus(bookId: Long, status: ReadingStatus): Int

    /**
     * 更新开始阅读时间
     */
    suspend fun updateStartReadingDate(bookId: Long, startDate: Long): Int

    /**
     * 更新结束阅读时间和状态
     */
    suspend fun updateEndReadingDateAndStatus(
        bookId: Long, 
        endDate: Long, 
        status: ReadingStatus
    ): Int

    /**
     * 更新书总字数
     */
    suspend fun updateWordCount(bookId: Long, wordCount: Long): Int

    /**
     * 更新 PDF 阅读进度字段
     */
    suspend fun updatePdfProgressFields(
        bookId: Long,
        locator: String,
        progression: Float,
        readingStatus: ReadingStatus,
        endReadingDate: Long?
    ): Int

    /**
     * 更新删除标志
     */
    suspend fun updateDeletedFlag(bookId: Long, deleted: Boolean): Int
}