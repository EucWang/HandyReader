package com.wxn.base.util.numReplacer

import kotlin.math.abs

class PtNumberReplacer : INumberReplacer {

    private val rules = mutableListOf<Rule>()

    private fun addRule(pattern: Regex, handler: (MatchResult) -> String, priority: Int) {
        rules.add(Rule(pattern, handler, priority))
        rules.sortByDescending { it.priority }
    }

    private fun registerDefaultRules() {
        val monthGroup = "(janeiro|fevereiro|março|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro|jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez)"

        addRule(Regex("""(?i)$monthGroup\.?\s+(\d{1,2}),?\s+(\d{4})"""), ::dateMonthDayYearHandler, 100)

        addRule(Regex("""(\d{1,2})\s+de\s+(?i)$monthGroup\.?\s+de\s+(\d{4})"""), ::dateDayMonthYearHandler, 100)

        addRule(Regex("""(\d{1,2})\s+de\s+(?i)$monthGroup\.?(?!\s+de\s+\d)"""), ::dateDayMonthHandler, 100)

        addRule(Regex("""(\d+)(°|\.º|º|\.ª|ª)(?![\d°ºªCF℃℉])"""), ::ordinalSuffixHandler, 95)

        addRule(Regex("""(?i)(em|desde|até|entre|ano)\s+(\d{4})(?!\d)"""), ::yearInContextHandler, 90)

        addRule(Regex("""(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?(?!\d)"""), ::timeHandler, 85)

        addRule(Regex("""(-?\d+(?:[,.]\d+)?)[-~](-?\d+(?:[,.]\d+)?)(°[CF]|[℃℉])"""), ::tempRangeHandler, 82)

        addRule(Regex("""(?<!\d)(\+\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*(?:\d[\s\-().]*)*\d|\d{3}[\s\-().]\d{3}[\s\-().]\d{4}|\(\d{3}\)[\s\-().]*\d{3}[\s\-().]\d{4})(?!\d)"""), ::phoneHandler, 80)

        addRule(Regex("""[-+]?\d+(?:[,.]\d+)?%"""), ::percentHandler, 78)

        addRule(Regex("""(-?\d+(?:[,.]\d+)?)(°[CF]|[℃℉])"""), ::tempHandler, 76)

        addRule(Regex("""R\$\s*(\d+(?:[,.]\d+)?)"""), ::brlHandler, 77)
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
        val dayStr = if (day == 1) "primeiro" else intToPortuguese(day.toLong())
        return "$dayStr de ${normalizeMonth(monthStr)} de ${yearToPortuguese(year)}"
    }

    private fun dateDayMonthYearHandler(match: MatchResult): String {
        val day = match.groupValues[1].toInt()
        val monthStr = match.groupValues[2]
        val year = match.groupValues[3].toInt()
        val dayStr = if (day == 1) "primeiro" else intToPortuguese(day.toLong())
        return "$dayStr de ${normalizeMonth(monthStr)} de ${yearToPortuguese(year)}"
    }

    private fun dateDayMonthHandler(match: MatchResult): String {
        val day = match.groupValues[1].toInt()
        val monthStr = match.groupValues[2]
        val dayStr = if (day == 1) "primeiro" else intToPortuguese(day.toLong())
        return "$dayStr de ${normalizeMonth(monthStr)}"
    }

    private fun ordinalSuffixHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        val suffix = match.groupValues[2]
        val isFeminine = suffix.contains('ª')
        return if (isFeminine) ordinalToPortugueseFeminine(num) else ordinalToPortuguese(num)
    }

    private fun yearInContextHandler(match: MatchResult): String {
        val prefix = match.groupValues[1]
        val yearNum = match.groupValues[2].toIntOrNull() ?: return match.value
        return "$prefix ${yearToPortuguese(yearNum)}"
    }

    private fun timeHandler(match: MatchResult): String {
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues.getOrNull(3)?.toIntOrNull()
        return if (minute == 0 && second == null) {
            intToPortuguese(hour.toLong())
        } else {
            val parts = mutableListOf(intToPortuguese(hour.toLong()))
            if (minute > 0) parts.add(intToPortuguese(minute.toLong()))
            if (second != null && second > 0) parts.add(intToPortuguese(second.toLong()))
            parts.joinToString(" e ")
        }
    }

    private fun tempRangeHandler(match: MatchResult): String {
        val fromStr = match.groupValues[1]
        val toStr = match.groupValues[2]
        val unit = match.groupValues[3]
        val fromPt = convertNumberStr(fromStr)
        val toPt = convertNumberStr(toStr)
        return "$fromPt a $toPt graus ${tempUnitToPortuguese(unit)}"
    }

    private fun tempHandler(match: MatchResult): String {
        val numStr = match.groupValues[1]
        val unit = match.groupValues[2]
        val numPt = convertNumberStr(numStr)
        val grauWord = if (isSingularNumber(numStr)) "grau" else "graus"
        return "$numPt $grauWord ${tempUnitToPortuguese(unit)}"
    }

    private fun tempUnitToPortuguese(unit: String): String = when (unit) {
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
        val numPt = convertNumberStr(rawNum)
        return "$numPt por cento"
    }

    private fun usdHandler(match: MatchResult): String {
        return currencyToPortuguese(match.groupValues[1], "dólar", "dólares", "centavo", "centavos")
    }

    private fun eurHandler(match: MatchResult): String {
        return currencyToPortuguese(match.groupValues[1], "euro", "euros", "centavo", "centavos")
    }

    private fun brlHandler(match: MatchResult): String {
        return currencyToPortuguese(match.groupValues[1], "real", "reais", "centavo", "centavos")
    }

    private fun currencyToPortuguese(amountStr: String, singularMain: String, pluralMain: String, singularSub: String, pluralSub: String): String {
        val cleaned = cleanThousandSep(amountStr)
        val normalized = cleaned.replace(',', '.')
        val parts = normalized.split('.')
        val intPart = parts[0].toLong()

        if (intPart == 0L && parts.size > 1 && !parts[1].all { it == '0' }) {
            val fracStr = parts[1].padEnd(2, '0').substring(0, 2)
            val cents = fracStr.toInt()
            val centWord = if (cents == 1) "um" else intToPortuguese(cents.toLong())
            val subWord = if (cents == 1) singularSub else pluralSub
            return "$centWord $subWord"
        }

        if (parts.size == 1 || parts[1].all { it == '0' }) {
            val unitWord = if (intPart == 1L) singularMain else pluralMain
            val intWord = if (intPart == 1L) "um" else intToPortuguese(intPart)
            return "$intWord $unitWord"
        }
        val fracStr = parts[1].padEnd(2, '0').substring(0, 2)
        val cents = fracStr.toInt()
        val mainWord = if (intPart == 1L) singularMain else pluralMain
        val subWord = if (cents == 1) singularSub else pluralSub
        val intWord = if (intPart == 1L) "um" else intToPortuguese(intPart)
        val centWord = if (cents == 1) "um" else intToPortuguese(cents.toLong())
        return "$intWord $mainWord e $centWord $subWord"
    }

    private fun sciHandler(match: MatchResult): String {
        val baseStr = match.groupValues[1].replace(',', '.')
        val expStr = match.groupValues[2]
        val hasDot = baseStr.contains('.')
        val base = baseStr.toDoubleOrNull() ?: 0.0
        val exp = expStr.toIntOrNull() ?: 0
        val basePt = if (hasDot) floatToPortuguese(base) else intToPortuguese(baseStr.toLong())
        val expPt = intToPortuguese(abs(exp).toLong())
        return if (exp < 0) {
            "$basePt vezes dez a menos $expPt"
        } else {
            "$basePt vezes dez a $expPt"
        }
    }

    private fun thousandSepHandler(match: MatchResult): String {
        return intToPortuguese(match.value.replace(".", "").toLong())
    }

    private fun completeNumberHandler(match: MatchResult): String {
        val raw = match.value
        val commaIdx = raw.lastIndexOf(',')
        val intPart = raw.substring(0, commaIdx).replace(".", "")
        val fracPart = raw.substring(commaIdx + 1)
        val intPt = intToPortuguese(intPart.toLong())
        val fracPt = fracPart.map { FLOAT_DIGITS[it] ?: it.toString() }.joinToString(" ")
        return "$intPt vírgula $fracPt"
    }

    private fun floatHandler(match: MatchResult): String {
        return floatToPortuguese(match.value.replace(',', '.').toDouble())
    }

    private fun fractionHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        val den = match.groupValues[2].toInt()
        return fractionToPortuguese(num, den)
    }

    private fun rangeHandler(match: MatchResult): String {
        val fromPt = convertNumberStr(match.groupValues[1])
        val toPt = convertNumberStr(match.groupValues[2])
        return "$fromPt a $toPt"
    }

    private fun scoreHandler(match: MatchResult): String {
        val left = match.groupValues[1].toInt()
        val right = match.groupValues[2].toInt()
        return "${intToPortuguese(left.toLong())} a ${intToPortuguese(right.toLong())}"
    }

    private fun romanHandler(match: MatchResult): String {
        val roman = match.value
        if (roman == "I") return roman
        if (roman.length > 15) return roman
        val num = romanToInt(roman)
        if (num !in 1..3999) return roman
        val intToRoman = intToRomanNumeral(num)
        if (intToRoman.equals(roman, ignoreCase = true)) return intToPortuguese(num.toLong())
        return roman
    }

    private fun integerHandler(match: MatchResult): String {
        return intToPortuguese(match.value.toLong())
    }

    private fun convertNumberStr(s: String): String {
        val cleaned = cleanThousandSep(s)
        val normalized = cleaned.replace(',', '.')
        return if (normalized.contains('.')) floatToPortuguese(normalized.toDouble()) else intToPortuguese(normalized.toLong())
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
            "zero", "um", "dois", "três", "quatro",
            "cinco", "seis", "sete", "oito", "nove",
            "dez", "onze", "doze", "treze", "quatorze",
            "quinze"
        )

        private val TEENS = mapOf(
            16 to "dezesseis", 17 to "dezessete", 18 to "dezoito", 19 to "dezenove"
        )

        private val TENS = arrayOf(
            "", "", "vinte", "trinta", "quarenta",
            "cinquenta", "sessenta", "setenta", "oitenta", "noventa"
        )

        private val HUNDREDS = arrayOf(
            "", "cento", "duzentos", "trezentos", "quatrocentos",
            "quinhentos", "seiscentos", "setecentos", "oitocentos", "novecentos"
        )

        private val PHONE_DIGITS = mapOf(
            '0' to "zero", '1' to "um", '2' to "dois", '3' to "três", '4' to "quatro",
            '5' to "cinco", '6' to "seis", '7' to "sete", '8' to "oito", '9' to "nove"
        )

        private val FLOAT_DIGITS = mapOf(
            '0' to "zero", '1' to "um", '2' to "dois", '3' to "três", '4' to "quatro",
            '5' to "cinco", '6' to "seis", '7' to "sete", '8' to "oito", '9' to "nove"
        )

        private val MONTH_NAMES = mapOf(
            "janeiro" to "janeiro", "fevereiro" to "fevereiro", "março" to "março",
            "abril" to "abril", "maio" to "maio", "junho" to "junho",
            "julho" to "julho", "agosto" to "agosto", "setembro" to "setembro",
            "outubro" to "outubro", "novembro" to "novembro", "dezembro" to "dezembro",
            "jan" to "janeiro", "fev" to "fevereiro", "mar" to "março",
            "abr" to "abril", "mai" to "maio", "jun" to "junho",
            "jul" to "julho", "ago" to "agosto", "set" to "setembro",
            "out" to "outubro", "nov" to "novembro", "dez" to "dezembro"
        )

        private val ORDINALS_1_10 = mapOf(
            1 to "primeiro", 2 to "segundo", 3 to "terceiro", 4 to "quarto",
            5 to "quinto", 6 to "sexto", 7 to "sétimo", 8 to "oitavo",
            9 to "nono", 10 to "décimo"
        )

        private val ORDINALS_11_19 = mapOf(
            11 to "décimo primeiro", 12 to "décimo segundo", 13 to "décimo terceiro",
            14 to "décimo quarto", 15 to "décimo quinto", 16 to "décimo sexto",
            17 to "décimo sétimo", 18 to "décimo oitavo", 19 to "décimo nono"
        )

        private val ORDINAL_TENS = mapOf(
            20 to "vigésimo", 30 to "trigésimo", 40 to "quadragésimo",
            50 to "quinquagésimo", 60 to "sexagésimo", 70 to "septuagésimo",
            80 to "octogésimo", 90 to "nonagésimo"
        )

        private val HUNDRED_ORDINALS = arrayOf(
            "", "centésimo", "ducentésimo", "trecentésimo", "quadringentésimo",
            "quingentésimo", "sexcentésimo", "septingentésimo", "octingentésimo", "noningentésimo"
        )

        private val SPECIAL_FRACTIONS = mapOf(
            2 to "meio", 3 to "terço", 4 to "quarto"
        )

        private val FRACTION_DENOM_5_10 = mapOf(
            5 to "quinto", 6 to "sexto", 7 to "sétimo",
            8 to "oitavo", 9 to "nono", 10 to "décimo"
        )

        internal fun normalizeMonth(monthStr: String): String {
            return MONTH_NAMES[monthStr.lowercase()] ?: monthStr.lowercase()
        }

        fun intToPortuguese(n: Int): String = intToPortuguese(n.toLong())

        fun intToPortuguese(n: Long): String {
            if (n == 0L) return "zero"
            if (n < 0) return "menos ${intToPortuguese(abs(n))}"

            val parts = mutableListOf<String>()
            var num = n

            if (num >= 1_000_000_000_000L) {
                val v = (num / 1_000_000_000_000L).toInt()
                val text = if (v == 1) "um trilhão" else "${convertBelow1000(v)} trilhões"
                val needE = parts.isNotEmpty() && needsEConnector(v)
                parts.add(if (needE) "e $text" else text)
                num %= 1_000_000_000_000L
            }

            if (num >= 1_000_000_000L) {
                val v = (num / 1_000_000_000L).toInt()
                val text = if (v == 1) "um bilhão" else "${convertBelow1000(v)} bilhões"
                val needE = parts.isNotEmpty() && needsEConnector(v)
                parts.add(if (needE) "e $text" else text)
                num %= 1_000_000_000L
            }

            if (num >= 1_000_000) {
                val v = (num / 1_000_000).toInt()
                val text = if (v == 1) "um milhão" else "${convertUpTo999999(v)} milhões"
                val needE = parts.isNotEmpty() && needsEConnector(v)
                parts.add(if (needE) "e $text" else text)
                num %= 1_000_000
            }

            if (num >= 1000) {
                val v = (num / 1000).toInt()
                val text = if (v == 1) "mil" else "${convertBelow1000(v)} mil"
                val needE = parts.isNotEmpty() && needsEConnector(v)
                parts.add(if (needE) "e $text" else text)
                num %= 1000
            }

            if (num > 0) {
                val v = num.toInt()
                val text = convertBelow1000(v)
                val needE = parts.isNotEmpty() && needsEConnector(v)
                parts.add(if (needE) "e $text" else text)
            }

            return parts.joinToString(" ")
        }

        private fun convertUpTo999999(n: Int): String {
            if (n < 1000) return convertBelow1000(n)
            val thousands = n / 1000
            val remainder = n % 1000
            val thousandPart = if (thousands == 1) "mil" else "${convertBelow1000(thousands)} mil"
            return if (remainder == 0) thousandPart else {
                val needE = needsEConnector(remainder)
                if (needE) "$thousandPart e ${convertBelow1000(remainder)}" else "$thousandPart ${convertBelow1000(remainder)}"
            }
        }

        private fun convertBelow1000(n: Int): String {
            if (n == 0) return ""
            if (n == 100) return "cem"
            if (n < 100) return convertBelow100(n)

            val hundreds = n / 100
            val remainder = n % 100
            val hundredWord = HUNDREDS[hundreds]
            return if (remainder == 0) hundredWord else "$hundredWord e ${convertBelow100(remainder)}"
        }

        private fun convertBelow100(n: Int): String {
            if (n <= 15) return ONES[n]
            if (n in 16..19) return TEENS[n]!!
            val tens = n / 10
            val ones = n % 10
            return if (ones == 0) TENS[tens] else "${TENS[tens]} e ${ONES[ones]}"
        }

        fun floatToPortuguese(value: Double): String {
            if (value == 0.0) return "zero"
            val negative = value < 0
            val absValue = abs(value)
            val str = absValue.toString()
            val parts = str.split('.')
            val intPart = parts[0].toLong()
            val fracPart = if (parts.size > 1) parts[1] else ""

            val intPt = intToPortuguese(intPart)
            val result = if (fracPart.isEmpty() || fracPart.all { it == '0' }) {
                intPt
            } else {
                val fracPt = fracPart.map { FLOAT_DIGITS[it] ?: it.toString() }.joinToString(" ")
                "$intPt vírgula $fracPt"
            }
            return if (negative) "menos $result" else result
        }

        fun ordinalToPortuguese(n: Int): String {
            if (n <= 0) return intToPortuguese(n.toLong())

            ORDINALS_1_10[n]?.let { return it }
            ORDINALS_11_19[n]?.let { return it }
            ORDINAL_TENS[n]?.let { return it }
            if (n == 100) return "centésimo"
            if (n == 1000) return "milésimo"

            val parts = mutableListOf<String>()
            var remaining = n

            if (remaining >= 1000) {
                val thousands = remaining / 1000
                parts.add(if (thousands == 1) "milésimo" else "${ordinalToPortuguese(thousands)} milésimo")
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

        fun ordinalToPortugueseFeminine(n: Int): String {
            return ordinalToPortuguese(n).split(" ").joinToString(" ") { word ->
                if (word.endsWith("o")) word.dropLast(1) + "a" else word
            }
        }

        fun yearToPortuguese(year: Int): String {
            return intToPortuguese(year.toLong())
        }

        fun phoneToPortuguese(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            return digits.map { PHONE_DIGITS[it] ?: it.toString() }.joinToString(" ")
        }

        fun fractionToPortuguese(num: Int, den: Int): String {
            val numerator = if (num == 1) "um" else intToPortuguese(num.toLong())
            val denominator = fractionDenominator(den, num != 1)
            return "$numerator $denominator"
        }

        private fun fractionDenominator(den: Int, plural: Boolean): String {
            SPECIAL_FRACTIONS[den]?.let { base ->
                return when {
                    plural && base == "meio" -> "meios"
                    plural -> "${base}s"
                    else -> base
                }
            }
            if (den in 5..10) {
                val form = FRACTION_DENOM_5_10[den]!!
                return if (plural) "${form}s" else form
            }
            val base = intToPortuguese(den.toLong())
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

        internal fun needsEConnector(v: Int): Boolean {
            return v < 100 || (v % 100 == 0 && v in 100..900)
        }
    }
}
