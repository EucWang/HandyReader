package com.wxn.bookread.data.model

data class TextChar(
    val charData: String,
    var start: Float,
    var end: Float,
    var selected: Boolean = false,
    var isImage: Boolean = false
) {
}