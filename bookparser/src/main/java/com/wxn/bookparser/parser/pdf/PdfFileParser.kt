package com.wxn.bookparser.parser.pdf

import android.app.Application
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.openInputStream
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.R
import com.wxn.bookparser.domain.book.Book
import com.wxn.bookparser.domain.book.BookWithCover
import com.wxn.bookparser.domain.file.CachedFile
import java.io.InputStream
import javax.inject.Inject

class PdfFileParser @Inject constructor(
    private val application: Application
) : FileParser {

    override suspend fun parse(file: DocumentFile): BookWithCover? {
        return try {
            val inputStream = file.openInputStream(application.applicationContext)
            val baseName = file.baseName
            innerParse(inputStream, baseName, file.uri.toString())
        } catch (ex: Exception) {
            null
        }
    }

    override suspend fun parse(cachedFile: CachedFile): BookWithCover? {
        return try {
            val inputStream = cachedFile.openInputStream()
            val baseName = cachedFile.name.substringBeforeLast(".").trim()
            val path = cachedFile.path
            innerParse(inputStream, baseName, cachedFile.uri.toString())
        } catch (ex: Exception) {
            null
        }

    }

    private suspend fun innerParse(inputStream: InputStream?, baseName: String, path: String): BookWithCover? {
        return try {
            PDFBoxResourceLoader.init(application)
            val document = PDDocument.load(inputStream)

            val title = document.documentInformation.title ?: baseName
            val author = document.documentInformation.author.orEmpty()
            val description = document.documentInformation.subject.orEmpty()

            document.close()

            BookWithCover(
                book = Book(
                    title = title,
                    author = author,
                    description = description,
                    scrollIndex = 0,
                    scrollOffset = 0,
                    progress = 0f,
                    filePath = path,
                    lastOpened = null,
                    category = "",
                    coverImage = null,
                    fileType = "pdf"
                ),
                coverImage = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}