package com.wxn.base

import com.wxn.base.util.BreakParagraphUtil.isAbbreviationApostrophe
import com.wxn.base.util.BreakParagraphUtil.normalizeForTts
import org.junit.Assert.*
import org.junit.Test

class BreakParagraphUtilTest {

    @Test
    fun normalizeForTts_chineseDoubleQuotes_removed() {
        assertEquals("他说你好吗", normalizeForTts("他说\u201C你好\u201D吗"))
    }

    @Test
    fun normalizeForTts_bookTitleMarks_removed() {
        assertEquals("读红楼梦有感", normalizeForTts("读\u300A红楼梦\u300B有感"))
    }

    @Test
    fun normalizeForTts_multipleBookTitles_removed() {
        assertEquals("我喜欢三体和沙丘", normalizeForTts("我喜欢\u300A三体\u300B和\u300A沙丘\u300B"))
    }

    @Test
    fun normalizeForTts_fullwidthParentheses_replacedWithSpace() {
        assertEquals("他 小明 来了", normalizeForTts("他\uFF08小明\uFF09来了"))
    }

    @Test
    fun normalizeForTts_asciiParentheses_replacedWithSpace() {
        assertEquals("The quick brown fox", normalizeForTts("The (quick) brown fox"))
    }

    @Test
    fun normalizeForTts_fullwidthSquareBrackets_replacedWithSpace() {
        assertEquals("请参考 附录A", normalizeForTts("请参考\uFF3B附录A\uFF3D"))
    }

    @Test
    fun normalizeForTts_blackLenticularBrackets_replacedWithSpace() {
        assertEquals("重要 通知", normalizeForTts("\u3010重要\u3011通知"))
    }

    @Test
    fun normalizeForTts_doubleDash_replacedWithColon() {
        assertEquals("他走了:不再回来", normalizeForTts("他走了\u2014\u2014不再回来"))
    }

    @Test
    fun normalizeForTts_singleEmDash_preserved() {
        assertEquals("a\u2014b", normalizeForTts("a\u2014b"))
    }

    @Test
    fun normalizeForTts_mixedQuotesAndBrackets() {
        assertEquals("他说你好 世界", normalizeForTts("他说\u201C你好\u201D\uFF08世界\uFF09"))
    }

    @Test
    fun normalizeForTts_englishQuotesWithSpaces_collapsed() {
        assertEquals("He said hello to me", normalizeForTts("He said \u201Chello\u201D to me"))
    }

    @Test
    fun normalizeForTts_englishQuotesAndParens() {
        assertEquals("The quick brown fox", normalizeForTts("The \u201Cquick\u201D (brown) fox"))
    }

    @Test
    fun normalizeForTts_consecutiveSpaces_collapsed() {
        assertEquals("a b c", normalizeForTts("a  b  c"))
    }

    @Test
    fun normalizeForTts_noChange_needed() {
        assertEquals("Hello world", normalizeForTts("Hello world"))
    }

    @Test
    fun normalizeForTts_emptyString_returnsEmpty() {
        assertEquals("", normalizeForTts(""))
    }

    @Test
    fun normalizeForTts_allQuotes_returnsEmpty() {
        assertEquals("", normalizeForTts("\u201C\u201D"))
    }

    @Test
    fun normalizeForTts_trailingPunctuation_stripped() {
        assertEquals("Hello", normalizeForTts("Hello.\""))
    }

    @Test
    fun normalizeForTts_singleQuoteApostrophe_preserved() {
        assertEquals("it's a test", normalizeForTts("it's a test"))
    }

    @Test
    fun normalizeForTts_smartSingleQuoteApostrophe_preserved() {
        assertEquals("it\u2019s a test", normalizeForTts("it\u2019s a test"))
    }

    @Test
    fun normalizeForTts_bracketAdjacentToWord_stillWorks() {
        assertEquals("Hello world", normalizeForTts("Hello(world)"))
    }

    @Test
    fun normalizeForTts_bookTitleWithEnglish_insideChinese() {
        assertEquals("读AI有感", normalizeForTts("读\u300AAI\u300B有感"))
    }

    @Test
    fun normalizeForTts_multipleBrackets_collapsedSpaces() {
        assertEquals("a b c d", normalizeForTts("a(b)c(d)"))
    }

    @Test
    fun normalizeForTts_quoteBetweenSpaces_noDoubleSpace() {
        assertEquals("a b c", normalizeForTts("a \u201Cb\u201D c"))
    }

    @Test
    fun normalizeForTts_parenAfterSpace_noDoubleSpace() {
        assertEquals("a b c", normalizeForTts("a (b) c"))
    }

    @Test
    fun normalizeForTts_fullwidthParenWithContent() {
        assertEquals("这本书 共300页 很厚", normalizeForTts("这本书\uFF08共300页\uFF09很厚"))
    }

    //region isAbbreviationApostrophe tests

    private fun String.toCharList(): List<String> = this.map { it.toString() }

    @Test
    fun apostrophe_betweenLetters_ascii() {
        val chars = "don't".toCharList()
        assertTrue(isAbbreviationApostrophe(chars, 3))
    }

    @Test
    fun apostrophe_betweenLetters_smartQuote() {
        val chars = "it\u2019s".toCharList()
        assertTrue(isAbbreviationApostrophe(chars, 2))
    }

    @Test
    fun apostrophe_betweenLetters_leftSmartQuote() {
        val chars = "it\u2018s".toCharList()
        assertTrue(isAbbreviationApostrophe(chars, 2))
    }

    @Test
    fun apostrophe_atWordBoundary_notAbbreviation() {
        val chars = "don' t".toCharList()
        assertFalse(isAbbreviationApostrophe(chars, 3))
    }

    @Test
    fun apostrophe_atStart_false() {
        val chars = "'hello".toCharList()
        assertFalse(isAbbreviationApostrophe(chars, 0))
    }

    @Test
    fun apostrophe_twas_atParagraphStart_false() {
        val chars = "'Twas".toCharList()
        assertFalse(isAbbreviationApostrophe(chars, 0))
    }

    @Test
    fun apostrophe_afterSpace_beforeLetter_false() {
        val chars = " 'Twas".toCharList()
        assertFalse(isAbbreviationApostrophe(chars, 1))
    }

    @Test
    fun apostrophe_atEnd_false() {
        val chars = "hello'".toCharList()
        assertFalse(isAbbreviationApostrophe(chars, 5))
    }

    @Test
    fun nonApostrophe_false() {
        val chars = "a.b".toCharList()
        assertFalse(isAbbreviationApostrophe(chars, 1))
    }

    @Test
    fun apostrophe_spanish_contraction() {
        val chars = "l\u2019estaci\u00F3".toCharList()
        assertTrue(isAbbreviationApostrophe(chars, 1))
    }

    @Test
    fun apostrophe_portuguese_contraction() {
        val chars = "d\u2019\u00E1gua".toCharList()
        assertTrue(isAbbreviationApostrophe(chars, 1))
    }

    @Test
    fun apostrophe_french_contraction() {
        val chars = "l\u2019avion".toCharList()
        assertTrue(isAbbreviationApostrophe(chars, 1))
    }

    @Test
    fun apostrophe_emptyList_false() {
        assertFalse(isAbbreviationApostrophe(emptyList(), 0))
    }

    @Test
    fun apostrophe_indexOutOfBounds_false() {
        val chars = "don't".toCharList()
        assertFalse(isAbbreviationApostrophe(chars, 10))
    }

    //endregion
}
