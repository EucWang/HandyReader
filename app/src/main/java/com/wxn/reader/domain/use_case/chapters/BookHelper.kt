package com.wxn.reader.domain.use_case.chapters

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.spreada.utils.chinese.ZHConverter
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.base.util.Logger
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFileCompat
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.reader.data.source.local.AppPreferencesUtil

object BookHelper {

    /***
     * 从文件中解析得到章节信息
     */
    suspend fun getChapters(context: Context, bookId: Long, bookUri: String?, textParser: TextParser): List<BookChapter> {
        bookUri?.let { uri ->
            val cachedFile = CachedFileCompat.fromUri(context, uri.toUri())
            val chapters = textParser.parseChapterInfo(bookId, cachedFile)
            chapters.forEach { chapter ->
                chapter.bookId = bookId
            }
            return chapters
        }
        return emptyList()
    }

    suspend fun loadChapterContent(context: Context, book: Book, chapter: BookChapter, textparser: TextParser): List<ReaderText> {
        val encodedUri =  book.filePath.toUri()
        val cachedFile = CachedFileCompat.fromUri(context, encodedUri)
        val bookId = book.id
        val chapterIndex = chapter.chapterIndex
        return textparser.parsedChapterData(bookId, cachedFile, chapterIndex)
    }

    suspend fun disposeContent(
        appPreferencesUtil: AppPreferencesUtil,
        chapter: BookChapter,
        contents: List<ReaderText>
    ): List<ReaderText> {
        val chineseConverterType = appPreferencesUtil.chineseConverterType()
        //得到简繁体对应的章节名称
        chapter.chapterName = when (chineseConverterType) {
            1 -> ZHConverter.getInstance(ZHConverter.SIMPLIFIED).convert(chapter.chapterName)
            2 -> ZHConverter.getInstance(ZHConverter.TRADITIONAL).convert(chapter.chapterName)
            else -> chapter.chapterName
        }
        var title1: String = chapter.chapterName
        var content1: List<ReaderText> = contents
        try {
            when (chineseConverterType) {
                1 -> {
                    ZHConverter.getInstance(ZHConverter.SIMPLIFIED)
                }

                2 -> {
                    ZHConverter.getInstance(ZHConverter.TRADITIONAL)
                }

                else -> {
                    null
                }
            }?.let { converter ->
                title1 = converter.convert(title1)
                content1.forEach { content ->
                    if (content is ReaderText.Text) {
                        content.line = converter.convert(content.line)
                    } else if (content is ReaderText.Chapter) {
                        content.title = converter.convert(content.title)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(e)
        }
        content1.forEach { content ->
            if (content is ReaderText.Text) {
                val line = content.line.replace("^[\\n\\s\\r]+".toRegex(), "")
                content.line = "${ChapterProvider.paragraphIndent}$line"
            }
        }
        return content1
    }
}