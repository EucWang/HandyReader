package com.wxn.reader.util

object FontFamilyAnalyzer {

    private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc", "woff", "woff2")

    private val VARIANT_PATTERNS = listOf(
        "bolditalic", "boldoblique", "semibolditalic", "extrabolditalic",
        "semibold", "extrabold", "extralight", "ultralight", "ultrabold",
        "bold", "italic", "oblique", "regular", "medium", "light",
        "thin", "black", "heavy", "hairline", "demibold", "book",
        "roman", "normal", "demi", "condensed", "narrow", "text"
    )

    private val SORTED_PATTERNS_BY_LENGTH = VARIANT_PATTERNS.sortedByDescending { it.length }

    private val CAMEL_CASE_REGEX = Regex("(?<=[a-z])(?=[A-Z])")

    private val VARIANT_ABBREVIATIONS = mapOf(
        "it" to "italic",
        "bd" to "bold",
        "lt" to "light",
        "md" to "medium",
        "rg" to "regular",
        "bk" to "black",
        "th" to "thin",
        "hv" to "heavy",
        "ob" to "oblique",
        "sb" to "semibold",
        "eb" to "extrabold",
        "el" to "extralight",
        "ub" to "ultrabold",
        "ul" to "ultralight",
        "cn" to "condensed"
    )

    private val VARIANT_PREFIXES = mapOf(
        "semi" to "semi",
        "extra" to "extra",
        "ultra" to "ultra"
    )

    private val VARIANT_WEIGHT_MAP = mapOf(
        "hairline" to 50,
        "thin" to 100,
        "extralight" to 200,
        "ultralight" to 200,
        "light" to 300,
        "book" to 350,
        "regular" to 400,
        "normal" to 400,
        "roman" to 400,
        "medium" to 500,
        "demibold" to 600,
        "demi" to 600,
        "semibold" to 600,
        "bold" to 700,
        "extrabold" to 800,
        "ultrabold" to 800,
        "black" to 900,
        "heavy" to 900,
        "condensed" to 400,
        "narrow" to 400,
        "text" to 400
    )

    private val STYLE_SUFFIXES = listOf("italic", "oblique")

    fun variantWeight(variant: String): Int {
        val lower = variant.lowercase()

        val matchedStyle = STYLE_SUFFIXES.firstOrNull { lower.endsWith(it) }
        val isItalic = matchedStyle == "italic"
        val isOblique = matchedStyle == "oblique"

        val baseVariant = when {
            matchedStyle != null -> {
                val stripped = lower.substring(0, lower.length - matchedStyle.length)
                stripped.ifEmpty { "regular" }
            }
            else -> lower
        }

        val baseWeight = VARIANT_WEIGHT_MAP[baseVariant] ?: 400
        val styleModifier = when {
            isOblique -> 2
            isItalic -> 1
            else -> 0
        }
        return baseWeight + styleModifier
    }

    data class FontFileInfo(
        val familyName: String,
        val variant: String,
        val originalFileName: String
    )

    fun isFontFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in FONT_EXTENSIONS
    }

    fun analyze(fileName: String): FontFileInfo {
        val nameWithoutExt = fileName.substringBeforeLast(".").trim()
        if (nameWithoutExt.isEmpty()) {
            return FontFileInfo("ImportedFont", "regular", fileName)
        }

        val result = trySeparators(nameWithoutExt)
            ?: tryMatchVariantSuffix(nameWithoutExt)

        return result ?: FontFileInfo(nameWithoutExt, "regular", fileName)
    }

    private fun trySeparators(name: String): FontFileInfo? {
        for (sep in listOf('-', '_', ' ')) {
            if (!name.contains(sep)) continue
            val parts = name.split(sep).filter { it.isNotEmpty() }
            if (parts.size < 2) continue

            val lastPart = parts.last().lowercase()
            val matchedVariant = resolveVariant(lastPart)
                ?: tryCompoundVariant(parts.last())
            if (matchedVariant != null) {
                val familyName = parts.dropLast(1).joinToString(sep.toString())
                return FontFileInfo(familyName, matchedVariant, name)
            }
        }
        return null
    }

    private fun tryCompoundVariant(part: String): String? {
        val subParts = part.split(CAMEL_CASE_REGEX)
        if (subParts.size < 2) return null
        val resolved = subParts.map { resolveCompoundPart(it.lowercase()) }
        if (resolved.all { it != null }) {
            return resolved.joinToString("") { it!! }
        }
        return null
    }

    private fun tryMatchVariantSuffix(name: String): FontFileInfo? {
        var remaining = name
        val matchedVariants = mutableListOf<String>()

        while (remaining.isNotEmpty()) {
            val lowerRemaining = remaining.lowercase()
            var found = false
            for (pattern in SORTED_PATTERNS_BY_LENGTH) {
                if (lowerRemaining.endsWith(pattern) && lowerRemaining.length > pattern.length) {
                    matchedVariants.add(0, pattern)
                    remaining = remaining.substring(0, remaining.length - pattern.length)
                    found = true
                    break
                }
            }
            if (!found) break
        }

        if (matchedVariants.isEmpty()) return null

        val familyName = remaining.trimEnd('-', '_', ' ')
        if (familyName.isEmpty()) return null

        val variant = matchedVariants.joinToString("")
        return FontFileInfo(familyName, variant, name)
    }

    private fun resolveCompoundPart(candidate: String): String? {
        return VARIANT_PATTERNS.find { it == candidate }
            ?: VARIANT_ABBREVIATIONS[candidate]
            ?: VARIANT_PREFIXES[candidate]
    }

    private fun resolveVariant(candidate: String): String? {
        return VARIANT_PATTERNS.find { it == candidate }
            ?: VARIANT_ABBREVIATIONS[candidate]
    }

    fun groupByFamily(fileInfos: List<FontFileInfo>): Map<String, List<FontFileInfo>> {
        return fileInfos.groupBy { it.familyName }
    }
}
