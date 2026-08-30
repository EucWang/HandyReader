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


/** CJK 码点判定：
 * 区间表与 [CharExt.isWordChar] 同源；
 * 禁用 Character.UnicodeScript（API 24+，minSdk 23）
 * */
fun Int.isCjkCode(): Boolean =
    this in 0x2E80..0x9FFF ||    // CJK 部首/符号/注音/假名/汉字（含 Ext A）
            this in 0xAC00..0xD7FF ||    // 谚文音节 + 谚文扩展
            this in 0x1100..0x11FF ||    // 谚文字母 Jamo
            this in 0xA960..0xA97F ||    // 谚文扩展 A
            this in 0xF900..0xFAFF ||    // CJK 兼容表意文字
            this in 0xFF66..0xFF9D       // 半角片假名


/**
 * 连写/邻接 RTL 文字码点：
 * 词内加字距会打断 HarfBuzz 连写成形 → 行走纯词距分布（组内零移动，竞品一致）
 **/
fun Int.isConnectedScriptCode(): Boolean =
    this in 0x0600..0x06FF ||    // Arabic
    this in 0x0700..0x086F ||    // 叙利亚文/Thaana/N'Ko/曼达文等邻区 RTL 文字（R7 补全，纯词距处理皆安全）
    this in 0xFB50..0xFDFF ||    // Arabic Presentation Forms-A
    this in 0xFE70..0xFEFF ||    // Arabic Presentation Forms-B
    this in 0x1E900..0x1E95F     // Adlam（RTL 连写，R9 补全）