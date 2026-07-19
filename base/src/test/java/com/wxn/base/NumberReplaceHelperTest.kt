package com.wxn.base

import com.wxn.base.util.numReplacer.NumberReplaceHelper
import com.wxn.base.util.numReplacer.ZhNumberReplacer
import com.wxn.base.util.numReplacer.EnNumberReplacer
import com.wxn.base.util.numReplacer.EsNumberReplacer
import com.wxn.base.util.numReplacer.PtNumberReplacer
import com.wxn.base.util.numReplacer.FrNumberReplacer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class NumberReplaceHelperTest {

    private val helper = NumberReplaceHelper

    // ==================== Chinese locale ====================

    @Test
    fun testChineseLocaleBasic() {
        val result = helper.replace("我有3本书", Locale.CHINESE)
        assertEquals("我有三本书", result)
    }

    @Test
    fun testChineseLocaleMultipleNumbers() {
        val result = helper.replace("2024年3月15日", Locale.CHINESE)
        assertEquals("二零二四年三月十五日", result)
    }

    // ==================== English locale ====================

    @Test
    fun testEnglishLocaleBasic() {
        val result = helper.replace("I have 3 books", Locale.ENGLISH)
        assertEquals("I have three books", result)
    }

    @Test
    fun testEnglishLocaleDate() {
        val result = helper.replace("March 15, 2024", Locale.ENGLISH)
        assertEquals("March fifteenth, twenty twenty-four", result)
    }

    // ==================== Spanish locale ====================

    @Test
    fun testSpanishLocaleBasic() {
        val result = helper.replace("Tengo 3 libros", Locale("es"))
        assertEquals("Tengo tres libros", result)
    }

    @Test
    fun testSpanishLocaleDate() {
        val result = helper.replace("15 de marzo de 2024", Locale("es"))
        assertEquals("quince de marzo de dos mil veinticuatro", result)
    }

    // ==================== Locale switching ====================

    @Test
    fun testSwitchZhToEn() {
        val r1 = helper.replace("我有3本书", Locale.CHINESE)
        assertEquals("我有三本书", r1)
        val r2 = helper.replace("I have 3 books", Locale.ENGLISH)
        assertEquals("I have three books", r2)
    }

    @Test
    fun testSwitchEnToEs() {
        val r1 = helper.replace("5 dollars", Locale.ENGLISH)
        assertEquals("five dollars", r1)
        val r2 = helper.replace("Tengo 5 libros", Locale("es"))
        assertEquals("Tengo cinco libros", r2)
    }

    @Test
    fun testSwitchEsToZh() {
        val r1 = helper.replace("5 libros", Locale("es"))
        assertEquals("cinco libros", r1)
        val r2 = helper.replace("5本书", Locale.CHINESE)
        assertEquals("五本书", r2)
    }

    @Test
    fun testSwitchZhEnEsRoundTrip() {
        val r1 = helper.replace("100", Locale.CHINESE)
        assertEquals("一百", r1)
        val r2 = helper.replace("100", Locale.ENGLISH)
        assertEquals("one hundred", r2)
        val r3 = helper.replace("100", Locale("es"))
        assertEquals("cien", r3)
    }

    // ==================== Portuguese locale ====================

    @Test
    fun testPortugueseLocaleBasic() {
        val result = helper.replace("Tenho 3 livros", Locale("pt"))
        assertEquals("Tenho três livros", result)
    }

    @Test
    fun testPortugueseLocaleDate() {
        val result = helper.replace("15 de março de 2024", Locale("pt"))
        assertEquals("quinze de março de dois mil e vinte e quatro", result)
    }

    // ==================== Locale switching with Portuguese ====================

    @Test
    fun testSwitchEnToPt() {
        val r1 = helper.replace("5 dollars", Locale.ENGLISH)
        assertEquals("five dollars", r1)
        val r2 = helper.replace("Tenho 5 livros", Locale("pt"))
        assertEquals("Tenho cinco livros", r2)
    }

    @Test
    fun testSwitchPtToZh() {
        val r1 = helper.replace("5 livros", Locale("pt"))
        assertEquals("cinco livros", r1)
        val r2 = helper.replace("5本书", Locale.CHINESE)
        assertEquals("五本书", r2)
    }

    @Test
    fun testSwitchEsToPt() {
        val r1 = helper.replace("5 libros", Locale("es"))
        assertEquals("cinco libros", r1)
        val r2 = helper.replace("5 livros", Locale("pt"))
        assertEquals("cinco livros", r2)
    }

    @Test
    fun testSwitchZhEnEsPtRoundTrip() {
        val r1 = helper.replace("100", Locale.CHINESE)
        assertEquals("一百", r1)
        val r2 = helper.replace("100", Locale.ENGLISH)
        assertEquals("one hundred", r2)
        val r3 = helper.replace("100", Locale("es"))
        assertEquals("cien", r3)
        val r4 = helper.replace("100", Locale("pt"))
        assertEquals("cem", r4)
    }

    // ==================== Unsupported locale fallback ====================

    @Test
    fun testUnsupportedLocaleReturnsOriginal() {
        val result = helper.replace("I have 3 books", Locale.KOREAN)
        assertEquals("I have 3 books", result)
    }

    @Test
    fun testUnsupportedLocaleJapanese() {
        val result = helper.replace("3冊の本", Locale.JAPANESE)
        assertEquals("3冊の本", result)
    }

    // ==================== Same locale cache hit ====================

    @Test
    fun testSameLocaleUsesCache() {
        val r1 = helper.replace("123", Locale.ENGLISH)
        val r2 = helper.replace("456", Locale.ENGLISH)
        assertEquals("one hundred and twenty-three", r1)
        assertEquals("four hundred and fifty-six", r2)
    }

    // ==================== Empty and edge inputs ====================

    @Test
    fun testEmptyString() {
        assertEquals("", helper.replace("", Locale.ENGLISH))
        assertEquals("", helper.replace("", Locale.CHINESE))
        assertEquals("", helper.replace("", Locale("es")))
        assertEquals("", helper.replace("", Locale("pt")))
        assertEquals("", helper.replace("", Locale.FRENCH))
    }

    @Test
    fun testNoNumbers() {
        assertEquals("hello world", helper.replace("hello world", Locale.ENGLISH))
        assertEquals("你好世界", helper.replace("你好世界", Locale.CHINESE))
    }

    // ==================== Language-specific number output ====================

    @Test
    fun testSameNumberDifferentLanguage() {
        val zh = helper.replace("42", Locale.CHINESE)
        val en = helper.replace("42", Locale.ENGLISH)
        val es = helper.replace("42", Locale("es"))
        val pt = helper.replace("42", Locale("pt"))
        val fr = helper.replace("42", Locale.FRENCH)
        assertEquals("四十二", zh)
        assertEquals("forty-two", en)
        assertEquals("cuarenta y dos", es)
        assertEquals("quarenta e dois", pt)
        assertEquals("quarante-deux", fr)
        assertTrue(zh != en)
        assertTrue(en != es)
        assertTrue(es != pt)
        assertTrue(pt != fr)
    }

    @Test
    fun testCurrencyDifferentLanguage() {
        val en = helper.replace("\$5.99", Locale.ENGLISH)
        val es = helper.replace("\$5.99", Locale("es"))
        val pt = helper.replace("\$5.99", Locale("pt"))
        assertEquals("five dollars and ninety-nine cents", en)
        assertEquals("cinco dólares con noventa y nueve centavos", es)
        assertEquals("cinco dólares e noventa e nove centavos", pt)
    }

    @Test
    fun testPercentageDifferentLanguage() {
        val en = helper.replace("50%", Locale.ENGLISH)
        val es = helper.replace("50%", Locale("es"))
        val pt = helper.replace("50%", Locale("pt"))
        assertEquals("fifty percent", en)
        assertEquals("cincuenta por ciento", es)
        assertEquals("cinquenta por cento", pt)
    }

    @Test
    fun testTemperatureDifferentLanguage() {
        val en = helper.replace("36.5°C", Locale.ENGLISH)
        val es = helper.replace("36.5°C", Locale("es"))
        val pt = helper.replace("36.5°C", Locale("pt"))
        assertEquals("thirty-six point five degrees Celsius", en)
        assertEquals("treinta y seis coma cinco grados Celsius", es)
        assertEquals("trinta e seis vírgula cinco graus Celsius", pt)
    }

    @Test
    fun testBrlCurrency() {
        val result = helper.replace("R$5,50", Locale("pt"))
        assertEquals("cinco reais e cinquenta centavos", result)
    }

    // ==================== French locale ====================

    @Test
    fun testFrenchLocaleBasic() {
        val result = helper.replace("J'ai 3 livres", Locale.FRENCH)
        assertEquals("J'ai trois livres", result)
    }

    @Test
    fun testFrenchLocaleDate() {
        val result = helper.replace("15 mars 2024", Locale.FRENCH)
        assertEquals("quinze mars deux mille vingt-quatre", result)
    }

    @Test
    fun testFrenchLocaleCurrency() {
        val result = helper.replace("€50", Locale.FRENCH)
        assertEquals("cinquante euros", result)
    }

    // ==================== Locale switching with French ====================

    @Test
    fun testSwitchEnToFr() {
        val r1 = helper.replace("5 dollars", Locale.ENGLISH)
        assertEquals("five dollars", r1)
        val r2 = helper.replace("5 livres", Locale.FRENCH)
        assertEquals("cinq livres", r2)
    }

    @Test
    fun testSwitchFrToZh() {
        val r1 = helper.replace("5 livres", Locale.FRENCH)
        assertEquals("cinq livres", r1)
        val r2 = helper.replace("5本书", Locale.CHINESE)
        assertEquals("五本书", r2)
    }

    @Test
    fun testSwitchPtToFr() {
        val r1 = helper.replace("5 livros", Locale("pt"))
        assertEquals("cinco livros", r1)
        val r2 = helper.replace("5 livres", Locale.FRENCH)
        assertEquals("cinq livres", r2)
    }

    @Test
    fun testSwitchZhEnEsPtFrRoundTrip() {
        val r1 = helper.replace("100", Locale.CHINESE)
        assertEquals("一百", r1)
        val r2 = helper.replace("100", Locale.ENGLISH)
        assertEquals("one hundred", r2)
        val r3 = helper.replace("100", Locale("es"))
        assertEquals("cien", r3)
        val r4 = helper.replace("100", Locale("pt"))
        assertEquals("cem", r4)
        val r5 = helper.replace("100", Locale.FRENCH)
        assertEquals("cent", r5)
    }
}
