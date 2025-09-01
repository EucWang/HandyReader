package com.wxn.mobi

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.base.bean.ReaderText
import com.wxn.mobi.data.model.CountPair
import com.wxn.mobi.data.model.MetaInfo
import com.wxn.mobi.data.model.ParagraphData
import com.wxn.mobi.inative.NativeLib
import com.wxn.mobi.inative.NativeLib.getWordCount
import java.lang.annotation.Native

object EpubParser {

    fun getEpubInfo(context: Context, path: String): MetaInfo? {
        Log.d("EpubParser", "getEpubInfo:path=$path")
        val metaInfo: MetaInfo? = NativeLib.loadEpub(context.applicationContext, path)
        Log.d("EpubParser", "metaInfo = $metaInfo")
        return metaInfo
    }

    fun getEpubChapter(context: Context, bookId: Long, path: String): Array<BookChapter>? {
        Log.d("MobiParser", "getMobiChapter:path=$path")
        val chapters: Array<BookChapter>? = NativeLib.getChapters(context, bookId, path, 2)
        Log.d("MobiParser", "getMobiChapter: = ${chapters?.size}")
        return chapters
    }

    fun getEpubChapterData(context: Context, path: String, chapter: BookChapter): Array<ReaderText>? {
        Log.d("MobiParser", "getMobiChapterData:path=$path,chapter=$chapter")
        val texts: Array<ParagraphData>? = NativeLib.getChapter(context, path, chapter, 2)
        val ret = arrayListOf<ReaderText>()
        if (texts != null) {
            for (text in texts) {
                ret.add(ReaderText.Text(String(text.line), text.tags))
            }
        }
        Log.d("MobiParser", "getMobiChapterData: chapter=${chapter.chapterIndex}: texts.size = ${texts?.size}")
        return ret.toTypedArray()
    }

    fun getEpubCssInfo(context: Context, bookId: Long, cssNames: List<String>?, tagNames: List<String> = emptyList<String>(), ids: List<String> = emptyList<String>()): List<CssInfo>? {
        Log.d("MobiParser", "getMobiCssInfo:bookId=$bookId")
        val names = cssNames ?: return null
        val retVal = NativeLib.getCssInfo(context, bookId, names.toTypedArray(), tagNames.toTypedArray(), ids.toTypedArray(), 2)
        return retVal?.toList().orEmpty()
    }

    fun getEpubWordCount(context: Context, bookId: Long, path: String): List<Triple<Int, Int, Int>> {
        Log.d("MobiParser", "getMobiWordCount:path=$path,bookId=$bookId")
        val retVal: List<CountPair>? = getWordCount(bookId, path, 2)
        if (retVal == null || retVal.isEmpty()) {
            return emptyList()
        }
        return retVal.map {
            it.toTriple()
        }
    }

    fun closeBook(bookId: Long, path: String) {
        NativeLib.closeBook(bookId, path, 2)
    }
}