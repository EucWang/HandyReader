package com.wxn.bookread.provider

import com.wxn.bookread.data.model.TextChar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 单元格共享行推回数学 JVM 直测（方案 §7.1 文件 3）。
 * cellShiftRunBlock 与正文 shiftRunLineToCursor（TextLayoutProvider L521-551）同源。
 * TextChar 为纯数据类（无 Android 依赖），JVM 可构造。
 */
class TableCellShiftRunBlockTest {

    private fun boxes(vararg spans: Pair<Float, Float>) =
        ArrayList(spans.map { TextChar("x", it.first, it.second) })

    @Test
    fun ltr_block_shifts_min_to_cursor() {
        val chars = boxes(100f to 180f)
        val (min, max) = TableLayoutProvider.cellShiftRunBlock(chars, 0, lineIsRtl = false, cursor = 50f)
        assertEquals(50f, chars[0].start, 0f)
        assertEquals(130f, chars[0].end, 0f)      // 宽度不变
        assertEquals(50f, min, 0f)
        assertEquals(130f, max, 0f)
    }

    @Test
    fun rtl_block_shifts_max_to_cursor() {
        val chars = boxes(100f to 180f)
        val (min, max) = TableLayoutProvider.cellShiftRunBlock(chars, 0, lineIsRtl = true, cursor = 260f)
        assertEquals(180f, chars[0].start, 0f)
        assertEquals(260f, chars[0].end, 0f)      // 近端 = 块右缘贴 cursor
        assertEquals(180f, min, 0f)
        assertEquals(260f, max, 0f)
    }

    @Test
    fun empty_block_returns_cursor_and_touches_nothing() {
        val chars = boxes(0f to 10f)
        val r = TableLayoutProvider.cellShiftRunBlock(chars, 1, lineIsRtl = false, cursor = 7f)
        assertEquals(7f, r.first, 0f)
        assertEquals(7f, r.second, 0f)
        assertEquals(0f, chars[0].start, 0f)      // 既有盒不动
    }

    @Test
    fun zero_shift_keeps_coordinates() {
        val chars = boxes(50f to 130f)
        TableLayoutProvider.cellShiftRunBlock(chars, 0, lineIsRtl = false, cursor = 50f)
        assertEquals(50f, chars[0].start, 0f)
        assertEquals(130f, chars[0].end, 0f)
    }

    /** 共享行追加不变量：fromIndex 只含新块 → 旧行块坐标不动，仅新块平移 */
    @Test
    fun appended_block_only_moves_new_chars() {
        val chars = boxes(0f to 40f, 100f to 180f)
        TableLayoutProvider.cellShiftRunBlock(chars, 1, lineIsRtl = false, cursor = 40f)
        assertEquals(0f, chars[0].start, 0f)      // 旧行块不动
        assertEquals(40f, chars[1].start, 0f)     // 新块近端贴 cursor
        assertEquals(120f, chars[1].end, 0f)
    }
}
