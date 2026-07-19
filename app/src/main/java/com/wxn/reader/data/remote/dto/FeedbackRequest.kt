package com.wxn.reader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeedbackRequest(
    @SerialName("name")
    val name: String? = null,  // 可选，1-50字符

    @SerialName("email")
    val email: String,         // 必需，有效邮箱

    @SerialName("type")
    val type: FeedbackType,    // 必需：bug, feature, other

    @SerialName("content")
    val content: String,       // 必需，10-500字符

    @SerialName("app_version")
    val appVersion: String? = null,  // 可选，1-20字符

    @SerialName("source")
    val source: String = "android"  // 固定值
)

enum class FeedbackType {

    @SerialName("bug")
    BUG,

    @SerialName("feature")
    FEATURE,

    @SerialName("other")
    OTHER
}

