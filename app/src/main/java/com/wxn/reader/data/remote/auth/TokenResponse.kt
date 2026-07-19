package com.wxn.reader.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TokenResponse(
    @SerialName("token") val token: String,
    @SerialName("expires_at") val expiresAt: Long,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class TokenRequest(
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("source") val source: String = "android"
)
