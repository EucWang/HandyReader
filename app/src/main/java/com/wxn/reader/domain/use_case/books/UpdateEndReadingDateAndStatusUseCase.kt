package com.wxn.reader.domain.use_case.books

import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class UpdateEndReadingDateAndStatusUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    suspend operator fun invoke(
        bookId: Long,
        endDate: Long,
        status: ReadingStatus
    ): Int {
        return repository.updateEndReadingDateAndStatus(bookId, endDate, status)
    }
}
