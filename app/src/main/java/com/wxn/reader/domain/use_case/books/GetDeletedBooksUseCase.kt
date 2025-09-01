package com.wxn.reader.domain.use_case.books

import com.wxn.base.bean.Book
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDeletedBooksUseCase @Inject constructor(private val repository: BooksRepository) {
    operator fun invoke(): Flow<List<Book>> = repository.getDeletedBooks()
}