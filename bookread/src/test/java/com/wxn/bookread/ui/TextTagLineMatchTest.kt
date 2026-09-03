package com.wxn.bookread.ui

import com.wxn.base.bean.TextTag
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2（2026-09-03-plan-table-note-mark-fixes.md §2.2）：getPagesAnnotation 行-[tag] 匹配谓词
 * 必须为半开区间严格相交（TextTag [start, end) 契约）。旧闭区间谓词的端点误命中：
 * 行 [19,24) 命中相邻 tag [24,29)（نهاية 案例，用例 1/7/8 锁定）。
 */
class TextTagLineMatchTest {

    private fun tag(start: Int, end: Int) =
        TextTag(uuid = "t", name = "highlight", start = start, end = end)

    @Test fun `f2_行终点等于tag起点不命中`() {
        // نهاية 案例锁：行 [19,24)，tag [24,29)；旧谓词 lineEnd in tag.start..tag.end 闭区间误命中
        assertFalse(textTagAffectsLine(tag(24, 29), 19, 24))
    }

    @Test fun `f2_行起点等于tag终点不命中`() {
        // 回归守卫：审查 R1 复算旧谓词对该场景本就不命中（新旧一致，非红-旧锁）
        assertFalse(textTagAffectsLine(tag(10, 19), 19, 30))
    }

    @Test fun `f2_部分重叠命中`() {
        assertTrue(textTagAffectsLine(tag(10, 40), 30, 45))
    }

    @Test fun `f2_行含于tag命中`() {
        assertTrue(textTagAffectsLine(tag(10, 40), 15, 20))
    }

    @Test fun `f2_tag含行命中`() {
        assertTrue(textTagAffectsLine(tag(0, 50), 10, 20))
    }

    @Test fun `f2_无重叠不命中`() {
        assertFalse(textTagAffectsLine(tag(0, 10), 20, 30))
    }

    @Test fun `f2_跨行高亮逐行判定`() {
        // 多行高亮 tag [15,40)：首行 [0,15) 相邻不再过绘，中两行真实重叠全命中
        assertFalse(textTagAffectsLine(tag(15, 40), 0, 15))
        assertTrue(textTagAffectsLine(tag(15, 40), 15, 30))
        assertTrue(textTagAffectsLine(tag(15, 40), 30, 45))
    }

    @Test fun `f2_零长tag不命中`() {
        // 旧谓词 lineEnd in tag.start..tag.end 对 [24..24] 闭区间误命中
        assertFalse(textTagAffectsLine(tag(24, 24), 19, 24))
    }
}
