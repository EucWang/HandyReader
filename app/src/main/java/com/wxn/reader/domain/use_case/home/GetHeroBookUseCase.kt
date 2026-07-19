package com.wxn.reader.domain.use_case.home

import com.wxn.base.bean.Book
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetHeroBookUseCase @Inject constructor(
    private val booksRepository: BooksRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(lastBookId: Long): Flow<Book?> {
        return booksRepository.getBookByIdFlow(lastBookId)
            .flatMapLatest { book ->
                if (book != null) {
                    flowOf(book)
                } else {
                    booksRepository.getRecentlyReadBooks(1).map { it.firstOrNull() }
                }
            }
    }
}
