package com.wxn.reader.util.download

import com.wxn.base.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named

class OKHttpStringStreamer @Inject constructor(
    @Named("DownloadOkHttpClient") private val okHttpClient: OkHttpClient
) {

    suspend fun getStringFromUrl(url: String): Result<String> = try {
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body
                ?: throw IllegalStateException("Response body is null")
            try {
                Result.success(body.string())
            } finally {
                response.close()
            }
        }
    } catch (ex: Exception) {
        Logger.w("OKHttpStringStreamer::getStringFromUrl: error: $ex")
        Result.failure(ex)
    }
}