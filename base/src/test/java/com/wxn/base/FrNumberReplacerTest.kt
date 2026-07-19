package com.wxn.base

import com.wxn.base.util.numReplacer.FrNumberReplacer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FrNumberReplacerTest {

    private lateinit var replacer: FrNumberReplacer

    @Before
    fun setUp() {
        replacer = FrNumberReplacer()
    }

    // ==================== intToFrench: 0-19 ====================

    @Test
    fun testInt0() = assertEquals("zéro", FrNumberReplacer.intToFrench(0))

    @Test
    fun testInt1() = assertEquals("un", FrNumberReplacer.intToFrench(1))

    @Test
    fun testInt2() = assertEquals("deux", FrNumberReplacer.intToFrench(2))

    @Test
    fun testInt3() = assertEquals("trois", FrNumberReplacer.intToFrench(3))

    @Test
    fun testInt4() = assertEquals("quatre", FrNumberReplacer.intToFrench(4))

    @Test
    fun testInt5() = assertEquals("cinq", FrNumberReplacer.intToFrench(5))

    @Test
    fun testInt6() = assertEquals("six", FrNumberReplacer.intToFrench(6))

    @Test
    fun testInt7() = assertEquals("sept", FrNumberReplacer.intToFrench(7))

    @Test
    fun testInt8() = assertEquals("huit", FrNumberReplacer.intToFrench(8))

    @Test
    fun testInt9() = assertEquals("neuf", FrNumberReplacer.intToFrench(9))

    @Test
    fun testInt10() = assertEquals("dix", FrNumberReplacer.intToFrench(10))

    @Test
    fun testInt11() = assertEquals("onze", FrNumberReplacer.intToFrench(11))

    @Test
    fun testInt12() = assertEquals("douze", FrNumberReplacer.intToFrench(12))

    @Test
    fun testInt13() = assertEquals("treize", FrNumberReplacer.intToFrench(13))

    @Test
    fun testInt14() = assertEquals("quatorze", FrNumberReplacer.intToFrench(14))

    @Test
    fun testInt15() = assertEquals("quinze", FrNumberReplacer.intToFrench(15))

    @Test
    fun testInt16() = assertEquals("seize", FrNumberReplacer.intToFrench(16))

    @Test
    fun testInt17() = assertEquals("dix-sept", FrNumberReplacer.intToFrench(17))

    @Test
    fun testInt18() = assertEquals("dix-huit", FrNumberReplacer.intToFrench(18))

    @Test
    fun testInt19() = assertEquals("dix-neuf", FrNumberReplacer.intToFrench(19))

    // ==================== intToFrench: 20-69 (regular tens) ====================

    @Test
    fun testInt20() = assertEquals("vingt", FrNumberReplacer.intToFrench(20))

    @Test
    fun testInt21_et() = assertEquals("vingt et un", FrNumberReplacer.intToFrench(21))

    @Test
    fun testInt22() = assertEquals("vingt-deux", FrNumberReplacer.intToFrench(22))

    @Test
    fun testInt29() = assertEquals("vingt-neuf", FrNumberReplacer.intToFrench(29))

    @Test
    fun testInt30() = assertEquals("trente", FrNumberReplacer.intToFrench(30))

    @Test
    fun testInt31_et() = assertEquals("trente et un", FrNumberReplacer.intToFrench(31))

    @Test
    fun testInt32() = assertEquals("trente-deux", FrNumberReplacer.intToFrench(32))

    @Test
    fun testInt39() = assertEquals("trente-neuf", FrNumberReplacer.intToFrench(39))

    @Test
    fun testInt40() = assertEquals("quarante", FrNumberReplacer.intToFrench(40))

    @Test
    fun testInt41_et() = assertEquals("quarante et un", FrNumberReplacer.intToFrench(41))

    @Test
    fun testInt49() = assertEquals("quarante-neuf", FrNumberReplacer.intToFrench(49))

    @Test
    fun testInt50() = assertEquals("cinquante", FrNumberReplacer.intToFrench(50))

    @Test
    fun testInt51_et() = assertEquals("cinquante et un", FrNumberReplacer.intToFrench(51))

    @Test
    fun testInt59() = assertEquals("cinquante-neuf", FrNumberReplacer.intToFrench(59))

    @Test
    fun testInt60() = assertEquals("soixante", FrNumberReplacer.intToFrench(60))

    @Test
    fun testInt61_et() = assertEquals("soixante et un", FrNumberReplacer.intToFrench(61))

    @Test
    fun testInt69() = assertEquals("soixante-neuf", FrNumberReplacer.intToFrench(69))

    // ==================== intToFrench: 70-99 (French additive system) ====================

    @Test
    fun testInt70() = assertEquals("soixante-dix", FrNumberReplacer.intToFrench(70))

    @Test
    fun testInt71_et() = assertEquals("soixante et onze", FrNumberReplacer.intToFrench(71))

    @Test
    fun testInt72() = assertEquals("soixante-douze", FrNumberReplacer.intToFrench(72))

    @Test
    fun testInt73() = assertEquals("soixante-treize", FrNumberReplacer.intToFrench(73))

    @Test
    fun testInt74() = assertEquals("soixante-quatorze", FrNumberReplacer.intToFrench(74))

    @Test
    fun testInt75() = assertEquals("soixante-quinze", FrNumberReplacer.intToFrench(75))

    @Test
    fun testInt76() = assertEquals("soixante-seize", FrNumberReplacer.intToFrench(76))

    @Test
    fun testInt77() = assertEquals("soixante-dix-sept", FrNumberReplacer.intToFrench(77))

    @Test
    fun testInt78() = assertEquals("soixante-dix-huit", FrNumberReplacer.intToFrench(78))

    @Test
    fun testInt79() = assertEquals("soixante-dix-neuf", FrNumberReplacer.intToFrench(79))

    @Test
    fun testInt80() = assertEquals("quatre-vingts", FrNumberReplacer.intToFrench(80))

    @Test
    fun testInt81() = assertEquals("quatre-vingt-un", FrNumberReplacer.intToFrench(81))

    @Test
    fun testInt82() = assertEquals("quatre-vingt-deux", FrNumberReplacer.intToFrench(82))

    @Test
    fun testInt89() = assertEquals("quatre-vingt-neuf", FrNumberReplacer.intToFrench(89))

    @Test
    fun testInt90() = assertEquals("quatre-vingt-dix", FrNumberReplacer.intToFrench(90))

    @Test
    fun testInt91() = assertEquals("quatre-vingt-onze", FrNumberReplacer.intToFrench(91))

    @Test
    fun testInt92() = assertEquals("quatre-vingt-douze", FrNumberReplacer.intToFrench(92))

    @Test
    fun testInt95() = assertEquals("quatre-vingt-quinze", FrNumberReplacer.intToFrench(95))

    @Test
    fun testInt99() = assertEquals("quatre-vingt-dix-neuf", FrNumberReplacer.intToFrench(99))

    // ==================== intToFrench: 100-999 (cent system) ====================

    @Test
    fun testInt100() = assertEquals("cent", FrNumberReplacer.intToFrench(100))

    @Test
    fun testInt101() = assertEquals("cent un", FrNumberReplacer.intToFrench(101))

    @Test
    fun testInt110() = assertEquals("cent dix", FrNumberReplacer.intToFrench(110))

    @Test
    fun testInt111() = assertEquals("cent onze", FrNumberReplacer.intToFrench(111))

    @Test
    fun testInt200() = assertEquals("deux cents", FrNumberReplacer.intToFrench(200))

    @Test
    fun testInt201() = assertEquals("deux cent un", FrNumberReplacer.intToFrench(201))

    @Test
    fun testInt300() = assertEquals("trois cents", FrNumberReplacer.intToFrench(300))

    @Test
    fun testInt500() = assertEquals("cinq cents", FrNumberReplacer.intToFrench(500))

    @Test
    fun testInt999() = assertEquals("neuf cent quatre-vingt-dix-neuf", FrNumberReplacer.intToFrench(999))

    // ==================== intToFrench: 1000+ ====================

    @Test
    fun testInt1000() = assertEquals("mille", FrNumberReplacer.intToFrench(1000))

    @Test
    fun testInt1001() = assertEquals("mille un", FrNumberReplacer.intToFrench(1001))

    @Test
    fun testInt1234() = assertEquals("mille deux cent trente-quatre", FrNumberReplacer.intToFrench(1234))

    @Test
    fun testInt2000() = assertEquals("deux mille", FrNumberReplacer.intToFrench(2000))

    @Test
    fun testInt10000() = assertEquals("dix mille", FrNumberReplacer.intToFrench(10000))

    @Test
    fun testInt50000() = assertEquals("cinquante mille", FrNumberReplacer.intToFrench(50000))

    @Test
    fun testInt100000() = assertEquals("cent mille", FrNumberReplacer.intToFrench(100000))

    @Test
    fun testInt500000() = assertEquals("cinq cents mille", FrNumberReplacer.intToFrench(500000))

    @Test
    fun testInt1000000() = assertEquals("un million", FrNumberReplacer.intToFrench(1000000))

    @Test
    fun testInt2000000() = assertEquals("deux millions", FrNumberReplacer.intToFrench(2000000))

    @Test
    fun testInt10000000() = assertEquals("dix millions", FrNumberReplacer.intToFrench(10000000))

    @Test
    fun testInt100000000() = assertEquals("cent millions", FrNumberReplacer.intToFrench(100000000))

    @Test
    fun testInt1000000000() = assertEquals("un milliard", FrNumberReplacer.intToFrench(1000000000))

    @Test
    fun testInt2000000000() = assertEquals("deux milliards", FrNumberReplacer.intToFrench(2000000000))

    // ==================== intToFrench: negative ====================

    @Test
    fun testIntNegative1() = assertEquals("moins un", FrNumberReplacer.intToFrench(-1))

    @Test
    fun testIntNegative12() = assertEquals("moins douze", FrNumberReplacer.intToFrench(-12))

    @Test
    fun testIntNegative100() = assertEquals("moins cent", FrNumberReplacer.intToFrench(-100))

    // ==================== floatToFrench ====================

    @Test
    fun testFloatZero() = assertEquals("zéro", FrNumberReplacer.floatToFrench(0.0))

    @Test
    fun testFloatPointFive() = assertEquals("zéro virgule cinq", FrNumberReplacer.floatToFrench(0.5))

    @Test
    fun testFloat314() = assertEquals("trois virgule un quatre", FrNumberReplacer.floatToFrench(3.14))

    @Test
    fun testFloatPointZeroOne() = assertEquals("zéro virgule zéro un", FrNumberReplacer.floatToFrench(0.01))

    @Test
    fun testFloatOne() = assertEquals("un", FrNumberReplacer.floatToFrench(1.0))

    @Test
    fun testFloatNegative() = assertEquals("moins trois virgule un quatre", FrNumberReplacer.floatToFrench(-3.14))

    // ==================== replace: float ====================

    @Test
    fun testReplaceFloat314() {
        assertEquals("trois virgule un quatre", replacer.replace("3,14"))
    }

    @Test
    fun testReplaceFloat0_5() {
        assertEquals("zéro virgule cinq", replacer.replace("0,5"))
    }

    @Test
    fun testReplaceFloatNegative() {
        assertEquals("moins un virgule cinq", replacer.replace("-1,5"))
    }

    // ==================== replace: date ====================

    @Test
    fun testReplaceDateDayMonthYear() {
        assertEquals("quinze mars deux mille vingt-quatre", replacer.replace("15 mars 2024"))
    }

    @Test
    fun testReplaceDateDayMonthYear2() {
        assertEquals("premier janvier deux mille", replacer.replace("1 janvier 2000"))
    }

    @Test
    fun testReplaceDateMonthDayYear() {
        assertEquals("quinze mars deux mille vingt-quatre", replacer.replace("mars 15, 2024"))
    }

    @Test
    fun testReplaceDateDayMonth() {
        assertEquals("quinze mars", replacer.replace("15 mars"))
    }

    @Test
    fun testReplaceDateAbbrevMonth() {
        assertEquals("quinze septembre deux mille vingt-quatre", replacer.replace("15 sep 2024"))
    }

    @Test
    fun testReplaceDateAbbrevJan() {
        assertEquals("premier janvier deux mille", replacer.replace("1 jan 2000"))
    }

    @Test
    fun testReplaceDateAbbrevDec() {
        assertEquals("vingt-cinq décembre deux mille vingt-quatre", replacer.replace("25 déc 2024"))
    }

    // ==================== replace: ordinal ====================

    @Test
    fun testReplaceOrdinal1er() {
        assertEquals("premier", replacer.replace("1er"))
    }

    @Test
    fun testReplaceOrdinal1ere() {
        assertEquals("première", replacer.replace("1ère"))
    }

    @Test
    fun testReplaceOrdinal2e() {
        assertEquals("deuxième", replacer.replace("2e"))
    }

    @Test
    fun testReplaceOrdinal3eme() {
        assertEquals("troisième", replacer.replace("3ème"))
    }

    @Test
    fun testReplaceOrdinal5e() {
        assertEquals("cinquième", replacer.replace("5e"))
    }

    @Test
    fun testReplaceOrdinal9e() {
        assertEquals("neuvième", replacer.replace("9e"))
    }

    @Test
    fun testReplaceOrdinal10e() {
        assertEquals("dixième", replacer.replace("10e"))
    }

    @Test
    fun testReplaceOrdinal21e() {
        assertEquals("vingt et unième", replacer.replace("21e"))
    }

    @Test
    fun testReplaceOrdinal80e() {
        assertEquals("quatre-vingtième", replacer.replace("80e"))
    }

    @Test
    fun testReplaceOrdinal90e() {
        assertEquals("quatre-vingt-dixième", replacer.replace("90e"))
    }

    @Test
    fun testReplaceOrdinal100e() {
        assertEquals("centième", replacer.replace("100e"))
    }

    @Test
    fun testReplaceOrdinal30e() {
        assertEquals("trentième", replacer.replace("30e"))
    }

    @Test
    fun testReplaceOrdinal50e() {
        assertEquals("cinquantième", replacer.replace("50e"))
    }

    @Test
    fun testReplaceOrdinal70e() {
        assertEquals("soixante-dixième", replacer.replace("70e"))
    }

    @Test
    fun testReplaceOrdinal1000e() {
        assertEquals("millième", replacer.replace("1000e"))
    }

    @Test
    fun testReplaceOrdinal2Feminine() {
        assertEquals("deuxième", replacer.replace("2ème"))
    }

    @Test
    fun testReplaceOrdinal1Feminine() {
        assertEquals("première", replacer.replace("1ère"))
    }

    @Test
    fun testReplaceOrdinal1Masculine() {
        assertEquals("premier", replacer.replace("1er"))
    }

    @Test
    fun testReplaceOrdinalInContext() {
        assertEquals("la première fois", replacer.replace("la 1ère fois"))
    }

    @Test
    fun testReplaceOrdinalSpecialSpelling4() {
        assertEquals("quatrième", replacer.replace("4e"))
    }

    // ==================== replace: year ====================

    @Test
    fun testReplaceYear2024() {
        assertEquals("en deux mille vingt-quatre", replacer.replace("en 2024"))
    }

    @Test
    fun testReplaceYear1999() {
        assertEquals("depuis mille neuf cent quatre-vingt-dix-neuf", replacer.replace("depuis 1999"))
    }

    @Test
    fun testReplaceYear1500() {
        assertEquals("en mille cinq cents", replacer.replace("en 1500"))
    }

    @Test
    fun testReplaceYear1900() {
        assertEquals("en mille neuf cents", replacer.replace("en 1900"))
    }

    // ==================== replace: time ====================

    @Test
    fun testReplaceTime1430() {
        assertEquals("quatorze heures trente", replacer.replace("14:30"))
    }

    @Test
    fun testReplaceTime900() {
        assertEquals("neuf heures", replacer.replace("9:00"))
    }

    @Test
    fun testReplaceTime2359() {
        assertEquals("vingt-trois heures cinquante-neuf", replacer.replace("23:59"))
    }

    @Test
    fun testReplaceTimeWithSeconds() {
        assertEquals("neuf heures cinq trente", replacer.replace("9:05:30"))
    }

    // ==================== replace: temperature ====================

    @Test
    fun testReplaceTempCelsius() {
        assertEquals("vingt-huit degrés Celsius", replacer.replace("28°C"))
    }

    @Test
    fun testReplaceTemp1C_Singular() {
        assertEquals("un degré Celsius", replacer.replace("1°C"))
    }

    @Test
    fun testReplaceTempNegative() {
        assertEquals("moins cinq degrés Celsius", replacer.replace("-5°C"))
    }

    @Test
    fun testReplaceTempFahrenheit() {
        assertEquals("soixante-douze degrés Fahrenheit", replacer.replace("72°F"))
    }

    @Test
    fun testReplaceTempRange() {
        assertEquals("moins cinq à dix degrés Celsius", replacer.replace("-5~10°C"))
    }

    @Test
    fun testReplaceTemp0C() {
        assertEquals("zéro degrés Celsius", replacer.replace("0°C"))
    }

    @Test
    fun testReplaceTempFloat() {
        assertEquals("trente-six virgule cinq degrés Celsius", replacer.replace("36.5°C"))
    }

    @Test
    fun testReplaceTempRangeNegative() {
        assertEquals("moins dix à zéro degrés Celsius", replacer.replace("-10~0°C"))
    }

    @Test
    fun testReplaceTempRangeFahrenheit() {
        assertEquals("trente-deux à soixante-douze degrés Fahrenheit", replacer.replace("32~72°F"))
    }

    // ==================== replace: percent ====================

    @Test
    fun testReplacePercent50() {
        assertEquals("cinquante pour cent", replacer.replace("50%"))
    }

    @Test
    fun testReplacePercent1() {
        assertEquals("un pour cent", replacer.replace("1%"))
    }

    @Test
    fun testReplacePercentFloat() {
        assertEquals("trois virgule un quatre pour cent", replacer.replace("3,14%"))
    }

    @Test
    fun testReplacePercent100() {
        assertEquals("cent pour cent", replacer.replace("100%"))
    }

    @Test
    fun testReplacePercent99_9() {
        assertEquals("quatre-vingt-dix-neuf virgule neuf pour cent", replacer.replace("99,9%"))
    }

    // ==================== replace: currency ====================

    @Test
    fun testReplaceEur50() {
        assertEquals("cinquante euros", replacer.replace("€50"))
    }

    @Test
    fun testReplaceEur1() {
        assertEquals("un euro", replacer.replace("€1"))
    }

    @Test
    fun testReplaceEurWithCents() {
        assertEquals("vingt-cinq euros et quatre-vingt-dix-neuf centimes", replacer.replace("€25,99"))
    }

    @Test
    fun testReplaceEurCentsOnly() {
        assertEquals("cinquante centimes", replacer.replace("€0,50"))
    }

    @Test
    fun testReplaceUsd100() {
        assertEquals("cent dollars", replacer.replace("\$100"))
    }

    @Test
    fun testReplaceUsd1() {
        assertEquals("un dollar", replacer.replace("\$1"))
    }

    @Test
    fun testReplaceUsdWithCents() {
        assertEquals("cinq dollars et quatre-vingt-dix-neuf cents", replacer.replace("\$5.99"))
    }

    @Test
    fun testReplaceUsdCommaDecimal() {
        assertEquals("trois dollars et cinquante cents", replacer.replace("\$3,50"))
    }

    @Test
    fun testReplaceEur1Centime() {
        assertEquals("un euro et un centime", replacer.replace("€1,01"))
    }

    @Test
    fun testReplaceEur01() {
        assertEquals("un centime", replacer.replace("€0,01"))
    }

    // ==================== replace: scientific notation ====================

    @Test
    fun testReplaceSciFloat() {
        assertEquals("un virgule cinq fois dix à la dixième", replacer.replace("1.5e10"))
    }

    @Test
    fun testReplaceSciNegative() {
        assertEquals("deux fois dix à la moins huitième", replacer.replace("2e-8"))
    }

    @Test
    fun testReplaceSciInt() {
        assertEquals("trois fois dix à la sixième", replacer.replace("3E6"))
    }

    // ==================== replace: thousand separator ====================

    @Test
    fun testReplaceThousandSep1000() {
        assertEquals("mille", replacer.replace("1 000"))
    }

    @Test
    fun testReplaceThousandSep1500() {
        assertEquals("mille cinq cents", replacer.replace("1 500"))
    }

    @Test
    fun testReplaceThousandSepMillion() {
        assertEquals("un million cinq cents mille", replacer.replace("1 500 000"))
    }

    @Test
    fun testReplaceThousandSepWithDecimal() {
        assertEquals("un million deux cent trente-quatre mille cinq cent soixante-sept virgule huit neuf", replacer.replace("1 234 567,89"))
    }

    @Test
    fun testReplaceThousandSepWithDecimalSmall() {
        assertEquals("mille deux cent trente-quatre virgule cinq six", replacer.replace("1 234,56"))
    }

    // ==================== replace: range ====================

    @Test
    fun testReplaceRange1020() {
        assertEquals("dix à vingt", replacer.replace("10-20"))
    }

    @Test
    fun testReplaceRangeFloat() {
        assertEquals("un virgule cinq à trois virgule un quatre", replacer.replace("1,5-3,14"))
    }

    @Test
    fun testReplaceRangeTilde() {
        assertEquals("dix à vingt", replacer.replace("10~20"))
    }

    @Test
    fun testReplaceRangeInContext() {
        assertEquals("de dix à vingt personnes", replacer.replace("de 10-20 personnes"))
    }

    // ==================== replace: fraction ====================

    @Test
    fun testReplaceFraction12() {
        assertEquals("un demi", replacer.replace("1/2"))
    }

    @Test
    fun testReplaceFraction13() {
        assertEquals("un tiers", replacer.replace("1/3"))
    }

    @Test
    fun testReplaceFraction14() {
        assertEquals("un quart", replacer.replace("1/4"))
    }

    @Test
    fun testReplaceFraction34() {
        assertEquals("trois quarts", replacer.replace("3/4"))
    }

    @Test
    fun testReplaceFraction15() {
        assertEquals("un cinquième", replacer.replace("1/5"))
    }

    @Test
    fun testReplaceFraction37() {
        assertEquals("trois septièmes", replacer.replace("3/7"))
    }

    // ==================== replace: score ====================

    @Test
    fun testReplaceScore32() {
        assertEquals("trois à deux", replacer.replace("3:2"))
    }

    @Test
    fun testReplaceScore00() {
        assertEquals("zéro à zéro", replacer.replace("0:0"))
    }

    // ==================== replace: roman numerals ====================

    @Test
    fun testReplaceRomanXII() {
        assertEquals("douze", replacer.replace("XII"))
    }

    @Test
    fun testReplaceRomanMCMXCIX() {
        assertEquals("mille neuf cent quatre-vingt-dix-neuf", replacer.replace("MCMXCIX"))
    }

    @Test
    fun testReplaceRomanI() {
        assertEquals("I", replacer.replace("I"))
    }

    @Test
    fun testReplaceRomanIV() {
        assertEquals("quatre", replacer.replace("IV"))
    }

    @Test
    fun testReplaceRomanXL() {
        assertEquals("quarante", replacer.replace("XL"))
    }

    @Test
    fun testReplaceRomanMM() {
        assertEquals("deux mille", replacer.replace("MM"))
    }

    // ==================== replace: phone ====================

    @Test
    fun testReplacePhoneStart() {
        assertEquals("zéro un deux trois quatre cinq six sept huit neuf",
            replacer.replace("012-345-6789"))
    }

    // ==================== replace: integer ====================

    @Test
    fun testReplaceInt123() {
        assertEquals("cent vingt-trois", replacer.replace("123"))
    }

    @Test
    fun testReplaceInt999() {
        assertEquals("neuf cent quatre-vingt-dix-neuf", replacer.replace("999"))
    }

    @Test
    fun testReplaceInt1000000() {
        assertEquals("un million", replacer.replace("1000000"))
    }

    @Test
    fun testReplaceInt0() {
        assertEquals("zéro", replacer.replace("0"))
    }

    @Test
    fun testReplaceIntInContext() {
        assertEquals("J'ai vingt ans", replacer.replace("J'ai 20 ans"))
    }

    @Test
    fun testReplaceIntNegativeInContext() {
        assertEquals("Il fait moins cinq", replacer.replace("Il fait -5"))
    }

    // ==================== replace: French additive special cases ====================

    @Test
    fun testReplaceAdditive70() {
        assertEquals("soixante-dix personnes", replacer.replace("70 personnes"))
    }

    @Test
    fun testReplaceAdditive71() {
        assertEquals("soixante et onze ans", replacer.replace("71 ans"))
    }

    @Test
    fun testReplaceAdditive80() {
        assertEquals("quatre-vingts pages", replacer.replace("80 pages"))
    }

    @Test
    fun testReplaceAdditive81() {
        assertEquals("quatre-vingt-un ans", replacer.replace("81 ans"))
    }

    @Test
    fun testReplaceAdditive90() {
        assertEquals("quatre-vingt-dix jours", replacer.replace("90 jours"))
    }

    @Test
    fun testReplaceAdditive99() {
        assertEquals("quatre-vingt-dix-neuf francs", replacer.replace("99 francs"))
    }

    // ==================== replace: cent plural ====================

    @Test
    fun testReplaceCent200() {
        assertEquals("deux cents", replacer.replace("200"))
    }

    @Test
    fun testReplaceCent201() {
        assertEquals("deux cent un", replacer.replace("201"))
    }

    @Test
    fun testReplaceCent500() {
        assertEquals("cinq cents", replacer.replace("500"))
    }

    // ==================== replace: mille invariable ====================

    @Test
    fun testReplaceMille2000() {
        assertEquals("deux mille", replacer.replace("2000"))
    }

    @Test
    fun testReplaceMille3000() {
        assertEquals("trois mille", replacer.replace("3000"))
    }

    // ==================== replace: mixed context ====================

    @Test
    fun testReplaceMixedFull() {
        assertEquals("J'ai vingt-cinq ans et cent euros", replacer.replace("J'ai 25 ans et €100"))
    }

    @Test
    fun testReplaceNumberInFrenchText() {
        assertEquals("Il y a quarante-deux élèves dans la classe", replacer.replace("Il y a 42 élèves dans la classe"))
    }

    @Test
    fun testReplaceMultipleNumbers() {
        assertEquals("un et deux et trois", replacer.replace("1 et 2 et 3"))
    }

    // ==================== negative tests ====================

    @Test
    fun testNegativeChemicalFormula() {
        assertEquals("H2O", replacer.replace("H2O"))
    }

    @Test
    fun testNegativeCO2() {
        assertEquals("CO2", replacer.replace("CO2"))
    }

    @Test
    fun testNegative3D() {
        assertEquals("3D", replacer.replace("3D"))
    }

    @Test
    fun testNegativeISBN() {
        val result = replacer.replace("ISBN 978-3-16-123456-7")
        assert(result.contains("ISBN"))
    }

    @Test
    fun testNegativeVersionNumber() {
        val result = replacer.replace("version 2.0.1")
        assert(result.contains("version"))
    }

    @Test
    fun testNegativeIPAddress() {
        val result = replacer.replace("192.168.1.1")
        assert(result.contains("virgule") || result.contains("cent"))
    }

    @Test
    fun testNegativeAlphanumeric() {
        assertEquals("3D film", replacer.replace("3D film"))
    }

    @Test
    fun testNegativeFlightNumber() {
        val result = replacer.replace("vol AF1234")
        assert(result.contains("vol"))
    }

    @Test
    fun testNegativeModelNumber() {
        val result = replacer.replace("modèle X200")
        assert(result.contains("modèle"))
    }

    @Test
    fun testNegativeRomanI() {
        assertEquals("Je suis là", replacer.replace("Je suis là"))
    }

    @Test
    fun testNegativeAlreadyFrench() {
        assertEquals("cent vingt-trois", replacer.replace("cent vingt-trois"))
    }

    @Test
    fun testNegativeBracketedReference() {
        assertEquals("voir [trois] pour plus", replacer.replace("voir [3] pour plus"))
    }

    // ==================== negative: accented character boundary ====================

    @Test
    fun testNegativeAccentedCafe() {
        assertEquals("Nous avons pris café à trois heures", replacer.replace("Nous avons pris café à 3 heures"))
    }

    @Test
    fun testNegativeAccentedResume() {
        assertEquals("Son résumé liste cinq emplois", replacer.replace("Son résumé liste 5 emplois"))
    }

    @Test
    fun testNegativeAccentedFiancee() {
        assertEquals("Ma fiancée a vingt-huit ans", replacer.replace("Ma fiancée a 28 ans"))
    }

    @Test
    fun testNegativeAccentedNaive() {
        assertEquals("Un enfant naïf de cinq ans", replacer.replace("Un enfant naïf de 5 ans"))
    }

    // ==================== PRODUCTION: real-world ebook paragraphs ====================

    @Test
    fun testProductionNovelOpening() {
        val input = "Le 15 mars 2024, la température à Paris a atteint 28°C. Plus de 2 millions de personnes ont participé à l'événement."
        val expected = "Le quinze mars deux mille vingt-quatre, la température à Paris a atteint vingt-huit degrés Celsius. Plus de deux millions de personnes ont participé à l'événement."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionScienceArticle() {
        val input = "La vitesse de la lumière est d'environ 300 000 km/s. L'eau bout à 100°C au niveau de la mer."
        val expected = "La vitesse de la lumière est d'environ trois cents mille km/s. L'eau bout à cent degrés Celsius au niveau de la mer."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionCookingRecipe() {
        val input = "Préchauffer le four à 180°C. Mélanger 500 grammes de farine avec 1/2 cuillère à café de sel. Cuire pendant 25-30 minutes."
        val expected = "Préchauffer le four à cent quatre-vingts degrés Celsius. Mélanger cinq cents grammes de farine avec un demi cuillère à café de sel. Cuire pendant vingt-cinq à trente minutes."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionFinancialNews() {
        val input = "Le PIB a augmenté de 2,5% en 2024. L'inflation s'est maintenue à 3,1%. Le chômage a baissé à 11,7%."
        val expected = "Le PIB a augmenté de deux virgule cinq pour cent en deux mille vingt-quatre. L'inflation s'est maintenue à trois virgule un pour cent. Le chômage a baissé à onze virgule sept pour cent."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionHistoryText() {
        val input = "Le 14 juillet 1789, la Bastille a été prise. Au 19e siècle, la France s'est étendue dans le monde."
        val expected = "Le quatorze juillet mille sept cent quatre-vingt-neuf, la Bastille a été prise. Au dix-neuvième siècle, la France s'est étendue dans le monde."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionMixedContent() {
        val input = "En 2024, la population de la France était de 68 millions. Le salaire moyen est de 2500 euros par mois. La TVA est de 20%."
        val expected = "En deux mille vingt-quatre, la population de la France était de soixante-huit millions. Le salaire moyen est de deux mille cinq cents euros par mois. La TVA est de vingt pour cent."
        assertEquals(expected, replacer.replace(input))
    }

    // ==================== edge cases ====================

    @Test
    fun testReplaceEmpty() {
        assertEquals("", replacer.replace(""))
    }

    @Test
    fun testReplaceNoNumbers() {
        assertEquals("Bonjour le monde", replacer.replace("Bonjour le monde"))
    }

    @Test
    fun testReplaceOnlySpaces() {
        assertEquals("   ", replacer.replace("   "))
    }

    @Test
    fun testReplaceAlreadySpelled() {
        assertEquals("cent vingt-trois", replacer.replace("cent vingt-trois"))
    }

    @Test
    fun testReplaceRulesNoConflict() {
        val input = "Le 15 mars 2024, à 14:30, la température était de 25°C avec 50% d'humidité."
        val expected = "Le quinze mars deux mille vingt-quatre, à quatorze heures trente, la température était de vingt-cinq degrés Celsius avec cinquante pour cent d'humidité."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionLiterature() {
        val input = "Il avait exactement 72 ans lorsque, le 14 juillet 1889, il décida de parcourir les 850 kilomètres qui séparaient Paris de Marseille. Il marcha pendant 23 jours, traversant 12 départements et dormant dans 45 auberges différentes."
        val expected = "Il avait exactement soixante-douze ans lorsque, le quatorze juillet mille huit cent quatre-vingt-neuf, il décida de parcourir les huit cent cinquante kilomètres qui séparaient Paris de Marseille. Il marcha pendant vingt-trois jours, traversant douze départements et dormant dans quarante-cinq auberges différentes."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionTTSParagraph() {
        val input = "L'exposé aura lieu le 3 février à 9:00 dans la salle 201. Le conférencier présentera les résultats de l'étude portant sur 1500 sujets, avec une marge d'erreur de 2,5%. L'entrée coûte €15 ou €7,50 pour les étudiants."
        val expected = "L'exposé aura lieu le trois février à neuf heures dans la salle deux cent un. Le conférencier présentera les résultats de l'étude portant sur mille cinq cents sujets, avec une marge d'erreur de deux virgule cinq pour cent. L'entrée coûte quinze euros ou sept euros et cinquante centimes pour les étudiants."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionNewsSport() {
        val input = "Le PSG a gagné 3:1 contre Lyon. Devant 48 000 spectateurs, Mbappé a marqué 2 buts. La température était de -2°C et le match a duré 1/4 d'heure de plus."
        val expected = "Le PSG a gagné trois à un contre Lyon. Devant quarante-huit mille spectateurs, Mbappé a marqué deux buts. La température était de moins deux degrés Celsius et le match a duré un quart d'heure de plus."
        assertEquals(expected, replacer.replace(input))
    }
}
