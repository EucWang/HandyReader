package com.wxn.reader.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.wxn.base.bean.Book
import kotlinx.parcelize.Parcelize


@Parcelize
@Immutable
data class BookShelf(
    var bookId: Long = 0,
    var shelfId: Long = 0,
    var book: Book? = null,
    var shelf: Shelf? = null
) : Parcelable {}