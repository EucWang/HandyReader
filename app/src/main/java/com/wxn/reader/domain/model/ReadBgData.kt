package com.wxn.reader.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/***
 *
 */
@Serializable
data class ReadBgData(

    @SerialName("id")
    val id: String,

    @SerialName("image")
    val url: String,

    @SerialName("thumbnails")
    val thumbnailUrl: String,

    @SerialName("version")
    val version: Int,

    @Transient
    val path: String = "",

    @Transient
    val isDownloaded : Boolean = false
) {
}

@Serializable
data class ReadBgList(

    @SerialName("list")
    val list: List<ReadBgData>
)