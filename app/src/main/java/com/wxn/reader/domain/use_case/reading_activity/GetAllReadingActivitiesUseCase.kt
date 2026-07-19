package com.wxn.reader.domain.use_case.reading_activity

import com.wxn.reader.domain.model.ReadingActive
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllReadingActivitiesUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(): Flow<List<ReadingActive>> {
        return repository.getAllReadingActivities()
    }
}