package com.wxn.reader.domain.use_case.books

import com.wxn.bookparser.domain.book.Book
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class InsertBookUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(book: Book) = withContext(Dispatchers.IO) {
        repository.insertBook(book)
    }
}