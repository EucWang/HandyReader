package com.wxn.reader.domain.use_case.home

import com.wxn.base.bean.Book
import com.wxn.reader.data.model.RecentToggle
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRecentBooksUseCase @Inject constructor(
    private val booksRepository: BooksRepository
) {
    operator fun invoke(toggle: RecentToggle, limit: Int = 30): Flow<List<Book>> {
        return when (toggle) {
            RecentToggle.READ -> booksRepository.getRecentlyReadBooks(limit)
            RecentToggle.ADDED -> booksRepository.getRecentlyAddedBooks(limit)
            RecentToggle.FAVORITE -> booksRepository.getFavoriteBooks(limit)
        }
    }
}
