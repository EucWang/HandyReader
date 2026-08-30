package com.wxn.bookread.provider

import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextDirection
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.jni.SheenBidiNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (设备/模拟器): D 方向解耦守卫测试
 * （docs/plans/2026-08-29-plan-u5-mixed-base-ltr-line-order-fix.md §7.1）。
 *
 * 守卫目标：
 *   1. 排版基调 baseRtl 恒 = SheenBidi 首强（显式 dir 声明不再强制基级，U5 缺陷根消除）；
 *   2. declaredRtl 唯一消费 = 锚点方向 anchorBaseRtl（= declaredRtl ?: baseRtl）；
 *   3. U5 形态段（阿语 + 数字 + 尾部英文）在 RTL 基调下端到端排版行序 = 逻辑序。
 *
 * D1 同时承担原方案 §7.0（S0 验证门）的首强侧断言：baseLevel 奇偶 + 视觉序 runs 的
 * 整段反转结构（SheenBidi 视觉序语义已由 RTLSegmenterInstrumentedTest B1/B2 钉住）。
 *
 * 运行(需连接设备):
 *   gradlew.bat :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.DirectionDecoupleInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class DirectionDecoupleInstrumentedTest {

    /** U5 形态段：阿语块 + 数字 + 阿语块 + 尾部英文（首强 = 阿语 → RTL 基调） */
    private val u5LikeText =
        "هذه فقرة اختبار عربية طويلة تحتوي على كلمات كثيرة في السطر " +
            "والثاني والثالث لتغطية أكثر من سطر عند العرض 123 والكلمات " +
            "الإنجليزية مثل technology."

    @Before
    fun setUp() {
        // .so 随 APK 分发，设备上必须可用；不可用 = 打包/ABI 问题，直接失败
        assertTrue(
            "SheenBidiNative.available=false —— .so 未打进测试 APK（ABI/打包问题），本测试失去意义",
            SheenBidiNative.available
        )
    }

    /**
     * D1: segment(U5 形态段, declaredRtl = false) →
     * 排版基调 = 首强 RTL（声明不再强制基级 0）；锚点方向 = 声明（LTR）；
     * runs 为视觉序且满足整段反转结构（reversed 后 offset 升序连续覆盖 [0, len)）。
     */
    @Test
    fun D1_u5Arabic_declaredLtr_baseStaysFirstStrong() {
        val seg = RTLSegmenter.segment(u5LikeText, declaredRtl = false)
        println("D1: direction=${seg.direction} baseRtl=${seg.baseRtl} " +
            "anchorBaseRtl=${seg.anchorBaseRtl} runs=${seg.runs.size}")
        assertTrue("D1 失败: 首强阿语段排版基调应为 RTL（D 下声明不强制基级）", seg.baseRtl)
        assertFalse("D1 失败: 显式 dir=ltr 应传导到锚点方向（anchorBaseRtl=false）", seg.anchorBaseRtl)
        assertEquals("D1 失败: direction 应为 RTL（基调）", TextDirection.RTL, seg.direction)
        assertTrue("D1 失败: 混排段 runs 应保留（视觉序）", seg.runs.isNotEmpty())

        // 基级 1 下 L2 = 整段反转 → runs reversed 即逻辑序（消费序=逻辑序的结构前提）
        var expect = 0
        for (run in seg.runs.asReversed()) {
            assertEquals(
                "D1 失败: runs 反转后应按逻辑序连续覆盖 [0,len)，实际 offset=${run.offset} expect=$expect",
                expect, run.offset
            )
            expect += run.length
        }
        assertEquals("D1 失败: runs 反转后应覆盖全段", u5LikeText.length, expect)
        println("D1 ★ 通过: 声明不强制基调；视觉序 runs 整段反转结构成立（消费序=逻辑序的前提）")
    }

    /** D2: 无声明 → 锚点回退首强，anchorBaseRtl == baseRtl 恒等式 */
    @Test
    fun D2_noDeclaration_anchorFallsBackToFirstStrong() {
        val seg = RTLSegmenter.segment(u5LikeText)
        assertTrue("D2 失败: 首强阿语段 baseRtl 应为 true", seg.baseRtl)
        assertEquals("D2 失败: 无声明时 anchorBaseRtl 应恒等于 baseRtl",
            seg.baseRtl, seg.anchorBaseRtl)
        println("D2 ★ 通过: 无声明回退首强（锚点与排版同向）")
    }

    /** D3: 英文段含阿语词 + declaredRtl=true → 排版基调 LTR（首强）、锚点方向 RTL（声明） */
    @Test
    fun D3_englishWithArabic_declaredRtl_splitDirections() {
        val text = "A mixed English paragraph with Arabic كلمة inside"
        val seg = RTLSegmenter.segment(text, declaredRtl = true)
        println("D3: direction=${seg.direction} baseRtl=${seg.baseRtl} " +
            "anchorBaseRtl=${seg.anchorBaseRtl} runs=${seg.runs.size}")
        assertFalse("D3 失败: 首强 LTR 段排版基调应为 false", seg.baseRtl)
        assertTrue("D3 失败: 显式声明应传导到锚点方向", seg.anchorBaseRtl)
        assertEquals("D3 失败: direction 应为 LTR", TextDirection.LTR, seg.direction)
        assertTrue("D3 失败: 混排段 runs 应保留", seg.runs.isNotEmpty())
        println("D3 ★ 通过: 拉丁首强 + RTL 声明 → 排版/锚点方向分离")
    }

    /**
     * D4: 端到端（layoutNormalTextRtl 真实引擎）：U5 形态段以 RTL 基调排版，
     * 行序 = 逻辑序——各行 [charStartOffset, charEndOffset) 首尾相接且并集 = [0, len)。
     * 该判据独立于字号/栏宽的折行位置，是跨行阅读序错乱缺陷类的普适判据。
     */
    @Test
    fun D4_u5Arabic_endToEnd_lineOrderIsLogical() {
        ChapterProvider.apply {
            paddingHorizontal = 24
            paddingVertical = 24
            visibleWidth = 1000
            visibleHeight = 20000
            visibleRight = paddingHorizontal + visibleWidth
            visibleBottom = paddingVertical + visibleHeight
            lineSpacingExtra = 1.2f
            dualColumnEnabled = false
        }
        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 44f
            isAntiAlias = true
        }

        val seg = RTLSegmenter.segment(u5LikeText, declaredRtl = false)
        assertTrue("D4 前置失败: 该段应以 RTL 基调排版", seg.baseRtl)

        val textPages = arrayListOf(TextPage())
        TextLayoutProvider.layoutNormalTextRtl(
            u5LikeText, null, seg, paint,
            marginLeft = 0f, marginRight = 0f,
            firstLineIndent = 0f,
            isTitle = false, isListRow = false, listLevel = 0,
            paragraphIndex = 0,
            textAlign = com.wxn.base.bean.CssTextAlign.CssTextAlignUndefined,
            lineHeightParam = 1f,
            paragraph = ReaderText.Text(u5LikeText),
            textPages = textPages,
            pageLines = arrayListOf(),
            pageLengths = arrayListOf(),
            stringBuilder = StringBuilder(),
            offsetY = ChapterProvider.paddingVertical.toFloat(),
            bounds = layoutBoundsPage(),
            chapterIsRtl = true,
            hasInlineImage = false
        )

        val lines = textPages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }
        assertTrue("D4 失败: 应折为多行（窄栏前提），实际 ${lines.size} 行", lines.size >= 2)
        lines.forEachIndexed { i, l ->
            println("D4 L$i isRtl=${l.isRtl} [${l.charStartOffset},${l.charEndOffset}) '${l.text}'")
        }

        // 主守卫：行序 = 逻辑序（offset 区间首尾相接、覆盖 [0, len)）
        var expect = 0
        for ((i, line) in lines.withIndex()) {
            assertEquals("D4 失败: L$i 行起始偏移应无缝衔接（行序=逻辑序）",
                expect, line.charStartOffset)
            expect = line.charEndOffset
        }
        assertEquals("D4 失败: 行区间并集应覆盖全段", u5LikeText.length, expect)

        // 行内容拼接 = 逻辑全文（忽略空白差异：折行点空格归属属引擎既有口径）
        val norm: (String) -> String = { s -> s.filter { !it.isWhitespace() } }
        val joined = lines.joinToString("") { it.text }
        assertEquals("D4 失败: 各行 text 拼接应等于原逻辑全文（忽略空白）",
            norm(u5LikeText), norm(joined))

        // smoke：首行不应包含段尾英文词（段首/段尾同行的错乱类特征）
        assertFalse("D4 失败: 首行不应包含段尾 technology.",
            lines.first().text.contains("technology."))
        println("D4 ★ 通过: RTL 基调下跨行阅读序 = 逻辑序（U5 缺陷消除的端到端守卫）")
    }
}
