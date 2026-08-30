package com.wxn.bookread.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M2-③ 统一坐标约定回归（docs/plans/2026-08-28-plan-unify-ltr-to-layoutNormalTextRtl.md §3 Phase1-3）：
 *
 * 行内两种下标口径——
 *  - 数组口径：textChars 下标（图片 TextChar 占位，renderGroup=0）
 *  - 文本口径：line.text 的 String 下标（图片不占位）= charStartOffset 相加后与标签/选区/inlineStyle
 *    匹配的口径（ContentTextView 消费端统一走 textIndexAt/arrayIndexAt 换算）
 *
 * 纯 JVM 测试：TextLine/TextChar 为纯数据类，不触 Android API。
 */
class TextLineIndexConventionTest {

    private fun textChar(data: String) = TextChar(data, start = 0f, end = 0f)
    private fun imageChar(src: String = "file://img.png") = TextChar(src, start = 0f, end = 0f, isImage = true)

    // ── 无图行：两种口径恒等（旧行为零回归） ──

    @Test
    fun imagelessLine_twoIndexSpacesIdentical() {
        val line = TextLine(text = "hello").apply {
            "hello".forEach { textChars.add(textChar(it.toString())) }
        }
        assertEquals(5, line.textCharCount())
        for (i in 0..5) {
            assertEquals("数组下标 $i → 文本下标应恒等", i, line.textIndexAt(i))
            assertEquals("文本下标 $i → 数组下标应恒等", i, line.arrayIndexAt(i))
        }
    }

    // ── 行首图片：[img][h][e][l][l][o] ──

    @Test
    fun leadingImage_textIndexSkipsImageSlots() {
        val line = TextLine(text = "hello").apply {
            textChars.add(imageChar())
            "hello".forEach { textChars.add(textChar(it.toString())) }
        }
        assertEquals(5, line.textCharCount())
        // 数组 0 是图片 → 文本 0
        assertEquals(0, line.textIndexAt(0))
        // 数组 1 是 'h' → 文本 0；数组 5 是 'o' → 文本 4；数组 6 越尾 → 文本总数 5
        assertEquals(0, line.textIndexAt(1))
        assertEquals(4, line.textIndexAt(5))
        assertEquals(5, line.textIndexAt(6))
        // 文本 0 → 数组 1（第一个非图片位）；文本 4 → 数组 5；文本末尾越界位 → textChars.size
        assertEquals(1, line.arrayIndexAt(0))
        assertEquals(5, line.arrayIndexAt(4))
        assertEquals(6, line.arrayIndexAt(5))
    }

    // ── 行中图片（M2-③ 主场景：图文同行，图后文本不漂移） ──

    @Test
    fun middleImage_textAfterImageNotShifted() {
        val line = TextLine(text = "BeforeAfter").apply {
            "Before".forEach { textChars.add(textChar(it.toString())) }
            textChars.add(imageChar())
            "After".forEach { textChars.add(textChar(it.toString())) }
        }
        assertEquals(11, line.textCharCount())
        // 'B'=数组0/文本0 … 'e'=数组5/文本5；图片=数组6（文本位 6）；'A'=数组7 → 文本 6
        assertEquals(5, line.textIndexAt(5))
        assertEquals(6, line.textIndexAt(6))   // 图片位本身 = 前方文本数
        assertEquals(6, line.textIndexAt(7))   // 图后首字符不 +1（缺陷修复断言）
        assertEquals(10, line.textIndexAt(11)) // 末字符
        assertEquals(7, line.arrayIndexAt(6))  // 反向：文本6 → 图后首字符
    }

    // ── 多图 + 全图行 ──

    @Test
    fun multipleImages_countAllSkipped() {
        val line = TextLine(text = "abc").apply {
            textChars.add(imageChar())
            "ab".forEach { textChars.add(textChar(it.toString())) }
            textChars.add(imageChar("file://b.png"))
            textChars.add(textChar("c"))
        }
        assertEquals(3, line.textCharCount())
        assertEquals(0, line.textIndexAt(0)) // 图
        assertEquals(0, line.textIndexAt(1)) // a
        assertEquals(2, line.textIndexAt(3)) // 第二张图
        assertEquals(2, line.textIndexAt(4)) // c
        assertEquals(1, line.arrayIndexAt(0))
        assertEquals(4, line.arrayIndexAt(2))
        assertEquals(5, line.arrayIndexAt(3)) // 越界 → size
    }

    @Test
    fun allImageLine_textCountZero_arrayIndexClamped() {
        val line = TextLine(text = "").apply {
            textChars.add(imageChar())
            textChars.add(imageChar("file://b.png"))
        }
        assertEquals(0, line.textCharCount())
        assertEquals(0, line.textIndexAt(2))
        assertEquals(2, line.arrayIndexAt(0)) // 无文本位 → size
    }

    // ── 越界钳制 ──

    @Test
    fun outOfRange_clamped() {
        val line = TextLine(text = "ab").apply {
            "ab".forEach { textChars.add(textChar(it.toString())) }
        }
        assertEquals(0, line.textIndexAt(-1))
        assertEquals(0, line.textIndexAt(0))
        assertEquals(2, line.textIndexAt(99))
        assertEquals(0, line.arrayIndexAt(-1))
        assertEquals(2, line.arrayIndexAt(99))
    }
}
