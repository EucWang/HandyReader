package com.wxn.bookparser.domain.book

import android.graphics.Bitmap
import com.wxn.base.bean.Book
import androidx.compose.runtime.Immutable

@Immutable
data class BookWithCover(
    val book: Book,
//    val coverImage: Bitmap?
    val coverImage: String?
)