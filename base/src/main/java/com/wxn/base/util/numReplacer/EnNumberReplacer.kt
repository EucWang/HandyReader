package com.wxn.base.util.numReplacer

import kotlin.math.abs

class EnNumberReplacer : INumberReplacer {

    private val rules = mutableListOf<Rule>()

    private fun addRule(pattern: Regex, handler: (MatchResult) -> String, priority: Int) {
        rules.add(Rule(pattern, handler, priority))
        rules.sortByDescending { it.priority }
    }

    private fun registerDefaultRules() {
        val monthGroup = "(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)"

        addRule(Regex("""(?i)$monthGroup\.?\s+(\d{1,2}),?\s+(\d{4})"""), ::dateMonthDayYearHandler, 100)

        addRule(Regex("""(\d{1,2})\s+(?i)$monthGroup\.?,?\s+(\d{4})"""), ::dateDayMonthYearHandler, 100)

        addRule(Regex("""(?i)$monthGroup\.?\s+(\d{1,2})(?!\d)"""), ::dateMonthDayHandler, 100)

        addRule(Regex("""(\d+)(st|nd|rd|th)(?![a-zA-Z\d])"""), ::ordinalSuffixHandler, 95)

        addRule(Regex("""(?i)(in|since|from|between|and|year)\s+(\d{4})(?!\d)"""), ::yearInContextHandler, 90)

        addRule(Regex("""(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?(?!\d)"""), ::timeHandler, 85)

        addRule(Regex("""(-?\d+(?:\.\d+)?)[-~](-?\d+(?:\.\d+)?)(°[CF]|[℃℉])"""), ::tempRangeHandler, 82)

        addRule(Regex("""(?<!\d)(\+\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*\d[\s\-().]*(?:\d[\s\-().]*)*\d|\d{3}[\s\-().]\d{3}[\s\-().]\d{4}|\(\d{3}\)[\s\-().]*\d{3}[\s\-().]\d{4})(?!\d)"""), ::phoneHandler, 80)

        addRule(Regex("""[-+]?\d+(?:\.\d+)?%"""), ::percentHandler, 78)

        addRule(Regex("""(-?\d+(?:\.\d+)?)(°[CF]|[℃℉])"""), ::tempHandler, 76)

        addRule(Regex("""\$(\d+(?:\.\d+)?)"""), ::usdHandler, 75)
        addRule(Regex("""€(\d+(?:\.\d+)?)"""), ::euroHandler, 75)
        addRule(Regex("""£(\d+(?:\.\d+)?)"""), ::gbpHandler, 75)

        addRule(Regex("""([-+]?\d+(?:\.\d+)?)[eE]([-+]?\d+)"""), ::sciHandler, 72)

        addRule(Regex("""[-+]?\d+\.\d+"""), ::floatHandler, 70)

        addRule(Regex("""(\d+)/(\d+)"""), ::fractionHandler, 60)

        addRule(Regex("""(\d+(?:\.\d+)?)[-~](\d+(?:\.\d+)?)(?![:\d])"""), ::rangeHandler, 71)

        addRule(Regex("""(\d+):(\d+)(?!\d)"""), ::scoreHandler, 40)

        addRule(Regex("""(?<![\p{L}\d])(M{0,3}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3}))(?![\p{L}\d])"""), ::romanHandler, 30)

        addRule(Regex("""(?<!\p{L})[-+]?\d+(?!\p{L})"""), ::integerHandler, 10)
    }

    private fun dateMonthDayYearHandler(match: MatchResult): String {
        val monthStr = match.groupValues[1]
        val day = match.groupValues[2].toInt()
        val year = match.groupValues[3].toInt()
        return "${normalizeMonth(monthStr)} ${ordinalToEnglish(day)}, ${yearToEnglish(year)}"
    }

    private fun dateDayMonthYearHandler(match: MatchResult): String {
        val day = match.groupValues[1].toInt()
        val monthStr = match.groupValues[2]
        val year = match.groupValues[3].toInt()
        return "the ${ordinalToEnglish(day)} of ${normalizeMonth(monthStr)} ${yearToEnglish(year)}"
    }

    private fun dateMonthDayHandler(match: MatchResult): String {
        val monthStr = match.groupValues[1]
        val day = match.groupValues[2].toInt()
        return "${normalizeMonth(monthStr)} ${ordinalToEnglish(day)}"
    }

    private fun ordinalSuffixHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        return ordinalToEnglish(num)
    }

    private fun yearInContextHandler(match: MatchResult): String {
        val prefix = match.groupValues[1]
        val yearNum = match.groupValues[2].toIntOrNull() ?: return match.value
        return "$prefix ${yearToEnglish(yearNum)}"
    }

    private fun timeHandler(match: MatchResult): String {
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues.getOrNull(3)?.toIntOrNull()
        return if (minute == 0 && second == null) {
            "${intToEnglish(hour)} o'clock"
        } else {
            val minuteEn = if (minute == 0) "" else intToEnglish(minute)
            val secondEn = if (second != null && second != 0) " and ${intToEnglish(second)} seconds" else ""
            "${intToEnglish(hour)} $minuteEn$secondEn".trim()
        }
    }

    private fun tempRangeHandler(match: MatchResult): String {
        val fromStr = match.groupValues[1]
        val toStr = match.groupValues[2]
        val unit = match.groupValues[3]
        val fromEn = if (fromStr.contains('.')) floatToEnglish(fromStr.toDouble()) else intToEnglish(fromStr.toLong())
        val toEn = if (toStr.contains('.')) floatToEnglish(toStr.toDouble()) else intToEnglish(toStr.toLong())
        return "$fromEn to $toEn degrees ${tempUnitToEnglish(unit)}"
    }

    private fun tempHandler(match: MatchResult): String {
        val numStr = match.groupValues[1]
        val unit = match.groupValues[2]
        val numEn = if (numStr.contains('.')) floatToEnglish(numStr.toDouble()) else intToEnglish(numStr.toLong())
        return "$numEn degrees ${tempUnitToEnglish(unit)}"
    }

    private fun tempUnitToEnglish(unit: String): String = when (unit) {
        "°F", "℉" -> "Fahrenheit"
        else -> "Celsius"
    }

    private fun phoneHandler(match: MatchResult): String {
        val digits = match.value.filter { it.isDigit() }
        return digits.map { PHONE_DIGITS[it] ?: it.toString() }.joinToString(" ")
    }

    private fun percentHandler(match: MatchResult): String {
        val rawNum = match.value.dropLast(1)
        val numEn = if (rawNum.contains('.')) floatToEnglish(rawNum.toDouble())
        else intToEnglish(rawNum.toLong())
        return "$numEn percent"
    }

    private fun usdHandler(match: MatchResult): String {
        return currencyToEnglish(match.groupValues[1], "dollar", "cent")
    }

    private fun euroHandler(match: MatchResult): String {
        return currencyToEnglish(match.groupValues[1], "euro", "cent")
    }

    private fun gbpHandler(match: MatchResult): String {
        return currencyToEnglish(match.groupValues[1], "pound", "pence")
    }

    private fun currencyToEnglish(amountStr: String, mainUnit: String, subUnit: String): String {
        val parts = amountStr.split('.')
        val intPart = parts[0].toLong()
        if (intPart == 0L && parts.size > 1 && parts[1].any { it != '0' }) {
            val fracStr = parts[1].padEnd(2, '0').substring(0, 2)
            val cents = fracStr.toInt()
            val subUnitWord = if (cents == 1) subUnit else
                if (subUnit == "pence" || subUnit.endsWith("ce")) subUnit else "${subUnit}s"
            val centWord = intToEnglish(cents.toLong())
            return "$centWord $subUnitWord"
        }
        if (parts.size == 1 || parts[1].all { it == '0' }) {
            val unitWord = if (intPart == 1L) mainUnit else "${mainUnit}s"
            return "${intToEnglish(intPart)} $unitWord"
        }
        val fracStr = parts[1].padEnd(2, '0').substring(0, 2)
        val cents = fracStr.toInt()
        val mainUnitWord = if (intPart == 1L) mainUnit else "${mainUnit}s"
        val subUnitWord = if (cents == 1) subUnit else {
            if (subUnit == "pence" || subUnit.endsWith("ce")) subUnit else "${subUnit}s"
        }
        return "${intToEnglish(intPart)} $mainUnitWord and ${intToEnglish(cents.toLong())} $subUnitWord"
    }

    private fun sciHandler(match: MatchResult): String {
        val baseStr = match.groupValues[1]
        val expStr = match.groupValues[2]
        val hasDot = baseStr.contains('.')
        val base = baseStr.toDoubleOrNull() ?: 0.0
        val exp = expStr.toIntOrNull() ?: 0
        val baseEn = if (hasDot) floatToEnglish(base) else intToEnglish(baseStr.toLong())
        val expEn = ordinalToEnglish(abs(exp))
        return if (exp < 0) {
            "$baseEn times ten to the minus $expEn"
        } else {
            "$baseEn times ten to the $expEn"
        }
    }

    private fun floatHandler(match: MatchResult): String {
        return floatToEnglish(match.value.toDouble())
    }

    private fun fractionHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        val den = match.groupValues[2].toInt()
        return fractionToEnglish(num, den)
    }

    private fun rangeHandler(match: MatchResult): String {
        val fromStr = match.groupValues[1]
        val toStr = match.groupValues[2]
        val fromEn = if (fromStr.contains('.')) floatToEnglish(fromStr.toDouble()) else intToEnglish(fromStr.toLong())
        val toEn = if (toStr.contains('.')) floatToEnglish(toStr.toDouble()) else intToEnglish(toStr.toLong())
        return "$fromEn to $toEn"
    }

    private fun scoreHandler(match: MatchResult): String {
        val left = match.groupValues[1].toInt()
        val right = match.groupValues[2].toInt()
        return "${intToEnglish(left.toLong())} to ${intToEnglish(right.toLong())}"
    }

    private fun romanHandler(match: MatchResult): String {
        val roman = match.value
        if (roman == "I") return roman
        if (roman.length > 15) return roman
        val num = romanToInt(roman)
        if (num !in 1..3999) return roman
        val intToRoman = intToRomanNumeral(num)
        if (intToRoman.equals(roman, ignoreCase = true)) return intToEnglish(num.toLong())
        return roman
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

    private fun integerHandler(match: MatchResult): String {
        return intToEnglish(match.value.toLong())
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
            "zero", "one", "two", "three", "four",
            "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen",
            "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
        )
        private val TENS = arrayOf(
            "", "", "twenty", "thirty", "forty",
            "fifty", "sixty", "seventy", "eighty", "ninety"
        )
        private val GROUP_UNITS = arrayOf("", "thousand", "million", "billion", "trillion")

        private val PHONE_DIGITS = mapOf(
            '0' to "oh", '1' to "one", '2' to "two", '3' to "three", '4' to "four",
            '5' to "five", '6' to "six", '7' to "seven", '8' to "eight", '9' to "nine"
        )

        private val FLOAT_DIGITS = mapOf(
            '0' to "zero", '1' to "one", '2' to "two", '3' to "three", '4' to "four",
            '5' to "five", '6' to "six", '7' to "seven", '8' to "eight", '9' to "nine"
        )

        private val MONTH_NAMES = mapOf(
            "january" to "January", "february" to "February", "march" to "March",
            "april" to "April", "may" to "May", "june" to "June",
            "july" to "July", "august" to "August", "september" to "September",
            "october" to "October", "november" to "November", "december" to "December",
            "jan" to "January", "feb" to "February", "mar" to "March",
            "apr" to "April", "jun" to "June", "jul" to "July",
            "aug" to "August", "sep" to "September", "sept" to "September",
            "oct" to "October", "nov" to "November", "dec" to "December"
        )

        private val IRREGULAR_ORDINALS = mapOf(
            1 to "first", 2 to "second", 3 to "third",
            5 to "fifth", 8 to "eighth", 9 to "ninth",
            11 to "eleventh", 12 to "twelfth"
        )

        private val SPECIAL_FRACTIONS = mapOf(
            2 to "half", 3 to "third", 4 to "quarter"
        )

        internal fun normalizeMonth(monthStr: String): String {
            return MONTH_NAMES[monthStr.lowercase()] ?: monthStr
        }

        fun intToEnglish(n: Int): String = intToEnglish(n.toLong())

        fun intToEnglish(n: Long): String {
            if (n == 0L) return "zero"
            if (n < 0) return "minus ${intToEnglish(abs(n))}"
            return convertPositive(n)
        }

        private fun convertPositive(n: Long): String {
            if (n < 20) return ONES[n.toInt()]
            if (n < 100) {
                val tens = (n / 10).toInt()
                val ones = (n % 10).toInt()
                return if (ones == 0) TENS[tens] else "${TENS[tens]}-${ONES[ones]}"
            }
            if (n < 1000) {
                val hundreds = (n / 100).toInt()
                val remainder = n % 100
                return if (remainder == 0L) {
                    "${ONES[hundreds]} hundred"
                } else {
                    "${ONES[hundreds]} hundred and ${convertPositive(remainder)}"
                }
            }
            val groups = mutableListOf<Pair<Long, String>>()
            var num = n
            var unitIdx = 0
            while (num > 0) {
                val groupVal = (num % 1000).toInt()
                if (groupVal > 0 && unitIdx < GROUP_UNITS.size) {
                    groups.add(Pair(groupVal.toLong(), GROUP_UNITS[unitIdx]))
                }
                num /= 1000
                unitIdx++
            }
            val parts = mutableListOf<String>()
            for (i in groups.size - 1 downTo 0) {
                val (groupVal, unit) = groups[i]
                val groupText = if (groupVal < 100 && parts.isNotEmpty()) {
                    "and ${convertPositive(groupVal)}"
                } else {
                    convertPositive(groupVal)
                }
                if (unit.isNotEmpty()) {
                    parts.add("$groupText $unit")
                } else {
                    parts.add(groupText)
                }
            }
            return parts.joinToString(" ")
        }

        fun floatToEnglish(value: Double): String {
            if (value == 0.0) return "zero"
            val negative = value < 0
            val absValue = abs(value)
            val str = absValue.toString()
            val parts = str.split('.')
            val intPart = parts[0].toLong()
            val fracPart = if (parts.size > 1) parts[1] else ""

            val intEn = intToEnglish(intPart)
            val result = if (fracPart.isEmpty() || fracPart.all { it == '0' }) {
                intEn
            } else {
                val fracEn = fracPart.map { FLOAT_DIGITS[it] ?: it.toString() }.joinToString(" ")
                "$intEn point $fracEn"
            }
            return if (negative) "minus $result" else result
        }

        fun ordinalToEnglish(n: Int): String {
            if (n <= 0) return intToEnglish(n.toLong())
            IRREGULAR_ORDINALS[n]?.let { return it }
            val lastTwo = n % 100
            if (lastTwo in 11..19) {
                val base = intToEnglish(n.toLong())
                return convertLastWordToOrdinal(base)
            }
            val base = intToEnglish(n.toLong())
            val lastDigit = n % 10
            val suffix = when (lastDigit) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
            val hyphenIdx = base.lastIndexOf('-')
            if (hyphenIdx >= 0) {
                val prefix = base.substring(0, hyphenIdx)
                val lastWord = base.substring(hyphenIdx + 1)
                val ordLast = ordinalSingleWord(lastWord) ?: "$lastWord$suffix"
                return "$prefix-$ordLast"
            }
            val spaceIdx = base.lastIndexOf(' ')
            if (spaceIdx >= 0) {
                val prefix = base.substring(0, spaceIdx)
                val lastWord = base.substring(spaceIdx + 1)
                val ordLast = ordinalSingleWord(lastWord) ?: "$lastWord$suffix"
                return "$prefix $ordLast"
            }
            return ordinalSingleWord(base) ?: "$base$suffix"
        }

        private fun convertLastWordToOrdinal(base: String): String {
            val hyphenIdx = base.lastIndexOf('-')
            if (hyphenIdx >= 0) {
                val prefix = base.substring(0, hyphenIdx)
                val lastWord = base.substring(hyphenIdx + 1)
                val ordLast = ordinalSingleWord(lastWord) ?: "${lastWord}th"
                return "$prefix-$ordLast"
            }
            val spaceIdx = base.lastIndexOf(' ')
            if (spaceIdx >= 0) {
                val prefix = base.substring(0, spaceIdx)
                val lastWord = base.substring(spaceIdx + 1)
                val ordLast = ordinalSingleWord(lastWord) ?: "${lastWord}th"
                return "$prefix $ordLast"
            }
            return ordinalSingleWord(base) ?: "${base}th"
        }

        private fun ordinalSingleWord(word: String): String? {
            return when (word) {
                "one" -> "first"
                "two" -> "second"
                "three" -> "third"
                "four" -> "fourth"
                "five" -> "fifth"
                "six" -> "sixth"
                "seven" -> "seventh"
                "eight" -> "eighth"
                "nine" -> "ninth"
                "ten" -> "tenth"
                "eleven" -> "eleventh"
                "twelve" -> "twelfth"
                "thirteen" -> "thirteenth"
                "fourteen" -> "fourteenth"
                "fifteen" -> "fifteenth"
                "sixteen" -> "sixteenth"
                "seventeen" -> "seventeenth"
                "eighteen" -> "eighteenth"
                "nineteen" -> "nineteenth"
                "twenty" -> "twentieth"
                "thirty" -> "thirtieth"
                "forty" -> "fortieth"
                "fifty" -> "fiftieth"
                "sixty" -> "sixtieth"
                "seventy" -> "seventieth"
                "eighty" -> "eightieth"
                "ninety" -> "ninetieth"
                "hundred" -> "hundredth"
                "thousand" -> "thousandth"
                else -> null
            }
        }

        fun yearToEnglish(year: Int): String {
            if (year < 100) return intToEnglish(year.toLong())
            if (year % 100 == 0) {
                val hundreds = year / 100
                if (year == 2000) return "two thousand"
                return "${intToEnglish(hundreds.toLong())} hundred"
            }
            if (year in 2000..2009) {
                return "two thousand and ${intToEnglish((year % 100).toLong())}"
            }
            val upper = year / 100
            val lower = year % 100
            return "${intToEnglish(upper.toLong())} ${intToEnglish(lower.toLong())}"
        }

        fun phoneToEnglish(phone: String): String {
            val digits = phone.filter { it.isDigit() }
            return digits.map { PHONE_DIGITS[it] ?: it.toString() }.joinToString(" ")
        }

        fun fractionToEnglish(num: Int, den: Int): String {
            val numerator = intToEnglish(num.toLong())
            val denominator = fractionDenominator(den, num != 1)
            return "$numerator $denominator"
        }

        private fun fractionDenominator(den: Int, plural: Boolean): String {
            SPECIAL_FRACTIONS[den]?.let { base ->
                return if (plural) {
                    if (base == "half") "halves" else "${base}s"
                } else base
            }
            if (den in 2..10) {
                val ord = ordinalToEnglish(den)
                return if (plural) "${ord}s" else ord
            }
            val base = intToEnglish(den.toLong()).replace(" ", "-")
            val suffix = if (base.endsWith("y")) {
                "ieth"
            } else if (base.endsWith("d") || base.endsWith("t") || base.endsWith("e")) {
                "th"
            } else {
                "th"
            }
            val ordForm = if (base.endsWith("y")) {
                "${base.dropLast(1)}ieth"
            } else {
                "${base}th"
            }
            return if (plural) {
                "${ordForm}s"
            } else {
                ordForm
            }
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
    }
}
