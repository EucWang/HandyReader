package com.wxn.reader.domain.use_case.home

import com.wxn.reader.domain.model.ReadingActive
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHomeHeatmapUseCase @Inject constructor(
    private val booksRepository: BooksRepository
) {
    operator fun invoke(sinceTimestamp: Long): Flow<List<ReadingActive>> {
        return booksRepository.getReadingActivitiesSince(sinceTimestamp)
    }
}
