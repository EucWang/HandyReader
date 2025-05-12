package com.wxn.bookread.ext

import android.content.res.Resources


/***
 * 将像素单位的int值转换成以dp为单位的int值
 */
val Double.dp: Double
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics
    ).toDouble()

/***
 * 将像素单位的int值转换成sp为单位的int值
 */
val Double.sp: Double
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP, this.toFloat(), Resources.getSystem().displayMetrics
    ).toDouble()
