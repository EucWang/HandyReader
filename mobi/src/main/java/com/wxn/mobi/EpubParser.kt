package com.wxn.mobi

import android.content.Context
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.ReaderText.Chapter
import com.wxn.base.bean.ReaderText.Image
import com.wxn.base.util.Logger
import com.wxn.mobi.data.model.CountPair
import com.wxn.mobi.data.model.MetaInfo
import com.wxn.mobi.data.model.ParagraphData
import com.wxn.mobi.inative.NativeLib
import com.wxn.mobi.inative.NativeLib.getWordCount

object EpubParser {

    fun getEpubInfo(context: Context, path: String): MetaInfo? {
        Logger.d("EpubParser::getEpubInfo:path=$path")
        val metaInfo: MetaInfo? = NativeLib.loadEpub(context.applicationContext, path)
        Logger.d("EpubParser::metaInfo = $metaInfo")
        return metaInfo
    }

    fun getEpubChapter(context: Context, bookId: Long, path: String): Array<BookChapter>? {
        Logger.d("EpubParser:bookload:getMobiChapter:path=$path")
        val chapters: Array<BookChapter>? = NativeLib.getChapters(context, bookId, path, 2)
        Logger.d("EpubParser:bookload:getMobiChapter: = ${chapters?.size}")
        return chapters
    }

    fun getEpubChapterData(
        context: Context,
        path: String,
        chapter: BookChapter
    ): Array<ReaderText>? {
        Logger.d("EpubParser::getMobiChapterData:path=$path,chapter=$chapter")
        val texts: Array<ParagraphData>? = NativeLib.getChapter(context, path, chapter, 2)
        val ret = arrayListOf<ReaderText>()
        if (texts != null) {
            for (text in texts) {
                val paragraphText = String(text.line).trim()
                val tags = text.tags
                ret.add(
                    if (paragraphText.isNotEmpty()) {
                    val titleTag = tags.firstOrNull { it.name == "h1" }
                    if (titleTag != null) {
                        Chapter(
                            chapter.chapterIndex.toString(),
                            title = paragraphText,
                            nested = false
                        )
                    } else {
                        ReaderText.Text(paragraphText, tags)
                    }
                } else {
                    val imgTag = tags.firstOrNull { it.name == "img" || it.name == "image" }
                    if (imgTag != null) {
                        var width =
                            tags.firstOrNull { it.name.lowercase() == "width" }?.params?.toIntOrNull()
                                ?: 0
                        var height =
                            tags.firstOrNull { it.name.lowercase() == "height" }?.params?.toIntOrNull()
                                ?: 0
                        val paramItems = imgTag.paramsPairs()
                        var src = ""
                        for (item in paramItems) {
                            when (item.first) {
                                "src" -> {
                                    src = item.second.trim()
                                }

                                "width" -> {
                                    width = ((item.second.toIntOrNull() ?: 0) * 1.5).toInt()
                                }

                                "height" -> {
                                    height = ((item.second.toIntOrNull() ?: 0) * 1.5).toInt()
                                }
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
                }
                )
            }
        }
        Logger.d("EpubParser::getMobiChapterData: chapter=${chapter.chapterIndex}: texts.size = ${texts?.size}")
        return ret.toTypedArray()
    }

    fun getEpubWordCount(
        context: Context,
        bookId: Long,
        path: String
    ): List<Triple<Int, Int, Int>> {
        Logger.d("EpubParser::getMobiWordCount:path=$path,bookId=$bookId")
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