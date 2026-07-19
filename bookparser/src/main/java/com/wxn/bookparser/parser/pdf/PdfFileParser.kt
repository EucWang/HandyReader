package com.wxn.bookparser.parser.pdf

import android.app.Application
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.openInputStream
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
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
        var inputStream: InputStream? = null
        try {
            inputStream = file.openInputStream(application.applicationContext)
            val baseName = file.baseName
            inputStream?.let { istream ->
                return innerParse(istream, baseName, file.uri.toString())
            }
        } catch (ex: Exception) {
            Logger.e(ex)
        }finally {
            try {
                inputStream?.close()
            } catch (ex2: Exception) {
                Logger.e(ex2)
            }
        }

        return null
    }

    override suspend fun parse(cachedFile: CachedFile): Book? {
        var inputStream: InputStream? = null
         try {
            inputStream = cachedFile.openInputStream()
            val baseName = cachedFile.name.substringBeforeLast(".").trim()
             inputStream?.let { istream ->
                 return innerParse(istream, baseName, cachedFile.uri.toString())
             }
        } catch (ex: Exception) {
             Logger.e(ex)
        }finally {
            try {
                inputStream?.close()
            }catch (ex2: Exception) {
                Logger.e(ex2)
            }
        }
        return null
    }

    private suspend fun innerParse(
        inputStream: InputStream?,
        baseName: String,
        path: String
    ): Book? {
        var document : PDDocument? = null
        try {
            PDFBoxResourceLoader.init(application)

            document = PDDocument.load(inputStream, MemoryUsageSetting.setupTempFileOnly())

            document?.use { document ->
                val title = if (document.documentInformation.title.isNullOrEmpty()) baseName else document.documentInformation.title
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
                val cover = try {
                    if (saveBitmapToFile(
                            application.applicationContext,
                            PDFRenderer(document).renderImage(0, 0.5f, ImageType.RGB),
                            targetPath
                        )
                    ) {
                        targetPath
                    } else {
                        ""
                    }
                } catch (e: Exception) {
                    Log.e("PdfFileParser", "封面渲染失败: ${e.message}")
                    ""
                }

                Log.d(
                    "PdfFileParser",
                    "innerParse::title=$title,author=$author,description=$description," +
                            "subject=$subject,keywords=$keywords,creator=$keywords," +
                            "producer=$producer,creationDateMillis=$creationDateMillis,modificationDateMillis=$modificationDateMillis,cover=$cover"
                )

                return Book(
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
            }
        } catch (e: Exception) {
            Logger.e(e)
        } finally {
            try {
                document?.close()
            } catch (ex: Exception) {
                Logger.e(ex)
            }
        }
        return null
    }
}