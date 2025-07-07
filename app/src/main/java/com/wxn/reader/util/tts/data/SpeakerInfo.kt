package com.wxn.reader.util.tts.data

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Parcelize
@Immutable
@Serializable
data class SpeakerInfo(

    @SerialName("Name")
    val name: String,
    @SerialName("ShortName")

    val shortName: String,

    @SerialName("Gender")
    val gender: String,

    @SerialName("Locale")
    val locale: String,

    @SerialName("SuggestedCodec")
    val suggestedCodec: String,

    @SerialName("FriendlyName")
    val friendlyName: String,

    @SerialName("Status")
    val status: String,

    @SerialName("VoiceTag")
    val voiceTag: Tag
) : Parcelable

@Parcelize
@Immutable
@Serializable
data class Tag(

    @SerialName("ContentCategories")
    val contentCategories: List<String>,

    @SerialName("VoicePersonalities")
    val voicePersonalities: List<String>

) : Parcelable