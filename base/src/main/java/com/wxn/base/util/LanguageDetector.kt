package com.wxn.base.util

import kotlin.math.max

object LanguageDetector {

    private val supportedCodes = setOf(
        "zh", "en", "fr", "de", "es", "pt", "ja", "hi", "ru", "ar",
        "ko", "th", "he", "bn", "ta", "te", "kn", "ml",
        "vi", "my", "km", "lo", "hy", "ka", "am", "bo",
        "pl", "ro", "cs", "hu", "tr", "sv", "hr"
    )

    private val RTL_CODES = setOf("ar", "fa", "ur", "he", "ps", "ckb", "ug", "sd", "yi", "syr")

    // ── Level 1: 高频虚词表（≤4 字符，各语言 top 12 高频虚词） ──
    private val FUNCTION_WORDS = mapOf(
        "en" to setOf("the", "and", "to", "of", "in", "is", "it", "you", "that", "was", "for", "are"),
        "de" to setOf("der", "die", "das", "ist", "nicht", "und", "zu", "mit", "sich", "auf", "ein", "eine"),
        "fr" to setOf("le", "la", "les", "des", "est", "que", "pas", "dans", "une", "sur", "par", "pour"),
        "es" to setOf("el", "la", "que", "y", "es", "por", "del", "con", "una", "para", "los", "las"),
        "pt" to setOf("o", "a", "os", "as", "que", "e", "do", "da", "em", "um", "para", "com"),
        "it" to setOf("il", "la", "le", "gli", "che", "e", "di", "a", "in", "una", "per", "con"),
        "nl" to setOf("de", "het", "een", "en", "van", "in", "is", "te", "met", "op", "voor", "aan"),
        "pl" to setOf("i", "w", "na", "z", "do", "się", "to", "nie", "jak", "ma", "że", "od"),
        "cs" to setOf("a", "v", "na", "je", "se", "do", "to", "s", "z", "pro", "o", "že"),
        "hu" to setOf("a", "az", "és", "nem", "hogy", "van", "egy", "is", "meg", "még", "volna", "csak"),
        "tr" to setOf("bir", "ve", "bu", "ile", "için", "olarak", "olan", "ama", "daha", "ise", "her", "gibi"),
        "sv" to setOf("och", "i", "att", "det", "som", "en", "på", "är", "med", "han", "sig", "för"),
        "hr" to setOf("i", "u", "je", "se", "na", "za", "da", "s", "o", "od", "iz", "što"),
        "ro" to setOf("și", "de", "la", "în", "pe", "o", "un", "cu", "este", "are", "din", "mai"),
        "vi" to setOf("của", "và", "có", "là", "không", "được", "cho", "trong", "một", "với", "người", "các")
    )

    fun detectLanguage(text: String): String? {
        if (text.isBlank()) return null

        var zhCount = 0
        var jaCount = 0
        var koCount = 0
        var thCount = 0
        var loCount = 0

        var hiCount = 0
        var bnCount = 0
        var paCount = 0
        var guCount = 0
        var orCount = 0
        var taCount = 0
        var teCount = 0
        var knCount = 0
        var mlCount = 0
        var siCount = 0
        var myCount = 0
        var kmCount = 0

        var ruCount = 0
        var arCount = 0
        var heCount = 0
        var syrCount = 0
        var elCount = 0
        var hyCount = 0
        var kaCount = 0
        var amCount = 0
        var boCount = 0

        var latinCount = 0

        // 拉丁语系特征字母计数器
        var deSpecial = 0
        var frSpecial = 0
        var esSpecial = 0
        var ptSpecial = 0
        var plSpecial = 0
        var roSpecial = 0
        var csSpecial = 0
        var huSpecial = 0
        var trSpecial = 0
        var scSpecial = 0
        var hrSpecial = 0
        var viSpecial = 0

        // 标点特征计数器
        var guillemetCount = 0       // « »
        var germanQuoteCount = 0     // „ "
        var ordinalCount = 0         // º ª

        for (char in text) {
            val code = char.code
            when {
                code in 0x4E00..0x9FFF -> zhCount++
                code in 0x3400..0x4DBF -> zhCount++
                code in 0x3000..0x303F -> jaCount++
                code in 0x3040..0x309F -> jaCount += 3
                code in 0x30A0..0x30FF -> jaCount += 3

                code in 0xAC00..0xD7AF -> koCount++
                code in 0x1100..0x11FF -> koCount++
                code in 0x3130..0x318F -> koCount++

                code in 0x0E00..0x0E7F -> thCount++
                code in 0x0E80..0x0EFF -> loCount++

                code in 0x0900..0x097F -> hiCount++
                code in 0xA8E0..0xA8FF -> hiCount++
                code in 0x0980..0x09FF -> bnCount++
                code in 0x0A00..0x0A7F -> paCount++
                code in 0x0A80..0x0AFF -> guCount++
                code in 0x0B00..0x0B7F -> orCount++
                code in 0x0B80..0x0BFF -> taCount++
                code in 0x0C00..0x0C7F -> teCount++
                code in 0x0C80..0x0CFF -> knCount++
                code in 0x0D00..0x0D7F -> mlCount++
                code in 0x0D80..0x0DFF -> siCount++

                code in 0x1000..0x109F -> myCount++
                code in 0xA9E0..0xA9FF -> myCount++
                code in 0xAA60..0xAA7F -> myCount++
                code in 0x1780..0x17FF -> kmCount++
                code in 0x19E0..0x19FF -> kmCount++

                code in 0x0400..0x04FF -> ruCount++
                code in 0x0500..0x052F -> ruCount++

                code in 0x0600..0x06FF -> arCount++
                code in 0x0750..0x077F -> arCount++
                code in 0x08A0..0x08FF -> arCount++
                code in 0x0590..0x05FF -> heCount++
                code in 0x0700..0x074F -> syrCount++

                code in 0x0370..0x03FF -> elCount++
                code in 0x0530..0x058F -> hyCount++
                code in 0x10A0..0x10FF -> kaCount++
                code in 0x2D00..0x2D2F -> kaCount++
                code in 0x1200..0x137F -> amCount++
                code in 0x1380..0x139F -> amCount++
                code in 0x2D80..0x2DDF -> amCount++
                code in 0xAB00..0xAB2F -> amCount++
                code in 0x0F00..0x0FFF -> boCount++

                code in 0x0041..0x007A -> latinCount++

                code in 0x00C0..0x024F -> {
                    latinCount++
                    when (code) {
                        0x00DF, 0x1E9E -> deSpecial++
                        0x0152, 0x0153 -> frSpecial++
                        0x00D1, 0x00F1 -> esSpecial++
                        0x00BF, 0x00A1 -> esSpecial++
                        0x00C3, 0x00E3, 0x00D5, 0x00F5 -> ptSpecial++

                        0x0105, 0x0104, 0x0107, 0x0106,
                        0x0119, 0x0118, 0x0142, 0x0141,
                        0x0144, 0x0143, 0x015B, 0x015A,
                        0x017A, 0x0179, 0x017C, 0x017B -> plSpecial++

                        0x0103, 0x0102, 0x0219, 0x0218, 0x021B, 0x021A -> roSpecial++

                        0x010D, 0x010C, 0x010F, 0x010E,
                        0x011B, 0x011A, 0x0148, 0x0147,
                        0x0159, 0x0158, 0x0165, 0x0164,
                        0x016F, 0x016E, 0x017E, 0x017D -> csSpecial++

                        0x0151, 0x0150, 0x0171, 0x0170 -> huSpecial++
                        0x011F, 0x011E, 0x015F, 0x015E, 0x0131 -> trSpecial++

                        0x00E5, 0x00C5, 0x00E6, 0x00C6,
                        0x00F8, 0x00D8 -> scSpecial++
                        0x0111, 0x0110 -> hrSpecial++
                    }
                }
                code in 0x1E00..0x1EFF -> {
                    latinCount++
                    viSpecial++
                }

                // ── 标点特征 ──
                code == 0x00AB || code == 0x00BB -> {
                    latinCount++
                    guillemetCount++     // « » — 法语/意大利语等
                }
                code == 0x201E || code == 0x201D ||
                code == 0x201A || code == 0x2018 -> {
                    latinCount++
                    germanQuoteCount++   // „" ‚' — 德语/东欧语言引号
                }
                code == 0x00AA || code == 0x00BA -> {
                    latinCount++
                    ordinalCount++       // ª º — 西葡序数符
                }
            }
        }

        val scriptCounts = mapOf(
            "zh" to zhCount, "ja" to jaCount, "ko" to koCount,
            "th" to thCount, "lo" to loCount,
            "hi" to hiCount, "bn" to bnCount, "pa" to paCount,
            "gu" to guCount, "or" to orCount, "ta" to taCount,
            "te" to teCount, "kn" to knCount, "ml" to mlCount,
            "si" to siCount, "my" to myCount, "km" to kmCount,
            "ru" to ruCount,
            "ar" to arCount, "he" to heCount, "syr" to syrCount,
            "el" to elCount, "hy" to hyCount,
            "ka" to kaCount, "am" to amCount, "bo" to boCount,
            "en" to latinCount
        )

        val maxEntry = scriptCounts.maxByOrNull { it.value } ?: return null
        if (maxEntry.value == 0) return null

        if (maxEntry.key == "zh" && jaCount > zhCount * 2) return "ja"

        if (maxEntry.key == "en") {
            return resolveLatinLanguage(
                text = text,
                latinCount = latinCount,
                viSpecial = viSpecial,
                deSpecial = deSpecial,
                frSpecial = frSpecial,
                esSpecial = esSpecial,
                ptSpecial = ptSpecial,
                plSpecial = plSpecial,
                roSpecial = roSpecial,
                csSpecial = csSpecial,
                huSpecial = huSpecial,
                trSpecial = trSpecial,
                scSpecial = scSpecial,
                hrSpecial = hrSpecial,
                guillemetCount = guillemetCount,
                germanQuoteCount = germanQuoteCount,
                ordinalCount = ordinalCount
            )
        }

        return maxEntry.key
    }

    // ── Level 1: 高频虚词检测 ──
    private fun quickWordCheck(text: String): List<Pair<String, Int>> {
        val words = text.lowercase().split(Regex("\\W+")).filter { it.length in 1..4 }
        val hits = mutableMapOf<String, Int>()
        for (w in words) {
            for ((lang, dict) in FUNCTION_WORDS) {
                if (w in dict) {
                    hits[lang] = hits.getOrDefault(lang, 0) + 1
                }
            }
        }
        return hits.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    // ── Level 3: 德语名词大写检测 ──
    private fun hasGermanCapitalization(text: String): Boolean {
        if (text.length < 80) return false
        val words = text.split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.size < 10) return false

        // 句中大写词比例（排除句首第一个词）
        var capitalizedMidSentence = 0
        var checked = 0
        var sentenceStart = true
        for (w in words) {
            val first = w.first()
            if (sentenceStart) {
                sentenceStart = first in 'A'..'Z'
                continue
            }
            if (first in 'A'..'Z') {
                capitalizedMidSentence++
            }
            checked++
            if (w.endsWith('.') || w.endsWith('!') || w.endsWith('?')) {
                sentenceStart = true
            }
        }
        return checked > 0 && capitalizedMidSentence.toFloat() / checked > 0.15f
    }

    // ── 拉丁语系三层解析 ──
    private fun resolveLatinLanguage(
        text: String,
        latinCount: Int,
        viSpecial: Int,
        deSpecial: Int,
        frSpecial: Int,
        esSpecial: Int,
        ptSpecial: Int,
        plSpecial: Int,
        roSpecial: Int,
        csSpecial: Int,
        huSpecial: Int,
        trSpecial: Int,
        scSpecial: Int,
        hrSpecial: Int,
        guillemetCount: Int,
        germanQuoteCount: Int,
        ordinalCount: Int
    ): String {
        // Level 1: 高频虚词检测（需足够长的文本来获得统计意义）
        val wordHits = quickWordCheck(text)
        if (wordHits.isNotEmpty()) {
            val (topLang, topScore) = wordHits.first()
            val secondScore = wordHits.getOrNull(1)?.second ?: 0
            // 顶层虚词命中 ≥3 且是第二名的 2 倍以上 → 置信
            if (topScore >= 3 && topScore >= secondScore * 2) {
                return topLang
            }
            // 若虚词信号不足，降级到 Level 2
        }

        // Level 2: diacritics + 标点特征
        // 越南语: 变音符号密度极高
        if (viSpecial > latinCount * 0.03) return "vi"

        // ß → 德语
        if (deSpecial > 0) return "de"
        // œ → 法语
        if (frSpecial > 0) return "fr"

        // ñ/¿/¡ → 西班牙语
        if (esSpecial > latinCount * 0.005) return "es"
        // ã/õ → 葡萄牙语
        if (ptSpecial > latinCount * 0.005) return "pt"

        // 东欧拉丁各语系 diacritics
        if (plSpecial > 0) return "pl"
        if (roSpecial > 0) return "ro"
        if (csSpecial > 0) return "cs"
        if (huSpecial > 0) return "hu"
        if (trSpecial > 0) return "tr"
        if (scSpecial > 0) return "sv"
        if (hrSpecial > 0) return "hr"

        // Level 2 兜底: 标点特征辅助区分
        // 德语/东欧引号 „" 且无其他 diacritics 信号
        if (germanQuoteCount > 0) return "de"
        // « » 倾向法语/意大利语（无其他信号时归入法语）
        if (guillemetCount > 0) return "fr"
        // º ª 倾向西班牙语/葡萄牙语（无其他信号时归入西班牙语）
        if (ordinalCount > 0) return "es"

        // Level 3: 德语大写检测（无变音符号的德语文本）
        if (hasGermanCapitalization(text)) return "de"

        return "en"
    }

    fun isRtl(language: String?): Boolean =
        !language.isNullOrBlank() && language.split("-").first().lowercase() in RTL_CODES
}
