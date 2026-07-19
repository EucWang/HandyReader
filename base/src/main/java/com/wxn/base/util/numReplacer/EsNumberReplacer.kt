package com.wxn.base.util.numReplacer

import kotlin.math.abs

class EsNumberReplacer : INumberReplacer {

    private val rules = mutableListOf<Rule>()

    private fun addRule(pattern: Regex, handler: (MatchResult) -> String, priority: Int) {
        rules.add(Rule(pattern, handler, priority))
        rules.sortByDescending { it.priority }
    }

    private fun registerDefaultRules() {
        val monthGroup = "(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre|ene|feb|mar|abr|may|jun|jul|ago|sep|sept|oct|nov|dic)"

        addRule(Regex("""(?i)$monthGroup\.?\s+(\d{1,2}),?\s+(\d{4})"""), ::dateMonthDayYearHandler, 100)

        addRule(Regex("""(\d{1,2})\s+de\s+(?i)$monthGroup\.?\s+del?\s+(\d{4})"""), ::dateDayMonthYearHandler, 100)

        addRule(Regex("""(\d{1,2})\s+de\s+(?i)$monthGroup\.?(?!\s+del?\s+\d)"""), ::dateDayMonthHandler, 100)

        addRule(Regex("""(\d+)(°|\.º)(?![\d°ºªCF℃℉])"""), ::ordinalSuffixHandler, 95)

        addRule(Regex("""(?i)(en|desde|hasta|entre|año)\s+(\d{4})(?!\d)"""), ::yearInContextHandler, 90)

        addRule(Regex("""(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?(?!\d)"""), ::timeHandler, 85)

        addRule(Regex("""(-?\d+(?:[,.]\d+)?)[-~](-?\d+(?:[,.]\d+)?)(°[CF]|[℃℉])"""), ::tempRangeHandler, 82)

        addRule(Regex("""(?<!\d)(\+\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*(?:\d[\s\-().]*)*\d|\d{3}[\s\-().]\d{3}[\s\-().]\d{4}|\(\d{3}\)[\s\-().]*\d{3}[\s\-().]\d{4})(?!\d)"""), ::phoneHandler, 80)

        addRule(Regex("""[-+]?\d+(?:[,.]\d+)?%"""), ::percentHandler, 78)

        addRule(Regex("""(-?\d+(?:[,.]\d+)?)(°[CF]|[℃℉])"""), ::tempHandler, 76)

        addRule(Regex("""\$(\d+(?:[,.]\d+)?)"""), ::usdHandler, 75)
        addRule(Regex("""€(\d+(?:[,.]\d+)?)"""), ::eurHandler, 75)

        addRule(Regex("""([-+]?\d+(?:[,.]\d+)?)[eE]([-+]?\d+)"""), ::sciHandler, 72)

        addRule(Regex("""\d{1,3}(?:\.\d{3})+,\d+"""), ::completeNumberHandler, 73)

        addRule(Regex("""\d{1,3}(?:\.\d{3})+"""), ::thousandSepHandler, 71)

        addRule(Regex("""(\d+(?:[,.]\d+)?)[-~](\d+(?:[,.]\d+)?)(?![:\d])"""), ::rangeHandler, 71)

        addRule(Regex("""[-+]?\d+[,.]\d+"""), ::floatHandler, 70)

        addRule(Regex("""(\d+)/(\d+)"""), ::fractionHandler, 60)

        addRule(Regex("""(\d+):(\d+)(?!\d)"""), ::scoreHandler, 40)

        addRule(Regex("""(?<![\p{L}\d])(M{0,3}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3}))(?![\p{L}\d])"""), ::romanHandler, 30)

        addRule(Regex("""(?<!\p{L})[-+]?\d+(?!\p{L})"""), ::integerHandler, 10)
    }

    private fun dateMonthDayYearHandler(match: MatchResult): String {
        val monthStr = match.groupValues[1]
        val day = match.groupValues[2].toInt()
        val year = match.groupValues[3].toInt()
        val dayStr = if (day == 1) "primero" else intToSpanish(day.toLong())
        return "$dayStr de ${normalizeMonth(monthStr)} de ${yearToSpanish(year)}"
    }

    private fun dateDayMonthYearHandler(match: MatchResult): String {
        val day = match.groupValues[1].toInt()
        val monthStr = match.groupValues[2]
        val year = match.groupValues[3].toInt()
        val dayStr = if (day == 1) "primero" else intToSpanish(day.toLong())
        return "$dayStr de ${normalizeMonth(monthStr)} de ${yearToSpanish(year)}"
    }

    private fun dateDayMonthHandler(match: MatchResult): String {
        val day = match.groupValues[1].toInt()
        val monthStr = match.groupValues[2]
        val dayStr = if (day == 1) "primero" else intToSpanish(day.toLong())
        return "$dayStr de ${normalizeMonth(monthStr)}"
    }

    private fun ordinalSuffixHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        return ordinalToSpanish(num)
    }

    private fun yearInContextHandler(match: MatchResult): String {
        val prefix = match.groupValues[1]
        val yearNum = match.groupValues[2].toIntOrNull() ?: return match.value
        return "$prefix ${yearToSpanish(yearNum)}"
    }

    private fun timeHandler(match: MatchResult): String {
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues.getOrNull(3)?.toIntOrNull()
        return if (minute == 0 && second == null) {
            intToSpanish(hour.toLong())
        } else {
            val parts = mutableListOf(intToSpanish(hour.toLong()))
            if (minute > 0) parts.add(intToSpanish(minute.toLong()))
            if (second != null && second > 0) parts.add(intToSpanish(second.toLong()))
            parts.joinToString(" ")
        }
    }

    private fun tempRangeHandler(match: MatchResult): String {
        val fromStr = match.groupValues[1]
        val toStr = match.groupValues[2]
        val unit = match.groupValues[3]
        val fromSp = convertNumberStr(fromStr)
        val toSp = convertNumberStr(toStr)
        return "$fromSp a $toSp grados ${tempUnitToSpanish(unit)}"
    }

    private fun tempHandler(match: MatchResult): String {
        val numStr = match.groupValues[1]
        val unit = match.groupValues[2]
        val numSp = convertNumberStr(numStr)
        return "$numSp grados ${tempUnitToSpanish(unit)}"
    }

    private fun tempUnitToSpanish(unit: String): String = when (unit) {
        "°F", "℉" -> "Fahrenheit"
        else -> "Celsius"
    }

    private fun phoneHandler(match: MatchResult): String {
        val digits = match.value.filter { it.isDigit() }
        return digits.map { PHONE_DIGITS[it] ?: it.toString() }.joinToString(" ")
    }

    private fun percentHandler(match: MatchResult): String {
        val rawNum = match.value.dropLast(1)
        val numSp = convertNumberStr(rawNum)
        return "$numSp por ciento"
    }

    private fun usdHandler(match: MatchResult): String {
        return currencyToSpanish(match.groupValues[1], "dólar", "dólares", "centavo", "centavos")
    }

    private fun eurHandler(match: MatchResult): String {
        return currencyToSpanish(match.groupValues[1], "euro", "euros", "céntimo", "céntimos")
    }

    private fun currencyToSpanish(amountStr: String, singularMain: String, pluralMain: String, singularSub: String, pluralSub: String): String {
        val cleaned = cleanThousandSep(amountStr)
        val normalized = cleaned.replace(',', '.')
        val parts = normalized.split('.')
        val intPart = parts[0].toLong()

        if (intPart == 0L && parts.size > 1 && !parts[1].all { it == '0' }) {
            val fracStr = parts[1].padEnd(2, '0').substring(0, 2)
            val cents = fracStr.toInt()
            val centWord = if (cents == 1) "un" else intToSpanish(cents.toLong())
            val subWord = if (cents == 1) singularSub else pluralSub
            return "$centWord $subWord"
        }

        if (parts.size == 1 || parts[1].all { it == '0' }) {
            val unitWord = if (intPart == 1L) singularMain else pluralMain
            val intWord = if (intPart == 1L) "un" else intToSpanish(intPart)
            return "$intWord $unitWord"
        }
        val fracStr = parts[1].padEnd(2, '0').substring(0, 2)
        val cents = fracStr.toInt()
        val mainWord = if (intPart == 1L) singularMain else pluralMain
        val subWord = if (cents == 1) singularSub else pluralSub
        val intWord = if (intPart == 1L) "un" else intToSpanish(intPart)
        val centWord = if (cents == 1) "un" else intToSpanish(cents.toLong())
        return "$intWord $mainWord con $centWord $subWord"
    }

    private fun sciHandler(match: MatchResult): String {
        val baseStr = match.groupValues[1].replace(',', '.')
        val expStr = match.groupValues[2]
        val hasDot = baseStr.contains('.')
        val base = baseStr.toDoubleOrNull() ?: 0.0
        val exp = expStr.toIntOrNull() ?: 0
        val baseSp = if (hasDot) floatToSpanish(base) else intToSpanish(baseStr.toLong())
        val expSp = intToSpanish(abs(exp).toLong())
        return if (exp < 0) {
            "$baseSp por diez a la menos $expSp"
        } else {
            "$baseSp por diez a la $expSp"
        }
    }

    private fun thousandSepHandler(match: MatchResult): String {
        return intToSpanish(match.value.replace(".", "").toLong())
    }

    private fun completeNumberHandler(match: MatchResult): String {
        val raw = match.value
        val commaIdx = raw.lastIndexOf(',')
        val intPart = raw.substring(0, commaIdx).replace(".", "")
        val fracPart = raw.substring(commaIdx + 1)
        val intSp = intToSpanish(intPart.toLong())
        val fracSp = fracPart.map { FLOAT_DIGITS[it] ?: it.toString() }.joinToString(" ")
        return "$intSp coma $fracSp"
    }

    private fun floatHandler(match: MatchResult): String {
        return floatToSpanish(match.value.replace(',', '.').toDouble())
    }

    private fun fractionHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        val den = match.groupValues[2].toInt()
        return fractionToSpanish(num, den)
    }

    private fun rangeHandler(match: MatchResult): String {
        val fromSp = convertNumberStr(match.groupValues[1])
        val toSp = convertNumberStr(match.groupValues[2])
        return "$fromSp a $toSp"
    }

    private fun scoreHandler(match: MatchResult): String {
        val left = match.groupValues[1].toInt()
        val right = match.groupValues[2].toInt()
        return "${intToSpanish(left.toLong())} a ${intToSpanish(right.toLong())}"
    }

    private fun romanHandler(match: MatchResult): String {
        val roman = match.value
        if (roman == "I") return roman
        if (roman.length > 15) return roman
        val num = romanToInt(roman)
        if (num !in 1..3999) return roman
        val intToRoman = intToRomanNumeral(num)
        if (intToRoman.equals(roman, ignoreCase = true)) return intToSpanish(num.toLong())
        return roman
    }

    private fun integerHandler(match: MatchResult): String {
        return intToSpanish(match.value.toLong())
    }

    private fun convertNumberStr(s: String): String {
        val cleaned = cleanThousandSep(s)
        val normalized = cleaned.replace(',', '.')
        return if (normalized.contains('.')) floatToSpanish(normalized.toDouble()) else intToSpanish(normalized.toLong())
    }

    private fun cleanThousandSep(s: String): String {
        if (s.contains('.') && !s.contains(',')) {
            val parts = s.split('.')
            if (parts.size > 1 && parts.last().length == 3) {
                return s.replace(".", "")
            }
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
            "cero", "uno", "dos", "tres", "cuatro",
            "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce",
            "quince"
        )

        private val TEENS = mapOf(
            16 to "dieciséis", 17 to "diecisiete", 18 to "dieciocho", 19 to "diecinueve"
        )

        private val TWENTIES = mapOf(
            21 to "veintiuno", 22 to "veintidós", 23 to "veintitrés", 24 to "veinticuatro",
            25 to "veinticinco", 26 to "veintiséis", 27 to "veintisiete", 28 to "veintiocho",
            29 to "veintinueve"
        )

        private val TENS = arrayOf(
            "", "", "veinte", "treinta", "cuarenta",
            "cincuenta", "sesenta", "setenta", "ochenta", "noventa"
        )

        private val HUNDREDS = arrayOf(
            "", "ciento", "doscientos", "trescientos", "cuatrocientos",
            "quinientos", "seiscientos", "setecientos", "ochocientos", "novecientos"
        )

        private val PHONE_DIGITS = mapOf(
            '0' to "cero", '1' to "uno", '2' to "dos", '3' to "tres", '4' to "cuatro",
            '5' to "cinco", '6' to "seis", '7' to "siete", '8' to "ocho", '9' to "nueve"
        )

        private val FLOAT_DIGITS = mapOf(
            '0' to "cero", '1' to "uno", '2' to "dos", '3' to "tres", '4' to "cuatro",
            '5' to "cinco", '6' to "seis", '7' to "siete", '8' to "ocho", '9' to "nueve"
        )

        private val MONTH_NAMES = mapOf(
            "enero" to "enero", "febrero" to "febrero", "marzo" to "marzo",
            "abril" to "abril", "mayo" to "mayo", "junio" to "junio",
            "julio" to "julio", "agosto" to "agosto", "septiembre" to "septiembre",
            "octubre" to "octubre", "noviembre" to "noviembre", "diciembre" to "diciembre",
            "ene" to "enero", "feb" to "febrero", "mar" to "marzo",
            "abr" to "abril", "may" to "mayo", "jun" to "junio",
            "jul" to "julio", "ago" to "agosto", "sep" to "septiembre", "sept" to "septiembre",
            "oct" to "octubre", "nov" to "noviembre", "dic" to "diciembre"
        )

        private val ORDINALS_1_10 = mapOf(
            1 to "primero", 2 to "segundo", 3 to "tercero", 4 to "cuarto",
            5 to "quinto", 6 to "sexto", 7 to "séptimo", 8 to "octavo",
            9 to "noveno", 10 to "décimo"
        )

        private val ORDINALS_11_19 = mapOf(
            11 to "undécimo", 12 to "duodécimo", 13 to "decimotercero",
            14 to "decimocuarto", 15 to "decimoquinto", 16 to "decimosexto",
            17 to "decimoséptimo", 18 to "decimoctavo", 19 to "decimonoveno"
        )

        private val ORDINAL_TENS = mapOf(
            20 to "vigésimo", 30 to "trigésimo", 40 to "cuadragésimo",
            50 to "quincuagésimo", 60 to "sexagésimo", 70 to "septuagésimo",
            80 to "octogésimo", 90 to "nonagésimo"
        )

        private val HUNDRED_ORDINALS = arrayOf(
            "", "centésimo", "ducentésimo", "tricentésimo", "cuadringentésimo",
            "quingentésimo", "sexcentésimo", "septingentésimo", "octingentésimo", "noningentésimo"
        )

        private val SPECIAL_FRACTIONS = mapOf(
            2 to "medio", 3 to "tercio", 4 to "cuarto"
        )

        private val FRACTION_DENOM_5_10 = mapOf(
            5 to "quinto", 6 to "sexto", 7 to "séptimo",
            8 to "octavo", 9 to "noveno", 10 to "décimo"
        )

        internal fun normalizeMonth(monthStr: String): String {
            return MONTH_NAMES[monthStr.lowercase()] ?: monthStr.lowercase()
        }

        fun intToSpanish(n: Int): String = intToSpanish(n.toLong())

        fun intToSpanish(n: Long): String {
            if (n == 0L) return "cero"
            if (n < 0) return "menos ${intToSpanish(abs(n))}"

            val parts = mutableListOf<String>()
            var num = n

            if (num >= 1_000_000_000_000L) {
                val v = (num / 1_000_000_000_000L).toInt()
                parts.add(if (v == 1) "un billón" else "${convertBelow1000(v)} billones")
                num %= 1_000_000_000_000L
            }

            if (num >= 1_000_000) {
                val v = (num / 1_000_000).toInt()
                if (v == 1) {
                    parts.add("un millón")
                } else {
                    parts.add("${convertUpTo999999(v)} millones")
                }
                num %= 1_000_000
            }

            if (num >= 1000) {
                val v = (num / 1000).toInt()
                parts.add(if (v == 1) "mil" else "${convertBelow1000(v)} mil")
                num %= 1000
            }

            if (num > 0) {
                parts.add(convertBelow1000(num.toInt()))
            }

            return parts.joinToString(" ")
        }

        private fun convertUpTo999999(n: Int): String {
            if (n < 1000) return convertBelow1000(n)
            val thousands = n / 1000
            val remainder = n % 1000
            val thousandPart = if (thousands == 1) "mil" else "${convertBelow1000(thousands)} mil"
            return if (remainder == 0) thousandPart else "$thousandPart ${convertBelow1000(remainder)}"
        }

        private fun convertBelow1000(n: Int): String {
            if (n == 0) return ""
            if (n == 100) return "cien"
            if (n < 100) return convertBelow100(n)

            val hundreds = n / 100
            val remainder = n % 100
            val hundredWord = HUNDREDS[hundreds]
            return if (remainder == 0) hundredWord else "$hundredWord ${convertBelow100(remainder)}"
        }

        private fun convertBelow100(n: Int): String {
            if (n <= 15) return ONES[n]
            if (n in 16..19) return TEENS[n]!!
            if (n == 20) return "veinte"
            if (n in 21..29) return TWENTIES[n]!!

            val tens = n / 10
            val ones = n % 10
            return if (ones == 0) TENS[tens] else "${TENS[tens]} y ${ONES[ones]}"
        }

        fun floatToSpanish(value: Double): String {
            if (value == 0.0) return "cero"
            val negative = value < 0
            val absValue = abs(value)
            val str = absValue.toString()
            val parts = str.split('.')
            val intPart = parts[0].toLong()
            val fracPart = if (parts.size > 1) parts[1] else ""

            val intSp = intToSpanish(intPart)
            val result = if (fracPart.isEmpty() || fracPart.all { it == '0' }) {
                intSp
            } else {
                val fracSp = fracPart.map { FLOAT_DIGITS[it] ?: it.toString() }.joinToString(" ")
                "$intSp coma $fracSp"
            }
            return if (negative) "menos $result" else result
        }

        fun ordinalToSpanish(n: Int): String {
            if (n <= 0) return intToSpanish(n.toLong())

            ORDINALS_1_10[n]?.let { return it }
            ORDINALS_11_19[n]?.let { return it }
            ORDINAL_TENS[n]?.let { return it }
            if (n == 100) return "centésimo"
            if (n == 200) return "ducentésimo"
            if (n == 1000) return "milésimo"

            val parts = mutableListOf<String>()
            var remaining = n

            if (remaining >= 1000) {
                val thousands = remaining / 1000
                parts.add(if (thousands == 1) "milésimo" else "${ordinalToSpanish(thousands)} milésimo")
                remaining %= 1000
            }

            if (remaining >= 100) {
                val h = remaining / 100
                parts.add(HUNDRED_ORDINALS[h])
                remaining %= 100
            }

            if (remaining > 0) {
                if (remaining in 11..19) {
                    ORDINALS_11_19[remaining]?.let { parts.add(it) }
                } else {
                    if (remaining >= 20) {
                        val tens = (remaining / 10) * 10
                        ORDINAL_TENS[tens]?.let { parts.add(it) }
                        remaining %= 10
                    }
                    if (remaining in 1..10) {
                        ORDINALS_1_10[remaining]?.let { parts.add(it) }
                    }
                }
            }

            return parts.joinToString(" ")
        }

        fun yearToSpanish(year: Int): String {
            return intToSpanish(year.toLong())
        }

        fun phoneToSpanish(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            return digits.map { PHONE_DIGITS[it] ?: it.toString() }.joinToString(" ")
        }

        fun fractionToSpanish(num: Int, den: Int): String {
            val numerator = if (num == 1) "un" else intToSpanish(num.toLong())
            val denominator = fractionDenominator(den, num != 1)
            return "$numerator $denominator"
        }

        private fun fractionDenominator(den: Int, plural: Boolean): String {
            SPECIAL_FRACTIONS[den]?.let { base ->
                return when {
                    plural && base == "medio" -> "medios"
                    plural -> "${base}s"
                    else -> base
                }
            }
            if (den in 5..10) {
                val form = FRACTION_DENOM_5_10[den]!!
                return if (plural) "${form}s" else form
            }
            val base = intToSpanish(den.toLong())
            val avoForm = "${base}avo"
            return if (plural) "${avoForm}s" else avoForm
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
