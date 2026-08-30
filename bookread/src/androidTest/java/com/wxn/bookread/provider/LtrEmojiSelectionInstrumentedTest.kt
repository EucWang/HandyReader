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
 * M3（emoji 码点口径）选区下标映射回归
 * （docs/plans/2026-08-28-plan-unify-ltr-to-layoutNormalTextRtl.md §5 LtrEmojiSelectionTest）。
 *
 * 选区链路契约：命中层产生 sC/eC（ContentTextView 传 lineText 的 String 下标），
 * selectText getter 用 lineText.substring(sC, eC+1) 取词。统一后 TextChar 按码点切分、
 * sC/eC 与 charStartOffset 同为文本口径 → 以下不变量必须在含 emoji 行上成立：
 *  - 每个码点一个 TextChar，start/end 互不重叠且递增；
 *  - 按 textIndexAt 口径取 [sC, eC] 的 TextChar 串接 == lineText.substring(sC, eC+1)。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.LtrEmojiSelectionInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class LtrEmojiSelectionInstrumentedTest {

    private val text = "Say 😀 hi to 🌍 now"

    private fun layoutEmojiLine(): com.wxn.bookread.data.model.TextLine {
        ChapterProvider.apply {
            paddingHorizontal = 40
            paddingVertical = 40
            visibleWidth = 1200
            visibleHeight = 20000
            visibleRight = paddingHorizontal + visibleWidth
            visibleBottom = paddingVertical + visibleHeight
            lineSpacingExtra = 1.2f
            dualColumnEnabled = false
            columnGapActual = 0
            columnWidth = 0
        }
        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 48f
            isAntiAlias = true
        }
        val seg = RTLSegmenter.segment(text)
        val textPages = arrayListOf(TextPage())
        TextLayoutProvider.layoutNormalTextRtl(
            text, null, seg, paint,
            marginLeft = 0f, marginRight = 0f, firstLineIndent = 0f,
            isTitle = false, isListRow = false, listLevel = 0,
            paragraphIndex = 0, textAlign = CssTextAlign.CssTextAlignLeft,
            lineHeightParam = 1f,
            paragraph = ReaderText.Text(text),
            textPages = textPages, pageLines = arrayListOf(), pageLengths = arrayListOf(),
            stringBuilder = StringBuilder(), offsetY = 40f,
            bounds = layoutBoundsPage(), chapterIsRtl = false, hasInlineImage = false
        )
        val lines = textPages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }
        assertEquals("样例应单行", 1, lines.size)
        return lines[0]
    }

    @Test
    fun emojiLine_codePointTextChars_distinctX() {
        val line = layoutEmojiLine()
        val expected = text.codePointCount(0, text.length)
        assertEquals("码点数 == TextChar 数", expected, line.textChars.size)
        for (i in 0 until line.textChars.size) {
            val ch = line.textChars[i]
            assertEquals("码点切分: '${ch.charData}'", 1, ch.charData.codePointCount(0, ch.charData.length))
            assertTrue("宽度为正", ch.end > ch.start)
            if (i > 0) {
                assertTrue(
                    "字符 x 递增且不重叠: ${line.textChars[i - 1].end} vs ${ch.start}",
                    ch.start >= line.textChars[i - 1].end - 0.6f
                )
            }
        }
    }

    @Test
    fun selectionSubstringMapping_textIndexSpace() {
        val line = layoutEmojiLine()
        val lineText = line.text
        assertEquals("行文本与原文一致", text, lineText)

        // textIdx 口径 = 码点序号；lineText.substring = UTF-16 码元。
        // 两者在首个增补码点（emoji）之前恒一致（M2-③ 图片位跳过亦在此口径内验证）：
        for (sC in 0..firstEmojiTextIdx() step 2) {
            val expected = substringByCodePoints(lineText, sC, sC + 2)
            val rebuilt = buildString {
                line.textChars.forEachIndexed { arrIdx, ch ->
                    if (!ch.isImage) {
                        val ti = line.textIndexAt(arrIdx)
                        if (ti in sC until sC + 2) append(ch.charData)
                    }
                }
            }
            assertEquals("emoji 前缀两口径应一致: sC=$sC", expected, rebuilt)
        }

        // ★ M3 已知差异（钉住现状，方案 §2.2 M3：新引擎按码点切分后 textIdx 与 String 码元
        //   下标在 emoji 之后错位；选区文本提取需码点感知截取，属后续独立修复，真机 AC-5 关注）：
        //   textIdx=6 是 emoji 之后的 'h'，而码元 substring(0,7) 只到 emoji 后的空格。
        val rebuiltCp6 = buildString {
            line.textChars.forEachIndexed { arrIdx, ch ->
                if (!ch.isImage && line.textIndexAt(arrIdx) <= 6) append(ch.charData)
            }
        }
        assertEquals("码点口径前 7 个字符", "Say 😀 h", rebuiltCp6)
        assertTrue("码元口径同区间比码点口径短 1", "Say 😀 h".length - 1 == lineText.substring(0, 7).length)
        assertEquals("码元 substring(0,7) = 'Say 😀 '（半边界空格）", "Say 😀 ", lineText.substring(0, 7))

        // emoji 字符定位：码点下标 ↔ 数组下标往返
        val emojiArrIdx = line.arrayIndexAt(firstEmojiTextIdx())
        assertEquals("😀", line.textChars[emojiArrIdx].charData)
    }

    /** 首个 emoji（😀）的码点下标 */
    private fun firstEmojiTextIdx(): Int {
        val unitIdx = text.indexOf("😀")
        return text.codePointCount(0, unitIdx)
    }

    /** 按码点下标截取 String（码点感知） */
    private fun substringByCodePoints(s: String, startCp: Int, endCpExclusive: Int): String {
        val startUnit = s.offsetByCodePoints(0, startCp)
        val endUnit = s.offsetByCodePoints(startUnit, endCpExclusive - startCp)
        return s.substring(startUnit, endUnit)
    }
}
