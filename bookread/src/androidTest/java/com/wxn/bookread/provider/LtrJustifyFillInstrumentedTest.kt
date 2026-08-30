package com.wxn.bookread.provider

import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * justify 拉满（拉丁混合分布 + 阿文纯词距）全链路回归
 * （docs/plans/2026-08-29-plan-justify-max-gap-hybrid-fix.md §8.2，含 R5 拖尾空格口径）。
 *
 * 断言口径（直驱 layoutNormalTextRtl，textAlign=Justify，宽度不变性——不锁具体断行位置）：
 *  1. 纯拉丁段落每个中间行（非首/末行）：
 *     - 被分布的行（WORD/HYBRID/CHAR）：可见墨迹右缘 = effEnd − 行尾空白带宽（±2px，
 *       断行处空格按现状留在盒内行尾——锁死 AC-1-i 缺陷）；
 *     - 被判 SKIP 的行（行尾空格 measureText 补丁使 contentWidth 边际超宽 → B1/F4 禁负压缩
 *       兜底，与旧引擎口径一致）：右缘缺口 ≤1em 即视为到位（实测 ≈0.1 字宽，视觉无感）；
 *  2. 拉丁行词距封顶：空白相邻对/跨组对间距 ≤0.5em+1f（shaping 无关；组内字距精度
 *     由 JVM JustifyApplierTest 坐标断言覆盖）；
 *  3. 阿文段落纯词距对齐（无字距、无上限，竞品一致）：判据同 1 的两级口径（RTL 取左缘）
 *     + 词距非负 + isRtl。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.LtrJustifyFillInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class LtrJustifyFillInstrumentedTest {

    private val latin =
        "The quick brown fox jumps over the lazy dog. In publishing and graphic design, lorem " +
        "ipsum is a placeholder text commonly used to demonstrate the visual form of a document " +
        "or a typeface without relying on meaningful content. Lorem ipsum has been the " +
        "industry's standard dummy text ever since the 1500s, when an unknown printer took a " +
        "galley of type and scrambled it to make a type specimen book."

    private val arabic =
        "العنصر الثاني طويل جدا ويتوقع أن يلتف على أكثر من سطر واحد للتأكد من أن النقطة تظهر " +
        "على السطر الأول فقط وبشكل واضح ومقروء للقارئ في كل الأحوال"

    private fun layoutPages(text: String): Pair<ArrayList<TextPage>, TextPaint> {
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
        val paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 48f
            isAntiAlias = true
        }
        val pages = arrayListOf(TextPage())
        TextLayoutProvider.layoutNormalTextRtl(
            text,
            null,
            RTLSegmenter.segment(text),
            paint,
            marginLeft = 0f,
            marginRight = 0f,
            firstLineIndent = 0f,
            isTitle = false,
            isListRow = false,
            listLevel = 0,
            paragraphIndex = 0,
            textAlign = CssTextAlign.CssTextAlignJustify,
            lineHeightParam = 1f,
            paragraph = ReaderText.Text(text),
            textPages = pages,
            pageLines = arrayListOf(),
            pageLengths = arrayListOf(),
            stringBuilder = StringBuilder(),
            offsetY = 40f,
            bounds = layoutBoundsPage(),
            chapterIsRtl = false,
            hasInlineImage = false
        )
        return pages to paint
    }

    private fun visibleLines(pages: List<TextPage>): List<TextLine> =
        pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }

    /** 行可见墨迹端点（剔除空白；LTR 取右缘 / RTL 取左缘） */
    private fun inkEdge(line: TextLine, rtl: Boolean): Float {
        val ink = line.textChars.filter {
            !it.isImage && it.charData.firstOrNull()?.isWhitespace() != true
        }
        return if (rtl) ink.minOf { it.start } else ink.maxOf { it.end }
    }

    /** 行尾拖尾空白带宽（断行处空格按现状留在行内、占盒缘位置；R5 拉满判定的补偿项） */
    private fun trailingWsWidth(line: TextLine): Float {
        var w = 0f
        for (i in line.textChars.indices.reversed()) {
            val ch = line.textChars[i]
            if (ch.isImage || ch.charData.firstOrNull()?.isWhitespace() != true) break
            w += ch.end - ch.start
        }
        return w
    }

    /**
     * 行级词距守卫：跨组对（空格→词首）与空白相邻对的间距下限 −1f；capped=true 时上限 0.5em+1f。
     * 组内字符间距不在此断言——真实字体连写/连字（阿文成形、拉丁 ligature）使
     * measureText(孤立字符) 与行内推进宽度天然有偏差，组内精度由 JVM 级
     * JustifyApplierTest 坐标断言覆盖，此处只守 shaping 无关的词距口径。
     */
    private fun assertWordGapBounds(line: TextLine, paint: TextPaint, capped: Boolean) {
        val cs = line.textChars
        val maxWordGap = paint.textSize * 0.5f + 1f
        for (i in 1 until cs.size) {
            val prev = cs[i - 1]
            val cur = cs[i]
            if (prev.isImage || cur.isImage) continue
            val crossGroup = prev.renderGroup != cur.renderGroup
            val atSpace = prev.charData.firstOrNull()?.isWhitespace() == true ||
                    cur.charData.firstOrNull()?.isWhitespace() == true
            if (!(crossGroup || atSpace)) continue
            val gap = if (line.isRtl) prev.start - cur.end else cur.start - prev.end
            // capped=false（阿文纯词距行）：无上限语义，仅禁负压缩；capped=true（拉丁）：封顶 0.5em
            assertTrue(
                "词距越界 line=${line.hashCode()} i=$i gap=$gap",
                gap >= -1f && (!capped || gap <= maxWordGap)
            )
        }
    }

    @Test
    fun latin_justify_middleLinesReachRightEdge() {
        val (pages, paint) = layoutPages(latin)
        val lines = visibleLines(pages)
        assertTrue("应折行 ≥5 行（实际 ${lines.size}）", lines.size >= 5)
        val expected = ChapterProvider.visibleRight - TextLayoutProvider.inkPad(paint.textSize)
        val effWidth = ChapterProvider.visibleWidth - 2 * TextLayoutProvider.inkPad(paint.textSize)
        for (idx in 1 until lines.size - 1) {   // 中间行（首/末行退化为左对齐）
            val line = lines[idx]
            val right = inkEdge(line, rtl = false)
            val plan = JustifyChecker.resolveJustifyPlan(line.textChars, effWidth, paint.textSize)
            if (plan.mode == JustifyPlan.Mode.SKIP) {
                // 边际超宽行：行尾空格 patch 宽度 > 布局实际剩余 advance → B1/F4 兜底（旧引擎同口径）。
                // 墨迹已填满 ≥99%（缺口实测 ≈0.1 字宽），右缘缺口 ≤1em 即视为对齐到位。
                val gap = expected - right
                assertTrue(
                    "中间行 $idx 为 SKIP 但右缘缺口 $gap 超 1em",
                    gap <= paint.textSize && gap >= -2f
                )
            } else {
                val tail = trailingWsWidth(line)
                assertTrue(
                    "中间行 $idx 墨迹右缘 $right 应达 effEnd−尾空白 $expected−$tail",
                    abs(right - (expected - tail)) <= 2f
                )
            }
        }
        lines.forEach { assertWordGapBounds(it, paint, capped = true) }
    }

    @Test
    fun arabic_justify_wordGapOnly_fillsLikeCompetitors() {
        val (pages, paint) = layoutPages(arabic)
        val lines = visibleLines(pages)
        assertTrue("应折行 ≥3 行（实际 ${lines.size}）", lines.size >= 3)
        assertTrue("阿文行 isRtl", lines.all { it.isRtl })
        // 中间行拉满（纯词距分布恒等式精确到位；visibleLeft = paddingHorizontal；RTL 取左缘）
        val expected = ChapterProvider.paddingHorizontal + TextLayoutProvider.inkPad(paint.textSize)
        val effWidth = ChapterProvider.visibleWidth - 2 * TextLayoutProvider.inkPad(paint.textSize)
        for (idx in 1 until lines.size - 1) {
            val line = lines[idx]
            val left = inkEdge(line, rtl = true)
            val plan = JustifyChecker.resolveJustifyPlan(line.textChars, effWidth, paint.textSize)
            if (plan.mode == JustifyPlan.Mode.SKIP) {
                // 同拉丁口径：边际超宽行（F4 兜底）左缘缺口 ≤1em
                val gap = left - expected
                assertTrue(
                    "阿文中间行 $idx 为 SKIP 但左缘缺口 $gap 超 1em",
                    gap <= paint.textSize && gap >= -2f
                )
            } else {
                val tail = trailingWsWidth(line)
                assertTrue(
                    "阿文中间行 $idx 墨迹左缘 $left 应达 effStart+尾空白 $expected+$tail",
                    abs(left - (expected + tail)) <= 2f
                )
            }
        }
        // 纯词距无上限（竞品一致），仅断言非负（禁压缩）
        lines.forEach { assertWordGapBounds(it, paint, capped = false) }
    }
}
