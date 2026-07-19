package com.wxn.reader.data.repository

import com.wxn.base.util.Logger
import com.wxn.reader.data.dto.OpdsCatalogEntity
import com.wxn.reader.data.model.opds.OpdsFeed
import com.wxn.reader.data.model.opds.PredefinedCatalogItem
import com.wxn.reader.data.remote.opds.OpdsApiClient
import com.wxn.reader.data.source.local.OpdsBlacklistStore
import com.wxn.reader.data.source.local.OpdsCredentialStore
import com.wxn.reader.data.source.local.dao.OpdsCatalogDao
import com.wxn.reader.domain.repository.OpdsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpdsRepositoryImpl @Inject constructor(
    private val opdsCatalogDao: OpdsCatalogDao,
    private val opdsApiClient: OpdsApiClient,
    private val credentialStore: OpdsCredentialStore,
    private val blacklistStore: OpdsBlacklistStore
) : OpdsRepository {

    override fun getAllCatalogs(): Flow<List<OpdsCatalogEntity>> {
        return opdsCatalogDao.getAllCatalogs()
    }

    override fun getAllEnabledCatalogs(): Flow<List<OpdsCatalogEntity>> {
        return opdsCatalogDao.getAllEnabledCatalogs()
    }

    override suspend fun getCatalogById(id: Long): OpdsCatalogEntity? {
        return opdsCatalogDao.getCatalogById(id)
    }

    override suspend fun addCatalog(catalog: OpdsCatalogEntity): Long {
        return opdsCatalogDao.insertCatalog(catalog)
    }

    override suspend fun updateCatalog(catalog: OpdsCatalogEntity) {
        opdsCatalogDao.updateCatalog(catalog.copy(updatedAt = System.currentTimeMillis()))
    }

    override suspend fun deleteCatalog(id: Long) {
        val catalog = opdsCatalogDao.getCatalogById(id)
        if (catalog != null && catalog.isPredefined && !catalog.predefinedId.isNullOrEmpty()) {
            blacklistStore.addToBlacklist(catalog.predefinedId)
        }
        credentialStore.removeCredentials(id)
        opdsCatalogDao.deleteCatalog(id)
    }

    override suspend fun fetchFeed(url: String, catalogId: Long, useCache: Boolean): Result<OpdsFeed> {
        return opdsApiClient.fetchFeed(url, catalogId, useCache)
    }

    override suspend fun search(catalogId: Long, searchUrl: String, query: String, startIndex: Int, count: Int): Result<OpdsFeed> {
        return opdsApiClient.search(searchUrl, catalogId, query, startIndex, count)
    }

    override suspend fun validateCatalog(url: String, username: String?, password: String?): Result<OpdsFeed> {
        val tempCatalogId = -System.currentTimeMillis()
        return try {
            if (username != null && password != null) {
                credentialStore.saveCredentials(tempCatalogId, username, password)
            }
            opdsApiClient.fetchFeed(url, tempCatalogId, useCache = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { credentialStore.removeCredentials(tempCatalogId) } catch (e: Exception) {
                Logger.e("OpdsRepositoryImpl: Failed to cleanup temp credentials: $e")
            }
        }
    }

    override suspend fun syncPredefinedCatalogs(catalogs: List<PredefinedCatalogItem>) {
        val blacklistedIds = try {
            blacklistStore.getBlacklistedIds()
        } catch (e: Exception) {
            Logger.e("OpdsRepositoryImpl: Failed to read blacklist: $e")
            emptySet()
        }
        withContext(Dispatchers.IO) {
            for (item in catalogs) {
                if (item.id in blacklistedIds) continue
                if (opdsCatalogDao.catalogExistsByPredefinedId(item.id) == 0) {
                    opdsCatalogDao.insertCatalog(
                        OpdsCatalogEntity(
                            name = item.name,
                            url = item.url,
                            description = item.description,
                            iconUrl = item.iconUrl,
                            supportsSearch = item.supportsSearch,
                            authType = "NONE",
                            isPredefined = true,
                            predefinedId = item.id,
                            sortOrder = catalogs.indexOf(item)
                        )
                    )
                }
            }
        }
    }
}
