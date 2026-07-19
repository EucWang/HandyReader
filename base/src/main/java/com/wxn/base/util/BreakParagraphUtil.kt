package com.wxn.base.util

import com.wxn.base.bean.Locator
import com.wxn.base.bean.Locator.Companion.getUtteranceId
import com.wxn.base.bean.SpeakSentence
import java.util.Locale

object BreakParagraphUtil {

    /***
     * 将一个自然段落分割成 多个语句并返回
     */
    fun breakParagraph(paragraph: String,
                       language: Locale,
                       chapterIndex: Int,
                       paragraphIndex: Int): List<SpeakSentence> {
        val sentences = arrayListOf<SpeakSentence>()
        BreakSentenceUtil.breakSentence(paragraph, language).forEach { triple ->
            sentences.add(
                SpeakSentence(
                    triple.first,
                    Locator(
                        id = getUtteranceId(chapterIndex, paragraphIndex, triple.second, paragraphIndex, triple.third),
                        chapterIndex = chapterIndex,
                        startParagraphIndex = paragraphIndex,
                        endParagraphIndex = paragraphIndex,
                        startTextOffset = triple.second,
                        endTextOffset = triple.third,
                        progression = 0.0)
                )
            )
        }
        return sentences
    }

    /**
     * 为 TTS 引擎预处理文本：
     * 1. 去除首尾标点（stripPunctuation）
     * 2. 删除句中的引号、书名号（朗读无声，避免 TTS 噪音）
     * 3. 将句中的括号替换为空格（插入语需微停顿）
     * 4. 将中文双破折号替换为冒号（TTS 停顿）
     * 5. 折叠连续空格
     */
    fun normalizeForTts(text: String): String {
        val stripped = BreakSentenceUtil.stripPunctuation(text)
        return buildString(stripped.length) {
            var lastWasSpace = false
            var i = 0
            while (i < stripped.length) {
                val c = stripped[i]
                when {
                    c == '\u2014' && i + 1 < stripped.length && stripped[i + 1] == '\u2014' -> {
                        append(':')
                        lastWasSpace = false
                        i += 2
                    }
                    c == '\u201C' || c == '\u201D' || c == '\u300A' || c == '\u300B' -> {
                        i++
                    }
                    c == '(' || c == ')' || c == '\uFF08' || c == '\uFF09' ||
                    c == '\uFF3B' || c == '\uFF3D' || c == '\u3010' || c == '\u3011' -> {
                        if (!lastWasSpace) {
                            append(' ')
                            lastWasSpace = true
                        }
                        i++
                    }
                    c == ' ' -> {
                        if (!lastWasSpace) {
                            append(' ')
                            lastWasSpace = true
                        }
                        i++
                    }
                    else -> {
                        append(c)
                        lastWasSpace = false
                        i++
                    }
                }
            }
        }.trim()
    }

    private val apostropheChars = setOf(
        '\u0027',
        '\u2018',
        '\u2019',
    )

    fun isAbbreviationApostrophe(chars: List<String>, index: Int): Boolean {
        val c = chars.getOrNull(index)?.firstOrNull() ?: return false
        if (c !in apostropheChars) return false
        val prevChar = chars.getOrNull(index - 1)?.firstOrNull()
        val nextChar = chars.getOrNull(index + 1)?.firstOrNull()
        return prevChar?.isLetter() == true && nextChar?.isLetter() == true
    }
}