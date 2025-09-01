package com.wxn.reader.domain.use_case.books

import com.wxn.base.bean.Book
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetBookByIdUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(bookId: Long): Book? = withContext(Dispatchers.IO) {
        repository.getBookById(bookId)
    }
}