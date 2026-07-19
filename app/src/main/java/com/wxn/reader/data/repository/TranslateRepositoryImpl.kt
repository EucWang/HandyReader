package com.wxn.reader.data.repository

import android.content.Context
import com.wxn.base.util.Logger
import com.wxn.reader.data.remote.api.TranslateApi
import com.wxn.reader.data.remote.dto.BaseResponse
import com.wxn.reader.data.remote.dto.SupportedLanguage
import com.wxn.reader.data.remote.dto.TranslateLanguagesResult
import com.wxn.reader.data.remote.dto.TranslateResult
import com.wxn.reader.domain.repository.TranslateRepository
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslateRepositoryImpl @Inject constructor(
    private val translateApi: TranslateApi,
    private val context: Context,
    private val json: Json
) : TranslateRepository {

    companion object {
        private const val CACHE_DIR_NAME = "translate"
        private const val CACHE_FILE_NAME = "supported_languages.json"
        private const val CACHE_VALIDITY_DAYS = 7L
    }

    @Volatile
    private var cachedLanguages: List<SupportedLanguage>? = null

    private val cacheFile: File by lazy {
        File(File(context.cacheDir, CACHE_DIR_NAME), CACHE_FILE_NAME)
    }

    override suspend fun getSupportedLanguages(): List<SupportedLanguage> {
        cachedLanguages?.let { return it }

        if (isLocalCacheValid()) {
            try {
                val cacheJson = cacheFile.readText()
                val result = json.decodeFromString<TranslateLanguagesResult>(cacheJson)
                cachedLanguages = result.supportedLanguages
                return result.supportedLanguages
            } catch (e: Exception) {
                Logger.e("TranslateRepository: Failed to read cache: ${e.message}")
            }
        }

        return fetchFromRemote()
    }

    override suspend fun translate(
        text: String,
        targetLang: String,
        sourceLang: String
    ): Result<BaseResponse<TranslateResult>> {
        return translateApi.translate(text, targetLang, sourceLang)
    }

    private fun isLocalCacheValid(): Boolean {
        if (!cacheFile.exists()) return false
        val validityMillis = TimeUnit.DAYS.toMillis(CACHE_VALIDITY_DAYS)
        return (System.currentTimeMillis() - cacheFile.lastModified()) <= validityMillis
    }

    private suspend fun fetchFromRemote(): List<SupportedLanguage> {
        val result = translateApi.getSupportedLanguages()
        return result.fold(
            onSuccess = { response ->
                response.data?.let { data ->
                    saveLocalCache(data)
                    cachedLanguages = data.supportedLanguages
                    data.supportedLanguages
                } ?: run {
                    Logger.e("TranslateRepository: Remote response data is null")
                    fallbackToLocalCache()
                }
            },
            onFailure = { e ->
                Logger.e("TranslateRepository: Failed to fetch languages: ${e.message}")
                fallbackToLocalCache()
            }
        )
    }

    private fun saveLocalCache(data: TranslateLanguagesResult) {
        try {
            cacheFile.parentFile?.mkdirs()
            val jsonString = json.encodeToString(TranslateLanguagesResult.serializer(), data)
            cacheFile.writeText(jsonString)
        } catch (e: Exception) {
            Logger.e("TranslateRepository: Failed to save cache: ${e.message}")
        }
    }

    private fun fallbackToLocalCache(): List<SupportedLanguage> {
        if (cacheFile.exists()) {
            try {
                val cacheJson = cacheFile.readText()
                val result = json.decodeFromString<TranslateLanguagesResult>(cacheJson)
                cachedLanguages = result.supportedLanguages
                return result.supportedLanguages
            } catch (e: Exception) {
                Logger.e("TranslateRepository: Failed to read fallback cache: ${e.message}")
            }
        }
        return emptyList()
    }
}
