package com.wxn.bookparser.domain.book

import androidx.compose.runtime.Immutable


@Immutable
data class SelectableNullableBook(
    val data: NullableBook,
    val selected: Boolean
)