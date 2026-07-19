package com.wxn.reader.data.remote.api

import com.wxn.reader.data.model.WordResult
import com.wxn.reader.data.remote.dto.BaseResponse
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryApiImpl @Inject constructor(
    private val httpClient: HttpClient
) : DictionaryApi {

    override suspend fun lookup(word: String, lang: String): Result<BaseResponse<WordResult>> {
        return BaseApi.get(
            httpClient,
            "${Constants.DICTIONARY_BASE_URL}${ApiPath.API_DICTIONARY}",
            mapOf("word" to word, "lang" to lang),
            isFullUrl = true
        )
    }
}
