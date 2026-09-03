package com.wxn.bookread.provider

import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TextSelectionHandler JVM 直测（方案 2026-09-02-plan-table-select-hit-2d.md R3 §4）。
 * 双根因锁：RC1 跨格命中（Y 带内继续扫描、无早退）/ RC2 词边界单元格收敛；
 * 决策点①=B 口径锁：字符间隙/行尾空白/跨格间隙均严格无命中。
 * TextLine/TextChar 纯数据类直构，无 Android 依赖（与 TableGeometryTest 同款 JVM 约束）。
 */
class TextSelectionHandlerTest {

    /** 表格单元格行：默认共享 Y 带 top=100/bottom=160 */
    private fun cell(
        col: Int, row: Int = 0, para: Int = 7,
        boxes: List<Pair<Float, Float>>, top: Float = 100f, bottom: Float = 160f
    ): TextLine = TextLine(paragraphIndex = para, isTableCell = true, rowIndex = row, colIndex = col).apply {
        lineTop = top; lineBottom = bottom
        boxes.forEach { (s, e) -> textChars.add(TextChar("x", s, e)) }
    }

    /** 正文行：默认 Y 带 top=200/bottom=260 */
    private fun body(
        para: Int = 3, boxes: List<Pair<Float, Float>>, top: Float = 200f, bottom: Float = 260f
    ): TextLine = TextLine(paragraphIndex = para).apply {
        lineTop = top; lineBottom = bottom
        boxes.forEach { (s, e) -> textChars.add(TextChar("x", s, e)) }
    }

    /** 方案 §4 主夹具：两格共享 Y 带（盒 [100,300]/[500,700]）+ 正文行（盒 [100,140]/[160,200]） */
    private fun pageLines() = arrayListOf(
        cell(col = 0, boxes = listOf(100f to 300f)),          // line 0
        cell(col = 1, boxes = listOf(500f to 700f)),          // line 1
        body(boxes = listOf(100f to 140f, 160f to 200f)),     // line 2
    )

    // ---------- RC1：跨格命中（Y 带内继续扫描，无早退） ----------

    @Test fun `跨格命中第二格`() {
        assertEquals(1 to 0, TextSelectionHandler.findTextPositionAt(pageLines(), 600f, 130f, 0f))
    }

    @Test fun `跨格命中第一格`() {
        assertEquals(0 to 0, TextSelectionHandler.findTextPositionAt(pageLines(), 250f, 130f, 0f))
    }

    @Test fun `跨格间隙触摸严格无命中`() {
        assertNull(TextSelectionHandler.findTextPositionAt(pageLines(), 400f, 130f, 0f))
    }

    @Test fun `行内字符间隙不吸附`() {
        assertNull(TextSelectionHandler.findTextPositionAt(pageLines(), 150f, 230f, 0f))
    }

    @Test fun `行内字符严格命中`() {
        assertEquals(2 to 1, TextSelectionHandler.findTextPositionAt(pageLines(), 170f, 230f, 0f))
    }

    @Test fun `正文行命中不受表格行干扰`() {
        assertEquals(2 to 0, TextSelectionHandler.findTextPositionAt(pageLines(), 130f, 230f, 0f))
    }

    @Test fun `正文行尾外空白无响应`() {
        assertNull(TextSelectionHandler.findTextPositionAt(pageLines(), 900f, 230f, 0f))
    }

    /** 早退根源锁：三格同 Y 带，首格 X 未中不得阻断第三格命中（现状 RC1 此处返回 null） */
    @Test fun `多行Y重叠首行X未中继续扫描命中第三格`() {
        val lines = arrayListOf(
            cell(col = 0, boxes = listOf(100f to 200f)),
            cell(col = 1, boxes = listOf(300f to 400f)),
            cell(col = 2, boxes = listOf(500f to 600f)),
        )
        assertEquals(2 to 0, TextSelectionHandler.findTextPositionAt(lines, 550f, 130f, 0f))
    }

    /** 边框行（isLine）与空单元格不参与命中：即使边框行字符盒覆盖触点也跳过 */
    @Test fun `空单元格与边框行不参与命中`() {
        val border = TextLine(isLine = true).apply {
            lineTop = 100f; lineBottom = 160f
            textChars.add(TextChar("x", 100f, 300f))
        }
        val empty = cell(col = 0, boxes = emptyList())
        val real = cell(col = 1, boxes = listOf(500f to 700f))
        val lines = arrayListOf(border, empty, real)
        assertNull(TextSelectionHandler.findTextPositionAt(lines, 200f, 130f, 0f))
        assertEquals(2 to 0, TextSelectionHandler.findTextPositionAt(lines, 600f, 130f, 0f))
    }

    /** 图片字符不参与命中：触点落在图片盒内 → null（无响应口径） */
    @Test fun `图片字符不参与命中`() {
        val withImage = cell(col = 0, boxes = emptyList()).apply {
            textChars.add(TextChar("", 100f, 300f, isImage = true))
        }
        val lines = arrayListOf(withImage, cell(col = 1, boxes = listOf(500f to 700f)))
        assertNull(TextSelectionHandler.findTextPositionAt(lines, 200f, 130f, 0f))
    }

    /** yOffset 口径：分页三页滚动场景，行坐标加页偏移后参与 Y 比较 */
    @Test fun `yOffset参与Y带比较`() {
        assertNull(TextSelectionHandler.findTextPositionAt(pageLines(), 250f, 130f, 500f))
        assertEquals(0 to 0, TextSelectionHandler.findTextPositionAt(pageLines(), 250f, 630f, 500f))
    }

    // ---------- RC2：词边界分组（单元格收敛） ----------

    @Test fun `词组-正文同段相组与表格行相斥`() {
        val body1 = body(para = 3, boxes = listOf(100f to 140f))
        val body2 = body(para = 3, boxes = listOf(160f to 200f), top = 260f, bottom = 320f)
        val tableRow = cell(col = 0, para = 3, boxes = listOf(100f to 300f))
        assertTrue(TextSelectionHandler.sameWordGroup(body1, body2))
        assertFalse(TextSelectionHandler.sameWordGroup(body1, tableRow))
        val otherPara = body(para = 4, boxes = listOf(100f to 140f))
        assertFalse(TextSelectionHandler.sameWordGroup(body1, otherPara))
    }

    @Test fun `词组-同单元格折行连续`() {
        val pressed = cell(col = 1, para = 7, boxes = listOf(500f to 700f))
        val wrapped = cell(col = 1, para = 7, boxes = listOf(500f to 700f), top = 160f, bottom = 220f)
        assertTrue(TextSelectionHandler.sameWordGroup(pressed, wrapped))
    }

    @Test fun `词组-跨单元格不连词`() {
        val pressed = cell(col = 0, row = 0, para = 7, boxes = listOf(100f to 300f))
        val otherCol = cell(col = 1, row = 0, para = 7, boxes = listOf(500f to 700f))
        val otherRow = cell(col = 0, row = 1, para = 7, boxes = listOf(100f to 300f), top = 160f, bottom = 220f)
        assertFalse(TextSelectionHandler.sameWordGroup(pressed, otherCol))
        assertFalse(TextSelectionHandler.sameWordGroup(pressed, otherRow))
    }
}
