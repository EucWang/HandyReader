package com.wxn.base.util.numReplacer

import kotlin.math.abs

class FrNumberReplacer : INumberReplacer {

    private val rules = mutableListOf<Rule>()

    private fun addRule(pattern: Regex, handler: (MatchResult) -> String, priority: Int) {
        rules.add(Rule(pattern, handler, priority))
        rules.sortByDescending { it.priority }
    }

    private fun registerDefaultRules() {
        val monthGroup = "(janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre|jan|fév|mar|avr|jun|jui|aoû|sep|oct|nov|déc)"

        addRule(Regex("""(?i)$monthGroup\.?\s+(\d{1,2}),?\s+(\d{4})"""), ::dateMonthDayYearHandler, 100)

        addRule(Regex("""(\d{1,2})\s+(?i)$monthGroup\.?\s+(\d{4})"""), ::dateDayMonthYearHandler, 100)

        addRule(Regex("""(\d{1,2})\s+(?i)$monthGroup\.?(?!\s+\d)"""), ::dateDayMonthHandler, 100)

        addRule(Regex("""(\d+)(er|ère)"""), ::ordinalSpecialHandler, 95)
        addRule(Regex("""(\d+)(e|ème|me)(?![-+\d])"""), ::ordinalSuffixHandler, 95)

        addRule(Regex("""(?i)(en|depuis|dès)\s+(\d{4})(?!\d)"""), ::yearInContextHandler, 90)

        addRule(Regex("""(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?(?!\d)"""), ::timeHandler, 85)

        addRule(Regex("""(-?\d+(?:[,.]\d+)?)[-~](-?\d+(?:[,.]\d+)?)(°[CF]|[℃℉])"""), ::tempRangeHandler, 82)

        addRule(Regex("""(?<!\d)(\+\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*(?:\d[\s\-().]*)*\d|\d{3}[\s\-().]\d{3}[\s\-().]\d{4}|\(\d{3}\)[\s\-().]*\d{3}[\s\-().]\d{4})(?!\d)"""), ::phoneHandler, 80)

        addRule(Regex("""[-+]?\d+(?:[,.]\d+)?%"""), ::percentHandler, 78)

        addRule(Regex("""(-?\d+(?:[,.]\d+)?)(°[CF]|[℃℉])"""), ::tempHandler, 76)

        addRule(Regex("""€(\d+(?:[,.]\d+)?)"""), ::eurHandler, 75)
        addRule(Regex("""\$(\d+(?:[,.]\d+)?)"""), ::usdHandler, 75)

        addRule(Regex("""([-+]?\d+(?:[,.]\d+)?)[eE]([-+]?\d+)"""), ::sciHandler, 72)

        addRule(Regex("""\d{1,3}(?:\s\d{3})+,\d+"""), ::completeNumberHandler, 73)

        addRule(Regex("""\d{1,3}(?:\s\d{3})+"""), ::thousandSepHandler, 71)

        addRule(Regex("""(\d+(?:[,.]\d+)?)[-~](\d+(?:[,.]\d+)?)(?![:\d])"""), ::rangeHandler, 71)

        addRule(Regex("""[-+]?\d+[,.]\d+"""), ::floatHandler, 70)

        addRule(Regex("""(\d+)/(\d+)"""), ::fractionHandler, 60)

        addRule(Regex("""(\d+):(\d+)(?!\d)"""), ::scoreHandler, 40)

        addRule(Regex("""(?<![\p{L}\d'])(M{0,3}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3}))(?![\p{L}\d'])"""), ::romanHandler, 30)

        addRule(Regex("""(?<!\p{L})[-+]?\d+(?!\p{L})"""), ::integerHandler, 10)
    }

    private fun dateMonthDayYearHandler(match: MatchResult): String {
        val monthStr = match.groupValues[1]
        val day = match.groupValues[2].toInt()
        val year = match.groupValues[3].toInt()
        val dayStr = if (day == 1) "premier" else intToFrench(day.toLong())
        return "$dayStr ${normalizeMonth(monthStr)} ${yearToFrench(year)}"
    }

    private fun dateDayMonthYearHandler(match: MatchResult): String {
        val day = match.groupValues[1].toInt()
        val monthStr = match.groupValues[2]
        val year = match.groupValues[3].toInt()
        val dayStr = if (day == 1) "premier" else intToFrench(day.toLong())
        return "$dayStr ${normalizeMonth(monthStr)} ${yearToFrench(year)}"
    }

    private fun dateDayMonthHandler(match: MatchResult): String {
        val day = match.groupValues[1].toInt()
        val monthStr = match.groupValues[2]
        val dayStr = if (day == 1) "premier" else intToFrench(day.toLong())
        return "$dayStr ${normalizeMonth(monthStr)}"
    }

    private fun ordinalSpecialHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        val suffix = match.groupValues[2].lowercase()
        return if (num == 1) {
            if (suffix == "ère") "première" else "premier"
        } else {
            val base = ordinalToFrench(num)
            if (suffix == "ère") ordinalToFeminine(base) else base
        }
    }

    private fun ordinalSuffixHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        return ordinalToFrench(num)
    }

    private fun yearInContextHandler(match: MatchResult): String {
        val prefix = match.groupValues[1]
        val yearNum = match.groupValues[2].toIntOrNull() ?: return match.value
        return "$prefix ${yearToFrench(yearNum)}"
    }

    private fun timeHandler(match: MatchResult): String {
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues.getOrNull(3)?.toIntOrNull()
        return if (minute == 0 && second == null) {
            "${intToFrench(hour.toLong())} heures"
        } else {
            val parts = mutableListOf("${intToFrench(hour.toLong())} heures")
            if (minute > 0) parts.add(intToFrench(minute.toLong()))
            if (second != null && second > 0) parts.add(intToFrench(second.toLong()))
            parts.joinToString(" ")
        }
    }

    private fun tempRangeHandler(match: MatchResult): String {
        val fromStr = match.groupValues[1]
        val toStr = match.groupValues[2]
        val unit = match.groupValues[3]
        val fromFr = convertNumberStr(fromStr)
        val toFr = convertNumberStr(toStr)
        val unitWord = tempUnitToFrench(unit)
        return "$fromFr à $toFr degrés $unitWord"
    }

    private fun tempHandler(match: MatchResult): String {
        val numStr = match.groupValues[1]
        val unit = match.groupValues[2]
        val numFr = convertNumberStr(numStr)
        val degreWord = if (isSingularNumber(numStr)) "degré" else "degrés"
        return "$numFr $degreWord ${tempUnitToFrench(unit)}"
    }

    private fun tempUnitToFrench(unit: String): String = when (unit) {
        "°F", "℉" -> "Fahrenheit"
        else -> "Celsius"
    }

    private fun isSingularNumber(numStr: String): Boolean {
        val cleaned = cleanThousandSep(numStr).replace(',', '.')
        val value = cleaned.toDoubleOrNull() ?: return false
        return value == 1.0 || value == -1.0
    }

    private fun phoneHandler(match: MatchResult): String {
        val digits = match.value.filter { it.isDigit() }
        return digits.map { PHONE_DIGITS[it] ?: it.toString() }.joinToString(" ")
    }

    private fun percentHandler(match: MatchResult): String {
        val rawNum = match.value.dropLast(1)
        val numFr = convertNumberStr(rawNum)
        return "$numFr pour cent"
    }

    private fun usdHandler(match: MatchResult): String {
        return currencyToFrench(match.groupValues[1], "dollar", "dollars", "cent", "cents")
    }

    private fun eurHandler(match: MatchResult): String {
        return currencyToFrench(match.groupValues[1], "euro", "euros", "centime", "centimes")
    }

    private fun currencyToFrench(amountStr: String, singularMain: String, pluralMain: String, singularSub: String, pluralSub: String): String {
        val cleaned = cleanThousandSep(amountStr)
        val normalized = cleaned.replace(',', '.')
        val parts = normalized.split('.')
        val intPart = parts[0].toLong()

        if (intPart == 0L && parts.size > 1 && !parts[1].all { it == '0' }) {
            val fracStr = parts[1].padEnd(2, '0').substring(0, 2)
            val cents = fracStr.toInt()
            val centWord = intToFrench(cents.toLong())
            val subWord = if (cents == 1) singularSub else pluralSub
            return "$centWord $subWord"
        }

        if (parts.size == 1 || parts[1].all { it == '0' }) {
            val unitWord = if (intPart == 1L) singularMain else pluralMain
            val intWord = intToFrench(intPart)
            return "$intWord $unitWord"
        }
        val fracStr = parts[1].padEnd(2, '0').substring(0, 2)
        val cents = fracStr.toInt()
        val mainWord = if (intPart == 1L) singularMain else pluralMain
        val subWord = if (cents == 1) singularSub else pluralSub
        val intWord = intToFrench(intPart)
        val centWord = intToFrench(cents.toLong())
        return "$intWord $mainWord et $centWord $subWord"
    }

    private fun sciHandler(match: MatchResult): String {
        val baseStr = match.groupValues[1].replace(',', '.')
        val expStr = match.groupValues[2]
        val hasDot = baseStr.contains('.')
        val base = baseStr.toDoubleOrNull() ?: 0.0
        val exp = expStr.toIntOrNull() ?: 0
        val baseFr = if (hasDot) floatToFrench(base) else intToFrench(baseStr.toLong())
        val expFr = ordinalToFrench(abs(exp))
        return if (exp < 0) {
            "$baseFr fois dix à la moins $expFr"
        } else {
            "$baseFr fois dix à la $expFr"
        }
    }

    private fun thousandSepHandler(match: MatchResult): String {
        return intToFrench(match.value.replace(" ", "").toLong())
    }

    private fun completeNumberHandler(match: MatchResult): String {
        val raw = match.value
        val commaIdx = raw.lastIndexOf(',')
        val intPart = raw.substring(0, commaIdx).replace(" ", "")
        val fracPart = raw.substring(commaIdx + 1)
        val intFr = intToFrench(intPart.toLong())
        val fracFr = fracPart.map { FLOAT_DIGITS[it] ?: it.toString() }.joinToString(" ")
        return "$intFr virgule $fracFr"
    }

    private fun floatHandler(match: MatchResult): String {
        return floatToFrench(match.value.replace(',', '.').toDouble())
    }

    private fun fractionHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        val den = match.groupValues[2].toInt()
        return fractionToFrench(num, den)
    }

    private fun rangeHandler(match: MatchResult): String {
        val fromFr = convertNumberStr(match.groupValues[1])
        val toFr = convertNumberStr(match.groupValues[2])
        return "$fromFr à $toFr"
    }

    private fun scoreHandler(match: MatchResult): String {
        val left = match.groupValues[1].toInt()
        val right = match.groupValues[2].toInt()
        return "${intToFrench(left.toLong())} à ${intToFrench(right.toLong())}"
    }

    private fun romanHandler(match: MatchResult): String {
        val roman = match.value
        if (roman == "I") return roman
        if (roman.length > 15) return roman
        val num = romanToInt(roman)
        if (num !in 1..3999) return roman
        val intToRoman = intToRomanNumeral(num)
        if (intToRoman.equals(roman, ignoreCase = true)) return intToFrench(num.toLong())
        return roman
    }

    private fun integerHandler(match: MatchResult): String {
        return intToFrench(match.value.toLong())
    }

    private fun convertNumberStr(s: String): String {
        val cleaned = cleanThousandSep(s)
        val normalized = cleaned.replace(',', '.')
        return if (normalized.contains('.')) floatToFrench(normalized.toDouble()) else intToFrench(normalized.toLong())
    }

    private fun cleanThousandSep(s: String): String {
        val spaceRegex = Regex("""\d{1,3}(?:\s\d{3})+""")
        if (spaceRegex.matches(s)) {
            return s.replace(" ", "")
        }
        return s
    }

    init {
        registerDefaultRules()
    }

    override fun replace(text: String): String {
        var result = text
        for (rule in rules) {
            result = rule.pattern.replace(result) { match ->
                rule.handler(match)
            }
        }
        return result
    }

    companion object {

        private val ONES = arrayOf(
            "", "un", "deux", "trois", "quatre", "cinq",
            "six", "sept", "huit", "neuf", "dix",
            "onze", "douze", "treize", "quatorze", "quinze",
            "seize", "dix-sept", "dix-huit", "dix-neuf"
        )

        private val TENS = arrayOf(
            "", "dix", "vingt", "trente", "quarante",
            "cinquante", "soixante"
        )

        private val PHONE_DIGITS = mapOf(
            '0' to "zéro", '1' to "un", '2' to "deux", '3' to "trois", '4' to "quatre",
            '5' to "cinq", '6' to "six", '7' to "sept", '8' to "huit", '9' to "neuf"
        )

        private val FLOAT_DIGITS = mapOf(
            '0' to "zéro", '1' to "un", '2' to "deux", '3' to "trois", '4' to "quatre",
            '5' to "cinq", '6' to "six", '7' to "sept", '8' to "huit", '9' to "neuf"
        )

        private val MONTH_NAMES = mapOf(
            "janvier" to "janvier", "février" to "février", "mars" to "mars",
            "avril" to "avril", "mai" to "mai", "juin" to "juin",
            "juillet" to "juillet", "août" to "août", "septembre" to "septembre",
            "octobre" to "octobre", "novembre" to "novembre", "décembre" to "décembre",
            "jan" to "janvier", "fév" to "février", "mar" to "mars",
            "avr" to "avril", "mai" to "mai", "jun" to "juin",
            "jui" to "juillet", "aoû" to "août", "sep" to "septembre",
            "oct" to "octobre", "nov" to "novembre", "déc" to "décembre"
        )

        private val SPECIAL_FRACTIONS = mapOf(
            2 to Pair("demi", "demis"),
            3 to Pair("tiers", "tiers"),
            4 to Pair("quart", "quarts")
        )

        internal fun normalizeMonth(monthStr: String): String {
            return MONTH_NAMES[monthStr.lowercase()] ?: monthStr.lowercase()
        }

        fun intToFrench(n: Long): String {
            if (n == 0L) return "zéro"
            if (n < 0) return "moins ${intToFrench(-n)}"

            val parts = mutableListOf<String>()
            var num = n

            if (num >= 1_000_000_000L) {
                val v = (num / 1_000_000_000L).toInt()
                parts.add(if (v == 1) "un milliard" else "${convertUnder1000(v)} milliards")
                num %= 1_000_000_000L
            }

            if (num >= 1_000_000) {
                val v = (num / 1_000_000).toInt()
                parts.add(if (v == 1) "un million" else "${convertUnder1000(v)} millions")
                num %= 1_000_000
            }

            if (num >= 1000) {
                val v = (num / 1000).toInt()
                parts.add(if (v == 1) "mille" else "${convertUnder1000(v)} mille")
                num %= 1000
            }

            if (num > 0) {
                parts.add(convertUnder1000(num.toInt()))
            }

            return parts.joinToString(" ")
        }

        private fun convertUnder1000(n: Int): String {
            if (n == 0) return ""
            if (n < 20) return ONES[n]

            if (n < 70) {
                val tens = n / 10
                val ones = n % 10
                return when {
                    ones == 0 -> TENS[tens]
                    ones == 1 && tens >= 2 -> "${TENS[tens]} et un"
                    else -> "${TENS[tens]}-${ONES[ones]}"
                }
            }

            if (n < 80) {
                val ones = n - 60
                return if (ones == 11) "soixante et onze" else "soixante-${ONES[ones]}"
            }

            if (n == 80) return "quatre-vingts"

            if (n < 100) {
                val ones = n - 80
                return "quatre-vingt-${ONES[ones]}"
            }

            val hundreds = n / 100
            val remainder = n % 100

            val prefix = when {
                hundreds == 1 && remainder == 0 -> "cent"
                hundreds == 1 -> "cent"
                remainder == 0 -> "${ONES[hundreds]} cents"
                else -> "${ONES[hundreds]} cent"
            }

            return if (remainder == 0) prefix else "$prefix ${convertUnder1000(remainder)}"
        }

        fun floatToFrench(value: Double): String {
            if (value == 0.0) return "zéro"
            val negative = value < 0
            val absValue = abs(value)
            val str = absValue.toString()
            val parts = str.split('.')
            val intPart = parts[0].toLong()
            val fracPart = if (parts.size > 1) parts[1] else ""

            val intFr = intToFrench(intPart)
            val result = if (fracPart.isEmpty() || fracPart.all { it == '0' }) {
                intFr
            } else {
                val fracFr = fracPart.map { FLOAT_DIGITS[it] ?: it.toString() }.joinToString(" ")
                "$intFr virgule $fracFr"
            }
            return if (negative) "moins $result" else result
        }

        fun ordinalToFrench(n: Int): String {
            if (n <= 0) return intToFrench(n.toLong())
            if (n == 1) return "premier"
            val base = intToFrench(n.toLong())
            val ordinal = when {
                base.endsWith("quatre-vingts") -> "quatre-vingtième"
                base.endsWith("q") -> base.dropLast(1) + "quième"
                base.endsWith("f") -> base.dropLast(1) + "vième"
                base.endsWith("e") && !base.endsWith("ce") -> base.dropLast(1) + "ième"
                else -> base + "ième"
            }
            return ordinal
        }

        fun ordinalToFeminine(ordinal: String): String {
            return when {
                ordinal == "premier" -> "première"
                ordinal.endsWith("ième") -> ordinal
                else -> ordinal
            }
        }

        fun yearToFrench(year: Int): String {
            return intToFrench(year.toLong())
        }

        fun phoneToFrench(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            return digits.map { PHONE_DIGITS[it] ?: it.toString() }.joinToString(" ")
        }

        fun fractionToFrench(num: Int, den: Int): String {
            val numerator = if (num == 1) "un" else intToFrench(num.toLong())
            val denominator = fractionDenominator(den, num != 1)
            return "$numerator $denominator"
        }

        private fun fractionDenominator(den: Int, plural: Boolean): String {
            SPECIAL_FRACTIONS[den]?.let { (singular, _) ->
                return if (plural) {
                    if (den == 2) "demis" else "${singular}s"
                } else singular
            }
            val ordinal = ordinalToFrench(den)
            return if (plural) {
                ordinal.dropLast(4) + "ièmes"
            } else ordinal
        }

        fun romanToInt(roman: String): Int {
            val map = mapOf(
                'M' to 1000, 'D' to 500, 'C' to 100, 'L' to 50,
                'X' to 10, 'V' to 5, 'I' to 1
            )
            var result = 0
            var prev = 0
            for (ch in roman.reversed()) {
                val cur = map[ch] ?: 0
                if (cur < prev) result -= cur else result += cur
                prev = cur
            }
            return result
        }

        private fun intToRomanNumeral(num: Int): String {
            if (num <= 0 || num > 3999) return ""
            val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
            val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
            val sb = StringBuilder()
            var n = num
            for (i in values.indices) {
                while (n >= values[i]) {
                    sb.append(symbols[i])
                    n -= values[i]
                }
            }
            return sb.toString()
        }
    }
}
