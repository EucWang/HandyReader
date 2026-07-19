package com.wxn.base.util.numReplacer

import kotlin.math.abs


/**
 * 文本数字替换引擎：将文本中的数字根据上下文转换为中文发音
 * 支持：整数、浮点数、正负数、电话号码、年份/日期、时间、百分比、分数、
 * 范围、货币、序号、量词、大数字、科学计数法、罗马数字、比分、温度（摄氏/华氏）
 */
class ZhNumberReplacer : INumberReplacer {

    private val rules = mutableListOf<Rule>()

    fun addRule(pattern: String, handler: (MatchResult) -> String, priority: Int = 0) {
        addRule(Regex(pattern), handler, priority)
    }

    private fun addRule(pattern: Regex, handler: (MatchResult) -> String, priority: Int) {
        rules.add(Rule(pattern, handler, priority))
        rules.sortByDescending { it.priority }
    }

    private fun registerDefaultRules() {
        // p100: 序号（第1 → 第一）
        addRule(Regex("第(\\d+)"), ::ordinalHandler, priority = 100)

        // p95: 年份（2024年 → 二零二四年）
        addRule(Regex("(?<!\\d)(\\d{4})年"), ::yearHandler, priority = 95)

        // p90: 日期（3月15日）
        addRule(Regex("(\\d{1,2})月(\\d{1,2})日"), ::dateHandler, priority = 90)

        // p85: 时间（14:30 或 3:00:05）
        addRule(Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?!\\d)"), ::timeHandler, 85)

        // p82: 温度范围（20-30°C, 68-80°F）
        addRule(
            Regex("""(-?\d+(?:\.\d+)?)[-~](-?\d+(?:\.\d+)?)(°[CFK]|[℃℉])"""),
            ::temperatureRangeHandler, priority = 82
        )

        // p80: 电话号码（1开头11位数字）
        addRule(Regex("(?<!\\d)1[3-9]\\d{9}(?!\\d)"), ::phoneHandler, 80)

        // p78: 百分比（支持小数：3.14%）
        addRule(Regex("""[-+]?\d+(?:\.\d+)?%"""), ::percentHandler, priority = 78)

        // p76: 温度单体（20°C, 68°F, 25℃）
        addRule(
            Regex("""(-?\d+(?:\.\d+)?)(°[CFK]|[℃℉])"""),
            ::temperatureHandler, priority = 76
        )

        // p75: 货币（人民币/美元）
        addRule(Regex("[¥￥](\\d+(?:\\.\\d+)?)"), ::cnyHandler, priority = 75)
        addRule(Regex("\\$(\\d+(?:\\.\\d+)?)"), ::usdHandler, priority = 75)

        // p72: 科学计数法
        addRule(Regex("""([-+]?\d+(?:\.\d+)?)[eE]([-+]?\d+)"""), ::scientificHandler, priority = 72)

        // p70: 浮点数（包括正负）
        addRule(Regex("[-+]?\\d+\\.\\d+"), ::floatHandler, priority = 70)

        // p60: 分数
        addRule(Regex("(\\d+)/(\\d+)"), ::fractionHandler, priority = 60)

        // p55: 范围（20-30，支持小数）
        addRule(Regex("""(\d+(?:\.\d+)?)[-~](\d+(?:\.\d+)?)(?![:.\d])"""), ::rangeHandler, priority = 55)

        // p40: 比分（3:2）
        addRule(Regex("(\\d+):(\\d+)(?!\\d)"), ::scoreHandler, priority = 40)

        // p30: 罗马数字（仅匹配独立的标准罗马数字）
        addRule(Regex("(?<![a-zA-Z\\d])(M{0,3}(?:CM|CD|D?C{0,3})(?:XC|XL|L?X{0,3})(?:IX|IV|V?I{0,3}))(?![a-zA-Z\\d])"), ::romanHandler, priority = 30)

        // p20: 量词搭配（2只 → 两只）
        addRule(Regex("(\\d+)([个只条本张片杯件位台辆支双对把块道场门首棵颗架间座层根朵])"), ::measureWordHandler, priority = 20)

        // p10: 普通整数（兜底）
        addRule(Regex("(?<![a-zA-Z])[-+]?\\d+(?![a-zA-Z])"), ::integerHandler, priority = 10)
    }

    // ==================== 各场景处理器 ====================

    private fun scientificHandler(match: MatchResult): String {
        val baseStr = match.groupValues[1]
        val expStr = match.groupValues[2]
        val hasDot = baseStr.contains('.')
        val base = baseStr.toDoubleOrNull() ?: 0.0
        val exp = expStr.toIntOrNull() ?: 0

        val baseCn = if (hasDot) floatToChinese(base) else intToChinese(baseStr.toLong())
        val expCn = intToChinese(exp)

        return "${baseCn}乘以十的${expCn}次方"
    }

    private fun ordinalHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        return "第${intToChinese(num.toLong())}"
    }

    private fun yearHandler(match: MatchResult): String {
        val year = match.groupValues[1]
        val chineseDigits = year.map { DIGITS[it] ?: it.toString() }.joinToString("")
        return "${chineseDigits}年"
    }

    private fun dateHandler(match: MatchResult): String {
        val month = match.groupValues[1].toInt()
        val day = match.groupValues[2].toInt()
        return "${intToChinese(month.toLong())}月${intToChinese(day.toLong())}日"
    }

    private fun timeHandler(match: MatchResult): String {
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        val second = match.groupValues.getOrNull(3)?.toIntOrNull()
        val hourCn = intToChinese(hour.toLong())
        return if (minute == 0 && second == null) {
            "${hourCn}点整"
        } else {
            val minuteCn = if (minute == 0) "" else intToChinese(minute.toLong()) + "分"
            val secondCn = if (second != null && second != 0) intToChinese(second.toLong()) + "秒" else ""
            "${hourCn}点${minuteCn}${secondCn}".trim()
        }
    }

    private fun phoneHandler(match: MatchResult): String {
        return phoneToChinese(match.value)
    }

    private fun floatHandler(match: MatchResult): String {
        return floatToChinese(match.value.toDouble())
    }

    private fun percentHandler(match: MatchResult): String {
        val rawNum = match.value.dropLast(1)
        val isNeg = rawNum.startsWith("-")
        val numStr = if (isNeg || rawNum.startsWith("+")) rawNum.drop(1) else rawNum
        val numCn = if (numStr.contains('.')) {
            floatToChinese(numStr.toDouble())
        } else {
            intToChinese(numStr.toLong())
        }
        return if (isNeg) "负百分之$numCn" else "百分之$numCn"
    }

    private fun fractionHandler(match: MatchResult): String {
        val numerator = match.groupValues[1].toInt()
        val denominator = match.groupValues[2].toInt()
        return "${intToChinese(denominator.toLong())}分之${intToChinese(numerator.toLong())}"
    }

    private fun rangeHandler(match: MatchResult): String {
        val fromStr = match.groupValues[1]
        val toStr = match.groupValues[2]
        val fromCn = if (fromStr.contains('.')) floatToChinese(fromStr.toDouble()) else intToChinese(fromStr.toLong())
        val toCn = if (toStr.contains('.')) floatToChinese(toStr.toDouble()) else intToChinese(toStr.toLong())
        return "${fromCn}到${toCn}"
    }

    private fun cnyHandler(match: MatchResult): String {
        val amountStr = match.groupValues[1]
        val chinese = if (amountStr.contains('.')) floatToChinese(amountStr.toDouble())
        else intToChinese(amountStr.toLong())
        return "${chinese}元"
    }

    private fun usdHandler(match: MatchResult): String {
        val amountStr = match.groupValues[1]
        val chinese = if (amountStr.contains('.')) floatToChinese(amountStr.toDouble())
        else intToChinese(amountStr.toLong())
        return "${chinese}美元"
    }

    private fun temperatureHandler(match: MatchResult): String {
        val numStr = match.groupValues[1]
        val unit = match.groupValues[2]
        val numCn = if (numStr.contains('.')) floatToChinese(numStr.toDouble()) else intToChinese(numStr.toLong())
        return "$numCn${temperatureUnitToChinese(unit)}"
    }

    private fun temperatureRangeHandler(match: MatchResult): String {
        val fromStr = match.groupValues[1]
        val toStr = match.groupValues[2]
        val unit = match.groupValues[3]
        val fromCn = if (fromStr.contains('.')) floatToChinese(fromStr.toDouble()) else intToChinese(fromStr.toLong())
        val toCn = if (toStr.contains('.')) floatToChinese(toStr.toDouble()) else intToChinese(toStr.toLong())
        return "${fromCn}到${toCn}${temperatureUnitToChinese(unit)}"
    }

    private fun temperatureUnitToChinese(unit: String): String = when (unit) {
        "°F", "℉" -> "华氏度"
        "°K" -> "开尔文"
        else -> "摄氏度"
    }

    private fun scoreHandler(match: MatchResult): String {
        val left = match.groupValues[1].toInt()
        val right = match.groupValues[2].toInt()
        return "${intToChinese(left.toLong())}比${intToChinese(right.toLong())}"
    }

    private fun romanHandler(match: MatchResult): String {
        val roman = match.value
        if (roman == "I") return roman
        if (roman.length > 15) return roman
        val num = romanToInt(roman)
        if (num !in 1..3999) return roman
        val intToRoman = intToRomanNumeral(num)
        if (intToRoman.equals(roman, ignoreCase = true)) return intToChinese(num.toLong())
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

    private fun measureWordHandler(match: MatchResult): String {
        val num = match.groupValues[1].toInt()
        val word = match.groupValues[2]
        val numCn = when (num) {
            1 -> "一"
            2 -> "两"
            else -> intToChinese(num.toLong())
        }
        return "$numCn$word"
    }

    private fun integerHandler(match: MatchResult): String {
        return intToChinese(match.value.toLong())
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

    // ==================== 核心数字转换工具 ====================

    companion object {
        private const val MAX_CHAPTER_NUMBER = 99999

        /**
         * 中文数字串 → 整数（反向反查）。
         *
         * 复用已验证的 [intToChinese] 正向生成 + 字符串比对，零歧义。
         *
         * 范围限制：1..[MAX_CHAPTER_NUMBER]（99999，覆盖所有实际章节号）。
         * 超出、空串、非中文数字均返回 null。
         *
         * 性能：单次调用最坏 99999 次字符串比较 ~5ms（首次未缓存）；
         * [reverseLookupCache] 缓存后 < 0.1ms。`ConcurrentHashMap` 保证
         * 多本书并发扫描时的线程安全。
         *
         * 用途：`ChapterScanner.parseChapterNumber` 通过反查抹平「一/1/I」数字格式差异。
         */
        fun chineseToInt(s: String): Int? {
            if (s.isEmpty()) return null
            // 快速排除：明显非中文数字字符直接返回 null（避免 99999 次无效循环）
            val cjkDigits = "零一二三四五六七八九十百千万两"
            if (s.any { it !in cjkDigits }) return null
            // 快速排除：零在 intToChinese 输出中只出现在「中间零」「前导零」位置，
            // 不可能独占整个数字串（1..99999 不会生成 "零" / "零X" / "X零" 这种纯零开头的串）。
            // 提前拦截 "零"、"零零"、"零一" 等无效输入。
            if (s == "零" || s.startsWith("零")) return null
            reverseLookupCache[s]?.let { return it }
            for (n in 1..MAX_CHAPTER_NUMBER) {
                if (intToChinese(n.toLong()) == s) {
                    reverseLookupCache[s] = n
                    return n
                }
            }
            return null
        }

        private val reverseLookupCache =
            java.util.concurrent.ConcurrentHashMap<String, Int>()

        private val SMALL_NUM =
            arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
        private val TEL_DIGITS = mapOf(
            '0' to "零", '1' to "幺", '2' to "二", '3' to "三", '4' to "四",
            '5' to "五", '6' to "六", '7' to "七", '8' to "八", '9' to "九"
        )
        private val DIGITS = mapOf(
            '0' to "零", '1' to "一", '2' to "二", '3' to "三", '4' to "四",
            '5' to "五", '6' to "六", '7' to "七", '8' to "八", '9' to "九"
        )
        private val GROUP_UNITS = arrayOf("", "万", "亿", "兆", "京")

        fun intToChinese(n: Int): String = intToChinese(n.toLong())

        /**
         * 整数转中文：四位分组，每组用 fourDigitToChinese 独立转换，再拼接组单位。
         * 正确处理组间零、组内前导零、10~19 的"十"省略。
         */
        private fun intToChinese(n: Long): String {
            if (n == 0L) return "零"
            val negative = n < 0
            val absN = abs(n)
            val numStr = absN.toString()
            val paddedLen = ((numStr.length + 3) / 4) * 4
            val padded = numStr.padStart(paddedLen, '0')
            val result = StringBuilder()
            var needZero = false

            for (groupIdx in 0 until paddedLen step 4) {
                val groupDigits = padded.substring(groupIdx, groupIdx + 4)
                val groupValue = groupDigits.toInt()
                val groupUnit = GROUP_UNITS[(paddedLen - groupIdx) / 4 - 1]
                if (groupValue == 0) {
                    if (result.isNotEmpty()) needZero = true
                    continue
                }
                val hasLeadingZero = groupDigits[0] == '0' && result.isNotEmpty()
                if (needZero || hasLeadingZero) {
                    result.append("零")
                    needZero = false
                }
                result.append(fourDigitToChinese(groupValue))
                if (groupUnit.isNotEmpty()) {
                    result.append(groupUnit)
                }
            }

            var r = result.toString()
            r = r.replace("零+".toRegex(), "零")
            if (r.endsWith("零")) r = r.dropLast(1)
            return if (negative) "负$r" else r
        }

        /**
         * 四位以内数字转中文（1~9999）
         * 10~19 在无更高位时省略"一"：10→十, 12→十二, 110→一百一十
         */
        private fun fourDigitToChinese(num: Int): String {
            if (num == 0) return ""
            if (num in 1..10) {
                return if (num == 10) "十" else SMALL_NUM[num]
            }
            val thousand = num / 1000
            val hundred = (num % 1000) / 100
            val ten = (num % 100) / 10
            val one = num % 10

            val sb = StringBuilder()
            if (thousand > 0) {
                sb.append(SMALL_NUM[thousand]).append("千")
            }
            if (hundred > 0) {
                sb.append(SMALL_NUM[hundred]).append("百")
            } else if (thousand > 0 && (ten > 0 || one > 0)) {
                sb.append("零")
            }
            if (ten > 0) {
                if (ten == 1 && thousand == 0 && hundred == 0) {
                    sb.append("十")
                } else {
                    sb.append(SMALL_NUM[ten]).append("十")
                }
            } else if (hundred > 0 && one > 0) {
                sb.append("零")
            }
            if (one > 0) {
                sb.append(SMALL_NUM[one])
            }
            return sb.toString()
        }

        /**
         * 浮点数转中文
         */
        fun floatToChinese(value: Double): String {
            if (value == 0.0) return "零"
            val negative = value < 0
            val absValue = abs(value)
            val str = absValue.toString()
            val parts = str.split('.')
            val intPart = parts[0].toLong()
            val fracPart = if (parts.size > 1) parts[1] else ""

            val intCn = intToChinese(intPart)
            val result = if (fracPart.isEmpty() || fracPart.all { it == '0' }) {
                intCn
            } else {
                val fracCn = fracPart.map { DIGITS[it] ?: it.toString() }.joinToString("")
                "${intCn}点$fracCn"
            }
            return if (negative) "负$result" else result
        }

        /**
         * 电话号码逐位转换（幺、二...）
         */
        fun phoneToChinese(phone: String): String {
            return phone.map { TEL_DIGITS[it] ?: it.toString() }.joinToString("")
        }

        /**
         * 罗马数字转整数
         */
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
