package com.wxn.bookread.provider

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 表格边框主题感知派生色验算（方案 2026-09-02-plan-table-border-theme-color §4）。
 * 契约：保留输入 RGB、alpha 重置为 BORDER_ALPHA（0x66）——透明输入防御后仍可辨。
 */
class TableBorderColorTest {

    @Test fun `深色主题近白文字色 RGB 保留`() {
        val c = TableRenderProvider.borderColorFor(0xFFFEFEFE.toInt())
        assertEquals(0xFEFEFE, c and 0x00FFFFFF)
        assertEquals(TableRenderProvider.BORDER_ALPHA, c ushr 24)
    }

    @Test fun `浅色主题近黑文字色 RGB 保留`() {
        val c = TableRenderProvider.borderColorFor(0xFF121212.toInt())
        assertEquals(0x121212, c and 0x00FFFFFF)
        assertEquals(TableRenderProvider.BORDER_ALPHA, c ushr 24)
    }

    @Test fun `自定义主题褐色文字色 RGB 保留`() {
        val c = TableRenderProvider.borderColorFor(0xFF5C4A3A.toInt())
        assertEquals(0x5C4A3A, c and 0x00FFFFFF)
        assertEquals(TableRenderProvider.BORDER_ALPHA, c ushr 24)
    }

    @Test fun `透明输入防御 alpha 重置`() {
        val c = TableRenderProvider.borderColorFor(0x00121212)
        assertEquals(0x121212, c and 0x00FFFFFF)
        assertEquals(TableRenderProvider.BORDER_ALPHA, c ushr 24)
        assertEquals(0x66121212, c)
    }
}
