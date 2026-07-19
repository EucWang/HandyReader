package com.wxn.bookparser.parser.txt

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.wxn.base.bean.Book
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.domain.file.CachedFile
import javax.inject.Inject


class TxtFileParser @Inject constructor(val context: Context) : FileParser {

    override suspend fun parse(file: DocumentFile): Book? {
        return try {
            Book(
                title = file.baseName,
                author = "", // stringResource(R.string.unknown_author),
                description = null,
                scrollIndex = 0,
                scrollOffset = 0,
                progress = 0f,
                filePath = file.uri.toString(),
                lastOpened = null,
                category = "",
                coverImage = null,
                fileType = "txt"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun parse(cachedFile: CachedFile): Book? {
        return try {
            val title = cachedFile.name.substringBeforeLast(".").trim()
            val author = "" //stringResource(R.string.unknown_author)

            Book(
                title = title,
                author = author,
                description = null,
                scrollIndex = 0,
                scrollOffset = 0,
                progress = 0f,
                filePath = cachedFile.uri.toString(),
                lastOpened = null,
                category = "",
                coverImage = null,
                fileType = "txt"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}