package com.wxn.reader.data.remote.auth

import com.wxn.base.util.Logger
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class AuthInterceptor(
    private val tokenManager: TokenManager
) : Interceptor {

    companion object {
        private const val TAG = "AuthInterceptor"
        private val ERROR_JSON = """{"success":false,"code":"TOKEN_ERROR","message":"Failed to obtain auth token"}"""
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }

    private fun errorResponse(request: okhttp3.Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body(ERROR_JSON.toResponseBody(JSON_MEDIA_TYPE))
            .build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        if (path.endsWith("/api/v1/auth/token")) {
            val request = original.newBuilder()
                .header("Accept", "application/json")
                .method(original.method, original.body)
                .build()
            return chain.proceed(request)
        }

        val token = try {
            tokenManager.getToken()
        } catch (e: Exception) {
            Logger.w("$TAG: Token fetch failed, aborting request - ${e.message}")
            return errorResponse(original)
        }

        val requestBuilder = original.newBuilder()
            .header("Accept", "application/json")
            .method(original.method, original.body)

        if (token.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        if (response.code == 401 && token.isNotEmpty()) {
            response.close()
            Logger.w("$TAG: 401 received, clearing token and retrying")
            tokenManager.clearToken()

            val newToken = try {
                tokenManager.getToken()
            } catch (e: Exception) {
                Logger.e("$TAG: Token refresh on 401 failed - ${e.message}")
                return errorResponse(original)
            }

            val retryRequest = original.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .header("Accept", "application/json")
                .method(original.method, original.body)
                .build()
            return chain.proceed(retryRequest)
        }

        return response
    }
}
