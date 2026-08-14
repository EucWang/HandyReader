package com.wxn.bookread.data.model

data class TextChar(
    val charData: String,
    var start: Float,
    var end: Float,
    var isImage: Boolean = false,

    var renderGroup: Int = 0  //LTR 链路默认 0；TextLayoutProvider 填 ≥1
)