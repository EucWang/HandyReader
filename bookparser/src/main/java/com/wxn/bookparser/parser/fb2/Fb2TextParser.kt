package com.wxn.bookparser.parser.fb2

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.base.bean.ReaderText
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.mobi.Fb2Parser
import javax.inject.Inject

class Fb2TextParser @Inject constructor(private val context: Context) : TextParser {

    override suspend fun parseChapterInfo(
        bookId: Long,
        cachedFile: CachedFile
    ): List<BookChapter> {
        val path  = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("Fb2TextParser", ":parseChapterInfo failed, path is empty")
            return emptyList()
        }
        val retVal = Fb2Parser.getFb2Chapter(context, bookId, path)?.toList() ?: emptyList<BookChapter>()
        return retVal
    }

    override suspend fun parsedChapterData(
        bookId: Long,
        cachedFile: CachedFile,
        chapter: BookChapter
    ): List<ReaderText> {
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("Fb2TextParser", "parsedChapterData failed, path is empty")
            return emptyList()
        }
        val result : Array<ReaderText>? = Fb2Parser.getFb2ChapterData(context, path, chapter)
        if (result == null) {
            return emptyList()
        }
        return result.toList()
    }

    override suspend fun parseCss(
        bookId: Long,
        cachedFile: CachedFile,
        cssNames: List<String>,
        tagNames: List<String>,
        ids: List<String>
    ): List<CssInfo> {
        return emptyList()
    }

    override suspend fun getWordCount(
        bookId: Long,
        cachedFile: CachedFile
    ): List<Triple<Int, Int, Int>> {
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("Fb2TextParser", "getWordCount failed, path is empty")
            return emptyList()
        }
        return Fb2Parser.getFb2WordCount(context, bookId, path)
    }

    override suspend fun close(bookId: Long, cachedFile: CachedFile) {
        val path  = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("Fb2TextParser", ":close failed, path is empty")
            return
        }
        Fb2Parser.closeBook(bookId, path)
    }
}