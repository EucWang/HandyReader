package com.wxn.bookread.provider

import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.textIndexAt
import com.wxn.bookread.data.model.upLinesPosition
import com.wxn.bookread.textHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        val cellTextLines: List<TextLine>,
        val cursor: LayoutCursor? = null
    )

    private fun layoutRow(
        paragraph: ReaderText.Text,
        tableIsRtl: Boolean,
        paraSpacingZeroed: Boolean = false,
        ts: Float = 40f,
        width: Int = 906,
        align: CssTextAlign = CssTextAlign.CssTextAlignLeft,
        dual: Boolean = false,
        chapterIsRtl: Boolean = false,
        boundsFactory: (() -> LayoutBounds)? = null,   // configProvider 配置之后求值（列几何依赖单例字段）
        visibleBottomLines: Float? = null   // 非 null：visibleBottom = paddingVertical + N×行盒高（列流用）
    ): RowResult {
        configProvider(width)
        if (dual) {   // 双列 harness（列流方案 §4）：columnWidth=433; columnGapActual=40; visibleWidth=906
            ChapterProvider.columnGapActual = 40
            ChapterProvider.columnWidth = (ChapterProvider.visibleWidth - ChapterProvider.columnGapActual) / 2
            ChapterProvider.dualColumnEnabled = true
        }
        if (visibleBottomLines != null) {
            val lh = boxGeometry(ts).second
            ChapterProvider.visibleBottom = (ChapterProvider.paddingVertical + visibleBottomLines * lh).toInt()
        }
        val pages = arrayListOf(TextPage())
        val sb = StringBuilder()
        val cursor = TableLayoutProvider.layoutTableRow(
            paragraph, paint(ts),
            marginLeft = 0f, marginRight = 0f,
            paragraphIndex = 0, textAlign = align, lineHeightParam = 1f,
            textPages = pages, pageLines = arrayListOf(), pageLengths = arrayListOf(),
            stringBuilder = sb, offsetY = 40f,
            bounds = boundsFactory?.invoke() ?: layoutBoundsPage(),
            paraSpacingZeroed = paraSpacingZeroed,
            tableIsRtl = tableIsRtl, chapterIsRtl = chapterIsRtl
        )
        return RowResult(pages, sb, pages.flatMap { p -> p.textLines }.filter { it.isTableCell }, cursor)
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

    // ─────────────────────────────────────────────────────────────
    // [per-run] 单元格混排移植测试（方案 2026-09-02-plan-table-cell-per-run-engine.md §7.2）

    /** T-I1（C §8.1 移植）：RTL 表纯拉丁/数字格——盒不重叠、宽合理、字符守恒 */
    @Test
    fun rtl_cell_ltr_boxes_no_overlap_and_sane_width() {
        val p = paint(40f)
        listOf("12345", "English Text").forEach { text ->
            val row = layoutRow(tableRow(text, listOf(0 to text.length), "100%"), tableIsRtl = true)
            val line = row.cellTextLines.single()
            assertEquals("应单行", 1, row.cellTextLines.size)
            val boxes = sortedBoxes(line)
            assertEquals("字符数守恒", text.length, boxes.size)
            for (k in 1 until boxes.size) {
                assertTrue("相邻盒不重叠", boxes[k - 1].end <= boxes[k].start + 0.6f)
            }
            boxes.forEach { ch ->
                assertTrue("盒宽合理：'${ch.charData}'",
                    ch.end - ch.start <= 2.5f * p.measureText(ch.charData) + 2f)
            }
        }
    }

    /** T-I2（C §8.1b 移植）：RTL 表折行拉丁格——行首/行末不飞、patch 契约保持 */
    @Test
    fun rtl_cell_ltr_wrapped_lines_no_fly() {
        val text = "antidisestablishmentarianism"
        val row = layoutRow(tableRow(text, listOf(0 to text.length), "25%"), tableIsRtl = true, ts = 57f)
        val cellLines = row.cellTextLines.sortedBy { it.charStartOffset }
        assertTrue("窄列应折为多行（实际 ${cellLines.size}）", cellLines.size >= 2)

        val p = paint(57f)
        val allBoxes = cellLines.flatMap { sortedBoxes(it) }
        assertEquals("折行字符数合计守恒", text.length, allBoxes.size)

        cellLines.dropLast(1).forEach { ln ->
            val boxes = sortedBoxes(ln)
            val last = boxes.last()
            assertEquals("非末行行末盒宽 = measureText（patch 契约）",
                p.measureText(last.charData), last.end - last.start, 0.6f)
        }
        cellLines.forEach { ln ->
            val boxes = sortedBoxes(ln)
            assertTrue("行首盒宽合理（行首不再中毒）",
                boxes.first().end - boxes.first().start <= 2.5f * p.measureText(boxes.first().charData) + 2f)
            for (k in 1 until boxes.size) {
                assertTrue(boxes[k - 1].end <= boxes[k].start + 0.6f)
            }
        }
        val (lo, hi) = cellRegion(906, listOf(25), 0, true)
        allBoxes.forEach { ch ->
            assertTrue("盒应落在格内容区", ch.start >= lo - 2f && ch.end <= hi + 2f)
        }
    }

    /** T-I3（C §8.2 移植 + 审查 F-A）：RTL 表混排格——组墨迹跨度=自然宽、组墨迹区间两两分离 */
    @Test
    fun rtl_cell_mixed_group_anchor_at_true_left() {
        val text = "الكتاب Book 123 نهاية"
        val row = layoutRow(tableRow(text, listOf(0 to text.length), "100%"), tableIsRtl = true)
        val line = row.cellTextLines.single()
        val chars = line.textChars.filter { !it.isImage }
        assertEquals(text.length, chars.size)

        val groups = chars.groupBy { it.renderGroup }.values
        // 方向交界的中性空格随「后随 run」开头（SheenBidi run 边界），可形成纯空白组——
        // 组计数与墨迹断言只对含墨组生效（空白无墨、盒值真实，不影响渲染与锚点）
        val inkOf: (List<com.wxn.bookread.data.model.TextChar>) -> List<com.wxn.bookread.data.model.TextChar> =
            { g -> g.filter { it.charData.firstOrNull()?.isWhitespace() != true } }
        assertTrue("含墨组数应 ≥ 4（总组数 ${groups.size}）",
            groups.count { inkOf(it).isNotEmpty() } >= 4)

        val p = paint(40f)
        groups.forEach { g ->
            val ink = inkOf(g)
            if (ink.isEmpty()) return@forEach   // 纯空白组：无墨迹可断言
            val groupText = g.joinToString("") { it.charData }
            val span = ink.maxOf { it.end } - ink.minOf { it.start }
            assertTrue("组墨迹跨度 ≤ natural+3f：'$groupText'",
                span <= p.measureText(groupText.trim()) + 3f)
            g.filter { !it.needsRunShaping }.forEach { ch ->
                assertTrue("强 LTR 盒宽合理：'${ch.charData}'",
                    ch.end - ch.start <= 2.5f * p.measureText(ch.charData) + 2f)
            }
        }
        // F-A：组墨迹区间两两分离（必须去空白成员——组尾空白的真实视觉槽可翻到组左侧）
        val inkSorted = groups.mapNotNull { g ->
            val ink = inkOf(g)
            if (ink.isEmpty()) null else ink.minOf { it.start } to ink.maxOf { it.end }
        }.sortedBy { it.first }
        for (k in 1 until inkSorted.size) {
            assertTrue("组墨迹区间应两两分离：$inkSorted",
                inkSorted[k - 1].second <= inkSorted[k].first + 0.6f)
        }
    }

    /** T-I4（门禁 D4 锁）：CENTER/RIGHT 对齐样式不改变格内锚定——恒表基调起始缘 */
    @Test
    fun cell_alignment_independent_start_edge() {
        listOf(CssTextAlign.CssTextAlignCenter, CssTextAlign.CssTextAlignRight).forEach { align ->
            val rtlRow = layoutRow(tableRow("Hello", listOf(0 to 5), "100%"), tableIsRtl = true, align = align)
            val ltrRow = layoutRow(tableRow("Hello", listOf(0 to 5), "100%"), tableIsRtl = false, align = align)
            val (loR, hiR) = cellRegion(906, listOf(100), 0, true)
            val (loL, _) = cellRegion(906, listOf(100), 0, false)
            val usable = hiR - loR
            val rtlBoxes = sortedBoxes(rtlRow.cellTextLines.single())
            val ltrBoxes = sortedBoxes(ltrRow.cellTextLines.single())
            val rtlW = rtlBoxes.maxOf { it.end } - rtlBoxes.minOf { it.start }
            assertEquals("RTL 表 $align 样式仍锚右缘", loR + usable - rtlW, rtlBoxes.minOf { it.start }, 1f)
            assertEquals("LTR 表 $align 样式仍锚左缘", loL, ltrBoxes.minOf { it.start }, 1f)
        }
    }

    /** T-I5（T1/T3 镜像锁）：LTR 表阿语格——行基调不翻向（D3）、贴起始左缘 */
    @Test
    fun ltr_table_arabic_cell_mirror_unchanged() {
        val row = layoutRow(tableRow("عمر 25", listOf(0 to 3, 4 to 6), "60%;40%"), tableIsRtl = false)
        val line0 = row.cellTextLines.first { it.colIndex == 0 }
        assertEquals("D3：行基调 = 表基调（整格不翻向）", false, line0.isRtl)
        val boxes0 = sortedBoxes(line0)
        val (lo0, _) = cellRegion(906, listOf(60, 40), 0, false)
        assertTrue("阿语格贴起始左缘（min=${boxes0.minOf { it.start }}, lo0=$lo0）",
            boxes0.minOf { it.start } in (lo0 - 1f)..(lo0 + 4f))
        assertTrue("阿语组整组整形", boxes0.all { it.needsRunShaping })
        for (k in 1 until boxes0.size) {
            assertTrue(boxes0[k - 1].end <= boxes0[k].start + 0.6f)
        }
    }

    /** T-I6：TTS 拼接契约 + 行标志透传（发射循环零改动的行为锁） */
    @Test
    fun rtl_table_mixed_row_tts_and_flags_contract() {
        val row = layoutRow(
            tableRow("AB Cd", listOf(0 to 2, 3 to 5), "50%;50%"),
            tableIsRtl = true, paraSpacingZeroed = true)
        assertEquals("TTS 单行拼接", "AB\tCd\n", row.sb.toString())
        assertTrue("行基调盖章", row.cellTextLines.all { it.isRtl })
        assertTrue("letterSpacingZeroed 透传", row.cellTextLines.all { it.letterSpacingZeroed })
        assertEquals("rowLineOffset = tagCell.start",
            listOf(0, 3), row.cellTextLines.sortedBy { it.colIndex }.map { it.rowLineOffset })

        // col0 折 2 行 + col1 单行：发射循环按逻辑行合并（key 语义保持）。
        // 断行空格归属行 0（"AAAA "，与现状单 layout 逐位一致）；逻辑行间无分隔符，仅末行补 \n
        val row2 = layoutRow(
            tableRow("AAAA BBBB X", listOf(0 to 9, 10 to 11), "25%;75%"),
            tableIsRtl = true, ts = 57f)
        assertEquals("TTS 折行拼接", "AAAA \tXBBBB\n", row2.sb.toString())
        assertEquals(2, row2.cellTextLines.filter { it.colIndex == 0 }.size)
        assertEquals(1, row2.cellTextLines.filter { it.colIndex == 1 }.size)
    }

    /** T-I7（审查 F-B）：空/空白格契约——每列 1 个 TextLine、垂直居中记账一致 */
    @Test
    fun blank_and_whitespace_cell_emit_single_box() {
        val line = "L  X"   // 0='L',1=' ',2=' ',3='X' → 三格："L"、""（空段）、" "
        val row = layoutRow(
            tableRow(line, listOf(0 to 1, 1 to 1, 2 to 3), "34%;33%;33%"),
            tableIsRtl = false)
        val cols = row.cellTextLines.sortedBy { it.colIndex }
        assertEquals("三列各 1 个 TextLine", listOf(0, 1, 2), cols.map { it.colIndex })
        assertTrue("三列 lineTop 全等（垂直居中记账一致）",
            cols.map { it.lineTop }.distinctBy { Math.round(it * 100) }.size == 1)
        assertTrue("空格列 textChars 为空", cols[1].textChars.isEmpty())
        assertEquals("空白列恰 1 个空白盒", 1, cols[2].textChars.size)
        assertTrue("行基调盖章", cols.all { !it.isRtl })
    }

    // ─────────────────────────────────────────────────────────────
    // [column-flow] 行中途溢出承接同页次列（方案 2026-09-02-plan-table-column-flow.md R3 §4）
    // 几何：padH=40；左列 [40,473]、右列 [513,946]、列距 473、间隙中线 493

    /** 单词强制断词文本：恰折为 [lines] 个逻辑行（窄列断词与 StaticLayout 行为一致） */
    private fun forceBreakText(lines: Int, fullWidth: Float, ts: Float = 40f): String {
        val usable = fullWidth * (100 / 100f) - 2 * TableGeometry.CELL_INNER_PADDING   // "100%" 列
        val capacity = (usable / paint(ts).measureText("A")).toInt()
        return "A".repeat((lines - 1) * capacity + capacity / 2)
    }

    private companion object {
        const val COL_W = 433f
        const val GAP_MID = 493f   // padH + COL_W + gap/2 = 40+433+20
        const val COL_DIST = 473f  // 右列 startX − 左列 startX
    }

    /** T-1：RTL 首列（右列）中途溢出 → 行 2 承接左列；X 刚性平移、边框按块重建、页高单调、cursor 落左列 */
    @Test
    fun column_flow_rtl_first_column_overflows_to_second() {
        val text = forceBreakText(3, COL_W)
        val row = layoutRow(
            tableRow(text, listOf(0 to text.length), "100%"),
            tableIsRtl = true, dual = true, chapterIsRtl = true,
            boundsFactory = { layoutBoundsRightColumn() }, visibleBottomLines = 2.5f)
        val (_, lineHeight, inline) = boxGeometry(40f)
        assertEquals("前置：单格 3 逻辑行", 3, row.cellTextLines.size)
        assertEquals("列流不结页", 1, row.pages.size)

        // 按列分块（间隙中线 493 分界）
        val b1 = row.cellTextLines.filter { sortedBoxes(it).minOf { c -> c.start } > GAP_MID }
        val b2 = row.cellTextLines.filter { sortedBoxes(it).minOf { c -> c.start } <= GAP_MID }
        assertEquals("块 1 = 行 0-1（右列）", 2, b1.size)
        assertEquals("块 2 = 行 2（左列）", 1, b2.size)

        // 平行核算两列内容区（RTL 100%：leftOffset = cellLeftOffset，同一函数非同一调用点）
        val usable = COL_W - 2 * TableGeometry.CELL_INNER_PADDING
        val loR = 40f + COL_W + 40f + TableGeometry.cellLeftOffset(COL_W, 0, 100, true)
        val loL = 40f + TableGeometry.cellLeftOffset(COL_W, 0, 100, true)
        b1.flatMap { sortedBoxes(it) }.forEach {
            assertTrue("块1 盒应落右列内容区", it.start >= loR - 2f && it.end <= loR + usable + 2f) }
        b2.flatMap { sortedBoxes(it) }.forEach {
            assertTrue("块2 盒应落左列内容区", it.start >= loL - 2f && it.end <= loL + usable + 2f) }

        // X 刚性平移：RTL 右缘锚定 → 块1/块2 maxEnd 差 = 列距
        val maxEnd1 = b1.flatMap { sortedBoxes(it) }.maxOf { it.end }
        val maxEnd2 = b2.flatMap { sortedBoxes(it) }.maxOf { it.end }
        assertEquals("块界 X 平移 = 列距 473", COL_DIST, maxEnd1 - maxEnd2, 1f)

        // 块 2 Y：durY 重置 + blockOffset(单格 = 0) + inline（F-C4）
        assertEquals(ChapterProvider.paddingVertical + inline, b2.single().lineTop, 0.01f)

        // 边框按块重建：顶线（右列）+ 竖线×6（右 4 左 2）+ 底线（左列，无顶线）
        val segs = row.pages.single().textLines.filter { it.isLine }
        val hs = segs.filter { it.lineStart.first != it.lineEnd.first }
        assertEquals("顶线 + 底线", 2, hs.size)
        val topH = hs.first { it.lineStart.first > GAP_MID }
        val botH = hs.first { it.lineStart.first < GAP_MID }
        assertEquals(ChapterProvider.paddingVertical.toFloat(), topH.lineStart.second, 0.01f)
        assertEquals(ChapterProvider.paddingVertical + lineHeight, botH.lineStart.second, 0.01f)
        // 边框跨表格内容区（contentLeft/Right = 列界 + margin），非单元格内容区
        assertEquals(40f + COL_W + 40f, topH.lineStart.first, 0.01f)     // 右列 contentLeft = 513
        assertEquals(40f + 2 * COL_W + 40f, topH.lineEnd.first, 0.01f)   // 右列 contentRight = 946
        assertEquals(40f, botH.lineStart.first, 0.01f)                    // 左列 contentLeft
        assertEquals(40f + COL_W, botH.lineEnd.first, 0.01f)              // 左列 contentRight = 473
        val vs = segs.filter { it.lineStart.first == it.lineEnd.first }
        assertEquals("3 逻辑行 × 2 竖线", 6, vs.size)
        vs.filter { it.lineStart.first < GAP_MID }.forEach {
            assertEquals(ChapterProvider.paddingVertical.toFloat(), it.lineStart.second, 0.01f)
            assertEquals(ChapterProvider.paddingVertical + lineHeight, it.lineEnd.second, 0.01f)
        }

        // 页高单调化（F-C6）：块 2 不得覆盖块 1 峰值（未修复时 = padding + 1×行高，可区分）
        assertEquals(ChapterProvider.paddingVertical + 2 * lineHeight, row.pages.single().height, 0.01f)
        // TTS 全文完整；cursor 落左列 + 1 行
        assertEquals("$text\n", row.sb.toString())
        assertEquals(layoutBoundsLeftColumn(), row.cursor?.bounds)
        assertEquals(ChapterProvider.paddingVertical + lineHeight, row.cursor!!.offsetY, 0.01f)
    }

    /**
     * T-1b（F-C3 实施修正）：首行即溢出——级联路径契约锁。
     * 行首预检（L97，与发射溢出同式且先行）先做同页切列（右→左），发射行 0 仍溢出 →
     * 新页回首列（右）；行 1 列流承接左列；行 2 次列再溢出 → 新页右列。
     * 注：发射循环内 index=0 的"纯列流承接"不可达（预检必然先拦截）——本用例锁
     * 级联正确性：空页结页、顶边框随实际首发块、无内容丢失、TTS 跨结页/列流续拼完整。
     */
    @Test
    fun column_flow_first_line_overflows_cascade_contract() {
        val text = forceBreakText(3, COL_W)
        val row = layoutRow(
            tableRow(text, listOf(0 to text.length), "100%"),
            tableIsRtl = true, dual = true, chapterIsRtl = true,
            boundsFactory = { layoutBoundsRightColumn() }, visibleBottomLines = 0.5f)
        assertEquals(3, row.pages.size)
        assertEquals("页 1 为预检切列后的空页结页（既有新页路径行为）", "", row.pages[0].text)
        assertTrue(row.pages[0].textLines.isEmpty())
        // 页 2：行 0（右列，顶线随实际首发块）+ 行 1（列流承接左列）
        val p2Lines = row.pages[1].textLines.filter { it.isTableCell }
        assertEquals(2, p2Lines.size)
        assertTrue("行 0 应平移到右列（新页首列）", sortedBoxes(p2Lines[0]).minOf { it.start } > GAP_MID)
        assertTrue("行 1 应列流承接左列", sortedBoxes(p2Lines[1]).minOf { it.start } < GAP_MID)
        val p2H = row.pages[1].textLines.filter { it.isLine && it.lineStart.first != it.lineEnd.first }
        assertEquals("顶线恰 1 条且随首发块", 1, p2H.size)
        assertTrue(p2H[0].lineStart.first > GAP_MID)
        // 页 3：行 2（次列再溢出 → 新页右列）
        val p3Lines = row.pages[2].textLines.filter { it.isTableCell }
        assertEquals(1, p3Lines.size)
        assertTrue(sortedBoxes(p3Lines[0]).minOf { it.start } > GAP_MID)
        assertEquals("TTS 跨结页/列流续拼完整（末页内容在 sb，页 text 由章节循环收尾写）", "$text\n",
            row.pages[0].text + row.pages[1].text + row.sb.toString())
        assertEquals(layoutBoundsRightColumn(), row.cursor?.bounds)
    }

    /** T-2：LTR 镜像——首列（左列）中途溢出承接右列；左缘锚定 → minStart 平移 +473 */
    @Test
    fun column_flow_ltr_mirror() {
        val text = forceBreakText(3, COL_W)
        val row = layoutRow(
            tableRow(text, listOf(0 to text.length), "100%"),
            tableIsRtl = false, dual = true, chapterIsRtl = false,
            boundsFactory = { layoutBoundsLeftColumn() }, visibleBottomLines = 2.5f)
        val (_, lineHeight, inline) = boxGeometry(40f)
        assertEquals(3, row.cellTextLines.size)
        assertEquals(1, row.pages.size)
        val b1 = row.cellTextLines.filter { sortedBoxes(it).minOf { c -> c.start } < GAP_MID }
        val b2 = row.cellTextLines.filter { sortedBoxes(it).minOf { c -> c.start } > GAP_MID }
        assertEquals(2, b1.size); assertEquals(1, b2.size)
        // LTR 左缘锚定：全部行共享同一 minStart → 块界差 = +473
        val minStart1 = b1.flatMap { sortedBoxes(it) }.minOf { it.start }
        val minStart2 = b2.flatMap { sortedBoxes(it) }.minOf { it.start }
        assertEquals(COL_DIST, minStart2 - minStart1, 1f)
        assertEquals(ChapterProvider.paddingVertical + inline, b2.single().lineTop, 0.01f)
        // 边框镜像：顶线左列、底线右列
        val hs = row.pages.single().textLines.filter { it.isLine && it.lineStart.first != it.lineEnd.first }
        assertEquals(2, hs.size)
        assertTrue(hs.any { it.lineStart.first < GAP_MID && it.lineStart.second == ChapterProvider.paddingVertical.toFloat() })
        assertTrue(hs.any { it.lineStart.first > GAP_MID && it.lineStart.second == ChapterProvider.paddingVertical + lineHeight })
        assertEquals(ChapterProvider.paddingVertical + 2 * lineHeight, row.pages.single().height, 0.01f)
        assertEquals("$text\n", row.sb.toString())
        assertEquals(layoutBoundsRightColumn(), row.cursor?.bounds)
    }

    /** T-3：次列起始跨页——续排行平移到新页首列 X（§1 潜在不一致的修复锁，R3 行为改进） */
    @Test
    fun second_column_start_cross_page_translates_to_first_column() {
        val text = forceBreakText(2, COL_W)
        val row = layoutRow(
            tableRow(text, listOf(0 to text.length), "100%"),
            tableIsRtl = false, dual = true, chapterIsRtl = false,
            boundsFactory = { layoutBoundsRightColumn() }, visibleBottomLines = 1.5f)
        assertEquals(2, row.pages.size)
        val p1Line = row.pages[0].textLines.filter { it.isTableCell }.single()
        val p2Line = row.pages[1].textLines.filter { it.isTableCell }.single()
        val minStart1 = sortedBoxes(p1Line).minOf { it.start }
        val minStart2 = sortedBoxes(p2Line).minOf { it.start }
        assertTrue("行 0 应在右列（次列）", minStart1 > GAP_MID)
        assertTrue("行 1 应平移到新页首列（左列）", minStart2 < GAP_MID)
        assertEquals("跨页平移 = 列距", -COL_DIST, minStart2 - minStart1, 1f)
        // 边框：页 1 顶线在右列；页 2 底线在左列（isFirstLogicLine 只随行 0）
        val p1H = row.pages[0].textLines.filter { it.isLine && it.lineStart.first != it.lineEnd.first }
        val p2H = row.pages[1].textLines.filter { it.isLine && it.lineStart.first != it.lineEnd.first }
        assertEquals(1, p1H.size); assertTrue(p1H[0].lineStart.first > GAP_MID)
        assertEquals(1, p2H.size); assertTrue(p2H[0].lineStart.first < GAP_MID)
        assertEquals(layoutBoundsLeftColumn(), row.cursor?.bounds)
        assertEquals("TTS 跨结页续拼完整（末页内容在 sb）", "$text\n",
            row.pages[0].text + row.sb.toString())
    }

    /** T-4：单列回归——新页拆分 X 不平移（dx=0，与现状逐位一致） */
    @Test
    fun single_column_new_page_dx_zero() {
        val text = forceBreakText(2, 906f)   // 单列全宽 906
        val row = layoutRow(
            tableRow(text, listOf(0 to text.length), "100%"),
            tableIsRtl = false, dual = false, visibleBottomLines = 1.5f)
        assertEquals(2, row.pages.size)
        val p1Line = row.pages[0].textLines.filter { it.isTableCell }.single()
        val p2Line = row.pages[1].textLines.filter { it.isTableCell }.single()
        assertEquals("单列新页拆分 X 不平移",
            sortedBoxes(p1Line).minOf { it.start }, sortedBoxes(p2Line).minOf { it.start }, 0.01f)
        assertEquals(layoutBoundsPage(), row.cursor?.bounds)
        val p1H = row.pages[0].textLines.filter { it.isLine && it.lineStart.first != it.lineEnd.first }.single()
        val p2H = row.pages[1].textLines.filter { it.isLine && it.lineStart.first != it.lineEnd.first }.single()
        assertEquals("边框同页宽 X", p1H.lineStart.first, p2H.lineStart.first, 0.01f)
    }

    /** T-5（F-C7 重定义）：双列页底 justify 早退契约——列流页 upLinesPosition 零位移 */
    @Test
    fun column_flow_page_dual_justify_early_return_contract() {
        val text = forceBreakText(3, COL_W)
        val row = layoutRow(
            tableRow(text, listOf(0 to text.length), "100%"),
            tableIsRtl = true, dual = true, chapterIsRtl = true,
            boundsFactory = { layoutBoundsRightColumn() }, visibleBottomLines = 2.5f)
        val page = row.pages.single()
        // 页尾追加正文行（真实页结构，绕开"页尾边框行"守卫——锁定的是双列早退守卫本身）
        page.textLines.add(TextLine().apply {
            lineTop = row.cursor!!.offsetY; lineBase = lineTop + 50f; lineBottom = lineTop + 60f
        })
        val preTop = page.textLines.map { it.lineTop }
        val preHeight = page.height
        // 构造"若不早退即必然位移"的触发条件
        ChapterProvider.visibleBottom = page.textLines.last().lineBottom.toInt() + 100
        ChapterProvider.visibleHeight = page.height.toInt()
        page.upLinesPosition()
        page.textLines.forEachIndexed { i, ln ->
            assertEquals("第 $i 行应零位移（双列早退对列流页成立）", preTop[i], ln.lineTop, 0.0001f)
        }
        assertEquals("页高不变", preHeight, page.height, 0.01f)
    }

    /**
     * nextChunkBounds 决策矩阵直测（JVM 探针红 → F-C1/C10 fallback：仪器直测）。
     * 覆盖：RTL/LTR 首列→次列、LTR/RTL 次列→null、单列→null、防御性 FULL→null。
     */
    @Test
    fun next_chunk_bounds_decision_matrix() {
        ChapterProvider.columnGapActual = 40
        ChapterProvider.columnWidth = 433
        ChapterProvider.paddingHorizontal = 40
        assertEquals(layoutBoundsLeftColumn(),
            TableLayoutProvider.nextChunkBounds(layoutBoundsRightColumn(), true, true))
        assertEquals(layoutBoundsRightColumn(),
            TableLayoutProvider.nextChunkBounds(layoutBoundsLeftColumn(), false, true))
        assertNull(TableLayoutProvider.nextChunkBounds(layoutBoundsRightColumn(), false, true))
        assertNull(TableLayoutProvider.nextChunkBounds(layoutBoundsLeftColumn(), true, true))
        assertNull(TableLayoutProvider.nextChunkBounds(layoutBoundsPage(), false, false))
        assertNull(TableLayoutProvider.nextChunkBounds(layoutBoundsPage(), false, true))
    }

    // ─────────────────────────────────────────────────────────────
    // RC3/A1（方案 2026-09-02-plan-table-select-hit-2d.md §2.5 步骤 3）：
    // td 行 charStartOffset/charEndOffset tr 段内段落级化锁。

    /** A1-1：td 行偏移段落级（cell-local 现状下必红） */
    @Test
    fun td_offset_paragraph_level_unique() {
        val text = "Hello 25"
        val row = layoutRow(tableRow(text, listOf(0 to 5, 6 to 8), "60%;40%"), tableIsRtl = false)
        val lines = row.cellTextLines
        val col0 = lines.first { it.colIndex == 0 }
        val col1 = lines.first { it.colIndex == 1 }

        // 首逻辑行 charStartOffset = tagCell.start（0 / 6）——cell-local 现状两者同为 0（红）
        assertEquals("col0 首行 = td[0,5) 起点", 0, col0.charStartOffset)
        assertEquals("col1 首行 = td[6,8) 起点", 6, col1.charStartOffset)
        // 单行格：charEndOffset = tagCell.end
        assertEquals(5, col0.charEndOffset)
        assertEquals(8, col1.charEndOffset)
        // 行区间长 = 行文本码元长（UTF-16）
        lines.forEach { ln ->
            assertEquals("区间长=行文本长", ln.text.length, ln.charEndOffset - ln.charStartOffset)
        }
        // 同 tr 内 td 区间互斥（resolveVisualPos 唯一性前提）
        assertTrue("td 区间互斥",
            minOf(col0.charEndOffset, col1.charEndOffset) <= maxOf(col0.charStartOffset, col1.charStartOffset))
    }

    /** A1-2：段落级往返不变量——tr 段文本 substring(charStart, charEnd) == line.text */
    @Test
    fun td_offset_locator_roundtrip() {
        val text = "Hello 25"
        val row = layoutRow(tableRow(text, listOf(0 to 5, 6 to 8), "60%;40%"), tableIsRtl = false)
        row.cellTextLines.forEach { ln ->
            assertEquals("tr 段文本往返应逐位相等",
                text.substring(ln.charStartOffset, ln.charEndOffset), ln.text)
        }
    }

    /** A1-3：删补偿后消费端字符级匹配谓词（charStartOffset + textIdx ∈ [tag.start, tag.end)） */
    @Test
    fun td_offset_tag_match_after_removal() {
        val text = "Hello 25"
        val row = layoutRow(
            tableRow(text, listOf(0 to 5, 6 to 8), "60%;40%",
                extraTags = listOf(TextTag(uuid = "hl", name = "highlight", start = 6, end = 8, params = "color=#FF0000"))),
            tableIsRtl = false
        )
        val col0 = row.cellTextLines.first { it.colIndex == 0 }
        val col1 = row.cellTextLines.first { it.colIndex == 1 }
        fun hits(ln: TextLine) = ln.textChars.indices.any { i ->
            val off = ln.charStartOffset + ln.textIndexAt(i)
            off >= 6 && off < 8
        }
        assertTrue("highlight tag[6,8) 应命中 col1", hits(col1))
        assertFalse("highlight tag[6,8) 不得命中 col0", hits(col0))
    }
}
