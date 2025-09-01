package com.wxn.bookparser.parser.epub

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.base.bean.ReaderText
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.mobi.EpubParser
import javax.inject.Inject


class EpubTextParser @Inject constructor(
    private val context: Context
) : TextParser {
    /***
     * 解析得到章节列表
     */
    override suspend fun parseChapterInfo(bookId: Long, cachedFile: CachedFile): List<BookChapter> {
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("MobiTextParser", ":parseChapterInfo failed, path is empty")
            return emptyList()
        }
        val retVal = EpubParser.getEpubChapter(context, bookId, path)?.toList() ?: emptyList<BookChapter>()
        return retVal
    }

    /***
     * 解析得到给定章节数据
     */
    override suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapter: BookChapter): List<ReaderText> {
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("MobiTextparser", "parsedChapterData failed, path is empty")
            return emptyList()
        }
        val result: Array<ReaderText>? = EpubParser.getEpubChapterData(context, path, chapter)
        if (result == null) {
            return emptyList()
        }
        return result.toList()
    }

    override suspend fun parseCss(bookId: Long, cachedFile: CachedFile, cssNames: List<String>, tagNames: List<String>, ids: List<String>): List<CssInfo> {
        return EpubParser.getEpubCssInfo(context, bookId, cssNames, tagNames, ids).orEmpty()
    }

    override suspend fun getWordCount(bookId: Long, cachedFile: CachedFile): List<Triple<Int, Int, Int>> {
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("MobiTextparser", "parsedChapterData failed, path is empty")
            return emptyList()
        }
        return EpubParser.getEpubWordCount(context, bookId, path)
    }

    override suspend fun close(bookId:Long, cachedFile: CachedFile) {
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("MobiTextparser", "parsedChapterData failed, path is empty")
            return
        }
        EpubParser.closeBook(bookId, path)
    }
}