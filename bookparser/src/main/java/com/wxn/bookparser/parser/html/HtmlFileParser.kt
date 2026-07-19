package com.wxn.bookparser.parser.html

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.openInputStream
import com.wxn.base.bean.Book
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.domain.file.CachedFile
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.InputStream
import javax.inject.Inject


class HtmlFileParser @Inject constructor(val context: Context) : FileParser {

    override suspend fun parse(file: DocumentFile): Book? {
        return try {
            val inputStream = file.openInputStream(context)
            val title = file.baseName
            innerParse(inputStream, title, file.uri.toString())
        } catch (ex: Exception) {
            null
        }
    }

    override suspend fun parse(cachedFile: CachedFile): Book? {
        return try {
            val inputStream = cachedFile.openInputStream()
            val title = cachedFile.name.substringBeforeLast(".").trim()
            innerParse(inputStream, title, cachedFile.uri.toString())
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
            val document = inputStream?.use {
                Jsoup.parse(it, null, "", Parser.htmlParser())
            }

            val title = document?.select("head > title")?.text()?.trim().run {
                if (isNullOrBlank()) {
                    return@run baseName
                }
                return@run this
            }

            Book(
                title = title,
                author = "", // stringResource(R.string.unknown_author),
                description = null,
                scrollIndex = 0,
                scrollOffset = 0,
                progress = 0f,
                filePath = path,
                lastOpened = null,
                category = "",
                coverImage = null,
                fileType = "html"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}