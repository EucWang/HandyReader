package com.wxn.reader.domain.repository

import com.wxn.reader.data.dto.OpdsCatalogEntity
import com.wxn.reader.data.model.opds.OpdsFeed
import com.wxn.reader.data.model.opds.PredefinedCatalogItem
import kotlinx.coroutines.flow.Flow

interface OpdsRepository {
    fun getAllCatalogs(): Flow<List<OpdsCatalogEntity>>
    fun getAllEnabledCatalogs(): Flow<List<OpdsCatalogEntity>>
    suspend fun getCatalogById(id: Long): OpdsCatalogEntity?
    suspend fun addCatalog(catalog: OpdsCatalogEntity): Long
    suspend fun updateCatalog(catalog: OpdsCatalogEntity)
    suspend fun deleteCatalog(id: Long)
    suspend fun fetchFeed(url: String, catalogId: Long, useCache: Boolean = true): Result<OpdsFeed>
    suspend fun search(catalogId: Long, searchUrl: String, query: String, startIndex: Int = 0, count: Int = 50): Result<OpdsFeed>
    suspend fun validateCatalog(url: String, username: String? = null, password: String? = null): Result<OpdsFeed>
    suspend fun syncPredefinedCatalogs(catalogs: List<PredefinedCatalogItem>)
}
