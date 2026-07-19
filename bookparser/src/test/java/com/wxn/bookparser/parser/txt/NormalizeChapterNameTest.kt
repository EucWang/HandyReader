package com.wxn.bookparser.parser.txt

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [normalizeChapterName] 单测：验证 canonical key 抹平数字格式差异。
 *
 * 核心断言：同章不同写法 → 同 key；异章不同内容 → 不同 key。
 */
class NormalizeChapterNameTest {

    // ── 中文章节 ──

    @Test
    fun norm_cnWithSubtitle() {
        assertEquals("CN:章:1", normalizeChapterName("第一章 初遇"))
    }

    @Test
    fun norm_cnArabicSame() {
        // 第1章 与 第一章 应归一到同 key（数字格式抹平）
        assertEquals("CN:章:1", normalizeChapterName("第1章"))
    }

    @Test
    fun norm_cnMultiDigit() {
        assertEquals("CN:章:23", normalizeChapterName("第二十三章"))
        assertEquals("CN:章:23", normalizeChapterName("第23章"))
    }

    @Test
    fun norm_cnJieVsZhang() {
        // 第一章 与 第一节 不同 key（unit 区分）
        assertEquals("CN:章:1", normalizeChapterName("第一章"))
        assertEquals("CN:节:1", normalizeChapterName("第一节"))
    }

    @Test
    fun norm_cnVolumeNotConfuse() {
        assertEquals("CN:卷:1", normalizeChapterName("第一卷"))
        // 卷与章不同 key
        assert(normalizeChapterName("第一卷") != normalizeChapterName("第一章"))
    }

    // ── 英文章节 ──

    @Test
    fun norm_enBasic() {
        assertEquals("EN:chapter:1", normalizeChapterName("Chapter 1"))
    }

    @Test
    fun norm_enRomanSameAsArabic() {
        // Chapter I 与 Chapter 1 抹平到同 key
        assertEquals("EN:chapter:1", normalizeChapterName("Chapter I"))
        assertEquals("EN:chapter:1", normalizeChapterName("Chapter 1"))
    }

    @Test
    fun norm_enCaseInsensitive() {
        assertEquals("EN:chapter:1", normalizeChapterName("chapter 1"))
        assertEquals("EN:chapter:1", normalizeChapterName("CHAPTER 1"))
    }

    @Test
    fun norm_enKeywords() {
        assertEquals("EN:section:1", normalizeChapterName("Section 1"))
        assertEquals("EN:scene:2", normalizeChapterName("Scene 2"))
        assertEquals("EN:part:3", normalizeChapterName("Part 3"))
        assertEquals("EN:introduction:1", normalizeChapterName("Introduction 1"))
    }

    @Test
    fun norm_enKeywordCaseInsensitive() {
        assertEquals("EN:section:1", normalizeChapterName("SECTION 1"))
    }

    // ── 特殊词 ──

    @Test
    fun norm_specialXuVariants() {
        // 序/序章/序言 归一到 SP:序
        assertEquals("SP:序", normalizeChapterName("序"))
        assertEquals("SP:序", normalizeChapterName("序章"))
        assertEquals("SP:序", normalizeChapterName("序言"))
    }

    @Test
    fun norm_specialWithSub() {
        assertEquals("SP:楔子", normalizeChapterName("楔子"))
        assertEquals("SP:楔子", normalizeChapterName("楔子 缘起"))
    }

    @Test
    fun norm_specialOthers() {
        assertEquals("SP:前言", normalizeChapterName("前言"))
        assertEquals("SP:引", normalizeChapterName("引子"))
        assertEquals("SP:引", normalizeChapterName("引言"))
        assertEquals("SP:后记", normalizeChapterName("后记"))
        assertEquals("SP:后记", normalizeChapterName("跋"))
        assertEquals("SP:尾声", normalizeChapterName("尾声"))
    }

    @Test
    fun norm_specialEnglish() {
        assertEquals("SP:prologue", normalizeChapterName("Prologue"))
        assertEquals("SP:epilogue", normalizeChapterName("Epilogue"))
        assertEquals("SP:preface", normalizeChapterName("Preface"))
        assertEquals("SP:foreword", normalizeChapterName("Foreword"))
        assertEquals("SP:afterword", normalizeChapterName("Afterword"))
    }

    // ── 兜底 ──

    @Test
    fun norm_unknownPassthrough() {
        // 未识别格式 → RAW: 前缀 + 原文（剥尾部标点）
        assertEquals("RAW:XYZ 标题", normalizeChapterName("XYZ 标题"))
        assertEquals("RAW:XYZ", normalizeChapterName("XYZ。"))
    }

    @Test
    fun norm_empty() {
        assertEquals("", normalizeChapterName(""))
    }

    @Test
    fun norm_trimsWhitespace() {
        assertEquals("CN:章:1", normalizeChapterName("  第一章  "))
    }
}
