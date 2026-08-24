package com.wxn.bookread.data.model

/**
 * 计算命中字符的视觉水平跨度（方向无关）。
 * 返回 (left, right) = 命中字符的最小 start / 最大 end；无命中返回 null。
 *
 * RTL 行的 textChars 按逻辑序追加（视觉右→左，见 TextLayoutProvider.placeCharsFromLayout），
 * 数组首尾字符不能直接作矩形左右边，必须对命中区间取 min/max。
 * LTR 行（视觉左→右序）下 min/max 恰好等价于首尾，行为不变。
 *
 * @param match 以 textChars 下标（rawIndex）为入参的命中判定；默认整行
 */
fun List<TextChar>.visualSpan(match: (index: Int) -> Boolean = { true }): Pair<Float, Float>? {
    var left = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    for (i in indices) {
        if (!match(i)) continue
        val ch = this[i]
        if (ch.start < left) left = ch.start
        if (ch.end > right) right = ch.end
    }
    return if (right < left) null else left to right
}
