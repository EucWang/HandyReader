package com.wxn.bookread.provider

import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.InlineCssProps
import com.wxn.base.bean.InlineStyle
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 纯 LTR 段落统一走 layoutNormalTextRtl 后的结构等价回归
 * （docs/plans/2026-08-28-plan-unify-ltr-to-layoutNormalTextRtl.md §5 LtrParityDiffTest）。
 *
 * 旧引擎（setNormalText）已删除、无法同库前后 diff；本类以稳定结构不变量代替 golden 基线：
 *  - T1 纯 LTR 矩阵（拉丁/CJK/混合字号/justify/center/right/长URL/emoji）分页结构健全：
 *    有行、行墨迹不越列宽、字符 x 单调（LTR）、页高正值；
 *  - T2 文本字符 renderGroup ≥ 1（P6：整组 drawText 生效前提；图片字符恒 0）；
 *  - T3 行方向 isRtl 恒 false（M6：笔记图标方向判定的唯一数据源）；
 *  - T4 混合字号：InlineStyle 区间生效（分段处 renderGroup 切分，宽度Span 权威值）；
 *  - T5 emoji 段：TextChar 按码点切分（M3 下标空间统一的前提）。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.LtrUnifyParityInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class LtrUnifyParityInstrumentedTest {

    private val latin = "The quick brown fox jumps over the lazy dog. Pack my box with five dozen liquor jugs."
    private val cjk = "春眠不觉晓处处闻啼鸟夜来风雨声花落知多少"
    private val longUrl =
        "See https://example.com/a/very/long/path?with=query&params=values&more=segments to learn more about it."
    private val emoji = "Rating: 😀 good 🙂 nice 👍"

    private fun configure(width: Int = 1000, padding: Int = 40, spacing: Float = 1.2f) {
        ChapterProvider.apply {
            paddingHorizontal = padding
            paddingVertical = padding
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

    private fun layout(
        text: String,
        align: CssTextAlign = CssTextAlign.CssTextAlignJustify,
        inlineStyles: List<InlineStyle>? = null,
        textSize: Float = 48f
    ): ArrayList<TextPage> {
        configure()
        val paint = TextPaint().apply {
            color = Color.BLACK
            this.textSize = textSize
            isAntiAlias = true
        }
        val seg = RTLSegmenter.segment(text)
        val textPages = arrayListOf(TextPage())
        val sb = StringBuilder()
        // 与 setTypeText 同构：inline 字号经 buildSpannedText 烧入 RelativeSizeSpan
        val charSequence = ChapterProvider.buildSpannedText(text, inlineStyles)
        TextLayoutProvider.layoutNormalTextRtl(
            charSequence,
            inlineStyles,
            seg,
            paint,
            marginLeft = 0f,
            marginRight = 0f,
            firstLineIndent = 0f,
            isTitle = false,
            isListRow = false,
            listLevel = 0,
            paragraphIndex = 0,
            textAlign = align,
            lineHeightParam = 1f,
            paragraph = ReaderText.Text(text),
            textPages = textPages,
            pageLines = arrayListOf(),
            pageLengths = arrayListOf(),
            stringBuilder = sb,
            offsetY = 40f,
            bounds = layoutBoundsPage(),
            chapterIsRtl = false,
            hasInlineImage = false
        )
        return textPages
    }

    private fun visibleLines(pages: List<TextPage>) =
        pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }

    /** 全部字符盒（含跨页）不出列内容盒右界（溢出钳制生效，P4） */
    private fun assertNoOverflow(pages: List<TextPage>, rightLimit: Float, tag: String) {
        visibleLines(pages).forEach { line ->
            val maxEnd = line.textChars.maxOf { it.end }
            assertTrue(
                "$tag 行越界: maxEnd=$maxEnd > $rightLimit",
                maxEnd <= rightLimit + 0.6f
            )
        }
    }

    // ── T1：分页结构健全矩阵 ──

    @Test
    fun ltr_matrix_paginationStructureSane() {
        val cases = mapOf(
            "latin" to latin,
            "cjk" to cjk,
            "url" to longUrl,
            "emoji" to emoji
        )
        val aligns = listOf(
            CssTextAlign.CssTextAlignJustify,
            CssTextAlign.CssTextAlignLeft,
            CssTextAlign.CssTextAlignCenter,
            CssTextAlign.CssTextAlignRight
        )
        val rightLimit = ChapterProvider.visibleRight.toFloat()
        for ((name, text) in cases) {
            for (align in aligns) {
                val pages = layout(text, align)
                val lines = visibleLines(pages)
                assertTrue("$name/${align} 应至少 1 行", lines.isNotEmpty())
                // 页高在引擎层不最终化（由 getTextChapter 收口，M4）；此处只验行存在与不越界
                assertNoOverflow(pages, rightLimit, "$name/$align")
            }
        }
    }

    @Test
    fun ltr_lineChars_xMonotonic() {
        for ((name, text) in mapOf("latin" to latin, "cjk" to cjk)) {
            val pages = layout(text, CssTextAlign.CssTextAlignLeft)
            visibleLines(pages).forEachIndexed { li, line ->
                val starts = line.textChars.map { it.start }
                assertTrue("$name 行 $li 的字符 x 起点应非递减", starts.zipWithNext().all { (a, b) -> a <= b + 0.6f })
            }
        }
    }

    // ── T2：文本字符 renderGroup ≥ 1（P6 整组绘制） ──

    @Test
    fun ltr_textChars_renderGroupPositive() {
        val pages = layout(latin, CssTextAlign.CssTextAlignLeft)
        val textChars = visibleLines(pages).flatMap { it.textChars }.filter { !it.isImage }
        assertTrue("应有文本字符", textChars.isNotEmpty())
        textChars.forEach {
            assertTrue("文本字符 renderGroup 应 ≥1（实际 ${it.renderGroup}）", it.renderGroup >= 1)
        }
    }

    // ── T3：纯 LTR 行 isRtl 恒 false（M6 数据源） ──

    @Test
    fun ltr_lines_isRtlFalse() {
        val pages = layout(latin, CssTextAlign.CssTextAlignJustify)
        val lines = visibleLines(pages)
        assertTrue(lines.isNotEmpty())
        lines.forEachIndexed { i, line ->
            assertTrue("LTR 行 $i isRtl 应为 false", !line.isRtl)
        }
    }

    // ── T4：混合字号段落 ──

    @Test
    fun ltr_mixedFontSize_layoutsAndGroups() {
        // "ABC" 2.0em + "DEF" 常规
        val text = "ABCDEF"
        val styles = listOf(InlineStyle(0, 3, InlineCssProps(fontScale = 2.0f)))
        val pages = layout(text, CssTextAlign.CssTextAlignLeft, inlineStyles = styles)
        val lines = visibleLines(pages)
        assertTrue("混合字号应单行", lines.size == 1)
        val chars = lines[0].textChars
        assertEquals(6, chars.size)
        // span 区间字符实际更宽（scale 生效）
        val wideWidth = chars.take(3).map { it.end - it.start }.average()
        val normalWidth = chars.takeLast(3).map { it.end - it.start }.average()
        assertTrue(
            "放大区间应更宽: wide=$wideWidth normal=$normalWidth",
            wideWidth > normalWidth * 1.5f
        )
        // paint 边界处 renderGroup 切分（A/B/C 与 D 之间不同组）
        assertTrue("字号边界应切分 renderGroup", chars[2].renderGroup != chars[3].renderGroup)
    }

    // ── T5：emoji 按码点切分（M3） ──

    @Test
    fun ltr_emoji_oneTextCharPerCodePoint() {
        val pages = layout(emoji, CssTextAlign.CssTextAlignLeft)
        val textChars = visibleLines(pages).flatMap { it.textChars }.filter { !it.isImage }
        val expected = emoji.codePointCount(0, emoji.length)
        assertEquals(
            "TextChar 数应=码点数（surrogate 对不可拆成 2）",
            expected, textChars.size
        )
        textChars.forEach {
            assertEquals(
                "每个 TextChar 应恰含 1 个码点: '${it.charData}'",
                1, it.charData.codePointCount(0, it.charData.length)
            )
        }
    }
}
