package com.wxn.bookread.provider

import com.wxn.bookread.data.model.TextLine

/**
 * 长按/选区命中与词边界分组（S10，方案 2026-09-02-plan-table-select-hit-2d.md R3）。
 * 分页（ContentTextView）与连续滚动（ContinuousScrollReaderView）共用；纯几何/数据判定，JVM 可测。
 */
object TextSelectionHandler {

    /**
     * 二维命中（决策点①批复 = B：严格命中，间隙不吸附）：
     * Y 命中带内的每个候选行做严格 X 字符匹配（x ∈ (ch.start, ch.end)）；
     * Y 命中但 X 无匹配 → 继续扫描后续行（表格跨格落空的根源修复点，无兜底分支）；
     * 全部候选落空 → null（与现状"行尾空白/字符间隙触摸无选中"口径一致）。
     * 边框行显式排除；图片字符不参与命中。
     * @return Pair(lineIndex, charIndex)（page.textLines 数组口径），无命中 null
     */
    fun findTextPositionAt(
        lines: List<TextLine>, x: Float, y: Float, yOffset: Float
    ): Pair<Int, Int>? {
        for ((index, line) in lines.withIndex()) {
            if (line.isLine) continue
            val top = line.lineTop + yOffset
            val bottom = line.lineBottom + yOffset
            if (y <= top || y >= bottom) continue
            for ((ci, ch) in line.textChars.withIndex()) {
                if (!ch.isImage && x > ch.start && x < ch.end) return index to ci
            }
            // 本行 X 无匹配 → 继续扫描（同 Y 带的下一格可能是触点所在格）
        }
        return null
    }

    /**
     * 词边界分组判定（RC2 修复）：按下的行与候选行是否属同一分词组。
     * 正文行：paragraphIndex 相同（现状口径不变，表格行被排除）；
     * 表格行（isTableCell）：同一单元格（同 paragraphIndex + rowIndex + colIndex）——
     * 词边界收敛在单元格内，不再跨格连词。
     */
    fun sameWordGroup(pressed: TextLine, candidate: TextLine): Boolean {
        if (candidate.paragraphIndex != pressed.paragraphIndex) return false
        return if (pressed.isTableCell) {
            candidate.isTableCell &&
                candidate.rowIndex == pressed.rowIndex &&
                candidate.colIndex == pressed.colIndex
        } else {
            !candidate.isTableCell
        }
    }
}
