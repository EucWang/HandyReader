package com.wxn.bookparser.domain.book

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.wxn.bookparser.domain.ui.UIText
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
data class Book(
    val id: Int = 0,

    val title: String,
    val author: UIText,
    val description: String?,

    val filePath: String,
    val coverImage: Uri?,

    val scrollIndex: Int,
    val scrollOffset: Int,
    val progress: Float,

    val lastOpened: Long?,
    val category: Category
) : Parcelable