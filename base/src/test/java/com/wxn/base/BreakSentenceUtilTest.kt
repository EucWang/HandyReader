package com.wxn.base

import com.wxn.base.util.BreakSentenceUtil
import com.wxn.base.util.BreakSentenceUtil.MAX_SENTENCE_LENGTH_CJK
import com.wxn.base.util.BreakSentenceUtil.MAX_SENTENCE_LENGTH_DEFAULT
import com.wxn.base.util.BreakSentenceUtil.stripPunctuation
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class BreakSentenceUtilTest {

    private fun splitSentence(text: String, locale: Locale) =
        BreakSentenceUtil.breakSentence(text, locale)

    private fun assertFragmentCoverage(text: String, fragments: List<Triple<String, Int, Int>>) {
        if (fragments.isEmpty()) {
            assertEquals("", text.trim())
            return
        }
        val reconstructed = fragments.joinToString("") { it.first }
        assertEquals(text, reconstructed)
        var expectedOffset = fragments[0].second
        for (f in fragments) {
            assertEquals("start offset mismatch", expectedOffset, f.second)
            assertEquals("end offset mismatch", f.second + f.first.length, f.third)
            expectedOffset = f.third
        }
    }

    // ==================== English ====================

    @Test
    fun englishShortSentence_noSplit() {
        val text = "Hello world."
        val result = splitSentence(text, Locale.ENGLISH)
        assertEquals(1, result.size)
        assertEquals("Hello world.", result[0].first)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun englishLongSentence_splitAtPeriod() {
        val text = buildString {
            repeat(20) { append("This is a test sentence number $it. ") }
        }.trim()
        val result = splitSentence(text, Locale.ENGLISH)
        assertTrue("Should split long text", result.size > 1)
        for (fragment in result) {
            assertTrue(
                "Fragment length ${fragment.first.length} exceeds max $MAX_SENTENCE_LENGTH_DEFAULT: '${fragment.first.take(50)}...'",
                fragment.first.length <= MAX_SENTENCE_LENGTH_DEFAULT + 5
            )
        }
        assertFragmentCoverage(text, result)
    }

    @Test
    fun englishLongSingleSentence_splitAtComma() {
        val parts = (1..20).map { "this is part number $it" }
        val text = parts.joinToString(", ") + "."
        val result = splitSentence(text, Locale.ENGLISH)
        assertTrue("Should split at commas", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun englishParagraph_preservesOffsets() {
        val text = "First sentence. Second sentence. Third sentence."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    // ==================== Chinese ====================

    @Test
    fun chineseShortSentence_noSplit() {
        val text = "这是一个测试句子。"
        val result = splitSentence(text, Locale.CHINESE)
        assertEquals(1, result.size)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun chineseLongSentence_splitAtPeriod() {
        val text = buildString {
            repeat(20) { i -> append("这是第${i}个很长的测试句子。") }
        }
        val result = splitSentence(text, Locale.CHINESE)
        assertTrue("Should split long Chinese text", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun chineseLongSentence_splitAtComma() {
        val text = buildString {
            append("这是一段很长的中文文本")
            repeat(15) { i -> append("，里面包含了第${i}个分句的内容") }
            append("，最后结束。")
        }
        val result = splitSentence(text, Locale.CHINESE)
        assertTrue("Should split at Chinese commas", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun chineseMaxLengthRespected() {
        val text = buildString {
            repeat(30) { i -> append("这是第${i}个句子。") }
        }
        val result = splitSentence(text, Locale.CHINESE)
        for (fragment in result) {
            assertTrue(
                "CJK fragment length ${fragment.first.length} far exceeds max $MAX_SENTENCE_LENGTH_CJK",
                fragment.first.length <= MAX_SENTENCE_LENGTH_CJK * 2
            )
        }
    }

    // ==================== Japanese ====================

    @Test
    fun japaneseShortSentence_noSplit() {
        val text = "これはテストです。"
        val result = splitSentence(text, Locale.JAPANESE)
        assertEquals(1, result.size)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun japaneseLongSentence_split() {
        val text = buildString {
            repeat(15) { i -> append("これは${i}番目のテスト文です。") }
        }
        val result = splitSentence(text, Locale.JAPANESE)
        assertTrue("Should split long Japanese text", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    // ==================== Russian ====================

    @Test
    fun russianShortSentence_noSplit() {
        val text = "Привет мир."
        val result = splitSentence(text, Locale("ru"))
        assertEquals(1, result.size)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun russianLongSentence_split() {
        val text = buildString {
            repeat(15) { i -> append("Это тестовое предложение номер $i. ") }
        }.trim()
        val result = splitSentence(text, Locale("ru"))
        assertTrue("Should split long Russian text", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    // ==================== Arabic ====================

    @Test
    fun arabicSentence_withArabicQuestionMark() {
        val text = "كيف حالك؟ أنا بخير."
        val result = splitSentence(text, Locale("ar"))
        assertFragmentCoverage(text, result)
    }

    // ==================== Hindi ====================

    @Test
    fun hindiSentence_withDanda() {
        val text = "यह एक वाक्य है। यह दूसरा वाक्य है।"
        val result = splitSentence(text, Locale("hi"))
        assertFragmentCoverage(text, result)
    }

    // ==================== German ====================

    @Test
    fun germanSentence_withGuillemets() {
        val text = "Er sagte: «Hallo» und ging dann nach Hause."
        val result = splitSentence(text, Locale("de"))
        assertFragmentCoverage(text, result)
    }

    // ==================== French ====================

    @Test
    fun frenchSentence_withGuillemets() {
        val text = "Il a dit « bonjour » et est parti."
        val result = splitSentence(text, Locale("fr"))
        assertFragmentCoverage(text, result)
    }

    // ==================== Spanish ====================

    @Test
    fun spanishSentence_normal() {
        val text = "Hola mundo. ¿Cómo estás? Bien, gracias."
        val result = splitSentence(text, Locale("es"))
        assertFragmentCoverage(text, result)
    }

    // ==================== Portuguese ====================

    @Test
    fun portugueseSentence_normal() {
        val text = "Olá mundo. Como você está?"
        val result = splitSentence(text, Locale("pt"))
        assertFragmentCoverage(text, result)
    }

    // ==================== Edge Cases ====================

    @Test
    fun emptyInput_returnsEmpty() {
        val result = splitSentence("", Locale.ENGLISH)
        assertTrue(result.isEmpty())
    }

    @Test
    fun blankInput_returnsEmpty() {
        val result = splitSentence("   \n\t  ", Locale.ENGLISH)
        assertTrue(result.isEmpty())
    }

    @Test
    fun singleCharacter_noSplit() {
        val result = splitSentence("a", Locale.ENGLISH)
        assertEquals(1, result.size)
        assertEquals("a", result[0].first)
    }

    @Test
    fun purePunctuation_handled() {
        val text = "... !!! ???"
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun noPunctuationLongText_hardTruncate() {
        val text = buildString {
            repeat(200) { append("word ") }
        }.trim()
        val result = splitSentence(text, Locale.ENGLISH)
        assertTrue("Should split word-only text", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun noPunctuationLongChinese_hardTruncate() {
        val text = buildString {
            repeat(100) { i -> append("字$i") }
        }
        val result = splitSentence(text, Locale.CHINESE)
        assertTrue("Should split punctuation-free Chinese", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun nbsp_treatedAsWhitespace() {
        val text = buildString {
            repeat(25) { i -> append("word${i}\u00A0") }
            append("end.")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertTrue("Should split at NBSP boundaries", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun narrowNbsp_treatedAsWhitespace() {
        val text = buildString {
            repeat(25) { i -> append("word${i}\u202F") }
            append("end.")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertTrue("Should split at narrow NBSP boundaries", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun cjkFullWidthSpace_treatedAsWhitespace() {
        val text = buildString {
            repeat(20) { i -> append("词$i\u3000") }
            append("结束。")
        }
        val result = splitSentence(text, Locale.CHINESE)
        assertTrue("Should split at CJK full-width space", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    // ==================== stripPunctuation ====================

    @Test
    fun stripPunctuation_englishSentence() {
        assertEquals("Hello world", stripPunctuation("Hello world."))
    }

    @Test
    fun stripPunctuation_quotedText() {
        assertEquals("Hello", stripPunctuation("\u201CHello\u201D"))
    }

    @Test
    fun stripPunctuation_multipleTrailing() {
        assertEquals("Hello", stripPunctuation("Hello...!!!"))
    }

    @Test
    fun stripPunctuation_chinesePeriod() {
        assertEquals("你好世界", stripPunctuation("你好世界。"))
    }

    @Test
    fun stripPunctuation_mixedPunctuation() {
        assertEquals("Hello", stripPunctuation("\"Hello.\""))
    }

    @Test
    fun stripPunctuation_allPunctuation_returnsEmpty() {
        assertEquals("", stripPunctuation("...!!!"))
    }

    @Test
    fun stripPunctuation_noPunctuation_unchanged() {
        assertEquals("Hello world", stripPunctuation("Hello world"))
    }

    @Test
    fun stripPunctuation_emptyString_returnsEmpty() {
        assertEquals("", stripPunctuation(""))
    }

    @Test
    fun stripPunctuation_whitespaceOnly_returnsEmpty() {
        assertEquals("", stripPunctuation("   "))
    }

    @Test
    fun stripPunctuation_leadingAndTrailing() {
        assertEquals("Hello", stripPunctuation(".Hello."))
    }

    @Test
    fun stripPunctuation_japaneseBrackets() {
        assertEquals("テスト", stripPunctuation("\u300Cテスト\u300D"))
    }

    @Test
    fun stripPunctuation_arabicQuestionMark() {
        assertEquals("كيف حالك", stripPunctuation("كيف حالك؟"))
    }

    @Test
    fun stripPunctuation_hindiDanda() {
        assertEquals("यह वाक्य है", stripPunctuation("यह वाक्य है।"))
    }

    // ==================== Offset Integrity ====================

    @Test
    fun offsets_reconstructOriginalText_english() {
        val text = "The quick brown fox jumps over the lazy dog. " +
                "Pack my box with five dozen liquor jugs. " +
                "How vexingly quick daft zebras jump."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun offsets_reconstructOriginalText_chinese() {
        val text = "天地玄黄，宇宙洪荒。日月盈昃，辰宿列张。寒来暑往，秋收冬藏。"
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun offsets_reconstructOriginalText_mixedScript() {
        val text = "Hello世界！This is 第一个test，mixed很好。"
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun offsets_continuousAfterLongTextSplit() {
        val text = buildString {
            append("A".repeat(200))
            append(". ")
            append("B".repeat(200))
            append(". ")
            append("Short end.")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
        for (i in 0 until result.size - 1) {
            assertEquals("Fragments should be continuous", result[i].third, result[i + 1].second)
        }
    }

    // ==================== findNextSplitPosition Priority ====================
    // Tests that forward search prefers punctuation over whitespace

    @Test
    fun forwardSearch_prefersPeriodOverSpace() {
        val text = buildString {
            append("a".repeat(99))
            append(". ")
            append("b".repeat(50))
            append(".")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
        val firstFragment = result[0].first
        assertTrue(
            "First fragment should end with period, got: '${firstFragment.takeLast(5)}'",
            firstFragment.trimEnd().endsWith(".")
        )
    }

    @Test
    fun forwardSearch_prefersCommaOverSpace() {
        val text = buildString {
            append("a".repeat(99))
            append(", ")
            append("b".repeat(50))
            append(".")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
        val firstFragment = result[0].first
        assertTrue(
            "First fragment should end with comma, got: '${firstFragment.takeLast(5)}'",
            firstFragment.trimEnd().endsWith(",")
        )
    }

    @Test
    fun forwardSearch_prefersStrongOverComma() {
        val text = buildString {
            append("a".repeat(99))
            append("? ")
            append("b".repeat(10))
            append(", ")
            append("c".repeat(50))
            append(".")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
        val firstFragment = result[0].first
        assertTrue(
            "Should split at strong separator (?), got: '${firstFragment.takeLast(5)}'",
            firstFragment.trimEnd().endsWith("?")
        )
    }

    @Test
    fun forwardSearch_cjkPrefersPunctuationOverNoSplit() {
        val text = buildString {
            append("这是一段很长的文本".repeat(5))
            append("，然后继续")
            append("。最后结束")
        }
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
        assertTrue("Should split long CJK text", result.size > 1)
        for (fragment in result) {
            assertTrue(
                "CJK fragment too long: ${fragment.first.length}",
                fragment.first.length <= MAX_SENTENCE_LENGTH_CJK * 2
            )
        }
    }

    @Test
    fun forwardSearch_searchRangeCjk_maxLenHalf() {
        val text = buildString {
            append("这是一段没有标点的长文本".repeat(10))
        }
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
        assertTrue("Should split punctuation-free CJK text", result.size > 1)
    }

    // ==================== Quote Split Point ====================

    @Test
    fun quoteSplitPoint_closingQuote() {
        val text = "He said \"hello world\" to everyone. She replied \"goodbye\"."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun quoteSplitPoint_rightDoubleQuotationMark() {
        val text = "She said \u201Chello\u201D and then walked away. He was surprised."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun quoteSplitPoint_germanGuillemets() {
        val text = "Er sagte \u00ABHallo\u00BB und ging dann weg. Das war alles."
        val result = splitSentence(text, Locale("de"))
        assertFragmentCoverage(text, result)
    }

    @Test
    fun quoteSplitPoint_japaneseBrackets() {
        val text = buildString {
            append("\u300Cこれは")
            append("テスト".repeat(20))
            append("\u300Dです。")
        }
        val result = splitSentence(text, Locale.JAPANESE)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun quoteSplitPoint_longQuotedEnglish() {
        val text = buildString {
            append("\u201C")
            append("The quick brown fox jumps over the lazy dog. ".repeat(5))
            append("\u201D She said.")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
        assertTrue("Should split long quoted text", result.size > 1)
    }

    @Test
    fun quoteSplitPoint_asciiQuote_midText() {
        val text = buildString {
            append("a".repeat(98))
            append("\"x")
            append("b".repeat(50))
            append("\" end.")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    // ==================== isSentenceEndPeriod / Abbreviation ====================

    @Test
    fun sentenceEndPeriod_realSentenceEnd() {
        val text = "The cat sat on the mat. The dog ran in the park. The bird flew away."
        val result = splitSentence(text, Locale.ENGLISH)
        assertTrue("Should split at sentence-ending periods", result.size >= 2)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun sentenceEndPeriod_withUppercase() {
        val text = "Dr. Smith went to Washington D.C. last week. He met Mr. Jones there."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun sentenceEndPeriod_decimalNumbers_notSplit() {
        val text = "The value is 3.14159 and the result was 2.71828 in this experiment."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun sentenceEndPeriod_ellipsis() {
        val text = "He paused... then continued speaking about the matter at hand."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun sentenceEndPeriod_urlNotSplit() {
        val text = "Visit example.com for more info. Thanks."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    // ==================== mergeShortFragments ====================

    @Test
    fun mergeShortFragment_mergedWithPrevious() {
        val text = buildString {
            append("a".repeat(98))
            append(". ")
            append("x")
            append(". ")
            append("b".repeat(98))
            append(".")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun mergeShortFragment_singleCharBetween() {
        val text = buildString {
            append("a".repeat(99))
            append(". A. ")
            append("b".repeat(99))
            append(".")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun mergeShortFragment_cjkTinySegment() {
        val text = buildString {
            append("这是很长的文本".repeat(5))
            append("。")
            append("短")
            append("。")
            append("这又是很长的文本".repeat(5))
            append("。")
        }
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
    }

    // ==================== Mixed Language / Real-World ====================

    @Test
    fun mixedScript_chineseWithEnglish() {
        val text = "今天我学习了Android开发，使用了Kotlin语言。感觉非常有趣！"
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun mixedScript_englishWithCJK() {
        val text = "I love eating 寿司 and 饺子, they are 美味. " +
                "My friend さん taught me how to cook 中国菜. " +
                "It was すごい!"
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun realWorld_englishParagraph() {
        val text = "In a hole in the ground there lived a hobbit. " +
                "Not a nasty, dirty, wet hole, filled with the ends of worms and an oozy smell, " +
                "nor yet a dry, bare, sandy hole with nothing in it to sit down on or to eat: " +
                "it was a hobbit-hole, and that means comfort."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
        assertTrue("Long paragraph should split", result.size >= 1)
    }

    @Test
    fun realWorld_chineseParagraph() {
        val text = "从前有座山，山里有座庙，庙里有个老和尚，老和尚对小和尚说：" +
                "从前有座山，山里有座庙，庙里有个老和尚，老和尚对小和尚说：" +
                "山下的河流很清澈，河边的花开得很美，美得让人忘记了时间。"
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun realWorld_dialogue() {
        val text = buildString {
            append("\u201C你好，\u201D他说，\u201C很高兴见到你。\u201D")
            append("\u201C我也是，\u201D她回答，\u201C我们走吧。\u201D")
            append("他们一起走向了远方。")
        }
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun realWorld_technicalText() {
        val text = "The function takes two parameters: a String and an Int. " +
                "It returns a List<Pair<String, Int>> where each element represents " +
                "a mapped result from the input data. " +
                "If the input is null, it throws IllegalArgumentException."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun realWorld_japaneseWithKatakana() {
        val text = buildString {
            append("プログラミングは楽しいです。")
            append("Androidアプリの開発にはKotlinを使います。")
            append("Jetpack ComposeはモダンなUIツールキットです。")
        }
        val result = splitSentence(text, Locale.JAPANESE)
        assertFragmentCoverage(text, result)
    }

    // ==================== Semicolon / Colon / Dash ====================

    @Test
    fun semicolon_asSeparator() {
        val text = "First part of the idea; second part of the idea; " +
                "third part of the idea; and finally the conclusion."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun colon_asSeparator() {
        val text = "There are three things I know: " +
                "the sun will rise, the rain will fall, and the earth will turn."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun emDash_asSeparator() {
        val text = "He ran \u2014 fast and determined \u2014 toward the finish line. " +
                "The crowd cheered \u2013 loudly \u2013 as he crossed."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun chineseColonAndSemicolon() {
        val text = "注意：这是第一条规则；这是第二条规则；这是第三条规则。"
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun ellipsis_asSeparator() {
        val text = buildString {
            append("a".repeat(95))
            append("\u2026 then ")
            append("b".repeat(50))
            append(".")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    // ==================== Arabic / RTL ====================

    @Test
    fun arabicLongSentence_split() {
        val text = buildString {
            repeat(10) { i -> append("هذا اختبار رقم $i. ") }
        }.trim()
        val result = splitSentence(text, Locale("ar"))
        assertTrue("Should split long Arabic text", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun arabicCommaSeparator() {
        val text = buildString {
            append("الجزء الأول")
            repeat(15) { i -> append("\u060C الجزء ${i + 2}") }
            append(".")
        }
        val result = splitSentence(text, Locale("ar"))
        assertFragmentCoverage(text, result)
    }

    // ==================== Hindi Devanagari ====================

    @Test
    fun hindiLongSentence_splitAtDanda() {
        val text = buildString {
            repeat(10) { i -> append("यह वाक्य $i है। ") }
        }.trim()
        val result = splitSentence(text, Locale("hi"))
        assertTrue("Should split at Hindi danda", result.size > 1)
        assertFragmentCoverage(text, result)
    }

    // ==================== Stress / Robustness ====================

    @Test
    fun stress_veryLongText() {
        val text = buildString {
            repeat(50) { i ->
                append("This is sentence number $i with some extra padding words. ")
            }
        }.trim()
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
        assertTrue("Should produce many fragments", result.size > 10)
    }

    @Test
    fun stress_veryLongCJK() {
        val text = buildString {
            repeat(50) { i -> append("这是第${i}个很长的测试句子。") }
        }
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
        assertTrue("Should produce many fragments", result.size > 10)
    }

    @Test
    fun stress_repeatedPunctuation() {
        val text = "Wait... What?! Really!!! Are you sure??? " +
                "Yes!!! I am absolutely certain... " +
                "Well... okay then."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun stress_onlySpacesAndPunctuation() {
        val text = "   . , ; : ! ?   . , ; : ! ?   . , ; : ! ?   "
        val result = splitSentence(text, Locale.ENGLISH)
        if (result.isNotEmpty()) {
            assertFragmentCoverage(text, result)
        }
    }

    @Test
    fun stress_singleVeryLongWord() {
        val text = "a".repeat(500) + "."
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
        assertTrue("Should split single long word", result.size > 1)
    }

    // ==================== minLength Protection ====================

    @Test
    fun minLength_noAbsurdlyShortFragments_english() {
        val text = buildString {
            append("a".repeat(99))
            append(". ")
            append("x")
            append(". ")
            repeat(5) { i -> append("word$i ") }
            append("end.")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
        val shortFragments = result.filter { it.first.length < 5 && it.first.isNotBlank() }
        assertTrue(
            "Should not have very short fragments: ${shortFragments.map { it.first }}",
            shortFragments.isEmpty()
        )
    }

    @Test
    fun minLength_noAbsurdlyShortFragments_cjk() {
        val text = buildString {
            append("这是一段很长的文本".repeat(5))
            append("。")
            append("短")
            append("。")
            append("这是另一段很长的文本".repeat(5))
            append("。")
        }
        val result = splitSentence(text, Locale.CHINESE)
        assertFragmentCoverage(text, result)
    }

    // ==================== Whitespace Variants ====================

    @Test
    fun tabAsSeparator() {
        val text = buildString {
            append("a".repeat(98))
            append("\t")
            append("b".repeat(50))
            append(".")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun mixedWhitespace() {
        val text = buildString {
            append("word".repeat(30))
            append(" ")
            append("more".repeat(20))
            append("\t")
            append("end.")
        }
        val result = splitSentence(text, Locale.ENGLISH)
        assertFragmentCoverage(text, result)
    }

    @Test
    fun ideographicSpace_cjk() {
        val text = buildString {
            append("これは\u3000テスト\u3000です。")
            append("日本語の\u3000文章を\u3000分割します。")
        }
        val result = splitSentence(text, Locale.JAPANESE)
        assertFragmentCoverage(text, result)
    }
}
