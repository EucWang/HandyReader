package com.wxn.bookread.data.model

import android.text.TextPaint
import com.wxn.bookread.textHeight

/**
 * [eC]（文本/UTF-16 口径字符位）所指字符的右边界（exclusive）；越界/图尾行兜底为行文本长。
 * 前置条件（R13/A-1）：[eC] 须为字符**起始**码元位（textIndexAt 产物，3 处调用点均满足）；
 * 若传入代理对中位，按 R10 向上取整会多包一个字符（实际不可达，契约明示）。
 */
fun TextLine.endExclusiveUtf16(endCharIndex: Int) : Int {
    val endCharIdx = endCharIndex.coerceAtMost(text.length)
    val arr = arrayIndexAt(endCharIdx)
    return if (arr >= textChars.size) {
        text.length
    } else {
        textIndexAt(arr + 1)
    }
}


/**
 * 行内两种下标口径的双向换算（图片 TextChar 只占数组位、不占文本位）：
 * - 数组口径：textChars 的下标（含图片占位），用于 ShapedRunBuffer 相邻探测、视觉 span 等渲染链路；
 * - 文本口径：= 本行在 line.text 中的 UTF-16 码元下标（不含图片），用于 charStartOffset + index
 *   求段内偏移、标签/inlineStyle 匹配、选区 sC/eC 与 lineText 截取（统一坐标约定 M2-③；
 *   码点→码元口径统一，方案 M3 §3.1）。
 *
 * textCharCount 防误用警示（R13）：非文本上界——返回非图片**码点**数，emoji 行上
 * ≠ textIndexAt(末位)+1（码元更多），禁止与 UTF-16 下标比较或作 substring 边界。
 */
fun TextLine.textCharCount(): Int = textChars.count { !it.isImage }

/** 数组下标 → 文本(UTF-16)下标：[arrayIndex] 之前（不含）非图片字符的码元长度和（emoji 计 2）。越界钳制。 */
fun TextLine.textIndexAt(arrayIndex: Int): Int {
    if (arrayIndex <= 0) return 0
    var n = 0
    val upper = arrayIndex.coerceAtMost(textChars.size)
    for (i in 0 until upper) {
        if (!textChars[i].isImage) n += textChars[i].charData.length // ★ 码点计数 → 码元长度和
    }
    return n
}

/**
 * 文本(UTF-16)下标 → 数组下标：码元累计到达 [textIndex] 的非图片字符位；越界返回 textChars.size。
 * 中位代理对（R10）：[textIndex] 落在代理对中间（如陈旧 locator 的 mid-pair 值）时，
 * 返回该字符**之后**一个字符位（非 textChars.size）——不可分割前提下最接近的合法位。
 */
fun TextLine.arrayIndexAt(textIndex: Int): Int {
    if (textIndex < 0) return 0
    var n = 0
    textChars.forEachIndexed { i, ch ->
        if (!ch.isImage) {
            if (n >= textIndex) return i
            n += ch.charData.length // ★ 码点计数 → 码元长度和
        }
    }
    return textChars.size
}


fun TextLine.upTopBottom(durY: Float, textPaint: TextPaint) {
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
fun TextLine.upTopBottom(durY: Float, lineHeight: Float, descent: Float) {
    lineTop = durY
    lineBottom = lineTop + lineHeight
    lineBase = lineBottom - descent
}

fun TextLine.addTextChar(charData: String, start: Float, end: Float, renderGroup: Int = 0) {
    textChars.add(TextChar(charData, start = start, end = end, renderGroup = renderGroup))
}

fun TextLine.getTextCharAt(index: Int): TextChar {
    return textChars[index]
}

fun TextLine.getTextCharReverseAt(index: Int): TextChar {
    return textChars[textChars.lastIndex - index]
}

fun TextLine.getTextCharsCount(): Int {
    return textChars.size
}
