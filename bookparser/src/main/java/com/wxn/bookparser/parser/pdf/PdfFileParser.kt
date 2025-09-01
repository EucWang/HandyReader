package com.wxn.bookparser.parser.pdf

import android.app.Application
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.openInputStream
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.wxn.base.bean.Book
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.util.FileUtil.saveBitmapToFile
import com.wxn.bookparser.util.getCoverPath
import java.io.InputStream
import javax.inject.Inject

class PdfFileParser @Inject constructor(
    private val application: Application
) : FileParser {

    override suspend fun parse(file: DocumentFile): Book? {
        return try {
            val inputStream = file.openInputStream(application.applicationContext)
            val baseName = file.baseName
            innerParse(inputStream, baseName, file.uri.toString())
        } catch (ex: Exception) {
            null
        }
    }

    override suspend fun parse(cachedFile: CachedFile): Book? {
        return try {
            val inputStream = cachedFile.openInputStream()
            val baseName = cachedFile.name.substringBeforeLast(".").trim()
            val path = cachedFile.path
            innerParse(inputStream, baseName, cachedFile.uri.toString())
        } catch (ex: Exception) {
            null
        }

    }

    private suspend fun innerParse(
        inputStream: InputStream?,
        baseName: String,
        path: String
    ): Book? {
        return try {
            PDFBoxResourceLoader.init(application)
            val document = PDDocument.load(inputStream)

            val title = document.documentInformation.title ?: baseName
            val author = document.documentInformation.author.orEmpty()
            val description = document.documentInformation.subject.orEmpty()

            val subject = document.documentInformation.subject.orEmpty()
            val keywords = document.documentInformation.keywords.orEmpty()
            val creator = document.documentInformation.creator.orEmpty()

            val producer = document.documentInformation.producer.orEmpty()
            val creationDateMillis = document.documentInformation.creationDate?.timeInMillis ?: 0
            val modificationDateMillis =
                document.documentInformation.modificationDate?.timeInMillis ?: 0

            val pages = document.numberOfPages

            val targetPath = getCoverPath(application.applicationContext, title)
            val cover = if (saveBitmapToFile(
                    application.applicationContext,
                    PDFRenderer(document).renderImage(0, 0.5f, ImageType.RGB),
                    targetPath
                )
            ) {
                targetPath
            } else {
                ""
            }
            Log.d(
                "PdfFileParser",
                "innerParse::title=$title,author=$author,description=$description," +
                        "subject=$subject,keywords=$keywords,creator=$keywords," +
                        "producer=$producer,creationDateMillis=$creationDateMillis,modificationDateMillis=$modificationDateMillis,cover=$cover"
            )

            document.close()

            Book(
                title = title,
                author = author,
                description = description,
                publisher = if (producer.isEmpty()) creator else producer,
                numberOfPages = pages,

                scrollIndex = 0,
                scrollOffset = 0,
                progress = 0f,
                filePath = path,
                lastOpened = null,
                category = subject,
                coverImage = cover,
                fileType = "pdf"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}