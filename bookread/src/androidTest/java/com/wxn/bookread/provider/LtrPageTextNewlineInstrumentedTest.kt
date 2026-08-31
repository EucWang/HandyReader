package com.wxn.bookread.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * N2（段末 "\n"）+ 上游空段早退 + M5（segDirect null 兜底）集成回归
 * （docs/plans/2026-08-28-plan-unify-ltr-to-layoutNormalTextRtl.md §5 LtrPageTextNewlineTest / LtrBlankParagraphTest / LtrSegNullFallbackTest）。
 *
 * 断言口径（全部通过 getTextChapter 全管线，段落 segDirect 模拟 BookHelper 写入）：
 *  - N2：每个有内容段落的 page.text 以 "\n" 收尾（TalkBack 分段、pageLengths 基数）；
 *  - 空段（"" 与纯空白）：上游 setTypeText:1095 早退，不写 stringBuilder（不产生 page.text 增量、
 *    不重复补 "\n"），但仍占位行高（后续段落正常排版即证明）；
 *  - M5：segDirect=null 段落按 LTR 兜底排版，不崩溃；
 *  - pageLengths 与 page.text 逐页一致（进度基数正确）。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.LtrPageTextNewlineInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class LtrPageTextNewlineInstrumentedTest {

    @Before
    fun setUp() {
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
        // getTextChapter 内部使用单例画笔，测试前置 textSize（upStyle 需要 DataStore，测试直接赋值）
        ChapterProvider.contentPaint.textSize = 48f
        ChapterProvider.contentPaint.isAntiAlias = true
        ChapterProvider.titlePaint.textSize = 80f
    }

    private fun chapter(contents: List<ReaderText>) = runBlocking {
        ChapterProvider.getTextChapter(
            chapter = BookChapter(bookId = 1L, chapterIndex = 0, chapterName = "t"),
            contents = contents,
            chapterSize = 1
        )!!
    }

    @Test
    fun pageText_paragraphEndsWithNewline() {
        val p1 = ReaderText.Text("First paragraph").apply {
            segDirect = RTLSegmenter.segment(line)
        }
        val p2 = ReaderText.Text("Second paragraph").apply {
            segDirect = RTLSegmenter.segment(line)
        }
        val ch = chapter(listOf(p1, p2))

        assertEquals(1, ch.pages.size)
        val text = ch.pages[0].text
        assertEquals("两个段落各以一个 \\n 收尾", "First paragraph\nSecond paragraph\n", text)
        assertEquals("pageLengths 应与 page.text 等长", text.length, ch.pageLengths[0])
        assertEquals("pageLengths 页数", 1, ch.pageLengths.size)
    }

    @Test
    fun blankParagraph_occupiesSpace_writesNoText() {
        val p1 = ReaderText.Text("Before blank").apply {
            segDirect = RTLSegmenter.segment(line)
        }
        val blank = ReaderText.Text("")   // 空段：上游早退
        val blankWs = ReaderText.Text("   ") // 纯空白段：上游早退
        val p4 = ReaderText.Text("After blank").apply {
            segDirect = RTLSegmenter.segment(line)
        }
        val ch = chapter(listOf(p1, blank, blankWs, p4))

        val text = ch.pages[0].text
        assertEquals(
            "空段不产生 page.text 增量，也不重复补 \\n",
            "Before blank\nAfter blank\n", text
        )
        // 空段占位：其消耗行高把 p4 的行顶推低（对比无空段版式）
        val chNoBlank = chapter(
            listOf(
                ReaderText.Text("Before blank").apply { segDirect = RTLSegmenter.segment(line) },
                ReaderText.Text("After blank").apply { segDirect = RTLSegmenter.segment(line) }
            )
        )
        val withBlankTop = ch.pages[0].textLines.last { it.text.isNotEmpty() }.lineTop
        val noBlankTop = chNoBlank.pages[0].textLines.last { it.text.isNotEmpty() }.lineTop
        assertTrue(
            "空段应占行高（withBlank=$withBlankTop > noBlank=$noBlankTop）",
            withBlankTop > noBlankTop
        )
    }

    @Test
    fun nullSegDirect_fallsBackToLtr() {
        // 不写 segDirect（生产中 BookHelper 恒写入；此为防御路径 M5）
        val p = ReaderText.Text("Plain fallback paragraph without segDirect")
        val ch = chapter(listOf(p))
        val lines = ch.pages[0].textLines.filter { it.textChars.isNotEmpty() }
        assertTrue("兜底段落应正常成行", lines.isNotEmpty())
        assertTrue("兜底按 LTR", lines.all { !it.isRtl })
        assertEquals("兜底段落 page.text 仍补 \\n", "Plain fallback paragraph without segDirect\n", ch.pages[0].text)
    }

    @Test
    fun rtlParagraph_alsoNewlineTerminated() {
        val rtl = ReaderText.Text("نص عربي للتجربة").apply {
            segDirect = RTLSegmenter.segment(line)
        }
        val ch = chapter(listOf(rtl))
        assertEquals("RTL 段落同样以 \\n 收尾（N2 全路径生效）", "نص عربي للتجربة\n", ch.pages[0].text)
        assertTrue("RTL 行 isRtl=true", ch.pages[0].textLines.filter { it.textChars.isNotEmpty() }.all { it.isRtl })
        // 对照：内容不含别的分隔符
        assertFalse(ch.pages[0].text.contains("\n\n"))
    }

    // ── N-Q1 段落级防御钉（plan nq1-nq2 Phase 1 改动点 1-3 契约，审查 R20）：
    //    纯 LTR 段不携带置零标志（用户字距正常应用）；segDirect=null 防御路径谓词回退
    //    chapterIsRtl（本章 LTR）→ 同样 false，与画笔不置零同口径。
    @Test
    fun letterSpacingZeroedFlag_pureLtrAndNullSeg_pinnedFalse() {
        val p1 = ReaderText.Text("First paragraph with enough words to wrap").apply {
            segDirect = RTLSegmenter.segment(line)
        }
        val lines1 = chapter(listOf(p1)).pages.flatMap { it.textLines }
            .filter { it.textChars.isNotEmpty() }
        assertTrue("纯 LTR 段应成行", lines1.isNotEmpty())
        assertTrue(
            "纯 LTR 段不携带置零标志（用户字距正常应用）",
            lines1.all { !it.letterSpacingZeroed }
        )

        // 防御路径 M5 同钉：null segDirect → 谓词回退 chapterIsRtl（本章聚合 LTR）→ false
        val p2 = ReaderText.Text("Plain fallback paragraph without segDirect")
        val lines2 = chapter(listOf(p2)).pages.flatMap { it.textLines }
            .filter { it.textChars.isNotEmpty() }
        assertTrue("null 兜底段不携带置零标志（与画笔不置零同口径）", lines2.all { !it.letterSpacingZeroed })
    }
}
