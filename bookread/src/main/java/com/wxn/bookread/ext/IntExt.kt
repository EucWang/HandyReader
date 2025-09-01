package com.wxn.bookread.ext

import android.content.res.Resources

/***
 * 将像素单位的int值转换成以dp为单位的int值
 */
val Int.dp: Int
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics
    ).toInt()

/***
 * 将像素单位的int值转换成sp为单位的int值
 */
val Int.sp: Int
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP, this.toFloat(), Resources.getSystem().displayMetrics
    ).toInt()


/***
 * 将数字转换成16进制显示的字符串
 */
val Int.hexString: String
    get() = Integer.toHexString(this)