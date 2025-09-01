package com.wxn.bookread.data.model

import android.text.TextPaint
import androidx.annotation.ColorInt
import com.wxn.bookread.provider.ChapterProvider
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
    var isReadAloud: Boolean = false,

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

    var withLineDot: Int = 0    //行是否前面显示列表符号， 圆点/方块/空心圆.. 大于0即有符号，1 标识1级列表； 2表现2级列表...
) {

    fun upTopBottom(durY: Float, textPaint: TextPaint) {
        lineTop = durY
        lineBottom = lineTop + textPaint.textHeight
        lineBase = lineBottom - textPaint.fontMetrics.descent
    }

    fun addTextChar(charData: String, start: Float, end: Float) {
        textChars.add(TextChar(charData, start = start, end = end))
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