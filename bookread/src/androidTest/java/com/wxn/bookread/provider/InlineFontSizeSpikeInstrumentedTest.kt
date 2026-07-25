package com.wxn.bookread.provider

import android.graphics.Color
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.RelativeSizeSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Spike(instrumented,Phase 1.1 前置):验证 inline font-size 方案的 Span 度量根基。
 *
 * 用 instrumented test 而非 Robolectric,是因为 **StaticLayout 是 native 实现**,
 * Robolectric 的 shadow 不做真实文本测量(已验证:lineCount 恒为 1、getLineWidth 恒为 12)。
 * 参见项目既有经验:`StaticLayoutHyphenationSpikeInstrumentedTest` 头注释。
 *
 * 本文件覆盖 T0c/T0d(需要真实文本度量的部分)。T0a/T0b/T0e 不依赖真实渲染,
 * 保留在 unitTest(`InlineFontSizeSpikeTest`)。
 *
 * 2 条用例:
 * - T0c:RelativeSizeSpan 确实影响 getLineWidth(整个方案根基,失败则推倒重来)
 * - T0d:混合字号行高反映行内最大字号(P0-5 行高策略)
 *
 * 运行(需连接设备/模拟器):
 *   `gradlew.bat :bookread:connectedDebugAndroidTest --tests
 *    "com.wxn.bookread.provider.InlineFontSizeSpikeInstrumentedTest"`
 *
 * 决策来源:`docs/plans/plan-inline-fontsize-phase1.md` §6.1
 */
@RunWith(AndroidJUnit4::class)
class InlineFontSizeSpikeInstrumentedTest {

    private lateinit var paint: TextPaint
    private val visibleWidth = 600  // px,足够宽避免单字号被强制换行

    @Before
    fun setUp() {
        paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 40f   // 段落默认字号
            isAntiAlias = true
        }
    }

    private fun buildPlain(text: String, width: Int = visibleWidth): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .build()

    private fun buildSpanned(
        text: String,
        spans: List<Triple<Int, Int, Float>>,
        width: Int = visibleWidth
    ): StaticLayout {
        val ssb = SpannableStringBuilder(text)
        spans.forEach { (s, e, scale) ->
            ssb.setSpan(RelativeSizeSpan(scale), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return StaticLayout.Builder.obtain(ssb, 0, ssb.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T0c:Span 路径验证 - RelativeSizeSpan 确实影响 getLineWidth
    //   → 这是整个方案的根基,失败则方案推倒重来
    //   注:androidTest 编译成 DEX 时方法名不能含空格,故用下划线
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T0c_relativeSizeSpan_affects_getLineWidth() {
        val text = "大字号ABC小字号DEF"   // 9 字符
        val plainLayout = buildPlain(text)
        val spannedLayout = buildSpanned(text, listOf(Triple(0, 3, 1.5f)))   // 前 3 字放大 1.5

        val plainWidth = plainLayout.getLineWidth(0)
        val spannedWidth = spannedLayout.getLineWidth(0)
        val diff = spannedWidth - plainWidth

        println("T0c: plainWidth=$plainWidth spannedWidth=$spannedWidth diff=$diff")

        assertTrue(
            "T0c 失败:Span 路径未生效。spannedWidth=$spannedWidth 与 plainWidth=$plainWidth 差值 $diff ≤ 0\n" +
                "→ RelativeSizeSpan 未影响 StaticLayout 度量,整个方案推倒重来",
            diff > 1f
        )
        // 进一步验证:Span 区域的宽度增量 ≈ 原区域宽度 × 0.5(放大 1.5 倍,增量 0.5)
        val plainFirst3Width = StaticLayout.getDesiredWidth("大字号", paint)
        val expectedIncrement = plainFirst3Width * 0.5f
        assertTrue(
            "T0c 失败:Span 增量 $diff 与预期 $expectedIncrement 偏差过大(应 ≈ 原 3 字宽度 × 0.5)",
            abs(diff - expectedIncrement) < expectedIncrement * 0.3f   // 30% 容差
        )
        println("T0c ★ 通过:Span 路径生效,RelativeSizeSpan 正确影响 StaticLayout 度量")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // T0d:混合字号行高反映行内最大字号
    //   → 验证 P0-5 行高策略(layout.getLineDescent 在混合字号下增大)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun T0d_mixedFontSizes_lineMetrics_reflect_largestSize() {
        val text = "大字号ABC小字号DEF"   // 单行(宽度足够,不换行)
        val plainLayout = buildPlain(text)
        val spannedLayout = buildSpanned(
            text,
            listOf(Triple(0, 3, 1.5f))   // 前 3 字 1.5em
        )

        val plainAscent = -plainLayout.getLineAscent(0).toFloat()
        val plainDescent = plainLayout.getLineDescent(0).toFloat()
        val spannedAscent = -spannedLayout.getLineAscent(0).toFloat()
        val spannedDescent = spannedLayout.getLineDescent(0).toFloat()

        println("T0d: plain ascent=$plainAscent descent=$plainDescent")
        println("T0d: spanned ascent=$spannedAscent descent=$spannedDescent(行内有 1.5em 字号)")
        println("T0d: ascent 增量=${spannedAscent - plainAscent}, descent 增量=${spannedDescent - plainDescent}")

        // 1.5em 字号的 ascent/descent 应明显大于默认字号
        assertTrue(
            "T0d 失败:混合字号 ascent=$spannedAscent 未大于单一字号 ascent=$plainAscent\n" +
                "→ 行高未反映行内最大字号,P0-5 行高策略失效",
            spannedAscent > plainAscent + 1f
        )
        assertTrue(
            "T0d 失败:混合字号 descent=$spannedDescent 未大于单一字号 descent=$plainDescent",
            spannedDescent > plainDescent + 1f
        )
        // 进一步:ascent 增量应接近「原 ascent × 0.5」(1.5em 增量 0.5)
        val expectedAscentIncrement = plainAscent * 0.5f
        val actualAscentIncrement = spannedAscent - plainAscent
        assertTrue(
            "T0d 失败:ascent 增量 $actualAscentIncrement 与预期 $expectedAscentIncrement(原 ascent × 0.5)偏差过大",
            abs(actualAscentIncrement - expectedAscentIncrement) < expectedAscentIncrement * 0.4f
        )
        println("T0d ★ 通过:混合字号行高正确反映行内最大字号,P0-5 行高策略有效")
    }
}
