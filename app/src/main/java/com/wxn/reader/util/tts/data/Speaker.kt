package com.wxn.reader.util.tts.data

import com.wxn.reader.util.tts.repository.Voice

data class Speaker(
    val id: String,
    val name: String,
    val gender: String,
    val locale: String,
    val description: String,
    var active: Boolean = false,
) {
    companion object {
        fun from(voice: Voice, activeId: String): Speaker {
            val description = voice.voicePersonalities + voice.contentCategories
            return Speaker(
                id = voice.name,
                name = extractVoiceName(voice),
                gender = voice.gender,
                locale = voice.locale,
                description = description,
                active = activeId == voice.name
            )
        }
    }
}

// en-US-AvaMultilingualNeural 提取 AvaMultilingualNeural 删除 Neural
fun extractVoiceName(voice: Voice) = voice.shortName.split('-').last().replace("Neural", "")
