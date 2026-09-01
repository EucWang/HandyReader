package com.wxn.bookread.ext

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E8 逐字绘制安全码点白名单边界表（方案 §5.1）。
 *
 * 锁定 `Int.isPerCharDrawSafeCode()` 的三段语义：
 *  - 排除清单（格式/不可见/组合码点）→ false，且优先于白名单区间命中；
 *  - 白名单区间（ASCII/拉丁/希腊/西里尔/常用标点/上下标/货币/字母符号/罗马数字/带圈数字/
 *    几何符号/astral CJK/全角）→ true；
 *  - 缺省（未列出：泰语/天城文/希伯来/阿拉伯/emoji 等）→ false（保守整组整形）。
 *
 * 运行：`gradlew :bookread:testDebugUnitTest --tests "*PerCharDrawSafeCodeTest*"`
 */
class PerCharDrawSafeCodeTest {

    private fun assertSafe(vararg cps: Int) = cps.forEach { cp ->
        assertTrue("0x%04X 应为逐字安全（true）".format(cp), cp.isPerCharDrawSafeCode())
    }

    private fun assertUnsafe(vararg cps: Int) = cps.forEach { cp ->
        assertFalse("0x%04X 应整组整形（false）".format(cp), cp.isPerCharDrawSafeCode())
    }

    // —— 控制符 / ASCII ——
    @Test
    fun controlChars_unsafe_ascii_safe() {
        assertUnsafe(0x001F, 0x007F)
        assertSafe(0x0020, 0x0041, 0x007E)
    }

    // —— Latin-1：软连字符排除，其余预组合安全 ——
    @Test
    fun latin1_softHyphenExcluded_restSafe() {
        assertUnsafe(0x00AD)
        assertSafe(0x00A9, 0x00C0, 0x024B)
    }

    // —— 组合附加符 / 希腊（含二审 R2-3 的 0x0385）——
    @Test
    fun greek_combiningExcluded_precomposedSafe() {
        assertUnsafe(0x0300, 0x0385)
        assertSafe(0x0370, 0x03B1)
    }

    // —— 西里尔：组合重音排除，字母安全 ——
    @Test
    fun cyrillic_combiningExcluded_lettersSafe() {
        assertUnsafe(0x0483, 0x0489)
        assertSafe(0x048A, 0x0416)
    }

    // —— 未列出脚本：缺省保守整组整形 ——
    @Test
    fun unlistedScripts_defaultConservative() {
        assertUnsafe(
            0x05D0,   // 希伯来
            0x0600, 0x0627,   // 阿拉伯
            0x0E01,   // 泰语
            0x0905,   // 天城文
            0x1E900,  // Adlam
            0x1F600   // emoji
        )
    }

    // —— 组合 Jamo / ZW 系 / 双向控制 / 隐式格式 ——
    @Test
    fun formatAndCombining_excluded() {
        assertUnsafe(0x1100, 0x11FF)
        assertUnsafe(0x200B, 0x200D, 0x200E)
        assertUnsafe(0x202A, 0x2066)
        assertUnsafe(0x2060, 0x2061, 0x2065, 0x206F)
    }

    // —— 常用标点 / 上下标 / 字母符号 / 罗马数字（一审 W-2 增补区间）——
    @Test
    fun punctuationSuperscriptLetterlikeRoman_safe() {
        assertSafe(0x2005, 0x2010, 0x201C)
        assertSafe(0x2074)
        assertSafe(0x2122)
        assertSafe(0x2160, 0x2174)
    }

    // —— 假名组合浊点（一审 W-4 增补排除）——
    @Test
    fun kanaVoicing_excluded() {
        assertUnsafe(0x3099, 0x309A)
    }

    // —— CJK 及邻近符号 ——
    @Test
    fun cjkAndEnclosed_safe() {
        assertSafe(0x2460, 0x2467)          // 带圈数字 ①⑦
        assertSafe(0x25A0, 0x2605)          // ■★
        assertSafe(0x2E80, 0x3042, 0x4E2D, 0x9FA5)  // 部首/假名/汉字
        assertSafe(0xAC00, 0xD7A3)          // 谚文音节
        assertSafe(0xA960)                  // 谚文扩展 A
    }

    // —— 代理对残留不安全；astral CJK 安全 ——
    @Test
    fun surrogateUnsafe_astralCjkSafe() {
        assertUnsafe(0xD800)
        assertSafe(0x20000, 0x2A6DF, 0x2FA1D)
    }

    // —— 全角区 ——
    @Test
    fun fullWidth_safe() {
        assertSafe(0xFF01, 0xFF5E, 0xFF64)  // 全角！～｜
        assertSafe(0xFF66, 0xFF9D)          // 半角片假名（isCjkCode）
        assertSafe(0xFFE0, 0xFFEE)          // 全角￥￠
    }

    // —— astral 码点走 Character.charCount 语义（补两个 BMP 外端点）——
    @Test
    fun astralBoundaries() {
        assertSafe(0x20000, 0x2FA1F)
        assertUnsafe(0x2FA20, 0x1F000)
    }
}
