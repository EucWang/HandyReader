package com.wxn.bookread.provider

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 边框线段坐标数值不变式（方案 §5.3 / 任务 A1.2）。
 * 纯 LTR 输入下坐标必须与 legacy 内联边框公式数值一致（迁移等价红线）；
 * RTL 输入验证竖线镜像与哨兵（左边界不镜像）。TextLine 为纯数据类，JVM 可构造。
 */
class TableRenderProviderBorderTest {
    // bounds：startX=20, endX=420, width=400（FULL）
    private val bounds = LayoutBounds(width = 400, startX = 20, endX = 420, role = ColumnRole.FULL)

    @Test fun `LTR 两列表边框坐标与 legacy 公式一致`() {
        val lines = TableRenderProvider.buildRowBorders(
            bounds, 400f, 0f, 0f, listOf(50, 50),
            isFirstLogicLine = true, isLastTableRow = false, isLastLogicLine = true,
            rowTopY = 100f, rowBoxHeight = 40f, tableIsRtl = false
        )
        assertEquals(4, lines.size)   // 顶线 + 左/中/右竖线（无底线：非末行）
        val top = lines[0]
        assertEquals(20f, top.lineStart.first, 0.01f); assertEquals(100f, top.lineStart.second, 0.01f)
        assertEquals(420f, top.lineEnd.first, 0.01f)
        val left = lines[1];  assertEquals(20f, left.lineStart.first, 0.01f)   // 左边界
        val mid = lines[2];   assertEquals(220f, mid.lineStart.first, 0.01f)   // 20 + 400*50%
        val right = lines[3]; assertEquals(420f, right.lineStart.first, 0.01f)
        assertEquals(140f, right.lineEnd.second, 0.01f)                        // 100 + 40
    }

    @Test fun `RTL 两列表竖线镜像且边界不变`() {
        val lines = TableRenderProvider.buildRowBorders(
            bounds, 400f, 0f, 0f, listOf(30, 70),
            isFirstLogicLine = true, isLastTableRow = true, isLastLogicLine = true,
            rowTopY = 0f, rowBoxHeight = 30f, tableIsRtl = true
        )
        assertEquals(5, lines.size)   // 顶线 + 3 竖线 + 底线（末表行）
        assertEquals(300f, lines[2].lineStart.first, 0.01f)   // 内部线 30% → 镜像 70%：20+400×0.7=300
        assertEquals(20f, lines[1].lineStart.first, 0.01f)    // 左边界不镜像（哨兵）
        val bottom = lines.last()
        assertEquals(30f, bottom.lineStart.second, 0.01f)     // 底线 y = rowTopY + rowBoxHeight
    }

    @Test fun `非首逻辑行无顶线`() {
        val lines = TableRenderProvider.buildRowBorders(
            bounds, 400f, 0f, 0f, listOf(100),
            isFirstLogicLine = false, isLastTableRow = false, isLastLogicLine = false,
            rowTopY = 0f, rowBoxHeight = 30f, tableIsRtl = false
        )
        assertEquals(2, lines.size)   // 仅左右边界竖线
    }

    @Test fun `非零 margin 的边界坐标`() {
        // bounds 20..420，margin 左 10 / 右 20 → 内容区 [30, 400]；fullWidth=360（由调用方算好传入）
        val lines = TableRenderProvider.buildRowBorders(
            bounds, 360f, 10f, 20f, listOf(50, 50),
            isFirstLogicLine = true, isLastTableRow = false, isLastLogicLine = true,
            rowTopY = 0f, rowBoxHeight = 30f, tableIsRtl = false
        )
        assertEquals(4, lines.size)
        assertEquals(30f, lines[1].lineStart.first, 0.01f)    // 左边界 = 20 + 10
        assertEquals(210f, lines[2].lineStart.first, 0.01f)   // 内部线 = 30 + 360×50%
        assertEquals(400f, lines[3].lineStart.first, 0.01f)   // 右边界 = 420 − 20
        assertEquals(30f, lines[0].lineStart.first, 0.01f)    // 顶线起点 = 内容区左缘
        assertEquals(400f, lines[0].lineEnd.first, 0.01f)     // 顶线终点 = 内容区右缘
    }

    @Test fun `末表行多逻辑行-中间逻辑行无底线`() {
        // 表格最后一行折成多个逻辑行：非末逻辑行不得出底线（底线条件 = isLastTableRow && isLastLogicLine）
        val mid = TableRenderProvider.buildRowBorders(
            bounds, 400f, 0f, 0f, listOf(50, 50),
            isFirstLogicLine = false, isLastTableRow = true, isLastLogicLine = false,
            rowTopY = 0f, rowBoxHeight = 30f, tableIsRtl = false
        )
        assertEquals("中间逻辑行：仅 3 竖线（无顶线无底线）", 3, mid.size)
        val last = TableRenderProvider.buildRowBorders(
            bounds, 400f, 0f, 0f, listOf(50, 50),
            isFirstLogicLine = false, isLastTableRow = true, isLastLogicLine = true,
            rowTopY = 0f, rowBoxHeight = 30f, tableIsRtl = false
        )
        assertEquals("末逻辑行：3 竖线 + 底线", 4, last.size)
    }

    @Test fun `边框行Y带为几何真实值-横线`() {
        // F1（2026-09-03-plan-table-note-mark-fixes.md §2.1）：lineTop/Bottom 缺省 0 时
        // getLineMargin 把邻行 margin 记成行 Y → 笔记/朗读/搜索背景竖条延伸。旧码此断言必红。
        val lines = TableRenderProvider.buildRowBorders(
            bounds, 400f, 0f, 0f, listOf(50, 50),
            isFirstLogicLine = true, isLastTableRow = true, isLastLogicLine = true,
            rowTopY = 100f, rowBoxHeight = 40f, tableIsRtl = false
        )
        val top = lines.first()
        assertEquals("顶横线 lineTop = y", 100f, top.lineTop, 0.01f)
        assertEquals("顶横线 lineBottom = y（退化带）", 100f, top.lineBottom, 0.01f)
        assertEquals("顶横线 Y 带 = 线几何 Y", top.lineStart.second, top.lineTop, 0.01f)
        val bottom = lines.last()
        assertEquals("底横线 lineTop = rowTopY + rowBoxHeight", 140f, bottom.lineTop, 0.01f)
        assertEquals("底横线 lineBottom 同（退化带）", 140f, bottom.lineBottom, 0.01f)
    }

    @Test fun `边框行Y带为几何真实值-竖线`() {
        // F1：竖线 Y 带 = 所在逻辑行带 [rowTopY, rowTopY + rowBoxHeight]
        val lines = TableRenderProvider.buildRowBorders(
            bounds, 400f, 0f, 0f, listOf(50, 50),
            isFirstLogicLine = true, isLastTableRow = true, isLastLogicLine = true,
            rowTopY = 100f, rowBoxHeight = 40f, tableIsRtl = false
        )
        val verticals = lines.filter { it.lineStart.first == it.lineEnd.first }
        assertEquals("顶线 + 3 竖线 + 底线中的竖线数", 3, verticals.size)
        verticals.forEach {
            assertEquals("竖线 lineTop = rowTopY", 100f, it.lineTop, 0.01f)
            assertEquals("竖线 lineBottom = rowTopY + rowBoxHeight", 140f, it.lineBottom, 0.01f)
        }
    }
}
