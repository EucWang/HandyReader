package com.wxn.bookread.data.beans

data class PageKey(
    val chapterIndex: Int,
    val pageIndex: Int,
    val contentHash: Int = 0,
    val viewWidth: Int = 0,
    val viewHeight: Int = 0
)