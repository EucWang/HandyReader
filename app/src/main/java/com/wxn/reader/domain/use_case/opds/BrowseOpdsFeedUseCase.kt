package com.wxn.reader.domain.use_case.opds

import com.wxn.reader.data.model.opds.OpdsFeed
import com.wxn.reader.domain.repository.OpdsRepository
import javax.inject.Inject

class BrowseOpdsFeedUseCase @Inject constructor(
    private val opdsRepository: OpdsRepository
) {
    suspend operator fun invoke(catalogId: Long, url: String, useCache: Boolean = true): Result<OpdsFeed> {
        return opdsRepository.fetchFeed(url, catalogId, useCache)
    }
}
