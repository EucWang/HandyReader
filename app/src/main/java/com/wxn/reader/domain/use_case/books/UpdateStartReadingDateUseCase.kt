package com.wxn.reader.domain.use_case.books

import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class UpdateStartReadingDateUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    suspend operator fun invoke(bookId: Long, startDate: Long): Int {
        return repository.updateStartReadingDate(bookId, startDate)
    }
}
