package com.wxn.bookread.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单元格共享行回退判定 JVM 直测（方案 §7.1 文件 2）。
 * cellLine0MidWord 与正文 TextLayoutProvider L230-232 同源；cellSharedLineShouldFallback 与 L233-245 同源。
 */
class TableCellSharedLineFallbackTest {

    // ── cellLine0MidWord ──

    @Test
    fun midword_break_latin() {
        assertTrue(TableLayoutProvider.cellLine0MidWord("HelloWorld", 5))    // o|W 均词字符
    }

    @Test
    fun word_boundary_not_midword() {
        assertFalse(TableLayoutProvider.cellLine0MidWord("Hello World", 6))  // o 后是空格
    }

    @Test
    fun cjk_boundary_is_legal_break() {
        assertFalse(TableLayoutProvider.cellLine0MidWord("中文abc", 2))       // CJK 间是合法断点
    }

    @Test
    fun end_of_text_not_midword() {
        assertFalse(TableLayoutProvider.cellLine0MidWord("Hello", 5))        // 无下一字符
    }

    @Test
    fun empty_line0_end_not_midword() {
        assertFalse(TableLayoutProvider.cellLine0MidWord("", 0))
    }

    @Test
    fun arabic_word_break_is_midword() {
        assertTrue(TableLayoutProvider.cellLine0MidWord("عمرها", 3))         // 连写词被拆断
    }

    // ── cellSharedLineShouldFallback ──

    @Test
    fun non_shared_line_never_fallback() {
        assertFalse(TableLayoutProvider.cellSharedLineShouldFallback(false, 999f, 10, true))
    }

    @Test
    fun width_overflow_fallback() {
        assertTrue(TableLayoutProvider.cellSharedLineShouldFallback(true, 102f, 100, false))
    }

    @Test
    fun within_tolerance_no_fallback() {
        assertFalse(TableLayoutProvider.cellSharedLineShouldFallback(true, 100.5f, 100, false))
    }

    @Test
    fun midword_alone_triggers_fallback() {
        assertTrue(TableLayoutProvider.cellSharedLineShouldFallback(true, 100f, 100, true))
    }
}
