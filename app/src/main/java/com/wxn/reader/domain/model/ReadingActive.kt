package com.wxn.reader.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
data class ReadingActive(
    val date: Long, // Date in milliseconds
    val readingTime: Long, // Reading time in milliseconds
) : Parcelable {

}