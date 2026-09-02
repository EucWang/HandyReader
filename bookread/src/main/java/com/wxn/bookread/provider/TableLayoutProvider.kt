package com.wxn.bookread.provider

import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.RunLayout
import com.wxn.base.bean.TextTag
import com.wxn.bookread.data.beans.LineAssemblyState
import com.wxn.bookread.data.beans.LineBlockRecord
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.upTopBottom
import com.wxn.bookread.ext.isWordChar
import com.wxn.bookread.provider.ChapterProvider.dualColumnEnabled
import com.wxn.bookread.provider.ChapterProvider.lineSpacingExtra
import com.wxn.bookread.provider.ChapterProvider.paddingVertical
import com.wxn.bookread.provider.ChapterProvider.visibleBottom
import com.wxn.bookread.textHeight
import kotlin.math.roundToInt

object TableLayoutProvider {

    /**
     * v4（方案 B）：返回类型从 Float 改为 [LayoutCursor]，新增 [bounds] 参数。
     * 表格行作为原子单元切列（不拆分）。
     *
     * [bugfix]：原 `durY + lineHeight > visibleHeight` 改为 `> visibleBottom`——
     * visibleHeight 是高度值，visibleBottom 是 Y 偏移，分页判断应该用后者。
     * 此 bug 在单列下因两者差值（= paddingVertical）较小而偶发未暴露，双列/大边距下会少建一页。
     */
    internal fun layoutTableRow(
        paragraph: ReaderText,
        textPaint: TextPaint,
        marginLeft: Float,
        marginRight: Float,
        paragraphIndex: Int,
        textAlign: CssTextAlign,
        lineHeightParam: Float,
        textPages: ArrayList<TextPage>,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        stringBuilder: StringBuilder,
        offsetY: Float,
        bounds: LayoutBounds = layoutBoundsPage(),   // v4 新增
        paraSpacingZeroed: Boolean = false,
        tableIsRtl :Boolean = false,   // 整表基调（D1：拼接全表文本 first-strong）——镜像列序/单元格方向/对齐映射
        chapterIsRtl: Boolean = false  // 章节方向——切列/分页的首列判定用（B2.3 接通，接通前未使用）
    ): LayoutCursor {
        var durY = offsetY
        var currentBounds = bounds   // v4：局部变量，随列切换更新
        if (paragraph is ReaderText.Text) {
            val tagTable = paragraph.annotations.firstOrNull { tag ->
                tag.name == "table"
            }
            val tagTr = paragraph.annotations.firstOrNull { tag ->
                tag.name == "tr"
            }
            val tagCells = paragraph.annotations.filter { tag ->
                tag.name == "td" || tag.name == "th"
            }
            if (tagCells.isNotEmpty()) {
                var rows = 0    //表格行数
                var cols = 0    //表格列数
                var tablePercents = arrayListOf<Int>()   //每一行所占的百分比
                tagTable?.paramsPairs()?.forEach { param ->
                    if (param.first == "cols") {
                        cols = param.second.toIntOrNull() ?: 0
                    } else if (param.first == "rows") {
                        rows = param.second.toIntOrNull() ?: 0
                    } else if (param.first == "table_percent") {
                        val pers = param.second.split(";")
                        if (pers.isNotEmpty()) {
                            for (per in pers) {
                                if (per.endsWith("%")) {
                                    tablePercents.add(
                                        per.substring(0, per.length - 1).toIntOrNull() ?: 0
                                    )
                                }
                            }
                        }
                    }
                }
                //当前行索引
                val rowIndex = tableRowIndex(tagTr)
//                Logger.d("ChapterProvider::rows=$rows,cols=$cols,rowIndex=$rowIndex")
                if (tagCells.size == tablePercents.size) { //
                    var leftOffsetPercent: Int = 0  //距离左边的宽度的百分比
                    // v4 方案 B：表格行作为原子单元，整段在同一个列内排版（不中途切列）。
                    // 先用「单行高度」做预检：若连一行都放不下当前列，则切列/建新页。
                    // 切列后用 layoutBounds 快照固定本段表格的列几何——单元格坐标和边框线都基于它，
                    // 保证一致；下方行渲染循环只做页面级拆分（不再切列）。
                    val singleLineHeight = textPaint.textHeight * lineSpacingExtra * lineHeightParam
                    if (durY + singleLineHeight > visibleBottom) {
                        if (currentBounds.isFirstColumnOf(chapterIsRtl)) {
                            // 语义 = legacy「已在首列 → 同页切到次列」（原代码是切右列，不是回首列！），
                            // 方向化后次列 = 首列的另一侧：RTL 章节（首列=右列）切到左列
                            currentBounds = if (chapterIsRtl) layoutBoundsLeftColumn() else layoutBoundsRightColumn()
                            durY = paddingVertical.toFloat()
                        } else {
                            val lastPage = textPages.last()
                            lastPage.text = stringBuilder.toString()
                            pageLines.add(lastPage.textLines.size)
                            pageLengths.add(lastPage.text.length)
                            lastPage.height = maxOf(lastPage.height, durY)   // §2.4 页高单调化

                            textPages.add(TextPage())
                            stringBuilder.clear()
                            durY = paddingVertical.toFloat()
                            currentBounds = when {
                                !dualColumnEnabled -> layoutBoundsPage()
                                chapterIsRtl -> layoutBoundsRightColumn()   // 新页回首列（RTL=右列）
                                else -> layoutBoundsLeftColumn()
                            }
                        }
                    }
                    val layoutBounds = currentBounds   // 固定快照，单元格坐标 + 边框线均基于此
                    // 列流当前块快照（初值 = 起始列；行中途溢出切列/新页时随 currentBounds 更新）
                    var chunkBounds = layoutBounds
                    val fullWidth =
                        layoutBounds.width - marginLeft.roundToInt() - marginRight.roundToInt()   // v4：layoutBounds.width
                    var textLineMaps = hashMapOf<Int, ArrayList<TextLine>>()  //遍历完，用来合并TextLine
                    //每个单元格
                    for (index in 0 until tagCells.size) {
                        val tagCell = tagCells[index]
                        val tagPercent: Int = tablePercents[index] //当前单元格所占的宽度的百分比,
//                        Logger.d("ChapterProvider::setTextTable::line[${paragraph.line}],tagCell[$tagCell],index[$index],tagPercent=$tagPercent")
                        val text =
                            if (tagCell.start in 0 until paragraph.line.length && tagCell.end in 0..paragraph.line.length) {
                                paragraph.line.substring(tagCell.start, tagCell.end)
                            } else if (tagCell.start in 0 until paragraph.line.length && tagCell.end > paragraph.line.length) {
                                paragraph.line.substring(tagCell.start)
                            } else {
                                ""
                            }

                        val usableWidth =
                            // 可用宽度；coerceAtLeast(1)：legacy 无下限，percents 总和 >100 的畸形书末列可为负
                            //（StaticLayout 不接受负宽），下限容错——正常书数值不受影响
                            (fullWidth * (tagPercent / 100f) - 2 * TableGeometry.CELL_INNER_PADDING).toInt().coerceAtLeast(1)
                        // 单元格内容区左偏移：LTR 从左累加；RTL 镜像（首列贴右）
                        val leftOffset = TableGeometry.cellLeftOffset(
                            fullWidth.toFloat(),
                            leftOffsetPercent,
                            tagPercent,
                            tableIsRtl
                        ).toInt()

                        // 单元格内容区左缘（canvas 绝对坐标；P-L1：只在此处加一次 startX）
                        val cellStartX = layoutBounds.startX + marginLeft + leftOffset
                        // per-run 排版（方案 2026-09-02-plan-table-cell-per-run-engine.md）：
                        // 格内文本经 SheenBidi 分段后逐 run 建单方向 StaticLayout，跨 run 共享行装配——
                        // 与正文 layoutNormalTextRtl 同构。此后 placeCharsFromLayout 的全部调用方
                        // 均为单方向 layout，其「单方向」前提构造性成立（bidi run 边界 ph 光标位
                        // 污染字符盒的根因就此消灭）。行发射循环（页拆分/TTS/垂直居中/边框）不动。
                        val cellMap = layoutCellRuns(
                            cellText = text,
                            textPaint = textPaint,
                            usableWidth = usableWidth,
                            cellStartX = cellStartX,
                            tableIsRtl = tableIsRtl,
                            paragraphIndex = paragraphIndex,
                            rowIndex = rowIndex,
                            colIndex = index,
                            cellCharStart = tagCell.start,
                            paragraph = paragraph,
                            tagCell = tagCell,
                            paraSpacingZeroed = paraSpacingZeroed
                        )
                        cellMap.forEach { (k, v) -> textLineMaps.getOrPut(k) { arrayListOf() }.addAll(v) }
                        leftOffsetPercent += tagPercent
                    }

                    val lines: List<Int> = textLineMaps.keys.toList().sorted()
                    val rowBoxCount = lines.size
                    val cellBoxCounts = hashMapOf<Int,Int>()
                    textLineMaps.values.forEach { tls ->
                        tls.forEach {
                            cellBoxCounts[it.colIndex] = (cellBoxCounts[it.colIndex] ?: 0) + 1
                        }
                    }
                    for ((index, line) in lines.withIndex()) { //按行处理不同单元格的内容
                        val lineHeight = textPaint.textHeight * lineSpacingExtra * lineHeightParam
                        val textLines = textLineMaps.get(line).orEmpty()
                        // v4 方案 B + [bugfix]：原 visibleHeight → visibleBottom 修复保留。
                        // 列流（2026-09-02-plan-table-column-flow.md R3）：行中途溢出时双列首列 →
                        // 同页次列承接——格断行结构只随列宽、不随列身份变（两列 width 相等）→
                        // 已排版字符盒整体 X 平移 + 边框按当前块快照重建，无需重排版；
                        // 次列再溢出 / 单列 → 新页回首列（门禁决策点①）。
                        if (durY + lineHeight > visibleBottom) {
                            val nextChunk = nextChunkBounds(currentBounds, chapterIsRtl, dualColumnEnabled)
                            if (nextChunk == null) {
                                val lastPage = textPages.last()
                                lastPage.text = stringBuilder.toString()
                                pageLines.add(lastPage.textLines.size)
                                pageLengths.add(lastPage.text.length)
                                lastPage.height = maxOf(lastPage.height, durY)

                                textPages.add(TextPage())
                                stringBuilder.clear()
                                // 新页从首列开始（表格跨页时整段重排到首列/单列）
                                currentBounds = when {
                                    !dualColumnEnabled -> layoutBoundsPage()
                                    chapterIsRtl -> layoutBoundsRightColumn()
                                    else -> layoutBoundsLeftColumn()
                                }
                            } else {
                                // 列流：同页次列承接——不结页、不清 stringBuilder（TTS 跨块续拼）
                                currentBounds = nextChunk
                            }
                            durY = paddingVertical.toFloat()
                            chunkBounds = currentBounds   // 新页路径同样走（单列新页 bounds == 原快照 → dx=0）
                        }

                        // 本行字符盒平移到当前块（每逻辑行只发射一次，无累积；dx=0 时零开销等价现状）
                        val dx = (chunkBounds.startX - layoutBounds.startX).toFloat()
                        if (dx != 0f) textLines.forEach { tls -> tls.textChars.forEach { it.start += dx; it.end += dx } }

                        var words = StringBuilder()
                        textLines.forEach {
                            if (!words.isEmpty()) {
                                words.append("\t")
                            }
                            words.append(it.text)
                        }
                        stringBuilder.append(words)
                        val lastLine = (index == lines.size - 1)
                        if (lastLine) {
                            stringBuilder.append("\n")
                        }
                        val lastPage = textPages.last()
                        val innlineOffset = ((lineHeight - textPaint.textHeight) / 2f).coerceAtLeast(0f)
                        textLines.forEach {
                            val blockOffset = (rowBoxCount - (cellBoxCounts[it.colIndex] ?: 0)) * lineHeight / 2f
                            it.upTopBottom(durY + blockOffset + innlineOffset, textPaint)
                        }
                        lastPage.textLines.addAll(textLines)

                        lastPage.textLines.addAll(
                            TableRenderProvider.buildRowBorders(
                                layoutBounds = chunkBounds,   // 当前块快照（列流后 = 次列；单列 = 原快照）
                                fullWidth = fullWidth.toFloat(),
                                marginLeft = marginLeft,
                                marginRight = marginRight,
                                tablePercents = tablePercents,
                                isFirstLogicLine = (index == 0),
                                isLastTableRow = (rowIndex == rows - 1),
                                isLastLogicLine = (index == lines.size - 1),
                                rowTopY = durY,
                                rowBoxHeight = lineHeight,
                                tableIsRtl = tableIsRtl)
                        )

                        durY += lineHeight
                        // 页高单调化（§2.4 / 审查 r2 F-C6）：同页双纪元下块 2 不得覆盖块 1 峰值
                        lastPage.height = maxOf(lastPage.height, durY)
                    }
                } else {
                    /* 暂时不考虑跨行或者跨列的情况 */
                }
            }
        }
        return LayoutCursor(durY, currentBounds)
    }

    /***
     * paragraph.annotations 全部标签 start/end 换算到单元格内坐标
     * （tag.start - tagCell.start，收敛到 [0, cellTextLen]；td/tr/table 等结构注解边界自然被裁掉）。
     * 供 placeCharsFromLayout 的样式边界切组使用——renderGroup 整词绘制以组首 paint 快照，
     * 词中标签边界不切组会吞样式（高亮/变色半词失效）。
     */
    private fun cellStyleBoundaries(
        paragraph: ReaderText.Text,
        tagCell: TextTag,
        cellTextLen: Int
    ) : Set<Int> {
        val boundaries = mutableSetOf<Int>()
        paragraph.annotations.forEach { tag ->
            boundaries.add((tag.start - tagCell.start).coerceIn(0, cellTextLen))
            boundaries.add((tag.end - tagCell.start).coerceIn(0, cellTextLen))
        }
        return boundaries
    }

    // 首列判定：LTR 章节首列=左列；RTL 章节首列=右列
    private fun LayoutBounds.isFirstColumnOf(chapterIsRtl: Boolean): Boolean =
        if (chapterIsRtl) isRightColumn else isLeftColumn

    /**
     * 溢出时的下一块几何（列流方案 §2.2，internal 纯决策函数，JVM 可测）：
     * 双列且当前在首列 → 次列（同页列流）；否则 null（新页回首列——次列再溢出/单列）。
     * 调用方传 ChapterProvider 单例 dualColumnEnabled；次列工厂读单例列几何。
     */
    internal fun nextChunkBounds(
        cur: LayoutBounds,
        chapterIsRtl: Boolean,
        dualColumnEnabled: Boolean
    ): LayoutBounds? {
        if (!dualColumnEnabled || !cur.isColumn) return null      // 单列 / 防御性 FULL → 新页（现状）
        return if (cur.isFirstColumnOf(chapterIsRtl)) {
            if (chapterIsRtl) layoutBoundsLeftColumn() else layoutBoundsRightColumn()
        } else null
    }

    /**
     *  tr 标签的行索引（index 参数缺失/非法按 0）
     *  ——layoutTableRow 与表格首行段前间距判定共用
     * */
    internal fun tableRowIndex(tagTr: TextTag?): Int =
        tagTr?.paramsPairs()?.firstOrNull {
            it.first == "index"
        }?.second?.toIntOrNull() ?: 0

    // ─────────────────────────────────────────────────────────────
    // 单元格 per-run 排版移植件（方案 2026-09-02-plan-table-cell-per-run-engine.md）
    // 纯几何/纯判定件：无 Android API，bookread/src/test JVM 直测（同 tableRowIndex 先例）

    /**
     * §3.5 对齐映射（门禁裁决 D4）：表格绘制不受用户对齐样式影响——
     * 单元格恒锚表基调起始缘：LTR 表 → 左缘 0；RTL 表 → 右缘 usable−contentW。
     * 超宽行不钳制：按起始缘锚定自然外溢（与现状 ALIGN_NORMAL 同型）。
     */
    internal fun cellAnchorTargetLeft(
        contentWidth: Float,
        usableWidth: Int,
        tableIsRtl: Boolean
    ): Float = if (tableIsRtl) usableWidth - contentWidth else 0f

    /** 行内词截断判定（正文 TextLayoutProvider L230-232 同源）：line0 在词内断开（两侧均非 CJK 字母） */
    internal fun cellLine0MidWord(runText: String, line0End: Int): Boolean =
        line0End < runText.length &&
                runText[line0End - 1].isWordChar() &&
                runText[line0End].isWordChar()

    /** 共享行回退判定（正文 L233-235 同源）：line0 实宽超剩余宽度（±1f 容差）或词内截断 */
    internal fun cellSharedLineShouldFallback(
        sharedLine: Boolean,
        line0Width: Float,
        firstLineWidth: Int,
        midWord: Boolean
    ): Boolean = sharedLine && (line0Width > firstLineWidth + 1f || midWord)

    /**
     * 共享行推回（正文 shiftRunLineToCursor L521-551 数学，cell-local）：
     * 把 [fromIndex, size) 的新增块整体平移，使其「近端」贴 cursor
     * （RTL 近端 = 块右缘 max end / LTR 近端 = 块左缘 min start），块内相对位置与宽度不变。
     * @return 块平移后的 (minStart, maxEnd)
     */
    internal fun cellShiftRunBlock(
        chars: List<TextChar>,
        fromIndex: Int,
        lineIsRtl: Boolean,
        cursor: Float
    ): Pair<Float, Float> {
        if (fromIndex >= chars.size) return Pair(cursor, cursor)
        var blockMin = Float.POSITIVE_INFINITY
        var blockMax = Float.NEGATIVE_INFINITY
        for (i in fromIndex until chars.size) {
            if (chars[i].start < blockMin) blockMin = chars[i].start
            if (chars[i].end > blockMax) blockMax = chars[i].end
        }
        val shift = if (lineIsRtl) cursor - blockMax else cursor - blockMin
        if (shift != 0f) {
            for (i in fromIndex until chars.size) {
                chars[i].start += shift
                chars[i].end += shift
            }
        }
        return Pair(blockMin + shift, blockMax + shift)
    }

    /**
     * 单元格 per-run 排版（方案 2026-09-02-plan-table-cell-per-run-engine.md §3.3；
     * 正文 layoutNormalTextRtl L107-307 裁剪同源，行尾注释标注源出处——阶段二合并对照）。
     *
     * 与正文的 3 处刻意差异（方案 §3.1）：
     *  ① 行基调 = 表级嵌入方向 tableIsRtl（正文 = 段落首强 segDirect.baseRtl，D3）——
     *     格继承表方向是 CSS direction 语义，并锁定 T1/T3 镜像（阿语格+LTR 表）现状；
     *     SheenBidi runs 只用于 run 枚举与 run.isRtl（强字符 run 边界与基级无关）。
     *  ② 无分页/切列/行内图/列表/TTS 分支（有界性：Y 坐标由发射循环 upTopBottom 延后赋值）。
     *  ③ 空白 run 不跳过（正文 L166 continue）：空/空白格也发射 1 个（空）TextLine，
     *     保持 cellBoxCounts 垂直居中记账与现状逐位一致。
     *
     * @return 逻辑行 key（格内从 0 计）→ 该格该行的 TextLine（单格单逻辑行恒 1 个）
     */
    private fun layoutCellRuns(
        cellText: String,
        textPaint: TextPaint,
        usableWidth: Int,
        cellStartX: Float,                 // 内容区左缘 canvas 坐标
        tableIsRtl: Boolean,
        paragraphIndex: Int,
        rowIndex: Int,
        colIndex: Int,
        cellCharStart: Int,                // tagCell.start（rowLineOffset 契约）
        paragraph: ReaderText.Text,
        tagCell: TextTag,
        paraSpacingZeroed: Boolean
    ): HashMap<Int, ArrayList<TextLine>> {
        val textLineMaps = hashMapOf<Int, ArrayList<TextLine>>()
        val boundaries = cellStyleBoundaries(paragraph, tagCell, cellText.length)

        val seg = RTLSegmenter.segment(cellText, declaredRtl = tableIsRtl)
        val runs = if (seg.runs.isEmpty()) {
            // 纯方向/空文本：合成全覆盖单 run（正文 L72-77 同源；空文本 → 1 行 → 空 TextLine，
            // cellBoxCounts 记账与现状一致——§3.1 差异③）
            listOf(RunLayout(seg.baseRtl, 0, cellText.length, if (seg.baseRtl) 1 else 0))
        } else {
            seg.runs.sortedBy { it.offset }        // 逻辑序消费（正文 L95/L1066 同源）
        }

        var lineIsRtl = tableIsRtl                 // ★ D3：表级嵌入方向（非格内首强）
        var cursor = if (lineIsRtl) usableWidth.toFloat() else 0f
        var logicLineIndex = -1                    // textLineMaps 的逻辑行 key（发射循环按 key 升序消费）
        // 粘合段重锚（正文 L102 条件 !baseRtl && runs≥2 的 D3 推论：仅 LTR 表可能出 LTR 基行）
        val assembly = if (!tableIsRtl && seg.runs.size >= 2) LineAssemblyState() else null

        for (run in runs) {
            val runText = cellText.substring(run.offset, run.offset + run.length)
            // （正文 L166 对 blank run continue——单元格不跳，§3.1 差异③）

            val atEdge = if (lineIsRtl) cursor >= usableWidth - 0.5f else cursor <= 0.5f   // 正文 L168-172
            val firstLineWidth = if (atEdge) usableWidth else
                ((if (lineIsRtl) cursor else usableWidth.toFloat() - cursor)
                    .roundToInt()).coerceAtLeast(1)                                            // 正文 L174-181
            var sharedLine = !atEdge && logicLineIndex >= 0        // 正文 L183（格内无段界，行界=首行界）
            val sharedLineIndent = usableWidth - firstLineWidth
            val leftIndentArr = if (lineIsRtl) intArrayOf(0, 0) else intArrayOf(sharedLineIndent, 0)
            val rightIndentArr = if (lineIsRtl) intArrayOf(sharedLineIndent, 0) else intArrayOf(0, 0)
            var layout = StaticLayout.Builder.obtain(runText, 0, runText.length, textPaint, usableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)        // 对齐后置到 anchorCellLine（§3.5 D4）
                .setTextDirection(
                    if (run.isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                .setIncludePad(false)                               // 正文 L212；仅影响 Y 向 padding（§9-R5）
                .setIndents(leftIndentArr, rightIndentArr)
                .build()

            // 共享行回退重建（正文 L229-245 同源：碎片超宽/词内截断 → 整块换行重排）
            if (cellSharedLineShouldFallback(
                    sharedLine,
                    layout.getLineWidth(0),
                    firstLineWidth,
                    cellLine0MidWord(runText, layout.getLineEnd(0)))
            ) {
                sharedLine = false
                layout = StaticLayout.Builder.obtain(runText, 0, runText.length, textPaint, usableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(
                        if (run.isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                    .setIncludePad(false)
                    .setIndents(intArrayOf(0, 0), intArrayOf(0, 0))
                    .build()
            }

            for (lineIndex in 0 until layout.lineCount) {
                val lineShared = sharedLine && lineIndex == 0
                val line: TextLine
                if (!lineShared) {
                    lineIsRtl = tableIsRtl                  // 正文 L251（行基=嵌入方向）
                    cursor = if (lineIsRtl) usableWidth.toFloat() else 0f
                    assembly?.let { finalizeCellPendingLine(it) }   // 行关闭→重锚（正文 L443-444）
                    logicLineIndex++
                    line = TextLine(
                        isTitle = false,
                        paragraphIndex = paragraphIndex,
                        charStartOffset = run.offset + layout.getLineStart(lineIndex),   // cell-local（现状 L169 契约）
                        charEndOffset = run.offset + layout.getLineEnd(lineIndex),
                        rowIndex = rowIndex,
                        colIndex = colIndex,
                        rowLineOffset = cellCharStart,      // 现状 L173 契约
                        isTableCell = true
                    )
                    line.isRtl = lineIsRtl                  // 现状 L177 契约（= tableIsRtl，逐位保持）
                    line.letterSpacingZeroed = paraSpacingZeroed   // 现状 L178 契约
                    textLineMaps.getOrPut(logicLineIndex) { arrayListOf() }.add(line)
                } else {
                    // 共享行 = 前一 run 的末行（本格行映射中该逻辑行的既有 TextLine）
                    line = textLineMaps.getValue(logicLineIndex).last()
                }
                val charsBaseStart = line.textChars.size

                // ★ 保真修正（门禁裁决③）：charIsRtl = run.isRtl（正文 L473 同源）——
                //   现状传 tableIsRtl，混排格行末 patch 方向不正确（本缺陷族的一部分）。
                //   runLength 必传：预扫描 run 级，漏传 → needsRunShaping 恒 false → 连写断裂静默回归
                TextLayoutProvider.placeCharsFromLayout(
                    layout,
                    lineIndex,
                    cellStartX,
                    cellText,
                    boundaries,
                    line,
                    charIsRtl = run.isRtl,
                    offsetBase = run.offset,
                    runLength = run.length
                )
                line.text += runText.substring(
                    layout.getLineStart(lineIndex), layout.getLineEnd(lineIndex))   // 正文 L498-502
                line.charEndOffset = run.offset + layout.getLineEnd(lineIndex)   // 正文 L453

                // 反向 run 共享行推回 packing 位（正文 L478-483/L496）
                val (blockMin, blockMax) = cellShiftRunBlock(
                    line.textChars, charsBaseStart, lineIsRtl, cursor)
                cursor = if (lineIsRtl) blockMin else blockMax

                // 粘合段块记录（正文 L486-494）
                assembly?.let {
                    if (line.textChars.size > charsBaseStart) {
                        it.pendingLine = line
                        it.blocks.add(LineBlockRecord(charsBaseStart, line.textChars.size, run.level))
                    }
                }
            }
        }
        assembly?.let { finalizeCellPendingLine(it) }       // 末行关闭重锚（正文 L317）

        // cell-local 对齐锚定（§3.5 D4：恒锚表基调起始缘；锚定在重锚后 = 正文 L317→L323 同序）
        for ((_, tls) in textLineMaps) {
            tls.forEach { anchorCellLine(it, cellStartX, usableWidth, tableIsRtl) }
        }
        return textLineMaps
    }

    /** 正文 anchorLine L1001-1050 的表格切片（D4：无 textAlign/justify/inkPad/图片/列表圆点） */
    private fun anchorCellLine(
        textLine: TextLine,
        cellStartX: Float,
        usableWidth: Int,
        tableIsRtl: Boolean
    ) {
        val chars = textLine.textChars
        if (chars.isEmpty()) return
        val contentLeft = chars.minOf { it.start }
        val contentRight = chars.maxOf { it.end }
        val targetLeft = cellStartX + cellAnchorTargetLeft(
            contentRight - contentLeft, usableWidth, tableIsRtl)
        val shift = targetLeft - contentLeft
        if (shift != 0f) {
            chars.forEach { it.start += shift; it.end += shift }
        }
    }

    /** 正文 finalizePendingLine L1244-1258 移植（直调 internal TextLayoutProvider.reorderGluedSpans） */
    private fun finalizeCellPendingLine(state: LineAssemblyState) {
        val line = state.pendingLine ?: return
        state.pendingLine = null
        try {
            if (state.blocks.size >= 2 && !line.textChars.any { it.isImage }) {
                if (TextLayoutProvider.reorderGluedSpans(line.textChars, state.blocks)) {
                    line.spanReordered = true
                }
            }
        } finally {
            state.blocks.clear()
        }
    }
}