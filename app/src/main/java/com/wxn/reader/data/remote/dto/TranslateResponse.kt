package com.wxn.reader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TranslateResult(
    @SerialName("source_lang")
    val sourceLang: String,
    @SerialName("target_lang")
    val targetLang: String,
    @SerialName("translated_text")
    val translatedText: String
)

@Serializable
data class TranslateLanguagesResult(
    @SerialName("service")
    val service: String,
    @SerialName("model")
    val model: String,
    @SerialName("supported_languages")
    val supportedLanguages: List<SupportedLanguage>
)

@Serializable
data class SupportedLanguage(
    @SerialName("code")
    val code: String,
    @SerialName("name")
    val name: String
)
