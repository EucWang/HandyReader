package com.wxn.bookread.provider

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 单元格对齐锚定映射 JVM 直测（方案 2026-09-02-plan-table-cell-per-run-engine.md §7.1 文件 1）。
 * 门禁裁决 D4：表格绘制不受用户对齐样式影响——恒锚表基调起始缘。
 */
class TableCellAnchorShiftTest {

    @Test
    fun ltr_table_anchors_left_start_edge() {
        assertEquals(0f, TableLayoutProvider.cellAnchorTargetLeft(100f, 300, tableIsRtl = false), 0f)
    }

    @Test
    fun rtl_table_anchors_right_start_edge() {
        assertEquals(200f, TableLayoutProvider.cellAnchorTargetLeft(100f, 300, tableIsRtl = true), 0f)
    }

    @Test
    fun overflow_ltr_still_anchors_left_edge() {
        assertEquals(0f, TableLayoutProvider.cellAnchorTargetLeft(350f, 300, tableIsRtl = false), 0f)
    }

    @Test
    fun overflow_rtl_overflows_beyond_left_of_right_edge() {
        assertEquals(-50f, TableLayoutProvider.cellAnchorTargetLeft(350f, 300, tableIsRtl = true), 0f)
    }

    /** D4 构造性锁定：无样式分支——任意墨迹宽下 LTR 恒 0、RTL 恒 usable−contentW */
    @Test
    fun alignment_independent_invariant() {
        listOf(0f, 50f, 300f, 400f).forEach { contentW ->
            assertEquals(0f, TableLayoutProvider.cellAnchorTargetLeft(contentW, 300, tableIsRtl = false), 0f)
            assertEquals(300f - contentW, TableLayoutProvider.cellAnchorTargetLeft(contentW, 300, tableIsRtl = true), 0f)
        }
    }
}
