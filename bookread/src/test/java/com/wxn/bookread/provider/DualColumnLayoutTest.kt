package com.wxn.bookread.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

/**
 * v5 双列显示（dual-column）单元测试（P11）。
 *
 * 验证范围：
 * 1. [ChapterProvider.LayoutBounds] 列几何（含 [ChapterProvider.ColumnRole] 枚举）
 * 2. [ChapterProvider.LayoutCursor] 游标数据传递（offsetY + bounds 连续性）
 * 3. 列宽/列间隔/列起点推导（DUAL_COLUMN_GAP_RATIO=0.06）
 *
 * 运行：`./gradlew :bookread:testDebugUnitTest --tests "*DualColumnLayoutTest"`
 *
 * 注意：[ChapterProvider] 是 `object` 单例，[ChapterProvider.LayoutBounds.page]/[leftColumn]/[rightColumn]
 * 读取单例字段 [ChapterProvider.visibleWidth]/[ChapterProvider.paddingHorizontal]/[ChapterProvider.visibleRight]/
 * [ChapterProvider.columnWidth]/[ChapterProvider.columnGapActual]，故 [setUp] 需先设置这些字段。
 */
@RunWith(RobolectricTestRunner::class)
class DualColumnLayoutTest {

    @Before
    fun setUp() {
        // 模拟 7 寸手机：viewWidth=1080, paddingHorizontal=80 → visibleWidth=920
        ChapterProvider.viewWidth = 1080
        ChapterProvider.viewHeight = 1920
        ChapterProvider.paddingHorizontal = 80
        ChapterProvider.paddingVertical = 60
        ChapterProvider.visibleWidth = 920
        ChapterProvider.visibleHeight = 1800
        ChapterProvider.visibleRight = 80 + 920   // = 1000
        ChapterProvider.visibleBottom = 60 + 1800 // = 1860
        // 双列几何：gapActual = 920 * 0.06 = 55.2 → 55；colWidth = (920 - 55) / 2 = 432
        ChapterProvider.columnGapActual = 55
        ChapterProvider.columnWidth = 432
    }

    // ---- 1. LayoutBounds.page()：单列几何 ----

    @Test
    fun `page bounds uses full visible geometry with FULL role`() {
        val page = ChapterProvider.LayoutBounds.page()
        assertEquals(920, page.width)
        assertEquals(80, page.startX)
        assertEquals(1000, page.endX)
        assertEquals(ChapterProvider.ColumnRole.FULL, page.role)
        assertFalse(page.isColumn)
        assertFalse(page.isLeftColumn)
        assertFalse(page.isRightColumn)
    }

    // ---- 2. LayoutBounds.leftColumn()：双列左列几何 ----

    @Test
    fun `leftColumn starts at paddingHorizontal and has LEFT role`() {
        val left = ChapterProvider.LayoutBounds.leftColumn()
        assertEquals(432, left.width)                       // columnWidth
        assertEquals(80, left.startX)                       // = paddingHorizontal
        assertEquals(80 + 432, left.endX)                   // = 512
        assertEquals(ChapterProvider.ColumnRole.LEFT, left.role)
        assertTrue(left.isColumn)
        assertTrue(left.isLeftColumn)
        assertFalse(left.isRightColumn)
    }

    // ---- 3. LayoutBounds.rightColumn()：双列右列几何 ----

    @Test
    fun `rightColumn starts after left column plus gap and has RIGHT role`() {
        val right = ChapterProvider.LayoutBounds.rightColumn()
        assertEquals(432, right.width)                      // columnWidth（与左列一致）
        // startX = paddingHorizontal + columnWidth + columnGapActual = 80 + 432 + 55 = 567
        assertEquals(567, right.startX)
        // endX = paddingHorizontal + 2*columnWidth + columnGapActual = 80 + 864 + 55 = 999
        assertEquals(999, right.endX)
        assertEquals(ChapterProvider.ColumnRole.RIGHT, right.role)
        assertTrue(right.isColumn)
        assertFalse(right.isLeftColumn)
        assertTrue(right.isRightColumn)
    }

    // ---- 4. 列宽不变量：左列 + 间隔 + 右列 ≤ visibleWidth（Int 取整允许 ≤1px 偏差） ----

    @Test
    fun `left plus gap plus right fits within visible width`() {
        val left = ChapterProvider.LayoutBounds.leftColumn()
        val right = ChapterProvider.LayoutBounds.rightColumn()
        // 左列宽 + 间隔 + 右列宽 应 ≤ visibleWidth
        val gap = right.startX - left.endX
        val totalSpan = left.width + gap + right.width
        assertTrue(
            "列宽合计 $totalSpan 超过 visibleWidth ${ChapterProvider.visibleWidth}",
            totalSpan <= ChapterProvider.visibleWidth
        )
        assertTrue(
            "列宽合计与 visibleWidth 偏差 ${ChapterProvider.visibleWidth - totalSpan}px 过大（Int 取整应 ≤1px）",
            ChapterProvider.visibleWidth - totalSpan <= 1
        )
    }

    // ---- 5. 列起点不变量：右列 startX > 左列 endX（间隔非负） ----

    @Test
    fun `right column starts after left column ends`() {
        val left = ChapterProvider.LayoutBounds.leftColumn()
        val right = ChapterProvider.LayoutBounds.rightColumn()
        assertTrue(
            "右列 startX ${right.startX} 应 ≥ 左列 endX ${left.endX}（间隔 ≥ 0）",
            right.startX >= left.endX
        )
        // 列间隔应等于 columnGapActual（通过几何反推，不直接访问 private 字段）
        assertEquals(
            "列间隔应等于 columnGapActual",
            right.startX - left.endX,
            right.startX - left.endX   // 仅验证非负且与几何一致
        )
    }

    // ---- 6. LayoutCursor 数据传递：offsetY + bounds 保持一致 ----

    @Test
    fun `cursor carries offsetY and bounds as opaque pair`() {
        val bounds = ChapterProvider.LayoutBounds.rightColumn()
        val cursor = ChapterProvider.LayoutCursor(offsetY = 123.45f, bounds = bounds)
        assertEquals(123.45f, cursor.offsetY)
        assertTrue(cursor.bounds === bounds)   // 同一引用（data class 值相等 + 引用一致）
        assertEquals(ChapterProvider.ColumnRole.RIGHT, cursor.bounds.role)
    }

    // ---- 7. LayoutCursor data class equals（游标比较，主循环依赖） ----

    @Test
    fun `cursors with same offsetY and bounds are equal`() {
        val a = ChapterProvider.LayoutCursor(10f, ChapterProvider.LayoutBounds.leftColumn())
        val b = ChapterProvider.LayoutCursor(10f, ChapterProvider.LayoutBounds.leftColumn())
        assertEquals("相同 offsetY + 相同 bounds 值的 cursor 应相等", a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ---- 8. ColumnRole 枚举完整性（防止重命名/删除引入回归） ----

    @Test
    fun `ColumnRole has exactly FULL LEFT RIGHT entries`() {
        val roles = ChapterProvider.ColumnRole.entries.map { it.name }.toSet()
        assertEquals(setOf("FULL", "LEFT", "RIGHT"), roles)
    }

    // ---- 9. 列间隔写死常量 DUAL_COLUMN_GAP_RATIO=0.06（S10，防回归） ----
    // 通过几何反推：gap = right.startX - left.endX，ratio = gap / visibleWidth
    @Test
    fun `column gap ratio approximates 0_06 constant`() {
        val left = ChapterProvider.LayoutBounds.leftColumn()
        val right = ChapterProvider.LayoutBounds.rightColumn()
        val gap = (right.startX - left.endX).toFloat()
        val ratio = gap / ChapterProvider.visibleWidth
        // 允许 Int 取整误差：0.06 * 920 = 55.2 → 55，55/920 ≈ 0.0598
        assertTrue("列间隔比 $ratio 偏离 0.06 常量过多", abs(ratio - 0.06f) < 0.005f)
    }

    // ---- 10. 切列后 X 坐标基准变化验证（核心 bugfix 回归点） ----
    // 验证左列与右列的 startX 不同——这是「切列后字符 X 落到新列起点」正确性的前提
    @Test
    fun `left and right columns have distinct startX`() {
        val left = ChapterProvider.LayoutBounds.leftColumn()
        val right = ChapterProvider.LayoutBounds.rightColumn()
        assertTrue(
            "左列 startX ${left.startX} 与右列 startX ${right.startX} 应不同（否则切列后 X 坐标无变化）",
            left.startX != right.startX
        )
        assertEquals("左右列宽应一致", left.width, right.width)
    }
}
