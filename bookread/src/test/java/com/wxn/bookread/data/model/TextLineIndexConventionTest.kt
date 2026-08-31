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

    // ── M3：emoji（代理对）行 UTF-16 口径（方案 §5.1 增 4 例） ──

    @Test
    fun emojiLine_utf16UnitAdvance() {
        // "a😀b"：a=1 码元，😀=2 码元，b=1 码元；line.text.length == 4
        val line = TextLine(text = "a😀b").apply {
            textChars.add(textChar("a"))
            textChars.add(textChar("😀"))
            textChars.add(textChar("b"))
        }
        // textCharCount 保持码点计数语义（§2 契约钉，防未来被误当文本上界）
        assertEquals(3, line.textCharCount())
        // 数组 → 文本：emoji 前下标 +0，emoji 后下标 +2（★ M3 核心换算）
        assertEquals(0, line.textIndexAt(0))   // a 前
        assertEquals(1, line.textIndexAt(1))   // 😀 前（a 占 1 码元）
        assertEquals(3, line.textIndexAt(2))   // b 前（a+😀 = 3 码元）
        assertEquals(4, line.textIndexAt(3))   // 尾越界 → 总码元长
        // 文本 → 数组：起始码元位命中
        assertEquals(0, line.arrayIndexAt(0))  // 码元 0 → a
        assertEquals(1, line.arrayIndexAt(1))  // 码元 1 → 😀（起始命中）
        assertEquals(2, line.arrayIndexAt(3))  // 码元 3 → b
        assertEquals(3, line.arrayIndexAt(4))  // 码元 4（行末）→ size 兜底
    }

    @Test
    fun imageAndEmoji_pathNotCounted_rulesStack() {
        // [IMG(路径串)][😀][b]：图占数组位不占文本位 + 代理对计长，双规则叠加；
        // 图片 charData 用长路径 fixture——若 isImage 守卫失效，路径长度会泄入码元和
        val line = TextLine(text = "😀b").apply {
            textChars.add(imageChar("file://very/long/path.png"))
            textChars.add(textChar("😀"))
            textChars.add(textChar("b"))
        }
        assertEquals(2, line.textCharCount())
        assertEquals(0, line.textIndexAt(0))   // 图位 → 0
        assertEquals(0, line.textIndexAt(1))   // 😀 前：图不计长
        assertEquals(2, line.textIndexAt(2))   // b 前：😀 计 2 码元
        assertEquals(3, line.textIndexAt(3))   // 尾
        assertEquals(1, line.arrayIndexAt(0))  // 码元 0 → 😀（跳过图）
        assertEquals(2, line.arrayIndexAt(2))  // 码元 2 → b
        assertEquals(3, line.arrayIndexAt(3))  // 越界 → size
    }

    @Test
    fun endExclusiveUtf16_boundaries() {
        // 行尾 emoji：右边界 = 行文本长，不切代理对（§3.3 selectText 截取核心）
        val line = TextLine(text = "a😀").apply {
            textChars.add(textChar("a"))
            textChars.add(textChar("😀"))
        }
        assertEquals(1, line.endExclusiveUtf16(0))    // a 右边界 = eC+1（无 emoji 等价）
        assertEquals(3, line.endExclusiveUtf16(1))    // 😀 起始 → 右边界 3 = 行长
        assertEquals(3, line.endExclusiveUtf16(3))    // eC = 行长 → 兜底行长
        assertEquals(3, line.endExclusiveUtf16(99))   // 越界钳制 → 行长
        // 无 emoji 行逐位等价 eC + 1（零回归钉）
        val plain = TextLine(text = "abc").apply {
            "abc".forEach { textChars.add(textChar(it.toString())) }
        }
        assertEquals(1, plain.endExclusiveUtf16(0))
        assertEquals(2, plain.endExclusiveUtf16(1))
        assertEquals(3, plain.endExclusiveUtf16(2))
    }

    @Test
    fun arrayIndexAt_midSurrogate_roundsUp() {
        // R10 契约：中位代理对（陈旧 locator 的 mid-pair 值）→ 该字符之后一个字符位
        val line = TextLine(text = "a😀b").apply {
            textChars.add(textChar("a"))
            textChars.add(textChar("😀"))
            textChars.add(textChar("b"))
        }
        // 😀 占码元 [1,3)：码元 2 = 代理对中位 → 向上取整 = b 的数组位
        assertEquals(2, line.arrayIndexAt(2))
        // 码元 3 = b 起始（合法位）→ 同为 b，与中位取整结果一致
        assertEquals(2, line.arrayIndexAt(3))
    }
}
