package com.wxn.bookparser.parser.html

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.base.bean.ReaderText
import com.wxn.bookparser.parser.base.DocumentParser
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
        Log.i(HTML_TAG, "Started HTML parsing: ${cachedFile.name}.")

        return try {
            val readerText = cachedFile.openInputStream()?.use { stream ->
                documentParser.parseDocument(context, bookId, Jsoup.parse(stream, null, "", Parser.htmlParser()))
            }

            yield()

            if (
                readerText.isNullOrEmpty() ||
                readerText.filterIsInstance<ReaderText.Text>().isEmpty() ||
                readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
            ) {
                Log.e(HTML_TAG, "Could not extract text from HTML.")
                return emptyList()
            }

            Log.i(HTML_TAG, "Successfully finished HTML parsing.")
            readerText
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }



    /***
     * 解析得到章节列表
     */
    override suspend fun parseChapterInfo(bookId: Long, cachedFile: CachedFile): List<BookChapter> {
        return emptyList()
    }

    /***
     * 解析得到给定章节数据
     */
    override suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapter: BookChapter) : List<ReaderText> {
        return if (chapter.chapterIndex == 0) {
            parse(bookId, cachedFile)
        } else {
            emptyList()
        }
    }

    override suspend fun parseCss(bookId: Long,cachedFile: CachedFile,  cssNames: List<String>, tagNames: List<String>, ids: List<String>): List<CssInfo> {
        return emptyList()
    }

    override suspend fun getWordCount(bookId:Long, cachedFile: CachedFile):  List<Triple<Int, Int, Int>> {
        return emptyList()
    }

    override suspend fun close(bookId:Long, cachedFile: CachedFile) {

    }
}