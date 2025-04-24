package com.wxn.reader.domain.model
import com.wxn.reader.data.model.Book

data class Author(
    val name: String,
    val books: List<Book>
)
