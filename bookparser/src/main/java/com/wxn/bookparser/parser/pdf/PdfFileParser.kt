package com.wxn.bookparser.parser.pdf

import android.app.Application
import androidx.compose.ui.res.stringResource
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.R
import com.wxn.bookparser.domain.book.Book
import com.wxn.bookparser.domain.book.BookWithCover
import com.wxn.bookparser.domain.category.Category
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.ui.UIText
import javax.inject.Inject

class PdfFileParser @Inject constructor(
    private val application: Application
) : FileParser {

    override suspend fun parse(cachedFile: CachedFile): BookWithCover? {
        return try {
            PDFBoxResourceLoader.init(application)
            val document = PDDocument.load(cachedFile.openInputStream())

            val title = document.documentInformation.title
                ?: cachedFile.name.substringBeforeLast(".").trim()
            val author = document.documentInformation.author.run {
                if (isNullOrBlank()) stringResource(R.string.unknown_author)
                else this
            }
            val description = document.documentInformation.subject

            document.close()

            BookWithCover(
                book = Book(
                    title = title,
                    author = author,
                    description = description,
                    scrollIndex = 0,
                    scrollOffset = 0,
                    progress = 0f,
                    filePath = cachedFile.path,
                    lastOpened = null,
                    category = Category.DEFAULT,
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