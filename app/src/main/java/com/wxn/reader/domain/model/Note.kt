package com.wxn.reader.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.wxn.base.bean.Locator
import kotlinx.parcelize.Parcelize


@Parcelize
@Immutable
data class Note (
    var id : Long = 0,
    var locator: String = "",
    var selectedText : String = "",
    var note : String = "",
    var color : String = "",
    var bookId : Long = 0
) : Parcelable {

    val locatorInfo: Locator?
        get() {
            return Locator.fromJsonString(locator)
        }
}