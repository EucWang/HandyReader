package com.wxn.mobi

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.ReaderText.Chapter
import com.wxn.base.bean.ReaderText.Image
import com.wxn.mobi.data.model.CountPair
import com.wxn.mobi.data.model.MetaInfo
import com.wxn.mobi.data.model.ParagraphData
import com.wxn.mobi.inative.NativeLib
import com.wxn.mobi.inative.NativeLib.getWordCount
import kotlin.text.isNotEmpty
import kotlin.text.trim

object EpubParser {

    fun getEpubInfo(context: Context, path: String): MetaInfo? {
        Log.d("EpubParser", "getEpubInfo:path=$path")
        val metaInfo: MetaInfo? = NativeLib.loadEpub(context.applicationContext, path)
        Log.d("EpubParser", "metaInfo = $metaInfo")
        return metaInfo
    }

    fun getEpubChapter(context: Context, bookId: Long, path: String): Array<BookChapter>? {
        Log.d("EpubParser", "getMobiChapter:path=$path")
        val chapters: Array<BookChapter>? = NativeLib.getChapters(context, bookId, path, 2)
        Log.d("EpubParser", "getMobiChapter: = ${chapters?.size}")
        return chapters
    }

    fun getEpubChapterData(context: Context, path: String, chapter: BookChapter): Array<ReaderText>? {
        Log.d("EpubParser", "getMobiChapterData:path=$path,chapter=$chapter")
        val texts: Array<ParagraphData>? = NativeLib.getChapter(context, path, chapter, 2)
        val ret = arrayListOf<ReaderText>()
        if (texts != null) {
            for (text in texts) {
                val paragraphText = String(text.line).trim()
                val tags = text.tags
                ret.add(if (paragraphText.isNotEmpty()) {
                    val titleTag = tags.firstOrNull { it.name == "h1" }
                    if (titleTag != null) {
                        Chapter(chapter.chapterIndex.toString(), title = paragraphText, nested = false)
                    } else {
                        ReaderText.Text(paragraphText, tags)
                    }
                } else {
                    val imgTag = tags.firstOrNull { it.name == "img" || it.name == "image" }
                    if (imgTag != null) {
                        var width = tags.firstOrNull { it.name.lowercase() == "width" }?.params?.toIntOrNull() ?: 0
                        var height = tags.firstOrNull { it.name.lowercase() == "height" }?.params?.toIntOrNull() ?: 0
                        val paramItems = imgTag.paramsPairs()
                        var src = ""
                        for (item in paramItems) {
                            when (item.first) {
                                "src" -> { src = item.second.trim() }
                                "width" -> { width = ((item.second.toIntOrNull() ?: 0) * 1.5).toInt() }
                                "height" -> { height = ((item.second.toIntOrNull() ?: 0) * 1.5).toInt() }
                            }
                        }
                        if (src.isNotEmpty()) {
                            Image(src, width, height)
                        } else {
                            ReaderText.Text(paragraphText, tags)
                        }
                    } else {
                        ReaderText.Text(paragraphText, tags)
                    }
                })
            }
        }
        Log.d("EpubParser", "getMobiChapterData: chapter=${chapter.chapterIndex}: texts.size = ${texts?.size}")
        return ret.toTypedArray()
    }

    fun getEpubCssInfo(context: Context, bookId: Long, cssNames: List<String>?, tagNames: List<String> = emptyList<String>(), ids: List<String> = emptyList<String>()): List<CssInfo>? {
        Log.d("EpubParser", "getMobiCssInfo:bookId=$bookId")
        val names = cssNames ?: return null
        val retVal = NativeLib.getCssInfo(context, bookId, names.toTypedArray(), tagNames.toTypedArray(), ids.toTypedArray(), 2)
        return retVal?.toList().orEmpty()
    }

    fun getEpubWordCount(context: Context, bookId: Long, path: String): List<Triple<Int, Int, Int>> {
        Log.d("EpubParser", "getMobiWordCount:path=$path,bookId=$bookId")
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