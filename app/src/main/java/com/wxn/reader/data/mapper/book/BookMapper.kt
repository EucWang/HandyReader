package com.wxn.reader.data.mapper.book

import com.wxn.base.bean.Book
import com.wxn.reader.data.dto.BookEntity

interface BookMapper {
    suspend fun toBookEntity(book: Book): BookEntity

    suspend fun toBook(bookEntity: BookEntity): Book
}
