package com.wxn.bookread.provider

import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RTL 首行缩进一致性回归（fix-rtl-first-line-indent-inconsistency.md AC1/AC2/AC4/AC5/AC6）。
 *
 * 缺陷：原 applyFirstLineIndent 事后平移 + min(缩进, 剩余宽度) 截断 →
 *   短首行拿全额缩进、满宽首行缩进 0 → 同级列表段（LD-B 单级 ul 前两项）起始边不一致，
 *   圆点（锚定行内容右缘 +30px）随之错位；Calibre 对照为右缘对齐。
 *   红灯实证（2026-08-26, Mi 10）：short=881.6 long=947.4（cap 截断 30.2px）。
 *
 * 修复后语义（预约式，与 LTR StaticLayout.setIndents 同构）：首 Run 折行前扣减 line0
 *   盒宽 + cursor/锚定边同步内缩 ⇒ 首行墨迹起始边恒定，满宽首行提前折行。
 *
 * 注意：ChapterProvider 伴生对象几何状态跨测试共享且初始为 0——凡依赖
 *   visibleHeight/visibleBottom 计算的输入（如 T4 的 offY）必须在 configure() 之后取值。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.RtlFirstLineIndentUniformInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class RtlFirstLineIndentUniformInstrumentedTest {

    /** LD-B 单级 ul 前两项同源：短项（单行）与长项（多行折行） */
    private val shortRtl = "العنصر الأول قصير"
    private val longRtl =
        "العنصر الثاني طويل جدا ويتوقع أن يلتف على أكثر من سطر واحد للتأكد من أن النقطة تظهر على السطر الأول فقط"

    /** LTR 基调混排（首强字符 'A' → 基调 LTR，走 RTL 引擎的 baseLtr 分支） */
    private val shortLtr = "A short LTR line"
    private val longLtr =
        "A much longer mixed paragraph with Arabic كلمة and number 42 and URL https://example.com that must wrap onto several lines here"

    private fun configure(width: Int, padding: Int) {
        ChapterProvider.apply {
            paddingHorizontal = padding
            paddingVertical = padding
            visibleWidth = width
            visibleHeight = 20000
            visibleRight = paddingHorizontal + visibleWidth
            visibleBottom = paddingVertical + visibleHeight
            lineSpacingExtra = 1.2f
            dualColumnEnabled = false
            columnGapActual = 0
            columnWidth = 0
        }
    }

    private fun layoutPages(
        text: String, baseRtl: Boolean, indent: Float, align: CssTextAlign,
        mL: Float, mR: Float, width: Int, textSize: Float, offsetY: Float
    ): ArrayList<TextPage> {
        configure(width, 40)
        val paint = TextPaint().apply {
            color = Color.BLACK
            this.textSize = textSize
            isAntiAlias = true
        }
        val seg = RTLSegmenter.segment(text)
        val textPages = arrayListOf(TextPage())
        TextLayoutProvider.layoutNormalTextRtl(
            text,
            null,
            seg,
            paint,
            marginLeft = mL,
            marginRight = mR,
            firstLineIndent = indent,
            isTitle = false,
            isListRow = true,
            listLevel = 1,
            paragraphIndex = 0,
            textAlign = align,
            lineHeightParam = 1f,
            paragraph = ReaderText.Text(text),
            textPages = textPages,
            pageLines = arrayListOf(),
            pageLengths = arrayListOf(),
            stringBuilder = StringBuilder(),
            offsetY = offsetY,
            bounds = layoutBoundsPage(),
            chapterIsRtl = baseRtl,
            hasInlineImage = false
        )
        return textPages
    }

    /** 行的墨迹边缘（排除空白与图片）：与既有测试同口径 */
    private fun inkBounds(line: TextLine): Pair<Float, Float>? {
        val ink = line.textChars.filter {
            !it.isImage && it.charData.firstOrNull()?.isWhitespace() != true
        }
        if (ink.isEmpty()) return null
        return Pair(ink.minOf { it.start }, ink.maxOf { it.end })
    }

    private fun inkRight(pages: List<TextPage>, lineIdx: Int): Float {
        val lines = pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }
        assertTrue("行数不足: 需要 index=$lineIdx, 实际 ${lines.size} 行", lines.size > lineIdx)
        val ink = inkBounds(lines[lineIdx])
        assertTrue("第 $lineIdx 行无墨迹（异常）", ink != null)
        return ink!!.second
    }

    private fun inkLeft(pages: List<TextPage>, lineIdx: Int): Float {
        val lines = pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }
        assertTrue("行数不足: 需要 index=$lineIdx, 实际 ${lines.size} 行", lines.size > lineIdx)
        val ink = inkBounds(lines[lineIdx])
        assertTrue("第 $lineIdx 行无墨迹（异常）", ink != null)
        return ink!!.first
    }

    private fun lineCount(pages: List<TextPage>): Int =
        pages.flatMap { it.textLines }.count { it.textChars.isNotEmpty() }

    // ───────── T1：RTL 基调首行起始边一致性（主回归断言，红灯用例） ─────────

    @Test
    fun rtl_firstLineIndent_uniform_shortVsLongParagraphs() {
        for (textSize in floatArrayOf(48f, 64f)) {
            for (align in listOf(
                CssTextAlign.CssTextAlignUndefined,   // 默认路径：effAlign 映射 Right
                CssTextAlign.CssTextAlignLeft         // 用户左对齐：引擎层同样映射 Right（Fix A 在入口层另行抑制）
            )) {
                val indent = 2f * textSize
                val mR = 60f
                val width = 1000

                val shortPages = layoutPages(shortRtl, true, indent, align, 0f, mR, width, textSize, 40f)
                val longPages = layoutPages(longRtl, true, indent, align, 0f, mR, width, textSize, 40f)

                // 前置：短段单行、长段折行（覆盖满宽首行场景）
                assertEquals(
                    "ts=$textSize 短段应恰为单行", 1, lineCount(shortPages)
                )
                assertTrue(
                    "ts=$textSize 长段必须折行(实际 ${lineCount(longPages)} 行)",
                    lineCount(longPages) > 1
                )

                // 主断言：短段与长段首行墨迹右缘一致（修复前差 ≈ indent）
                val shortFirst = inkRight(shortPages, 0)
                val longFirst = inkRight(longPages, 0)
                assertTrue(
                    "ts=$textSize align=$align 首行起始边不一致: short=$shortFirst long=$longFirst diff=${shortFirst - longFirst}",
                    kotlin.math.abs(shortFirst - longFirst) <= 2.5f
                )

                // 缩进量恒定：长段「续行 - 首行」右缘差 ≈ indent（修复前满宽首行 diff≈0）
                val longSecond = inkRight(longPages, 1)
                assertTrue(
                    "ts=$textSize align=$align 续行-首行右缘差 ${longSecond - longFirst} 应≈indent=$indent",
                    kotlin.math.abs((longSecond - longFirst) - indent) <= 3f
                )
                println("[PASS] T1 ts=$textSize align=$align first=$longFirst cont=$longSecond")
            }
        }
    }

    // ───────── T2：零缩进基线（AC4 现有行为零影响） ─────────

    @Test
    fun rtl_zeroIndent_firstLineEqualsContinuation() {
        for (textSize in floatArrayOf(48f, 64f)) {
            val pages = layoutPages(
                longRtl, true, 0f, CssTextAlign.CssTextAlignUndefined, 0f, 60f, 1000, textSize, 40f
            )
            assertTrue("ts=$textSize 应折行", lineCount(pages) > 1)
            val first = inkRight(pages, 0)
            val second = inkRight(pages, 1)
            assertTrue(
                "ts=$textSize 零缩进下首行/续行右缘应一致: $first vs $second",
                kotlin.math.abs(first - second) <= 2.5f
            )
            println("[PASS] T2 ts=$textSize first=$first second=$second")
        }
    }

    // ───────── T3：LTR 基调（RTL 引擎 baseLtr 分支）方向对称性 ─────────
    // 注：本用例在修复前偶然通过（per-run 折行粒度使长段首行剩余 ≥96px，cap 未截断），
    //     定位为"修复后不变量"断言而非红灯证据。

    @Test
    fun ltrBase_firstLineIndent_leftEdgeUniform() {
        val indent = 2f * 48f
        val mL = 60f
        val shortPages = layoutPages(shortLtr, false, indent, CssTextAlign.CssTextAlignLeft, mL, 0f, 1000, 48f, 40f)
        val longPages = layoutPages(longLtr, false, indent, CssTextAlign.CssTextAlignLeft, mL, 0f, 1000, 48f, 40f)

        assertTrue("LTR 长段必须折行(实际 ${lineCount(longPages)} 行)", lineCount(longPages) > 1)

        val shortFirst = inkLeft(shortPages, 0)
        val longFirst = inkLeft(longPages, 0)
        assertTrue(
            "LTR 基调首行左缘不一致: short=$shortFirst long=$longFirst",
            kotlin.math.abs(shortFirst - longFirst) <= 2.5f
        )
        // 首行左缘 = 列起始边 + marginLeft + indent（± inkPad 余量）
        val expectedStart = 40f + mL + indent
        assertTrue(
            "LTR 首行左缘 $longFirst 应≈ start+mL+indent=$expectedStart",
            longFirst >= expectedStart - 1f && longFirst <= expectedStart + TextLayoutProvider.inkPad(48f) + 1.5f
        )
        println("[PASS] T3 first=$longFirst expected=$expectedStart")
    }

    // ───────── T4：跨页——首行落新页，缩进仍生效（AC6 换页 cursor 重置路径） ─────────

    @Test
    fun rtl_pageCross_firstLineKeepsIndent() {
        val textSize = 48f
        val indent = 2f * textSize
        val mR = 60f
        // offY 依赖 configure 后的 visibleHeight（伴生对象状态共享，必须先配置再取值）
        configure(1000, 40)
        // offsetY 贴近 visibleBottom → 首行必触发换页（覆盖 processMixedLine 换页重置分支 B3）
        val offY = ChapterProvider.paddingVertical + ChapterProvider.visibleHeight - 10f
        val pages = layoutPages(
            longRtl, true, indent, CssTextAlign.CssTextAlignUndefined, 0f, mR, 1000, textSize, offY
        )
        assertTrue("应触发换页(实际 ${pages.size} 页)", pages.size >= 2)
        assertTrue("新页应有行", pages.last().textLines.any { it.textChars.isNotEmpty() })

        val newPageFirstRight = inkRight(pages, 0)
        val expected = (40f + 1000f) - mR - indent
        assertTrue(
            "跨页首行右缘 $newPageFirstRight 应≈ end-mR-indent=$expected",
            kotlin.math.abs(newPageFirstRight - expected) <=
                    TextLayoutProvider.inkPad(textSize) + 2.5f
        )
        println("[PASS] T4 pages=${pages.size} firstRight=$newPageFirstRight expected=$expected")
    }
}
