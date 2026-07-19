package com.wxn.reader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranslateRequest(
    @SerialName("text")
    val text: String,
    @SerialName("target_lang")
    val targetLang: String,
    @SerialName("source_lang")
    val sourceLang: String? = null
)
