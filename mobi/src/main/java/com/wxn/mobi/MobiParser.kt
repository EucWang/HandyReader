package com.wxn.mobi

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.base.bean.ReaderText
import com.wxn.mobi.data.model.CountPair
import com.wxn.mobi.data.model.FileCrc
import com.wxn.mobi.data.model.MetaInfo
import com.wxn.mobi.data.model.ParagraphData
import com.wxn.mobi.inative.NativeLib
import com.wxn.mobi.inative.NativeLib.getWordCount

object MobiParser {

    fun getFileCrc(path: String): FileCrc? {
        val fileCrc = NativeLib.nativeFilesCrc(arrayOf(path))?.firstOrNull()
        Log.d("MobiParser", "getFileCrc:path=$path,result crc is ${fileCrc?.crc}")
        return fileCrc
    }

    fun getMobiInfo(context: Context, path: String): MetaInfo? {
        Log.d("MobiParser", "getMobiInfo:path=$path")
        val metaInfo: MetaInfo? = NativeLib.loadMobi(context, path)
        Log.d("MobiParser", "mobiInfo = $metaInfo")
        return metaInfo
    }

    fun getMobiChapter(context: Context, bookId: Long, path: String): Array<BookChapter>? {
        Log.d("MobiParser", "getMobiChapter:path=$path")
        val chapters: Array<BookChapter>? = NativeLib.getChapters(context, bookId, path, 1)
        Log.d("MobiParser", "getMobiChapter: = ${chapters?.size}")
        return chapters
    }

    fun getMobiChapterData(context: Context, path: String, chapter: BookChapter): Array<ReaderText>? {
        Log.d("MobiParser", "getMobiChapterData:path=$path,chapter=$chapter")
        val texts: Array<ParagraphData>? = NativeLib.getChapter(context, path, chapter, 1)
        val ret = arrayListOf<ReaderText>()
        if (texts != null) {
            for (text in texts) {
                ret.add(ReaderText.Text(String(text.line), text.tags))
            }
        }

        Log.d("MobiParser", "getMobiChapterData: chapter=${chapter.chapterIndex}: texts.size = ${texts?.size}")
        return ret.toTypedArray()
    }

    fun getMobiCssInfo(context: Context, bookId: Long, cssNames: List<String>, tagNames : List<String>, ids : List<String>): List<CssInfo>? {
        Log.d("MobiParser", "getMobiCssInfo:bookId=$bookId")
        if (cssNames.isEmpty() && tagNames.isEmpty() && ids.isEmpty()) {
            return emptyList()
        }
        val retVal = NativeLib.getCssInfo(context, bookId, cssNames.toTypedArray(), tagNames.toTypedArray(), ids.toTypedArray(), 1)
        return retVal?.toList().orEmpty()
    }

    fun getMobiWordCount(context: Context, bookId: Long, path: String): List<Triple<Int, Int, Int>> {
        Log.d("MobiParser", "getMobiWordCount:path=$path,bookId=$bookId")
        val retVal: List<CountPair>? = getWordCount(bookId, path, 1)
        if (retVal == null || retVal.isEmpty()) {
            return emptyList()
        }
        return retVal.map {
            it.toTriple()
        }
    }

    fun closeBook(bookId: Long, path: String) {
        NativeLib.closeBook(bookId, path, 1)
    }

//    fun toEpub(context: Context, path: String): String? {
//        Log.d("MobiParser", "toEpub:path=$path")
//        val epubPath: String? = NativeLib.convertToEpub(context, path)
//        Log.d("MobiParser", "toEpub = $epubPath")
//        return epubPath
//    }
}

