package com.wxn.mobi

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.mobi.data.model.CountPair
import com.wxn.mobi.data.model.ParagraphData
import com.wxn.mobi.inative.NativeLib

object HtmlParser {

    fun getHtmlChapterData(context: Context, path: String, chapter: BookChapter): Array<ReaderText>? {
        Log.d("HtmlParser", "getMobiChapterData:path=$path,chapter=$chapter")
        val texts: Array<ParagraphData>? = NativeLib.getChapter(context, path, chapter, 4)
        val ret = arrayListOf<ReaderText>()
        if (texts != null) {
            for (text in texts) {
                ret.add(ReaderText.Text(String(text.line), text.tags))
            }
        }

        Log.d("HtmlParser", "getMobiChapterData: chapter=${chapter.chapterIndex}: texts.size = ${texts?.size}")
        return ret.toTypedArray()
    }

    fun getHtmlWordCount(bookId: Long, path: String): List<Triple<Int, Int, Int>> {
        Log.d("HtmlParser", "getHtmlWordCount:path=$path,bookId=$bookId")
        val retVal: List<CountPair>? = NativeLib.getWordCount(bookId, path, 4)
        if (retVal.isNullOrEmpty()) {
            return emptyList()
        }
        return retVal.map { it.toTriple() }
    }

    fun closeBook(bookId: Long, path: String) {
        NativeLib.closeBook(bookId, path, 4)
    }
}