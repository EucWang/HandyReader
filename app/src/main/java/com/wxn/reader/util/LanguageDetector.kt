package com.wxn.reader.util

object LanguageDetector {

    private val supportedCodes = setOf("zh", "en", "fr", "de", "es", "pt", "ja", "hi", "ru", "ar")

    fun detectLanguage(text: String): String? {
        if (text.isBlank()) return null

        var zhCount = 0
        var jaCount = 0
        var hiCount = 0
        var ruCount = 0
        var arCount = 0
        var latinCount = 0

        for (char in text) {
            val code = char.code
            when {
                code in 0x4E00..0x9FFF -> zhCount++
                code in 0x3400..0x4DBF -> zhCount++
                code in 0x3000..0x303F -> jaCount++
                code in 0x3040..0x309F -> jaCount += 3
                code in 0x30A0..0x30FF -> jaCount += 3
                code in 0x0900..0x097F -> hiCount++
                code in 0x0400..0x04FF -> ruCount++
                code in 0x0600..0x06FF -> arCount++
                code in 0x0041..0x007A -> latinCount++
                code in 0x00C0..0x024F -> latinCount++
            }
        }

        val maxCount = maxOf(zhCount, jaCount, hiCount, ruCount, arCount, latinCount)
        if (maxCount == 0) return null

        return when (maxCount) {
            zhCount -> if (jaCount > zhCount * 2) "ja" else "zh"
            jaCount -> "ja"
            hiCount -> "hi"
            ruCount -> "ru"
            arCount -> "ar"
            latinCount -> null
            else -> null
        }
    }

    fun mapBookLanguageToSupported(bookLanguage: String?): String? {
        if (bookLanguage.isNullOrBlank()) return null
        val lower = bookLanguage.lowercase().take(2)
        return if (lower in supportedCodes) lower else null
    }
}
