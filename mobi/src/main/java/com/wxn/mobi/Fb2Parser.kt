package com.wxn.mobi

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.mobi.data.model.CountPair
import com.wxn.mobi.data.model.MetaInfo
import com.wxn.mobi.data.model.ParagraphData
import com.wxn.mobi.inative.NativeLib
import com.wxn.mobi.inative.NativeLib.getWordCount

object Fb2Parser {

    fun getFb2Info(context: Context, path: String) : MetaInfo? {
        val metaInfo: MetaInfo? = NativeLib.loadFb2(context, path)
        Log.d("Fb2Parser", "getFb2Info: $metaInfo")
        return metaInfo
    }

    fun getFb2Chapter(context: Context, bookId: Long, path: String) : Array<BookChapter>? {
        Log.d("Fb2Parser", "getFb2Chapter:path=$path")
        val chapters: Array<BookChapter>? = NativeLib.getChapters(context, bookId, path, 3)
        Log.d("Fb2Parser", "getFb2Chapter: = ${chapters?.size}")
        return chapters
    }

    fun getFb2ChapterData(context: Context, path: String, chapter: BookChapter): Array<ReaderText>? {
        Log.d("Fb2Parser", "getMobiChapterData:path=$path,chapter=$chapter")
        val texts: Array<ParagraphData>? = NativeLib.getChapter(context, path, chapter, 3)
        val ret = arrayListOf<ReaderText>()
        if (texts != null) {
            for (text in texts) {
                ret.add(ReaderText.Text(String(text.line), text.tags))
            }
        }

        Log.d("Fb2Parser", "getMobiChapterData: chapter=${chapter.chapterIndex}: texts.size = ${texts?.size}")
        return ret.toTypedArray()
    }

    fun getFb2WordCount(context : Context, bookId: Long, path: String) : List<Triple<Int, Int, Int>> {
        Log.d("MobiParser", "getMobiWordCount:path=$path,bookId=$bookId")
        val retVal: List<CountPair>? = getWordCount(bookId, path, 3)
        if (retVal == null || retVal.isEmpty()) {
            return emptyList()
        }
        return retVal.map {
            it.toTriple()
        }
    }

    fun closeBook(bookId: Long, path: String) {
        NativeLib.closeBook(bookId, path, 3)
    }

}