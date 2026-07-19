package com.wxn.reader.domain.use_case.books

import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
import com.wxn.reader.data.backup.ContentHashCalculator
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class InsertBookUseCase @Inject constructor(
    private val repository: BooksRepository,
    private val contentHashCalculator: ContentHashCalculator,
) {
    /**
     * 单本插入。contentHash 由 FileParserImpl 在解析阶段算填(book.contentHash),
     * 插入后立即调 [ContentHashCalculator.handlePotentialConflict] 做并发兜底去重。
     *
     * 历史/老路径(book.contentHash == null,如 OPDS 下载)不触发去重,
     * 保持与原有行为兼容(其 hash 由 [ContentHashCalculator.ensureContentHash] 后续补算)。
     * ★ A+++ 严重-9:BackupImporter orphan 路径已手动调 handlePotentialConflict(事务外)。
     */
    suspend operator fun invoke(book: Book): Long = withContext(Dispatchers.IO) {
        val bookId = repository.insertBook(book)
        if (bookId > 0 && !book.contentHash.isNullOrBlank()) {
            try {
                contentHashCalculator.handlePotentialConflict(bookId, book.contentHash!!)
            } catch (e: CancellationException) {
                throw e  // 红线 #6:取消向上传播,不吞
            } catch (e: Exception) {
                Logger.w("InsertBookUseCase:handlePotentialConflict failed bookId=$bookId: ${e.message}")
            }
        }
        bookId
    }

    suspend fun insert(books: List<Book>): Int = withContext(Dispatchers.IO) {
        repository.insertBooks(books)
    }
}
