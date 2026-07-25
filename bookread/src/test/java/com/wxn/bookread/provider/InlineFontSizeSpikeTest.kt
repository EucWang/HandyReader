package com.wxn.bookread.provider

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

/**
 * Spike(Phase 1.1 前置,Robolectric 部分):验证 inline font-size 方案的行高公式根基。
 *
 * 本文件覆盖 T0a/T0b/T0e —— 这三条不依赖真实文本渲染,只需 Layout 内部数值自洽,
 * Robolectric 的 StaticLayout shadow 可胜任。
 *
 * T0c/T0d(Span 是否真实影响度量)依赖 native StaticLayout,必须用 instrumented test,
 * 见 `androidTest/.../InlineFontSizeSpikeInstrumentedTest`。
 * (Robolectric 的 StaticLayout shadow 不做真实测量 —— 已由项目既有的
 * `StaticLayoutHyphenationSpikeInstrumentedTest` 验证:lineCount 恒为 1、getLineWidth 恒为固定值)
 *
 * 用例结论(任一失败需调整方案):
 * - T0a 失败 → F3 公式 (descent - ascent) 不等于权威行高 (bottom - top),需调研
 * - T0b 首末行差异 > 1px → F3 实施时首末行需降级旧公式(警告,不阻塞)
 * - T0e 失败 → leading 处理方式需调整
 *
 * 运行:`gradlew.bat :bookread:testDebugUnitTest --tests "*InlineFontSizeSpikeTest"`
 *
 * 决策来源:`docs/plans/plan-inline-fontsize-phase1.md` §6.1
 */
@RunWith(RobolectricTestRunner::class)
class InlineFontSizeSpikeTest {

    private lateinit var paint: TextPaint
    private val visibleWidth = 600  // px,足够宽避免单字号都被强制换行

    @Before
    fun setUp() {
        paint = TextPaint().apply {
            textSize = 40f   // 段落默认字号
            isAntiAlias = true
        }
    }

    /** 构造单一字号(纯 String)的 StaticLayout,setIncludePad 与项目一致用 true。 */
    private fun buildPlain(text: String, width: Int = visibleWidth): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .build()

    // ─────────────────────────────────────────────────────────────────────────
    // T0a:无 Span 的中间行,layout 内部行高自洽(descent - ascent == bottom - top)
    //   → 验证 F3 公式 (descent - ascent) 可替代 textHeight(=fontMetrics.descent - ascent + leading)
    //   注:Robolectric 下 paint.fontMetrics 返回全 0(未真实渲染),故改用 layout 内部
    //      getLineTop/getLineBottom 作为权威值对比
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `T0a plain text middle line - layout height self-consistent`() {
        // 多行文本,取中间行(lineIndex=1)避免 setIncludePad 首末行影响
        val text = "第一行文本用于填充\n第二行作为中间行测试\n第三行文本用于填充"
        val layout = buildPlain(text)

        assertTrue("应至少 3 行,实际 ${layout.lineCount}", layout.lineCount >= 3)

        val midLine = 1
        val lineTop = layout.getLineTop(midLine).toFloat()
        val lineBottom = layout.getLineBottom(midLine).toFloat()
        val ascent = layout.getLineAscent(midLine).toFloat()    // 负值
        val descent = layout.getLineDescent(midLine).toFloat()  // 正值

        val heightByTopBottom = lineBottom - lineTop
        val heightByMetrics = descent - ascent   // 候选公式(不含 leading)

        println("T0a: lineTop=$lineTop lineBottom=$lineBottom → topBottom=$heightByTopBottom")
        println("T0a: ascent=$ascent descent=$descent → descent-ascent=$heightByMetrics")
        println("T0a: 差值=${abs(heightByTopBottom - heightByMetrics)}")

        assertTrue(
            "T0a 失败:descent-ascent=$heightByMetrics 与权威 top-bottom=$heightByTopBottom 差异 > 1px\n" +
                "→ F3 公式 (descent - ascent) 在无 inline 段落不成立",
            abs(heightByTopBottom - heightByMetrics) <= 1f
        )
        println("T0a ★ 通过:descent-ascent 即权威行高,F3 公式正确")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T0b:setIncludePad(true) 首末行与中间行的 ascent/descent 差异
    //   → 若首行 ascent 或末行 descent 比中间行偏大 > 1px,F3 需首末行降级旧公式
    //   注:Robolectric 下用中间行作为基准(而非 fontMetrics,后者在 Robolectric 返回 0)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `T0b first and last line deviation under setIncludePad true`() {
        val text = "第一行文本用于填充\n第二行作为中间行测试\n第三行文本用于填充"
        val layout = buildPlain(text)

        val firstAscent = -layout.getLineAscent(0).toFloat()
        val lastDescent = layout.getLineDescent(layout.lineCount - 1).toFloat()
        val midAscent = -layout.getLineAscent(1).toFloat()
        val midDescent = layout.getLineDescent(1).toFloat()

        val firstDiff = abs(firstAscent - midAscent)
        val lastDiff = abs(lastDescent - midDescent)
        println("T0b: firstAscent=$firstAscent midAscent=$midAscent firstDiff=$firstDiff")
        println("T0b: lastDescent=$lastDescent midDescent=$midDescent lastDiff=$lastDiff")

        // 决策:首末行差异是否可接受(≤ 1px 则 F3 统一公式可用于所有行)
        // 若差异较大,记录现象但不强制失败(Robolectric 环境与真机有差异,实施时再核实)
        if (firstDiff > 1f || lastDiff > 1f) {
            println("T0b 警告:首末行与中间行差异 > 1px(firstDiff=$firstDiff, lastDiff=$lastDiff)")
            println("        → F3 实施时需在真机核实,必要时首末行降级 upTopBottom(durY, textPaint)")
        } else {
            println("T0b ★ 通过:首末行差异 ≤ 1px,F3 统一公式可直接用于所有行")
        }
        assertTrue("T0b 应允许 setIncludePad 首末行有差异(本断言仅记录,不阻塞)", true)
    }

    // T0c / T0d 见 androidTest/InlineFontSizeSpikeInstrumentedTest(需真实 native StaticLayout)

    // ─────────────────────────────────────────────────────────────────────────
    // T0e:验证 leading 是否被重复计入
    //   对比 getLineBottom - getLineTop 与 getLineDescent - getLineAscent
    //   (getLineLeading API 在本 SDK 不存在,见 LayoutApiProbeTest)
    //   → 若 bottom-top == descent-ascent:F3 公式应用 `(descent - ascent)`,不加 leading
    //   → 若 bottom-top > descent-ascent:leading 需单独加(但 leading API 不存在,需用 paint.fontMetrics.leading)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun `T0e verify leading is not double counted in line height formula`() {
        val text = "第一行文本用于填充\n第二行作为中间行测试\n第三行文本用于填充"
        val layout = buildPlain(text)

        // 取中间行(lineIndex=1)和首行(0)分别验证
        for (lineIndex in 0 until layout.lineCount) {
            val lineTop = layout.getLineTop(lineIndex).toFloat()
            val lineBottom = layout.getLineBottom(lineIndex).toFloat()
            val ascent = layout.getLineAscent(lineIndex).toFloat()    // 负值
            val descent = layout.getLineDescent(lineIndex).toFloat()  // 正值

            val heightByTopBottom = lineBottom - lineTop
            val heightByMetrics = descent - ascent

            println(
                "T0e line=$lineIndex: topBottom=$heightByTopBottom | " +
                    "descent-ascent=$heightByMetrics | " +
                    "diff=${heightByTopBottom - heightByMetrics}"
            )
        }

        // 单独取中间行做严格断言(避免 setIncludePad 首末行干扰)
        val midLine = 1
        val midHeightByTopBottom = (layout.getLineBottom(midLine) - layout.getLineTop(midLine)).toFloat()
        val midAscent = layout.getLineAscent(midLine).toFloat()
        val midDescent = layout.getLineDescent(midLine).toFloat()
        val midHeightByMetrics = midDescent - midAscent

        val diff = abs(midHeightByTopBottom - midHeightByMetrics)

        println("\nT0e 结论(中间行):")
        println("  getLineBottom - getLineTop = $midHeightByTopBottom (Android 内部权威行高)")
        println("  descent - ascent           = $midHeightByMetrics (F3 候选公式)")
        println("  diff = $diff")

        if (diff < 1f) {
            println("T0e ★ 结论:descent-ascent 即权威行高,leading 已隐含,F3 公式应用 (descent - ascent)")
            println("        → 修正:actualLineHeight = (lineDescent - lineAscent) × lineSpacingExtra × lineHeightParam")
        } else {
            println("T0e ★ 结论:descent-ascent 与权威行高差 $diff,leading 未隐含")
            println("        → F3 公式需补 leading,但 getLineLeading 不存在,需用 paint.fontMetrics.leading")
        }

        assertTrue(
            "T0e 验证:中间行 descent-ascent 应与 top-bottom 对齐(±1px),实际差 $diff",
            diff < 1f
        )
    }
}
