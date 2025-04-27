package com.wxn.bookparser.domain.book

import androidx.compose.runtime.Immutable
import com.wxn.bookparser.domain.ui.UIText

@Immutable
sealed class NullableBook(
    val bookWithCover: BookWithCover?,
    val fileName: String?,
    val message: UIText?
) {
    class NotNull(
        bookWithCover: BookWithCover
    ) : NullableBook(
        bookWithCover = bookWithCover,
        fileName = null,
        message = null
    )

    class Null(
        fileName: String,
        message: UIText?
    ) : NullableBook(
        bookWithCover = null,
        fileName = fileName,
        message = message
    )
}