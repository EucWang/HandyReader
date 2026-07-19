package com.wxn.reader.data.remote.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.wxn.base.util.Logger
import com.wxn.reader.BuildConfig
import com.wxn.reader.data.remote.api.ApiPath
import com.wxn.reader.data.remote.api.Constants
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class TokenManager @Inject constructor(
    @Named("TokenOkHttpClient") private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    private var cachedToken: String? = null
    private var expiresAt: Long = 0L
    private val lock = Any()
    private val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_EXPIRES = "auth_expires_at"
        private const val BUFFER_SECONDS = 300L
        private const val TAG = "TokenManager"
    }

    fun getToken(): String {
        synchronized(lock) {
            if (cachedToken != null && now() < expiresAt - BUFFER_SECONDS) {
                return cachedToken!!
            }

            val stored = prefs.getString(KEY_TOKEN, null)
            val storedExp = prefs.getLong(KEY_EXPIRES, 0)
            if (stored != null && now() < storedExp - BUFFER_SECONDS) {
                cachedToken = stored
                expiresAt = storedExp
                return stored
            }

            return fetchNewToken()
        }
    }

    private fun fetchNewToken(): String {
        //TODO 当备份/还原 提供的deviceId 完成之后，这里也可以用上了
        val deviceId = ""
        val appVersion = BuildConfig.VERSION_NAME

        val requestBody = json.encodeToString(
            TokenRequest.serializer(),
            TokenRequest(deviceId = deviceId, appVersion = appVersion, source = "android")
        )

        val request = Request.Builder()
            .url("${Constants.BASE_URL}${ApiPath.API_AUTH_TOKEN}")
            .header("X-API-Key", BuildConfig.API_KEY)
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use {
            val body = it.body?.string()
                ?: throw RuntimeException("Token fetch failed: empty body")

            if (!it.isSuccessful) {
                val errMsg = try {
                    JSONObject(body).optString("message", "HTTP ${it.code}")
                } catch (_: Exception) {
                    "Token fetch failed: HTTP ${it.code}"
                }
                Logger.e("$TAG: $errMsg")
                throw RuntimeException(errMsg)
            }

            val jsonObj = JSONObject(body)
            if (!jsonObj.optBoolean("success", false)) {
                val errMsg = jsonObj.optString("message", "Token fetch failed")
                Logger.e("$TAG: $errMsg")
                throw RuntimeException(errMsg)
            }

            val data = jsonObj.getJSONObject("data")
            val token = data.getString("token")
            val exp = data.getLong("expires_at")

            cachedToken = token
            expiresAt = exp
            prefs.edit()
                .putString(KEY_TOKEN, token)
                .putLong(KEY_EXPIRES, exp)
                .apply()

            Logger.i("$TAG: Token refreshed successfully, expires_at=$exp")
            return token
        }
    }

    fun clearToken() {
        synchronized(lock) {
            cachedToken = null
            expiresAt = 0L
            prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_EXPIRES)
                .apply()
        }
    }

    private fun now(): Long = System.currentTimeMillis() / 1000
}
