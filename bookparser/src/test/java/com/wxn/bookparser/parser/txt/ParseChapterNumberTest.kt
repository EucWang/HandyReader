package com.wxn.bookparser.parser.txt

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [parseChapterNumber] 单测。
 *
 * 覆盖：
 *   - ASCII 数字（所有语种）
 *   - 罗马数字（IVXLCDM，含 D/M 补全）
 *   - 中文数字（1..99999，复用 chineseToInt 反查）
 *   - 边界：超出范围 / 0 / 混合数字 / 非数字
 */
class ParseChapterNumberTest {

    // ── ASCII 数字 ──

    @Test
    fun parse_ascii_single() {
        assertEquals(3, parseChapterNumber("第3章"))
        assertEquals(1, parseChapterNumber("Chapter 1"))
    }

    @Test
    fun parse_ascii_multi() {
        assertEquals(12, parseChapterNumber("Chapter 12"))
        assertEquals(12345, parseChapterNumber("第12345章"))
    }

    // ── 罗马数字（含 D/M 补全）──

    @Test
    fun parse_roman_basic() {
        assertEquals(4, parseChapterNumber("Chapter IV"))
        assertEquals(9, parseChapterNumber("Chapter IX"))
    }

    @Test
    fun parse_roman_withD() {
        // D=500，原 matchEnRe 漏 D，v4 补全
        assertEquals(500, parseChapterNumber("Chapter D"))
        assertEquals(400, parseChapterNumber("Chapter CD"))
    }

    @Test
    fun parse_roman_withM() {
        // M=1000，原 matchEnRe 漏 M，v4 补全
        assertEquals(1000, parseChapterNumber("Chapter M"))
        assertEquals(2024, parseChapterNumber("Chapter MMXXIV"))
    }

    // ── 中文数字 ──

    @Test
    fun parse_cn_singleDigit() {
        assertEquals(1, parseChapterNumber("第一章"))
        assertEquals(9, parseChapterNumber("第九章"))
    }

    @Test
    fun parse_cn_ten() {
        assertEquals(10, parseChapterNumber("第十章"))
        assertEquals(19, parseChapterNumber("第十九章"))
        assertEquals(20, parseChapterNumber("第二十章"))
    }

    @Test
    fun parse_cn_hundred() {
        assertEquals(100, parseChapterNumber("第一百章"))
        assertEquals(105, parseChapterNumber("第一百零五章"))
        assertEquals(234, parseChapterNumber("第二百三十四章"))
    }

    @Test
    fun parse_cn_thousand() {
        assertEquals(1001, parseChapterNumber("第一千零一章"))
        assertEquals(9999, parseChapterNumber("第九千九百九十九章"))
    }

    @Test
    fun parse_cn_wan() {
        assertEquals(10000, parseChapterNumber("第一万章"))
        assertEquals(99999, parseChapterNumber("第九万九千九百九十九章"))
    }

    // ── 边界 / 失败 ──

    @Test
    fun parse_overflow_returnsNull() {
        assertEquals(null, parseChapterNumber("第十万章"))     // 100000 > 99999
        assertEquals(null, parseChapterNumber("第一百万章"))    // 1000000
    }

    @Test
    fun parse_zero_returnsNull() {
        // P1：范围 1..99999，第零章不在范围内（反查循环 1..99999 不会到 0）
        assertEquals(null, parseChapterNumber("第零章"))
    }

    @Test
    fun parse_specialWords_returnsNull() {
        // 特殊词无数字段
        assertEquals(null, parseChapterNumber("序章"))
        assertEquals(null, parseChapterNumber("楔子"))
        assertEquals(null, parseChapterNumber("Prologue"))
    }

    @Test
    fun parse_mixedDigits_returnsNull() {
        // 中文 + ASCII 混合（第3十2章）→ extractNumberSegment 返回 null
        assertEquals(null, parseChapterNumber("第3十2章"))
    }

    // ── extractNumberSegment 直接测试 ──

    @Test
    fun extract_ascii() {
        assertEquals("3", extractNumberSegment("第3章"))
        assertEquals("12", extractNumberSegment("Chapter 12"))
    }

    @Test
    fun extract_cjk() {
        assertEquals("二十三", extractNumberSegment("第二十三章"))
        assertEquals("一", extractNumberSegment("第一章"))
    }

    @Test
    fun extract_roman() {
        assertEquals("IV", extractNumberSegment("Chapter IV"))
        assertEquals("MMXXIV", extractNumberSegment("Chapter MMXXIV"))
    }

    @Test
    fun extract_mixed_returnsNull() {
        assertEquals(null, extractNumberSegment("第3十2章"))
    }

    @Test
    fun extract_none_returnsNull() {
        assertEquals(null, extractNumberSegment("序章"))
        assertEquals(null, extractNumberSegment("Prologue"))
    }

    @Test
    fun extract_romanNotSwallowLatin() {
        // 关键防退化：Chapter 的 C 不应被当罗马数字 C=100 吞掉
        // "Chapter IV" 中 ASCII find 不到（无数字），CJK 找不到，
        // 罗马 find 应该是 "IV"（因为 (?<![A-Za-z]) 排除了 Chapter 的 C 和 h,a,p,t,e,r）
        val seg = extractNumberSegment("Chapter IV")
        assertEquals("IV", seg)
    }
}
