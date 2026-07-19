package com.wxn.bookparser.exts

// 预编译复用：clearMarkdown / clearAllMarkdown 在 TXT 章节扫描（每行 1 次）与
// 内容渲染（每行 2 次）路径上被高频调用。改为 top-level 常量后，N 行书从 N 次
// 正则编译降为 0 次。模式串与原内联写法完全一致，行为不变。
private val MARKDOWN_RE = Regex("(_+)|(\\*+)")
private val ALL_MARKDOWN_RE = Regex("(_+)|(\\*+)|(#+)")

fun String.removeTrailingZero(): String {
    if (!this.contains('.'))
        return this
    return this
        .dropLastWhile { it == '0' }
        .dropLastWhile { it == '.' }
}

fun Double.removeDigits(digits: Int) = "%.${digits}f".format(this).replace(",", ".")

fun Float.calculateProgress(digits: Int): String {
    return (this * 100)
        .toDouble()
        .removeDigits(digits)
        .removeTrailingZero()
        .dropWhile { it == '-' }
}

fun Float?.coerceAndPreventNaN(): Float {
    if (this == null) return 0f
    if (isNaN()) return 0f
    return this.coerceIn(0f, 1f)
}

fun String.clearMarkdown(): String {
    return replace(MARKDOWN_RE, "")
}

fun String.clearAllMarkdown(): String {
    return replace(ALL_MARKDOWN_RE, "").trim()
}

fun String.containsVisibleText(): Boolean {
    return any { it.isVisibleCharacter() }
}

fun Char.isVisibleCharacter(): Boolean {
    return when (this.category) {
        CharCategory.UPPERCASE_LETTER,
        CharCategory.LOWERCASE_LETTER,
        CharCategory.TITLECASE_LETTER,
        CharCategory.MODIFIER_LETTER,
        CharCategory.OTHER_LETTER,
        CharCategory.DECIMAL_DIGIT_NUMBER,
        CharCategory.LETTER_NUMBER,
        CharCategory.OTHER_NUMBER,
        CharCategory.MATH_SYMBOL,
        CharCategory.CURRENCY_SYMBOL,
        CharCategory.OTHER_SYMBOL,
        CharCategory.INITIAL_QUOTE_PUNCTUATION,
        CharCategory.FINAL_QUOTE_PUNCTUATION,
        CharCategory.CONNECTOR_PUNCTUATION,
        CharCategory.DASH_PUNCTUATION,
        CharCategory.START_PUNCTUATION,
        CharCategory.END_PUNCTUATION,
        CharCategory.OTHER_PUNCTUATION -> true

        else -> false
    }
}