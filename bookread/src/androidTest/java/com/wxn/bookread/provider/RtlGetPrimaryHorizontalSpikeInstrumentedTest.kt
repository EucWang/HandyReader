package com.wxn.bookread.provider

import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
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
