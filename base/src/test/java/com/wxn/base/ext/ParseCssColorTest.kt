package com.wxn.base.ext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [parseCssColor] 纯逻辑单测：CSS 颜色字符串 → ARGB Int。
 *
 * 不依赖 Android 类（故可用纯 JVM junit，无需 Robolectric）。
 * 覆盖：hex（3/4/6/8 位）、rgb()/rgba()、hsl()/hsla()、命名色、非法输入。
 *
 * 对应 bug：epub 内联样式 `color=rgb(255, 0, 0)` 渲染期抛
 * `IllegalArgumentException: Unknown color`（[String.toColor] 旧实现仅委托
 * `androidx.core.graphics.toColorInt()`，不支持 CSS 函数格式）。
 */
class ParseCssColorTest {

    // ════════════════════════════════════════════════════════════════════
    // hex / 命名色：parseCssColor 刻意不处理，留给 toColorInt（见 toColor 回退）
    // ════════════════════════════════════════════════════════════════════

    @Test fun hex_returnsNull_delegatedToColorInt() = assertNull("#FF0000".parseCssColor())
    @Test fun named_returnsNull_delegatedToColorInt() = assertNull("red".parseCssColor())

    // ════════════════════════════════════════════════════════════════════
    // rgb() / rgba()  ← 本次 bug 修复目标
    // ════════════════════════════════════════════════════════════════════

    @Test fun rgb_spaces() = assertEquals(0xFFFF0000.toInt(), "rgb(255, 0, 0)".parseCssColor())
    @Test fun rgb_noSpaces() = assertEquals(0xFFFF0000.toInt(), "rgb(255,0,0)".parseCssColor())
    @Test fun rgb_extraSpaces() = assertEquals(0xFF00FF00.toInt(), "rgb( 0 , 255 , 0 )".parseCssColor())
    @Test fun rgb_decimal_truncated() = assertEquals(0xFFFF0000.toInt(), "rgb(255.9, 0, 0)".parseCssColor())

    @Test fun rgba_int_alpha() = assertEquals(0x80FF0000.toInt(), "rgba(255, 0, 0, 0.5)".parseCssColor())
    @Test fun rgba_alpha_1_opaque() = assertEquals(0xFFFF0000.toInt(), "rgba(255, 0, 0, 1)".parseCssColor())
    @Test fun rgba_alpha_0_transparent() = assertEquals(0x00FF0000.toInt(), "rgba(255, 0, 0, 0)".parseCssColor())

    // ════════════════════════════════════════════════════════════════════
    // hsl() / hsla()
    // ════════════════════════════════════════════════════════════════════

    @Test fun hsl_red() = assertEquals(0xFFFF0000.toInt(), "hsl(0, 100%, 50%)".parseCssColor())
    @Test fun hsl_green() = assertEquals(0xFF00FF00.toInt(), "hsl(120, 100%, 50%)".parseCssColor())
    @Test fun hsl_blue() = assertEquals(0xFF0000FF.toInt(), "hsl(240, 100%, 50%)".parseCssColor())
    @Test fun hsla_half_alpha() = assertEquals(0x80FF0000.toInt(), "hsla(0, 100%, 50%, 0.5)".parseCssColor())

    // ════════════════════════════════════════════════════════════════════
    // 命名色：parseCssColor 刻意不处理，留给 toColorInt（red/blue/transparent 等）
    // ════════════════════════════════════════════════════════════════════
    // 见上方 hex 区块，命名色同理由 toColor() 整体回归（instrumented test 覆盖）。

    // ════════════════════════════════════════════════════════════════════
    // 非法 / 边界
    // ════════════════════════════════════════════════════════════════════

    @Test fun empty_returnsNull() = assertNull("".parseCssColor())
    @Test fun blank_returnsNull() = assertNull("   ".parseCssColor())
    @Test fun garbage_returnsNull() = assertNull("not-a-color".parseCssColor())
    @Test fun rgb_wrongArgCount_returnsNull() = assertNull("rgb(255, 0)".parseCssColor())
    @Test fun rgb_nonNumeric_returnsNull() = assertNull("rgb(abc, 0, 0)".parseCssColor())
}
