package com.wxn.reader.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.wxn.reader.data.dto.AnnotationType
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
data class BookAnnotation(
    val id: Long = 0,
    val bookId: Long,
    val locator: String,
    val color: String,
    val note: String?,
    val type: AnnotationType
) : Parcelable {
}