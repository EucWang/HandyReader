package com.wxn.bookparser.domain.book

import androidx.compose.runtime.Immutable

import com.wxn.base.bean.Book

@Immutable
data class SelectableBook(
    val data: Book,
    val selected: Boolean
)