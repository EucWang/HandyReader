package com.wxn.bookread.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag
import com.wxn.bookread.jni.SheenBidiNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented (设备/模拟器): dir 声明继承端到端链路测试。
 *
 * 方案: docs/plans/2026-08-27-plan-rtl-dir-inheritance.md §6 V2
 *
 * 链路: ReaderText.Text(annotations) → declaredBaseRtl（Kotlin 解析）
 *       → RTLSegmenter.segment(line, declaredBaseRtl)
 *         （D 方向解耦，docs/plans/2026-08-29-plan-u5-mixed-base-ltr-line-order-fix.md §4.2：
 *          排版基调 baseRtl 恒 = SheenBidi 首强，declaredRtl 不再强制基级；
 *          declaredRtl 唯一消费 = 锚点方向 anchorBaseRtl = declaredRtl ?: baseRtl）
 *       → SegmentResult.baseRtl（排版链路真相源）/ anchorBaseRtl（列表锚点方向）。
 *
 * 与 RTLSegmenterInstrumentedTest 的分工：B6-B8 测 segment 的 declaredRtl 参数契约，
 * 本测试测「annotations 解析 → segment 融合」的完整链（BookHelper.disposeContent 的同构形态）。
 *
 * 运行(需连接设备，MIUI 走 am instrument 而非 gradle UTP):
 *   adb shell am instrument -w -e class com.wxn.bookread.provider.DirInheritChainInstrumentedTest \
 *     com.wxn.bookread.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class DirInheritChainInstrumentedTest {

    @Before
    fun setUp() {
        assertTrue(
            "SheenBidiNative.available=false —— .so 未打进测试 APK（ABI/打包问题），本测试失去意义",
            SheenBidiNative.available
        )
    }

    private fun tag(
        name: String,
        params: String,
        start: Int = 0,
        end: Int = 0
    ): TextTag = TextTag(
        uuid = name + start + end + params.hashCode(),
        name = name, start = start, end = end, params = params
    )

    /** C1:div[dir=rtl] 声明链 → 拉丁文本排版基调仍首强 LTR，声明只达列表锚点（D 方向解耦） */
    @Test
    fun C1_divRtlChain_declaredReachesAnchorOnly() {
        val text = ReaderText.Text(
            line = "Hello world",
            annotations = listOf(
                tag("div", "dir=rtl&lang=ar"),
                tag("ul", ""),
                tag("li", "")
            )
        )

        assertEquals("C1 失败: div[dir=rtl] 祖先链应解析出显式 RTL", true, text.declaredBaseRtl)

        val seg = RTLSegmenter.segment(text.line, text.declaredBaseRtl)
        assertTrue("C1 失败: 嗅探路径此文本为 LTR，D 下声明不再强制基级 → baseRtl 应为 false",
            !seg.baseRtl)
        assertTrue("C1 失败: 显式声明应传导到锚点方向 anchorBaseRtl（列表圆点右侧）",
            seg.anchorBaseRtl)
        println("C1 ★ 通过: div[dir=rtl] → declaredBaseRtl=true → 锚点 RTL；排版基调恒首强（D 方向解耦链路）")
    }

    /** C2:dir=auto 阻断继承 → 回落嗅探（拉丁首强 → LTR） */
    @Test
    fun C2_autoBlocks_fallsBackToSniffing() {
        val text = ReaderText.Text(
            line = "Hello world",
            annotations = listOf(
                tag("div", "dir=rtl"),
                tag("li", "dir=auto")
            )
        )

        assertNull("C2 失败: dir=auto 应阻断向外继承，declaredBaseRtl=null", text.declaredBaseRtl)

        val seg = RTLSegmenter.segment(text.line, text.declaredBaseRtl)
        assertTrue("C2 失败: 无声明回落首强嗅探，拉丁首强应 baseRtl=false", !seg.baseRtl)
        assertEquals("C2 失败: 无声明时 anchorBaseRtl 应恒等于 baseRtl",
            seg.baseRtl, seg.anchorBaseRtl)
        println("C2 ★ 通过: dir=auto 阻断继承，回落嗅探（A5 场景2 的行为地基）")
    }

    /** C3:同标签内 内联 style > CSS 规则（CSS 级联）；CSS 规则 > HTML dir */
    @Test
    fun C3_cssCascade_inlineStyleBeatsRule_ruleBeatsDir() {
        // a) style direction:rtl 与 CSS 规则 direction=ltr 并存 → inline 胜 → true
        val a = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("p", "direction=ltr&style=direction:rtl"))
        )
        assertEquals("C3a 失败: 内联 style 应胜 CSS 规则", true, a.declaredBaseRtl)

        // b) CSS 规则 direction=rtl 与 HTML dir=ltr 并存 → 规则胜 presentation hint → true
        val b = ReaderText.Text(
            line = "Hello",
            annotations = listOf(tag("p", "dir=ltr&direction=rtl"))
        )
        assertEquals("C3b 失败: CSS 规则应胜 HTML dir", true, b.declaredBaseRtl)

        // c) 融合到 segment：b 的声明链（true）传导到锚点方向；排版基调仍首强（"Hello" → false）
        val seg = RTLSegmenter.segment(b.line, b.declaredBaseRtl)
        assertTrue("C3c 失败: CSS 规则声明的 RTL 应传导到锚点方向 anchorBaseRtl", seg.anchorBaseRtl)
        assertTrue("C3c 失败: 排版基调恒首强（D）→ baseRtl 应为 false", !seg.baseRtl)
        println("C3 ★ 通过: style > CSS 规则 > HTML dir 三级级联契约正确（声明达锚点，基调恒首强）")
    }

    /** C4:虚拟 __root__ 标签（C-C1 注入载体，start=end=0）→ html/body 级声明可达段落 */
    @Test
    fun C4_virtualRootTag_rootDeclarationReachesParagraph() {
        val text = ReaderText.Text(
            line = "Chapter 1",
            annotations = listOf(
                tag("__root__", "dir=rtl", start = 0, end = 0),
                tag("body", ""),
                tag("p", "")
            )
        )

        assertEquals("C4 失败: __root__ 虚拟标签应作为最浅祖先参与声明解析", true, text.declaredBaseRtl)

        val seg = RTLSegmenter.segment(text.line, text.declaredBaseRtl)
        assertTrue("C4 失败: html 级声明应传导到锚点方向 anchorBaseRtl",
            seg.anchorBaseRtl)
        assertTrue("C4 失败: 排版基调恒首强（拉丁文本 → LTR，纯阿语书首强=声明不受影响）",
            !seg.baseRtl)
        println("C4 ★ 通过: __root__ 注入载体 → 声明达锚点；排版基调恒首强（D 方向解耦）")
    }

    /** C5:含 __root__ 标签的段落 parseTextCss() 产出与不含时一致（C-C1 无干扰证明） */
    @Test
    fun C5_virtualRootTag_parseTextCssUnaffected() {
        fun build(withRoot: Boolean): ReaderText.Text {
            val tags = buildList {
                if (withRoot) add(tag("__root__", "dir=rtl", start = 0, end = 0))
                add(tag("body", ""))
                add(tag("p", "class=chapter&font-size=1.2em", start = 0, end = 9))
            }
            return ReaderText.Text(line = "Chapter 1", annotations = tags)
        }

        val withRoot = build(true)
        val withoutRoot = build(false)
        withRoot.parseTextCss()
        withoutRoot.parseTextCss()

        // CssUnit 未覆写 equals（data class 等值退化为引用比较），用 toString 投影比较全字段
        assertEquals(
            "C5 失败: __root__ 标签不应改变 parseTextCss 的 textCssInfo 产出",
            withoutRoot.textCssInfo.toString(), withRoot.textCssInfo.toString()
        )
        assertEquals(
            "C5 失败: __root__ 标签不应改变 parseTextCss 的 inlineStyles 产出",
            withoutRoot.inlineStyles, withRoot.inlineStyles
        )
        println("C5 ★ 通过: __root__ 注入对既有 CSS 解析零干扰")
    }
}
