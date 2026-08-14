package com.wxn.bookread.provider

import com.wxn.bookread.data.model.TextLine


data class LineRecord(
    val line: TextLine,
    val bounds: LayoutBounds,
    val isFirstLine: Boolean
)
