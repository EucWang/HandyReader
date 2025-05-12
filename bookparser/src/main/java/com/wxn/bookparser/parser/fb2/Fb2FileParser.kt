package com.wxn.bookparser.parser.fb2

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.openInputStream
import com.anggrayudi.storage.file.toRawFile
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.R
import com.wxn.bookparser.domain.book.Book
import com.wxn.bookparser.domain.book.BookWithCover
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.util.FileUtil
import com.wxn.bookparser.util.getCoverPath
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

class Fb2FileParser @Inject constructor(val context: Context) : FileParser {

    override suspend fun parse(file: DocumentFile): BookWithCover? {
        return try {
            val inputStream: InputStream? = file.openInputStream(context)
            val title = file.baseName
            innerParse(inputStream, title, file.uri.toString())
        }catch (ex : Exception) {
            null
        }
    }

    override suspend fun parse(cachedFile: CachedFile): BookWithCover? {
        return try {
            val inputStream: InputStream? = cachedFile.openInputStream()
            val title = cachedFile.name.substringBeforeLast(".").trim()
            innerParse(inputStream, title, cachedFile.uri.toString())
        } catch (ex: Exception) {
            null
        }
    }

    private fun innerParse(inputStream: InputStream?, baseName: String, uri: String): BookWithCover? {
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
                    return@run "" // stringResource(R.string.unknown_author)
                }
                this.trim()
            }

            val description = document?.selectFirst("annotation")?.text()?.trim().run {
                if (isNullOrBlank()) {
                    return@run null
                }
                this
            }

            var coverPath = ""
            document?.selectFirst("coverpage")?.let { ele ->
                val imageRef: String = ele.select("image").attr("xlink:href")
                Log.d("Fb2FileParser", "innerParse:imageRef=$imageRef")
                if (imageRef.startsWith("#")) {
                    document.selectFirst("binary[id=${imageRef.substring(1)}]")?.let { binary ->
                        val base64Data = binary.text();
                        val inputStream = ByteArrayInputStream(Base64.getDecoder().decode(base64Data))
                        coverPath  = getCoverPath(context, UUID.randomUUID().toString() + ".jpg")
                        FileUtil.writeStreamToFile(inputStream, coverPath)
                        Log.d("Fb2FileParser", "innerParse::coverPath=$coverPath")
                    }
                }
            }

            BookWithCover(
                book = Book(
                    title = title,
                    author = author,
                    description = description,
                    scrollIndex = 0,
                    scrollOffset = 0,
                    progress = 0f,
                    filePath = uri,
                    lastOpened = null,
                    category = "",
                    coverImage = coverPath,
                    fileType = "fb2",
                ),
                coverImage = coverPath
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}