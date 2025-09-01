package com.wxn.reader.domain.use_case.bookmarks

import com.wxn.base.bean.Bookmark
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBookmarksForBookUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(bookId: Long): Flow<List<Bookmark>> {
        return repository.getBookmarksForBook(bookId)
    }
}