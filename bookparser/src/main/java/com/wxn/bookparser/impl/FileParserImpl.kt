package com.wxn.bookparser.impl

import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.extension
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.domain.book.BookWithCover
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.parser.epub.EpubFileParser
import com.wxn.bookparser.parser.fb2.Fb2FileParser
import com.wxn.bookparser.parser.html.HtmlFileParser
import com.wxn.bookparser.parser.pdf.PdfFileParser
import com.wxn.bookparser.parser.txt.TxtFileParser
import javax.inject.Inject

private const val FILE_PARSER = "File Parser"

class FileParserImpl @Inject constructor(
    private val txtFileParser: TxtFileParser,
    private val pdfFileParser: PdfFileParser,
    private val epubFileParser: EpubFileParser,
    private val fb2FileParser: Fb2FileParser,
    private val htmlFileParser: HtmlFileParser,
) : FileParser {

    override suspend fun parse(file: DocumentFile): BookWithCover? {

        if (!file.isFile || !file.canRead()) {
            Log.e(FILE_PARSER, "File does not exist or no read access is granted.")
            return null
        }

        val fileFormat = file.extension
        return when (fileFormat) {
            "pdf" -> {
                pdfFileParser.parse(file)
            }

            "epub" -> {
                epubFileParser.parse(file)
            }

            "txt" -> {
                txtFileParser.parse(file)
            }

            "fb2" -> {
                fb2FileParser.parse(file)
            }

            "html" -> {
                htmlFileParser.parse(file)
            }

            "htm" -> {
                htmlFileParser.parse(file)
            }

            "md" -> {
                txtFileParser.parse(file)
            }

            else -> {
                Log.e(FILE_PARSER, "Wrong file format, could not find supported extension.")
                null
            }
        }
    }

    override suspend fun parse(cachedFile: CachedFile): BookWithCover? {
        if (!cachedFile.canAccess()) {
            Log.e(FILE_PARSER, "File does not exist or no read access is granted.")
            return null
        }

        val fileFormat = ".${cachedFile.name.substringAfterLast(".")}".lowercase().trim()
        return when (fileFormat) {
            ".pdf" -> {
                pdfFileParser.parse(cachedFile)
            }

            ".epub" -> {
                epubFileParser.parse(cachedFile)
            }

            ".txt" -> {
                txtFileParser.parse(cachedFile)
            }

            ".fb2" -> {
                fb2FileParser.parse(cachedFile)
            }

            ".html" -> {
                htmlFileParser.parse(cachedFile)
            }

            ".htm" -> {
                htmlFileParser.parse(cachedFile)
            }

            ".md" -> {
                txtFileParser.parse(cachedFile)
            }

            else -> {
                Log.e(FILE_PARSER, "Wrong file format, could not find supported extension.")
                null
            }
        }
    }
}