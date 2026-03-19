package com.wxn.reader.domain.use_case.books

import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class UpdateProgressFieldsUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    suspend operator fun invoke(
        bookId: Long,
        lastOpened: Long,
        scrollIndex: Int,
        scrollOffset: Int,
        progress: Float
    ): Int {
        return repository.updateProgressFields(
            bookId, lastOpened, scrollIndex, scrollOffset, progress
        )
    }
}
