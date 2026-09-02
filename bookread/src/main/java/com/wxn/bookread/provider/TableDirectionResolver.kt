package com.wxn.bookread.provider

import com.wxn.base.bean.ReaderText


/**
 * 表格方向预扫描器（已批方案 D1/P0-1 语义，判定函数注入以便 JVM 单测——segment 走 JNI）。
 * getTextChapter 主循环前对 contents 一次 O(n) 遍历，产物为局部 Map（非全局状态）。
 *
 * 与 `paragraph.segDirect` 的关系（审查七确认）：整合后 BookHelper.disposeContent 已为
 * 每个表格行段落写入 segDirect，但那是**段落级** first-strong；D1 语义是**整表拼接后**
 * first-strong（数字数据行 + 阿语数据行 → 整表 RTL）。二者在「首行无强字符」时判定不同，
 * 不可互相替代，preScan 保留。
 */
object TableDirectionResolver {

    /***
     * 对章节中的
     * 表格方向预扫描
     */
    fun preScan(
        contents: List<ReaderText>, //一段章节内容
        isRtlOf: (String) -> Boolean = { RTLSegmenter.segment(it).baseRtl }  //判定一个文本方向是否是Rtl的
    ): Map<Int, Boolean> {
        val result = HashMap<Int, Boolean>()
        var i = 0
        while (i < contents.size) {
            val  cur = contents[i]
            //非Table 内的情况，过滤掉
            if (cur !is ReaderText.Text ||
                !TableAnnotation.isTableRow(cur)) {
                i++
                continue
            }
            val key = groupKey(cur)
            val joined = StringBuilder(cur.line)
            var j = i + 1
            while ( j < contents.size) {
                val next = contents[j]
                if (next is ReaderText.Text &&
                    TableAnnotation.isTableRow(next) &&
                    groupKey(next) == key) {
                    joined.append(' ').append(next.line)
                    j++
                } else {
                    break
                }
            }
            //判定表格的基本排版方向
            val tableIsRtl = isRtlOf(joined.toString())
            for (k in i until j) {
                result[k] = tableIsRtl
            }
            i = j
        }
        return result
    }

    private fun groupKey(paragraph : ReaderText.Text) : String =
        TableAnnotation.tableSignatureOf(paragraph) ?: ("anno:" + paragraph.line.hashCode())
}