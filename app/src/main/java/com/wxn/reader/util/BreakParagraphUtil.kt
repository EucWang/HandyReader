package com.wxn.reader.util

import com.wxn.base.bean.Locator
import com.wxn.bookread.data.model.SpeakSentence
import com.wxn.reader.util.tts.BreakSentenceUtil
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
}