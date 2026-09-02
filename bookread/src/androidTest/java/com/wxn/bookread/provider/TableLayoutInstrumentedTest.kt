package com.wxn.bookread.provider

import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.upLinesPosition
import com.wxn.bookread.textHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 表格统一摆放链路（A3.2/A3.3）仪器化测试——审查十补充覆盖（第七轮）。
 *
 * 被测对象是 TableLayoutProvider.layoutTableRow 生产全链（注解解析 → 单元格 StaticLayout →
 * placeCharsFromLayout 统一摆放 → 逻辑行发射 → buildRowBorders 边框），与 JVM 纯函数测试
 * （TableGeometry/TableRenderProviderBorder/TableDirectionResolver）互补，填补「单元格摆放
 * 零自动化」缺口。核心断言对应方案风险项：
 *  - R1/P-L2：字符盒落在所属单元格内容区内、视觉序连续不重叠（盒=字形位置）
 *  - P-L3/runLength 陷阱：阿语单元格 needsRunShaping=true、拉丁/数字 false（漏传 runLength
 *    即整组标志塌陷，此测试当场红）
 *  - P0-2：词中标签边界切 renderGroup（组首 paint 快照不吞样式）
 *  - R2：多行单元格行末字符 patch 盒宽 = measureText（LTR 精确等式）
 *  - N-Q1：paraSpacingZeroed 行盖章透传
 *  - RTL 镜像：首列贴右、列序相反
 *
 * harness 与 DrawRunShapingInstrumentedTest 同款：直接配置 ChapterProvider 静态字段，
 * 生产入口调用，禁止绕过被测代码。
 *
 * 运行：`gradlew :bookread:connectedDebugAndroidTest --tests "*TableLayoutInstrumentedTest*"`
 */
@RunWith(AndroidJUnit4::class)
class TableLayoutInstrumentedTest {

    private fun configProvider(width: Int) {
        ChapterProvider.apply {
            paddingHorizontal = 40
            paddingVertical = 40
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

    private fun paint(ts: Float) = TextPaint().apply { textSize = ts; isAntiAlias = true }

    /** 构造一行两列表格段落：line 文本 + td 偏移对 + 列宽百分比 */
    private fun tableRow(
        line: String,
        cells: List<Pair<Int, Int>>,
        percents: String,
        rowIndex: Int = 0,
        rows: Int = 1,
        extraTags: List<TextTag> = emptyList()
    ) = ReaderText.Text(line).also {
        it.annotations = buildList {
            add(TextTag(uuid = "tr", name = "tr", start = 0, end = 0, params = "index=$rowIndex"))
            add(TextTag(uuid = "table", name = "table", start = 0, end = 0,
                params = "cols=${cells.size}&rows=$rows&table_percent=$percents"))
            cells.forEach { (s, e) -> add(TextTag(uuid = "td$s", name = "td", start = s, end = e)) }
            addAll(extraTags)
        }
    }

    private class RowResult(
        val pages: ArrayList<TextPage>,
        val sb: StringBuilder,
        val cellTextLines: List<TextLine>
    )

    private fun layoutRow(
        paragraph: ReaderText.Text,
        tableIsRtl: Boolean,
        paraSpacingZeroed: Boolean = false,
        ts: Float = 40f,
        width: Int = 906
    ): RowResult {
        configProvider(width)
        val pages = arrayListOf(TextPage())
        val sb = StringBuilder()
        TableLayoutProvider.layoutTableRow(
            paragraph, paint(ts),
            marginLeft = 0f, marginRight = 0f,
            paragraphIndex = 0, textAlign = CssTextAlign.CssTextAlignLeft, lineHeightParam = 1f,
            textPages = pages, pageLines = arrayListOf(), pageLengths = arrayListOf(),
            stringBuilder = sb, offsetY = 40f,
            bounds = layoutBoundsPage(), paraSpacingZeroed = paraSpacingZeroed,
            tableIsRtl = tableIsRtl, chapterIsRtl = false
        )
        return RowResult(pages, sb, pages.flatMap { p -> p.textLines }.filter { it.isTableCell })
    }

    /** 用 TableGeometry 纯函数独立复算第 col 列的内容区（与被测代码平行核算，非同一份实现） */
    private fun cellRegion(width: Int, percents: List<Int>, col: Int, isRtl: Boolean): Pair<Float, Float> {
        val leftPct = percents.take(col).sum()
        val usable = (width * (percents[col] / 100f) - 2 * TableGeometry.CELL_INNER_PADDING).toInt().coerceAtLeast(1)
        val left = TableGeometry.cellLeftOffset(width.toFloat(), leftPct, percents[col], isRtl).toInt()
        val start = ChapterProvider.paddingHorizontal + 0f + left   // marginLeft=0
        return start to (start + usable)
    }

    private fun sortedBoxes(line: TextLine) = line.textChars.filter { !it.isImage }
        .sortedBy { it.start }

    // ─────────────────────────────────────────────────────────────

    /** 场景 1（LTR 基线，R1/P-L2）：盒在格内、视觉序连续不重叠；拉丁/数字逐字安全；TTS 拼接 */
    @Test
    fun ltr_boxes_in_cell_and_flags() {
        val row = layoutRow(tableRow("Hello 25", listOf(0 to 5, 6 to 8), "60%;40%"), tableIsRtl = false)
        val percents = listOf(60, 40)
        assertEquals(2, row.cellTextLines.map { it.colIndex }.toSet().size)

        percents.forEachIndexed { col, _ ->
            val (lo, hi) = cellRegion(906, percents, col, false)
            val boxes = sortedBoxes(row.cellTextLines.first { it.colIndex == col })
            assertTrue("col$col 应有字符", boxes.isNotEmpty())
            boxes.forEach { ch ->
                assertTrue("col$col 字符盒 [$${ch.start}, ${ch.end}] 越出内容区 [$lo, $hi]",
                    ch.start >= lo - 2f && ch.end <= hi + 2f)
                assertFalse("拉丁/数字应逐字安全（runLength 陷阱护栏）", ch.needsRunShaping)
            }
            for (k in 1 until boxes.size) {
                assertTrue("col$col 视觉序第$k 字重叠前字", boxes[k - 1].end <= boxes[k].start + 0.6f)
            }
        }
        // TTS：行主序、\t 分隔、末行 \n
        assertEquals("Hello\t25\n", row.sb.toString())
    }

    /** 场景 2（RTL 镜像，D1/§5.2）：首列贴右、两格不重叠；阿语整组整形；盖章透传 */
    @Test
    fun rtl_mirror_order_shaping_and_stamp() {
        val line = "عمر 25"   // ع م ر + 空格 + 2 5
        val row = layoutRow(
            tableRow(line, listOf(0 to 3, 4 to 6), "60%;40%"),
            tableIsRtl = true, paraSpacingZeroed = true
        )
        val percents = listOf(60, 40)
        val region0 = cellRegion(906, percents, 0, true)   // 首列 = 右侧
        val region1 = cellRegion(906, percents, 1, true)

        val boxes0 = sortedBoxes(row.cellTextLines.first { it.colIndex == 0 })
        val boxes1 = sortedBoxes(row.cellTextLines.first { it.colIndex == 1 })
        assertTrue("RTL 首列（右格）应在左格右侧：min0=${boxes0.minOf { it.start }} max1=${boxes1.maxOf { it.end }}",
            boxes0.minOf { it.start } >= boxes1.maxOf { it.end } - 2f)

        // 单行 RTL 单元格贴内容区右缘（ALIGN_NORMAL = RTL 基调起始边）
        assertTrue("RTL 首行文本应贴近右缘（居右）",
            boxes0.minOf { it.start } > (region0.first + region0.second) / 2f)

        // 阿语列整组整形（runLength 护栏）；数字列逐字安全
        assertTrue("阿语字符应全部整组整形", boxes0.all { it.needsRunShaping })
        assertTrue("数字应逐字安全", boxes1.none { it.needsRunShaping })

        // N-Q1 行盖章透传
        assertTrue("paraSpacingZeroed 应盖到每个单元格行",
            row.cellTextLines.all { it.letterSpacingZeroed })
    }

    /** 场景 3（R2 行末 patch）：窄列折行后，中间行行末字符盒宽 = measureText（LTR 精确等式） */
    @Test
    fun multiline_end_patch_box_width() {
        val text = "AAAAAAAA BBBB"
        val row = layoutRow(tableRow(text, listOf(0 to text.length), "25%"), tableIsRtl = false, ts = 57f)
        val cellLines = row.cellTextLines.sortedBy { it.charStartOffset }
        assertTrue("窄列应折为多行（实际 ${cellLines.size}）", cellLines.size >= 2)

        val nonLast = cellLines.dropLast(1)
        val p = paint(57f)
        nonLast.forEach { ln ->
            val boxes = sortedBoxes(ln)
            assertTrue(boxes.isNotEmpty())
            val last = boxes.last()
            val w = p.measureText(last.charData)
            assertEquals("行末 patch 盒宽应等于单字测量宽（localStart + chWidth）",
                w, last.end - last.start, 0.6f)
        }
        // 折行盒也必须留在内容区内
        val (lo, hi) = cellRegion(906, listOf(25), 0, false)
        cellLines.flatMap { sortedBoxes(it) }.forEach { ch ->
            assertTrue(ch.start >= lo - 2f && ch.end <= hi + 2f)
        }
    }

    /** 场景 4（P0-2 样式边界切组）：词中标签边界处 renderGroup 必须递增 */
    @Test
    fun style_boundary_splits_render_group() {
        val word = "عمرها"   // 5 字符阿语词
        val tag = TextTag(uuid = "hl", name = "highlight", start = 1, end = 3)   // 词中 [1,3)
        val row = layoutRow(
            tableRow(word, listOf(0 to word.length), "100%", extraTags = listOf(tag)),
            tableIsRtl = true
        )
        val chars = row.cellTextLines.first().textChars.filter { !it.isImage }
        assertEquals(word.length, chars.size)
        // cellStyleBoundaries 产出 {1,3}（+结构注解 clamp 到 0/5）→ 逻辑第 1 字后与第 3 字后切组
        assertTrue("切组后组数应 ≥ 2（实际 ${chars.map { it.renderGroup }.toSet().size}）",
            chars.map { it.renderGroup }.toSet().size >= 2)
        assertTrue("第 1 字后应切组", chars[1].renderGroup > chars[0].renderGroup)
        assertTrue("第 3 字后应切组", chars[3].renderGroup > chars[2].renderGroup)
        assertTrue("阿语词整组整形", chars.all { it.needsRunShaping })
    }

    /** 场景 5（A3.3 接线）：单行 2 列表边框 = 顶线 + 3 竖线 + 底线（rows=1 即末表行） */
    @Test
    fun borders_wired_from_emission_loop() {
        val row = layoutRow(tableRow("Hello 25", listOf(0 to 5, 6 to 8), "60%;40%"), tableIsRtl = false)
        val lineSegs = row.pages.first().textLines.filter { it.isLine }
        assertEquals("顶线 + 左/中/右竖线 + 底线", 5, lineSegs.size)

        val contentLeft = ChapterProvider.paddingHorizontal + 0f
        val contentRight = ChapterProvider.visibleRight - 0f
        val mid = contentLeft + TableGeometry.verticalBorderX(906f, 60f, false)
        assertEquals(contentLeft, lineSegs[1].lineStart.first, 0.01f)   // 左边界
        assertEquals(mid, lineSegs[2].lineStart.first, 0.01f)           // 内部线 60%
        assertEquals(contentRight, lineSegs[3].lineStart.first, 0.01f)  // 右边界
        // 竖线高度 = rowBoxHeight（lineHeight），顶线 y = rowTopY
        assertEquals(lineSegs[1].lineEnd.second - lineSegs[1].lineStart.second,
            lineSegs[1 + 1].lineEnd.second - lineSegs[2].lineStart.second, 0.01f)
        assertTrue(lineSegs[1].lineEnd.second > lineSegs[1].lineStart.second)
    }

    // ─────────────────────────────────────────────────────────────
    // [fix-S1] 页底 justify（upLinesPosition）与表格边框——上一轮方案的 §8.1 补录

    /**
     * 场景 6（[rigid-table]，重写）：表格块整块一槽——块内位移全等，块后正文精确贴底。
     * 旧版「边框继承最近文字行」断言固化的是 S2 缺陷模型（跨格/行界位移差 tj），已废弃。
     */
    @Test
    fun page_justify_table_block_rigid() {
        configProvider(width = 906)
        val p = paint(40f)
        val pages = arrayListOf(TextPage())
        val pl = arrayListOf<Int>(); val pLen = arrayListOf<Int>(); val sb = StringBuilder()
        var cursor = TableLayoutProvider.layoutTableRow(
            tableRow("Hello 25", listOf(0 to 5, 6 to 8), "60%;40%", rowIndex = 0, rows = 2),
            p, 0f, 0f, 0, CssTextAlign.CssTextAlignLeft, 1f,
            pages, pl, pLen, sb, offsetY = 40f, bounds = layoutBoundsPage(),
            tableIsRtl = false, chapterIsRtl = false)
        cursor = TableLayoutProvider.layoutTableRow(
            tableRow("Second Row", listOf(0 to 6, 7 to 10), "60%;40%", rowIndex = 1, rows = 2),
            p, 0f, 0f, 1, CssTextAlign.CssTextAlignLeft, 1f,
            pages, pl, pLen, sb, offsetY = cursor.offsetY, bounds = cursor.bounds,
            tableIsRtl = false, chapterIsRtl = false)
        val page = pages.first()
        // 追加块后正文行（真实页结构：表格后有正文段，页尾必为正文行）
        page.textLines.add(TextLine().apply {
            lineTop = cursor.offsetY; lineBase = lineTop + 50f; lineBottom = lineTop + 60f
        })

        val preTop = page.textLines.map { it.lineTop }
        val preHeight = page.height
        val preSegLen = page.textLines.map { it.lineEnd.second - it.lineStart.second }
        // 构造触发条件：页底空隙 100，且 visibleHeight - height < 末行行高（不跳过）
        ChapterProvider.visibleBottom = page.textLines.last().lineBottom.toInt() + 100
        ChapterProvider.visibleHeight = page.height.toInt()
        // 显式推导 surplus（toInt 截断使实际 surplus ≠ 字面 100，断言以其实际值为准）
        val surplusF = ChapterProvider.visibleBottom - page.textLines.last().lineBottom
        page.upLinesPosition()

        val shifts = page.textLines.mapIndexed { i, ln -> ln.lineTop - preTop[i] }
        // a) 表格块（除正文行外全部：4 文字 + 9 边框）位移全等（块为首槽 → 位移 0）
        val blockShifts = shifts.dropLast(1).distinct()
        assertEquals("表格块内位移应全等（刚性块）", 1, blockShifts.size)
        assertEquals("块为首槽位移 0", 0f, blockShifts.first(), 0.01f)
        // b) 正文行（次槽）贴底：位移 == surplus（tj = surplusF / (2-1)）
        assertEquals("末正文行应精确贴底",
            ChapterProvider.visibleBottom.toFloat(), page.textLines.last().lineBottom, 0.01f)
        assertEquals(surplusF, shifts.last(), 0.01f)
        // c) 边框段长不变（平移非缩放）
        page.textLines.forEachIndexed { i, ln ->
            if (ln.isLine) assertEquals("边框段长应不变", preSegLen[i],
                ln.lineEnd.second - ln.lineStart.second, 0.01f)
        }
        // d) height 记账 == 原值 + surplus（账实一致）
        assertEquals(preHeight + surplusF, page.height, 0.01f)
    }

    /** 场景 7（[fix-S1] 回归等价）：纯文本页位移公式与旧实现逐位一致 */
    @Test
    fun page_justify_pure_text_unchanged() {
        configProvider(width = 906)
        val page = TextPage()
        val lh = 60f
        repeat(5) { k ->
            page.textLines.add(TextLine().apply {
                lineTop = 40f + k * lh; lineBase = lineTop + 50f; lineBottom = lineTop + lh
            })
        }
        page.height = 40f + 5 * lh
        ChapterProvider.visibleBottom = page.textLines.last().lineBottom.toInt() + 90
        ChapterProvider.visibleHeight = page.height.toInt()
        val surplusF = ChapterProvider.visibleBottom - page.textLines.last().lineBottom
        page.upLinesPosition()
        val tj = surplusF / (page.textLines.size - 1)
        page.textLines.forEachIndexed { i, ln ->
            assertEquals("第 $i 行位移应为 tj×i", tj * i, ln.lineTop - (40f + i * 60f), 0.01f)
        }
    }

    // ─────────────────────────────────────────────────────────────
    // [vcenter] 单元格内容垂直居中（2026-09-02 方案 §8.2 场景 8-10）

    /** 行盒几何平行核算（与被测代码非同一份实现）：行盒高 = textHeight×lineSpacingExtra×param */
    private fun boxGeometry(ts: Float, spacing: Float = ChapterProvider.lineSpacingExtra,
                            param: Float = 1f): Triple<Float, Float, Float> {
        val th = paint(ts).textHeight
        val lineHeight = th * spacing * param
        val inline = ((lineHeight - th) / 2f).coerceAtLeast(0f)
        return Triple(th, lineHeight, inline)
    }

    /** 场景 8（[vcenter]）：单行格文字盒在行盒内垂直居中，与边框上下间隙相等 */
    @Test
    fun single_line_cell_vertically_centered() {
        val row = layoutRow(tableRow("Hello 25", listOf(0 to 5, 6 to 8), "60%;40%"), tableIsRtl = false)
        val rowTop = 40f                                   // offsetY = paddingVertical
        val (th, lineHeight, inline) = boxGeometry(40f)

        row.cellTextLines.forEach { ln ->
            assertEquals("col${ln.colIndex} 行盒内居中", rowTop + inline, ln.lineTop, 0.01f)
            assertEquals("文字盒高 = textHeight", th, ln.lineBottom - ln.lineTop, 0.01f)
        }

        // 与边框的上下间隙相等（顶/底线 Y 由 buildRowBorders 输入平行核算）
        val borders = row.pages.first().textLines.filter { it.isLine }
        assertTrue("应有横线+竖线", borders.size >= 5)
        val topBorderY = borders.minOf { it.lineStart.second }
        val bottomBorderY = borders.maxOf { it.lineEnd.second }
        assertEquals(rowTop, topBorderY, 0.01f)
        assertEquals(rowTop + lineHeight, bottomBorderY, 0.01f)
        row.cellTextLines.forEach { ln ->
            assertEquals("上间隙==下间隙",
                ln.lineTop - topBorderY, bottomBorderY - ln.lineBottom, 0.01f)
        }
    }

    /** 场景 9（[vcenter]）：col0 折多行、col1 单行时，col1 内容块相对整行居中 */
    @Test
    fun short_cell_centered_against_tall_neighbor() {
        // 32 个 A + " BBB" ≈ 900px，超出 60% 列宽可用 ~523px，确定折为 ≥2 行；
        // 单元格分隔用普通空格（不属于任何单元格子串，测量行为确定）
        val longText = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA BBB"
        val row = layoutRow(
            tableRow(longText + " X", listOf(0 to longText.length, longText.length + 1 to longText.length + 2), "60%;40%"),
            tableIsRtl = false)
        val col0 = row.cellTextLines.filter { it.colIndex == 0 }.sortedBy { it.lineTop }
        val col1 = row.cellTextLines.filter { it.colIndex == 1 }
        assertTrue("col0 应折为 ≥2 行（实际 ${col0.size}）", col0.size >= 2)
        assertEquals("col1 应单行", 1, col1.size)

        val n0 = col0.size                                  // 行盒总数 N = col0 行数（col1 占 1 盒）
        val rowTop = 40f
        val (_, lineHeight, inline) = boxGeometry(40f)

        // col0 占满全部 N 个行盒：blockOffset = 0，逐行贴各盒顶 + inline
        col0.forEachIndexed { k, ln ->
            assertEquals("col0 第 $k 行", rowTop + k * lineHeight + inline, ln.lineTop, 0.01f)
        }
        // col1（1 行）：blockOffset = (N - 1) × lineHeight / 2
        assertEquals("col1 内容块整体居中",
            rowTop + (n0 - 1) * lineHeight / 2f + inline, col1.first().lineTop, 0.01f)

        // 边框不受居中影响：竖线逐逻辑行发射（每段高 = 1 行盒），全部竖线段的总跨距 = N × lineHeight
        val vs = row.pages.first().textLines.filter { it.isLine && it.lineStart.first == it.lineEnd.first }
        assertEquals("竖线总跨距 = N×lineHeight",
            n0 * lineHeight, vs.maxOf { it.lineEnd.second } - vs.minOf { it.lineStart.second }, 0.01f)
    }

    /** 场景 10（[vcenter]）：行距系数压缩（lineHeight < textHeight）时钳 0，文字不越出顶边框 */
    @Test
    fun compressed_line_spacing_clamps_to_zero() {
        configProvider(906)
        ChapterProvider.lineSpacingExtra = 0.75f
        try {
            val pages = arrayListOf(TextPage())
            val sb = StringBuilder()
            TableLayoutProvider.layoutTableRow(
                tableRow("Hello 25", listOf(0 to 5, 6 to 8), "60%;40%"), paint(40f),
                marginLeft = 0f, marginRight = 0f,
                paragraphIndex = 0, textAlign = CssTextAlign.CssTextAlignLeft, lineHeightParam = 1f,
                textPages = pages, pageLines = arrayListOf(), pageLengths = arrayListOf(),
                stringBuilder = sb, offsetY = 40f,
                bounds = layoutBoundsPage(), paraSpacingZeroed = false,
                tableIsRtl = false, chapterIsRtl = false
            )
            val (th, lineHeight, _) = boxGeometry(40f, spacing = 0.75f)
            assertTrue("前置：行盒高应小于文字盒高", lineHeight < th)
            pages.first().textLines.filter { it.isTableCell }.forEach { ln ->
                assertEquals("钳位后贴盒顶（不越出顶边框）", 40f, ln.lineTop, 0.01f)
            }
        } finally {
            ChapterProvider.lineSpacingExtra = 1.2f
        }
    }

    // ─────────────────────────────────────────────────────────────
    // [rigid-table] S2：表格刚性块 justify（2026-09-02 方案 §7.1 场景 11/12）

    /** 排版 N 行 × 3 格单行表 + 块后正文行，返回（页, 正文行前的行数） */
    private fun layoutThreeRowTableWithBody(): Pair<TextPage, Int> {
        configProvider(width = 906)
        val p = paint(40f)
        val pages = arrayListOf(TextPage())
        val pl = arrayListOf<Int>(); val pLen = arrayListOf<Int>(); val sb = StringBuilder()
        val rowTexts = listOf("Alpha Beta Gamma", "Delta Epsilon Zeta", "Eta Theta Iota")
        var cursor = LayoutCursor(40f, layoutBoundsPage())
        rowTexts.forEachIndexed { r, line ->
            cursor = TableLayoutProvider.layoutTableRow(
                tableRow(line, listOf(0 to 5, 6 to 11, 12 to 16), "34%;33%;33%",
                    rowIndex = r, rows = rowTexts.size),
                p, 0f, 0f, r, CssTextAlign.CssTextAlignLeft, 1f,
                pages, pl, pLen, sb, offsetY = cursor.offsetY, bounds = cursor.bounds,
                tableIsRtl = false, chapterIsRtl = false)
        }
        val page = pages.first()
        page.textLines.add(TextLine().apply {   // 块后正文行（页尾必为正文行的真实页结构）
            lineTop = cursor.offsetY; lineBase = lineTop + 50f; lineBottom = lineTop + 60f
        })
        return page to page.textLines.size
    }

    /**
     * 场景 11（[rigid-table]）：justify 后同行跨格对齐、行间边框连续、格内居中保持。
     * 「全部边框位移相等」同时是 isLine 全库唯一生产者不变量的护栏（R-3）。
     */
    @Test
    fun table_cross_cell_alignment_and_border_continuity() {
        val (page, lineCount) = layoutThreeRowTableWithBody()
        val preTop = page.textLines.map { it.lineTop }
        val preRowTop = page.textLines.map { if (it.isLine) it.lineStart.second else it.lineTop }
        val lh = boxGeometry(40f).second
        ChapterProvider.visibleBottom = page.textLines.last().lineBottom.toInt() + 100
        ChapterProvider.visibleHeight = page.height.toInt()
        val surplusF = ChapterProvider.visibleBottom - page.textLines.last().lineBottom
        page.upLinesPosition()

        val cells = page.textLines.filter { it.isTableCell }
        val borders = page.textLines.filter { it.isLine }
        // a) 跨格对齐：同 rowIndex 的三个单元格 lineTop 相等（S2 症状②护栏）
        cells.groupBy { it.rowIndex }.forEach { (row, lines) ->
            assertEquals("第 $row 行跨格 lineTop 应相等", 1,
                lines.map { it.lineTop }.distinctBy { Math.round(it * 100) }.size)
        }
        // b) 刚性：全部边框位移相等且 == 单元格位移（块内同移；症状①③护栏 + isLine 生产者护栏）
        val firstCellIdx = page.textLines.indexOfFirst { it.isTableCell }
        val cellShift = page.textLines[firstCellIdx].lineTop - preTop[firstCellIdx]
        val borderShifts = page.textLines.mapIndexed { i, ln ->
            if (ln.isLine) Math.round((ln.lineStart.second - preRowTop[i]) * 100) else null
        }.filterNotNull().distinct()
        assertEquals("全部边框位移应相等", 1, borderShifts.size)
        assertEquals("边框位移应等于单元格位移", Math.round(cellShift * 100), borderShifts.first())
        // c) 行间连续：横线 Y 等距（相邻行界差 == lineHeight）、竖线段高 == lineHeight（S2 症状①护栏）
        val hYs = borders.filter { it.lineStart.first != it.lineEnd.first }
            .map { it.lineStart.second }.sorted()
        assertEquals("应有 3 顶线 + 1 底线", 4, hYs.size)
        for (k in 0..2) assertEquals("行界连续：横线间距应等于行盒高",
            lh, hYs[k + 1] - hYs[k], 0.01f)
        borders.filter { it.lineStart.first == it.lineEnd.first }.forEach { v ->
            assertEquals("竖线段高应等于行盒高", lh, v.lineEnd.second - v.lineStart.second, 0.01f)
        }
        // d) 块后正文行贴底
        assertEquals("末正文行应精确贴底",
            ChapterProvider.visibleBottom.toFloat(), page.textLines.last().lineBottom, 0.01f)
        assertEquals("正文行位移应等于 surplus", Math.round(surplusF * 100),
            Math.round((page.textLines.last().lineTop - preTop[lineCount - 1]) * 100))
    }

    /** 场景 12（[rigid-table]）：页尾为边框行的表格页（:60 守卫）justify 后零位移 */
    @Test
    fun table_last_page_skips_justify() {
        configProvider(width = 906)
        val pages = arrayListOf(TextPage())
        val pl = arrayListOf<Int>(); val pLen = arrayListOf<Int>(); val sb = StringBuilder()
        TableLayoutProvider.layoutTableRow(
            tableRow("Hello 25", listOf(0 to 5, 6 to 8), "60%;40%", rowIndex = 0, rows = 1),
            paint(40f), 0f, 0f, 0, CssTextAlign.CssTextAlignLeft, 1f,
            pages, pl, pLen, sb, offsetY = 40f, bounds = layoutBoundsPage(),
            tableIsRtl = false, chapterIsRtl = false)
        val page = pages.first()
        assertTrue("前置：页尾应为边框行", page.textLines.last().isLine)
        val preTop = page.textLines.map { it.lineTop }
        // 构造「若不守卫即会触发」的条件
        ChapterProvider.visibleBottom = page.textLines.last().lineBottom.toInt() + 100
        ChapterProvider.visibleHeight = page.height.toInt()
        page.upLinesPosition()
        page.textLines.forEachIndexed { i, ln ->
            assertEquals("页尾边框行页应整体跳过 justify", preTop[i], ln.lineTop, 0.01f)
        }
    }
}
