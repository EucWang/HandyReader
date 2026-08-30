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
     * 默认 false
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
     * - [lineHeight]: 实际行高(已含 lineSpacingExtra 系数)
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

    /**
     * 行内两种下标口径的双向换算（图片 TextChar 只占数组位、不占文本位）：
     * - 数组口径：textChars 的下标（含图片占位），用于 ShapedRunBuffer 相邻探测、视觉 span 等渲染链路；
     * - 文本口径：= 本行在 line.text 中的下标（不含图片），用于 charStartOffset + index 求
     *   段内偏移、标签/inlineStyle 匹配、选区 sC/eC 与 lineText 截取（统一坐标约定，方案 M2-③）。
     */
    fun textCharCount(): Int = textChars.count { !it.isImage }

    /** 数组下标 → 文本下标：[arrayIndex] 之前（不含）的非图片字符数。越界按钳制处理。 */
    fun textIndexAt(arrayIndex: Int): Int {
        if (arrayIndex <= 0) return 0
        var n = 0
        val upper = arrayIndex.coerceAtMost(textChars.size)
        for (i in 0 until upper) {
            if (!textChars[i].isImage) n++
        }
        return n
    }

    /** 文本下标 → 数组下标：第 [textIndex] 个非图片字符的数组位；行内图片后缀/越界时返回 textChars.size。 */
    fun arrayIndexAt(textIndex: Int): Int {
        if (textIndex < 0) return 0
        var n = 0
        textChars.forEachIndexed { i, ch ->
            if (!ch.isImage) {
                if (n == textIndex) return i
                n++
            }
        }
        return textChars.size
    }

}
