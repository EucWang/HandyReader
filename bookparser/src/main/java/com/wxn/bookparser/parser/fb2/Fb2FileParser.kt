package com.wxn.bookparser.parser.fb2

import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.openInputStream
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.R
import com.wxn.bookparser.domain.book.Book
import com.wxn.bookparser.domain.book.BookWithCover
import com.wxn.bookparser.domain.file.CachedFile
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.InputStream
import javax.inject.Inject

class Fb2FileParser @Inject constructor(val context: Context) : FileParser {

    override suspend fun parse(file: DocumentFile): BookWithCover? {
        return try {
            val inputStream: InputStream? = file.openInputStream(context)
            val title = file.baseName
            val absolutePath = file.getAbsolutePath(context)
            innerParse(inputStream, title, absolutePath)
        }catch (ex : Exception) {
            null
        }
    }

    override suspend fun parse(cachedFile: CachedFile): BookWithCover? {
        return try {
            val inputStream: InputStream? = cachedFile.openInputStream()
            val title = cachedFile.name.substringBeforeLast(".").trim()
            val absolutePath = cachedFile.path
            innerParse(inputStream, title, absolutePath)
        } catch (ex: Exception) {
            null
        }
    }

    private fun innerParse(inputStream: InputStream?, baseName: String, path: String): BookWithCover? {
        return try {
            val document = inputStream?.use {
                Jsoup.parse(it, null, "", Parser.xmlParser())
            }

            val title = document?.selectFirst("book-title")?.text()?.trim().run {
                if (isNullOrBlank()) {
                    return@run baseName
                }
                this
            }

            val author = document?.selectFirst("author")?.text()?.trim().run {
                if (isNullOrBlank()) {
                    return@run stringResource(R.string.unknown_author)
                }
                this.trim()
            }

            val description = document?.selectFirst("annotation")?.text()?.trim().run {
                if (isNullOrBlank()) {
                    return@run null
                }
                this
            }

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
                    fileType = "fb2",
                ),
                coverImage = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}