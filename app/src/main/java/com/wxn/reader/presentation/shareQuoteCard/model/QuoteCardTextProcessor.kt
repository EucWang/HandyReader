package com.wxn.reader.presentation.shareQuoteCard.model

/**
 * 文本去噪处理器（纯 Kotlin，KMP-clean）。
 *
 * 仅做格式净化（折叠多余空白、移除控制字符、首尾 trim），
 * **不删除任何内容行**——保护用户选中的原始文本，避免误删页码/年份/数字等正文内容。
 */
object QuoteCardTextProcessor {

    /**
     * 去噪选中文本。
     *
     * @param rawText 原始选中文本
     * @param chapterName 当前章节名（保留参数兼容签名，当前不使用）
     * @return 去噪后的文本（仅格式净化，不删内容）
     */
    @Suppress("UNUSED_PARAMETER")
    fun denoise(rawText: String, chapterName: String?): String {
        if (rawText.isBlank()) return ""

        return rawText
            .replaceMultipleWhitespace()
            .removeControlChars()
            .trim()
    }

    /** 折叠连续空白为单个空格，保留换行 */
    private fun String.replaceMultipleWhitespace(): String {
        return this.replace(Regex("[ \\t]+"), " ")
    }

    /** 移除控制字符（保留换行 \n 和制表符 \t） */
    private fun String.removeControlChars(): String {
        return this.filter { char ->
            char == '\n' || char == '\t' || char.code >= 0x20
        }
    }

    /** 文本是否过短（< 2 个非空白字符） */
    fun isTooShort(text: String): Boolean {
        return text.replace("\\s".toRegex(), "").length < 2
    }
}
