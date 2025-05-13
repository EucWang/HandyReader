package com.wxn.reader.domain.use_case.books

import com.wxn.base.bean.Book
import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class UpdateBookUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(book: Book) {
        repository.updateBook(book)
    }

}