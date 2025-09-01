package com.wxn.bookparser.impl

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.extension
import com.wxn.base.bean.Book
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.exts.rawFile
import com.wxn.bookparser.parser.audio.AudioFileParser
import com.wxn.bookparser.parser.epub.EpubFileParser
import com.wxn.bookparser.parser.fb2.Fb2FileParser
import com.wxn.bookparser.parser.html.HtmlFileParser
import com.wxn.bookparser.parser.mobi.MobiFileParser
import com.wxn.bookparser.parser.pdf.PdfFileParser
import com.wxn.bookparser.parser.txt.TxtFileParser
import com.wxn.mobi.MobiParser
import javax.inject.Inject

private const val FILE_PARSER = "File Parser"

class FileParserImpl @Inject constructor(
    private val context: Context,
    private val txtFileParser: TxtFileParser,
    private val pdfFileParser: PdfFileParser,
    private val epubFileParser: EpubFileParser,
    private val fb2FileParser: Fb2FileParser,
    private val htmlFileParser: HtmlFileParser,
    private val audioFileParser: AudioFileParser,
    private val mobiFileParser: MobiFileParser,
) : FileParser {

    override suspend fun parse(file: DocumentFile): Book? {
        if (!file.isFile || !file.canRead()) {
            Log.e(FILE_PARSER, "File does not exist or no read access is granted.")
            return null
        }

        val fileFormat = file.extension
        var book = when (fileFormat) {
            "pdf" -> {
                pdfFileParser.parse(file)
            }

            "epub" -> {
                epubFileParser.parse(file)
            }

            in listOf("mobi", "azw3") -> {
                mobiFileParser.parse(file)
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

            in listOf(
                "mp3",
                "m4a",
                "m4b",
                "aac"
            ) -> {
                audioFileParser.parse(file)
            }

            else -> {
                Log.e(FILE_PARSER, "Wrong file format, could not find supported extension.")
                null
            }
        }

        file.rawFile(context)?.absoluteFile?.absolutePath?.let { filePath ->
            val crc = MobiParser.getFileCrc(filePath)
            book?.crc = crc?.crc ?: 0
        }
        return book
    }

    override suspend fun parse(cachedFile: CachedFile): Book? {
        if (!cachedFile.canAccess()) {
            Log.e(FILE_PARSER, "File does not exist or no read access is granted.")
            return null
        }

        val book = when (cachedFile.extension) {
            "pdf" -> {
                pdfFileParser.parse(cachedFile)
            }
            "epub" -> {
                epubFileParser.parse(cachedFile)
            }
            in listOf("mobi", "azw3") -> {
                mobiFileParser.parse(cachedFile)
            }
            "txt" -> {
                txtFileParser.parse(cachedFile)
            }
            "fb2" -> {
                fb2FileParser.parse(cachedFile)
            }
            "html" -> {
                htmlFileParser.parse(cachedFile)
            }
            "htm" -> {
                htmlFileParser.parse(cachedFile)
            }
            "md" -> {
                txtFileParser.parse(cachedFile)
            }
            in listOf(
                "mp3",
                "m4a",
                "m4b",
                "aac"
            ) -> {
                audioFileParser.parse(cachedFile)
            }
            else -> {
                Log.e(FILE_PARSER, "Wrong file format, could not find supported extension.")
                null
            }
        }

        cachedFile.rawFile?.absoluteFile?.absolutePath?.let { filePath ->
            val crc = MobiParser.getFileCrc(filePath)
            book?.crc = crc?.crc ?: 0
        }

        return book
    }
}