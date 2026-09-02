package com.wxn.bookread.provider


/***
 * 列几何镜像
 */
object TableGeometry {

    /**
     * 单元格左右内边距
     * （= legacy setTextTable 局部常量
     * tableCellInnerPadding :1506，Int 10px 非 dp；使用 :1550/:1552）
     **/
    const val CELL_INNER_PADDING = 10f


    /***
     *  单元格内容区左边缘 X（相对 layoutBounds.startX + marginLeft）。
     *   LTR：首列在左，从左累加；RTL：首列在右（镜像）。
     */
    fun cellLeftOffset(fullWidth: Float,
                       leftOffsetPercent: Int,
                       tagPercent: Int,
                       isRtl: Boolean) : Float =
        if (isRtl) {
            fullWidth - fullWidth * (leftOffsetPercent + tagPercent) / 100f + CELL_INNER_PADDING
        } else {
            fullWidth * (leftOffsetPercent / 100f) + CELL_INNER_PADDING
        }

    /***
     * 内部列分隔竖线 X（相对列内容区左缘）。
     *  ★ 哨兵：leftPercent==0（最左边界）不镜像——镜像会变 100% 与右边界重合（BUG-2）。
     */
    fun verticalBorderX(fullWidth: Float,
        leftPercent: Float,
        isRtl: Boolean) : Float =
        if (isRtl && leftPercent > 0f) {
            fullWidth * ((100f - leftPercent) / 100f)
        } else {
            fullWidth * (leftPercent / 100f)
        }
}