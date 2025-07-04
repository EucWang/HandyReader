package com.wxn.bookread.data.model

import android.text.Layout
import android.text.StaticLayout
import com.wxn.base.util.Logger
import com.wxn.bookread.provider.ChapterProvider
import java.text.DecimalFormat


/****
 * 一页显示的书籍文案内容
 * 包含若干TextLine
 */
data class TextPage(
    /***
     * 页面索引
     */
    var index: Int = 0,
    var text: String = "", //App.INSTANCE.getString(R.string.data_loading),

    /***
     * 标题
     */
    var title: String = "",
    /***
     * 行数据列表
     */
    val textLines: ArrayList<TextLine> = arrayListOf(),
    /***
     * 当前所在章节包含的页面数
     */
    var pageSize: Int = 0,
    /***
     * 章节数
     */
    var chapterSize: Int = 0,
    /***
     * 章节索引
     */
    var chapterIndex: Int = 0,
    /***
     * 高度
     */
    var height: Float = 0f,

    var bookmarkId: Long = -1,
) {


    /****
     * 在一页刚好显示下全部行时，根据TextLines集合的值和页面可视高度，矫正每一行的上，下，基线位置
     */
    fun upLinesPosition() = ChapterProvider.apply {
//        if (!ReadBookConfig.textBottomJustify) return@apply                 //如果不适配底部，则跳过
        if (textLines.size <= 1) return@apply                               //如果TextLines集合只有1个数据，跳过
        if (textLines.last().isImage) return@apply                          //如果TextLines集合最后一项是图片，跳过
        if (visibleHeight - height >= with(textLines.last()) { lineBottom - lineTop }) return@apply //可视高度和TextPage高度之差，超过了TextLines最后一行的行高，跳过
        val surplus = (visibleBottom - textLines.last().lineBottom)         //计算 页面可见底部位置 减去 TextLines集合最后一项的底部位置 的差
        if (surplus == 0f) return@apply                                     //为0，则跳过
        height += surplus                                                   //根据这个差值，矫正height值
        val tj = surplus / (textLines.size - 1)                             //将这个差平均到每一行上
        for (i in 1 until textLines.size) {                                 //遍历每一行，矫正每一行上的上，下，基线位置
            val line = textLines[i]
            line.lineTop = line.lineTop + tj * i
            line.lineBase = line.lineBase + tj * i
            line.lineBottom = line.lineBottom + tj * i
        }
    }

    /****
     * 如果textLines中的内容为空时，
     * 则根据text的内容，重新生成textLines列表
     */
    @Suppress("DEPRECATION")
    fun format(): TextPage {
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
                textLine.lineTop = ChapterProvider.paddingTop + y + layout.getLineTop(lineIndex)    //行上位置
                textLine.lineBase = ChapterProvider.paddingTop + y + layout.getLineBaseline(lineIndex)              //行基线位置
                textLine.lineBottom = ChapterProvider.paddingTop + y + layout.getLineBottom(lineIndex)                //行底位置
                var x = ChapterProvider.paddingLeft + (ChapterProvider.visibleWidth - layout.getLineMax(lineIndex)) / 2           //行左位置
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
            Logger.e("TextPage::format::textLines is empty and visibleWidth[${ChapterProvider.visibleWidth}] > 0 and text.size=${text.length}")
        }
        return this
    }

    fun removePageAloudSpan(): TextPage {
        textLines.forEach { textLine ->
            textLine.isReadAloud = false
        }
        return this
    }

    fun upPageAloudSpan(pageStart: Int) {
        removePageAloudSpan()
        var lineStart = 0
        for ((index, textLine) in textLines.withIndex()) {
            if (pageStart > lineStart && pageStart < lineStart + textLine.text.length) {
                for (i in index - 1 downTo 0) {
                    if (textLines[i].text.endsWith("\n")) {
                        break
                    } else {
                        textLines[i].isReadAloud = true
                    }
                }
                for (i in index until textLines.size) {
                    if (textLines[i].text.endsWith("\n")) {
                        textLines[i].isReadAloud = true
                        break
                    } else {
                        textLines[i].isReadAloud = true
                    }
                }
                break
            }
            lineStart += textLine.text.length
        }
    }

    /***
     * 用于显示的阅读进度
     */
    val readProgress: String
        get() {
            val df = DecimalFormat("0.0%")
            if (chapterSize == 0 || pageSize == 0 && chapterIndex == 0) {
                return "0.0%"
            } else if (pageSize == 0) {
                return df.format((chapterIndex + 1.0f) / chapterSize.toDouble())
            }
            var percent =
                df.format(chapterIndex * 1.0f / chapterSize + 1.0f / chapterSize * (index + 1) / pageSize.toDouble())
            if (percent == "100.0%" && (chapterIndex + 1 != chapterSize || index + 1 != pageSize)) {
                percent = "99.9%"
            }
            return percent
        }
}