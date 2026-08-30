package com.wxn.bookread.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * M1（混合章节纯 LTR 段切列方向 = chapterIsRtl 章级聚合）集成回归
 * （docs/plans/2026-08-28-plan-unify-ltr-to-layoutNormalTextRtl.md §5 MixedColumnDirectionTest）。
 *
 * chapterIsRtl = 章内段落 segDirect.baseRtl 占比 > 0.33（ChapterProvider:723-728）。
 * 双列模式下统一后全章按同一方向流动：
 *  - RTL 占比过阈 → 章节从右列起排（含纯 LTR 段，M1 语义）；
 *  - 全 LTR 章节 → 从左列起排（旧行为）。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.MixedColumnDirectionInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class MixedColumnDirectionInstrumentedTest {

    private val longLtr = buildString {
        repeat(60) { i -> append("Paragraph line word number $i fills the column. ") }
    }

    @Before
    fun setUp() {
        ChapterProvider.apply {
            viewWidth = 2400
            viewHeight = 4000
            paddingHorizontal = 40
            paddingVertical = 40
            visibleWidth = viewWidth - paddingHorizontal * 2      // 2320
            visibleHeight = viewHeight - paddingVertical * 2
            visibleRight = paddingHorizontal + visibleWidth
            visibleBottom = paddingVertical + visibleHeight
            lineSpacingExtra = 1.2f
            // 与 recomputeDerivedSizes 双列公式一致：gap=2320*0.06=139, width=(2320-139)/2=1090
            dualColumnEnabled = true
            columnGapActual = (visibleWidth * 0.06).toInt()
            columnWidth = (visibleWidth - columnGapActual) / 2
        }
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

    private fun midX(): Float = ChapterProvider.paddingHorizontal + ChapterProvider.columnWidth + ChapterProvider.columnGapActual / 2f

    @Test
    fun mixedChapter_ltrParagraphStartsRightColumn() {
        val rtl = ReaderText.Text("نص عربي قصير").apply {
            segDirect = RTLSegmenter.segment(line)   // baseRtl=true → 占比 1/2 > 0.33
        }
        val ltr = ReaderText.Text(longLtr).apply {
            segDirect = RTLSegmenter.segment(line)   // 纯 LTR：runs 为空、baseRtl=false
        }
        val ch = chapter(listOf(rtl, ltr))

        val ltrLines = ch.pages.flatMap { it.textLines }
            .filter { it.paragraphIndex == 1 && it.textChars.isNotEmpty() }
        assertTrue("LTR 段应有行", ltrLines.isNotEmpty())

        val firstStartX = ltrLines.first().textChars.minOf { it.start }
        assertTrue(
            "章节判 RTL 后纯 LTR 段应从右列起排: firstStartX=$firstStartX mid=${midX()}",
            firstStartX > midX()
        )
        // RTL 段同样右列起排（全章一致）
        val rtlLines = ch.pages.flatMap { it.textLines }
            .filter { it.paragraphIndex == 0 && it.textChars.isNotEmpty() }
        val rtlStartX = rtlLines.first().textChars.maxOf { it.end }
        assertTrue("RTL 段应贴右列", rtlStartX > midX())
    }

    @Test
    fun ltrChapter_allParagraphsStartLeftColumn() {
        val p1 = ReaderText.Text(longLtr).apply { segDirect = RTLSegmenter.segment(line) }
        val p2 = ReaderText.Text(longLtr).apply { segDirect = RTLSegmenter.segment(line) }
        val ch = chapter(listOf(p1, p2))

        val firstLine = ch.pages.first().textLines.first { it.textChars.isNotEmpty() }
        val firstStartX = firstLine.textChars.minOf { it.start }
        // 容差 4f：首字符字形 left side bearing 使墨迹略进内容盒（实测 ~2.4px @48f）
        assertTrue(
            "全 LTR 章节应从左列起排: firstStartX=$firstStartX",
            firstStartX <= ChapterProvider.paddingHorizontal + 4f
        )
        assertEquals(0, firstLine.paragraphIndex)
    }

    @Test
    fun subThresholdRtlRatio_chapterStaysLtr() {
        // 1/4 = 0.25 ≤ 0.33 → 章节仍判 LTR
        val rtl = ReaderText.Text("نص عربي").apply { segDirect = RTLSegmenter.segment(line) }
        val ls = (1..3).map {
            ReaderText.Text("Short ltr $it").apply { segDirect = RTLSegmenter.segment(line) }
        }
        val ch = chapter(listOf(rtl) + ls)
        // RTL 段（paragraphIndex=0）即使章节 LTR 也贴列右侧排（行内方向），不能作章级方向断言；
        // 取首个 LTR 段（paragraphIndex=1）断言章节从左列起排
        val ltrLine = ch.pages.first().textLines.first { it.textChars.isNotEmpty() && it.paragraphIndex == 1 }
        assertTrue(
            "占比不过阈 LTR 段应左列起排: ${ltrLine.textChars.minOf { it.start }}",
            ltrLine.textChars.minOf { it.start } <= ChapterProvider.paddingHorizontal + 4f
        )
    }
}
