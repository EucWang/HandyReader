package com.wxn.bookread.provider

import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Spike (instrumented): 验证 StaticLayout.getPrimaryHorizontal 对 RTL 文本的行为。
 *
 * 用 instrumented test 而非 Robolectric,是因为 StaticLayout 是 native 实现,
 * Robolectric 的 shadow 不做真实文本测量(参见 InlineFontSizeSpikeInstrumentedTest 头注释)。
 *
 * 决策来源: docs/reviews/review-2026-07-24-rtl-reading-support.md §4.4
 *
 * 5 条用例:
 * - T1: getPrimaryHorizontal(0) 的原点(非零)
 * - T2: 视觉方向——offset 递增时 x 是否递减(RTL 特征)
 * - T3: 连写宽度——相邻 offset 差是否为正
 * - T4: leading/trailing 边——与 lineLeft/lineRight 的比对
 * - T5: ALIGN_OPPOSITE 下行起点与 lineWidth 的关系
 *
 * 注意:getPrimaryHorizontal(int,boolean) 带 clamped 参数的变体在 AGP 隐藏 API
 * 限制下不可用(虽自 API 23 引入,但属于受限 API),因此 T4 改用 line bounds 验证。
 *
 * 运行(需连接设备/模拟器):
 *   gradlew.bat :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.RtlGetPrimaryHorizontalSpikeInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class RtlGetPrimaryHorizontalSpikeInstrumentedTest {

    private lateinit var paint: TextPaint
    private val layoutWidth = 600  // px,足够宽避免被强制换行

    // 测试文本: 覆盖纯 RTL、词内混排、中性字符边界
    private val pureArabic = "مرحبا بالعالم"
    private val mixedInline = "iPhone 15 نسخة"
    private val neutralBoundary = "كلمة ، جملة"

    @Before
    fun setUp() {
        paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 40f
            isAntiAlias = true
        }
    }

    private fun buildLayout(text: String, width: Int = layoutWidth): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
            .setIncludePad(true)
            .build()

    // ─────────────────────────────────────────────────────────────────────────
    // T1: 原点 —— getPrimaryHorizontal(0) 是否为 0?
    //     RTL 段落的首字符在视觉最右侧,x 应远离 0 点。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T1_origin_getPrimaryHorizontal0() {
        val text = pureArabic
        val layout = buildLayout(text)

        val x0 = layout.getPrimaryHorizontal(0)
        val lineLeft = layout.getLineLeft(0)
        val lineRight = layout.getLineRight(0)
        val lineWidth = layout.getLineWidth(0)

        println("T1: text='$text'")
        println("T1: getPrimaryHorizontal(0)=$x0")
        println("T1: getLineLeft(0)=$lineLeft")
        println("T1: getLineRight(0)=$lineRight")
        println("T1: getLineWidth(0)=$lineWidth")

        // RTL 段落: 首字符应在视觉右侧,x 应 > lineWidth * 0.5
        assertTrue(
            "T1 失败:RTL 首字符 getPrimaryHorizontal(0)=$x0 应远离左边界(> lineWidth/2=${
                lineWidth * 0.5f})",
            x0 > lineWidth * 0.5f
        )
        // 首字符 x 应 < layoutWidth (在布局范围内)
        assertTrue(
            "T1 失败:RTL 首字符 x=$x0 超过了 layoutWidth=$layoutWidth",
            x0 <= layoutWidth.toFloat() + 1f
        )
        println("T1 ★ 通过:getPrimaryHorizontal(0)=$x0 在右侧,非零")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T2: 视觉方向 —— offset 升序时 x 是否递减?
    //     对纯 RTL 文本,offset 越大字符越靠左,getPrimaryHorizontal 应递减。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T2_visualDirection_decreasing() {
        val layout = buildLayout(pureArabic)

        var decreasing = true
        var failOffset = -1
        var prevX = layout.getPrimaryHorizontal(0)

        println("T2: text='$pureArabic' length=${pureArabic.length}")
        for (offset in 0 until pureArabic.length) {
            val x = layout.getPrimaryHorizontal(offset)
            val ch = pureArabic[offset]
            println("T2:   offset=$offset char='$ch' Unicode=U+${ch.code.toString(16)} x=$x")
            if (offset > 0 && x >= prevX) {
                decreasing = false
                failOffset = offset
                println("T2:   ⚠ offset=$offset x=$x >= prev=$prevX (未递减)")
            }
            prevX = x
        }

        assertTrue(
            "T2 失败:纯 RTL 文本应在 offset 递增时 x 递减,但在 offset=$failOffset 处未递减",
            decreasing
        )
        println("T2 ★ 通过:offset 递增时 x 严格递减(RTL 视觉方向正确)")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T3: 连写宽度 —— 相邻 offset 差是否为正?
    //     验证 getPrimaryHorizontal(offset+1) - getPrimaryHorizontal(offset)
    //     是否为字符 shaping 后的真实视觉宽度,特别关注 bidi 边界。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T3_shapingWidth_positiveAcrossBoundaries() {
        val scenarios = listOf(
            "纯阿拉伯语" to pureArabic,
            "词内混排" to mixedInline,
            "中性字符边界" to neutralBoundary
        )

        var hasNegativeDiff = false

        for ((label, text) in scenarios) {
            val layout = buildLayout(text)
            println("T3: ── $label ──")
            println("T3: text='$text'")

            for (offset in 0 until text.length - 1) {
                val xCurr = layout.getPrimaryHorizontal(offset)
                val xNext = layout.getPrimaryHorizontal(offset + 1)
                val diff = xNext - xCurr
                val chCurr = text.substring(offset, offset + 1)
                val chNext = text.substring(offset + 1, offset + 2)

                if (diff <= 0) {
                    hasNegativeDiff = true
                }

                println("T3:   offset=$offset '$chCurr'→'$chNext' diff=$diff${
                    if (diff <= 0) " ⚠ 负/零 diff" else ""
                }")
            }

            // 打印行级别信息
            println("T3:   lineLeft=${layout.getLineLeft(0)} lineRight=${layout.getLineRight(0)} lineWidth=${layout.getLineWidth(0)}")
        }

        if (hasNegativeDiff) {
            println("T3: ⚠ 发现负 diff(bidi 边界) —— P2 需要 leading/trailing 边特殊处理")
        } else {
            println("T3 ★ 所有 diff 均为正(P2 getPrimaryHorizontal 在此场景可直接使用)")
        }
        // 本项不 assert,只记录——决策取决于 P2 是否仍可用
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T4: leading/trailing 边 —— 与 lineLeft/lineRight 的比对
    //     getPrimaryHorizontal(int,boolean) clamped 变体受 AGP 隐藏 API 限制不可用,
    //     故通过对比 getPrimaryHorizontal(offset) 与 lineLeft/lineRight 验证:
    //     - 所有 offset 的 x 值在 line bounds 范围内(无越界)
    //     - 首字符接近 lineRight(RTL:offset=0 在视觉最右侧)
    //     - 末字符接近 lineLeft(RTL:offset=last 在视觉最左侧)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T4_leadingTrailing_edgeComparison() {
        val scenarios = listOf(
            "纯阿拉伯语" to pureArabic,
            "词内混排" to mixedInline,
            "中性字符边界" to neutralBoundary
        )

        for ((label, text) in scenarios) {
            val layout = buildLayout(text)
            val lineLeft = layout.getLineLeft(0)
            val lineRight = layout.getLineRight(0)

            println("T4: ── $label ──")
            println("T4: text='$text'")
            println("T4:   lineLeft=$lineLeft lineRight=$lineRight")

            var allInBounds = true
            for (offset in 0 until text.length) {
                val x = layout.getPrimaryHorizontal(offset)
                val ch = text[offset]
                val edge = when {
                    abs(x - lineLeft) < lineRight * 0.05f -> "(≈lineLeft)"
                    abs(x - lineRight) < lineRight * 0.05f -> "(≈lineRight)"
                    else -> ""
                }
                println("T4:   offset=$offset char='$ch' x=$x $edge")
                if (x < lineLeft - 1f || x > lineRight + 1f) {
                    println("T4:   ⚠ x=$x 超出 bounds [$lineLeft, $lineRight]")
                    allInBounds = false
                }
            }

            assertTrue("T4 失败:text='$text' 部分 offset 超出 line bounds", allInBounds)
        }
        println("T4 ★ 通过:所有 getPrimaryHorizontal 值均在 line bounds 内")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T5: ALIGN_OPPOSITE 行起点 —— 段落方向由首字符决定
    //     ALIGN_OPPOSITE = 与段落方向相反的 alignment:
    //     - 纯阿拉伯语(首字符 RTL→段落 RTL): ALIGN_OPPOSITE = LEFT, lineLeft≈0
    //     - 前有英语(首字符 LTR→段落 LTR): ALIGN_OPPOSITE = RIGHT, lineLeft>0
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T5_alignOpposite_lineStart() {
        // 纯阿拉伯语: 首字符 RTL → 段落 RTL → ALIGN_OPPOSITE = LEFT
        val layoutRtl = buildLayout(pureArabic)
        val lineLeftRtl = layoutRtl.getLineLeft(0)
        val lineRightRtl = layoutRtl.getLineRight(0)
        val lineWidthRtl = layoutRtl.getLineWidth(0)
        println("T5: ── 纯阿拉伯语 (首字符 RTL) ──")
        println("T5:   lineLeft=$lineLeftRtl lineRight=$lineRightRtl lineWidth=$lineWidthRtl")
        assertTrue("纯阿拉伯语:lineLeft=$lineLeftRtl 应 ≈ 0(LEFT 对齐)", lineLeftRtl < 1f)
        assertTrue(
            "纯阿拉伯语:lineRight=$lineRightRtl 应 ≈ lineWidth=$lineWidthRtl",
            abs(lineRightRtl - lineWidthRtl) < 1f
        )

        // 前有英语: 首字符 LTR → 段落 LTR → ALIGN_OPPOSITE = RIGHT
        val layoutLtr = buildLayout(mixedInline)
        val lineLeftLtr = layoutLtr.getLineLeft(0)
        val lineRightLtr = layoutLtr.getLineRight(0)
        val lineWidthLtr = layoutLtr.getLineWidth(0)
        println("T5: ── 词内混排 (首字符 LTR) ──")
        println("T5:   lineLeft=$lineLeftLtr lineRight=$lineRightLtr lineWidth=$lineWidthLtr")
        val expectedLeft = layoutWidth - lineWidthLtr
        assertTrue(
            "词内混排:lineLeft=$lineLeftLtr 应 ≈ layoutWidth-lineWidth=$expectedLeft",
            abs(lineLeftLtr - expectedLeft) < 2f
        )
        assertTrue(
            "词内混排:lineRight=$lineRightLtr 应 ≈ layoutWidth=$layoutWidth",
            abs(lineRightLtr - layoutWidth.toFloat()) < 2f
        )

        println("T5 ★ 通过:ALIGN_OPPOSITE 行为正确——首字符 RTL→LEFT,首字符 LTR→RIGHT")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T6: ALIGN_NORMAL + TextDirectionHeuristics.RTL 对齐行为
    //
    // 方案 §2a/§2b 假设：ALIGN_NORMAL + RTL = 段落右对齐（lineLeft > 0, lineRight ≈ width）
    // 这是 handleRtlLine 坐标计算的地基——若实际是左对齐，所有视觉坐标推导失效。
    // 本测试构建 4 个变体验证 alignment × textDirection 的组合：
    //   - 纯阿语 ALIGN_NORMAL 默认方向（依赖首字符自动判定 RTL）
    //   - 纯阿语 ALIGN_NORMAL + 显式 RTL（方案组合）
    //   - 纯阿语 ALIGN_CENTER + RTL（Center 分支验证）
    //   - 词内混排 ALIGN_NORMAL + RTL（验证混排下仍右对齐）
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T6_alignNormal_rtl_rightAligned() {
        data class Variant(
            val label: String,
            val text: String,
            val alignment: Layout.Alignment,
            val textDirection: android.text.TextDirectionHeuristic?
        )

        val variants = listOf(
            Variant("纯阿语 NORMAL 默认方向", pureArabic, Layout.Alignment.ALIGN_NORMAL, null),
            Variant("纯阿语 NORMAL + RTL（方案组合）", pureArabic, Layout.Alignment.ALIGN_NORMAL, android.text.TextDirectionHeuristics.RTL),
            Variant("纯阿语 CENTER + RTL", pureArabic, Layout.Alignment.ALIGN_CENTER, android.text.TextDirectionHeuristics.RTL),
            Variant("词内混排 NORMAL + RTL", mixedInline, Layout.Alignment.ALIGN_NORMAL, android.text.TextDirectionHeuristics.RTL)
        )

        for (v in variants) {
            val builder = StaticLayout.Builder.obtain(v.text, 0, v.text.length, paint, layoutWidth)
                .setAlignment(v.alignment)
                .setIncludePad(true)
            if (v.textDirection != null) {
                builder.setTextDirection(v.textDirection)
            }
            val layout = builder.build()

            val lineLeft = layout.getLineLeft(0)
            val lineRight = layout.getLineRight(0)
            val lineWidth = layout.getLineWidth(0)
            val widthOccupiedRatio = if (layoutWidth > 0) lineWidth.toFloat() / layoutWidth else 0f

            println("T6: ── ${v.label} ──")
            println("T6:   text='${v.text}'")
            println("T6:   lineLeft=$lineLeft lineRight=$lineRight lineWidth=$lineWidth")
            println("T6:   widthOccupiedRatio=$widthOccupiedRatio (lineWidth/layoutWidth)")

            when {
                // 右对齐特征：lineRight 紧贴右边界，lineLeft > 0
                v.alignment == Layout.Alignment.ALIGN_NORMAL && v.textDirection != null -> {
                    assertTrue(
                        "T6 失败 [${v.label}]:NORMAL+RTL 应右对齐,lineRight=$lineRight 应 ≈ layoutWidth=$layoutWidth",
                        abs(lineRight - layoutWidth.toFloat()) < 2f
                    )
                    assertTrue(
                        "T6 失败 [${v.label}]:NORMAL+RTL 应右对齐,lineLeft=$lineLeft 应 > 0(文字未顶到左边界)",
                        lineLeft > 1f || widthOccupiedRatio > 0.95f   // 占满全宽时 lineLeft≈0 是正常的
                    )
                    println("T6 ★ [${v.label}] 右对齐行为确认（NORMAL+RTL = 右对齐）")
                }
                // Center 对齐特征：两侧留白对称
                v.alignment == Layout.Alignment.ALIGN_CENTER -> {
                    val leftMargin = lineLeft
                    val rightMargin = layoutWidth - lineRight
                    println("T6:   leftMargin=$leftMargin rightMargin=$rightMargin (应大致对称)")
                    assertTrue(
                        "T6 失败 [${v.label}]:CENTER 应居中,leftMargin=$leftMargin 与 rightMargin=$rightMargin 应大致对称",
                        abs(leftMargin - rightMargin) < 3f
                    )
                    println("T6 ★ [${v.label}] 居中行为确认（CENTER+RTL = 居中）")
                }
                // 默认方向 + NORMAL：记录行为，不强制 assert（混排下可能左对齐也可能右对齐）
                else -> {
                    println("T6:   (记录,不 assert) 默认方向行为:lineLeft=$lineLeft lineRight=$lineRight")
                }
            }
        }
        println("T6 ★★ 通过:方案 ALIGN_NORMAL+RTL 组合的行为已实测确认")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T7: setIndents(left, right) 在 RTL 段落下的语义（实测记录，方案假设已废弃）
    //
    // 原方案 §2a A1 假设：setIndents 是「视觉左右」语义，RTL 首行缩进修 right 参数。
    //
    // 2026-07-25 实测结果（Mi 10）：该假设错误。
    // ALIGN_NORMAL + RTL 下，setIndents 的 left/right 参数无视觉差异——都表现为
    // "内容块整体右移，lineRight 不变"，无法控制首行缩进方向。
    //
    // 方案已改为：放弃 setIndents，在 handleRtlLine 内用 indentShift 手动叠加首行缩进。
    // 本测试保留为"实测记录"，验证 setIndents 的实际行为，作为方案决策的可追溯证据。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T7_setIndents_rtl_visualSemantics() {
        val indent = 60   // px

        // 用长文本确保换行（至少 2 行），才能区分首行 vs 后续行缩进
        val longText = "مرحبا بالعالم هذا اختبار طويل للتأكد من وجود عدة أسطر في الفقرة"

        // 基线：无缩进（首行 + 后续行都 0）
        val layoutBase = buildRtlLayoutWithIndents(longText, firstLeft = 0, firstRight = 0, restLeft = 0, restRight = 0)
        val baseLine0Left = layoutBase.getLineLeft(0)
        val baseLine0Right = layoutBase.getLineRight(0)
        println("T7: ── 基线（无缩进, ${layoutBase.lineCount} 行） ──")
        println("T7:   line0 left=$baseLine0Left right=$baseLine0Right")

        // 变体 A：首行 left=indent
        val layoutA = buildRtlLayoutWithIndents(longText, firstLeft = indent, firstRight = 0, restLeft = 0, restRight = 0)
        val aLine0Left = layoutA.getLineLeft(0)
        val aLine0Right = layoutA.getLineRight(0)
        println("T7: ── 变体 A（首行 left=$indent） ──")
        println("T7:   line0 left=$aLine0Left right=$aLine0Right (Δleft=${aLine0Left - baseLine0Left}, Δright=${aLine0Right - baseLine0Right})")

        // 变体 B：首行 right=indent（原方案 §2a 组合）
        val layoutB = buildRtlLayoutWithIndents(longText, firstLeft = 0, firstRight = indent, restLeft = 0, restRight = 0)
        val bLine0Left = layoutB.getLineLeft(0)
        val bLine0Right = layoutB.getLineRight(0)
        println("T7: ── 变体 B（首行 right=$indent，原方案 §2a 组合） ──")
        println("T7:   line0 left=$bLine0Left right=$bLine0Right (Δleft=${bLine0Left - baseLine0Left}, Δright=${bLine0Right - baseLine0Right})")

        // 实测结论：A 和 B 的视觉表现应该相同（setIndents 在 RTL 下无法控制方向）
        val aLeftDelta = aLine0Left - baseLine0Left
        val bLeftDelta = bLine0Left - baseLine0Left
        val aRightDelta = aLine0Right - baseLine0Right
        val bRightDelta = bLine0Right - baseLine0Right

        println("T7: 结论分析:")
        println("T7:   变体 A(left=$indent):  Δleft=$aLeftDelta, Δright=$aRightDelta")
        println("T7:   变体 B(right=$indent): Δleft=$bLeftDelta, Δright=$bRightDelta")

        val abSameBehavior = abs(aLeftDelta - bLeftDelta) < 2f && abs(aRightDelta - bRightDelta) < 2f
        if (abSameBehavior) {
            println("T7 ★ 实测确认：setIndents 的 left/right 参数在 ALIGN_NORMAL+RTL 下行为完全相同")
            println("T7 ★ → 无法通过 setIndents 控制 RTL 首行缩进方向")
            println("T7 ★ → 方案 §2a 第一步不处理 RTL 首行缩进（A1 决策，正确）")
        } else {
            println("T7 ⚠ 意外：left/right 行为不同（Δleft 差 ${aLeftDelta - bLeftDelta}, Δright 差 ${aRightDelta - bRightDelta}）")
            println("T7 ⚠ 需重新评估方案——若 right 让 lineRight 左移更多，可考虑恢复 setIndents(right)")
        }

        // 不再强制 assert（实测发现假设错误，改为记录）。
        // 仅验证"setIndents 确实产生了某种缩进效果"（Δleft 或 Δright 至少有一个非零）
        assertTrue(
            "T7 失败:setIndents 在 RTL 下完全无效果（Δleft=0, Δright=0）——与 2026-07-25 实测不符",
            abs(aLeftDelta) > 1f || abs(aRightDelta) > 1f
        )
        println("T7 ★★ 通过（记录型测试）：setIndents 在 RTL 下的行为已实测并归档")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T8: Center 对齐下 getPrimaryHorizontal 是否已含居中偏移
    //
    // 方案 §2b 的 alignShift 计算：Center 时 alignShift = (bounds.width - desiredWidth) / 2
    // 但若 getPrimaryHorizontal 在 Center 对齐下已返回含居中偏移的坐标,
    // 则 alignShift 公式会重复计算,导致坐标整体右偏 alignShift 像素。
    //
    // 验证方式：构建 Center+RTL 的 layout,对比 lineLeft 与 getPrimaryHorizontal(0):
    //   - 若 getPrimaryHorizontal(0) ≈ lineLeft → 已含居中偏移,alignShift 应为 0
    //   - 若 getPrimaryHorizontal(0) ≈ 0 → 未含偏移,需要 alignShift 公式
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T8_center_primaryHorizontalIncludesOffset() {
        // 用短文本 + 宽 layout,确保 Center 下两侧有明显留白（便于观察偏移）
        val shortText = "مرحبا"
        val builder = StaticLayout.Builder.obtain(shortText, 0, shortText.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .setTextDirection(android.text.TextDirectionHeuristics.RTL)
        val layout = builder.build()

        val lineLeft = layout.getLineLeft(0)
        val lineRight = layout.getLineRight(0)
        val lineWidth = layout.getLineWidth(0)
        val x0 = layout.getPrimaryHorizontal(0)
        val xLast = layout.getPrimaryHorizontal(shortText.length)

        println("T8: ── CENTER + RTL (短文本 '$shortText' 宽 layout=$layoutWidth) ──")
        println("T8:   lineLeft=$lineLeft lineRight=$lineRight lineWidth=$lineWidth")
        println("T8:   getPrimaryHorizontal(0)=$x0  getPrimaryHorizontal(len)=$xLast")
        println("T8:   视觉左边界 = minOf(x0,xLast) = ${minOf(x0, xLast)}")
        println("T8:   lineLeft 与 getPrimaryHorizontal 视觉左边界差异 = ${lineLeft - minOf(x0, xLast)}")

        val visualLeftFromPrimary = minOf(x0, xLast)
        val offset = lineLeft - visualLeftFromPrimary

        // 判定：
        // - 若 |offset| < 2px → getPrimaryHorizontal 已含居中偏移，alignShift 应为 0
        // - 若 offset ≈ (layoutWidth - lineWidth)/2 → 未含偏移，需要 alignShift 公式
        val expectedAlignShift = (layoutWidth - lineWidth) / 2f
        println("T8:   预期 alignShift（若未含偏移）= (width - lineWidth)/2 = $expectedAlignShift")
        println("T8:   实际 offset（lineLeft - primary 视觉左）= $offset")

        val includesOffset = abs(offset) < 2f
        val needsAlignShift = abs(offset - expectedAlignShift) < 2f

        assertTrue(
            "T8 失败:getPrimaryHorizontal 在 Center 下既不是「已含偏移」也不是「需要 alignShift」,offset=$offset, expectedAlignShift=$expectedAlignShift。需人工核查。",
            includesOffset || needsAlignShift
        )

        if (includesOffset) {
            println("T8 ★★ 结论:getPrimaryHorizontal 已含居中偏移 → 方案 §2b alignShift 应改为 0（Center 分支）")
            println("T8 ★★ → §2b/§9 需修正：Center 下不再加 (bounds.width - desiredWidth)/2 偏移")
        } else {
            println("T8 ★★ 结论:getPrimaryHorizontal 未含居中偏移 → 方案 §2b alignShift 公式正确")
            println("T8 ★★ → Center 时 alignShift = (bounds.width - desiredWidth)/2 保持不变")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T9: setIndents 对 RTL getPrimaryHorizontal 的影响（gph 级验证，纠正 T7 的指标错误）
    //
    // 背景：T7 测量了 getLineLeft/getLineRight（基于 getLineMax，不含 Builder indents），
    //       得出「left/right 参数在 RTL 下无差异」的结论。
    //       但 AOSP 源码显示 getPrimaryHorizontal 走 getLineStartPos 路径：
    //         ALIGN_NORMAL + RTL → x = mWidth + (-rightIndents[line])
    //       即 rightIndents 在 RTL 下直接偏移字符定位，leftIndents 不参与。
    //
    // 本测试验证 getPrimaryHorizontal 层面（而非 getLineLeft）的行为：
    //   - rightIndents=[indent,0] → line 0 首字 gph 左移 indent（右侧留白）
    //   - leftIndents=[indent,0]  → line 0 首字 gph 不偏移（仅缩减换行宽度）
    //   - 两者在 gph 层面应有差异（推翻 T7 基于 getLineLeft 的结论）
    //   - line 1+ 恢复全宽（rightIndents[1]=0，gph 不偏移）
    //
    // 决策意义：若 T9 通过 → setIndents 可用于 layoutMixedRun 替代 narrowed-width + Plan H
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T9_setIndents_rtl_gphLevelVerification() {
        val indent = 150  // px
        val longText = "مرحبا بالعالم هذا اختبار طويل للتأكد من وجود عدة أسطر في الفقرة"

        // 三个 layout：基线、rightIndents、leftIndents
        val layoutBase = buildRtlLayoutWithIndents(longText, 0, 0, 0, 0)
        val layoutRight = buildRtlLayoutWithIndents(longText, 0, indent, 0, 0)  // rightIndents=[indent,0]
        val layoutLeft = buildRtlLayoutWithIndents(longText, indent, 0, 0, 0)   // leftIndents=[indent,0]

        println("T9: indent=$indent, text length=${longText.length}")
        println("T9: lineCount base=${layoutBase.lineCount} right=${layoutRight.lineCount} left=${layoutLeft.lineCount}")

        // ── 1. line 0 首字 gph 对比（offset=0，所有 layout 一致）──
        val baseGph0 = layoutBase.getPrimaryHorizontal(0)
        val rightGph0 = layoutRight.getPrimaryHorizontal(0)
        val leftGph0 = layoutLeft.getPrimaryHorizontal(0)
        val rightShift = baseGph0 - rightGph0   // 正值 = 左移
        val leftShift = baseGph0 - leftGph0

        println("T9: ── line 0 首字(offset=0) gph ──")
        println("T9:   base=$baseGph0  rightIndent=$rightGph0  leftIndent=$leftGph0")
        println("T9:   rightShift(基线-right)=$rightShift  (源码预测 ≈ $indent)")
        println("T9:   leftShift(基线-left)=$leftShift   (源码预测 ≈ 0)")

        // ── 2. line 0 逐字 gph（诊断，验证整行偏移一致）──
        val baseL0End = layoutBase.getLineEnd(0)
        val rightL0End = layoutRight.getLineEnd(0)
        println("T9: ── line 0 逐字 gph（base vs rightIndent，前 8 字）──")
        println("T9:   base line0 [0..$baseL0End]  right line0 [0..$rightL0End]")
        val printCount = minOf(baseL0End, rightL0End, 8)
        for (off in 0 until printCount) {
            val bg = layoutBase.getPrimaryHorizontal(off)
            val rg = layoutRight.getPrimaryHorizontal(off)
            println("T9:   off=$off char='${longText.substring(off, off + 1)}'  base=$bg  right=$rg  diff=${bg - rg}")
        }

        // ── 3. line 1+ 首字 gph 对比（验证全宽恢复）──
        println("T9: ── line 1 首字 gph（rightIndents[1]=0 → 不应偏移）──")
        if (layoutBase.lineCount > 1 && layoutRight.lineCount > 1) {
            val baseL1Start = layoutBase.getLineStart(1)
            val rightL1Start = layoutRight.getLineStart(1)
            val baseGph1 = layoutBase.getPrimaryHorizontal(baseL1Start)
            val rightGph1 = layoutRight.getPrimaryHorizontal(rightL1Start)
            println("T9:   base line1[$baseL1Start] gph=$baseGph1  right line1[$rightL1Start] gph=$rightGph1  shift=${baseGph1 - rightGph1}")
            assertTrue(
                "T9 失败: rightIndents 下 line 1 首字 gph 应不偏移（恢复全宽）。" +
                    "base=$baseGph1 right=$rightGph1 shift=${baseGph1 - rightGph1}",
                abs(baseGph1 - rightGph1) < 10f
            )
        } else {
            println("T9:   (行数不足 2 行，跳过 line 1 断言)")
        }

        // ── 4. rightIndents vs leftIndents 在 gph 层面的差异（核心断言）──
        val gphDiff = abs(rightGph0 - leftGph0)
        println("T9: ── rightIndents vs leftIndents（line 0 首字 gph 差=$gphDiff）──")
        println("T9:   T7(getLineLeft) 结论「无差异」; gph 层面 ${if (gphDiff > 10f) "有差异 ★★" else "仍无差异"}")

        // ── 断言 ──
        assertTrue(
            "T9 失败[1]: rightIndents 未让 line 0 gph 左移 indent。" +
                "rightShift=$rightShift, 预期 ≈ $indent(±10)",
            abs(rightShift - indent.toFloat()) < 10f
        )
        assertTrue(
            "T9 失败[2]: leftIndents 不应偏移 line 0 gph。" +
                "leftShift=$leftShift, 预期 ≈ 0(±10)",
            abs(leftShift) < 10f
        )
        assertTrue(
            "T9 失败[3]: rightIndents 与 leftIndents 在 gph 层面无差异。" +
                "rightGph0=$rightGph0 leftGph0=$leftGph0 diff=$gphDiff。" +
                "若差 < 10 说明 gph 也不区分 left/right，setIndents 方案不可行。",
            gphDiff > 10f
        )
        println("T9 ★★ 通过: rightIndents 在 RTL gph 层面生效——line 0 左移 $indent, leftIndents 不偏移, 两者有差异")
        println("T9 ★★ → setIndents(null, [indent, 0]) 可用于 RTL run 的首行缩窄（替代 narrowed-width + Plan H）")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T10: setIndents 对 LTR getPrimaryHorizontal 的影响
    //
    // layoutMixedRun 中 LTR run（英文段）也需要首行缩窄（共享前一 run 末行空间）。
    // 源码预测：ALIGN_NORMAL + LTR 下 rightIndents 仅缩减换行宽度，不偏移 gph：
    //   getLineStartPos = left + getIndentAdjust(line, ALIGN_LEFT) = 0 + leftIndents[line]
    //   leftIndents=null → getLineStartPos=0（不偏移）
    //
    // 即 LTR run 用 rightIndents=[indent,0] → 首字仍在 x=0，但换行更早（宽度缩窄）。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T10_setIndents_ltr_gphLevelVerification() {
        val indent = 150
        val longText = "The quick brown fox jumps over the lazy dog and keeps running for many words to fill"

        fun buildLtr(firstRight: Int): StaticLayout {
            val builder = StaticLayout.Builder.obtain(longText, 0, longText.length, paint, layoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(android.text.TextDirectionHeuristics.LTR)
                .setIncludePad(true)
            if (firstRight > 0) {
                builder.setIndents(intArrayOf(0, 0), intArrayOf(firstRight, 0))
            }
            return builder.build()
        }

        val layoutBase = buildLtr(0)
        val layoutRight = buildLtr(indent)

        println("T10: indent=$indent, text length=${longText.length}")
        println("T10: lineCount base=${layoutBase.lineCount} right=${layoutRight.lineCount}")

        // ── 1. line 0 首字 gph（LTR 首字应在 x≈0，rightIndents 不偏移）──
        val baseGph0 = layoutBase.getPrimaryHorizontal(0)
        val rightGph0 = layoutRight.getPrimaryHorizontal(0)
        println("T10: line 0 首字 gph: base=$baseGph0  rightIndent=$rightGph0  shift=${baseGph0 - rightGph0}")
        assertTrue(
            "T10 失败: LTR rightIndents 不应偏移首字 gph。base=$baseGph0 right=$rightGph0",
            abs(baseGph0 - rightGph0) < 5f
        )

        // ── 2. 换行宽度缩减验证（line 0 更短 = 更早换行）──
        val baseL0Width = layoutBase.getLineWidth(0)
        val rightL0Width = layoutRight.getLineWidth(0)
        val baseL0End = layoutBase.getLineEnd(0)
        val rightL0End = layoutRight.getLineEnd(0)
        println("T10: line 0: base width=$baseL0Width chars=$baseL0End  right width=$rightL0Width chars=$rightL0End")
        assertTrue(
            "T10 失败: rightIndents 应让 line 0 更早换行（width 或 chars 更小）。" +
                "base width=$baseL0Width right width=$rightL0Width",
            rightL0Width <= baseL0Width + 1f
        )

        // ── 3. line 1+ 恢复全宽（rightIndents[1]=0）──
        if (layoutBase.lineCount > 1 && layoutRight.lineCount > 1) {
            val baseL1Width = layoutBase.getLineWidth(1)
            val rightL1Width = layoutRight.getLineWidth(1)
            println("T10: line 1: base width=$baseL1Width  right width=$rightL1Width (应接近，均全宽)")
            // line 1 均为全宽排布（可能因文本不同略有差异，但都应接近 layoutWidth）
            assertTrue(
                "T10 失败: line 1 应恢复全宽。base=$baseL1Width right=$rightL1Width layoutWidth=$layoutWidth",
                rightL1Width > layoutWidth - indent.toFloat()  // 远大于缩窄宽度，说明恢复了全宽
            )
        }

        println("T10 ★★ 通过: LTR rightIndents 不偏移 gph(首字≈0), 仅缩减换行宽度, line 1+ 恢复全宽")
        println("T10 ★★ → LTR run 同样可用 setIndents(null, [indent, 0]) 做首行缩窄")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T11: setIndents 模拟 layoutMixedRun shared-line 场景（端到端验证）
    //
    // 模拟：baseRtl 段落，Run 0 末行占满后 cursor 在某处，Run 1 首行要共享剩余空间。
    // Run 1 用 fullWidth + setIndents(null, [indent, 0]) 测量：
    //   - line 0 应在 [0, fullWidth - indent] 范围内（缩窄到 firstLineWidth = fullWidth - indent）
    //   - line 1+ 应在 [0, fullWidth] 范围内（恢复全宽）
    //   - 所有 gph 值 ∈ [0, fullWidth]
    //
    // 同时验证 RTL 版本：rightIndents 下 line 0 右边界 = fullWidth - indent（右侧留白）
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T11_setIndents_sharedLineSimulation() {
        val fullWidth = layoutWidth  // 600
        val firstLineWidth = 380     // 模拟 cursor - startX = 剩余宽度
        val indent = fullWidth - firstLineWidth  // 220

        val rtlText = "مرحبا بالعالم هذا اختبار طويل للتأكد من وجود عدة أسطر في الفقرة الكاملة"

        // RTL run shared-line 模拟
        val layout = buildRtlLayoutWithIndents(rtlText, 0, indent, 0, 0)

        println("T11: fullWidth=$fullWidth firstLineWidth=$firstLineWidth indent=$indent")
        println("T11: RTL layout ${layout.lineCount} 行")

        // ── 1. line 0 右边界应 ≈ firstLineWidth（= fullWidth - indent，右侧留白 indent）──
        val l0FirstGph = layout.getPrimaryHorizontal(layout.getLineStart(0))
        val l0LastGph = layout.getPrimaryHorizontal(layout.getLineEnd(0) - 1)
        val l0RightEdge = maxOf(l0FirstGph, l0LastGph)
        println("T11: line 0: firstGph=$l0FirstGph lastGph=$l0LastGph rightEdge=$l0RightEdge (应 ≈ $firstLineWidth)")
        assertTrue(
            "T11 失败: line 0 右边界 $l0RightEdge 应 ≈ firstLineWidth=$firstLineWidth (右侧留 indent=$indent)",
            abs(l0RightEdge - firstLineWidth.toFloat()) < 15f
        )

        // ── 2. line 1+ 右边界应 ≈ fullWidth（恢复全宽，无留白）──
        if (layout.lineCount > 1) {
            val l1FirstGph = layout.getPrimaryHorizontal(layout.getLineStart(1))
            val l1LastGph = layout.getPrimaryHorizontal(layout.getLineEnd(1) - 1)
            val l1RightEdge = maxOf(l1FirstGph, l1LastGph)
            println("T11: line 1: firstGph=$l1FirstGph lastGph=$l1LastGph rightEdge=$l1RightEdge (应 ≈ $fullWidth)")
            assertTrue(
                "T11 失败: line 1 右边界 $l1RightEdge 应 ≈ fullWidth=$fullWidth (恢复全宽)",
                abs(l1RightEdge - fullWidth.toFloat()) < 15f
            )
        }

        // ── 3. 所有 gph 值 ∈ [0, fullWidth]（无钳制、无越界）──
        var allInBounds = true
        for (line in 0 until layout.lineCount) {
            val start = layout.getLineStart(line)
            val end = layout.getLineEnd(line)
            for (off in start until end) {
                val gph = layout.getPrimaryHorizontal(off)
                if (gph < -1f || gph > fullWidth + 1f) {
                    println("T11: ⚠ line=$line off=$off gph=$gph 越界 [0, $fullWidth]")
                    allInBounds = false
                }
            }
        }
        assertTrue("T11 失败: 部分 gph 值越界 [0, $fullWidth]", allInBounds)

        println("T11 ★★ 通过: setIndents 模拟 shared-line 场景——line 0 缩窄到 $firstLineWidth, line 1+ 恢复 $fullWidth, gph 全部在界内")
        println("T11 ★★ → 单次 setIndents 测量等价于 narrowed-width + Plan H 两步，且无 gph 钳制风险")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T12: setIndents 对 LTR getPrimaryHorizontal 的影响 —— leftIndents（方向对称验证）
    //
    // T9 证明了 RTL + rightIndents 让 gph 左移 indent（定位偏移生效）。
    // T10 证明了 LTR + rightIndents 不偏移 gph（仅缩减换行宽度）。
    // 本测试验证 LTR + leftIndents：AOSP 源码预测 gph 右移 indent（方向对称于 T9）。
    //
    // 源码预测：ALIGN_NORMAL + LTR → getLineStartPos = 0 + leftIndents[line]
    //   → leftIndents=[indent,0] 时 line 0 起始 x = indent → gph 整体右移 indent
    //   → line 1+ leftIndents[1]=0 → 起始 x = 0 → gph 不偏移
    //
    // 这是「方向对称 indent」方案的基石：baseLtr 用 leftIndents → gph 自动偏移 →
    // 定位公式可简化为 absX = startX + gph（无 anchor / indentCorrection / layoutLeft）。
    //
    // 同时验证 RTL + leftIndents（leftIndents 不应偏移 RTL gph，对称于 T10）。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T12_setIndents_ltr_leftIndents_gphShift() {
        val indent = 150
        val ltrText = "The quick brown fox jumps over the lazy dog and keeps running for many words to fill"

        fun buildLtrWithLeftIndent(firstLeft: Int): StaticLayout {
            val builder = StaticLayout.Builder.obtain(ltrText, 0, ltrText.length, paint, layoutWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(android.text.TextDirectionHeuristics.LTR)
                .setIncludePad(true)
            if (firstLeft > 0) {
                builder.setIndents(intArrayOf(firstLeft, 0), intArrayOf(0, 0))  // ★ leftIndents
            }
            return builder.build()
        }

        val layoutBase = buildLtrWithLeftIndent(0)
        val layoutLeft = buildLtrWithLeftIndent(indent)

        println("T12: indent=$indent, text length=${ltrText.length}")
        println("T12: lineCount base=${layoutBase.lineCount} leftIndent=${layoutLeft.lineCount}")

        // ── 1. line 0 首字 gph：leftIndents 应让 LTR 首字右移 indent ──
        val baseGph0 = layoutBase.getPrimaryHorizontal(0)
        val leftGph0 = layoutLeft.getPrimaryHorizontal(0)
        val shift = leftGph0 - baseGph0   // 正值 = 右移
        println("T12: line 0 首字 gph: base=$baseGph0  leftIndent=$leftGph0  shift=$shift (预期 ≈ +$indent)")
        assertTrue(
            "T12 失败[1]: LTR leftIndents 应让 line 0 首字 gph 右移 indent。" +
                "shift=$shift, 预期 ≈ $indent(±10)",
            abs(shift - indent.toFloat()) < 10f
        )

        // ── 2. line 0 换行宽度缩减（leftIndents 也缩减换行，与 rightIndents 一致）──
        val baseL0Width = layoutBase.getLineWidth(0)
        val leftL0Width = layoutLeft.getLineWidth(0)
        println("T12: line 0 width: base=$baseL0Width  leftIndent=$leftL0Width (后者应更小)")
        assertTrue(
            "T12 失败[2]: leftIndents 应缩减 line 0 换行宽度。base=$baseL0Width left=$leftL0Width",
            leftL0Width <= baseL0Width + 1f
        )

        // ── 3. line 1+ 恢复全宽 + gph 不偏移（leftIndents[1]=0）──
        if (layoutBase.lineCount > 1 && layoutLeft.lineCount > 1) {
            val baseL1Start = layoutBase.getLineStart(1)
            val leftL1Start = layoutLeft.getLineStart(1)
            val baseGph1 = layoutBase.getPrimaryHorizontal(baseL1Start)
            val leftGph1 = layoutLeft.getPrimaryHorizontal(leftL1Start)
            val shift1 = leftGph1 - baseGph1
            println("T12: line 1 首字 gph: base=$baseGph1  leftIndent=$leftGph1  shift=$shift1 (预期 ≈ 0)")
            assertTrue(
                "T12 失败[3]: leftIndents[1]=0 时 line 1 gph 不应偏移。shift=$shift1",
                abs(shift1) < 10f
            )
        }

        // ── 4. RTL + leftIndents：leftIndents 不应偏移 RTL gph（对称于 T10）──
        val rtlText = "مرحبا بالعالم هذا اختبار طويل للتأكد من وجود عدة أسطر في الفقرة"
        val rtlBase = buildRtlLayoutWithIndents(rtlText, 0, 0, 0, 0)
        val rtlLeftIndent = buildRtlLayoutWithIndents(rtlText, indent, 0, 0, 0)  // leftIndents=[indent,0]
        val rtlBaseGph0 = rtlBase.getPrimaryHorizontal(0)
        val rtlLeftGph0 = rtlLeftIndent.getPrimaryHorizontal(0)
        val rtlShift = rtlLeftGph0 - rtlBaseGph0
        println("T12: RTL + leftIndents: base=$rtlBaseGph0  leftIndent=$rtlLeftGph0  shift=$rtlShift (预期 ≈ 0)")
        assertTrue(
            "T12 失败[4]: leftIndents 不应偏移 RTL gph。shift=$rtlShift, 预期 ≈ 0(±10)",
            abs(rtlShift) < 10f
        )

        println("T12 ★★ 通过: LTR + leftIndents → gph 右移 indent + 换行缩窄 + line1 不偏移; RTL + leftIndents → gph 不偏移")
        println("T12 ★★ → 方向对称 indent（baseLtr=leftIndents, baseRtl=rightIndents）成立")
        println("T12 ★★ → 定位公式可简化为 absX = startX + gph（无需 anchor/indentCorrection/layoutLeft）")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T13: off-diagonal indent 的换行收窄验证（填补 T12[4] 的缺口）
    //
    // 方向对称 indent 方案中，indent 类型按段落方向选择（baseRtl→rightIndents, baseLtr→leftIndents），
    // 但段落内可能包含反向 run：
    //   - baseRtl 段落的 LTR run → 用 rightIndents（off-diagonal）
    //   - baseLtr 段落的 RTL run → 用 leftIndents（off-diagonal）
    //
    // T10 已验证 LTR+rightIndents：gph 不偏移 + 换行收窄。
    // T12[4] 仅验证 RTL+leftIndents 的 gph 不偏移，未验证换行收窄。
    //
    // 本测试补全 RTL+leftIndents 的换行收窄验证，确认 off-diagonal indent 同时满足：
    //   ① 换行宽度收窄（line 0 字符更少 / 总行数更多）—— indent 对换行有效
    //   ② gph 不偏移（位置不受 indent 影响）—— 反向 run 自然对齐到远端边缘
    //   ③ 续行恢复全宽（indent[1]=0）—— 仅首行缩窄
    //
    // 关键：setIndents 的两个效果是独立的——
    //   换行收窄走 LineBreaker 路径（leftIndents+rightIndents 求和，方向无关）；
    //   gph 偏移走 getLineStartPos 路径（只有匹配方向的 indent 生效）。
    //   → off-diagonal indent「收窄换行但不偏移 gph」是两个独立机制的必然结果。
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T13_setIndents_offDiagonal_wrappingNarrowing() {
        val indent = 150

        // ══ Case 1: LTR run + rightIndents（复验 T10，确保对比基准一致）══
        val ltrText = "The quick brown fox jumps over the lazy dog and keeps running for many words to fill"
        val ltrBase = StaticLayout.Builder.obtain(ltrText, 0, ltrText.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(android.text.TextDirectionHeuristics.LTR)
            .setIncludePad(true).build()
        val ltrRight = StaticLayout.Builder.obtain(ltrText, 0, ltrText.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(android.text.TextDirectionHeuristics.LTR)
            .setIncludePad(true)
            .setIndents(intArrayOf(0, 0), intArrayOf(indent, 0)).build()

        println("T13[1]: LTR+rightIndents — lineCount base=${ltrBase.lineCount} right=${ltrRight.lineCount}")
        println("T13[1]:   line0 end  base=${ltrBase.getLineEnd(0)}  right=${ltrRight.getLineEnd(0)}")
        println("T13[1]:   gph(0)     base=${ltrBase.getPrimaryHorizontal(0)}  right=${ltrRight.getPrimaryHorizontal(0)}")

        assertTrue(
            "T13[1a]: LTR+rightIndents 应收窄换行（line0 字符更少或行数更多）。" +
                "base end=${ltrBase.getLineEnd(0)} right end=${ltrRight.getLineEnd(0)}",
            ltrRight.getLineEnd(0) < ltrBase.getLineEnd(0) || ltrRight.lineCount > ltrBase.lineCount
        )
        assertTrue(
            "T13[1b]: LTR+rightIndents gph 不应偏移。" +
                "base=${ltrBase.getPrimaryHorizontal(0)} right=${ltrRight.getPrimaryHorizontal(0)}",
            abs(ltrRight.getPrimaryHorizontal(0) - ltrBase.getPrimaryHorizontal(0)) < 5f
        )

        // ══ Case 2: RTL run + leftIndents（★ 填补 T12[4] 缺口）══
        val rtlText = "مرحبا بالعالم هذا اختبار طويل للتأكد من وجود عدة أسطر في الفقرة"
        val rtlBase = buildRtlLayoutWithIndents(rtlText, 0, 0, 0, 0)
        val rtlLeft = buildRtlLayoutWithIndents(rtlText, indent, 0, 0, 0)  // leftIndents=[indent,0]

        val rtlBaseL0End = rtlBase.getLineEnd(0)
        val rtlLeftL0End = rtlLeft.getLineEnd(0)
        println("T13[2]: RTL+leftIndents — lineCount base=${rtlBase.lineCount} left=${rtlLeft.lineCount}")
        println("T13[2]:   line0 end   base=$rtlBaseL0End  left=$rtlLeftL0End")
        println("T13[2]:   line0 width base=${rtlBase.getLineWidth(0)}  left=${rtlLeft.getLineWidth(0)}")
        println("T13[2]:   gph(0)      base=${rtlBase.getPrimaryHorizontal(0)}  left=${rtlLeft.getPrimaryHorizontal(0)}")

        // 2a. ★ 核心断言：leftIndents 应让 RTL 文本 line 0 更早换行（字符更少）或产生更多行
        assertTrue(
            "T13[2a] 失败: RTL+leftIndents 应收窄换行（line0 字符更少或行数更多）。" +
                "base end=$rtlBaseL0End left end=$rtlLeftL0End " +
                "base lines=${rtlBase.lineCount} left lines=${rtlLeft.lineCount}",
            rtlLeftL0End < rtlBaseL0End || rtlLeft.lineCount > rtlBase.lineCount
        )

        // 2b. gph 不偏移（复验 T12[4]）
        assertTrue(
            "T13[2b] 失败: RTL+leftIndents gph 不应偏移。" +
                "base=${rtlBase.getPrimaryHorizontal(0)} left=${rtlLeft.getPrimaryHorizontal(0)}",
            abs(rtlLeft.getPrimaryHorizontal(0) - rtlBase.getPrimaryHorizontal(0)) < 10f
        )

        // 2c. 续行恢复全宽（leftIndents[1]=0）—— line 1 字符数应接近基线
        if (rtlBase.lineCount > 1 && rtlLeft.lineCount > 1) {
            val baseL1End = rtlBase.getLineEnd(1)
            val leftL1End = rtlLeft.getLineEnd(1)
            val baseL1Chars = baseL1End - rtlBase.getLineStart(1)
            val leftL1Chars = leftL1End - rtlLeft.getLineStart(1)
            println("T13[2c]: line1 chars base=$baseL1Chars left=$leftL1Chars (续行均全宽，应接近)")
            assertTrue(
                "T13[2c] 失败: 续行应恢复全宽（字符数不应大幅少于基线）。base=$baseL1Chars left=$leftL1Chars",
                leftL1Chars >= baseL1Chars - 2   // 允许 word-break 级别差异
            )
        }

        println("T13 ★★ 通过: off-diagonal indent 两种组合均「换行收窄 + gph 不偏移 + 续行全宽」")
        println("T13 ★★ → 反向 run 的 indent 有效：收窄换行让文本在剩余空间内断行，gph 不偏移让文本自然对齐到远端边缘")
        println("T13 ★★ → 方向对称 indent 在全部 4 种组合（同向×2 + 反向×2）下行为一致且正确")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 辅助：构建 RTL + 自定义 setIndents 的 layout（T7 专用）
    //
    // setIndents(left[], right[]) 语义（Android 官方文档）：
    //   - 第一参数 left[]：[首行左缩进, 后续行左缩进]
    //   - 第二参数 right[]：[首行右缩进, 后续行右缩进]
    // 注意：这里的 left/right 在 RTL 下是「视觉左右」还是「逻辑起始/结束边」就是 T7 要验证的
    // ─────────────────────────────────────────────────────────────────────────
    private fun buildRtlLayoutWithIndents(
        text: String,
        firstLeft: Int,
        firstRight: Int,
        restLeft: Int,
        restRight: Int
    ): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setTextDirection(android.text.TextDirectionHeuristics.RTL)
        if (firstLeft > 0 || firstRight > 0 || restLeft > 0 || restRight > 0) {
            builder.setIndents(intArrayOf(firstLeft, restLeft), intArrayOf(firstRight, restRight))
        }
        return builder.build()
    }
}
