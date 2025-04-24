package com.wxn.reader.domain.use_case.reading_activity

import com.wxn.reader.data.model.ReadingActivity
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllReadingActivitiesUseCase @Inject constructor(private val repository: BooksRepository) {
    suspend operator fun invoke(): Flow<List<ReadingActivity>> {
        return repository.getAllReadingActivities()
    }
}