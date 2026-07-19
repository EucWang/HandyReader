package com.wxn.reader.data.remote.api

import com.wxn.reader.data.remote.dto.BaseResponse
import com.wxn.reader.domain.model.TTSModelsList
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TTSModelsApiImpl @Inject constructor(
    private val httpClient: HttpClient
) : TTSModelsApi {
    override suspend fun ttsModelsList(
        page: Int,
        pageSize: Int
    ): Result<BaseResponse<TTSModelsList>> {
        return BaseApi.get<TTSModelsList>(httpClient, ApiPath.API_TTS_MODELS,
            mapOf(
                "page" to page,
                "page_size" to pageSize
            ))
    }
}