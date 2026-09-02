package com.wxn.bookread.data.model

import android.text.Layout
import android.text.StaticLayout
import com.wxn.base.util.Logger
import com.wxn.bookread.provider.ChapterProvider

/****
 * 在一页刚好显示下全部行时，根据TextLines集合的值和页面可视高度，矫正每一行的上，下，基线位置
 */
fun TextPage.upLinesPosition() = ChapterProvider.apply {
//        if (!ReadBookConfig.textBottomJustify) return@apply                 //如果不适配底部，则跳过
    //双列模式：左/右列各自从 paddingVertical 独立排版，顶部天然对齐；
    //跨列底部 justify 会按 textLines 索引把右列行（索引较大）整体下推，
    //造成"左列顶部高于右列、两列高度不等"。双列不做底部对齐，符合多列阅读惯例。
    if (dualColumnEnabled) return@apply
    if (textLines.size <= 1) return@apply                               //如果TextLines集合只有1个数据，跳过
    if (textLines.last().isImage) return@apply                          //如果TextLines集合最后一项是图片，跳过
    if (textLines.last().isLine) return@apply                          //页尾是边框行：表格页无正文收尾，无可均摊锚点，跳过
    if (visibleHeight - height >= with(textLines.last()) {
            lineBottom - lineTop }) return@apply //可视高度和TextPage高度之差，超过了TextLines最后一行的行高，跳过
    val surplus = (visibleBottom - textLines.last().lineBottom)         //计算 页面可见底部位置 减去 TextLines集合最后一项的底部位置 的差
    if (surplus == 0f) return@apply                                     //为0，则跳过


    // [rigid-table]（S2）槽位语义：正文行逐行一槽；连续表格块（isTableCell 文字行 + isLine
    // 边框行）整块一槽。表格内部行距/跨格基线是结构几何，不参与页底均摊——逐行槽位下
    // 「行内文字-边框刚性」「同行跨格对齐」「行间边框连续」三者互斥（相邻槽位移差 tj 正是
    // S2 三类症状来源），块槽位使其同时成立。isLine 全库唯一生产者是表格边框
    // （TableRenderProvider.buildRowBorders），isTableCell||isLine 可安全判块。
    var slotCount = 0
    var inBlock = false
    for(line in textLines) {
        val tableRelated = line.isTableCell || line.isLine
        if (!tableRelated) {
            slotCount++
            inBlock = false
        } else if (!inBlock) {
            slotCount++
            inBlock = true
        }
    }
    if (slotCount <= 1) return@apply                                    //防御性：单槽页无均摊对象（现网不可达——页尾为表格行已被 :60 拦截），在 height 记账前无副作用

    height += surplus                                                   //根据这个差值，矫正height值
    val tj = surplus / (slotCount - 1)                             //将这个差平均到每个槽位上

    var slot = 0
    var prevShift = 0f
    for (line in textLines) {
        val tableRelated = line.isTableCell || line.isLine
        if (!tableRelated) {
            prevShift = tj * slot
            slot++
            inBlock = false
        } else if (!inBlock) {
            prevShift = tj * slot
            slot++
            inBlock = true
        }
        line.lineTop = line.lineTop + prevShift
        line.lineBase = line.lineBase + prevShift
        line.lineBottom = line.lineBottom + prevShift
        if (line.isLine) {
            line.lineStart = Pair(line.lineStart.first, line.lineStart.second + prevShift)
            line.lineEnd = Pair(line.lineEnd.first, line.lineEnd.second + prevShift)
        }
    }
}

/****
 * 如果textLines中的内容为空时，
 * 则根据text的内容，重新生成textLines列表
 */
@Suppress("DEPRECATION")
fun TextPage.format(): TextPage {
    if (textLines.isEmpty() && ChapterProvider.visibleWidth > 0) {
        val layout = StaticLayout(
            text,                               //source
            ChapterProvider.contentPaint,       //paint
            ChapterProvider.visibleWidth,       //width
            Layout.Alignment.ALIGN_NORMAL,      //align
            1f,     //spacingmult
            0f,     //spacingadd
            false   //includepad
        )
        var y = (ChapterProvider.visibleHeight - layout.height) / 2f
        if (y < 0) y = 0f
        for (lineIndex in 0 until layout.lineCount) {
            val textLine = TextLine()
            textLine.lineTop = ChapterProvider.paddingVertical + y + layout.getLineTop(lineIndex)    //行上位置
            textLine.lineBase = ChapterProvider.paddingVertical + y + layout.getLineBaseline(lineIndex)              //行基线位置
            textLine.lineBottom = ChapterProvider.paddingVertical + y + layout.getLineBottom(lineIndex)                //行底位置
            var x = ChapterProvider.paddingHorizontal + (ChapterProvider.visibleWidth - layout.getLineMax(lineIndex)) / 2           //行左位置
            textLine.text = text.substring(layout.getLineStart(lineIndex), layout.getLineEnd(lineIndex))    //截取一行文字给TextLine
            for (i in textLine.text.indices) {//遍历行文字每个字
                val char = textLine.text[i].toString()
                val cw = StaticLayout.getDesiredWidth(char, ChapterProvider.contentPaint)       //测量每个字
                val x1 = x + cw
                textLine.addTextChar(charData = char, start = x, end = x1)                      //计算每个字的左右位置
                x = x1
            }
            textLines.add(textLine)
        }
        height = ChapterProvider.visibleHeight.toFloat()
        Logger.d("TextPage::format::textLines is empty and visibleWidth[${ChapterProvider.visibleWidth}] > 0 and text.size=${text.length}")
    }
    return this
}

///***
// * 用于显示的阅读进度
// */
//val readProgress: String
//    get() {
//        val df = DecimalFormat("0.0%")
//        if (chapterSize == 0 || pageSize == 0 && chapterIndex == 0) {
//            return "0.0%"
//        } else if (pageSize == 0) {
//            return df.format((chapterIndex + 1.0f) / chapterSize.toDouble())
//        }
//        var percent =
//            df.format(chapterIndex * 1.0f / chapterSize + 1.0f / chapterSize * (index + 1) / pageSize.toDouble())
//        if (percent == "100.0%" && (chapterIndex + 1 != chapterSize || index + 1 != pageSize)) {
//            percent = "99.9%"
//        }
//        return percent
//    }