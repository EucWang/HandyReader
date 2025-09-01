package com.wxn.base.ext

import android.content.Context
import android.util.TypedValue

object DpExt {

    fun dp2px(context: Context, dpValue: Float): Float {
        val pixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue, context.resources.displayMetrics)
        return pixels
    }

}