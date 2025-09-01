package com.wxn.bookread.data.model.config

import androidx.compose.ui.text.style.TextAlign

fun toTextAlign(align: String?): TextAlign? {
    if (align.isNullOrEmpty()) return null
    return when (align) {
        "Left" -> TextAlign.Left
        "Right" -> TextAlign.Right
        "Center" -> TextAlign.Center
        "Justify" -> TextAlign.Justify
        "Start" -> TextAlign.Start
        "End" -> TextAlign.End
        else -> null
    }
}