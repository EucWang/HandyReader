package com.wxn.bookread.provider

import com.wxn.bookread.data.beans.LineBlockRecord
import com.wxn.bookread.data.model.TextChar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D+ 粘合段重锚纯几何单测（docs/plans/2026-08-31-plan-u5-route-dplus-glued-span-line-assembly.md §W4-a）。
 *
 * 被测：[TextLayoutProvider.reorderGluedSpans]（object 成员、纯 TextChar 几何，无 Android 触碰 → JVM
 * 直测，与 distributeJustifyChars JVM 直测同一前提：后续向 object 添加 Android 初始化器会破坏本测试）。
 *
 * 构造口径：名义左打包（引擎 shiftRunLineToCursor 之后、行关闭时的形态）——块按逻辑序（消费序）
 * 从 x 连续排布，每字符等宽 adv=10f；LineBlockRecord 记录各块在 textChars 中的区间与 bidi level。
 * 断言要素：组序（min(start) 升序）、块内字符宽度不变、槽位 [min,max] 外边界不变（anchorLine 正交前提）。
 *
 * 用例 7（RTL 基调不启用重锚）为集成态断言 → MixedBaseLtrLogicalOrderInstrumentedTest
 * .rtlParagraph_zeroRegressionPin。
 */
class GluedSpanReorderTest {

    /** n 个字符、bidi level = level 的块 */
    private data class B(val n: Int, val level: Int)

    /** 名义左打包构造：块序列 → (chars, records) */
    private fun line(vararg blocks: B, adv: Float = 10f): Pair<ArrayList<TextChar>, List<LineBlockRecord>> {
        val chars = ArrayList<TextChar>()
        val records = ArrayList<LineBlockRecord>()
        var x = 0f
        for ((bi, b) in blocks.withIndex()) {
            val start = chars.size
            repeat(b.n) {
                chars.add(TextChar("c", start = x, end = x + adv, renderGroup = bi + 1))
                x += adv
            }
            records.add(LineBlockRecord(start, chars.size, b.level))
        }
        return chars to records
    }

    private fun minStart(chars: List<TextChar>, r: LineBlockRecord) =
        (r.charStart until r.charEnd).minOf { chars[it].start }

    private fun maxEnd(chars: List<TextChar>, r: LineBlockRecord) =
        (r.charStart until r.charEnd).maxOf { chars[it].end }

    private fun coords(chars: List<TextChar>) = chars.map { it.start to it.end }

    private fun assertBlockUnmoved(before: List<Pair<Float, Float>>, chars: List<TextChar>, r: LineBlockRecord) {
        for (k in r.charStart until r.charEnd) {
            assertEquals("char[$k].start", before[k].first, chars[k].start, 1e-4f)
            assertEquals("char[$k].end", before[k].second, chars[k].end, 1e-4f)
        }
    }

    /** 重锚不变量：块内字符宽度保持；全行 [min,max] 外边界不变 */
    private fun assertWidthsAndSlot(before: List<Pair<Float, Float>>, after: List<TextChar>) {
        assertEquals(before.size, after.size)
        for (i in before.indices) {
            assertEquals(
                "char[$i] width", before[i].second - before[i].first,
                after[i].end - after[i].start, 1e-4f
            )
        }
        assertEquals("slot min", before.minOf { it.first }, after.minOf { it.start }, 1e-4f)
        assertEquals("slot max", before.maxOf { it.second }, after.maxOf { it.end }, 1e-4f)
    }

    // ── 用例 1：U7 主形态 [中0][AR1-1][300-2][AR2-1] ──
    @Test
    fun u7_coreShape_spanReversedRightToLeft() {
        val (chars, records) = line(B(2, 0), B(3, 1), B(3, 2), B(2, 1))
        val before = coords(chars)
        val spanMinBefore = (records[1].charStart until records[3].charEnd).minOf { chars[it].start }

        assertTrue(TextLayoutProvider.reorderGluedSpans(chars, records))
        assertWidthsAndSlot(before, chars)

        // 中文块（level-0 隔断块）逐位不变
        assertBlockUnmoved(before, chars, records[0])
        // 组内反序：视觉左→右 = [AR2][300][AR1]
        assertTrue(minStart(chars, records[3]) < minStart(chars, records[2]))
        assertTrue(minStart(chars, records[2]) < minStart(chars, records[1]))
        // 逻辑首块（AR1）贴槽右端 = 全行最右
        assertEquals(chars.maxOf { it.end }, maxEnd(chars, records[1]), 1e-4f)
        // 逻辑末块（AR2）贴槽左端（块连续无洞）
        assertEquals(spanMinBefore, minStart(chars, records[3]), 1e-4f)
    }

    // ── 用例 2：段尾共享 [AR尾-1][Eng-0] 回归钉（§4.3 段尾镜像免疫）──
    @Test
    fun tailArabicThenEng_noReorder() {
        val (chars, records) = line(B(3, 1), B(2, 0))
        val before = coords(chars)

        assertFalse(TextLayoutProvider.reorderGluedSpans(chars, records))
        // 逐位不变（Eng 是 level-0 隔断 → 名义左打包 = 平台真值，不可动）
        assertEquals(before, coords(chars))
    }

    // ── 用例 3：粘合段在行首 [AR1-1][300-2][AR2-1][中0] ──
    @Test
    fun spanAtLineStart_chineseTailUnaffected() {
        val (chars, records) = line(B(2, 1), B(2, 2), B(2, 1), B(2, 0))
        val before = coords(chars)

        assertTrue(TextLayoutProvider.reorderGluedSpans(chars, records))
        assertWidthsAndSlot(before, chars)

        // 中文尾块不变；重锚后 span 右缘恰接中文块左缘（槽右端语义）
        assertBlockUnmoved(before, chars, records[3])
        assertEquals(minStart(chars, records[3]), maxEnd(chars, records[0]), 1e-4f)
        // 组内反序 [AR2][300][AR1]
        assertTrue(minStart(chars, records[2]) < minStart(chars, records[1]))
        assertTrue(minStart(chars, records[1]) < minStart(chars, records[0]))
    }

    // ── 用例 4：纯 LTR 行恒 no-op ──
    @Test
    fun pureLtr_noop() {
        val (chars, records) = line(B(2, 0), B(3, 0), B(2, 0))
        val before = coords(chars)

        assertFalse(TextLayoutProvider.reorderGluedSpans(chars, records))
        assertEquals(before, coords(chars))
    }

    // ── 用例 5：双粘合段 [AR-1][中0][AR2-1][300-2][AR3-1]，仅多块段重锚 ──
    @Test
    fun twoSpans_onlyMultiBlockSpanReordered() {
        val (chars, records) = line(B(2, 1), B(2, 0), B(2, 1), B(2, 2), B(2, 1))
        val before = coords(chars)

        assertTrue(TextLayoutProvider.reorderGluedSpans(chars, records))
        assertWidthsAndSlot(before, chars)

        // 第一段单块 + 中文隔断块：逐位不变
        assertBlockUnmoved(before, chars, records[0])
        assertBlockUnmoved(before, chars, records[1])
        // 第二段组内反序 [AR3][300][AR2]
        assertTrue(minStart(chars, records[4]) < minStart(chars, records[3]))
        assertTrue(minStart(chars, records[3]) < minStart(chars, records[2]))
    }

    // ── 用例 6：空块记录防御（charStart==charEnd）：无 NaN/Inf、不消耗槽位 ──
    @Test
    fun emptyBlockRecord_defensiveNoOp() {
        val (chars, records0) = line(B(2, 1), B(2, 1))
        // 在两个 level-1 块之间插入空记录（引擎路径由 charsBaseStart 守卫保证非空，此处直调防御）
        val records = listOf(records0[0], LineBlockRecord(2, 2, 1), records0[1])
        val before = coords(chars)

        assertTrue(TextLayoutProvider.reorderGluedSpans(chars, records))
        // 空块被跳过且不消耗槽位：两块正常互换、坐标全部有限
        for (c in chars) {
            assertTrue("坐标应有限: ${c.start},${c.end}", c.start.isFinite() && c.end.isFinite())
        }
        assertWidthsAndSlot(before, chars)
        assertTrue(minStart(chars, records[2]) < minStart(chars, records[0]))
    }
}
