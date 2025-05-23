package com.wxn.bookparser.impl

import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.base.util.Logger
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.parser.epub.EpubTextParser
import com.wxn.bookparser.parser.html.HtmlTextParser
import com.wxn.bookparser.parser.mobi.MobiTextParser
import com.wxn.bookparser.parser.pdf.PdfTextParser
import com.wxn.bookparser.parser.txt.TxtTextParser
import com.wxn.bookparser.parser.xml.XmlTextParser
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
    private val xmlTextParser: XmlTextParser,
    private val mobiTextParser: MobiTextParser
) : TextParser {

//    override suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapterIndex: Int): List<ReaderText> {
//        if (!cachedFile.canAccess()) {
//            Log.e(TEXT_PARSER, "File does not exist or no read access is granted.")
//            return emptyList()
//        }
//
//        val fileFormat = cachedFile.extension
//        Logger.d("TextParserImpl:parse:fileFormat=$fileFormat")
//        return withContext(Dispatchers.IO) {
//            when (fileFormat) {
//                "pdf" -> {
//                    pdfTextParser.parse(bookId, cachedFile)
//                }
//
//                "epub" -> {
//                    epubTextParser.parse(bookId, cachedFile)
//                }
//
//                in listOf("mobi", "azw3") -> {
//                    mobiTextParser.parse(bookId, cachedFile)
//                }
//
//                "txt" -> {
//                    txtTextParser.parse(bookId, cachedFile)
//                }
//
//                "fb2" -> {
//                    xmlTextParser.parse(bookId, cachedFile)
//                }
//
//                "html" -> {
//                    htmlTextParser.parse(bookId, cachedFile)
//                }
//
//                "htm" -> {
//                    htmlTextParser.parse(bookId, cachedFile)
//                }
//
//                "md" -> {
//                    htmlTextParser.parse(bookId, cachedFile)
//                }
//
//                else -> {
//                    Log.e(TEXT_PARSER, "Wrong file format, could not find supported extension.")
//                    emptyList()
//                }
//            }
//        }
//    }


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
                    xmlTextParser.parseChapterInfo(bookId, cachedFile)
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
    override suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapterIndex: Int): List<ReaderText> {
        if (!cachedFile.canAccess()) {
            Log.e(TEXT_PARSER, "File does not exist or no read access is granted.")
            return emptyList()
        }

        val fileFormat = cachedFile.extension
        Logger.d("TextParserImpl:parse:fileFormat=$fileFormat")
        return withContext(Dispatchers.IO) {
            when (fileFormat) {
                "pdf" -> {
                    pdfTextParser.parsedChapterData(bookId, cachedFile, chapterIndex)
                }

                "epub" -> {
                    epubTextParser.parsedChapterData(bookId, cachedFile, chapterIndex)
                }

                in listOf("mobi", "azw3") -> {
                    mobiTextParser.parsedChapterData(bookId, cachedFile, chapterIndex)
                }

                "txt" -> {
                    txtTextParser.parsedChapterData(bookId, cachedFile, chapterIndex)
                }

                "fb2" -> {
                    xmlTextParser.parsedChapterData(bookId, cachedFile, chapterIndex)
                }

                "html" -> {
                    htmlTextParser.parsedChapterData(bookId, cachedFile, chapterIndex)
                }

                "htm" -> {
                    htmlTextParser.parsedChapterData(bookId, cachedFile, chapterIndex)
                }

                "md" -> {
                    htmlTextParser.parsedChapterData(bookId, cachedFile, chapterIndex)
                }

                else -> {
                    Log.e(TEXT_PARSER, "Wrong file format, could not find supported extension.")
                    emptyList()
                }
            }
        }
    }
}