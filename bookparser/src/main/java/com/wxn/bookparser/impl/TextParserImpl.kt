package com.wxn.bookparser.impl

import android.util.Log
import com.wxn.base.util.Logger
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.reader.ReaderText
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

    override suspend fun parse(cachedFile: CachedFile): List<ReaderText> {
        if (!cachedFile.canAccess()) {
            Log.e(TEXT_PARSER, "File does not exist or no read access is granted.")
            return emptyList()
        }

        val fileFormat = cachedFile.extension
        Logger.d("TextParserImpl:parse:fileFormat=$fileFormat")
        return withContext(Dispatchers.IO) {
            when (fileFormat) {
                "pdf" -> {
                    pdfTextParser.parse(cachedFile)
                }

                "epub" -> {
                    epubTextParser.parse(cachedFile)
                }

                in listOf("mobi", "azw3") -> {
                    mobiTextParser.parse(cachedFile)
                }

                "txt" -> {
                    txtTextParser.parse(cachedFile)
                }

                "fb2" -> {
                    xmlTextParser.parse(cachedFile)
                }

                "html" -> {
                    htmlTextParser.parse(cachedFile)
                }

                "htm" -> {
                    htmlTextParser.parse(cachedFile)
                }

                "md" -> {
                    htmlTextParser.parse(cachedFile)
                }

                else -> {
                    Log.e(TEXT_PARSER, "Wrong file format, could not find supported extension.")
                    emptyList()
                }
            }
        }
    }
}