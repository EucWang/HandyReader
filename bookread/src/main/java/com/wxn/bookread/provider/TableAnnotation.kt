package com.wxn.bookread.provider

import com.wxn.base.bean.ReaderText


/**
 *  表格注解纯解析（零 android 依赖，JVM 可测）
 **/
object TableAnnotation {

    /****
     * 是否当前ReaderText是表格行tr
     */
    fun isTableRow(paragraph: ReaderText): Boolean =
        paragraph is ReaderText.Text && paragraph.annotations.any { it.name == "tr" }


    /***
     *  表格签名（同表识别键）：
     *  返回：cols|rows|table_percent 原始串；
     *  无 table 注解返回 null
     */
    fun tableSignatureOf(paragraph: ReaderText.Text): String? {
        val tagTable = paragraph.annotations.firstOrNull { it.name == "table" } ?: return null
        var cols = 0;
        var rows = 0;
        var percents = ""

        tagTable.paramsPairs().forEach { (k, v) ->
            when (k) {
                "cols" -> cols = v.toIntOrNull() ?: 0
                "rows" -> rows = v.toIntOrNull() ?: 0
                "table_percent" -> percents = v
            }
        }
        return "$cols|$rows|$percents"
    }
}