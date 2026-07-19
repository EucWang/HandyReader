package com.wxn.reader.util.download


import com.wxn.base.util.Logger
import okhttp3.OkHttpClient
import okhttp3.Request

/***
 * 服务器是否支持断点续传, 以及其他服务器相关信息
 */
data class ServerCapabilities(
    val supportsRange: Boolean = false,
    val contentLength: Long? = null,
    val etag: String? = null,
    val lastModified: String? = null
)

/****
 * 服务器资源探测工具, 检测服务器是否支持断点续传
 */
class ServerCapabilityDetector(private val okHttpClient: OkHttpClient) {
    suspend fun detectCapabilities(url: String): ServerCapabilities {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            val response = okHttpClient.newCall(request).execute()

            ServerCapabilities(
                supportsRange = response.header("Accept-Ranges") == "bytes",
                contentLength = response.header("Content-Length")?.toLongOrNull(),
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified")
            ).also {
                response.close()
            }
        } catch (e: Exception) {
            Logger.w("ServerCapabilityDetector: failed to detect capabilities for $url: ${e.message}")
            ServerCapabilities()  // 返回默认值
        }
    }
}