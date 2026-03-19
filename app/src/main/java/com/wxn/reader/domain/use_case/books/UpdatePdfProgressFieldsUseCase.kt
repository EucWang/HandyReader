package com.wxn.reader.domain.use_case.books

import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class UpdatePdfProgressFieldsUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    suspend operator fun invoke(
        bookId: Long,
        locator: String,
        progress: Float,
        readingStatus: ReadingStatus,
        endReadingDate: Long?
    ): Int {
        return repository.updatePdfProgressFields(
            bookId, locator, progress, readingStatus, endReadingDate
        )
    }
}
