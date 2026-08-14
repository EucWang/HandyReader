package com.wxn.base.util

import org.junit.Test
import org.junit.Assert.*
import com.wxn.base.util.LanguageDetector.isRtlChar

/**
 * RTL 码点判断单元测试（A1 Task）。
 *
 * 仅覆盖 [LanguageDetector.isRtlChar]——纯函数、无 Android 依赖，可在 JVM 单测环境运行。
 * 空串/纯空白由调用方（RTLSegmenter）处理，isRtlChar 不负责。
 */
class LanguageDetectorRtlTest {

    // ── 纯阿语串：首字码点 ──
    @Test
    fun isRtlChar_arabicLetter() {
        // Arabic letter 0x0627 (ALEF) → true
        assertTrue(isRtlChar(0x0627))
    }

    // ── 纯英文串 ──
    @Test
    fun isRtlChar_asciiLetter_isFalse() {
        // 'A'.code = 65
        assertEquals(false, isRtlChar('A'.code))
        // 'a'.code = 97
        assertEquals(false, isRtlChar('a'.code))
    }

    // ── 边缘码点：希伯来 / Urdu / Syriac ──
    @Test
    fun isRtlChar_hebrew_isTrue() {
        // Hebrew 0x05D0 (ALEF) → true
        assertTrue(isRtlChar(0x05D0))
    }

    @Test
    fun isRtlChar_urdu_isTrue() {
        // Urdu پ = 0x067E (Arabic block, PEH) → true
        assertTrue(isRtlChar(0x067E))
    }

    @Test
    fun isRtlChar_syriac_isTrue() {
        // Syriac 0x0710 (ALAPH) → true
        assertTrue(isRtlChar(0x0710))
    }

    // ── 边界：区间外码点必须返回 false ──
    @Test
    fun isRtlChar_outsideRanges_isFalse() {
        // 拉丁、CJK、数字、标点、控制字符均不应判为 RTL
        assertEquals(false, isRtlChar('0'.code))         // 数字
        assertEquals(false, isRtlChar(' '.code))         // 空格
        assertEquals(false, isRtlChar(0x4E2D))           // CJK 中
        assertEquals(false, isRtlChar(0x0000))           // NUL
        assertEquals(false, isRtlChar(0x058F))           // Hebrew 区间前一位（0x058F < 0x0590）
        assertEquals(false, isRtlChar(0x08A0 - 1))       // 0x089F，Arabic Extended-A 区间前一位
        assertEquals(false, isRtlChar(0x1E95F + 1))      // 0x1E960，Adlam 区间后一位
    }

    // ── 区间端点：含起始/终止码点 ──
    @Test
    fun isRtlChar_rangeBounds() {
        // Hebrew [0x0590..0x05FF] 端点
        assertTrue(isRtlChar(0x0590))
        assertTrue(isRtlChar(0x05FF))
        // Arabic [0x0600..0x06FF] 端点
        assertTrue(isRtlChar(0x0600))
        assertTrue(isRtlChar(0x06FF))
        // Adlam [0x1E900..0x1E95F] 端点
        assertTrue(isRtlChar(0x1E900))
        assertTrue(isRtlChar(0x1E95F))
    }
}
