package com.wxn.base.bean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * declaredBaseRtl 计算属性单测（MIX-1 修复：W3C「显式声明 > 首强嗅探」）。
 *
 * 方案: docs/plans/2026-08-27-plan-rtl-dir-inheritance.md §6 V1
 *
 * 断言对象 = ReaderText.Text(line, annotations).declaredBaseRtl，纯函数、无 JNI 依赖。
 * annotations 顺序契约：文档序（浅→深）——用例 D4 是该顺序假设的护栏
 * （C++ tags 先序 push + get_fathers_tags 保序输出 + JNI 透传保序三级保证，顺序颠倒时 D4 失败）。
 */
class ReaderTextDeclaredBaseRtlTest {

    private fun tag(
        name: String,
        params: String,
        start: Int = 0,
        end: Int = 0
    ): TextTag = TextTag(uuid = name + start + end, name = name, start = start, end = end, params = params)

    /** D1:空 annotations（TXT/MD 等无标签来源）→ 无声明 */
    @Test
    fun D1_emptyAnnotations_null() {
        val text = ReaderText.Text(line = "纯文本", annotations = emptyList())
        assertNull(text.declaredBaseRtl)
    }

    /** D2:div[dir=rtl] 祖先 + li 无声明 → true（MIX-1 样书第六章的真实形态） */
    @Test
    fun D2_divRtlAncestor_liNoDeclaration_true() {
        val text = ReaderText.Text(
            line = "Hello world",
            annotations = listOf(tag("div", "dir=rtl&lang=ar"), tag("ul", ""), tag("li", ""))
        )
        assertEquals(true, text.declaredBaseRtl)
    }

    /** D3:div[dir=rtl] > div(无) > li(无) 嵌套穿透 → true */
    @Test
    fun D3_nestedNoDeclarationBlocks_pierceThrough_true() {
        val text = ReaderText.Text(
            line = "مرحبا",
            annotations = listOf(
                tag("div", "dir=rtl"),
                tag("div", "class=wrapper"),
                tag("li", "")
            )
        )
        assertEquals(true, text.declaredBaseRtl)
    }

    /** D4:div[dir=rtl] 外层 + p[dir=ltr] 内层 → false（最近声明胜 + 浅→深顺序护栏） */
    @Test
    fun D4_nearestDeclarationWins_false() {
        val text = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("div", "dir=rtl"), tag("p", "dir=ltr"))
        )
        assertEquals(false, text.declaredBaseRtl)
    }

    /** D5:span[dir=rtl] 内联标签 → 白名单外忽略，向外找 → null（isolate 语义不升格基调） */
    @Test
    fun D5_inlineSpanDir_ignored_null() {
        val text = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("span", "dir=rtl", start = 0, end = 5))
        )
        assertNull(text.declaredBaseRtl)
    }

    /** D6:dir="RTL" 大写值 → true（值大小写不敏感） */
    @Test
    fun D6_uppercaseValue_true() {
        val text = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("p", "dir=RTL"))
        )
        assertEquals(true, text.declaredBaseRtl)
    }

    /** D7:tag.name="DIV" 大写标签名 → 匹配（name 小写化比较） */
    @Test
    fun D7_uppercaseTagName_matches() {
        val text = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("DIV", "dir=rtl"))
        )
        assertEquals(true, text.declaredBaseRtl)
    }

    /** D8:direction=rtl（CSS 规则）与 dir=ltr（HTML）同标签 → true（CSS 规则 > presentation hint） */
    @Test
    fun D8_cssRule_beatsHtmlDir_true() {
        val text = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("div", "dir=ltr&direction=rtl"))
        )
        assertEquals(true, text.declaredBaseRtl)
    }

    /** D9:dir=auto 内层 + dir=rtl 外层 → null（auto 显式选择嗅探并阻断继续向外找） */
    @Test
    fun D9_autoBlocksInheritance_null() {
        val text = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("div", "dir=rtl"), tag("li", "dir=auto"))
        )
        assertNull(text.declaredBaseRtl)
    }

    /** D10:dir=lro（HTML4 废弃值）→ 该标签视同无声明，继续向外找 → 取外层 rtl */
    @Test
    fun D10_unknownValue_skipped_outerWins() {
        val text = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("div", "dir=rtl"), tag("p", "dir=lro"))
        )
        assertEquals(true, text.declaredBaseRtl)
    }

    /** D11:params 混合键 class=x&dir=rtl&lang=ar → true（paramsPairs 解析不受干扰） */
    @Test
    fun D11_mixedParamsKeys_true() {
        val text = ReaderText.Text(
            line = "مرحبا",
            annotations = listOf(tag("div", "class=x&dir=rtl&lang=ar"))
        )
        assertEquals(true, text.declaredBaseRtl)
    }

    /** D12:重复键 direction=ltr&direction=rtl（CSS last-wins 合并形态）→ true */
    @Test
    fun D12_duplicateDirectionKeys_lastWins_true() {
        val text = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("div", "direction=ltr&direction=rtl"))
        )
        assertEquals(true, text.declaredBaseRtl)
    }

    /**
     * D13:内联 style 声明。
     *  a) style="direction:rtl;color:red" → true（内联 style 解析）；
     *  b) 同标签 style direction:rtl 与 CSS 规则 direction=ltr 并存 → 取 style（inline > 规则）；
     *  c) style 内无 direction → 落到 CSS 规则/HTML dir 链。
     */
    @Test
    fun D13_inlineStyleParsing() {
        // a) 内联 style 命中
        assertEquals(true, ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("p", "style=direction:rtl;color:red"))
        ).declaredBaseRtl)

        // b) inline > CSS 规则（同标签内）
        assertEquals(true, ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("p", "direction=ltr&style=direction:rtl"))
        ).declaredBaseRtl)

        // c) style 无 direction 声明 → 落到后续 CSS 规则链（direction=rtl 生效）
        assertEquals(true, ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("p", "style=color:red&direction=rtl"))
        ).declaredBaseRtl)
    }

    /** D14:虚拟根标签 name="__root__", params="dir=rtl"（start=end=0 区间形态）→ true（C-C1 注入载体） */
    @Test
    fun D14_virtualRootTag_true() {
        val text = ReaderText.Text(
            line = "Hello",
            annotations = listOf(
                tag("__root__", "dir=rtl", start = 0, end = 0),
                tag("body", ""),
                tag("p", "")
            )
        )
        assertEquals(true, text.declaredBaseRtl)
    }

    /** D15:style="text-align:center"（style 内无 direction）→ 不误判，继续走后续链 → 此处无声明 → null */
    @Test
    fun D15_styleWithoutDirection_fallsThrough_null() {
        val text = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("p", "style=text-align:center"))
        )
        assertNull(text.declaredBaseRtl)
    }
}
