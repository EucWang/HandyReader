package com.wxn.bookparser.impl

import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.base.bean.ReaderText
import com.wxn.base.util.Logger
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.parser.epub.EpubTextParser
import com.wxn.bookparser.parser.html.HtmlTextParser
import com.wxn.bookparser.parser.mobi.MobiTextParser
import com.wxn.bookparser.parser.pdf.PdfTextParser
import com.wxn.bookparser.parser.txt.TxtTextParser
import com.wxn.bookparser.parser.fb2.Fb2TextParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TEXT_PARSER = "Text Parser"

class TextParserImpl @Inject constructor(

    // Markdown parser (Markdown)
    private val txtTextParser: TxtTextParser,
    private val pdfTextParser: PdfTextParser,

    // Document parser (HTML+Markdown)
    private val epubTextParser: EpubTextParser,
    private val htmlTextParser: HtmlTextParser,
    private val fb2TextParser: Fb2TextParser,
    private val mobiTextParser: MobiTextParser
) : TextParser {

    /***
     * 解析得到章节列表
     */
    override suspend fun parseChapterInfo(bookId: Long, cachedFile: CachedFile): List<BookChapter> {
        if (!cachedFile.canAccess()) {
            Log.e(TEXT_PARSER, "File does not exist or no read access is granted.")
            return emptyList()
        }

        val fileFormat = cachedFile.extension
        Logger.d("TextParserImpl:parse:fileFormat=$fileFormat")
        return withContext(Dispatchers.IO) {
            when (fileFormat) {
                "pdf" -> {
                    pdfTextParser.parseChapterInfo(bookId, cachedFile)
                }

                "epub" -> {
                    epubTextParser.parseChapterInfo(bookId, cachedFile)
                }

                in listOf("mobi", "azw3") -> {
                    mobiTextParser.parseChapterInfo(bookId, cachedFile)
                }

                "txt" -> {
                    txtTextParser.parseChapterInfo(bookId, cachedFile)
                }

                "fb2" -> {
                    fb2TextParser.parseChapterInfo(bookId, cachedFile)
                }

                "html" -> {
                    htmlTextParser.parseChapterInfo(bookId, cachedFile)
                }

                "htm" -> {
                    htmlTextParser.parseChapterInfo(bookId, cachedFile)
                }

                "md" -> {
                    htmlTextParser.parseChapterInfo(bookId, cachedFile)
                }

                else -> {
                    Log.e(TEXT_PARSER, "Wrong file format, could not find supported extension.")
                    emptyList()
                }
            }
        }
    }

    /***
     * 解析得到给定章节数据
     */
    override suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapter: BookChapter): List<ReaderText> {
        if (!cachedFile.canAccess()) {
            Log.e(TEXT_PARSER, "File does not exist or no read access is granted.")
            return emptyList()
        }

        val fileFormat = cachedFile.extension
        Logger.d("TextParserImpl:parse:fileFormat=$fileFormat")
        return withContext(Dispatchers.IO) {
            when (fileFormat) {
                "pdf" -> {
                    pdfTextParser.parsedChapterData(bookId, cachedFile, chapter)
                }

                "epub" -> {
                    epubTextParser.parsedChapterData(bookId, cachedFile, chapter)
                }

                in listOf("mobi", "azw3") -> {
                    mobiTextParser.parsedChapterData(bookId, cachedFile, chapter)
                }

                "txt" -> {
                    txtTextParser.parsedChapterData(bookId, cachedFile, chapter)
                }

                "fb2" -> {
                    fb2TextParser.parsedChapterData(bookId, cachedFile, chapter)
                }

                "html" -> {
                    htmlTextParser.parsedChapterData(bookId, cachedFile, chapter)
                }

                "htm" -> {
                    htmlTextParser.parsedChapterData(bookId, cachedFile, chapter)
                }

                "md" -> {
                    htmlTextParser.parsedChapterData(bookId, cachedFile, chapter)
                }

                else -> {
                    Log.e(TEXT_PARSER, "Wrong file format, could not find supported extension.")
                    emptyList()
                }
            }
        }
    }

    override suspend fun parseCss(bookId: Long, cachedFile: CachedFile, cssNames: List<String>, tagNames: List<String>, ids: List<String>): List<CssInfo> {
        if (!cachedFile.canAccess()) {
            Log.e(TEXT_PARSER, "File does not exist or no read access is granted.")
            return emptyList()
        }

        val fileFormat = cachedFile.extension
        Logger.d("TextParserImpl:parse:fileFormat=$fileFormat")
        return withContext(Dispatchers.IO) {
            when (fileFormat) {
                "pdf" -> {
                    pdfTextParser.parseCss(bookId, cachedFile, cssNames, tagNames, ids)
                }

                "epub" -> {
                    epubTextParser.parseCss(bookId, cachedFile, cssNames, tagNames, ids)
                }

                in listOf("mobi", "azw3") -> {
                    mobiTextParser.parseCss(bookId, cachedFile, cssNames, tagNames, ids)
                }

                "txt" -> {
                    txtTextParser.parseCss(bookId, cachedFile, cssNames, tagNames, ids)
                }

                "fb2" -> {
                    fb2TextParser.parseCss(bookId, cachedFile, cssNames, tagNames, ids)
                }

                "html" -> {
                    htmlTextParser.parseCss(bookId, cachedFile, cssNames, tagNames, ids)
                }

                "htm" -> {
                    htmlTextParser.parseCss(bookId, cachedFile, cssNames, tagNames, ids)
                }

                "md" -> {
                    htmlTextParser.parseCss(bookId, cachedFile, cssNames, tagNames, ids)
                }

                else -> {
                    Log.e(TEXT_PARSER, "Wrong file format, could not find supported extension.")
                    emptyList()
                }
            }
        }
    }

    override suspend fun getWordCount(bookId:Long, cachedFile: CachedFile): List<Triple<Int, Int, Int>> {
        if (!cachedFile.canAccess()) {
            Log.e(TEXT_PARSER, "File does not exist or no read access is granted.")
            return emptyList()
        }

        val fileFormat = cachedFile.extension
        Logger.d("TextParserImpl:parse:fileFormat=$fileFormat")
        return withContext(Dispatchers.IO) {
            when (fileFormat) {
                "pdf" -> {
                    pdfTextParser.getWordCount(bookId, cachedFile)
                }

                "epub" -> {
                    epubTextParser.getWordCount(bookId, cachedFile)
                }

                in listOf("mobi", "azw3") -> {
                    mobiTextParser.getWordCount(bookId, cachedFile)
                }

                "txt" -> {
                    txtTextParser.getWordCount(bookId, cachedFile)
                }

                "fb2" -> {
                    fb2TextParser.getWordCount(bookId, cachedFile)
                }

                "html" -> {
                    htmlTextParser.getWordCount(bookId, cachedFile)
                }

                "htm" -> {
                    htmlTextParser.getWordCount(bookId, cachedFile)
                }

                "md" -> {
                    htmlTextParser.getWordCount(bookId, cachedFile)
                }

                else -> {
                    Log.e(TEXT_PARSER, "Wrong file format, could not find supported extension.")
                    emptyList()
                }
            }
        }
    }

    override suspend fun close(bookId:Long, cachedFile: CachedFile) {
        if (!cachedFile.canAccess()) {
            Log.e(TEXT_PARSER, "File does not exist or no read access is granted.")
            return
        }

        val fileFormat = cachedFile.extension
        Logger.d("TextParserImpl:parse:fileFormat=$fileFormat")
        return withContext(Dispatchers.IO) {
            when (fileFormat) {
                "pdf" -> {
                    pdfTextParser.close(bookId, cachedFile)
                }

                "epub" -> {
                    epubTextParser.close(bookId, cachedFile)
                }

                in listOf("mobi", "azw3") -> {
                    mobiTextParser.close(bookId, cachedFile)
                }

                "txt" -> {
                    txtTextParser.close(bookId, cachedFile)
                }

                "fb2" -> {
                    fb2TextParser.close(bookId, cachedFile)
                }

                "html" -> {
                    htmlTextParser.close(bookId, cachedFile)
                }

                "htm" -> {
                    htmlTextParser.close(bookId, cachedFile)
                }

                "md" -> {
                    htmlTextParser.close(bookId, cachedFile)
                }

                else -> {
                    Log.e(TEXT_PARSER, "Wrong file format, could not find supported extension.")
                }
            }
        }
    }
}