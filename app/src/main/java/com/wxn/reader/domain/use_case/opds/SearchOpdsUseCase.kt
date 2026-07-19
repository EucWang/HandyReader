package com.wxn.reader.domain.use_case.opds

import com.wxn.reader.data.model.opds.OpdsFeed
import com.wxn.reader.domain.repository.OpdsRepository
import javax.inject.Inject

class SearchOpdsUseCase @Inject constructor(
    private val opdsRepository: OpdsRepository
) {
    suspend operator fun invoke(
        catalogId: Long,
        searchUrl: String,
        query: String,
        startIndex: Int = 0,
        count: Int = 50
    ): Result<OpdsFeed> {
        if (query.isBlank()) return Result.failure(IllegalArgumentException("Search query is empty"))
        return opdsRepository.search(catalogId, searchUrl, query, startIndex, count)
    }
}
