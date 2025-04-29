package com.wxn.bookparser.domain.category

import androidx.compose.runtime.Immutable
import com.wxn.bookparser.domain.book.SelectableBook
import com.wxn.bookparser.domain.ui.UIText

@Immutable
data class CategoryWithBooks(
    val category: String,
    val title: UIText,
    val books: List<SelectableBook>
)