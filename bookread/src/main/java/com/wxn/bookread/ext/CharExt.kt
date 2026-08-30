package com.wxn.bookread.ext

/** 词内字符判定：字母且非 CJK（CJK 字符间是合法断点，不算词内截断）。
    *  不能用 Character.UnicodeScript——整个类 API 24 才有（lint 数据库 since=24），
    *  minSdk 23 真机会 NoClassDefFoundError，故用码点区间判定。 */
fun Char.isWordChar() : Boolean {
    if (!this.isLetter()) return false
    val code = this.code
    return !(code in 0x2E80..0x9FFF ||    // CJK 部首/符号/注音/假名/汉字（含 Ext A）
            code in 0xAC00..0xD7FF ||     // 谚文音节 + 谚文扩展
            code in 0x1100..0x11FF ||     // 谚文字母 Jamo
            code in 0xA960..0xA97F ||     // 谚文扩展 A
            code in 0xF900..0xFAFF ||     // CJK 兼容表意文字
            code in 0xFF66..0xFF9D)       // 半角片假名
}