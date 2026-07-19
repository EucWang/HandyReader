package com.wxn.reader.util.tts.data

import com.wxn.reader.util.tts.repository.Voice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Speaker(
    val id: String = "",

    val index: Int,
    val name: String,
    val gender: String,
    val locale: String = "",

    @SerialName("desc")
    val description: String = "",
    var active: Boolean = false,

    @SerialName("sample_voice")
    val sampleVoice: String
) {

    companion object {
        fun from(index: Int, voice: Voice, activeId: String): Speaker {
            val description = voice.voicePersonalities + voice.contentCategories
            return Speaker(
                id = voice.name,
                index = index,
                name = extractVoiceName(voice),
                gender = voice.gender,
                locale = voice.locale,
                description = description,
                active = activeId == voice.name,
                sampleVoice = ""
            )
        }
    }
}

// en-US-AvaMultilingualNeural 提取 AvaMultilingualNeural 删除 Neural
fun extractVoiceName(voice: Voice) = voice.shortName.split('-').last().replace("Neural", "")
