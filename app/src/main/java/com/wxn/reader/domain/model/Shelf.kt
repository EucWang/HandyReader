package com.wxn.reader.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize


@Parcelize
@Immutable
data class Shelf (
    var id : Long = 0,
    var name : String = "",
    var order : Int = 0
): Parcelable