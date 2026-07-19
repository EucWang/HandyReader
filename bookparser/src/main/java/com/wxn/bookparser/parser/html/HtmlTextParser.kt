package com.wxn.bookparser.parser.html

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.base.bean.ReaderText
import com.wxn.bookparser.parser.base.DocumentParser
import com.wxn.mobi.Fb2Parser
import com.wxn.mobi.HtmlParser
import kotlinx.coroutines.yield
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import javax.inject.Inject

private const val HTML_TAG = "HTML Parser"

class HtmlTextParser @Inject constructor(
    private val context : Context,
    private val documentParser: DocumentParser
) : TextParser {

    suspend fun parse(bookId: Long, cachedFile: CachedFile): List<ReaderText> {
        Log.i(HTML_TAG, "Started HTML parsing: ${cachedFile.name}.") //TODO
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("Fb2TextParser", "parsedChapterData failed, path is empty")
            return emptyList()
        }
        val result : Array<ReaderText>? = HtmlParser.getHtmlChapterData(context, path, BookChapter(bookId = bookId, chapterIndex = 0, chapterName = ""))
        if (result == null) {
            return emptyList()
        }
        return result.toList()
    }


    /***
     * 解析得到章节列表
     */
    override suspend fun parseChapterInfo(bookId: Long, cachedFile: CachedFile): List<BookChapter> {
        return listOf(
            BookChapter(0,  bookId = bookId, chapterIndex = 0, chapterName = "")
        )
    }

    /***
     * 解析得到给定章节数据
     */
    override suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapter: BookChapter) : List<ReaderText> {
//        return if (chapter.chapterIndex == 0) {
//            parse(bookId, cachedFile)
//        } else {
//            emptyList()
//        }

        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("Fb2TextParser", "parsedChapterData failed, path is empty")
            return emptyList()
        }
        val result : Array<ReaderText>? = HtmlParser.getHtmlChapterData(context, path, chapter)
        if (result == null) {
            return emptyList()
        }
        return result.toList()
    }

    override suspend fun getWordCount(bookId: Long, cachedFile: CachedFile): List<Triple<Int, Int, Int>> {
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e(HTML_TAG, "getWordCount failed, path is empty")
            return emptyList()
        }
        return HtmlParser.getHtmlWordCount(bookId, path)
    }

    override suspend fun close(bookId: Long, cachedFile: CachedFile) {
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e(HTML_TAG, "close failed, path is empty")
            return
        }
        HtmlParser.closeBook(bookId, path)
    }
}