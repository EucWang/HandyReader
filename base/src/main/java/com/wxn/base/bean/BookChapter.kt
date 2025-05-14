package com.wxn.base.bean

data class BookChapter(
    val id : Long = 0,

    val bookId: Long,
    val chapterIndex: Int,
    var chapterName: String,
    val createTimeValue: Long,
    val updateDate: String,
    val updateTimeValue: Long,
    val chapterUrl: String?,

    val cachedName: String?,

    val chaptersSize: Int
) {
}