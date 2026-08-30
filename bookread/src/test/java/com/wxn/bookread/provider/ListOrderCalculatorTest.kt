package com.wxn.bookread.provider

import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag
import com.wxn.bookread.ui.ListDotRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ListOrderCalculator 计数/预扫描 + 序号缩进公式（plan §7.1 OL-U1~U5，
 * docs/plans/2026-08-26-plan-list-dot-shape-enum.md §2-D8）。
 *
 * TextTag 为 base 模块纯 data class → 全部 JVM 可测。
 */
class ListOrderCalculatorTest {

    private fun tag(uuid: String, name: String, parent: String = "", params: String = "") =
        TextTag(uuid = uuid, name = name, parentUuid = parent, params = params)

    private fun liOf(liUuid: String, parent: TextTag, params: String = "") =
        tag(liUuid, "li", parent.uuid, params)

    private fun textPara(vararg tags: TextTag) =
        ReaderText.Text("item", tags.toList())

    private fun liOf(paragraph: ReaderText.Text): TextTag =
        paragraph.annotations.first { it.name == "li" }

    @Before
    fun reset() = ListOrderCalculator.clear()

    // ------------------------------------------------------------
    // OL-U1：嵌套独立计数（按父级 ol uuid 分组）
    // ------------------------------------------------------------

    @Test
    fun olU1_nestedLists_countIndependently() {
        val o1 = tag("o1", "ol")
        val l1 = liOf("l1", o1)
        val o2 = tag("o2", "ol", parent = l1.uuid)          // o1 > l1 > o2（内层 ol）
        val l2 = liOf("l2", o2)
        val l3 = liOf("l3", o1)

        assertEquals(1, ListOrderCalculator.getLiOrder(l1, listOf(o1, l1)))
        // 内层独立从 1 起计数，不受外层干扰
        assertEquals(1, ListOrderCalculator.getLiOrder(l2, listOf(o1, l1, o2, l2)))
        // 外层延续（第 2 项）
        assertEquals(2, ListOrderCalculator.getLiOrder(l3, listOf(o1, l3)))

        // ul 父级 → 非有序，返回 0（走形状符号）
        val u1 = tag("u1", "ul")
        val lu = liOf("lu", u1)
        assertEquals(0, ListOrderCalculator.getLiOrder(lu, listOf(u1, lu)))
        // 孤儿 li（parentUuid 空）→ 0
        val orphan = tag("lo", "li")
        assertEquals(0, ListOrderCalculator.getLiOrder(orphan, listOf(orphan)))
    }

    // ------------------------------------------------------------
    // OL-U2：<ol start="5"> 起始值
    // ------------------------------------------------------------

    @Test
    fun olU2_startAttribute() {
        val o1 = tag("o1", "ol", params = "start=5")
        val l1 = liOf("l1", o1)
        val l2 = liOf("l2", o1)
        assertEquals(5, ListOrderCalculator.getLiOrder(l1, listOf(o1, l1)))
        assertEquals(6, ListOrderCalculator.getLiOrder(l2, listOf(o1, l2)))
    }

    // ------------------------------------------------------------
    // OL-U3：<li value="10"> 跳号（其后延续）
    // ------------------------------------------------------------

    @Test
    fun olU3_valueAttribute_jumpAndContinue() {
        val o1 = tag("o1", "ol")
        val l1 = liOf("l1", o1)
        val l2 = liOf("l2", o1, params = "value=10")
        val l3 = liOf("l3", o1)
        assertEquals(1, ListOrderCalculator.getLiOrder(l1, listOf(o1, l1)))
        assertEquals(10, ListOrderCalculator.getLiOrder(l2, listOf(o1, l2)))
        assertEquals(11, ListOrderCalculator.getLiOrder(l3, listOf(o1, l3)))
    }

    // ------------------------------------------------------------
    // OL-U4：prescan 一致性（≥ 正式计数全程）+ 只读不消耗 + clear 重置
    // ------------------------------------------------------------

    @Test
    fun olU4_prescanConsistent_readOnly_andClearable() {
        val o1 = tag("o1", "ol")
        val l1 = liOf("l1", o1)
        val l2 = liOf("l2", o1, params = "value=10")
        val l3 = liOf("l3", o1)
        val contents = listOf(
            textPara(o1, l1), textPara(o1, l2), textPara(o1, l3)
        )

        ListOrderCalculator.prescan(contents)

        // prescan 最大序号 = 11（value=10 → 10、11）
        assertEquals(11, ListOrderCalculator.maxOrderOf(l2, listOf(o1, l2)))

        // prescan 不消耗正式计数器：getLiOrder 仍按原序从 1 起
        val orders = contents.map { p ->
            ListOrderCalculator.getLiOrder(liOf(p), p.annotations)
        }
        assertEquals(listOf(1, 10, 11), orders)
        // OL-U4 核心性质：正式计数全程任意返回值 ≤ prescan(max)
        assertTrue("prescan(max)=11 应 ≥ 全部正式计数 $orders", orders.all { it <= 11 })

        // clear 同时重置计数器与 prescan 结果
        ListOrderCalculator.clear()
        assertEquals(0, ListOrderCalculator.maxOrderOf(l2, listOf(o1, l2)))
        assertEquals(1, ListOrderCalculator.getLiOrder(l1, listOf(o1, l1)))
    }

    // ------------------------------------------------------------
    // OL-U5：序号缩进公式（四参 calcListIndent，plan §2-D8-3/4）
    //   base = min(level×max(0.75t,36), container/5)；column = labelWidth 钳 [0, 0.15×container]
    // ------------------------------------------------------------

    @Test
    fun olU5_indentFormula_withOrderColumn() {
        // base=2×36=72（cap 200 未触）+ column=83（< 150 未触）= 155
        assertEquals(155f, ListDotRenderer.calcListIndent(2, 48f, 1000f, 83f), 0.001f)
        // column 封顶：0.15×200=30 → 36+30=66
        assertEquals(66f, ListDotRenderer.calcListIndent(1, 48f, 200f, 500f), 0.001f)
        // 0 宽退化为旧签名值
        assertEquals(72f, ListDotRenderer.calcListIndent(2, 48f, 1000f, 0f), 0.001f)
        assertEquals(
            ListDotRenderer.calcListIndent(2, 48f, 1000f),
            ListDotRenderer.calcListIndent(2, 48f, 1000f, 0f), 0.001f
        )
    }

    // ------------------------------------------------------------
    // T1~T4：findOwnLi 选取规则（plan §5.1，嵌套 ol 序号扁平化修复回归，
    // docs/plans/2026-08-30-plan-nested-ordered-list-own-li-selection-fix.md）
    // fixture 链形状与诊断测试 NestedListChainDiagInstrumentedTest 真机输出同构：
    // 注解链 = [祖先链…, 自身 li]（DFS 先序，祖先 li 在前、自身 li 在链尾）
    // ------------------------------------------------------------

    @Test
    fun t1_findOwnLi_chainShapeMatrix() {
        val body = tag("bd", "body")
        val o1 = tag("o1", "ol")
        val l1 = liOf("l1", o1)
        val o2 = tag("o2", "ol", parent = l1.uuid)
        val l2 = liOf("l2", o2)

        // 嵌套子项链：祖先 li 在前、自身 li 在链尾 → 取链尾（修复前 firstOrNull 命中祖先 li）
        assertEquals(l2, ListOrderCalculator.findOwnLi(listOf(body, o1, l1, o2, l2)))
        // 单层列表：唯一 li 即自身
        assertEquals(l1, ListOrderCalculator.findOwnLi(listOf(body, o1, l1)))
        // li 内块级子元素段落（<li><p>…）：链尾 li = 包裹 li，序号归包裹 li
        val pTag = tag("p1", "p", parent = l1.uuid)
        assertEquals(l1, ListOrderCalculator.findOwnLi(listOf(o1, l1, pTag)))
        // 无 li / 空链 → null
        assertEquals(null, ListOrderCalculator.findOwnLi(listOf(body, o1)))
        assertEquals(null, ListOrderCalculator.findOwnLi(emptyList()))
    }

    @Test
    fun t2_nestedOl_realChain_orderSequence_andMaxes() {
        val body = tag("bd", "body")
        val o1 = tag("o1", "ol")
        val l1 = liOf("l1", o1)                       // 外层第 1 项
        val o2 = tag("o2", "ol", parent = l1.uuid)    // 内层 ol（嵌在 l1 内）
        val s1 = liOf("s1", o2)                       // 子项 1
        val s2 = liOf("s2", o2)                       // 子项 2
        val l2 = liOf("l2", o1)                       // 外层第 2 项

        // 段落注解链（同真实解析输出：同一祖先 li 实例复用于子项链）
        val main1 = textPara(body, o1, l1)
        val sub1 = textPara(body, o1, l1, o2, s1)
        val sub2 = textPara(body, o1, l1, o2, s2)
        val main2 = textPara(body, o1, l2)
        val contents = listOf(main1, sub1, sub2, main2)

        // prescan 与排版期同走 findOwnLi（OL-U4 同源性质）
        ListOrderCalculator.prescan(contents)
        val orders = contents.map { p ->
            ListOrderCalculator.getLiOrder(ListOrderCalculator.findOwnLi(p.annotations), p.annotations)
        }
        // 修复前（firstOrNull 命中祖先 li → 计数挂外层 ol）：1,2,3,4 扁平
        assertEquals("嵌套 ol 应按列表独立计数 1,(1,2),2", listOf(1, 1, 2, 2), orders)

        // maxOrder 按所属列表取值：外层 ol=2、内层 ol=2（修复前外层被推高到 4）
        assertEquals(2, ListOrderCalculator.maxOrderOf(l1, listOf(body, o1, l1)))
        assertEquals(2, ListOrderCalculator.maxOrderOf(s1, sub1.annotations))
    }

    @Test
    fun t3_valueAttribute_attributionToOwnLi() {
        val o1 = tag("o1", "ol")
        val l1 = liOf("l1", o1, params = "value=10")   // 外层项带 value=10
        val o2 = tag("o2", "ol", parent = l1.uuid)
        val s1 = liOf("s1", o2)                        // 子项自身无 value

        // 子项不得继承祖先 li 的 value：内层独立从 1 起计数
        // （修复前 firstOrNull 命中祖先 l1 → 误读其 value=10）
        val subChain = listOf(o1, l1, o2, s1)
        assertEquals("祖先 li 的 value 不得影响子项",
            1, ListOrderCalculator.getLiOrder(ListOrderCalculator.findOwnLi(subChain), subChain))

        // 自身 li 的 value 正常生效并延续（语义与 OL-U3 一致，经 findOwnLi 选取）
        val l2 = liOf("l2", o1)
        val l3 = liOf("l3", o1)
        val m1 = listOf(o1, l1)
        val m2 = listOf(o1, l2)
        val m3 = listOf(o1, l3)
        assertEquals(10, ListOrderCalculator.getLiOrder(ListOrderCalculator.findOwnLi(m1), m1))
        assertEquals(11, ListOrderCalculator.getLiOrder(ListOrderCalculator.findOwnLi(m2), m2))
        assertEquals(12, ListOrderCalculator.getLiOrder(ListOrderCalculator.findOwnLi(m3), m3))
    }

    @Test
    fun t4_ulParent_andOrphanLi_stayZero() {
        // ul 父级：非有序列表，恒 0（走形状符号）
        val u1 = tag("u1", "ul")
        val lu = liOf("lu", u1)
        val uChain = listOf(u1, lu)
        assertEquals(0, ListOrderCalculator.getLiOrder(ListOrderCalculator.findOwnLi(uChain), uChain))

        // 孤儿 li（parentUuid 空）：findOwnLi 仍选中，getLiOrder 兜底 0
        val orphan = tag("lo", "li")
        val oChain = listOf(orphan)
        assertEquals(orphan, ListOrderCalculator.findOwnLi(oChain))
        assertEquals(0, ListOrderCalculator.getLiOrder(ListOrderCalculator.findOwnLi(oChain), oChain))
    }
}
