package com.wxn.reader.domain.model

import com.wxn.base.bean.Book

data class Author(
    val name: String,
    val books: List<Book>
)
