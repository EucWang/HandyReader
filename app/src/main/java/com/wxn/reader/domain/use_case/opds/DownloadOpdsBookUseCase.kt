package com.wxn.reader.domain.use_case.opds

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.wxn.base.bean.Book
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.bookparser.FileParser
import com.wxn.reader.data.model.opds.OpdsEntry
import com.wxn.reader.data.model.opds.OpdsLink
import com.wxn.reader.data.remote.opds.OpdsApiClient
import com.wxn.reader.domain.use_case.books.InsertBookUseCase
import com.wxn.reader.util.download.FileDownloadManager
import com.wxn.reader.util.download.FileValidationException
import com.wxn.reader.util.download.FileValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class DownloadOpdsBookUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileDownloadManager: FileDownloadManager,
    private val fileParser: FileParser,
    private val insertBookUseCase: InsertBookUseCase,
    private val opdsApiClient: OpdsApiClient
) {
    data class DownloadResult(
        val fileId: String,
        val targetPath: String,
        val entry: OpdsEntry
    )

    private data class DownloadFileInfo(
        val fileId: String,
        val targetPath: String,
        val fileName: String,
        val authHeader: String?
    )

    private fun getDownloadFileInfo(entry: OpdsEntry, link: OpdsLink, catalogId: Long): DownloadFileInfo {
        val urlFileName = link.href.substringAfterLast("/").substringBefore("?")
            .takeIf { it.contains(".") }

        val fileName = if (urlFileName != null) {
            val knownExtensions = setOf(
                "epub", "pdf", "mobi", "azw3", "fb2", "txt", "html", "htm", "md", "mp3", "m4a", "m4b", "aac"
            )
            val urlExtension = urlFileName.substringAfterLast(".").lowercase()
            if (urlExtension in knownExtensions) urlFileName
            else urlFileName + extensionFromMimeType(link.type)
        } else {
            sanitizeFileName(entry.title) + extensionFromMimeType(link.type)
        }

        val subDir = "$catalogId"
        val fileId = "opds_${catalogId}_${entry.id.hashCode().toUInt()}_${link.href.hashCode().toUInt()}"
        val targetPath = PathUtil.getDownloadFilePath(
            context,
            DownloadFileType.OPDS_BOOK,
            "$subDir/$fileName",
            fileId
        )

        val authHeader = opdsApiClient.buildAuthHeader(catalogId)

        return DownloadFileInfo(fileId, targetPath, fileName, authHeader)
    }

    fun enqueueDownload(entry: OpdsEntry, link: OpdsLink, catalogId: Long): DownloadResult {
        val info = getDownloadFileInfo(entry, link, catalogId)

        val existingFile = File(info.targetPath)
        if (existingFile.exists()) {
            existingFile.delete()
            Logger.d("DownloadOpdsBookUseCase::enqueueDownload: deleted existing file: ${info.targetPath}")
        }

        fileDownloadManager.enqueueDownload(
            fileId = info.fileId,
            url = link.href,
            fileType = DownloadFileType.OPDS_BOOK,
            fileName = "$catalogId/${info.fileName}",
            authHeader = info.authHeader
        )

        return DownloadResult(info.fileId, info.targetPath, entry)
    }

    suspend fun importDownloadedBook(targetPath: String, expectedSize: Long? = null): Result<Book> {
        return try {
            val file = File(targetPath)
            if (!file.exists()) return Result.failure(Exception("File not found: $targetPath"))

            val extension = targetPath.substringAfterLast(".").lowercase()

            val validation = FileValidator.validate(file, extension, expectedSize)
            if (!validation.isValid) {
                Logger.e("DownloadOpdsBookUseCase::importDownloadedBook: validation failed: ${validation.reason}, file=${file.absolutePath}, size=${file.length()}")
                file.delete()
                Logger.d("DownloadOpdsBookUseCase::importDownloadedBook: deleted corrupted file: ${file.absolutePath}")
                return Result.failure(FileValidationException("File validation failed: ${validation.reason}"))
            }

            val documentFile = DocumentFile.fromFile(file)
            val parsed = fileParser.parse(documentFile)
                ?: return Result.failure(Exception("Failed to parse book file: $targetPath"))

            val book = parsed.copy(filePath = documentFile.uri.toString(), source = "opds")
            val bookId = insertBookUseCase(book)
            if (bookId <= 0) {
                return Result.failure(Exception("Database insert failed for: ${book.title}"))
            }

            val importedBook = book.copy(id = bookId)
            Logger.d("DownloadOpdsBookUseCase::importDownloadedBook: imported ${importedBook.title} id=$bookId")
            Result.success(importedBook)
        } catch (e: Exception) {
            Logger.e("DownloadOpdsBookUseCase::importDownloadedBook: error: $e")
            Result.failure(e)
        }
    }

    suspend fun copyToSafAndUpdateBook(book: Book, tempPath: String, safTreeUri: String): Result<Book> {
        return try {
            val tempFile = File(tempPath)
            if (!tempFile.exists()) {
                return Result.failure(Exception("Temp file not found: $tempPath"))
            }

            val ext = tempPath.substringAfterLast(".").lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
            val fileNameWithoutExt = tempFile.nameWithoutExtension

            val safDir = DocumentFile.fromTreeUri(context, Uri.parse(safTreeUri))
                ?: return Result.failure(Exception("Invalid SAF tree URI: $safTreeUri"))

            val safFile = safDir.createFile(mimeType, fileNameWithoutExt)
                ?: return Result.failure(Exception("Failed to create file in SAF directory"))

            context.contentResolver.openOutputStream(safFile.uri)?.use { out ->
                tempFile.inputStream().use { inp -> inp.copyTo(out) }
            }

            tempFile.delete()

            val updatedBook = book.copy(filePath = safFile.uri.toString())
            val updateResult = insertBookUseCase(updatedBook)
            if (updateResult <= 0) {
                Logger.e("DownloadOpdsBookUseCase::copyToSafAndUpdateBook: DB update failed for book ${book.id}")
            }

            Logger.d("DownloadOpdsBookUseCase::copyToSafAndUpdateBook: copied ${tempFile.name} to SAF, bookId=${book.id}")
            Result.success(updatedBook)
        } catch (e: Exception) {
            Logger.e("DownloadOpdsBookUseCase::copyToSafAndUpdateBook: error: $e")
            Result.failure(e)
        }
    }

    private fun sanitizeFileName(title: String): String {
        return title.replace(Regex("[^a-zA-Z0-9._\\-\\s]"), "")
            .trim()
            .take(100)
            .ifBlank { "book" }
    }

    private fun extensionFromMimeType(mimeType: String?): String {
        return when (mimeType?.lowercase()) {
            "application/epub+zip", "application/epub" -> ".epub"
            "application/pdf" -> ".pdf"
            "application/x-mobipocket-ebook" -> ".mobi"
            "application/x-mobipocket-ebook-azw3", "application/vnd.amazon.mobi8-ebook" -> ".azw3"
            "application/x-fictionbook+xml" -> ".fb2"
            "text/plain" -> ".txt"
            "text/html" -> ".html"
            "text/markdown" -> ".md"
            "audio/mpeg" -> ".mp3"
            "audio/mp4" -> ".m4b"
            else -> ".epub"
        }
    }
    // MIME type list should be kept in sync with OpdsLink.DOWNLOADABLE_MIME_TYPES
}
