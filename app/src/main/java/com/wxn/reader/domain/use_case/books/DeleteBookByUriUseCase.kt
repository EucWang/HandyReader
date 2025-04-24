package com.wxn.reader.domain.use_case.books

import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class DeleteBookByUriUseCase @Inject constructor(private val repository: BooksRepository)  {
    suspend operator fun invoke(bookUri: String) {
        repository.deleteBookByUri(bookUri)
    }
}