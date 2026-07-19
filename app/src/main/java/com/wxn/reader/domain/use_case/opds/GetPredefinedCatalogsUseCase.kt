package com.wxn.reader.domain.use_case.opds

import com.wxn.reader.data.model.opds.OpdsCatalogConfigParser
import com.wxn.reader.data.model.opds.PredefinedCatalogItem
import com.wxn.reader.domain.repository.OpdsRepository
import javax.inject.Inject

class GetPredefinedCatalogsUseCase @Inject constructor(
    private val opdsRepository: OpdsRepository
) {

    data class SyncResult(
        val catalogs: List<PredefinedCatalogItem>,
        val isRemoteError: Boolean = false
    )

    suspend operator fun invoke(): SyncResult {
        return SyncResult(OpdsCatalogConfigParser.FALLBACK_CATALOGS.catalogs)
    }

    suspend fun syncToDatabase(catalogs: List<PredefinedCatalogItem>) {
        opdsRepository.syncPredefinedCatalogs(catalogs)
    }
}
