package com.wxn.bookread.provider

import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.InlineStyle
import com.wxn.base.bean.LayoutItem
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.RunLayout
import com.wxn.base.bean.SegmentResult
import com.wxn.base.bean.TextTag
import com.wxn.bookread.data.model.LineDot
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.provider.ChapterProvider.dualColumnEnabled
import com.wxn.bookread.provider.ChapterProvider.lineSpacingExtra
import com.wxn.bookread.provider.ChapterProvider.paddingVertical
import com.wxn.bookread.provider.ChapterProvider.visibleBottom
import com.wxn.bookread.textHeight
import java.util.Arrays
import kotlin.math.roundToInt

object TextLayoutProvider {

    /** 墨迹安全内边距系数：advance 外墨迹溢出（阿拉伯连写/斜体）占字号比例，可调 */
    const val INK_PAD_RATIO = 0.05f

    inline fun inkPad(textSize: Float) =
        maxOf(2f, textSize * INK_PAD_RATIO)   // INK_PAD_RATIO = 0.05f（顶层 const，可调）

    internal fun layoutNormalTextRtl(
        text: CharSequence,                    // buildSpannedText 产物（含 RelativeSizeSpan）
        inlineFontSizes: List<InlineStyle>?,   // 几何轨 scale 反查数据源
        segDirect: SegmentResult,                   // 段落方向（baseRtl + runs）
        textPaint: TextPaint,                 // setTypeText 已构建（含 typeface/italic/textSize）
        marginLeft: Float, marginRight: Float,
        firstLineIndent: Float,
        isTitle: Boolean, isListRow: Boolean, listLevel: Int, listOrder: Int = 0,
        paragraphIndex: Int,
        textAlign: CssTextAlign,
        lineHeightParam: Float,
        paragraph: ReaderText,
        textPages: ArrayList<TextPage>,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        stringBuilder: StringBuilder,
        offsetY: Float,
        bounds: LayoutBounds = layoutBoundsPage(),
        chapterIsRtl: Boolean,                 // 双列切列方向
        hasInlineImage: Boolean = false
    ): LayoutCursor {

        var durY = offsetY  //行绘制位置
        var currentBounds = bounds
        // ★ 视觉行游标：标记当前行的"拼接前沿"。
        //   baseRtl → 初始在右界(bounds.endX)，每放一个 run 向左推进；
        //   baseLtr → 初始在左界(bounds.startX)，每放一个 run 向右推进。
        //   游标 ≠ 列边缘 → 当前行有剩余宽度，下一个 run 的首行可共享此行。
        var isFirstLineOfParagraph = true
        val paint = textPaint

        val runs = if (segDirect.runs.isEmpty()) {
            listOf(RunLayout(segDirect.baseRtl, 0, text.length))
        } else {
            segDirect.runs
        }
        // ★ SheenBidi 覆盖守卫（仅混合段需要——纯方向已合成全覆盖 run）：
        //   B 类字符（\n \r \u2028 \u2029）截断 paraLen < text.length 时，runs 不覆盖全段。
        //   buildSpannedText 理论不产 B 类字符；防御性日志 + 截断处理（只排覆盖部分），不崩。
        if (runs.first().offset != 0 || runs.last().offset + runs.last().length < text.length) {
            android.util.Log.w("layoutMixedRun", "SheenBidi 覆盖不全，截断处理")
        }
        val imgTags = if (hasInlineImage && paragraph is ReaderText.Text) {
            paragraph.annotations.filter { it.name == "img" || it.name == "image" }
                .sortedBy { it.start }
        } else emptyList()
        //得到被bidi算法，和 行内图片 切分得到的分段集合
        // ★ SheenBidi runs 是视觉序（左→右；B1/B2 仪器测试实测：'نص 123' 的 runs[0] 是数字 run）。
        //   堆叠按「阅读序」从起始边推进：RTL 基调 = 逻辑序 = 视觉序的【run 级逆序】——逆序后首
        //   run 恰为逻辑首 run，从右缘起排，后续 run 依次向左 → 视觉位置与 Calibre/标准 bidi 一致；
        //   LTR 基调视觉序即逻辑序，保持原序。
        //   （修复前：RTL 基调按视觉序从右缘消费 → 整行块顺序镜像，LD-B 混排 li 与 Calibre 对照可复现）
        val layoutItems = buildLayoutItems(runs, imgTags, segDirect.baseRtl)

        // 首个 Run 定方向
        var lineIsRtl = if (layoutItems.any { it is LayoutItem.Run }) {
            segDirect.baseRtl
        } else {
            chapterIsRtl
        }

        var cursor = if (lineIsRtl) {
            currentBounds.endX.toFloat() - marginRight
        } else {
            currentBounds.startX.toFloat() + marginLeft
        }

        //有效的对齐方式
        val effAlign = when {
            segDirect.baseRtl && textAlign == CssTextAlign.CssTextAlignLeft -> CssTextAlign.CssTextAlignRight
            textAlign == CssTextAlign.CssTextAlignUndefined ->
                if (segDirect.baseRtl) CssTextAlign.CssTextAlignRight else CssTextAlign.CssTextAlignLeft

            else -> textAlign
        }
        //段落的行缓存
        val paragraphLines = arrayListOf<LineRecord>()

        val fullWidth = currentBounds.width - marginLeft.roundToInt() - marginRight.roundToInt()
        for (layoutItem in layoutItems) {
            when (layoutItem) {
                is LayoutItem.Image -> {
                    val layoutResult = layoutInnerImage(
                        layoutItem.tag,
                        lineIsRtl,
                        cursor,
                        currentBounds,
                        marginLeft,
                        marginRight,
                        durY,
                        isFirstLineOfParagraph,

                        textPages,
                        pageLines,
                        pageLengths,
                        stringBuilder,

                        paragraphLines,
                        paragraphIndex,

                        isTitle,
                        paint,
                        lineHeightParam,
                        chapterIsRtl
                    )
                    cursor = layoutResult.cursor
                    currentBounds = layoutResult.bounds      // 可能因分栏/分页而变
                    durY = layoutResult.durY
                    isFirstLineOfParagraph = false
                }

                is LayoutItem.Run -> {
                    val run = layoutItem.run
                    val runText = text.subSequence(run.offset, run.offset + run.length)
                    if (runText.isBlank()) continue

                    val atEdge = if (lineIsRtl) {
                        cursor >= currentBounds.endX - marginRight - 0.5f
                    } else {
                        cursor <= currentBounds.startX  + marginLeft + 0.5f
                    }

                    val firstLineWidth =
                        if (atEdge) fullWidth
                        else {
                            (if (lineIsRtl)
                                cursor - (currentBounds.startX + marginLeft)
                            else (currentBounds.endX - marginRight) - cursor
                                    ).roundToInt().coerceAtLeast(1)
                        }
                    // 防御：段落首行永不与前一段落共享（正常路径由 atEdge 不变量保证，恒惰性）
                    var sharedLine = !atEdge && paragraphLines.isNotEmpty()
                    val sharedLineIndent = fullWidth - firstLineWidth

                    val paraFirstIndent = if (isFirstLineOfParagraph  && firstLineIndent > 0f) {
                        firstLineIndent.toInt()
                    } else {
                        0
                    }
                    val leftIndentArr = if (lineIsRtl) {
                        intArrayOf(0, 0)
                    } else {
                        intArrayOf(sharedLineIndent  + paraFirstIndent, 0)
                    }
                    val rightIndentArr = if (lineIsRtl) {
                        intArrayOf(sharedLineIndent + paraFirstIndent, 0)
                    } else {
                        intArrayOf(0, 0)
                    }
                    var layout = StaticLayout.Builder.obtain(
                        runText,
                        0, runText.length, paint, fullWidth
                    )
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setTextDirection(
                            if (run.isRtl) {
                                TextDirectionHeuristics.RTL
                            } else {
                                TextDirectionHeuristics.LTR
                            }
                        ).setIncludePad(false)
                        .setIndents(leftIndentArr, rightIndentArr)
                        .build()

                    //增加处理，当共享行剩余宽度容纳不下一个完整单词或者一个完整阿拉伯语连词时，
                    //会发生强制将词分开显示的情况
                    //   缩进技巧把 line0 压进「剩余宽度」后有两种病理（实测）：
                    //   a) 簇级碎片超宽：词被按簇拆开塞 box，碎片仍可超 box 数 px（P1 +4 / P2 +10），
                    //      1px box 级联时整个字素（30~60px）钉在已越界的 cursor 上（dump 行尾 'E'/'UR'）；
                    //   b) 词内截断【不伴随超宽】（P3：碎片 65 ≤ box 92）——阿拉伯连写被拆断跨行，
                    //      即使不溢出也是排版缺陷。
                    //   标准行为（Calibre/浏览器）：放不下就整块换行、永不词内截断。故触发条件取两者之或：
                    //   line0 实宽超剩余宽度，或 line0 在词内截断（两侧均为非 CJK 字母）。
                    //   回退 = 取消共享缩进重建 layout，line0 以整行宽重排（lineShared=false 走新行流程）；
                    //   同时化解「剩余宽度为负 → box 被 coerce 成 1px」的级联。
                    //   CJK 例外：CJK 字符间是合法断点，不视为词内截断（LD-C 中性+中文场景）。
                    //   词宽于整列（AR-I 超长 URL）时重建后仍会在整行宽下按既有策略处理，无循环。
                    val line0End = layout.getLineEnd(0)
                    val line0MidWord = line0End < runText.length &&
                            isWordChar(runText[line0End - 1]) &&
                            isWordChar(runText[line0End])
                    if (sharedLine && layout.lineCount > 0 &&
                        (layout.getLineWidth(0) > firstLineWidth + 1f ||
                                line0MidWord)) {
                        sharedLine = false //不共享行了，重新排版，重启一行
                        layout = StaticLayout.Builder.obtain(runText, 0,
                            runText.length, paint, fullWidth)
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setTextDirection( if (run.isRtl) TextDirectionHeuristics.RTL
                                else TextDirectionHeuristics.LTR)
                            .setIncludePad(false)
                            .setIndents(intArrayOf(0, 0), intArrayOf(0, 0))
                            .build()
                    }

                    for (lineIndex in 0 until layout.lineCount) {
                        val lineShared = sharedLine && lineIndex == 0 //行首可共享前一run末行
                        if (!lineShared) {
                            // ★ 新建行方向 = 段落基调（UAX#9：行的基方向恒为段落嵌入方向）。
                            lineIsRtl = segDirect.baseRtl
                            cursor =
                                if (lineIsRtl) currentBounds.endX.toFloat() - marginRight
                                else currentBounds.startX.toFloat() + marginLeft
                        }

                        val lineStart = layout.getLineStart(lineIndex)
                        val lineEnd = layout.getLineEnd(lineIndex)
                        val paragraphCharStartOffset = run.offset + lineStart
                        val paragraphCharEndOffset = run.offset + lineEnd
                        val boundaries = computePaintBoundaryOffsets(
                            paragraphCharStartOffset,
                            paragraphCharEndOffset,
                            inlineFontSizes,
                            paragraph,
                            isTitle
                        )

                        val (targetBounds, targetY, targetCursor) = processMixedLine(
                            layout, lineIndex, run,
                            lineShared,
                            lineIsRtl,
                            isFirstLine = (lineIndex == 0 && isFirstLineOfParagraph),
                            firstLineIndent,
                            boundaries,

                            text,
                            paragraphIndex,
                            isTitle,
                            isListRow,
                            listLevel,
                            listOrder,

                            textAlign,
                            lineHeightParam,
                            textPages,
                            pageLines,
                            pageLengths,
                            stringBuilder,
                            durY,
                            currentBounds,
                            marginLeft,
                            marginRight,

                            chapterIsRtl,
                            cursor,
                            paragraphLines
                        )
                        currentBounds = targetBounds
                        durY = targetY
                        cursor = targetCursor
                    }
                    isFirstLineOfParagraph = false
                }
            }
        }

        for ((i, rec) in paragraphLines.withIndex()) {
            postProcessRtlLine(
                textLine = rec.line,
                bounds = rec.bounds,
                textAlign = effAlign,
                paragraphIsRtl = segDirect.baseRtl,
                lineIsRtl = rec.line.isRtl,
                firstLineIndent = firstLineIndent,
                isFirstLine = rec.isFirstLine,
                isLastLine = (i == paragraphLines.lastIndex),
                textSize = textPaint.textSize,
                marginLeft,
                marginRight
            )
        }

        return LayoutCursor(durY, currentBounds)
    }

    private fun processMixedLine(
        layout: StaticLayout,     //当前run分段的测量的layout
        lineIndex: Int,          //当前run分段的行序列
        run: RunLayout,         //当前分段的Run Layout
        sharedLine: Boolean,   //是否是续行的第一行
        lineIsRtl: Boolean,
        isFirstLine: Boolean,  //段落第一行
        firstLineIndent: Float,

        boundaries: Set<Int>,

        text: CharSequence,
        paragraphIndex: Int,
        isTitle: Boolean,
        isListRow: Boolean,
        listLevel: Int,
        listOrder: Int,
        textAlign: CssTextAlign,
        lineHeightParam: Float,
        textPages: ArrayList<TextPage>,
        pageLines: ArrayList<Int>,
        pageLengths: ArrayList<Int>,
        stringBuilder: StringBuilder,
        durY: Float,
        currentBounds: LayoutBounds = layoutBoundsPage(),
        marginLeft: Float,
        marginRight: Float,

        chapterIsRtl: Boolean,                 // 双列切列方向
        cursor: Float,
        paragraphLines: ArrayList<LineRecord>,
    ): Triple<LayoutBounds, Float, Float> {

        val lineStart = layout.getLineStart(lineIndex)
        val lineEnd = layout.getLineEnd(lineIndex)
        val lineAscent = layout.getLineAscent(lineIndex).toFloat()
        val lineDescent = layout.getLineDescent(lineIndex).toFloat()
        val actualLineHeight = (lineDescent - lineAscent) * lineSpacingExtra * lineHeightParam
        val actualDescent = lineDescent * lineSpacingExtra * lineHeightParam

        val paragraphCharStartOffset = run.offset + lineStart
        val paragraphCharEndOffset = run.offset + lineEnd

        //需要输出的数据
        var targetBounds: LayoutBounds = currentBounds
        var targetY = durY
        var targetCursor = if (isFirstLine && firstLineIndent > 0f) {
            if (lineIsRtl) cursor - firstLineIndent else cursor + firstLineIndent
        } else {
            cursor
        }

        //换行->  新行 -> 新列/新页
        if (!sharedLine && durY + actualLineHeight > visibleBottom) {
            //非双列模式下为true； //双列模式+章节RTL + 当前为右列 -> true
            // 双列模式 + 章节LTR + 当前左列 -> true
            val isStartColumn = when {
                !dualColumnEnabled -> targetBounds.role == ColumnRole.FULL
                chapterIsRtl -> targetBounds.isRightColumn
                else -> targetBounds.isLeftColumn
            }

            //双列模式且还有下列的情况下， 下一列是左列还是右列
            if (isStartColumn && dualColumnEnabled) {
                targetBounds =
                    if (chapterIsRtl) layoutBoundsLeftColumn() else layoutBoundsRightColumn()
                targetY = paddingVertical.toFloat()
            } else { // 需要创建新页的情况下
                val lastPage = textPages.last()
                lastPage.text = stringBuilder.toString()
                pageLines.add(lastPage.textLines.size)
                pageLengths.add(lastPage.text.length)
                lastPage.height = targetY
                textPages.add(TextPage())
                stringBuilder.clear()
                targetBounds =
                    if (!dualColumnEnabled) layoutBoundsPage()
                    else {
                        if (chapterIsRtl) layoutBoundsRightColumn()
                        else layoutBoundsLeftColumn()
                    }
                targetY = paddingVertical.toFloat()
            }

            // ★ 段落首行换页/换列（B3）：重置游标同样内缩首行缩进（AC6）
            val firstIndentOnReset = if (isFirstLine) firstLineIndent else 0f
            targetCursor = if (lineIsRtl) {
                targetBounds.endX.toFloat() - marginRight - firstIndentOnReset
            } else {
                targetBounds.startX.toFloat() + marginLeft  + firstIndentOnReset
            }
        }

        // ── 获取/创建 TextLine ──
        val textLine = if (sharedLine) {
            val lastLine = textPages.last().textLines.last()  // append 到共享行
            // ★ 图片创建的共享行修正 charStartOffset：
            //   消费端 offset = rawIndex + charStartOffset（forEachIndexed 的 index 含图片下标位、
            //   isImage 在循环体内跳过渲染但不重新编号）。图片占 textChars[0..n-1] 下标位，
            //   把后续文本顶偏 n 位，故 charStartOffset 须扣 n（= 此时 textChars 里的图片数）。
            //   条件用 all { it.isImage }：覆盖单图/多图；Run→Run 共享行（existing 含文本）不触发。
            if (lastLine.textChars.isNotEmpty() && lastLine.textChars.all { it.isImage }) {
                lastLine.charStartOffset = paragraphCharStartOffset - lastLine.textChars.size
            }
            lastLine
        } else {
            TextLine(
                isTitle = isTitle,
                paragraphIndex = paragraphIndex,
                charStartOffset = paragraphCharStartOffset,
                charEndOffset = paragraphCharEndOffset,
                isRtl = lineIsRtl
            )
        }
        textLine.charEndOffset = paragraphCharEndOffset

        // 列表符号：段落首行且为列表行
        if (isFirstLine && isListRow && listLevel > 0) {
            textLine.lineDot = LineDot(true, listLevel, order = listOrder)
        }

        val charsBaseStart = textLine.textChars.size

        // ── 逐字定位（Step 5 详述）──
        // ★ 定位公式 = startX +  gph。
        //   同向 run / 续行：startX + gph（indent 已将 gph 偏移到 packing 位置）。
        //   反向 run 共享行：将远端边缘的文本推回 packing 位置。
        placeCharsFromLayout(
            layout,
            lineIndex,
            run,
            targetBounds.startX.toFloat(),
            text,
            boundaries,
            textLine
        )

        val (blockMin, blockMax) = shiftRunLineToCursor(
            textLine,
            charsBaseStart,
            lineIsRtl,
            targetCursor
        )

        targetCursor = if (lineIsRtl) blockMin else blockMax

        stringBuilder.append(text.substring(paragraphCharStartOffset, paragraphCharEndOffset))
        // ★ RTL 引擎此前从未给 TextLine.text 赋值（恒 ""）——选区 getSelectText 按
        //   textChars 下标 substring(line.text) 直接越界崩溃（AR-A/LD-B 实机复现）。
        //   累积顺序与 textChars/stringBuilder 一致（run 处理序），sC/eC 下标空间对齐。
        textLine.text += text.substring(paragraphCharStartOffset, paragraphCharEndOffset)

        if (!sharedLine) {
            textPages.last().textLines.add(textLine)
            textLine.upTopBottom(targetY, actualLineHeight, actualDescent)
            targetY += actualLineHeight

            paragraphLines.add(LineRecord(textLine, targetBounds, isFirstLine))
        }

        return Triple(targetBounds, targetY, targetCursor)
    }

    /**
     *  把 [charsBaseStart, size) 区间的字符块整体平移，使其「近端」贴到 cursor。
     *  - lineIsRtl：近端 = 块的右端(max end)，贴 cursor 后 cursor 向左(减)
     *  - LTR    ：近端 = 块的左端(min start)，贴 cursor 后 cursor 向右(加)
     * 块内字符相对位置不变（start/end 同加 shift，宽度不变，连写形态/bidi 视觉序不受影响）。
     */
    private fun shiftRunLineToCursor(
        textLine: TextLine,
        charsBaseStart: Int,
        lineIsRtl: Boolean,
        cursor: Float
    ): Pair<Float, Float> {
        val chars = textLine.textChars
        if (charsBaseStart >= chars.size) {
            return Pair(cursor, cursor)
        }
        var blockMin = Float.POSITIVE_INFINITY
        var blockMax = Float.NEGATIVE_INFINITY
        //得到新增加到shareLine这一行中的这一block区域的全部字符的最左最右边
        for (i in charsBaseStart until chars.size) {
            if (chars[i].start < blockMin) blockMin = chars[i].start
            if (chars[i].end > blockMax) blockMax = chars[i].end
        }

        val shift = if (lineIsRtl) {
            cursor - blockMax
        } else {
            cursor - blockMin
        }
        if (shift != 0f) {
            for (i in charsBaseStart until chars.size) {
                chars[i].start += shift
                chars[i].end += shift
            }
        }
        return Pair(blockMin + shift, blockMax + shift)
    }

    /**
     * 逐字绝对坐标定位：把 run 子 layout 某一行的 getPrimaryHorizontal 结果转为 TextLine 字符坐标。
     *
     * 定位公式：absX = startX + lineShift + gph
     *   - startX = 列左边缘 canvas 坐标
     *   - lineShift = packingShift（反向 run 共享行）或 0（同向/续行/首 run）
     *   - gph = layout.getPrimaryHorizontal(offset)，局部坐标 ∈ [0, layout.width]
     *
     * 行末跨越 patch（per-run 架构唯一 patch）：
     *   多行 layout 的 gph(lineEnd) 跨到下一行起点，不可信。
     *   用 localStart ± chWidth 反推，方向感知（RTL→减 / LTR→加）。
     *   末行不需要 patch（gph(textEnd) 不跨越）。
     *
     * 交叉验证：历史 handleRtlLine 有 3 分支 patch（行末跨越 / 行首LTR / bidi衔接），
     *   per-run 纯方向 layout 消除了后 2 个（无方向混合 → 无行首跨向字 / 无bidi接缝）。
     *   详见 docs/plans/2026-08-12-plan-placechars-from-layout.md §4。
     */
    private fun placeCharsFromLayout(
        layout: StaticLayout,              // 当前 run 子 layout（单方向 + 方向对称 setIndents）
        lineIndex: Int,                    // 当前 run layout 的行号
        run: RunLayout,                    // 当前 run（isRtl + offset + length）
        startX: Float,                     // 列左边缘 canvas 坐标（= targetBounds.startX.toFloat()）
        text: CharSequence,                // 段落全文（buildSpannedText 产物，用于提取字符）
        paintBoundaryOffsets: Set<Int>,    // paint 边界 offset 集合（段落坐标，run 级预计算一次）
        textLine: TextLine                 // 目标行（共享行时已含前一 run 的 chars）
    ) {
        val lineStart = layout.getLineStart(lineIndex)
        val lineEnd = layout.getLineEnd(lineIndex)
        val isLastLayoutLine = lineIndex == layout.lineCount - 1
        val paint = layout.paint

        // renderGroup 初始化：跨 run 边界必切 group
        var renderGroup = if (textLine.textChars.isNotEmpty()) {
            textLine.textChars.last().renderGroup + 1
        } else {
            1
        }

        var offset = lineStart
        while (offset < lineEnd) {
            // 获取当前字符的 Unicode 码点
            val codePoint = Character.codePointAt(text, run.offset + offset)
            //计算其对应的 char 数量
            val charCount = Character.charCount(codePoint)
            //计算下一个字符的偏移量。
            val nextOffset = offset + charCount
            // 当前字符是否为该行的最后一个字符
            val isLineEnd = nextOffset >= lineEnd

            // 提取当前字符的字符串表示（可能包含两个 char，如 emoji）
            val ch = text.subSequence(run.offset + offset, run.offset + nextOffset).toString()
            // 利用画笔测量该字符的绘制宽度（像素）。
            val chWidth = paint.measureText(ch)

            // 获取该字符起始位置相对于行首的水平坐标（由 StaticLayout 计算）。
            val localStart = layout.getPrimaryHorizontal(offset)

            // 如果是行末 且 不是最后一行（即换行处），
            //      用 localStart ± chWidth 推算结束位置（因为 StaticLayout 对行末字符的
            //      getPrimaryHorizontal(nextOffset) 可能返回下一行首位置，不适合）。
            // 否则直接使用 getPrimaryHorizontal(nextOffset) 获取下一个字符的起始位置
            //      作为当前字符的结束位置（对于 RTL 文本，localEnd 可能小于 localStart）
            val localEnd = if (isLineEnd && !isLastLayoutLine) {
                //行尾字符，但是不是run段的最后一个字符
                if (run.isRtl) {
                    localStart - chWidth
                } else {
                    localStart + chWidth
                }
            } else {
                layout.getPrimaryHorizontal(nextOffset)
            }

            //计算字符在屏幕上的绝对左边界和右边界
            // （取 localStart 和 localEnd 的最小/最大值，确保左小右大）。
            val absLeft = startX + minOf(localStart, localEnd)
            val absRight = startX + maxOf(localStart, localEnd)

            textLine.addTextChar(ch, absLeft, absRight, renderGroup)

            val isWhiespace = ch.firstOrNull()?.isWhitespace() == true
            val nextParaOffset = run.offset + nextOffset
            if (isWhiespace || nextParaOffset in paintBoundaryOffsets) {
                renderGroup++
            }

            offset = nextOffset
        }
    }

    /***
     * 间接驱动 renderGroup 递增——paint 边界处（字号/颜色/标签切换）
     * 切分 renderGroup，保证同 group 内字符同 paint 渲染。
     */
    private fun computePaintBoundaryOffsets(
        offsetStart: Int,
        offsetEnd: Int,
        inlineFontSizes: List<InlineStyle>?,
        paragraph: ReaderText?,
        isTitle: Boolean
    ): Set<Int> {
        if (isTitle) {
            return emptySet()
        }

        val boundaries = mutableSetOf<Int>()

        val range = offsetStart until offsetEnd

        if (!inlineFontSizes.isNullOrEmpty()) {
            for (style in inlineFontSizes) {
                if (style.start in range) {
                    boundaries.add(style.start)
                }
                if (style.end in range) {
                    boundaries.add(style.end)
                }
            }
        }

        if (paragraph != null && paragraph is ReaderText.Text) {
            for (tag in paragraph.annotations) {
                if (tag.start in range) {
                    boundaries.add(tag.start)
                }
                if (tag.end in range) {
                    boundaries.add(tag.end)
                }
            }
        }
        return boundaries
    }


    /***
     * 对添加的行进行调整，对齐
     */
    private fun postProcessRtlLine(
        textLine: TextLine,
        bounds: LayoutBounds,
        textAlign: CssTextAlign,
        paragraphIsRtl: Boolean,
        lineIsRtl: Boolean,
        firstLineIndent: Float,    // ④ 首行缩进
        isFirstLine: Boolean,      // ①②④ 判定（processMixedLine 已有 :146 传入的 isFirstLine 形参）
        isLastLine: Boolean,       // ① justify 末行不齐（当前 processMixedLine 还没算 isLastLine，需补）
        textSize: Float,            // ① maxGapWidth 上限 = textSize*0.5（防短行被极端拉伸）
        marginLeft: Float,
        marginRight: Float,
    ) {
        val chars = textLine.textChars
        if (chars.isEmpty()) {
            return
        }

        // Justify 首/末行退化为起始边对齐（与 LTR setNormalText:2184-2217 一致：justify 边缘行走 Left）
        val lineEffAlign = when {
            textAlign == CssTextAlign.CssTextAlignJustify && (isFirstLine || isLastLine) ->
                if (paragraphIsRtl) CssTextAlign.CssTextAlignRight else CssTextAlign.CssTextAlignLeft

            else -> textAlign
        }

        val inkSize = inkPad(textSize)
        var rawStart = bounds.startX.toFloat() + marginLeft
        var rawEnd = bounds.endX.toFloat() - marginRight

        // 列表圆点锚点：钉在内容盒阅读起始侧（层级槽位），不随 text-align/text-indent 漂移（浏览器 outside marker 语义）
        if (textLine.lineDot?.enable == true && (textLine.lineDot?.level?:0) > 0) {
            textLine.lineDot?.markerRtl = paragraphIsRtl
            textLine.lineDot?.anchorX =
                if (paragraphIsRtl) rawEnd - inkSize else rawStart + inkSize
        }

        //首行，有缩进时， 重新校对 开始/结束位置
        if (isFirstLine && firstLineIndent > 0f) {
            val indentApplies =  when (lineEffAlign) {
                CssTextAlign.CssTextAlignRight -> paragraphIsRtl
                CssTextAlign.CssTextAlignLeft -> !paragraphIsRtl
                else -> false
            }
            if (indentApplies) {
                if (paragraphIsRtl) {
                    rawEnd -= firstLineIndent
                } else {
                    rawStart += firstLineIndent
                }
            }
        }

        val rawWidth = rawEnd - rawStart

        val effStart = rawStart + inkSize
        val effEnd = rawEnd - inkSize
        val effWidth = rawWidth - 2 * inkSize

        // ── Job 1: justify ──
        if (lineEffAlign == CssTextAlign.CssTextAlignJustify) {
            justifyLine(chars, effStart, effEnd, effWidth, textSize, lineIsRtl)
        }

        // ── Job 2: exceedRtl（超宽压缩）── 貌似没有什么效果
//        exceedZipWidth(chars, boundsStartX, boundsEndX, boundsWidth, baseRtl)

        // ── Job 3: 锚点定位 + 溢出钳制 ， 左/右/居中共用 ──
        anchorLine(chars, lineEffAlign, lineIsRtl, rawStart, rawEnd, rawWidth, inkSize)
    }


    /***
     * 两端对齐， 拉开词间距
     *  * 两端对齐：contentWidth < boundsWidth 时，把 (boundsWidth - contentWidth) 均摊到词间 gap。
     *  * 按 renderGroup 分词；词内字符只平移不改宽度（保 HarfBuzz 连写形态）。
     *  * 方向感知（D4）：baseRtl 从 endX 向左分配；baseLtr 从 startX 向右分配。
     *  * 守卫：
     *  *  - gapCount > 0（单词行不 justify，防除零）
     *  *  - gapWidth > 0（contentWidth >= boundsWidth 时 gapWidth <= 0，跳过——不压缩，那是 exceedRtl 的活）
     *  *  - gapWidth <= maxGapWidth（= 0.5 字号；防止短行极端拉伸，阿拉伯文常见词间距约 1/4 字号）
     */
    private fun justifyLine(
        chars: ArrayList<TextChar>,
        boundsStartX: Float,
        boundsEndX: Float,
        boundsWidth: Float,
        textSize: Float,
        lineIsRtl: Boolean
    ) {
        //按照renderGroup分组，得到多少个词，CJK每个字就是一个词
        val words = chars.groupBy { it.renderGroup }.values.toList()

        if (words.size > 1) {
            //有多少个间隔
            val gapCount = words.size - 1

            if (gapCount <= 0) return   // 单词行不 justify

            val contentWidth = words.sumOf { w ->
                (w.maxOf { it.end } - w.minOf { it.start }).toDouble()
            }.toFloat()
            val gapWidth = (boundsWidth - contentWidth) / gapCount
            val maxGapWidth = textSize * 0.5f

            if (gapWidth <= 0f || gapWidth > maxGapWidth) return   // 超宽/极端拉伸 → 跳过

            distributeWords(words, boundsStartX, boundsEndX, gapWidth, lineIsRtl)
        } else {
            // CJK（整行 1 个 renderGroup，无空格）→ 逐字拉开
            val charWords = chars.map { listOf(it) }   // 每个 char 独立成"词"，复用 distributeWords

            //有多少个间隔
            val gapCount = charWords.size - 1

            if (gapCount <= 0) return   // 单词行不 justify

            val contentWidth = charWords.sumOf { w ->
                (w.maxOf { it.end } - w.minOf { it.start }).toDouble()
            }.toFloat()
            val gapWidth = (boundsWidth - contentWidth) / gapCount
            val maxGapWidth = textSize * 0.25f

            if (gapWidth <= 0f || gapWidth > maxGapWidth) return   // 超宽/极端拉伸 → 跳过

            distributeWords(charWords, boundsStartX, boundsEndX, gapWidth, lineIsRtl)
        }
    }

    /**
     * 按词（renderGroup 分组）重新分布：词内字符只平移不改宽度（保 HarfBuzz 连写形态）。
     *
     * 方向感知（D4 关键）：
     *   - baseRtl：cursor 从 boundsEndX 向左；words[0]=最低 renderGroup=阅读首词=视觉最右 → 对齐到 endX
     *   - baseLtr：cursor 从 boundsStartX 向右；words[0]=最低 renderGroup=阅读首词=视觉最左 → 对齐到 startX
     *
     * @param gapWidth 词间距：justify 正值（拉开）/ exceedRtl 负值（压缩）
     *
     * 不变量（C1）：origCharWidth = ch.end - ch.start 先捕获，再 ch.end += shift，再 ch.start = ch.end - origCharWidth。
     *   ⇒ 等效于 start/end 同位移 shift，且宽度不变、start<end 严格成立。
     *
     * 交叉验证：
     *   baseRtl：shift = cursor - origWordEnd → 词的 max(end)=origWordEnd 移到 cursor（视觉右端对齐）✓
     *   baseLtr：shift = cursor - origWordStart → 词的 min(start)=origWordStart 移到 cursor（视觉左端对齐）✓
     */
    private fun distributeWords(
        words: List<List<TextChar>>,
        boundsStartX: Float,
        boundsEndX: Float,
        gapWidth: Float,
        lineIsRtl: Boolean
    ) {
        var cursor = if (lineIsRtl) boundsEndX else boundsStartX
        words.forEach { word ->
            val origWordStart = word.minOf { it.start }
            val origWordEnd = word.maxOf { it.end }
            val wordWidth = origWordEnd - origWordStart

            // 整词平移：把词的"近端"对齐到 cursor
            //   baseRtl 近端 = 视觉右端（origWordEnd）；baseLtr 近端 = 视觉左端（origWordStart）
            val shift = if (lineIsRtl) {
                cursor - origWordEnd
            } else {
                cursor - origWordStart
            }

            word.forEach { ch ->
                val origCharWidth = ch.end - ch.start
                ch.end += shift
                ch.start = ch.end - origCharWidth
            }

            // cursor 推进到下一个词的近端（baseRtl 向左减 / baseLtr 向右加）
            cursor = if (lineIsRtl) {
                cursor - wordWidth - gapWidth
            } else {
                cursor + wordWidth + gapWidth
            }
        }
    }


    /**
     * 超宽压缩：contentWidth > boundsWidth 时，把 (boundsWidth - contentWidth)（负值）均摊到词间 gap，
     * 压缩词间距挤入列宽。与 justify 互补：justify 守卫 gapWidth>0，exceedRtl 守卫 gapWidth<0，天然互斥。
     *
     * 返回值无意义（第二遍遍历中，anchorLine 会兜底处理 exceedRtl 未覆盖的单字超宽）。
     * 守卫：
     *  - words.size > 1（单词无法压缩）
     *  - gapWidth < 0（未超宽则跳过）
     *
     * 不变量（C3）：origCharWidth = ch.end - ch.start 先捕获，再 ch.end += shift，再 ch.start = ch.end - origCharWidth。
     */
    private fun exceedZipWidth(
        chars: ArrayList<TextChar>,
        boundsStartX: Float,
        boundsEndX: Float,
        boundsWidth: Float,
        baseRtl: Boolean
    ) {
        val words = chars.groupBy { it.renderGroup }.values.toList()
        if (words.size <= 1) return   // 单词无法压缩，交 anchorLine 兜底

        val contentWidth =
            words.sumOf { w -> (w.maxOf { it.end } - w.minOf { it.start }).toDouble() }.toFloat()
        val gapCount = words.size - 1
        val gapWidth = (boundsWidth - contentWidth) / gapCount
        if (gapWidth >= 0f) return    // 未超宽，跳过

        distributeWords(words, boundsStartX, boundsEndX, gapWidth, baseRtl)
    }


    /**
     * 按 lineEffAlign 锚定整行 + 溢出钳制。
     * 左/右/居中共用 ──
     * 图片字符（isImage）坐标是绝对画布系（由 ImageLayoutProvider 设定），不参与定位/钳制——
     * 快照后排除，最后恢复（与历史 L3414-3454 一致）。
     */
    private fun anchorLine(
        textChars: ArrayList<TextChar>,
        lineEffAlign: CssTextAlign,
        lineIsRtl: Boolean,
        rawStart: Float,
        rawEnd: Float,
        rawWidth: Float,
        inkSize: Float
    ) {
        if (textChars.isEmpty()) return

        val effStart = rawStart + inkSize
        val effEnd = rawEnd - inkSize
        val effWidth = rawWidth - 2f * inkSize

        val contentLeft = textChars.minOf { it.start }
        val contentRight = textChars.maxOf { it.end }
        val contentWidth = contentRight - contentLeft

        val shift: Float = when {
            contentWidth >= rawWidth -> {
                if (lineIsRtl) effEnd - contentRight else effStart - contentLeft
            }

            else -> {
                val targetLeft = when (lineEffAlign) {
                    CssTextAlign.CssTextAlignRight -> {
                        effEnd - contentWidth
                    }

                    CssTextAlign.CssTextAlignLeft -> {
                        effStart
                    }

                    CssTextAlign.CssTextAlignCenter -> {
                        effStart + (effWidth - contentWidth) / 2f
                    }

                    else -> {  // Justify 兜底
                        if (lineIsRtl) effEnd - contentWidth else effStart
                    }
                }
                targetLeft - contentLeft
            }
        }

        if (shift != 0f) {
            textChars.forEach { it.start += shift; it.end += shift }
        }
    }

    /****
     * 组合runs 和 行内图片，得到新的 List<LayoutItem> 集合
     */
    private fun buildLayoutItems(
        runs: List<RunLayout>,
        imgTags: List<TextTag>,
        baseRtl: Boolean
    ): List<LayoutItem> {
        // ★ runs 是视觉序（L→R）；堆叠消费需要逻辑序。RTL 基调下 run 级整体逆序 = 逻辑序
        //   （L2 重排在两极层级下 = 整段反转，B1/B2 实测验证）。注意只能 run 级逆序：
        //   run 内部「图前文本/图/图后文本」子项本就按 offset 升序（逻辑序）生成，若对整个
        //   items 平铺反转会把 run 内部顺序也镜像 → RTL 段内嵌图片时图片两侧文本互换。
        //   imgTags 按 start 升序单调消费，run 逆序后 offset 仍单调递增，归并语义不变。
        val orderedRuns = if (baseRtl) runs.asReversed() else runs
        if (imgTags.isEmpty()) {
            return orderedRuns.map {
                LayoutItem.Run(it)
            }
        }

        val items = mutableListOf<LayoutItem>()

        var imgIndex = 0

        for (run in orderedRuns) {
            var segStart = run.offset
            val segEnd = run.offset + run.length

            while (imgIndex < imgTags.size && imgTags[imgIndex].start < segEnd) {
                val imgTag = imgTags[imgIndex]
                if (imgTag.start > segStart) {
                    items.add(
                        LayoutItem.Run(
                            RunLayout(
                                run.isRtl,
                                segStart,
                                imgTag.start - segStart
                            )
                        )
                    )
                }
                items.add(LayoutItem.Image(imgTag))
                segStart = imgTag.start
                imgIndex++
            }
            if (segStart < segEnd) {
                items.add(
                    LayoutItem.Run(
                        RunLayout(
                            run.isRtl,
                            segStart,
                            segEnd - segStart
                        )
                    )
                )
            }
        }
        while (imgIndex < imgTags.size) {
            items.add(LayoutItem.Image(imgTags[imgIndex]))
            imgIndex++
        }
        return items
    }

    /****
     * 放置Line Inner Image
     */
    private fun layoutInnerImage(
        imgTag: TextTag,
        lineIsRtl: Boolean,              // ★ 唯一方向源
        cursor: Float,                   // ★ 唯一位置源（行中=文本边缘 / 新行=行缘）

        currentBounds: LayoutBounds,
        marginLeft: Float,
        marginRight: Float,
        durY: Float,
        isFirstLineOfParagraph: Boolean,

        textPages: ArrayList<TextPage>,
        pageLines: ArrayList<Int>,

        pageLengths: ArrayList<Int>,
        stringBuilder: StringBuilder,
        paragraphLines: ArrayList<LineRecord>,
        paragraphIndex: Int,

        isTitle: Boolean,
        paint: TextPaint,
        lineHeightParam: Float,
        chapterIsRtl: Boolean
    ): ImageLayoutResult {

        val pairs = imgTag.paramsPairs()
        val imgSrc = pairs.firstOrNull { it.first == "src" }?.second.orEmpty()
        val imgWidth = pairs.firstOrNull { it.first == "width" }?.second?.toIntOrNull() ?: 0
        val imgHeight = pairs.firstOrNull { it.first == "height" }?.second?.toIntOrNull() ?: 0

        if (imgSrc.isEmpty() || imgWidth <= 0 || imgHeight <= 0) {
            return ImageLayoutResult(currentBounds, durY, cursor)   // 无效图：不处理
        }

        val targetImgHeight = (paint.textHeight * lineSpacingExtra).toInt()
        val (imgW, _) = ImageLayoutProvider.fillImageSize(
            imgWidth, imgHeight, imgSrc, 2 * targetImgHeight, targetImgHeight
        )

        val (imgRight, imgLeft) = if (lineIsRtl) {
            Pair(cursor, cursor - imgW)
        } else {
            Pair(cursor + imgW, cursor)
        }

        //是否在行剩余空间的范围内
        val fits = if (lineIsRtl) {
            imgLeft >= currentBounds.startX + marginLeft
        } else {
            imgRight <= currentBounds.endX  - marginRight
        }
        //当前图片所在行
        val lastLine = paragraphLines.lastOrNull()?.line
        if (fits && lastLine != null) {
            lastLine.textChars.add(
                TextChar(imgSrc, imgLeft, imgRight, isImage = true)
            )
            return ImageLayoutResult(currentBounds, durY, cursor = if (lineIsRtl) imgLeft else imgRight)
        }

        // 用 paint 指标算行高/descent（对齐 processMixedLine:303-304）
        val actualLineHeight = paint.textHeight * lineSpacingExtra * lineHeightParam
        val actualDescent = paint.descent() * lineSpacingExtra * lineHeightParam
        var targetBounds = currentBounds
        var targetY = durY

        //一行塞不下，创建新行
        if (targetY + actualLineHeight > visibleBottom) {
            val isStartColumn = when {
                !dualColumnEnabled -> targetBounds.role == ColumnRole.FULL
                chapterIsRtl -> targetBounds.isRightColumn
                else -> targetBounds.isLeftColumn
            }
            if (isStartColumn && dualColumnEnabled) { //双列模式下，并且当前位于开始列，则进入第二列中
                targetBounds = if (chapterIsRtl) layoutBoundsLeftColumn() else layoutBoundsRightColumn()
                targetY = paddingVertical.toFloat()
            } else { //否则，创建新页
                val lastPage = textPages.last()
                lastPage.text = stringBuilder.toString()
                pageLines.add(lastPage.textLines.size)
                pageLengths.add(lastPage.text.length)
                lastPage.height = targetY

                textPages.add(TextPage()) // new page
                stringBuilder.clear()
                targetBounds = if (!dualColumnEnabled) layoutBoundsPage()
                else {
                    if (chapterIsRtl) layoutBoundsRightColumn()
                    else layoutBoundsLeftColumn()
                }
                targetY = paddingVertical.toFloat()
            }
        }

        //先计算图片横向位置， 在新行中贴在行边缘
        val edgeCursor = if(lineIsRtl) targetBounds.endX.toFloat() - marginRight
                        else targetBounds.startX.toFloat() + marginLeft
        val (newImgLeft, newImgRight) = if (lineIsRtl) {
            Pair(edgeCursor - imgW, edgeCursor)
        } else {
            Pair(edgeCursor, edgeCursor + imgW)
        }

        //创建TextLine
        val newLine = TextLine(isTitle = isTitle,
            paragraphIndex = paragraphIndex,
            charStartOffset = imgTag.start,
            charEndOffset = imgTag.start,
            isRtl = lineIsRtl)

        newLine.textChars.add(TextChar(imgSrc, newImgLeft, newImgRight, isImage = true))
        textPages.last().textLines.add(newLine)
        newLine.upTopBottom(targetY, actualLineHeight, actualDescent)
        paragraphLines.add(LineRecord(newLine, targetBounds, isFirstLineOfParagraph))
        return ImageLayoutResult(targetBounds, targetY + actualLineHeight,
            if (lineIsRtl) newImgLeft else newImgRight)
    }

    /** 词内字符判定：字母且非 CJK（CJK 字符间是合法断点，不算词内截断）。
     *  不能用 Character.UnicodeScript——整个类 API 24 才有（lint 数据库 since=24），
     *  minSdk 23 真机会 NoClassDefFoundError，故用码点区间判定。 */
    private fun isWordChar(ch: Char) : Boolean {
        if (!ch.isLetter()) return false
        val code = ch.code
        return !(code in 0x2E80..0x9FFF ||    // CJK 部首/符号/注音/假名/汉字（含 Ext A）
                code in 0xAC00..0xD7FF ||     // 谚文音节 + 谚文扩展
                code in 0x1100..0x11FF ||     // 谚文字母 Jamo
                code in 0xA960..0xA97F ||     // 谚文扩展 A
                code in 0xF900..0xFAFF ||     // CJK 兼容表意文字
                code in 0xFF66..0xFF9D)       // 半角片假名
    }

}