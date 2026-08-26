package com.wxn.bookread.data.beans

/**
 * 一次 bidi 解析的结果：段落基级（P2-P3）+ 视觉 run 列表（逻辑序）
 *  */
data class BidiParagraph(
    val baseLevel: Int,        // 0=LTR 基调, 1=RTL 基调（奇数即 RTL）
    val runs: List<BidiRun>
)