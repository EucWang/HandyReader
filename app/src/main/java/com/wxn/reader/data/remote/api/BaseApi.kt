package com.wxn.reader.data.remote.api

import com.wxn.base.util.Logger
import com.wxn.reader.data.remote.dto.BaseResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import java.net.UnknownHostException

/***
 * 基础网络接口请求get/post封装
 */
object BaseApi {

    suspend inline fun <reified T> get(
        httpClient: HttpClient,
        api: String,
        params: Map<String, Any?> = emptyMap()
    ): Result<BaseResponse<T>> {
        return try {
            val response = httpClient.get("${Constants.BASE_URL}$api") {
                contentType(ContentType.Application.Json)

                for (item in params) {
                    parameter(item.key, item.value)
                }
            }.body<BaseResponse<T>>()

            if (response.success == true) {
                Result.success(response)
            } else {
                Result.failure(
                    ApiBaseException(
                        response.code,
                        response.message
                    )
                )
            }
        } catch (e: ClientRequestException) {
            Logger.e("BaseApi: HTTP error ${e.response.status.value}")
            // HTTP 错误 (400, 500等) - 尝试读取错误响应
            val errorResponse = try {
                e.response.body<BaseResponse<T>>()
            } catch (_: Exception) {
                null
            }

            val errorMsg = errorResponse?.message
                ?: "HTTP ${e.response.status.value} error"

            Result.failure(
                ApiBaseException(
                    errorResponse?.code ?: ApiCode.CODE_SERV_ERROR,
                    errorMsg,
                )
            )
        } catch (e: UnknownHostException) {
            Logger.e("BaseApi: Network unavailable - ${e.message}")
            // 网络不可达
            Result.failure(
                ApiBaseException(
                    ApiCode.CODE_SERV_UNKOWN,
                    "Network unavailable",
                )
            )
        } catch (e: SerializationException) {
            Logger.e("BaseApi: Data format error - ${e.message}")
            // JSON 解析错误
            Result.failure(
                ApiBaseException(
                    ApiCode.CODE_SERIALIZATION_ERROR,
                    "Data format error: ${e.message}",
                )
            )
        } catch (e: Exception) {
            Logger.e("BaseApi: Unexpected error - ${e.message}")
            // 其他错误
            Result.failure(
                ApiBaseException(
                    ApiCode.CODE_UNKNOWN_ERROR,
                    e.message ?: "Unknown error",
                )
            )
        }
    }

    suspend inline fun <reified T, reified V> post(
        httpClient: HttpClient,
        api: String,
        request: V
    ): Result<BaseResponse<T>> {
        return try {
            val response = httpClient.post(Constants.BASE_URL + api) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body<BaseResponse<T>>()

            if (response.success == true) {
                Logger.i("BaseApi: successful - ${response.message}")
                Result.success(response)
            } else {
                Result.failure(
                    ApiBaseException(
                        response.code,
                        response.message
                    )
                )
            }
        } catch (e: ClientRequestException) {
            Logger.e("BaseApi: HTTP error ${e.response.status.value}")
            // HTTP 错误 (400, 500等) - 尝试读取错误响应
            val errorResponse = try {
                e.response.body<BaseResponse<T>>()
            } catch (_: Exception) {
                null
            }

            val errorMsg = errorResponse?.message
                ?: "HTTP ${e.response.status.value} error"

            Result.failure(
                ApiBaseException(
                    errorResponse?.code ?: ApiCode.CODE_SERV_ERROR,
                    errorMsg,
                )
            )
        } catch (e: UnknownHostException) {
            Logger.e("BaseApi: Network unavailable - ${e.message}")
            // 网络不可达
            Result.failure(
                ApiBaseException(
                    ApiCode.CODE_SERV_UNKOWN,
                    "Network unavailable"
                )
            )
        } catch (e: SerializationException) {
            Logger.e("BaseApi: Data format error - ${e.message}")
            // JSON 解析错误
            Result.failure(
                ApiBaseException(
                    ApiCode.CODE_SERIALIZATION_ERROR,
                    "Data format error: ${e.message}"
                )
            )
        } catch (e: Exception) {
            Logger.e("BaseApi: Unexpected error - ${e.message}")
            // 其他错误
            Result.failure(
                ApiBaseException(
                    ApiCode.CODE_UNKNOWN_ERROR,
                    e.message ?: "Unknown error"
                )
            )
        }
    }
}