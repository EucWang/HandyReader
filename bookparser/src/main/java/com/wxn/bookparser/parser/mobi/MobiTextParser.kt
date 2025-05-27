package com.wxn.bookparser.parser.mobi

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.wxn.base.bean.BookChapter
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.base.bean.ReaderText
import com.wxn.bookparser.exts.addAll
import com.wxn.bookparser.exts.containsVisibleText
import com.wxn.bookparser.parser.base.DocumentParser
import com.wxn.bookparser.provideImageExtensions
import com.wxn.mobi.MobiParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jsoup.Jsoup
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
private val innerDispatcher = Dispatchers.IO.limitedParallelism(3)

class MobiTextParser @Inject constructor(
    val context: Context,
    private val documentParser: DocumentParser
) : TextParser  {

    /***
     * 解析得到章节列表
     */
    override suspend fun parseChapterInfo(bookId: Long, cachedFile: CachedFile): List<BookChapter> {
        val path  = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("MobiTextParser", ":parseChapterInfo failed, path is empty")
            return emptyList()
        }
        val retVal = MobiParser.getMobiChapter(context, bookId, path)?.toList() ?: emptyList<BookChapter>()
        return retVal
    }

    /***
     * 解析得到给定章节数据
     */
    override suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapter: BookChapter) : List<ReaderText> {
        val path = cachedFile.rawFile?.absolutePath
        if (path.isNullOrEmpty()) {
            Log.e("MobiTextparser", "parsedChapterData failed, path is empty")
            return emptyList()
        }
        val result : Array<ReaderText>? = MobiParser.getMobiChapterData(context, path, chapter)
        if (result == null) {
            return emptyList()
        }
        return result.toList()
    }

    companion object {
        val MOBI_TAG = "MobiTextParser"

        fun release() {
            MobiParser.clear()
        }

    }
}