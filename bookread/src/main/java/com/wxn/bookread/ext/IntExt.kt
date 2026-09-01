package com.wxn.bookread.ext

import android.content.res.Resources

/***
 * 将像素单位的int值转换成以dp为单位的int值
 */
val Int.dp: Int
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics
    ).toInt()

/***
 * 将像素单位的int值转换成sp为单位的int值
 */
val Int.sp: Int
    get() = android.util.TypedValue.applyDimension(
        android.util.TypedValue.COMPLEX_UNIT_SP, this.toFloat(), Resources.getSystem().displayMetrics
    ).toInt()


/***
 * 将数字转换成16进制显示的字符串
 */
val Int.hexString: String
    get() = Integer.toHexString(this)


/** CJK 码点判定：
 * 区间表与 [CharExt.isWordChar] 同源；
 * 禁用 Character.UnicodeScript（API 24+，minSdk 23）
 * */
fun Int.isCjkCode(): Boolean =
    this in 0x2E80..0x9FFF ||    // CJK 部首/符号/注音/假名/汉字（含 Ext A）
            this in 0xAC00..0xD7FF ||    // 谚文音节 + 谚文扩展
            this in 0x1100..0x11FF ||    // 谚文字母 Jamo
            this in 0xA960..0xA97F ||    // 谚文扩展 A
            this in 0xF900..0xFAFF ||    // CJK 兼容表意文字
            this in 0xFF66..0xFF9D       // 半角片假名


/**
 * 连写/邻接 RTL 文字码点：
 * 词内加字距会打断 HarfBuzz 连写成形 → 行走纯词距分布（组内零移动，竞品一致）
 **/
fun Int.isConnectedScriptCode(): Boolean =
    this in 0x0600..0x06FF ||    // Arabic
    this in 0x0700..0x086F ||    // 叙利亚文/Thaana/N'Ko/曼达文等邻区 RTL 文字（R7 补全，纯词距处理皆安全）
    this in 0xFB50..0xFDFF ||    // Arabic Presentation Forms-A
    this in 0xFE70..0xFEFF ||    // Arabic Presentation Forms-B
    this in 0x1E900..0x1E95F     // Adlam（RTL 连写，R9 补全）


/***
 * 逐字独立绘制安全码点白名单（E8）：
 * true = 该码点离开上下文字形不变（无连写/组合/重排序），可脱离 run 单独 drawText，
 *        ShapedRunBuffer 按 ch.start 逐字绘制 → justify 组内分布可见。
 * false = 必须整组整形绘制（连写脚本/组合标点/格式符/未知脚本，缺省保守）。
 *
 * 区间表风格与 [isCjkCode] 同源；未列出码点一律 false（整组整形，安全缺省）。
 */
fun Int.isPerCharDrawSafeCode(): Boolean = when {
    // —— 格式/不可见/组合码点：显式排除（优先命中，白名单区间不得覆盖它们）——
    this == 0x00AD ||                   // 软连字符
            this in 0x200B..0x200F ||           // ZWSP/ZWNJ/ZWJ/LRM/RLM
            this in 0x202A..0x202E ||           // 双向格式控制
            this in 0x2060..0x206F ||           // 隐式格式/废弃格式符
            this in 0xFE00..0xFE0F ||           // 变体选择符 VS1-16
            this in 0xE0100..0xE01EF ||         // 变体选择符补充
            this in 0x1100..0x11FF ||           // 组合 Jamo（谚文成组逻辑，逐字会拆散音节组合）
            this in 0x3099..0x309A ||           // 假名组合浊点/半浊点（isCjkCode 区间内的 Mn 组合符）
            this in 0x0483..0x0489 ||           // 西里尔组合重音
            this == 0x0385 ||                   // 希腊组合元音附标（R2-3：0370..03FF 白名单区内的 Mn，显式排除）
            this in 0x0300..0x036F              // 组合附加符（通用）
        -> false

    // —— 白名单：逐字安全 ——
    this in 0x0020..0x007E -> true      // ASCII 基本拉丁（字母/数字/标点）
    this in 0x00A0..0x024F -> true      // Latin-1 补充 + 拉丁扩展 A/B（预组合字母，含 ©®°±¹²³）
    this in 0x0370..0x03FF -> true      // 希腊（预组合；0340-0345 落 0300 排除区、0385 已显式排除）
    this in 0x0400..0x04FF -> true      // 西里尔（组合 0483-0489 已前置排除）
    this in 0x2000..0x205F -> true      // 常用标点（引号/破折号/各类空格；2060 起已前置排除）
    this in 0x2070..0x209F -> true      // 上标/下标 ⁰¹⁴ₐ（00B9/B2/B3 已由 Latin-1 覆盖）
    this in 0x20A0..0x20CF -> true      // 货币符号 €£¥
    this in 0x2100..0x214F -> true      // 字母类符号 ™℮℗（™ 高频）
    this in 0x2150..0x218F -> true      // 分数/罗马数字 Ⅰ Ⅱ Ⅲ Ⅷ（中文书章节编号高频）
    this in 0x2460..0x24FF -> true      // 带圈数字①②⑴（CJK 书常见）
    this in 0x25A0..0x27BF -> true      // 几何图形/杂项符号 ■●★（CJK 书常见）
    this in 0x20000..0x2FA1F -> true    // CJK 扩展 B-F + 兼容补充（繁体古字，astral）
    this in 0xFF01..0xFF65 -> true      // 全角标点/字母/数字（isCjkCode 仅覆盖 FF66 起）
    this in 0xFFE0..0xFFEE -> true      // 全角符号 ￥￠
    this.isCjkCode() -> true            // CJK 部首/注音/假名/汉字/谚文音节/兼容表意
    //（2E80-9FFF 含 3000-303F CJK 标点；组合浊点已前置排除）
    else -> false                       // 缺省保守：泰语/天城文/希伯来/阿拉伯/emoji 等 → 整组整形
}