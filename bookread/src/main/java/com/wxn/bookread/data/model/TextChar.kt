package com.wxn.bookread.data.model

data class TextChar(
    val charData: String,
    var start: Float,
    var end: Float,
    var isImage: Boolean = false,

    var renderGroup: Int = 0,  //LTR 链路默认 0；TextLayoutProvider 填 ≥1


    // E8：本字符是否必须与同组字符合并为一次 drawText（连写/组合脚本 HarfBuzz 整形）。
    // true  → ShapedRunBuffer 累积进同组 run，组内坐标须自然衔接（组内逐字坐标不参与绘制）；
    // false → 逐字 drawText（ch.start 为准，justify CHAR_DISTRIBUTE/HYBRID 组内分布可见）。
    // 由 TextLayoutProvider.placeCharsFromLayout 按 run 级白名单判定（任一码点不在
    // isPerCharDrawSafeCode 白名单 → 整个 run 置 true，缺省保守 = 历史整组行为）。
    var needsRunShaping: Boolean = false
)