package com.wxn.base.util

import android.os.Build
import androidx.annotation.RequiresApi
import java.text.BreakIterator
import java.util.Locale
import android.icu.text.BreakIterator as IcuBreakIterator

object BreakSentenceUtil {

    const val MAX_SENTENCE_LENGTH_CJK = 40      // 中文/日文/韩文
    const val MAX_SENTENCE_LENGTH_DEFAULT = 100 // 英文/欧洲/阿拉伯等

    private const val MIN_SENTENCE_LENGTH_CJK = 3
    private const val MIN_SENTENCE_LENGTH = 5

    // 定义各种语言的标点集合（按优先级分组）
    // 优先级1：句子终结符和强停顿符
    private val strongSeparators = setOf(
        // 中文/日文/韩文
        '。', '！', '？', '；', '：',
        // 英文/欧洲语言
        '!', '?', ';', ':',
        // 阿拉伯语
        '؟',  // 阿拉伯问号
        // 印地语
        '।',  // Danda (句号)
        '॥',  // 双Danda (段落结束，视为强分隔)
        // 俄语（标点与英文相同，无需额外添加）
    )

    // 优先级2：逗号类分隔符
    private val commaSeparators = setOf(
        // 中文/日文
        '，', '、',
        // 英文/欧洲
        ',',
        // 阿拉伯语
        '،',  // 阿拉伯逗号
        '—', '–',   // 破折号
        '…',        // 省略号
    )

    private fun isCJKLocale(locale: Locale): Boolean {
        val lang = locale.language
        return lang == "zh" || lang == "ja" || lang == "ko" // 中,日,韩
    }

    // Unambiguous closing quotes - always valid split points
    private val closingQuoteChars = setOf(
        '\u201D',  // " RIGHT DOUBLE QUOTATION MARK
        '\u00BB',  // » RIGHT-POINTING DOUBLE ANGLE QUOTATION MARK 德语的闭引号
        '\u300D',  // 」 RIGHT CORNER BRACKET
        '\u300F',  // 』 RIGHT WHITE CORNER BRACKET
    )

    // ASCII 直引号需要上下文判断
    private val contextQuoteChars  = setOf(
        '"',       // U+0022 ASCII 直引号
        '\u201C',  // " LEFT DOUBLE QUOTATION MARK（英语开/德语闭）
    )

    // 闭引号后常见的字符（空白+标点）
    private val postCloseChars = setOf(
        ' ', '\t', '\n', '\r',
        ',', '，', '、', '.', '。', '!', '！', '?', '？',
        ';', '；', ':', '：', '—', '–', '…',
    )
    // 句末标点（用于"前面是句末"判断）
    private val sentenceEndChars = setOf('。', '！', '？', '.', '!', '?')

    private fun isQuoteSplitPoint(text: String, index: Int): Boolean {
        val ch = text[index]
        // Unicode 方向已明确的引号字符，无需上下文判断
        // 这些字符在所有语言中方向一致：
        // » (U+00BB) 永远指向右侧 = 闭引号
        // 」(U+300D) 『(U+300F) 永远是闭引号
        if (ch in closingQuoteChars) return true
        // ASCII quote - context check
        if (ch in contextQuoteChars) {
            if (index + 1 >= text.length) return true
            val nextChar = text[index + 1]
            if (nextChar in postCloseChars || nextChar.isWhitespace()) return true
            if (index > 0 && text[index - 1] in sentenceEndChars) return true
            return false
        }
        return false
    }

    private fun isSentenceEndPeriod(text: String, index: Int): Boolean {
        if (text[index] != '.') return false
        if (index + 1 < text.length && !isWhitespace(text[index + 1])) return false
        if (index > 0 && !text[index - 1].isLetter()) return false
        return true
    }

    private fun isNaturalSeparator(text: String, index: Int): Boolean {
        val ch = text[index]
        return ch in strongSeparators ||
                ch in commaSeparators ||
                isQuoteSplitPoint(text, index) ||
                isSentenceEndPeriod(text, index)
    }

    // 优先级3：空白字符
    private val trailingPunctuationToRemove: Set<Char> = strongSeparators + commaSeparators + setOf(
        '\u201C', '\u201D', '"',
        '\u2018', '\u2019', '\'',
        '\u00BB', '\u00AB',
        '\u300C', '\u300D', '\u300E', '\u300F',
        ')', ']', '}',
        '\uFF09', '\u3011', '\u3017',
        '\u2014', '\u2013', '-',
        '\u2026',
        '~', '*', '/', '\\', '|',
        '\u00B7', '\u30FB',
        ' ', '.', ',',
    )

    /***
     * 去除尾部一些标点字符
     */
    fun stripPunctuation(text: String): String {
        val trimText = text.trim()
        var end = trimText.length
        var start = 0
        while (end > 0 && trimText[end - 1] in trailingPunctuationToRemove) {
            end--
        }
        while (start < trimText.length && trimText[start] in trailingPunctuationToRemove) {
            start++
        }
        val result = if (start == 0 && end == trimText.length) trimText
            else if (start >= end) ""
            else  trimText.substring(start, end)
        return result
    }

    private fun isWhitespace(ch: Char) =
        ch.isWhitespace() || ch == '\u3000' || ch == '\u00A0' || ch == '\u202F'

    fun breakSentence(paragraph: String, locale: Locale): List<Triple<String, Int, Int>> {
        val result = mutableListOf<Triple<String, Int, Int>>()

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> breakSentenceN(paragraph, locale)
            else -> breakSentenceOld(paragraph, locale)
        }.forEach { item ->
            result.addAll(
                splitLongSentence(
                    item.first,
                    item.second,
                    if (isCJKLocale(locale)) MAX_SENTENCE_LENGTH_CJK else MAX_SENTENCE_LENGTH_DEFAULT,
                    if (isCJKLocale(locale)) MIN_SENTENCE_LENGTH_CJK else MIN_SENTENCE_LENGTH
                )
            )
        }

        return result
    }

    private fun breakSentenceOld(
        paragraph: String,
        locale: Locale
    ): List<Triple<String, Int, Int>> {
        val retList = arrayListOf<Triple<String, Int, Int>>()
        val breakIterator = BreakIterator.getSentenceInstance(locale)
        breakIterator.setText(paragraph)

        var start: Int = breakIterator.first()
        while (true) {
            val end = breakIterator.next()
            if (end == BreakIterator.DONE) {
                break
            }
            val sentence = paragraph.substring(start, end)
            if (sentence.isNotBlank()) {
                retList.add(Triple(sentence, start, end))
            }
            start = end
        }
        return retList
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun breakSentenceN(paragraph: String, locale: Locale): List<Triple<String, Int, Int>> {
        val retList = arrayListOf<Triple<String, Int, Int>>()
        val breakIterator = IcuBreakIterator.getSentenceInstance(locale)
        breakIterator.setText(paragraph)

        var start: Int = breakIterator.first()
        while (true) {
            val end = breakIterator.next()
            if (end == IcuBreakIterator.DONE) {
                break
            }
            val sentence = paragraph.substring(start, end)
            if (sentence.isNotBlank()) {
                retList.add(Triple(sentence, start, end))
            }
            start = end
        }
        return retList
    }


    /**
     * 将单个长句子按最大长度拆分为多个片段
     * @param sentence 原始句子
     * @param startOffset 该句子在整个段落中的起始偏移量
     * @param maxLength 最大片段长度, 超过这个长度的句子才会被处理
     * @return 列表，每个元素为 (子句文本, 子句起始偏移, 子句结束偏移)
     */
    private fun splitLongSentence(
        sentence: String,
        startOffset: Int,
        maxLength: Int,
        minLength: Int   // 最小片段长度，避免过短
    ): List<Triple<String, Int, Int>> {
        if (sentence.length <= maxLength) {
            return listOf(Triple(sentence, startOffset, startOffset + sentence.length))
        }

        val retList = arrayListOf<Triple<String, Int, Int>>()
        var remaining = sentence
        var currentOffset = startOffset
        while (remaining.length > maxLength) {
            val splitPos = findBestSplitPosition(remaining, maxLength)
            // 避免死循环：如果 splitPos == 0，强制按 maxLength 切分
            // 保护：如果 splitPos <= 0 或 splitPos 过小，强制按 maxLength 切分
            val actualSplitPos = when {
                splitPos <= 0 -> maxLength.coerceAtMost(remaining.length)
                splitPos < minLength && remaining.length > maxLength + minLength -> {
                    findNextSplitPosition(remaining, maxLength)
                }
                splitPos == maxLength && splitPos < remaining.length && !isNaturalSeparator(remaining, splitPos - 1) -> {
                    findNextSplitPosition(remaining, maxLength, maxLength + 1)
                }
                else -> splitPos
            }
            val part = remaining.substring(0, actualSplitPos)
            // 避免空片段
            if (part.isNotEmpty()) {
                retList.add(Triple(part, currentOffset, currentOffset + actualSplitPos))
            }

            remaining = remaining.substring(actualSplitPos)
            currentOffset += actualSplitPos
        }
        // 处理剩余部分
        val lastPart = remaining
        if (lastPart.isNotEmpty()) {
            retList.add(Triple(lastPart, currentOffset, currentOffset + lastPart.length))
        }

        // 后处理：合并过短片段（可选，防止出现长度 < minLength 的孤立片段）
        return mergeShortFragments(retList, minLength, maxLength)
    }


    /**
     * 在字符串前 maxLen 个字符内寻找最佳分割位置
     * 优先级（从高到低）：
     * 1. 强分隔符：句号、问号、感叹号、分号、冒号（中英文及多语言变体）
     * 2. 次强分隔符：逗号（中英文及多语言变体）
     * 3. 弱分隔符：空格、制表符
     * 4. 保底：硬截断
     */
    private fun separatorPriorityAt(text: String, index: Int): Int {
        val ch = text[index]
        return when {
            ch in strongSeparators -> 3
            isQuoteSplitPoint(text, index) -> 3
            isSentenceEndPeriod(text, index) -> 3
            ch in commaSeparators -> 2
            isWhitespace(ch) -> 1
            else -> 0
        }
    }

    private fun findBestSplitPosition(text: String, maxLen: Int): Int {
        val searchEnd = minOf(maxLen, text.length)
        if (searchEnd <= 0) return 0

        for (priority in 3 downTo 1) {
            for (i in searchEnd downTo 1) {
                if (separatorPriorityAt(text, i - 1) == priority) {
                    return i
                }
            }
        }

        return searchEnd
    }

    /**
     * 在超过 maxLength 的区域寻找最佳拆分点
     * 优先寻找标点（强分隔符 > 逗号类），实在找不到再考虑空白符
     * 搜索范围与 maxLength 关联，避免硬编码
     */
    private fun findNextSplitPosition(text: String, maxLen: Int, searchStart: Int = maxLen): Int {
        val startSearch = searchStart.coerceAtMost(text.length)
        val endSearch = text.length.coerceAtMost(startSearch + maxLen / 2)

        for (priority in 3 downTo 1) {
            for (i in startSearch..endSearch) {
                if (separatorPriorityAt(text, i - 1) == priority) {
                    return i
                }
            }
        }
        return maxLen.coerceAtMost(text.length)
    }

    /**
     * 合并过短的片段（长度 < minLength），将其附加到前一段或后一段
     */
    private fun mergeShortFragments(
        fragments: List<Triple<String, Int, Int>>,
        minLength: Int,
        maxLength: Int
    ): List<Triple<String, Int, Int>> {
        if (fragments.size <= 1) return fragments
        val result = mutableListOf<Triple<String, Int, Int>>()
        var i = 0
        while (i < fragments.size) {
            val current = fragments[i]
            if (current.first.length < minLength) {
                if (result.isNotEmpty()) {
                    val prev = result.last()
                    val mergedText = prev.first + current.first
                    if (mergedText.length <= maxLength) {
                        result[result.lastIndex] = Triple(mergedText, prev.second, current.third)
                    } else if (i + 1 < fragments.size) {
                        val next = fragments[i + 1]
                        val mergedText2 = current.first + next.first
                        if (mergedText2.length <= maxLength) {
                            result.add(Triple(mergedText2, current.second, next.third))
                            i++
                        } else {
                            result.add(current)
                        }
                    } else {
                        result.add(current)
                    }
                } else if (i + 1 < fragments.size) {
                    val next = fragments[i + 1]
                    val mergedText = current.first + next.first
                    if (mergedText.length <= maxLength) {
                        result.add(Triple(mergedText, current.second, next.third))
                        i++
                    } else {
                        result.add(current)
                    }
                } else {
                    result.add(current)
                }
            } else {
                result.add(current)
            }
            i++
        }
        return result
    }
}

