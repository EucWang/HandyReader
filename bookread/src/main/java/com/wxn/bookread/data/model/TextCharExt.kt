package com.wxn.bookread.data.model

import com.wxn.bookread.ext.isCjkCode
import com.wxn.bookread.ext.isConnectedScriptCode

/**
 * 计算命中字符的视觉水平跨度（方向无关）。
 * 返回 (left, right) = 命中字符的最小 start / 最大 end；无命中返回 null。
 *
 * RTL 行的 textChars 按逻辑序追加（视觉右→左，见 TextLayoutProvider.placeCharsFromLayout），
 * 数组首尾字符不能直接作矩形左右边，必须对命中区间取 min/max。
 * LTR 行（视觉左→右序）下 min/max 恰好等价于首尾，行为不变。
 *
 * @param match 以 textChars 下标（rawIndex）为入参的命中判定；默认整行
 */
fun List<TextChar>.visualSpan(match: (index: Int) -> Boolean = { true }): Pair<Float, Float>? {
    var left = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    for (i in indices) {
        if (!match(i)) continue
        val ch = this[i]
        if (ch.start < left) left = ch.start
        if (ch.end > right) right = ch.end
    }
    return if (right < left) null else left to right
}


/****
 * 可剔除空白（行首/行尾 run 口径）：
 * 图片 charData 是路径字符串，永不算空白
 */
fun TextChar.isTrimableWs() : Boolean =
    !this.isImage && this.charData.firstOrNull()?.isWhitespace() == true



/**
 * 逐码点扫描（charData 可能含代理对，禁逐 Char）
 * 图片字符不参与
 */
private fun anyCodePoint(chars: List<TextChar>, pred: (Int)->Boolean) : Boolean =
    chars.any { ch ->
        if (ch.isImage) {
            false
        } else {
            var i = 0
            var hit = false
            val str = ch.charData
            while (i < str.length) {
                val code = str.codePointAt(i)
                if (pred(code)) {
                    hit = true
                    break
                }
                i += Character.charCount(code)
            }
            hit
        }
    }

/***
 * 判定一行文字中是否包含中日韩文
 */
fun List<TextChar>.containsCJK() =
    anyCodePoint(this) {
        it.isCjkCode()
    }

/***
 * 判定一行文字中是否包含
 * 阿拉伯语/叙利亚文/Thaana/N'Ko/曼达文等邻区 RTL 文字/Adlam
 * 这些需要支持文字连写的内容
 */
fun List<TextChar>.containsConnectedScript() =
    anyCodePoint(this){
        it.isConnectedScriptCode()
    }