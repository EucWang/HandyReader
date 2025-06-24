package com.wxn.base.util

import android.graphics.Color
import androidx.core.graphics.toColorInt

object ColorUtil {

    //在 Android 中，颜色值可以以多种格式表示，包括 rgb、argb、rrggbb、aarrggbb 等，Color.parseColor 支持这些格式
    fun toColor(fontColor: String): Int? {
        return try {
            fontColor.toColorInt()
        } catch (ex: Exception) {
            null
        }
    }
}