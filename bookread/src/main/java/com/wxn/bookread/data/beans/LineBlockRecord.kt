package com.wxn.bookread.data.beans

/***
 * 行块记录：
 * 一个 run 行块
 * 在 TextLine.textChars 中的区间与
 * 其 bidi level（消费序=逻辑序追加）
 */
data class LineBlockRecord(val charStart: Int,  //一块连续的分段的起始字符索引
                           val charEnd: Int,  //一块连续的分段的结束字符索引
                           val level: Int  // 分段块的级别
)