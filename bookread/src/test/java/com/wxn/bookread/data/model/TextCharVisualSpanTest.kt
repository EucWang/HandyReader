package com.wxn.bookread.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R1 visualSpan 单元测试（主方案 U1-U7 + U8）。
 *
 * 背景：RTL 行 textChars 按逻辑序（视觉右→左）追加，数组首尾 ≠ 视觉左右边，
 * 跨字符区间矩形必须 min(start)/max(end)。U1 锁定 LTR 等价性（零回归），
 * U2-U3 锁定 RTL/混合正确性，U4-U8 锁定边界。
 *
 * 运行：`./gradlew :bookread:testDebugUnitTest --tests "*TextCharVisualSpanTest*"`
 */
class TextCharVisualSpanTest {

    private fun tc(start: Float, end: Float, group: Int = 0) =
        TextChar("x", start, end, false, group)

    // U1：LTR 升序 → 与"首尾取值"等价（零回归承诺）
    @Test
    fun u1_ltrAscending_equalsFirstLast() {
        val chars = listOf(tc(10f, 20f), tc(20f, 30f), tc(30f, 40f))
        val span = chars.visualSpan()
        assertEquals(10f to 40f, span)
    }

    // U2：RTL 降序（模拟 placeCharsFromLayout 产出）→ min/max 归一，left < right
    @Test
    fun u2_rtlDescending_minMax() {
        val chars = listOf(tc(30f, 40f), tc(20f, 30f), tc(10f, 20f))
        val span = chars.visualSpan()
        assertEquals(10f to 40f, span)
    }

    // U3：混合方向共享行（RTL 段 + LTR 段拼接，x 非单调）→ 全局 min/max
    @Test
    fun u3_mixedBlocks_globalMinMax() {
        val chars = listOf(tc(50f, 60f), tc(40f, 50f), tc(0f, 15f), tc(15f, 30f))
        val span = chars.visualSpan()
        assertEquals(0f to 60f, span)
    }

    // U4：空列表 → null
    @Test
    fun u4_empty_null() {
        assertNull(emptyList<TextChar>().visualSpan())
    }

    // U5：谓词无命中 → null（各消费端 ?: continue / ?: return 依赖此契约）
    @Test
    fun u5_noMatch_null() {
        val chars = listOf(tc(10f, 20f), tc(20f, 30f))
        assertNull(chars.visualSpan { false })
    }

    // U6：单字符 → (start, end)
    @Test
    fun u6_singleChar() {
        assertEquals(5f to 9f, listOf(tc(5f, 9f)).visualSpan())
    }

    // U7：区间谓词只对命中段取 min/max（PageView tag 命中用法）
    @Test
    fun u7_subRangePredicate() {
        val chars = listOf(tc(30f, 40f), tc(20f, 30f), tc(10f, 20f))
        val span = chars.visualSpan { it in 0..1 }
        assertEquals(20f to 40f, span)
    }

    // U8：图片字符（isImage=true）参与默认跨度（笔记底色含行内图的行）
    @Test
    fun u8_imageCharIncludedInDefaultSpan() {
        val chars = listOf(
            TextChar("img.png", 10f, 60f, isImage = true),
            tc(60f, 70f)
        )
        assertEquals(10f to 70f, chars.visualSpan())
    }
}
