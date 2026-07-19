package com.wxn.reader.domain.use_case.books

import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class UpdateWordCountUseCase @Inject constructor(
    private val repository: BooksRepository
) {
    suspend operator fun invoke(bookId: Long, wordCount: Long): Int {
        return repository.updateWordCount(bookId, wordCount)
    }
}
