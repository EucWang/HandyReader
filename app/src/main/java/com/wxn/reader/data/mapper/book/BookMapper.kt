package com.wxn.reader.data.mapper.book

import com.wxn.bookparser.domain.book.Book
import com.wxn.reader.data.model.BookEntity

interface BookMapper {
    suspend fun toBookEntity(book: Book): BookEntity

    suspend fun toBook(bookEntity: BookEntity): Book
}
