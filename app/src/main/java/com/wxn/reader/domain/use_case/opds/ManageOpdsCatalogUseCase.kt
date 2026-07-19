package com.wxn.reader.domain.use_case.opds

import com.wxn.reader.data.dto.OpdsCatalogEntity
import com.wxn.reader.data.source.local.OpdsCredentialStore
import com.wxn.reader.domain.repository.OpdsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ManageOpdsCatalogUseCase @Inject constructor(
    private val opdsRepository: OpdsRepository,
    private val credentialStore: OpdsCredentialStore
) {
    fun getAllCatalogs(): Flow<List<OpdsCatalogEntity>> = opdsRepository.getAllCatalogs()

    fun getAllEnabledCatalogs(): Flow<List<OpdsCatalogEntity>> = opdsRepository.getAllEnabledCatalogs()

    suspend fun addCatalog(
        name: String,
        url: String,
        description: String? = null,
        iconUrl: String? = null,
        authType: String = "NONE",
        username: String? = null,
        password: String? = null
    ): Result<Long> {
        return try {
            val catalog = OpdsCatalogEntity(
                name = name,
                url = url.trimEnd('/'),
                description = description,
                iconUrl = iconUrl,
                authType = authType
            )
            val id = opdsRepository.addCatalog(catalog)
            if (username != null && password != null) {
                credentialStore.saveCredentials(id, username, password)
            }
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCatalog(catalog: OpdsCatalogEntity) {
        opdsRepository.updateCatalog(catalog)
    }

    suspend fun deleteCatalog(id: Long) {
        opdsRepository.deleteCatalog(id)
    }

    suspend fun saveCredentials(catalogId: Long, username: String, password: String) {
        credentialStore.saveCredentials(catalogId, username, password)
    }

    suspend fun hasCredentials(catalogId: Long): Boolean {
        return credentialStore.hasCredentials(catalogId)
    }
}
