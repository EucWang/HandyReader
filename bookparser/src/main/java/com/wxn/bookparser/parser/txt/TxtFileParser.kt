package com.wxn.bookparser.parser.txt

import android.content.Context
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.fullName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.isRawFile
import com.anggrayudi.storage.file.toRawFile
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.R
import com.wxn.bookparser.domain.book.Book
import com.wxn.bookparser.domain.book.BookWithCover
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.ui.UIText
import javax.inject.Inject


class TxtFileParser @Inject constructor(val context: Context) : FileParser {

    override suspend fun parse(file: DocumentFile): BookWithCover? {
        return try {
            BookWithCover(
                book = Book(
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
                ),
                coverImage = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun parse(cachedFile: CachedFile): BookWithCover? {
        return try {
            val title = cachedFile.name.substringBeforeLast(".").trim()
            val author = "" //stringResource(R.string.unknown_author)

            BookWithCover(
                book = Book(
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
                ),
                coverImage = null
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}