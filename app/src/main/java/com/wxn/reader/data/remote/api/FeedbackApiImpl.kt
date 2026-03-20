package com.wxn.reader.data.remote.api

import com.wxn.base.util.Logger
import com.wxn.reader.data.remote.dto.FeedbackRequest
import com.wxn.reader.data.remote.dto.FeedbackResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerializationException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class FeedbackApiImpl @Inject constructor(
    private val httpClient: HttpClient
) : FeedbackApi {

    companion object {
        private val BASE_URL = com.wxn.reader.BuildConfig.FEEDBACK_API_URL
        private const val FEEDBACK_ENDPOINT = "/api/v1/feedback"
    }

    override suspend fun submitFeedback(request: FeedbackRequest): Result<FeedbackResponse> {
        return try {
            Logger.d("FeedbackApi: Submitting feedback - email=${request.email}, type=${request.type}")

            val response =  httpClient.post("$BASE_URL$FEEDBACK_ENDPOINT") {
                contentType(ContentType.Application.Json)
                setBody(request)

                headers {
                    append(HttpHeaders.UserAgent, "HandyReader-Android/${request.appVersion ?: "1.0"}")
                }
            }.body<FeedbackResponse>()

            // 检查响应是否成功
            if (response.isSuccess) {
                Logger.i("FeedbackApi: Submission successful - ${response.message}")
                Result.success(response)
            } else {
                Logger.w("FeedbackApi: Submission failed - ${response.errorMessage}")
                Result.failure(FeedbackException(
                    response.errorMessage ?: "Submission failed",
                    "API_ERROR"
                ))
            }
        } catch (e: ClientRequestException) {
            Logger.e("FeedbackApi: HTTP error ${e.response.status.value}")
            // HTTP 错误 (400, 500等) - 尝试读取错误响应
            val errorResponse = try {
                e.response.body<FeedbackResponse>()
            } catch (_: Exception) {
                null
            }

            val errorMsg = errorResponse?.errorMessage
                ?: "HTTP ${e.response.status.value} error"

            Result.failure(FeedbackException(errorMsg, "HTTP_${e.response.status.value}"))
        } catch (e: UnknownHostException) {
            Logger.e("FeedbackApi: Network unavailable - ${e.message}")
            // 网络不可达
            Result.failure(FeedbackException("Network unavailable", "NETWORK_ERROR"))
        } catch (e: SerializationException) {
            Logger.e("FeedbackApi: Data format error - ${e.message}")
            // JSON 解析错误
            Result.failure(FeedbackException("Data format error: ${e.message}", "SERIALIZATION_ERROR"))
        } catch (e: Exception) {
            Logger.e("FeedbackApi: Unexpected error - ${e.message}")
            // 其他错误
            Result.failure(FeedbackException(e.message ?: "Unknown error", "UNKNOWN_ERROR"))
        }
    }
}

/**
 * 自定义反馈异常
 */
class FeedbackException(
    message: String,
    val code: String? = null
) : Exception(message)