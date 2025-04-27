package com.wxn.bookparser.domain.book

import androidx.compose.runtime.Immutable


@Immutable
data class SelectableBook(
    val data: Book,
    val selected: Boolean
)