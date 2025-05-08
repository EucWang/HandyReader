package com.wxn.bookparser.domain.book

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

@Immutable
data class BookWithCover(
    val book: Book,
//    val coverImage: Bitmap?
    val coverImage: String?
)