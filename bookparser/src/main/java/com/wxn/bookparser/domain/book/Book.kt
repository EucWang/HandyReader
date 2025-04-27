package com.wxn.bookparser.domain.book

import android.net.Uri
import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.wxn.bookparser.domain.category.Category
import com.wxn.bookparser.domain.ui.UIText
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
data class Book(
    val id: Long = 0,
    val title: String,
    val author: UIText,

    val description: String?,
    val filePath: String,
    val coverImage: Uri?,

    val scrollIndex: Int,
    val scrollOffset: Int,
    val progress: Float,

    val lastOpened: Long?,
    val category: Category,

    //----------------
    val fileType: String,

    val publishDate: String?, // New: Publication date 出版日期
    val publisher: String?, // New: Publisher  出版商
    val language: String?, // New: Primary language 语言

    val numberOfPages: Int?, // New: Total number of pages 总页数
    val locator: String, //阅读位置
    val deleted: Boolean = false, // flag to mark the book as deleted 是否删除

    val rating: Float = 0f, // rating of the book  标星
    val isFavorite: Boolean = false, // flag to mark the book as favorite 是否最喜欢

    val readingStatus: Int? = 0, // reading status of the book

    val readingTime: Long = 0, // total time spent reading the book in milliseconds
    val startReadingDate: Long? = null, // timestamp of when the user started reading the book
    val endReadingDate: Long? = null, // timestamp of when the user finished reading the book

    val review: String? = null,
    val duration: Long? = null, // Total duration of the audiobook in milliseconds
    val narrator: String? = null, // Name of the audiobook narrator

) : Parcelable