package com.wxn.reader.data.mapper.bookshelf

import com.wxn.reader.data.dto.BookShelfEntity
import com.wxn.reader.domain.model.BookShelf
import javax.inject.Inject

class BookShelfMapperImpl @Inject constructor() : BookShelfMapper {
    override suspend fun toBookShelfEntity(bookShelf: BookShelf): BookShelfEntity {
        return BookShelfEntity(
            bookId = bookShelf.bookId,
            shelfId = bookShelf.shelfId
        )
    }

    override suspend fun toBookShelf(bookShelfEntity: BookShelfEntity): BookShelf {
        return BookShelf(
            bookId = bookShelfEntity.bookId,
            shelfId = bookShelfEntity.shelfId,
            book = null,
            shelf = null
        )
    }
}