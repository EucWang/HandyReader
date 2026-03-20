package com.wxn.reader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedbackResponse(
    @SerialName("success")
    val success: Boolean? = null,

    @SerialName("message")
    val message: String? = null,

    @SerialName("error")
    val error: String? = null,

    @SerialName("details")
    val details: String? = null
) {
    val isSuccess: Boolean
        get() = success == true

    val errorMessage: String?
        get() = error ?: details
}

@Serializable
data class ErrorResponse(
    @SerialName("error")
    val error: String,

    @SerialName("code")
    val code: String? = null,

    @SerialName("details")
    val details: String? = null
)