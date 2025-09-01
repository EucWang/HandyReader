package com.wxn.bookread.ext

import android.content.res.Resources


/***
 * 将像素单位的int值转换成以dp为单位的int值
 */
val Float.dp: Float
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, this, Resources.getSystem().displayMetrics
    ).toFloat()

/***
 * 将像素单位的int值转换成sp为单位的int值
 */
val Float.sp: Float
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP, this, Resources.getSystem().displayMetrics
    ).toFloat()
