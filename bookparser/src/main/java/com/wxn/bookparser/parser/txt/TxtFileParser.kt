package com.wxn.bookparser.parser.txt

import androidx.compose.ui.res.stringResource
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.R
import com.wxn.bookparser.domain.book.Book
import com.wxn.bookparser.domain.book.BookWithCover
import com.wxn.bookparser.domain.category.Category
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.ui.UIText
import javax.inject.Inject


class TxtFileParser @Inject constructor() : FileParser {

    override suspend fun parse(cachedFile: CachedFile): BookWithCover? {
        return try {
            val title = cachedFile.name.substringBeforeLast(".").trim()
            val author = stringResource(R.string.unknown_author)

            BookWithCover(
                book = Book(
                    title = title,
                    author = author,
                    description = null,
                    scrollIndex = 0,
                    scrollOffset = 0,
                    progress = 0f,
                    filePath = cachedFile.path,
                    lastOpened = null,
                    category = Category.DEFAULT,
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