package com.wxn.bookread.provider

import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.textIndexAt
import com.wxn.bookread.textHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 行内图片统一路径回归（docs/plans/2026-08-28-plan-unify-ltr-to-layoutNormalTextRtl.md §5 LtrInlineImageFlowTest，
 * M2 + M2-③）。
 *
 * 统一坐标约定：图片 TextChar 只占 textChars 数组位、不占文本位；
 * TextLine.charStartOffset 恒为原始段内文本偏移（不再按图片数修正）；
 * 消费端口径 = charStartOffset + textIndexAt(数组下标)。
 *
 * S1：img 在文本后（"Before" + IMG + "After"，图共享文本行——旧实现 setTextWithInnerImg 的
 *     fits 同行路径等价，M2-②）；
 * S2：img 在行首（IMG + "After"，图建新行、文本共享图片行——旧实现 all-image 修正特判覆盖的场景）。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.LtrInlineImageFlowInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class LtrInlineImageFlowInstrumentedTest {

    /** M2-① 断言参照物：layout() 实际传入引擎的局部 TextPaint（全局 contentPaint 本测试不配置，不可作参照） */
    private lateinit var lastLayoutPaint: TextPaint

    private fun configure() {
        ChapterProvider.apply {
            paddingHorizontal = 40
            paddingVertical = 40
            visibleWidth = 1000
            visibleHeight = 20000
            visibleRight = paddingHorizontal + visibleWidth
            visibleBottom = paddingVertical + visibleHeight
            lineSpacingExtra = 1.2f
            dualColumnEnabled = false
            columnGapActual = 0
            columnWidth = 0
        }
    }

    private fun layout(
        text: String,
        imgStart: Int,
        imgWidth: Int = 30,
        imgHeight: Int = 30
    ): ArrayList<TextPage> {
        configure()
        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 48f
            isAntiAlias = true
        }
        lastLayoutPaint = paint
        val paragraph = ReaderText.Text(text).apply {
            annotations = listOf(
                TextTag(
                    uuid = "img-1",
                    name = "img",
                    start = imgStart,
                    params = "src=file://fake.png&width=$imgWidth&height=$imgHeight"
                )
            )
        }
        val seg = RTLSegmenter.segment(text)
        val textPages = arrayListOf(TextPage())
        val sb = StringBuilder()
        TextLayoutProvider.layoutNormalTextRtl(
            text,
            null,
            seg,
            paint,
            marginLeft = 0f,
            marginRight = 0f,
            firstLineIndent = 0f,
            isTitle = false,
            isListRow = false,
            listLevel = 0,
            paragraphIndex = 0,
            textAlign = CssTextAlign.CssTextAlignLeft,
            lineHeightParam = 1f,
            paragraph = paragraph,
            textPages = textPages,
            pageLines = arrayListOf(),
            pageLengths = arrayListOf(),
            stringBuilder = sb,
            offsetY = 40f,
            bounds = layoutBoundsPage(),
            chapterIsRtl = false,
            hasInlineImage = true
        )
        assertEquals("无图文本字符外的 page.text 不含占位符且段末补 \\n", "$text\n", sb.toString())
        return textPages
    }

    /**
     * M2-③ 核心不变量：任意文本字符的段内偏移 = charStartOffset + textIndexAt(数组下标)。
     * LTR 行数组序 = 逻辑序，原始偏移按前方非图片字符的码点长度累加重构。
     */
    private fun assertRawOffsetInvariant(line: com.wxn.bookread.data.model.TextLine, tag: String) {
        // M2-③：段内偏移 = charStartOffset + textIndexAt(数组位)。raw 从行起始段内偏移起算
        //（charStartOffset 为段内绝对偏移，多行段落 ≠ 0），保证多行复用时口径正确
        var raw = line.charStartOffset
        var sawImage = false
        line.textChars.forEachIndexed { arrIdx, ch ->
            if (ch.isImage) {
                sawImage = true
            } else {
                val mapped = line.charStartOffset + line.textIndexAt(arrIdx)
                assertEquals(
                    "$tag 数组位 $arrIdx ('${ch.charData}') 段内偏移漂移",
                    raw, mapped
                )
                raw += ch.charData.length
            }
        }
        assertTrue("$tag 应包含图片字符", sawImage)
    }

    // ── S1：图共享文本行（图在文后） ──

    @Test
    fun imageAfterText_sharedLine_offsetsUnshifted() {
        val text = "BeforeAfter"
        val pages = layout(text, imgStart = 6)

        val lines = pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }
        assertEquals("短文本+小图应同行单行", 1, lines.size)
        val line = lines[0]

        val imgChars = line.textChars.filter { it.isImage }
        assertEquals("恰一个图片字符", 1, imgChars.size)
        assertEquals("图片字符 renderGroup=0", 0, imgChars[0].renderGroup)

        val textChars = line.textChars.filter { !it.isImage }
        assertEquals("11 个文本字符", text.length, textChars.size)
        textChars.forEach { assertTrue("文本字符 renderGroup ≥1", it.renderGroup >= 1) }

        // charStartOffset 恒为原始偏移（不再按图片数修正），行文本口径与图片数无关
        assertEquals(0, line.charStartOffset)
        assertEquals("charEndOffset=段落长", text.length, line.charEndOffset)

        assertRawOffsetInvariant(line, "S1")

        // 标签匹配语义（模拟 ContentTextView：charIndex = charStartOffset + textIdx）
        val tagStart = 6; val tagEnd = 11  // "After"
        val matched = line.textChars.filterIndexed { arrIdx, ch ->
            !ch.isImage && (line.charStartOffset + line.textIndexAt(arrIdx)) in tagStart until tagEnd
        }.joinToString("") { it.charData }
        assertEquals("区间 [6,11) 应恰好命中图后文本", "After", matched)
    }

    // ── S2：图先行（图建新行，文本共享图片行） ──

    @Test
    fun imageBeforeText_imageLineShared_offsetsUnshifted() {
        val text = "After"
        val pages = layout(text, imgStart = 0)

        val lines = pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }
        assertEquals("IMG+文本共享一行", 1, lines.size)
        val line = lines[0]

        assertEquals("图片在数组首位", true, line.textChars.first().isImage)
        assertEquals("图片行 charStartOffset=imgTag.start（原始，无修正）", 0, line.charStartOffset)

        // M2-①：图片行行高公式 = 排版 paint 的 textHeight × lineSpacingExtra × lineHeightParam。
        // 参照物必须与引擎同源（layout() 的局部 paint）：旧断言错用未配置的全局
        // contentPaint，产生 76.38 vs 19.09 假差异（基线例外方案例 1，2026-08-31 取证）
        val imgLineHeight = line.lineBottom - line.lineTop
        val expectedH = lastLayoutPaint.textHeight * ChapterProvider.lineSpacingExtra
        assertTrue(
            "图片行高 $imgLineHeight 应≈ textHeight×spacing $expectedH（M2-①）",
            kotlin.math.abs(imgLineHeight - expectedH) <= 1.5f
        )

        assertRawOffsetInvariant(line, "S2")

        // 图后文本的选区/标签映射：区间 [0,5) 命中全部文本字符（跳过图片位）
        val matched = line.textChars.filterIndexed { arrIdx, ch ->
            !ch.isImage && (line.charStartOffset + line.textIndexAt(arrIdx)) in 0 until 5
        }.joinToString("") { it.charData }
        assertEquals("After", matched)
    }

    // ── 行尾图片：随 fillImageSize 缩放（≤2×行高）后通常与文本同行/贴文本行尾，断言几何与偏移不变量 ──

    @Test
    fun textThenImage_imageGeometryAndOffsetsConsistent() {
        val text = "The quick brown fox jumps over the lazy dog and keeps running far beyond the fence"
        val pages = layout(text, imgStart = text.length, imgWidth = 200, imgHeight = 200)

        val lines = pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }
        assertTrue("应多行", lines.size >= 2)

        // 图片字符存在且 renderGroup=0，x 范围在列内容盒内
        val imgLine = lines.first { it.textChars.any { ch -> ch.isImage } }
        val img = imgLine.textChars.first { it.isImage }
        assertEquals(0, img.renderGroup)
        assertTrue(
            "图片 x 应在列内: ${img.start}..${img.end}",
            img.start >= ChapterProvider.paddingHorizontal.toFloat() - 0.6f &&
                    img.end <= ChapterProvider.visibleRight.toFloat() + 0.6f
        )

        // 文本行偏移不变量不受图片影响
        // M2-③：charStartOffset 是段内绝对偏移，计数基准随行推进（行 1 从行 0 的
        // charEndOffset 起算），不能每行重置为 0
        var raw = 0
        lines.forEachIndexed { i, line ->
            line.textChars.forEachIndexed { arrIdx, ch ->
                if (!ch.isImage) {
                    assertEquals(
                        "行 $i 数组位 $arrIdx 偏移",
                        raw, line.charStartOffset + line.textIndexAt(arrIdx)
                    )
                    raw += ch.charData.length
                }
            }
        }
    }
}
