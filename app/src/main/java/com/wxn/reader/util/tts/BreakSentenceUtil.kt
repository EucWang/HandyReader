package com.wxn.reader.util.tts

import android.os.Build
import androidx.annotation.RequiresApi
import android.icu.text.BreakIterator as IcuBreakIterator
import java.text.BreakIterator
import java.util.Locale

object BreakSentenceUtil {

    fun breakSentence(paragraph: String, locale: Locale): List<Triple<String, Int, Int>> {
        return when{
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N -> breakSentenceN(paragraph, locale)
            else -> breakSentenceOld(paragraph, locale)
        }
    }

    private fun breakSentenceOld(paragraph: String, locale: Locale): List<Triple<String, Int, Int>> {
        val retList = arrayListOf<Triple<String, Int, Int>>()
        val breakIterator = BreakIterator.getSentenceInstance(locale)
        breakIterator.setText(paragraph)

        var start: Int = breakIterator.first()
        while (true) {
            val end = breakIterator.next()
            if (end == BreakIterator.DONE) {
                break
            }
            retList.add(Triple<String, Int, Int>(
            paragraph.substring(start, end),
                start, end))
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
            retList.add(Triple<String, Int, Int>(
                paragraph.substring(start, end),
                start, end))
            start = end
        }
        return retList
    }
}