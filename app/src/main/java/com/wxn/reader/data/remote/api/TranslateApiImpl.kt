package com.wxn.reader.data.remote.api

import com.wxn.reader.data.remote.dto.BaseResponse
import com.wxn.reader.data.remote.dto.TranslateLanguagesResult
import com.wxn.reader.data.remote.dto.TranslateRequest
import com.wxn.reader.data.remote.dto.TranslateResult
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TranslateApiImpl @Inject constructor(
    private val httpClient: HttpClient
) : TranslateApi {

    override suspend fun translate(text: String, targetLang: String, sourceLang: String): Result<BaseResponse<TranslateResult>> {
        return BaseApi.post(httpClient, ApiPath.API_TRANSLATE, TranslateRequest(
            text = text,
            targetLang = targetLang,
            sourceLang = sourceLang
        ))
    }

    override suspend fun getSupportedLanguages(): Result<BaseResponse<TranslateLanguagesResult>> {
        return BaseApi.get(httpClient, ApiPath.API_TRANSLATE_LANGUAGES)
    }
}
