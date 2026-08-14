package com.wxn.bookread.provider

/**
 * 排版游标。
 * 子函数返回此对象，主循环和段落间传递。
 *
 * 与 v3 的 LayoutResult 不同，
 * LayoutCursor 不携带「溢出行数」——因为方案 B 的子函数
 * 内部已经处理完列切换，返回时整段已排完，
 * 游标只是告诉调用方「下一段应从哪个 Y、哪一列继续」。
 *
 * @param offsetY 排版后的 Y 偏移
 * @param bounds 当前所在的列几何
 *              （单列时为 page，双列时为 leftColumn/rightColumn）
 */
data class LayoutCursor(
    val offsetY: Float,
    val bounds: LayoutBounds
)