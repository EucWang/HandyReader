package com.wxn.base.ext


import android.net.Uri
import java.io.File

val removeHtmlRegex = "</?(?:div|p|br|hr|h\\d|article|dd|dl)[^>]*>".toRegex()
val imgRegex = "<img[^>]*>".toRegex()
val notImgHtmlRegex = "</?(?!img)\\w+[^>]*>".toRegex()

fun String?.safeTrim() = if (this.isNullOrBlank()) null else this.trim()

fun String?.isContentPath(): Boolean = this?.startsWith("content://") == true

fun String.parseToUri(): Uri {
    return if (isContentPath()) {
        Uri.parse(this)
    } else {
        Uri.fromFile(File(this))
    }
}

fun String?.isAbsUrl() =
    this?.let {
        it.startsWith("http://", true)
                || it.startsWith("https://", true)
    } ?: false

fun String?.isJson(): Boolean =
    this?.run {
        val str = this.trim()
        when {
            str.startsWith("{") && str.endsWith("}") -> true
            str.startsWith("[") && str.endsWith("]") -> true
            else -> false
        }
    } ?: false

fun String?.isJsonObject(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("{") && str.endsWith("}")
    } ?: false

fun String?.isJsonArray(): Boolean =
    this?.run {
        val str = this.trim()
        str.startsWith("[") && str.endsWith("]")
    } ?: false

fun String?.htmlFormat(): String {
    this ?: return ""
    return this
        .replace(imgRegex, "\n$0\n")
        .replace(removeHtmlRegex, "\n")
        .replace(notImgHtmlRegex, "")
        .replace("\\s*\\n+\\s*".toRegex(), "\n　　")
        .replace("^[\\n\\s]+".toRegex(), "　　")
        .replace("[\\n\\s]+$".toRegex(), "")
}

fun String.splitNotBlank(vararg delimiter: String): Array<String> = run {
    this.split(*delimiter).map { it.trim() }.filterNot { it.isBlank() }.toTypedArray()
}

fun String.splitNotBlank(regex: Regex, limit: Int = 0): Array<String> = run {
    this.split(regex, limit).map { it.trim() }.filterNot { it.isBlank() }.toTypedArray()
}

/**
 * 将字符串拆分为单个字符,包含emoji
 */
fun String.toStringArray(): Array<String> {
    var codePointIndex = 0
    return try {
        Array(codePointCount(0, length)) {
            val start = codePointIndex
            codePointIndex = offsetByCodePoints(start, 1)
            substring(start, codePointIndex)
        }
    } catch (e: Exception) {
        split("").toTypedArray()
    }
}


/****
 * 判断一个字符是否是标点符号
 */
fun Char.isPunctuation(): Boolean {
    return when (this.category) {
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

fun Char.isCJKChar(): Boolean {
    val code = this.code
    return code in 0x4E00..0x9FFF      // CJK Unified Ideographs (中日韩统一表意文字)
            || code in 0x3400..0x4DBF      // CJK Unified Ideographs Extension A
            || code in 0x3000..0x303F      // CJK Symbols and Punctuation
            || code in 0x3040..0x309F      // Hiragana (平假名)
            || code in 0x30A0..0x30FF      // Katakana (片假名)
            || code in 0xAC00..0xD7AF      // Hangul Syllables (韩文音节)
            || code in 0xFF00..0xFFEF      // Fullwidth Forms (全角字符)
}
