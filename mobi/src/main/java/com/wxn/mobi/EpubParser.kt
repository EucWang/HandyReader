package com.wxn.mobi

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.base.bean.ReaderText
import com.wxn.mobi.data.model.MetaInfo
import com.wxn.mobi.inative.NativeLib

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
        val texts: Array<ReaderText>? = NativeLib.getChapter(context, path, chapter, 2)
        Log.d("MobiParser", "getMobiChapterData: chapter=${chapter.chapterIndex}: texts.size = ${texts?.size}")
        return texts
    }

    fun getEpubCssInfo(context: Context, bookId: Long, cssNames: List<String>?): List<CssInfo>? {
        Log.d("MobiParser", "getMobiCssInfo:bookId=$bookId")
        val names = cssNames ?: return null
        val retVal = NativeLib.getCssInfo(context, bookId, names.toTypedArray(), 2)
        return retVal?.toList().orEmpty()
    }
}