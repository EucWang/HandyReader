package com.wxn.reader.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize


@Parcelize
@Immutable
data class Bookmark(
    var id: Long = 0,
    var bookId: Long = 0,
    var locator: String = "",
    var dateAndTime: Long = 0,
    var color: String? = null
) : Parcelable{

}