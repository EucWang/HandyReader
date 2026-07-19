package com.wxn.bookparser.parser.epub

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.base.util.Logger
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
        Logger.i("MobiTextParser:bookload:parseChapterInfo:bookId=$bookId")
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Logger.w("MobiTextParser:bookload:parseChapterInfo failed, path is empty")
            return emptyList()
        }
        val retVal = EpubParser.getEpubChapter(context, bookId, path)?.toList() ?: emptyList<BookChapter>()
        Logger.i("MobiTextParser:bookload:parseChapterInfo:bookId=$bookId,done")
        return retVal
    }

    /***
     * 解析得到给定章节数据
     */
    override suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapter: BookChapter): List<ReaderText> {
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Logger.e("MobiTextparser::parsedChapterData failed, path is empty")
            return emptyList()
        }
        val result: Array<ReaderText>? = EpubParser.getEpubChapterData(context, path, chapter)
        if (result == null) {
            return emptyList()
        }
        return result.toList()
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