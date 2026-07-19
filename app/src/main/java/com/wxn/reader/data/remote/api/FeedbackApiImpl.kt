package com.wxn.reader.data.remote.api

import com.wxn.reader.data.remote.dto.BaseResponse
import com.wxn.reader.data.remote.dto.FeedbackRequest
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackApiImpl @Inject constructor(
    private val httpClient: HttpClient
) : FeedbackApi {

    override suspend fun submitFeedback(request: FeedbackRequest): Result<BaseResponse<String?>> {
        return BaseApi.post(httpClient, ApiPath.API_FEEDBACK, request)
    }
}