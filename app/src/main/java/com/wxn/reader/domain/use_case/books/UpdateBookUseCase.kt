package com.wxn.reader.domain.use_case.books

import com.wxn.base.bean.Book
import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

/**
 * 全字段更新书籍。调用方必须持有完整的 Book 数据（通过 getBookById 获取），
 * 否则会用空值/默认值覆盖真实数据（如 locator、cachedDir 等阅读器需要的字段）。
 *
 * 优先使用选择性更新方法（updateLastOpened、updateRating、updateReadingStatusFull 等）。
 * 仅在 EditMetadataModal 等需要更新元数据字段的场景使用本方法。
 */
class UpdateBookUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(book: Book) {
        repository.updateBook(book)
    }

}