package com.wxn.reader.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WordResult(
    val word: String = "",
    val lang: String = "en",
    val source: String? = null,
    val phonetic: String? = null,
    val phonetics: List<PhoneticItem> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val sourceUrls: List<String> = emptyList(),
    val definitions: List<DictDefinition> = emptyList(),
    val lemma: String? = null,
    val root: String? = null
) {
    val hasResult: Boolean get() = definitions.isNotEmpty() || synonyms.isNotEmpty()

    fun filterChineseDefinitions(): WordResult {
        return copy(
            definitions = definitions.filter { it.note != DictDefinition.CHINESE_NOTE }
        )
    }

//    fun normalizeDefinitions(): WordResult {
//        return copy(definitions = definitions.map { it.normalize() })
//    }

    fun convertChinese(converter: (String) -> String): WordResult {
        return copy(
            word = converter(word),
            phonetic = phonetic?.let { converter(it) },
            phonetics = phonetics.map { item ->
                item.copy(text = item.text?.let { converter(it) })
            },
            definitions = definitions.map { def ->
                def.copy(
                    partOfSpeech = converter(def.partOfSpeech),
                    definition = converter(def.definition),
                    example = def.example?.let { converter(it) },
                    synonyms = def.synonyms.map { converter(it) },
                    antonyms = def.antonyms.map { converter(it) },
                    note = def.note?.let { converter(it) }
                )
            },
            synonyms = synonyms.map { converter(it) },
            antonyms = antonyms.map { converter(it) },
            lemma = lemma?.let { converter(it) },
            root = root?.let { converter(it) }
        )
    }
}

@Serializable
data class PhoneticItem(
    val text: String? = null,
    val audio: String? = null
) {
    val hasAudio: Boolean get() = !audio.isNullOrBlank()
}

@Serializable
data class DictDefinition(
    val partOfSpeech: String = "",
    val definition: String = "",
    val example: String? = null,
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val note: String? = null
) {
//    fun normalize(): DictDefinition {
//        val (pos, def) = Companion.normalize(partOfSpeech, definition)
//        return if (pos != partOfSpeech || def != definition) copy(partOfSpeech = pos, definition = def) else this
//    }

    companion object {
        const val CHINESE_NOTE = "中文释义"

//        private val POS_PREFIX_REGEX = Regex(
//            """^(interj|modal|abbr|adj|adv|prep|conj|pron|num|art|aux|vi|vt|n|v)\s+(.+)"""
//        )
//
//        fun normalize(partOfSpeech: String, definition: String): Pair<String, String> {
//            if (partOfSpeech != "unknown" && partOfSpeech.isNotBlank()) return partOfSpeech to definition
//            if (definition.length <= 4) return "" to definition
//            val match = POS_PREFIX_REGEX.matchEntire(definition) ?: return "" to definition
//            val extractedDef = match.groupValues[2]
//            if (extractedDef.isBlank()) return "" to definition
//            return match.groupValues[1] to extractedDef
//        }
    }
}
