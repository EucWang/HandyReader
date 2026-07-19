package com.wxn.reader.domain.model

import com.wxn.reader.util.tts.data.Speaker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class TTModelBaseData(
    val name: String,
    val url: String,
    val size: Int
)

/***
 */
@Serializable
data class TTSModelData(
    val name: String,
    val url: String,
    val type: String,
    val locale: String,
    val size: String,
    val base: List<TTModelBaseData>?,

    @SerialName("process-speed")
    val processSpeed: Float,

    @SerialName("quality")
    val quality: Float,
    val speakers_num: Int,
    val speakers: List<Speaker>,

    val license: String? = "",


    @SerialName("license_url")
    val licenseUrl: String? = "",

    val remark: String? = "",
)

@Serializable
data class TTSModelsList(

    @SerialName("list")
    val list: List<TTSModelData>
)