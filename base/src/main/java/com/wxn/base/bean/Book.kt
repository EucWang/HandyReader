package com.wxn.base.bean


import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
data class Book(
    val id: Long = 0,
    val title: String,
    val author: String,

    val description: String?,
    val filePath: String,
    val coverImage: String?,

    var scrollIndex: Int, //对应章节位置
    var scrollOffset: Int,  //对应章节内偏移位置，从而确定当前阅读位置
    var progress: Float,    //阅读进度,  取值：0~100

    var lastOpened: Long?,
    var category: String?,

    //----------------
    val fileType: String,

    val publishDate: String? = "", // New: Publication date 出版日期
    val publisher: String? = "", // New: Publisher  出版商
    val language: String? = "", // New: Primary language 语言

    val numberOfPages: Int? = 0, // New: Total number of pages 总页数
    var wordCount: Long = 0,   // 总字数
    val locator: String = "", //阅读位置
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

    var crc: Int = 0,
    var cachedDir: String? = null,

) : Parcelable