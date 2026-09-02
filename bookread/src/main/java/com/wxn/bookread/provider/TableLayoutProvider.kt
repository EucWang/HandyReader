package com.wxn.bookread.provider

import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.upTopBottom
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
                            lastPage.height = durY

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

                        // 对齐/方向进 layout（P-L4 修复）：由 StaticLayout 自身处理（alignOf 映射 + 表格基调），
                        // 废弃 legacy 三路 addChars* 手工偏移数学（P-L1 双重叠加 startX 同步根除）
                        val layout = StaticLayout.Builder.obtain(
                            text,
                            0,
                            text.length,
                            textPaint,
                            usableWidth
                        ).setAlignment(TableRenderProvider.alignOf(textAlign, tableIsRtl))
                            .setTextDirection(
                                if (tableIsRtl) TextDirectionHeuristics.RTL
                                else TextDirectionHeuristics.LTR
                            )
                            .setIncludePad(true)
                            .build()

                        // 单元格内容区左缘（canvas 绝对坐标；P-L1：只在此处加一次 startX）
                        val cellStartX = layoutBounds.startX + marginLeft + leftOffset
                        //每个单元格的字符串，生成多行的情况，每一行都是一个TextLine
                        for (lineIndex in 0 until layout.lineCount) {
                            val offsetStart = layout.getLineStart(lineIndex)
                            val offsetEnd = layout.getLineEnd(lineIndex)
                            val textLine = TextLine(
                                isTitle = false,
                                paragraphIndex = paragraphIndex,
                                charStartOffset = offsetStart,
                                charEndOffset = offsetEnd,
                                rowIndex = rowIndex,
                                colIndex = index,
                                rowLineOffset = tagCell.start,
                                isTableCell = true
                            )
                            textLine.text = text.substring(offsetStart, offsetEnd)
                            textLine.isRtl = tableIsRtl
                            textLine.letterSpacingZeroed = paraSpacingZeroed

                            // 统一摆放（P-L2/P-L3 修复）：与正文共用 placeCharsFromLayout——逐字 gph 坐标与断行
                            // 同源（字符盒=字形位置），renderGroup/needsRunShaping 分组整词绘制（连写正确）；
                            // ★ runLength 必传：漏传时预扫描区间为空 → needsRunShaping 恒 false → 连写断裂静默回归
                            TextLayoutProvider.placeCharsFromLayout(
                                layout,
                                lineIndex,
                                cellStartX,
                                text,
                                cellStyleBoundaries(paragraph, tagCell, text.length),
                                textLine,
                                charIsRtl = tableIsRtl,
                                offsetBase = 0,
                                runLength = text.length
                            )

                            if (textLineMaps.get(lineIndex) == null) {
                                textLineMaps[lineIndex] = arrayListOf<TextLine>()
                            }
                            textLineMaps.get(lineIndex)?.add(textLine)
                        }
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
                        // v4 方案 B + [bugfix]：表格只做页面级拆分（不切列——单元格已基于 layoutBounds 排好）。
                        // 原 visibleHeight → visibleBottom 的 bugfix 保留。
                        if (durY + lineHeight > visibleBottom) {
                            val lastPage = textPages.last()
                            lastPage.text = stringBuilder.toString()
                            pageLines.add(lastPage.textLines.size)
                            pageLengths.add(lastPage.text.length)
                            lastPage.height = durY

                            textPages.add(TextPage())
                            stringBuilder.clear()
                            durY = paddingVertical.toFloat()
                            // 新页从左列开始（表格跨页时整段重排到左列/单列）
                            currentBounds = when {
                                !dualColumnEnabled -> layoutBoundsPage()
                                chapterIsRtl -> layoutBoundsRightColumn()
                                else -> layoutBoundsLeftColumn()
                            }
                        }

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
                                layoutBounds = layoutBounds,
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
                        lastPage.height = durY
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
     *  tr 标签的行索引（index 参数缺失/非法按 0）
     *  ——layoutTableRow 与表格首行段前间距判定共用
     * */
    internal fun tableRowIndex(tagTr: TextTag?): Int =
        tagTr?.paramsPairs()?.firstOrNull {
            it.first == "index"
        }?.second?.toIntOrNull() ?: 0
}