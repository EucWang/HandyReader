package com.wxn.bookread.ui

import com.wxn.bookread.data.model.LineDot
import com.wxn.bookread.data.model.ListDotShape
import com.wxn.bookread.provider.TextLayoutProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ListDotRenderer 纯函数锚点测试。
 *
 * U1-U3：centerY 垂直锚点（plan §6.1，docs/plans/2026-08-26-plan-fix-list-dot-vertical-alignment.md）
 *   公式：字符带中心 = lineBase + (ascent + descent)/2（AOSP BulletSpan 语义），
 *   与行盒高度/行距系数无关——防止未来被"优化"回行盒几何中心。
 *
 * LI-U*：calcListIndent 缩进边界值（plan §8.1，docs/plans/2026-08-26-plan-list-indent-base.md）
 *   公式：level<=0 → 0；否则 min(level × max(textSize×0.75, LIST_INDENT_MIN_PX), containerWidth/5)
 *
 * DR-U*：符号几何随字号缩放（plan §3-D2）
 *   dotOffsetPx = (textSize×0.45).coerceIn(14,26)；dotRadiusPx = (textSize×0.16).coerceIn(5,10)
 *
 * LO-*：有序标签方向/几何契约（plan §3 C-5.1，docs/plans/2026-08-27-plan-rtl-mixed-list-marker-hardening.md）
 *   orderedLabel：RTL ".N" 点在左 / LTR "N."；orderedDrawX 点号列共线（外伸放置）；
 *   LineDot.markerRtl 缺省 false（legacy 安全默认）；calcListIndent 未封顶域预留 ≥ 标签宽+偏移-墨迹垫
 */
class ListDotRendererTest {

    @Test
    fun u1_zeroHeightBand_centerEqualsBaseline() {
        assertEquals(100f, ListDotRenderer.centerY(100f, -10f, 10f), 0f)
    }

    @Test
    fun u2_typicalFontMetrics() {
        // 48px 典型拉丁字体 ascent=-37.2 / descent=9.8：100 + (-27.4)/2 = 86.3
        assertEquals(86.3f, ListDotRenderer.centerY(100f, -37.2f, 9.8f), 0.001f)
    }

    @Test
    fun u3_translatesWithBaseline_notWithLineBox() {
        val a = -37.2f
        val d = 9.8f
        val y1 = ListDotRenderer.centerY(100f, a, d)
        val y2 = ListDotRenderer.centerY(105f, a, d)
        // lineBase 平移多少，中心平移多少；行距/行盒参数不进入公式
        assertEquals(5f, y2 - y1, 0f)
    }

    // ------------------------------------------------------------
    // LI-U*：calcListIndent 缩进边界值
    // ------------------------------------------------------------

    @Test
    fun liU1_levelZero_returnsZero() {
        assertEquals(0f, ListDotRenderer.calcListIndent(0, 48f, 1000f), 0f)
    }

    @Test
    fun liU2_defaultFontSize_singleLevel() {
        // max(48×0.75, 36) = 36；min(36, 1000/5) = 36
        assertEquals(36f, ListDotRenderer.calcListIndent(1, 48f, 1000f), 0.001f)
    }

    @Test
    fun liU3_multiLevel_linear() {
        assertEquals(108f, ListDotRenderer.calcListIndent(3, 48f, 1000f), 0.001f)
    }

    @Test
    fun liU4_smallText_flooredToMin() {
        // 24×0.75=18 < 下限 36 → 取下限（保证圆点占位）
        assertEquals(36f, ListDotRenderer.calcListIndent(1, 24f, 1000f), 0.001f)
    }

    @Test
    fun liU5_largeText_linear() {
        // max(96×0.75, 36)=72；min(2×72, 2000/5=400) = 144 未触封顶
        assertEquals(144f, ListDotRenderer.calcListIndent(2, 96f, 2000f), 0.001f)
    }

    @Test
    fun liU6_deepNesting_cappedAtContainerFifth() {
        // 10×72=720 > 1000/5=200 → 封顶
        assertEquals(200f, ListDotRenderer.calcListIndent(10, 96f, 1000f), 0.001f)
    }

    @Test
    fun liU7_zeroContainer_gracefulZero() {
        // containerWidth≤0 时 coerceAtMost 归零，无崩溃路径
        assertEquals(0f, ListDotRenderer.calcListIndent(3, 48f, 0f), 0f)
    }

    @Test
    fun liU8_dualColumnWidth_capped() {
        // 双栏单栏宽场景：7×36=252 > 1128/5=225.6 → 封顶
        assertEquals(225.6f, ListDotRenderer.calcListIndent(7, 48f, 1128f), 0.001f)
    }

    // ------------------------------------------------------------
    // DR-U*：符号几何随字号缩放
    // ------------------------------------------------------------

    @Test
    fun drU1_offsetDefault() {
        assertEquals(21.6f, ListDotRenderer.dotOffsetPx(48f), 0.001f)
    }

    @Test
    fun drU2_offsetFloorClamp() {
        // 24×0.45=10.8 < 14 → 钳到下限
        assertEquals(14f, ListDotRenderer.dotOffsetPx(24f), 0.001f)
    }

    @Test
    fun drU3_offsetCeilClamp() {
        // 96×0.45=43.2 > 26 → 钳到上限
        assertEquals(26f, ListDotRenderer.dotOffsetPx(96f), 0.001f)
    }

    @Test
    fun drU4_radiusDefaultFloorCeil() {
        assertEquals(7.68f, ListDotRenderer.dotRadiusPx(48f), 0.001f)
        // 24×0.16=3.84 < 5 → 钳到下限
        assertEquals(5f, ListDotRenderer.dotRadiusPx(24f), 0.001f)
        // 96×0.16=15.36 > 10 → 钳到上限
        assertEquals(10f, ListDotRenderer.dotRadiusPx(96f), 0.001f)
    }

    // ------------------------------------------------------------
    // MAP-U*：level→shape 规范深度映射（plan §2-D3，人工决策 b）
    //   HTML §15.3.7：1=disc，2=circle(空心)，嵌套≥2层=square 封顶不循环
    // ------------------------------------------------------------

    @Test
    fun mapU1_specDepthMapping() {
        assertEquals(ListDotShape.DISC, ListDotShape.shapeForLevel(1))
        assertEquals(ListDotShape.CIRCLE_HOLLOW, ListDotShape.shapeForLevel(2))
        assertEquals(ListDotShape.SQUARE, ListDotShape.shapeForLevel(3))
        assertEquals(ListDotShape.SQUARE, ListDotShape.shapeForLevel(4))
    }

    @Test
    fun mapU2_deepLevels_clampToSquare() {
        // 封顶不循环：更深层级仍命中规范三层选择器
        assertEquals(ListDotShape.SQUARE, ListDotShape.shapeForLevel(5))
        assertEquals(ListDotShape.SQUARE, ListDotShape.shapeForLevel(10))
    }

    // ------------------------------------------------------------
    // SH-U*：形状几何派生（plan §2-D2）
    //   半宽/半高 = dotRadiusPx × (inkEm / DISC_INK_EM)；描边 = max(0.25r, 1.5)
    // ------------------------------------------------------------

    @Test
    fun shU1_defaultTier_allShapes() {
        // t=48 → r=7.68
        assertEquals(7.68f, ListDotRenderer.shapeHalfWidthPx(ListDotShape.DISC, 48f), 0.001f)
        assertEquals(7.68f, ListDotRenderer.shapeHalfWidthPx(ListDotShape.CIRCLE_HOLLOW, 48f), 0.001f)
        assertEquals(7.2f, ListDotRenderer.shapeHalfWidthPx(ListDotShape.SQUARE, 48f), 0.001f)
        assertEquals(12f, ListDotRenderer.shapeHalfWidthPx(ListDotShape.DASH, 48f), 0.001f)
        assertEquals(1.92f, ListDotRenderer.shapeHalfHeightPx(ListDotShape.DASH, 48f), 0.001f)
    }

    @Test
    fun shU2_clampTiers_propagate() {
        // t=24 → r=5（下限）；t=96 → r=10（上限）：钳制随单位半径传播到各形状
        assertEquals(4.6875f, ListDotRenderer.shapeHalfWidthPx(ListDotShape.SQUARE, 24f), 0.001f)
        assertEquals(9.375f, ListDotRenderer.shapeHalfWidthPx(ListDotShape.SQUARE, 96f), 0.001f)
        assertEquals(7.8125f, ListDotRenderer.shapeHalfWidthPx(ListDotShape.DASH, 24f), 0.001f)
        assertEquals(15.625f, ListDotRenderer.shapeHalfWidthPx(ListDotShape.DASH, 96f), 0.001f)
    }

    @Test
    fun shU3_strokeWidth_threeTiers() {
        assertEquals(1.92f, ListDotRenderer.strokePx(48f), 0.001f)   // 0.25r 带内
        assertEquals(1.5f, ListDotRenderer.strokePx(24f), 0.001f)    // 0.25×5=1.25 < 1.5 → 下限
        assertEquals(2.5f, ListDotRenderer.strokePx(96f), 0.001f)
    }

    @Test
    fun shU4_selfConsistency_allShapes() {
        // 缩进自洽不变式（plan D7）：offsetEm + padEm + inkWidthEm/2 ≤ unitEm
        // DASH 恰好等号（0.45+0.05+0.25=0.75）——断言 ≤，为未来形状守门
        for (shape in ListDotShape.values()) {
            assertTrue(
                "shape=$shape 应满足 offsetEm+padEm+inkWidthEm/2 ≤ unitEm",
                shape.inkWidthEm / 2f + ListDotRenderer.DOT_OFFSET_EM + ListDotRenderer.INK_PAD_EM <=
                    ListDotRenderer.UNIT_INDENT_EM + 0.000001f
            )
        }
    }

    // ------------------------------------------------------------
    // LO-*：有序标签方向/几何契约（plan §3 C-5.1，
    //   docs/plans/2026-08-27-plan-rtl-mixed-list-marker-hardening.md，审查 R-6 补零覆盖）
    //   markerRtl 显式方向契约：RTL ".N" 点在左 / LTR "N." 点在右；
    //   orderedDrawX 点号列共线（外伸放置：RTL 钉左缘 anchor+offset，LTR 钉右缘 anchor-offset-w；
    //   详见 docs/plans/2026-08-27-plan-rtl-ordered-marker-outstretched.md）；
    //   LineDot.markerRtl 缺省 false（legacy 安全默认）
    //   LTR 内嵌注释中的「个位对齐」字样按外伸方案 §2 构造等价保留（lo4）
    // ------------------------------------------------------------

    @Test
    fun lo1_orderedLabel_rtlSingleDigit_dotPrefixed() {
        assertEquals(".5", ListDotRenderer.orderedLabel(5, true))
    }

    @Test
    fun lo2_orderedLabel_rtlTwoDigit_dotPrefixed() {
        assertEquals(".12", ListDotRenderer.orderedLabel(12, true))
    }

    @Test
    fun lo3_orderedLabel_ltr_dotSuffixed() {
        assertEquals("5.", ListDotRenderer.orderedLabel(5, false))
        assertEquals("12.", ListDotRenderer.orderedLabel(12, false))
    }

    @Test
    fun lo4_orderedDrawX_ltr_rightEdgeColinear() {
        // LTR：drawX = anchor - offset - w，标签占 [anchor-offset-w, anchor-offset]
        // → 右缘 = anchor-offset 恒定（与 w 无关）= 个位对齐不变量
        assertEquals(60f, ListDotRenderer.orderedDrawX(100f, 20f, 20f, false), 0f)
        assertEquals(52f, ListDotRenderer.orderedDrawX(100f, 20f, 28f, false), 0f)
        // w=0 边界：退化为 anchor-offset
        assertEquals(80f, ListDotRenderer.orderedDrawX(100f, 20f, 0f, false), 0f)
    }

    @Test
    fun lo5_orderedDrawX_rtl_dotColumnColinear_outstretched() {
        // RTL：drawX = anchor + offset（外伸放置），标签左缘 = anchor+offset 恒定（与 w 无关）
        // = 点号列单竖线不变量；位数增加向页缘（右）外伸，与正文间隙 ≥ offset（边缘对齐行恰 = offset）
        assertEquals(100f, ListDotRenderer.orderedDrawX(80f, 20f, 28f, true), 0f)
        assertEquals(100f, ListDotRenderer.orderedDrawX(80f, 20f, 48f, true), 0f)  // w 变化左缘不动
        assertEquals(106f, ListDotRenderer.orderedDrawX(80f, 26f, 18f, true), 0f)
        // w=0 边界：drawX = anchor+offset
        assertEquals(100f, ListDotRenderer.orderedDrawX(80f, 20f, 0f, true), 0f)
    }

    @Test
    fun lo6_lineDot_markerRtlDefaultsFalse() {
        // legacy 纯 LTR 路径不写 markerRtl → 缺省 false（路由可证明性：legacy 段必 baseRtl=false）
        assertFalse(LineDot().markerRtl)
        assertFalse(LineDot(enable = true, level = 1).markerRtl)
    }

    @Test
    fun lo7_listIndent_reservesLabelColumnPlusOffset_uncappedDomain() {
        // 预留充分性（审查 R-11：仅在未触发 15% 封顶的域 w ≤ 0.15W 断言；
        // 封顶域向内收语义归仪器测试锁定）：
        //   calcListIndent(1, t, W, w) >= w + dotOffsetPx(t) - inkPad(t)
        // w = ".5"/".12"/".105" 真机实测宽（MIUI，ts=48 时 ≈26/48/76px，fix 文档 §1.2）随字号线性缩放
        val W = 1200f
        val measuredAt48 = listOf(26f, 48f, 76f)
        for (t in floatArrayOf(24f, 48f, 64f, 96f)) {
            for (w48 in measuredAt48) {
                val w = w48 * (t / 48f)
                assertTrue("前置：组合应落在未封顶域 t=$t w=$w ≤ ${0.15f * W}", w <= 0.15f * W)
                val indent = ListDotRenderer.calcListIndent(1, t, W, w)
                val required = w + ListDotRenderer.dotOffsetPx(t) - TextLayoutProvider.inkPad(t)
                assertTrue(
                    "t=$t w=$w：预留 indent=$indent 应 ≥ 标签宽+偏移-墨迹垫 $required",
                    indent >= required
                )
            }
        }
    }
}
