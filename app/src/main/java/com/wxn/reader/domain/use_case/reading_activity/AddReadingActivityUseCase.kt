package com.wxn.reader.domain.use_case.reading_activity

import com.wxn.reader.domain.model.ReadingActive
import com.wxn.reader.domain.repository.BooksRepository
import javax.inject.Inject

class AddReadingActivityUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(readingActivity: ReadingActive) {
        repository.insertOrUpdateReadingActivity(readingActivity)
    }
}