package com.wxn.reader.data.repository

import com.wxn.reader.data.dto.DictionaryCacheEntity
import com.wxn.reader.data.model.WordResult
import com.wxn.reader.data.remote.api.ApiBaseException
import com.wxn.reader.data.remote.api.ApiCode
import com.wxn.reader.data.remote.api.DictionaryApi
import com.wxn.reader.data.source.local.DictionaryPrefsUtil
import com.wxn.reader.data.source.local.dao.DictionaryCacheDao
import com.wxn.reader.domain.repository.DictionaryRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepositoryImpl @Inject constructor(
    private val dictionaryApi: DictionaryApi,
    private val dictionaryCacheDao: DictionaryCacheDao,
    private val json: Json,
    private val dictionaryPrefsUtil: DictionaryPrefsUtil
) : DictionaryRepository {

    private val memoryCache = object : LinkedHashMap<String, WordResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WordResult>?): Boolean {
            return size > 20
        }
    }

    private fun cacheKey(word: String, lang: String) = "${word}|${lang}"

    override fun getCached(word: String, lang: String): WordResult? {
        return memoryCache[cacheKey(word, lang)]
    }

    override suspend fun lookup(word: String, lang: String): Result<WordResult> {
        memoryCache[cacheKey(word, lang)]?.let { return Result.success(it) }

        try {
            dictionaryCacheDao.getByWordAndLang(word, lang)?.let { entity ->
                val cached = json.decodeFromString<WordResult>(entity.dataJson)
                memoryCache[cacheKey(word, lang)] = cached
                return Result.success(cached)
            }
        } catch (_: Exception) { }

        if (!dictionaryPrefsUtil.canLookup()) {
            return Result.failure(
                ApiBaseException(
                    ApiCode.CODE_DAILY_LIMIT,
                    "Daily dictionary lookup limit reached"
                )
            )
        }

        val apiResult = dictionaryApi.lookup(word, lang)
        val result = if (apiResult.isSuccess) {
            val response = apiResult.getOrThrow()
            val data = response.data
            if (response.success == true && data != null) {
                Result.success(data)
            } else {
                Result.failure(
                    ApiBaseException(
                        response.code,
                        response.message
                    )
                )
            }
        } else {
            Result.failure(apiResult.exceptionOrNull() ?: Exception("Unknown error"))
        }

        if (result.isSuccess) {
            val data = result.getOrThrow()
            memoryCache[cacheKey(word, lang)] = data
            try {
                val entity = DictionaryCacheEntity(
                    word = word,
                    lang = lang,
                    dataJson = json.encodeToString(WordResult.serializer(), data)
                )
                dictionaryCacheDao.insertOrReplace(entity)
            } catch (_: Exception) { }
            dictionaryPrefsUtil.incrementLookupCount()
        }

        return result
    }
}
