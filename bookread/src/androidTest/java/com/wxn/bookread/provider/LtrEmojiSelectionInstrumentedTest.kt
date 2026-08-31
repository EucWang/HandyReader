package com.wxn.bookread.provider

import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.arrayIndexAt
import com.wxn.bookread.data.model.endExclusiveUtf16
import com.wxn.bookread.data.model.textIndexAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M3（emoji 选区 UTF-16 口径统一）回归
 * （docs/plans/2026-08-31-plan-m3-emoji-selection-utf16-space.md §5.2，按 UTF-16 口径重写——R3/R13）。
 *
 * 口径注记（R3，镜像 LtrInlineImageFlowInstrumentedTest 的前提注记）：
 * 本测试消费 **UTF-16 码元口径**——line.text 下标、charStartOffset、tag/搜索偏移同空间；
 * fixture 含 emoji 为**有意**选择（首个代理对之后码点 ≠ 码元，正是 M3 修复面）。
 * TextChar 仍按码点切分（😀 = 1 个 TextChar，charData = 完整代理对 = 2 码元）。
 *
 * 断言基础（生产公式镜像，非 View 实例化直调）：
 *  - 选区写入（ContinuousPageProvider.updateSelectionState / ContentTextView.selectWordAtChar）：
 *    段内偏移 = charStartOffset + textIndexAt(数组下标)；
 *  - 选区还原（ContentTextView.resolveVisualPos）：ci = 段内偏移 - charStartOffset，
 *    守卫 ci < line.text.length；
 *  - 复制截取（ContentTextView.selectText 同页同行分支）：
 *    line.text.substring(sC, endExclusiveUtf16(eC))；
 *  - 标签点击换算（PageView :633/:639）：arrayIndexAt(tag偏移 - charStartOffset)。
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

    /**
     * 全窗扫描：任意 (起字符, 止字符) 选区，生产截取公式 == 独立码元口径期望。
     * 期望值独立于被测公式（对 fixture 原文按码点走位累计码元边界，不经过 textIndexAt）。
     */
    @Test
    fun selectionSubstringMapping_utf16Space() {
        val line = layoutEmojiLine()
        val lineText = line.text
        assertEquals("行文本与原文一致", text, lineText)

        // 独立 oracle：逐码点走位得到每个字符的起始码元位与长度（不依赖被测函数）
        val independentStarts = run {
            val list = mutableListOf<Int>()
            var i = 0
            while (i < text.length) {
                list.add(i)
                i += Character.charCount(text.codePointAt(i))
            }
            list
        }
        // 锚点断言（可读性）：首个 emoji 😀 起始码元位 = 4，其后字符 '␣' = 6（+2，非 +1）
        assertEquals(4, independentStarts[4])
        assertEquals(6, independentStarts[5])
        assertEquals("😀", line.textChars[line.arrayIndexAt(4)].charData)

        val charCount = line.textChars.count { !it.isImage }
        assertEquals("无图 fixture：数组位 == 字符序", independentStarts.size, charCount)

        // 全窗：si/ei 为数组位（拖选命中口径）；sC/eC 为写入公式产物；截取公式还原
        for (si in 0 until charCount) {
            for (ei in si until charCount) {
                val sC = line.textIndexAt(si)
                val eC = line.textIndexAt(ei)
                val expected = text.substring(
                    independentStarts[si],
                    independentStarts[ei] + Character.charCount(text.codePointAt(independentStarts[ei]))
                )
                val actual = lineText.substring(
                    sC.coerceIn(0, lineText.length),
                    line.endExclusiveUtf16(eC).coerceAtMost(lineText.length)
                )
                assertEquals("选区 (si=$si, ei=$ei) sC=$sC eC=$eC", expected, actual)
            }
        }
    }

    /**
     * 端到端公式链（§1.1 真机复现场景的公式级镜像）：
     * 命中换算 → locator 写入 → resolveVisualPos 还原 → selectText 截取。
     * 覆盖「emoji 之后文本选区」（修复前该类选区复制整体漂移/劈对）。
     */
    @Test
    fun selectionAfterEmoji_endToEnd_formulaChain() {
        val line = layoutEmojiLine()
        val unitOf: (String) -> Int = { line.text.indexOf(it) }

        fun copy(startMark: String, endMark: String): String {
            // 1) 命中：PageView :633 换算（UTF-16 段内偏移 → 数组位）
            val startArr = line.arrayIndexAt(unitOf(startMark) - line.charStartOffset)
            val endArr = line.arrayIndexAt(unitOf(endMark) - line.charStartOffset)
            // 2) locator 写入公式（ContinuousPageProvider.updateSelectionState）
            val startOffset = line.charStartOffset + line.textIndexAt(startArr)
            val endOffset = line.charStartOffset + line.textIndexAt(endArr)
            // 3) resolveVisualPos 公式 + 守卫（修复后守卫为 line.text.length）
            val sC = startOffset - line.charStartOffset
            val eC = endOffset - line.charStartOffset
            assertTrue("sC 守卫（修复前 cp 口径守卫会误拒 emoji 后位置）", sC in 0 until line.text.length)
            assertTrue("eC 守卫", eC in 0 until line.text.length)
            // 4) selectText 同页同行分支截取公式
            return line.text.substring(sC, line.endExclusiveUtf16(eC))
        }

        // §1.1 复现表场景镜像：emoji 之后/含 emoji 的选区
        assertEquals("hi", copy("h", "i"))
        assertEquals("😀 hi", copy("😀", "i"))
        assertEquals("🌍 now", copy("🌍", "w"))
        assertEquals("hi to 🌍 now", copy("h", "w"))
    }

    /** 标签点击命中（镜像 PageView :633/:639 换算）：UTF-16 tag 区间 → 数组位。 */
    @Test
    fun emojiLine_tagClickHit_mirrorPageViewConversion() {
        val line = layoutEmojiLine()
        // parser tag 区间（段内 UTF-16）：[4, 9) = "😀 hi"
        val tagStart = 4
        val tagEnd = 9
        val startHit = line.arrayIndexAt(tagStart - line.charStartOffset)
        val endHit = line.arrayIndexAt(tagEnd - line.charStartOffset)
        assertEquals("tag 起点命中 emoji 本体", "😀", line.textChars[startHit].charData)
        assertEquals("tag 终点（exclusive 偏移）指向其后字符", " ", line.textChars[endHit].charData)
    }
}
