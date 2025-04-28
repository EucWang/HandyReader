package com.wxn.reader.domain.model

import com.wxn.bookparser.domain.book.Book

data class Author(
    val name: String,
    val books: List<Book>
)
