package com.wxn.reader.domain.use_case.books

import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class UpdateDeletedFlagUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    suspend operator fun invoke(bookId: Long, deleted: Boolean): Int {
        return repository.updateDeletedFlag(bookId, deleted)
    }
}
