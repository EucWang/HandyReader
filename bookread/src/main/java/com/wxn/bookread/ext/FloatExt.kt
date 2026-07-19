package com.wxn.bookread.ext

import android.content.res.Resources
import kotlin.math.roundToInt


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


/***
 * 将一个浮点数小时时间转换成显示的时间格式 "xxhxxm"
 * 只处理大于0 的情况
 */
fun Float.fmtToTime() : String {
    if (this <= 0) {
        return ""
    }

    val hour = this.toInt()
    val mins = ((this - hour) * 60).toInt()
    val ret = StringBuilder()
    if (hour > 0) {
        ret.append("${hour}h")
    }
    if (mins > 0) {
        ret.append("${mins}m")
    }
    return ret.toString()
}

/***
 * 将浮点数保留小数点后多少位
 * @param count 小数点后多少位
 */
fun Float.roundWithDot(count: Int): String {
    if(count <= 0) {
        return this.toString()
    }
    return (((this * (10 * count)).roundToInt()) * 1.0f / (10 * count)).toString()
}