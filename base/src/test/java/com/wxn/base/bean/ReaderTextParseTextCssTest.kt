package com.wxn.base.bean

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * InlineStyle 统一数据模型 + parseTextCss 子区间样式提取单测。
 *
 * 覆盖：
 * - Phase 1 回归（T1–T5d）：font-size(em) 收集、整段守卫、clamp、px 跳过、边界
 * - Phase 2 新增（R1–R6）：resolve 属性级 lastOrNull 算法、color 收集、CSS 层叠/继承
 *
 * 关键约束：InlineCssProps 未设置的属性必须为 null（否则破坏 resolve 的 CSS 继承语义）。
 */
class ReaderTextParseTextCssTest {

    // ════════════════════════════════════════════════════════════════════
    // Phase 1 回归：font-size(em) 收集（验证重构未破坏字号功能）
    // ════════════════════════════════════════════════════════════════════

    /**
     * T1:整段 font-size=1.2em → 应被整段守卫捕获,进入 textCssInfo.fontSize,
     * 不进入 inlineStyles(避免整段字号被双轨重复收集)
     */
    @Test
    fun T1_wholeParagraphFontsizeEm_goesToTextCssInfo_notInline() {
        val text = ReaderText.Text(
            line = "正文内容",
            annotations = listOf(
                TextTag(uuid = "t1", name = "p", start = 0, end = 4, params = "font-size=1.2em")
            )
        )

        text.parseTextCss()

        // 整段守卫命中 → 走 textCssInfo
        assertEquals(1.2f, text.textCssInfo.fontSize.value, 0.0001f)
        assertTrue("整段字号不应进入 inlineStyles", text.inlineStyles?.isEmpty() == true)
    }

    /**
     * T2:子区间 font-size=1.5em → 整段守卫未命中,进入 inlineStyles,fontScale=1.5
     */
    @Test
    fun T2_subIntervalFontsizeEm_collectedIntoInlineStyles() {
        val text = ReaderText.Text(
            line = "正文大字号正文",
            annotations = listOf(
                TextTag(
                    uuid = "t2", name = "span",
                    start = 2, end = 5,   // 子区间 "大字号"
                    params = "class=big&font-size=1.5em"
                )
            )
        )

        text.parseTextCss()

        // 整段守卫未命中 → textCssInfo.fontSize 保持默认 1em
        assertEquals(1f, text.textCssInfo.fontSize.value, 0.0001f)
        // inlineStyles 含一条,props.fontScale=1.5,color=null
        val styles = text.inlineStyles
        assertEquals(1, styles?.size)
        val first = styles!![0]
        assertEquals(2, first.start)
        assertEquals(5, first.end)
        assertEquals(1.5f, first.props.fontScale!!, 0.0001f)
        assertNull("仅设字号时 color 必须为 null(保证 resolve 继承语义)", first.props.color)
    }

    /**
     * T3:子区间 font-size=10em → clamp 到 5.0(防御损坏 EPUB)
     */
    @Test
    fun T3_overlargeEm_clampedToMax() {
        val text = ReaderText.Text(
            line = "正文大字正文",
            annotations = listOf(
                TextTag(
                    uuid = "t3", name = "span",
                    start = 2, end = 4,
                    params = "font-size=10em"
                )
            )
        )

        text.parseTextCss()

        val first = text.inlineStyles?.firstOrNull()
        assertEquals("10em 应 clamp 到 5.0", 5.0f, first?.props?.fontScale ?: 0f, 0.0001f)
    }

    /**
     * T3b:子区间 font-size=0.1em → clamp 到 0.5(防御下界)
     */
    @Test
    fun T3b_tinyEm_clampedToMin() {
        val text = ReaderText.Text(
            line = "正文小字正文",
            annotations = listOf(
                TextTag(
                    uuid = "t3b", name = "span",
                    start = 2, end = 4,
                    params = "font-size=0.1em"
                )
            )
        )

        text.parseTextCss()

        val first = text.inlineStyles?.firstOrNull()
        assertEquals("0.1em 应 clamp 到 0.5", 0.5f, first?.props?.fontScale ?: 0f, 0.0001f)
    }

    /**
     * T4:子区间 font-size=24px → 跳过(仅处理 em;px 不进入 inlineStyles)
     */
    @Test
    fun T4_pxUnit_skipped() {
        val text = ReaderText.Text(
            line = "正文大字正文",
            annotations = listOf(
                TextTag(
                    uuid = "t4", name = "span",
                    start = 2, end = 4,
                    params = "font-size=24px"
                )
            )
        )

        text.parseTextCss()

        assertTrue("px 单位应跳过,inlineStyles 必须为空",
            text.inlineStyles?.isEmpty() == true)
    }

    /**
     * T5:反向区间(start >= end)→ parseTextCss 不崩溃(F1 不做语义过滤,消费端处理)
     */
    @Test
    fun T5_reversedInterval_doesNotCrash() {
        val text = ReaderText.Text(
            line = "abcdef",
            annotations = listOf(
                TextTag(
                    uuid = "t5", name = "span",
                    start = 4, end = 2,   // 反向区间
                    params = "font-size=1.5em"
                )
            )
        )

        text.parseTextCss()  // 不应崩溃
    }

    /**
     * T5b:多个子区间 → 按原始顺序收集(不排序),匹配 C++ DOM 遍历序
     */
    @Test
    fun T5b_multipleIntervals_preservedInOrder() {
        val text = ReaderText.Text(
            line = "ABCDEFGHIJ",
            annotations = listOf(
                TextTag(uuid = "a", name = "span", start = 1, end = 3, params = "font-size=1.5em"),
                TextTag(uuid = "b", name = "span", start = 5, end = 7, params = "font-size=0.8em"),
                TextTag(uuid = "c", name = "span", start = 8, end = 9, params = "font-size=2em")
            )
        )

        text.parseTextCss()

        val styles = text.inlineStyles
        assertEquals(3, styles?.size)
        assertEquals(1, styles!![0].start)   // 按原始顺序
        assertEquals(5, styles[1].start)
        assertEquals(8, styles[2].start)
    }

    /**
     * T5c:整段守卫边界 — start=0, end=line.length-1 也算整段(原守卫 `>=`)
     */
    @Test
    fun T5c_wholeParagraphBoundary_endIsLengthMinusOne_stillTreatedAsWhole() {
        val line = "正文"   // length=2
        val text = ReaderText.Text(
            line = line,
            annotations = listOf(
                TextTag(
                    uuid = "x", name = "p",
                    start = 0, end = 1,   // = line.length - 1 = 1,整段守卫 `>=` 命中
                    params = "font-size=1.5em"
                )
            )
        )

        text.parseTextCss()

        // 应被整段守卫捕获 → textCssInfo.fontSize
        assertEquals(1.5f, text.textCssInfo.fontSize.value, 0.0001f)
        assertTrue("整段守卫边界(end == length-1)应算整段",
            text.inlineStyles?.isEmpty() == true)
    }

    /**
     * T5d:空 annotations(TXT/MD/FB2)→ inlineStyles 为 emptyList,不崩溃
     */
    @Test
    fun T5d_emptyAnnotations_emptyInlineStyles() {
        val text = ReaderText.Text(line = "纯文本无样式", annotations = emptyList())

        text.parseTextCss()

        assertTrue(text.inlineStyles?.isEmpty() == true)
    }

    // ════════════════════════════════════════════════════════════════════
    // Phase 2 新增:resolve 算法 + color 收集
    // ════════════════════════════════════════════════════════════════════

    /**
     * R1:resolve 空/null 列表 → 返回全 null 的默认 InlineCssProps
     *     (90%+ 段落走此路径,O(1) 早返回)
     */
    @Test
    fun R1_resolve_emptyList_returnsDefaultProps() {
        val fromNull = InlineStyle.resolve(null, offset = 0)
        val fromEmpty = InlineStyle.resolve(emptyList(), offset = 5)

        assertNull(fromNull.fontScale)
        assertNull(fromNull.color)
        assertNull(fromEmpty.fontScale)
        assertNull(fromEmpty.color)
    }

    /**
     * R2:resolve 单区间命中 → 返回该区间 props;offset 未命中 → 全 null
     */
    @Test
    fun R2_resolve_singleInterval_hitAndMiss() {
        val styles = listOf(
            InlineStyle(2, 5, InlineCssProps(fontScale = 1.5f, color = "#ff0000"))
        )

        // offset=3 命中 [2,5)
        val hit = InlineStyle.resolve(styles, offset = 3)
        assertEquals(1.5f, hit.fontScale!!, 0.0001f)
        assertEquals("#ff0000", hit.color)

        // offset=5 不命中([2,5) 不含 5)
        val miss = InlineStyle.resolve(styles, offset = 5)
        assertNull(miss.fontScale)
        assertNull(miss.color)
    }

    /**
     * R3:同属性嵌套(内层覆盖外层)— CSS 层叠核心场景
     *     外层 [0,10) color=#333333,内层 [4,7) color=#ffffff
     *     - offset=5(内层)→ #ffffff ✓
     *     - offset=1(仅外层)→ #333333 ✓
     *     - offset=8(仅外层)→ #333333 ✓
     *
     *     依赖 C++ 深度优先遍历产出顺序:外层先入列、内层后入列,
     *     正序遍历 + 非空覆盖 → 内层最后覆盖外层。
     */
    @Test
    fun R3_resolve_samePropertyNested_innerOverridesOuter() {
        val styles = listOf(
            InlineStyle(0, 10, InlineCssProps(color = "#333333")),    // 外层先入列
            InlineStyle(4, 7, InlineCssProps(color = "#ffffff"))      // 内层后入列
        )

        assertEquals("#ffffff", InlineStyle.resolve(styles, 5).color)   // 内层
        assertEquals("#333333", InlineStyle.resolve(styles, 1).color)   // 仅外层
        assertEquals("#333333", InlineStyle.resolve(styles, 8).color)   // 仅外层
    }

    /**
     * R4:不同属性错位重叠 — A 设字号、B 设颜色,区间重叠
     *     A [0,10) fontScale=1.5,B [4,7) color=#ff0000
     *     offset=5(同时在 A 和 B)→ fontScale 取 A、color 取 B,各自独立
     *
     *     这是属性级 lastOrNull 相对"整个对象 lastOrNull"的关键优势:
     *     若用对象级 lastOrNull,offset=5 只会返回 B(fontScale=null),丢失 A 的字号。
     */
    @Test
    fun R4_resolve_differentPropertyOverlap_eachIndependent() {
        val styles = listOf(
            InlineStyle(0, 10, InlineCssProps(fontScale = 1.5f)),      // A:仅字号
            InlineStyle(4, 7, InlineCssProps(color = "#ff0000"))       // B:仅颜色
        )

        val resolved = InlineStyle.resolve(styles, 5)
        assertEquals("A 的字号应保留", 1.5f, resolved.fontScale!!, 0.0001f)
        assertEquals("B 的颜色应生效", "#ff0000", resolved.color)

        // offset=1(仅 A)→ 有字号无颜色
        val onlyA = InlineStyle.resolve(styles, 1)
        assertEquals(1.5f, onlyA.fontScale!!, 0.0001f)
        assertNull(onlyA.color)
    }

    /**
     * R5:CSS 继承 — 外层设 color,内层未设 color(但设了 fontScale)时不覆盖外层 color
     *     外层 [0,10) color=#333333,内层 [4,7) fontScale=1.5(未设 color)
     *     offset=5(内层)→ color 仍为外层 #333333,fontScale=1.5
     *
     *     这验证了"未设属性必须为 null"的硬约束:
     *     内层 props.color==null → resolve 不覆盖,保留外层值。
     */
    @Test
    fun R5_resolve_cssInheritance_unsetPropertyDoesNotClobberOuter() {
        val styles = listOf(
            InlineStyle(0, 10, InlineCssProps(color = "#333333")),           // 外层:仅 color
            InlineStyle(4, 7, InlineCssProps(fontScale = 1.5f))              // 内层:仅 fontScale
        )

        val resolved = InlineStyle.resolve(styles, 5)
        assertEquals("内层未设 color,应继承外层", "#333333", resolved.color)
        assertEquals("内层 fontScale 生效", 1.5f, resolved.fontScale!!, 0.0001f)
    }

    /**
     * R6:parseTextCss color 收集 — 子区间 `<span style="color:red">` → inlineStyles 含 color
     *     同时验证:同一段中 color-only tag 和 fontScale-only tag 各自独立收集为两条 InlineStyle
     */
    @Test
    fun R6_parseTextCss_colorOnly_andMixedCollected() {
        val text = ReaderText.Text(
            line = "红字正常大字正常",
            //        0 1 2 3 4 5 6 7
            //        红 字 正 常 大 字 正 常
            annotations = listOf(
                TextTag(uuid = "c", name = "span", start = 0, end = 2, params = "color=#ff0000"),
                TextTag(uuid = "s", name = "span", start = 4, end = 6, params = "font-size=1.5em")
            )
        )

        text.parseTextCss()

        val styles = text.inlineStyles
        assertEquals("color-only 和 fontScale-only 应各收集一条", 2, styles?.size)

        val colorStyle = styles!!.first { it.start == 0 }
        assertEquals("#ff0000", colorStyle.props.color)
        assertNull("color-only tag 的 fontScale 必须为 null", colorStyle.props.fontScale)

        val scaleStyle = styles.first { it.start == 4 }
        assertEquals(1.5f, scaleStyle.props.fontScale!!, 0.0001f)
        assertNull("fontScale-only tag 的 color 必须为 null", scaleStyle.props.color)
    }
}
