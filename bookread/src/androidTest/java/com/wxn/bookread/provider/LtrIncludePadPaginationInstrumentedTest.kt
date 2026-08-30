package com.wxn.bookread.provider

import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * N3（setIncludePad true→false）分页矩阵回归
 * （docs/plans/2026-08-28-plan-unify-ltr-to-layoutNormalTextRtl.md §5 LtrIncludePadPaginationTest）。
 *
 * 旧引擎 setIncludePad(true)、新引擎 false → 行盒不再含字体 padding，行高/翻页点有既定漂移。
 * 本类断言统一后的不变量（多字号 × 多行距 × CJK/emoji/拉丁）：
 *  - 矩阵全部成功分页，行高为正、行顶单调；
 *  - 无字符丢失：全部 TextChar 码点数 == 原文码点数（防行盒收缩丢字/裁剪异常）；
 *  - 行盒 ≥ 字体度量带（descent-ascent），首行不被压扁；
 *  - 行距系数生效（1.5 行距的版面总高 > 1.0）。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.LtrIncludePadPaginationInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class LtrIncludePadPaginationInstrumentedTest {

    private val mixed = "Spring 春天 has arrived 到了, birds 🐦 sing everywhere 处处闻啼鸟. " +
            "Emoji 😀 and CJK 汉字 mix with Latin text flowing across many wrapped lines."

    private fun configure(width: Int, spacing: Float) {
        ChapterProvider.apply {
            paddingHorizontal = 40
            paddingVertical = 40
            visibleWidth = width
            visibleHeight = 20000
            visibleRight = paddingHorizontal + visibleWidth
            visibleBottom = paddingVertical + visibleHeight
            lineSpacingExtra = spacing
            dualColumnEnabled = false
            columnGapActual = 0
            columnWidth = 0
        }
    }

    private fun layout(textSize: Float, spacing: Float, width: Int = 1000): Pair<ArrayList<TextPage>, TextPaint> {
        configure(width, spacing)
        val paint = TextPaint().apply {
            color = Color.BLACK
            this.textSize = textSize
            isAntiAlias = true
        }
        val seg = RTLSegmenter.segment(mixed)
        val textPages = arrayListOf(TextPage())
        TextLayoutProvider.layoutNormalTextRtl(
            mixed, null, seg, paint,
            marginLeft = 0f, marginRight = 0f, firstLineIndent = 0f,
            isTitle = false, isListRow = false, listLevel = 0,
            paragraphIndex = 0, textAlign = CssTextAlign.CssTextAlignJustify,
            lineHeightParam = 1f,
            paragraph = ReaderText.Text(mixed),
            textPages = textPages, pageLines = arrayListOf(), pageLengths = arrayListOf(),
            stringBuilder = StringBuilder(), offsetY = 40f,
            bounds = layoutBoundsPage(), chapterIsRtl = false, hasInlineImage = false
        )
        return textPages to paint
    }

    @Test
    fun matrix_paginationSane_noCharLoss() {
        val expectedCodePoints = mixed.codePointCount(0, mixed.length)
        for (textSize in floatArrayOf(32f, 48f, 64f)) {
            for (spacing in floatArrayOf(1.0f, 1.5f)) {
                val (pages, paint) = layout(textSize, spacing)
                val lines = pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }
                assertTrue("ts=$textSize sp=$spacing 应有行", lines.isNotEmpty())

                // 行高为正、行顶单调
                lines.forEachIndexed { i, line ->
                    assertTrue("ts=$textSize sp=$spacing 行 $i 行高为正", line.lineBottom > line.lineTop)
                    if (i > 0) {
                        assertTrue(
                            "ts=$textSize sp=$spacing 行 $i 行顶应不低于前行行顶",
                            line.lineTop >= lines[i - 1].lineTop - 0.6f
                        )
                    }
                }

                // 行盒 ≥ 字体度量带（N3：includePad=false 最小收缩到 descent-ascent）
                val fm = paint.fontMetrics
                val band = fm.descent - fm.ascent
                lines.forEachIndexed { i, line ->
                    assertTrue(
                        "ts=$textSize sp=$spacing 行 $i 行高 ${line.lineBottom - line.lineTop} 应≥度量带 $band",
                        line.lineBottom - line.lineTop >= band * 0.95f
                    )
                }

                // 无丢字
                val charCount = lines.flatMap { it.textChars }.count { !it.isImage }
                assertEquals("ts=$textSize sp=$spacing 全部码点都应有 TextChar", expectedCodePoints, charCount)
            }
        }
    }

    @Test
    fun spacingFactor_increasesLayoutHeight() {
        val result1 = layout(48f, 1.0f)
        val result15 = layout(48f, 1.5f)
        val h1 = result1.first.flatMap { it.textLines }.lastOrNull()?.lineBottom ?: 0f
        val h15 = result15.first.flatMap { it.textLines }.lastOrNull()?.lineBottom ?: 0f
        assertTrue(
            "1.5 行距版面高 $h15 应显著大于 1.0 行距 $h1",
            h15 > h1 * 1.2f
        )
    }

    @Test
    fun narrowWidth_pagesSplit() {
        val (pagesWide, _) = layout(48f, 1.2f, width = 1600)
        val (pagesNarrow, _) = layout(48f, 1.2f, width = 500)
        assertTrue("窄列页数 ${pagesNarrow.size} 应≥宽列 ${pagesWide.size}", pagesNarrow.size >= pagesWide.size)
        assertEquals("原文码点不因分页丢失", mixed.codePointCount(0, mixed.length),
            pagesNarrow.flatMap { it.textLines }.flatMap { it.textChars }.count { !it.isImage })
    }
}

