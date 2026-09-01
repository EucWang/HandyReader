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
 *  2. 拉丁行词距：上限内路径（WORD≤0.5em / HYBRID）封顶口径由 JVM JustifyGapResolverTest
 *     数值断言承载；真·短行经无上限纯词距回退（docs/plans/2026-09-01-plan-justify-true-shortline-wordfill.md）
 *     合法持有 >0.5em 词距——仪器层仅守禁负压缩（capped=false），组内字距精度
 *     由 JVM JustifyApplierTest 坐标断言覆盖；
 *  3. 阿文段落纯词距对齐（无字距、无上限，竞品一致）：判据同 1 的两级口径（RTL 取左缘）
 *     + 词距非负 + isRtl；
 *  4. P3（docs/plans/2026-08-31-plan-p3-justify-firstline-css.md）：justify 首行 CSS 语义——
 *     首行在缩进后内容盒内两端对齐（分布签名口径，见各用例注释）、真·短行首行无上限词距拉满
 *     （原 SKIP 口径经 docs/plans/2026-09-01-plan-justify-true-shortline-wordfill.md 反转）、
 *     单行段落（首行即末行）仍退化不拉伸。
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

    private fun layoutPages(
        text: String,
        firstLineIndent: Float = 0f
    ): Pair<ArrayList<TextPage>, TextPaint> {
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
            firstLineIndent = firstLineIndent,
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
     * capped=true 仅适用于上限内路径（WORD/HYBRID）；真·短行无上限纯词距回退行
     * （2026-09-01 方案）合法持有 >0.5em 词距，必须走 capped=false（仅禁负压缩）。
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
            // capped=true：上限内路径封顶 0.5em；capped=false（阿文纯词距行 / 拉丁真·短行回退行）：无上限语义，仅禁负压缩
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
        // capped=false：真·短行回退行（2026-09-01 方案）合法持有 >0.5em 词距；封顶口径由 JVM 数值断言承载
        lines.forEach { assertWordGapBounds(it, paint, capped = false) }
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

    // ── P3 主例：首行在缩进后内容盒内两端对齐（S1a+S1b 双钉） ──
    // 口径（docs/reviews/2026-09-01-review-p3-justify-firstline-round2.md I-3 / C4 实证 F-1/F-2）：
    // 「已分布」的可靠证据 = distributeWords 分布签名（组间 gap 全等 + 盒缘精确贴合）；
    // post-hoc 重算 resolveJustifyPlan 对已分布行恒得 SKIP（循环论证），不作分支判据。
    // fixture：英文 + 中文尾——首行折行落在 CJK 内部（非空格收尾 ⇒ contentWidth ≤ effWidth，
    // WORD_DISTRIBUTE 稳定触发，无「行尾空格边际超宽 → SKIP」边际）。
    @Test
    fun p3_firstLine_justify_fillsIndentedBox_distributionSignature() {
        val indent = 2f * 48f
        val text = "The quick brown fox 春眠不觉晓处处闻啼鸟夜来风雨声花落知多少" +
                "春晓春眠不觉晓处处闻啼鸟夜来风雨声花落知多少"
        val (pages, paint) = layoutPages(text, firstLineIndent = indent)
        val lines = visibleLines(pages)
        assertTrue("应折行 ≥3 行（实际 ${lines.size}）", lines.size >= 3)

        val ink = TextLayoutProvider.inkPad(paint.textSize)
        val effStart = ChapterProvider.paddingHorizontal + indent + ink   // 缩进后盒缘
        val effEnd = ChapterProvider.visibleRight - ink

        // 钉 S1a+分布：首组盒左缘 = 缩进位（缺 S1a 时 justify 拉到无缩进位，必红）
        val first = lines.first()
        val boxes = first.textChars.filter { !it.isImage }
            .groupBy { it.renderGroup }
            .values.map { g -> g.minOf { it.start } to g.maxOf { it.end } }
            .sortedBy { it.first }
        assertTrue("首行应 ≥2 组（实际 ${boxes.size}）", boxes.size >= 2)
        assertTrue(
            "首组左缘应在缩进位 effStart=$effStart，实际 ${boxes.first().first}",
            abs(boxes.first().first - effStart) <= 0.01f
        )

        // 钉 S1b+分布：组间 gap 全等 + 末组右缘贴合缩进后 effEnd（首行不进 justify 则右缘自然短缺，必红）
        val gaps = (0 until boxes.size - 1).map { boxes[it + 1].first - boxes[it].second }
        assertTrue(
            "首行应带 distributeWords 分布签名：gaps=$gaps " +
                    "boxes=[${boxes.first()}, ${boxes.last()}] effEnd=$effEnd",
            gaps.maxOf { it } - gaps.minOf { it } <= 0.01f &&
                    abs(boxes.last().second - effEnd) <= 0.01f
        )

        // 末行仍起始边对齐（退化不变量）：墨迹左缘 = 全宽 effStart（无缩进）
        val lastLeft = lines.last().textChars.filter {
            !it.isImage && it.charData.firstOrNull()?.isWhitespace() != true
        }.minOf { it.start }
        val lastEffStart = ChapterProvider.paddingHorizontal + ink
        assertTrue(
            "末行应左对齐至全宽 effStart=$lastEffStart，实际 $lastLeft",
            abs(lastLeft - lastEffStart) <= 2f
        )
    }

    // ── P3 真短行首行：无上限纯词距拉满（docs/plans/2026-09-01-plan-justify-true-shortline-wordfill.md §4.2-2，
    // 原「真·短行首行 SKIP 起始锚定」口径反转）──
    // fixture：2 短词 + 超长不可断词——断行按缩进后宽度打包，首行恰含 2 短词，不满足即 fixture 失准须调整。
    // 松弛度推演（审查 R1）：首行 slack ≈ 长词宽 ≈4-5em，within=3 → 0.1em×within=0.3em，
    // 裕度 >10 倍，必然命中无上限词距回退分支而非 HYBRID。
    // 断言口径 = 分布签名（C4 pitfall：post-hoc resolveJustifyPlan 对已分布行无证据力）：
    // 组间 gap 全等 + 末组右缘贴缩进后 effEnd + 首组左缘在缩进位（S1a 不回退）+ gap > 0.5em（非 HYBRID）。
    @Test
    fun p3_firstLine_trueShortRow_fillsBox_uncappedWordGap() {
        val indent = 4f * 48f
        val longWord = "extraordinarilyunbreakablecompoundword"
        val text = "ab cd $longWord and more common words here to fill the remaining lines"
        val (pages, paint) = layoutPages(text, firstLineIndent = indent)
        val lines = visibleLines(pages)
        assertTrue("应折行 ≥2 行（实际 ${lines.size}）", lines.size >= 2)

        val first = lines.first()
        // fixture 意图钉：首行恰为 2 短词（不满足说明长词估宽失准，须调 fixture）
        val firstText = first.text.trim()
        assertTrue("首行应恰为 2 短词，实际 \"$firstText\"", firstText == "ab cd")

        val ink = TextLayoutProvider.inkPad(paint.textSize)
        val effStart = ChapterProvider.paddingHorizontal + indent + ink
        val effEnd = ChapterProvider.visibleRight - ink

        val boxes = first.textChars.filter { !it.isImage }
            .groupBy { it.renderGroup }
            .values.map { g -> g.minOf { it.start } to g.maxOf { it.end } }
            .sortedBy { it.first }
        assertTrue("首行应 ≥2 组（实际 ${boxes.size}）", boxes.size >= 2)

        // S1a 不回退：首组左缘仍在缩进位
        assertTrue(
            "首组左缘应在缩进位 effStart=$effStart，实际 ${boxes.first().first}",
            abs(boxes.first().first - effStart) <= 2f
        )

        // 分布签名：组间 gap 全等 + 末组右缘贴 effEnd（修复前真·短行 SKIP 右缘缺口 >2em，必红）
        val gaps = (0 until boxes.size - 1).map { boxes[it + 1].first - boxes[it].second }
        assertTrue(
            "首行应带 distributeWords 分布签名：gaps=$gaps " +
                    "boxes=[${boxes.first()}, ${boxes.last()}] effEnd=$effEnd",
            gaps.maxOf { it } - gaps.minOf { it } <= 0.01f &&
                    abs(boxes.last().second - effEnd) <= 0.01f
        )

        // 无上限回退（非 HYBRID）：词距 > 0.5em（原 SKIP 语义下行不拉伸，不可能达到）
        val maxWordGap = paint.textSize * 0.5f
        assertTrue(
            "真·短行回退应为无上限词距：gap=${gaps.first()} 应 > 0.5em=$maxWordGap",
            gaps.first() > maxWordGap + 1f
        )
    }

    // ── U1 松散中间行回归钉（docs/plans/2026-09-01-plan-justify-true-shortline-wordfill.md §4.2-3）──
    // U1 @137% 场景：贪心断行遇超长词留下大 slack 的中间行（"type involves selecting" 形态）
    // 曾被判「真·短行」SKIP 右侧留白；修复后须无上限纯词距拉满。
    // 松弛度推演（审查 R1）：目标行 2 短词、后随超长不可断词放不下 → slack ≈ 长词宽 ≈4-5em，
    // within=3 → 0.3em，裕度 >10 倍，必然命中回退分支而非 HYBRID。
    @Test
    fun latin_justify_looseMiddleLine_uncappedWordFill() {
        val longWord = "extraordinarilyunbreakablecompoundword"
        val text = "aa bb cc dd ee ff gg hh ii jj kk ll mm nn oo pp qq rr ss tt uu vv " +
                "ab cd $longWord ww xx yy zz"
        val (pages, paint) = layoutPages(text)
        val lines = visibleLines(pages)
        assertTrue("应折行 ≥3 行（实际 ${lines.size}）", lines.size >= 3)

        val expected = ChapterProvider.visibleRight - TextLayoutProvider.inkPad(paint.textSize)

        // 结构化定位：超长词必然独占新行（其宽 > 任何非空行余量），其前一中间行即目标松散行
        // （行尾为 "ab cd"，长词放不下被挤到下一行——正是 U1 "type involves selecting" 形态）
        val longWordIdx = lines.indexOfFirst { it.text.trim() == longWord }
        assertTrue(
            "未找到长词独占行（fixture 断行变化，须调整短词数量；禁止静默改 fixture）；" +
                    "实际断行=${lines.mapIndexed { i, l -> "L$i=[${l.text.trim()}]" }}",
            longWordIdx > 1
        )
        val targetIdx = longWordIdx - 1
        assertTrue("目标行应为中间行（实际 idx=$targetIdx）", targetIdx in 1 until lines.size - 1)
        val line = lines[targetIdx]
        assertTrue(
            "fixture 自校验：目标行应以 \"ab cd\" 收尾，实际=[${line.text.trim()}]",
            line.text.trimEnd().endsWith("ab cd")
        )

        val boxes = line.textChars.filter { !it.isImage }
            .groupBy { it.renderGroup }
            .values.map { g -> g.minOf { it.start } to g.maxOf { it.end } }
            .sortedBy { it.first }
        assertTrue("目标行应 ≥2 组（实际 ${boxes.size}）", boxes.size >= 2)

        val tail = trailingWsWidth(line)
        // 分布签名：组间 gap 全等 + 可见墨迹右缘贴 effEnd−尾空白（尾随空格挂在末词组盒内、
        // 占据 [effEnd−tail, effEnd] 带——与 latin_justify_middleLinesReachRightEdge 同口径。
        // 修复前 SKIP：右缘缺口 ≈ 长词宽，必红）
        val gaps = (0 until boxes.size - 1).map { boxes[it + 1].first - boxes[it].second }
        val inkRight = line.textChars
            .filter { !it.isImage && it.charData.firstOrNull()?.isWhitespace() != true }
            .maxOf { it.end }
        assertTrue(
            "松散中间行应带分布签名：gaps=$gaps inkRight=$inkRight " +
                    "effEnd−tail=${expected - tail} tail=$tail",
            gaps.maxOf { it } - gaps.minOf { it } <= 0.01f &&
                    abs(inkRight - (expected - tail)) <= 0.01f
        )
        val maxWordGap = paint.textSize * 0.5f
        assertTrue(
            "松散中间行应为无上限词距：gap=${gaps.first()} 应 > 0.5em=$maxWordGap",
            gaps.first() > maxWordGap + 1f
        )
    }

    // ── P3 单行钉：首行即末行 → 仍退化起始边（§2-4），缩进可见、不拉伸 ──
    // 防退化钉：S1b 前后行为逐位一致；此例防将来退化条件再被改动时单行段被误拉伸。
    @Test
    fun p3_singleLineParagraph_firstIsLast_staysStartAligned() {
        val indent = 2f * 48f
        val text = "Just one line"
        val (pages, paint) = layoutPages(text, firstLineIndent = indent)
        val lines = visibleLines(pages)
        assertEquals("单行段应恰 1 行（实际 ${lines.size}）", 1, lines.size)

        val ink = TextLayoutProvider.inkPad(paint.textSize)
        val effStart = ChapterProvider.paddingHorizontal + indent + ink
        val effEnd = ChapterProvider.visibleRight - ink

        val inkChars = lines.first().textChars.filter {
            !it.isImage && it.charData.firstOrNull()?.isWhitespace() != true
        }
        val contentLeft = inkChars.minOf { it.start }
        val contentRight = inkChars.maxOf { it.end }
        assertTrue(
            "首字符应在缩进位 effStart=$effStart，实际 $contentLeft",
            abs(contentLeft - effStart) <= 2f
        )
        assertTrue(
            "单行段不应拉伸：右缘缺口应 > 2em，实际 gap=${effEnd - contentRight}",
            effEnd - contentRight > 2f * paint.textSize
        )
    }
}
