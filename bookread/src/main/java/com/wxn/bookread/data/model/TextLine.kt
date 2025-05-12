package com.wxn.bookread.data.model

import android.text.TextPaint
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.textHeight

/***
 * 一行显示的字符串
 */
data class TextLine(

    /**
     * 显示的内容
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
    var isReadAloud: Boolean = false
) {

    fun upTopBottom(durY: Float, textPaint: TextPaint) {
        lineTop = ChapterProvider.paddingTop + durY
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