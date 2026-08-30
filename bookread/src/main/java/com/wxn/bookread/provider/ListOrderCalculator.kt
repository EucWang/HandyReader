package com.wxn.bookread.provider

import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag


/****
 * 列表段落的order计算
 */
object ListOrderCalculator {

    private val listOrderCounters = mutableMapOf<String, Int>()
    private val listOrderMaxes = mutableMapOf<String, Int>()     // prescan 结果：ol uuid → 该列表最大序号

    fun clear() {
        listOrderCounters.clear()
        listOrderMaxes.clear()
    }

    /**
     * 找到 li 的直接父级容器注解（ol/ul）。
     * annotations 为该段落的标签+祖先链；找不到返回 null（孤儿 li 兜底）。
     */
    private fun findParentTag(liTag: TextTag, annotations: List<TextTag>): TextTag? =
        if (liTag.parentUuid.isEmpty()) null
        else annotations.firstOrNull { it.uuid == liTag.parentUuid }

    /**
     * 选出段落的「自身 li」：注解链 = [祖先链…, 自身标签]（DFS 先序，祖先 li 在前、
     * 自身 li 在链尾——native get_fathers_tags 先返回祖先、cur_tags 追加在后）。
     *
     * 历史缺陷：firstOrNull 命中祖先 li → findParentTag 解析到外层 ol →
     * 嵌套 ol 计数全部挂到外层计数器（序号扁平 1,2,3,4，EPUB-B 验收缺陷）。
     * 全项目唯一 li 选取入口，prescan 与排版期必须同源
     */
    fun findOwnLi(annotations: List<TextTag>): TextTag? =
        annotations.lastOrNull { it.name == "li" }

    /**
     * 计算当前 li 的序号。
     * 计数器按 parentTag.uuid 分组——嵌套列表（ol>ol、ol>ul>ol）天然独立计数，互不干扰；
     * 同一 ol 在文档中多次出现（被标题打断后继续）共享同一 uuid → 计数自动延续；
     * @return 序号（≥1）；非有序列表返回 0
     */
    fun getLiOrder(
        liTag: TextTag?,
        annotations: List<TextTag>
    ): Int {
        if (liTag == null) return 0
        val parentTag = findParentTag(liTag, annotations) ?: return 0
        if (parentTag.name != "ol") return 0
        return nextOrder(liTag, parentTag, listOrderCounters)
    }


    /**
     * D8-2 预扫描：getTextChapter 在 clear() 后、排版循环前调用（contents 即段落全集）。
     * 只读模拟——独立 sim 计数器，不触碰正式计数器；与 getLiOrder 走同一 nextOrder，
     * 同输入同序列 → prescan(max) ≥ getLiOrder 全程任意返回值（OL-U4 锁定该性质）。
     * 扫描原始 contents（含后续被 tryParseToImage/tryParseToChapter 转走的段），
     * 覆盖面 ⊇ 实际进入 setTypeText 的集合 → 预留只可能偏宽（安全方向）。
     */
    fun prescan(contents: List<ReaderText>) {
        listOrderMaxes.clear()
        val sim = mutableMapOf<String, Int>()
        for (paragraph in contents) {
            if (paragraph !is ReaderText.Text) continue
            val annotations = paragraph.annotations
            val liTag = findOwnLi(annotations) ?: continue
            val parent = findParentTag(liTag, annotations) ?: continue
            if (parent.name != "ol") continue
            val order: Int = nextOrder(liTag, parent, sim)

            val currentOrder: Int = (listOrderMaxes[parent.uuid] ?: 0)
            listOrderMaxes[parent.uuid] = maxOf(currentOrder, order)
        }
    }

    /**
     * 该 li 所属 ol 的最大序号（prescan 结果；非 ol / 未 prescan / 孤儿 li 均返回 0）
     * */
    fun maxOrderOf(liTag: TextTag?, annotations: List<TextTag>): Int {
        if (liTag == null) return 0
        val parent = findParentTag(liTag, annotations) ?: return 0
        if (parent.name != "ol") return 0
        return listOrderMaxes[parent.uuid] ?: 0
    }

    private fun nextOrder(liTag: TextTag,
                          parent: TextTag,
                          counters: MutableMap<String, Int>) : Int {
        val valueAttr = liTag.paramsPairs()
            .firstOrNull { it.first == "value" }?.second?.toIntOrNull()
        val current = if (valueAttr != null && valueAttr >= 0) {
            valueAttr
        } else {
            val defaultStart = parent.paramsPairs()
                .firstOrNull { it.first == "start" }?.second?.toIntOrNull() ?: 1
            counters.getOrPut(parent.uuid) { maxOf(defaultStart, 1) }
        }
        counters[parent.uuid] = current + 1
        return current
    }
}