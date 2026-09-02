package com.wxn.bookread.provider

import android.text.Layout
import com.wxn.base.bean.CssTextAlign
import com.wxn.bookread.data.model.TextLine


/***
 * 表格视觉元素生成器：边框线段（isLine TextLine）+ 共享视觉常量 + 单元格对齐映射。
 * * 不持有 Canvas 代码——线段由排版期发射，
 * 绘制由 ContentTextView / ContinuousScrollReaderView
 *  的通用 isLine 路径完成（零改动）。
 */
object TableRenderProvider {


    const val BORDER_COLOR = "#333333"   // lineColor 字段填充值（绘制端不消费，数据契约保留）
    const val BORDER_WIDTH = 1f

    /**
     * 主题感知边框（S12）：边框色 = 当前正文文字色的 RGB + 固定 alpha。
     * 文字色（contentPaint.color，upStyle 随主题刷新）与背景恒保对比 → 派生边框在
     * 深/浅/自定义主题下自动可辨，无需感知主题枚举。
     */
    const val BORDER_ALPHA = 0x66   // 40%：实色可辨且弱于正文墨色

    fun borderColorFor(textColor: Int): Int = textColor and 0x00FFFFFF or (BORDER_ALPHA shl 24)

    /**
     * 单元格 StaticLayout 对齐映射（已批方案 §6.3.3 / D2 / D8）。
     * 单元格 layout 已 setTextDirection(表格基调)，
     * NORMAL = 基调起始边（RTL 下自动贴右缘）。
     * 注：物理 Left 在 RTL 基调下按 align-start 处理
     * （浏览器 dir=rtl 下 start 语义），
     *     CSS 物理 left 的精确语义差异为已知取舍（与已批方案一致）。
     */
    fun alignOf(textAlign: CssTextAlign, tableIsRtl: Boolean): Layout.Alignment = when (textAlign) {
        CssTextAlign.CssTextAlignCenter -> Layout.Alignment.ALIGN_CENTER
        CssTextAlign.CssTextAlignRight ->
            if (tableIsRtl) Layout.Alignment.ALIGN_NORMAL     // RTL 基调右缘
            else Layout.Alignment.ALIGN_OPPOSITE             // LTR 基调右缘
        // Left / Justify / Undefined → 基调起始边（Justify 为 D8 降级；Undefined 为 D2 默认）
        else -> Layout.Alignment.ALIGN_NORMAL
    }

    /**
     * 水平线
     */
    private fun hLine(x1: Float, x2: Float, y: Float) = TextLine(
        isLine = true,
        lineStart = Pair(x1, y), lineEnd = Pair(x2, y),
        lineBorder = BORDER_WIDTH, lineColor = BORDER_COLOR
    )

    /**
     * 垂直线
     */
    private fun vLine(x: Float, y1: Float, y2: Float) = TextLine(
        isLine = true,
        lineStart = Pair(x, y1), lineEnd = Pair(x, y2),
        lineBorder = BORDER_WIDTH, lineColor = BORDER_COLOR
    )

    /**
     * 生成一个表格逻辑行的全部边框线段（坐标规则与 legacy :1663-1730 逐条对应）：
     * - 顶横线：每个 tr 的首个逻辑行（跨行边界单线）
     * - 底横线：仅表格末行（rowIndex == rows-1）的末逻辑行
     * - 竖线：左边界、内部列分隔（RTL 镜像）、右边界，高度 = rowBoxHeight
     * 纯 LTR（tableIsRtl=false）输入下坐标与 legacy 公式数值一致。
     */
    fun buildRowBorders(
        layoutBounds: LayoutBounds,
        fullWidth: Float,
        marginLeft: Float,
        marginRight: Float,
        tablePercents: List<Int>,
        isFirstLogicLine: Boolean,
        isLastTableRow: Boolean,
        isLastLogicLine: Boolean,
        rowTopY: Float,
        rowBoxHeight: Float,
        tableIsRtl: Boolean
    ): List<TextLine> {
        val lines = arrayListOf<TextLine>()
        val contentLeft = layoutBounds.startX + marginLeft
        val contentRight = layoutBounds.endX - marginRight
        if (isFirstLogicLine) lines.add(hLine(contentLeft, contentRight, rowTopY))

        var leftPercent = 0f
        for (i in 0..tablePercents.size) {
            val x = when {
                i == 0 -> contentLeft                                        // 左边界（哨兵，不镜像）
                i == tablePercents.size -> contentRight                      // 右边界
                else -> {
                    leftPercent += tablePercents[i - 1]
                    contentLeft + TableGeometry.verticalBorderX(fullWidth, leftPercent, tableIsRtl)
                }
            }
            lines.add(vLine(x, rowTopY, rowTopY + rowBoxHeight))
        }
        if (isLastTableRow && isLastLogicLine) {
            lines.add(hLine(contentLeft, contentRight, rowTopY + rowBoxHeight))
        }
        return lines
    }
}