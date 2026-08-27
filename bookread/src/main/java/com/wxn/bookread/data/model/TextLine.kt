package com.wxn.bookread.data.model

import android.text.TextPaint
import com.wxn.bookread.textHeight

/***
 * 一行显示的字符串
 */
data class TextLine(

    /**
     * 显示的内容, 或者需要显示的图片的本地路径
     */
    var text: String = "",

    /***
     * 测量之后的，显示的每一个字符水平方向上的偏移位置
     */
    val textChars: ArrayList<TextChar> = arrayListOf(),

    /***
     * 行顶部位置
     */
    var lineTop: Float = 0f,

    /***
     * 行基线位置
     */
    var lineBase: Float = 0f,
    /***
     * 行底部位置
     */
    var lineBottom: Float = 0f,

    /***
     * 是否是标题
     */
    val isTitle: Boolean = false,
    /***
     * 是否是图片
     */
    val isImage: Boolean = false,

    /***
     * 是否正在播放tts语音
     */
//    var isReadAloud: Boolean = false,

    var paragraphIndex: Int = 0,        //当前行所在的段落的序号
    var charStartOffset: Int = 0,       //当前行在所在段落中的起始位置 the start index (inclusive).
    var charEndOffset: Int = 0,        //当前行在所在段落中的结束位置  the end index (exclusive),


    var isLine : Boolean = false,                           // 是否是线段
    var lineStart: Pair<Float, Float> = Pair(0f, 0f),       // 线的起点
    var lineEnd: Pair<Float, Float> = Pair(0f, 0f),         // 线的终点
    var lineBorder: Float = 1f,                             // 线段的粗细
    var lineColor: String? = null,                          // 线段颜色

    //表格中的每一个单元格也是一个TextLine,
    var isTableCell: Boolean = false,
    var rowIndex: Int = 0, //单元格行索引
    var colIndex: Int = 0,  //单元格列索引
    var rowLineOffset : Int = 0,   //单元格所在的行文字在一个tr行中的偏移量

    var lineDot: LineDot? = null,

    /**
     * 行方向：true=RTL，false=LTR。
     * 新建行（!lineShared）恒 = 段落基调 segDirect.baseRtl（UAX#9：行的基方向恒为段落嵌入
     * 方向；段落首行永不与前一段落共享 → 首行必为新建行）。run.isRtl 只决定 run 自身
     * StaticLayout 的文本方向，不决定行方向。
     * 驱动 cursor 起点/推进、相邻摆放、对齐（anchorLine/justify/indent）、列表圆点锚定侧。
     * 默认 false（setNormalText 纯 LTR 路径不赋值，保持 LTR 语义）。
     */
    var isRtl: Boolean = false
) {

    fun upTopBottom(durY: Float, textPaint: TextPaint) {
        lineTop = durY
        lineBottom = lineTop + textPaint.textHeight
        lineBase = lineBottom - textPaint.fontMetrics.descent
    }

    /**
     * F7 新增:用于混合字号行(lineHeight/descent 来自 layout.getLineAscent/getLineDescent)。
     *
     * - [lineHeight]: 实际行高(已含 lineSpacingExtra 系数,见 setNormalText F3 公式)
     * - [descent]:   实际 descent(已含 lineSpacingExtra 系数;基线 = bottom - descent)
     *
     * 与原 `upTopBottom(durY, textPaint)` 重载并存,非 inline 段落继续用原重载(零影响)。
     */
    fun upTopBottom(durY: Float, lineHeight: Float, descent: Float) {
        lineTop = durY
        lineBottom = lineTop + lineHeight
        lineBase = lineBottom - descent
    }

    fun addTextChar(charData: String, start: Float, end: Float, renderGroup: Int = 0) {
        textChars.add(TextChar(charData, start = start, end = end, renderGroup = renderGroup))
    }

    fun getTextCharAt(index: Int): TextChar {
        return textChars[index]
    }

    fun getTextCharReverseAt(index: Int): TextChar {
        return textChars[textChars.lastIndex - index]
    }

    fun getTextCharsCount(): Int {
        return textChars.size
    }

}
