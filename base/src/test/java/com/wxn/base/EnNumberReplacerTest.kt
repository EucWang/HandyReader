package com.wxn.base

import com.wxn.base.util.numReplacer.EnNumberReplacer
import org.junit.Assert.assertEquals
import org.junit.Test

class EnNumberReplacerTest {

    private val replacer = EnNumberReplacer()

    // ==================== intToEnglish: 0~19 ====================

    @Test
    fun testIntZero() {
        assertEquals("zero", EnNumberReplacer.intToEnglish(0))
    }

    @Test
    fun testIntOne() {
        assertEquals("one", EnNumberReplacer.intToEnglish(1))
    }

    @Test
    fun testIntNine() {
        assertEquals("nine", EnNumberReplacer.intToEnglish(9))
    }

    @Test
    fun testIntTen() {
        assertEquals("ten", EnNumberReplacer.intToEnglish(10))
    }

    @Test
    fun testIntEleven() {
        assertEquals("eleven", EnNumberReplacer.intToEnglish(11))
    }

    @Test
    fun testIntThirteen() {
        assertEquals("thirteen", EnNumberReplacer.intToEnglish(13))
    }

    @Test
    fun testIntFifteen() {
        assertEquals("fifteen", EnNumberReplacer.intToEnglish(15))
    }

    @Test
    fun testIntEighteen() {
        assertEquals("eighteen", EnNumberReplacer.intToEnglish(18))
    }

    @Test
    fun testIntNineteen() {
        assertEquals("nineteen", EnNumberReplacer.intToEnglish(19))
    }

    // ==================== intToEnglish: 20~99 ====================

    @Test
    fun testIntTwenty() {
        assertEquals("twenty", EnNumberReplacer.intToEnglish(20))
    }

    @Test
    fun testIntTwentyOne() {
        assertEquals("twenty-one", EnNumberReplacer.intToEnglish(21))
    }

    @Test
    fun testIntThirty() {
        assertEquals("thirty", EnNumberReplacer.intToEnglish(30))
    }

    @Test
    fun testIntFortyFive() {
        assertEquals("forty-five", EnNumberReplacer.intToEnglish(45))
    }

    @Test
    fun testIntNinetyNine() {
        assertEquals("ninety-nine", EnNumberReplacer.intToEnglish(99))
    }

    // ==================== intToEnglish: 100~999 ====================

    @Test
    fun testIntOneHundred() {
        assertEquals("one hundred", EnNumberReplacer.intToEnglish(100))
    }

    @Test
    fun testIntOneHundredOne() {
        assertEquals("one hundred and one", EnNumberReplacer.intToEnglish(101))
    }

    @Test
    fun testIntOneHundredTen() {
        assertEquals("one hundred and ten", EnNumberReplacer.intToEnglish(110))
    }

    @Test
    fun testIntOneHundredEleven() {
        assertEquals("one hundred and eleven", EnNumberReplacer.intToEnglish(111))
    }

    @Test
    fun testIntTwoHundred() {
        assertEquals("two hundred", EnNumberReplacer.intToEnglish(200))
    }

    @Test
    fun testIntTwoHundredFiftySix() {
        assertEquals("two hundred and fifty-six", EnNumberReplacer.intToEnglish(256))
    }

    @Test
    fun testIntNineHundredNinetyNine() {
        assertEquals("nine hundred and ninety-nine", EnNumberReplacer.intToEnglish(999))
    }

    // ==================== intToEnglish: 1000~999999 ====================

    @Test
    fun testIntOneThousand() {
        assertEquals("one thousand", EnNumberReplacer.intToEnglish(1000))
    }

    @Test
    fun testIntOneThousandOne() {
        assertEquals("one thousand and one", EnNumberReplacer.intToEnglish(1001))
    }

    @Test
    fun testIntOneThousandOneHundred() {
        assertEquals("one thousand one hundred", EnNumberReplacer.intToEnglish(1100))
    }

    @Test
    fun testIntOneThousandTwoHundredThirtyFour() {
        assertEquals("one thousand two hundred and thirty-four", EnNumberReplacer.intToEnglish(1234))
    }

    @Test
    fun testIntTenThousand() {
        assertEquals("ten thousand", EnNumberReplacer.intToEnglish(10000))
    }

    @Test
    fun testIntOneHundredThousand() {
        assertEquals("one hundred thousand", EnNumberReplacer.intToEnglish(100000))
    }

    @Test
    fun testIntNineHundredNinetyNineThousand() {
        assertEquals("nine hundred and ninety-nine thousand nine hundred and ninety-nine", EnNumberReplacer.intToEnglish(999999))
    }

    // ==================== intToEnglish: millions, billions ====================

    @Test
    fun testIntOneMillion() {
        assertEquals("one million", EnNumberReplacer.intToEnglish(1000000))
    }

    @Test
    fun testIntOneBillion() {
        assertEquals("one billion", EnNumberReplacer.intToEnglish(1000000000))
    }

    @Test
    fun testInt1234567890() {
        assertEquals("one billion two hundred and thirty-four million five hundred and sixty-seven thousand eight hundred and ninety", EnNumberReplacer.intToEnglish(1234567890))
    }

    // ==================== intToEnglish: negative ====================

    @Test
    fun testIntNegativeOne() {
        assertEquals("minus one", EnNumberReplacer.intToEnglish(-1))
    }

    @Test
    fun testIntNegativeOneHundred() {
        assertEquals("minus one hundred", EnNumberReplacer.intToEnglish(-100))
    }

    @Test
    fun testIntNegative1234() {
        assertEquals("minus one thousand two hundred and thirty-four", EnNumberReplacer.intToEnglish(-1234))
    }

    // ==================== floatToEnglish ====================

    @Test
    fun testFloatZero() {
        assertEquals("zero", EnNumberReplacer.floatToEnglish(0.0))
    }

    @Test
    fun testFloatOne() {
        assertEquals("one", EnNumberReplacer.floatToEnglish(1.0))
    }

    @Test
    fun testFloatPointFive() {
        assertEquals("zero point five", EnNumberReplacer.floatToEnglish(0.5))
    }

    @Test
    fun testFloat314() {
        assertEquals("three point one four", EnNumberReplacer.floatToEnglish(3.14))
    }

    @Test
    fun testFloatNegative() {
        assertEquals("minus five point five", EnNumberReplacer.floatToEnglish(-5.5))
    }

    @Test
    fun testFloatPointZeroOne() {
        assertEquals("zero point zero one", EnNumberReplacer.floatToEnglish(0.01))
    }

    @Test
    fun testFloat123Point456() {
        assertEquals("one hundred and twenty-three point four five six", EnNumberReplacer.floatToEnglish(123.456))
    }

    // ==================== ordinalToEnglish ====================

    @Test
    fun testOrdinal1() {
        assertEquals("first", EnNumberReplacer.ordinalToEnglish(1))
    }

    @Test
    fun testOrdinal2() {
        assertEquals("second", EnNumberReplacer.ordinalToEnglish(2))
    }

    @Test
    fun testOrdinal3() {
        assertEquals("third", EnNumberReplacer.ordinalToEnglish(3))
    }

    @Test
    fun testOrdinal4() {
        assertEquals("fourth", EnNumberReplacer.ordinalToEnglish(4))
    }

    @Test
    fun testOrdinal11() {
        assertEquals("eleventh", EnNumberReplacer.ordinalToEnglish(11))
    }

    @Test
    fun testOrdinal12() {
        assertEquals("twelfth", EnNumberReplacer.ordinalToEnglish(12))
    }

    @Test
    fun testOrdinal13() {
        assertEquals("thirteenth", EnNumberReplacer.ordinalToEnglish(13))
    }

    @Test
    fun testOrdinal21() {
        assertEquals("twenty-first", EnNumberReplacer.ordinalToEnglish(21))
    }

    @Test
    fun testOrdinal22() {
        assertEquals("twenty-second", EnNumberReplacer.ordinalToEnglish(22))
    }

    @Test
    fun testOrdinal23() {
        assertEquals("twenty-third", EnNumberReplacer.ordinalToEnglish(23))
    }

    @Test
    fun testOrdinal100() {
        assertEquals("one hundredth", EnNumberReplacer.ordinalToEnglish(100))
    }

    @Test
    fun testOrdinal101() {
        assertEquals("one hundred and first", EnNumberReplacer.ordinalToEnglish(101))
    }

    @Test
    fun testOrdinal1000() {
        assertEquals("one thousandth", EnNumberReplacer.ordinalToEnglish(1000))
    }

    // ==================== yearToEnglish ====================

    @Test
    fun testYear1066() {
        assertEquals("ten sixty-six", EnNumberReplacer.yearToEnglish(1066))
    }

    @Test
    fun testYear1900() {
        assertEquals("nineteen hundred", EnNumberReplacer.yearToEnglish(1900))
    }

    @Test
    fun testYear1999() {
        assertEquals("nineteen ninety-nine", EnNumberReplacer.yearToEnglish(1999))
    }

    @Test
    fun testYear2000() {
        assertEquals("two thousand", EnNumberReplacer.yearToEnglish(2000))
    }

    @Test
    fun testYear2008() {
        assertEquals("two thousand and eight", EnNumberReplacer.yearToEnglish(2008))
    }

    @Test
    fun testYear2024() {
        assertEquals("twenty twenty-four", EnNumberReplacer.yearToEnglish(2024))
    }

    @Test
    fun testYear2100() {
        assertEquals("twenty-one hundred", EnNumberReplacer.yearToEnglish(2100))
    }

    @Test
    fun testYear100() {
        assertEquals("one hundred", EnNumberReplacer.yearToEnglish(100))
    }

    // ==================== phoneToEnglish ====================

    @Test
    fun testPhoneBasic() {
        assertEquals("one two three four five six seven eight nine oh", EnNumberReplacer.phoneToEnglish("1234567890"))
    }

    @Test
    fun testPhoneWithZero() {
        assertEquals("oh one two oh three oh four", EnNumberReplacer.phoneToEnglish("0120304"))
    }

    // ==================== fractionToEnglish ====================

    @Test
    fun testFractionHalf() {
        assertEquals("one half", EnNumberReplacer.fractionToEnglish(1, 2))
    }

    @Test
    fun testFractionThird() {
        assertEquals("one third", EnNumberReplacer.fractionToEnglish(1, 3))
    }

    @Test
    fun testFractionTwoThirds() {
        assertEquals("two thirds", EnNumberReplacer.fractionToEnglish(2, 3))
    }

    @Test
    fun testFractionQuarter() {
        assertEquals("one quarter", EnNumberReplacer.fractionToEnglish(1, 4))
    }

    @Test
    fun testFractionThreeQuarters() {
        assertEquals("three quarters", EnNumberReplacer.fractionToEnglish(3, 4))
    }

    @Test
    fun testFractionFifth() {
        assertEquals("one fifth", EnNumberReplacer.fractionToEnglish(1, 5))
    }

    @Test
    fun testFractionThreeFifths() {
        assertEquals("three fifths", EnNumberReplacer.fractionToEnglish(3, 5))
    }

    @Test
    fun testFractionOneTenth() {
        assertEquals("one tenth", EnNumberReplacer.fractionToEnglish(1, 10))
    }

    @Test
    fun testFractionGeneric() {
        assertEquals("twenty-two sevenths", EnNumberReplacer.fractionToEnglish(22, 7))
    }

    @Test
    fun testFractionOneHundredth() {
        assertEquals("one one-hundredth", EnNumberReplacer.fractionToEnglish(1, 100))
    }

    @Test
    fun testFractionThreeHundredths() {
        assertEquals("three one-hundredths", EnNumberReplacer.fractionToEnglish(3, 100))
    }

    // ==================== replace: ordinal suffixes ====================

    @Test
    fun testReplaceOrdinal1st() {
        assertEquals("the first place", replacer.replace("the 1st place"))
    }

    @Test
    fun testReplaceOrdinal2nd() {
        assertEquals("the second floor", replacer.replace("the 2nd floor"))
    }

    @Test
    fun testReplaceOrdinal3rd() {
        assertEquals("the third time", replacer.replace("the 3rd time"))
    }

    @Test
    fun testReplaceOrdinal4th() {
        assertEquals("the fourth day", replacer.replace("the 4th day"))
    }

    @Test
    fun testReplaceOrdinal21st() {
        assertEquals("the twenty-first century", replacer.replace("the 21st century"))
    }

    @Test
    fun testReplaceOrdinal100th() {
        assertEquals("the one hundredth anniversary", replacer.replace("the 100th anniversary"))
    }

    // ==================== replace: dates ====================

    @Test
    fun testReplaceDateMonthDay() {
        assertEquals("March fifteenth", replacer.replace("March 15"))
    }

    @Test
    fun testReplaceDateMonthDayYear() {
        assertEquals("March fifteenth, twenty twenty-four", replacer.replace("March 15, 2024"))
    }

    @Test
    fun testReplaceDateDayMonthYear() {
        assertEquals("the fifteenth of March twenty twenty-four", replacer.replace("15 March 2024"))
    }

    @Test
    fun testReplaceDateAbbrevMonth() {
        assertEquals("January first", replacer.replace("Jan 1"))
    }

    @Test
    fun testReplaceDateAbbrevMonthWithDot() {
        assertEquals("February second, twenty twenty", replacer.replace("Feb. 2, 2020"))
    }

    // ==================== replace: year in context ====================

    @Test
    fun testReplaceYearInContext() {
        assertEquals("in twenty twenty-four", replacer.replace("in 2024"))
    }

    @Test
    fun testReplaceYearSinceContext() {
        assertEquals("since nineteen ninety-nine", replacer.replace("since 1999"))
    }

    @Test
    fun testReplaceYearFromContext() {
        assertEquals("from two thousand", replacer.replace("from 2000"))
    }

    // ==================== replace: time ====================

    @Test
    fun testReplaceTimeOnTheHour() {
        assertEquals("three o'clock", replacer.replace("3:00"))
    }

    @Test
    fun testReplaceTimeWithMinutes() {
        assertEquals("two thirty", replacer.replace("2:30"))
    }

    @Test
    fun testReplaceTimeWithSeconds() {
        assertEquals("ten fifteen and thirty seconds", replacer.replace("10:15:30"))
    }

    @Test
    fun testReplaceTime24Hour() {
        assertEquals("fourteen thirty", replacer.replace("14:30"))
    }

    @Test
    fun testReplaceTimeInSentence() {
        assertEquals("The meeting is at two thirty tomorrow", replacer.replace("The meeting is at 2:30 tomorrow"))
    }

    // ==================== replace: temperature range ====================

    @Test
    fun testReplaceTempRangeCelsius() {
        assertEquals("twenty to thirty degrees Celsius", replacer.replace("20-30°C"))
    }

    @Test
    fun testReplaceTempRangeFahrenheit() {
        assertEquals("sixty-eight to eighty degrees Fahrenheit", replacer.replace("68-80°F"))
    }

    @Test
    fun testReplaceTempRangeNegative() {
        assertEquals("minus five to five degrees Celsius", replacer.replace("-5-5°C"))
    }

    // ==================== replace: phone numbers ====================

    @Test
    fun testReplacePhoneUS() {
        assertEquals("call five five five one two three four five six seven", replacer.replace("call 555-123-4567"))
    }

    @Test
    fun testReplacePhoneInternational() {
        assertEquals("dial one eight oh oh five five five oh one two three", replacer.replace("dial +1-800-555-0123"))
    }

    @Test
    fun testReplacePhoneParentheses() {
        assertEquals("call five five five one two three four five six seven", replacer.replace("call (555) 123-4567"))
    }

    // ==================== replace: percentage ====================

    @Test
    fun testReplacePercent50() {
        assertEquals("fifty percent", replacer.replace("50%"))
    }

    @Test
    fun testReplacePercentFloat() {
        assertEquals("three point one four percent", replacer.replace("3.14%"))
    }

    @Test
    fun testReplacePercentOne() {
        assertEquals("one percent", replacer.replace("1%"))
    }

    // ==================== replace: temperature ====================

    @Test
    fun testReplaceTempCelsius() {
        assertEquals("twenty degrees Celsius", replacer.replace("20°C"))
    }

    @Test
    fun testReplaceTempFahrenheit() {
        assertEquals("sixty-eight degrees Fahrenheit", replacer.replace("68°F"))
    }

    @Test
    fun testReplaceTempNegative() {
        assertEquals("minus five degrees Celsius", replacer.replace("-5°C"))
    }

    @Test
    fun testReplaceTempFloat() {
        assertEquals("thirty-seven point five degrees Celsius", replacer.replace("37.5°C"))
    }

    // ==================== replace: currency ====================

    @Test
    fun testReplaceUSD() {
        assertEquals("one hundred dollars", replacer.replace("$100"))
    }

    @Test
    fun testReplaceUSDWithCents() {
        assertEquals("one hundred dollars and fifty cents", replacer.replace("$100.50"))
    }

    @Test
    fun testReplaceUSDNoCents() {
        assertEquals("fifty dollars", replacer.replace("$50"))
    }

    @Test
    fun testReplaceEuro() {
        assertEquals("thirty euros", replacer.replace("€30"))
    }

    @Test
    fun testReplaceEuroWithCents() {
        assertEquals("ninety-nine euros and ninety-nine cents", replacer.replace("€99.99"))
    }

    @Test
    fun testReplacePound() {
        assertEquals("twenty-five pounds", replacer.replace("£25"))
    }

    @Test
    fun testReplacePoundWithPence() {
        assertEquals("twenty-five pounds and ninety-nine pence", replacer.replace("£25.99"))
    }

    // ==================== replace: scientific notation ====================

    @Test
    fun testReplaceSciFloat() {
        assertEquals("one point five times ten to the tenth", replacer.replace("1.5e10"))
    }

    @Test
    fun testReplaceSciInt() {
        assertEquals("three times ten to the fifth", replacer.replace("3e5"))
    }

    @Test
    fun testReplaceSciNegativeExp() {
        assertEquals("one times ten to the minus third", replacer.replace("1e-3"))
    }

    // ==================== replace: float ====================

    @Test
    fun testReplaceFloat314() {
        assertEquals("three point one four", replacer.replace("3.14"))
    }

    @Test
    fun testReplaceFloatNegative() {
        assertEquals("minus five point five", replacer.replace("-5.5"))
    }

    @Test
    fun testReplaceFloatZero() {
        assertEquals("zero point one", replacer.replace("0.1"))
    }

    // ==================== replace: fraction ====================

    @Test
    fun testReplaceFraction1_3() {
        assertEquals("one third", replacer.replace("1/3"))
    }

    @Test
    fun testReplaceFraction2_5() {
        assertEquals("two fifths", replacer.replace("2/5"))
    }

    @Test
    fun testReplaceFraction3_4() {
        assertEquals("three quarters", replacer.replace("3/4"))
    }

    // ==================== replace: range ====================

    @Test
    fun testReplaceRange20_30() {
        assertEquals("twenty to thirty", replacer.replace("20-30"))
    }

    @Test
    fun testReplaceRange1_100() {
        assertEquals("one to one hundred", replacer.replace("1-100"))
    }

    // ==================== replace: score ====================

    @Test
    fun testReplaceScore3_2() {
        assertEquals("three to two", replacer.replace("3:2"))
    }

    @Test
    fun testReplaceScore0_0() {
        assertEquals("zero to zero", replacer.replace("0:0"))
    }

    // ==================== replace: roman numerals ====================

    @Test
    fun testReplaceRomanXII() {
        assertEquals("twelve", replacer.replace("XII"))
    }

    @Test
    fun testReplaceRomanIV() {
        assertEquals("four", replacer.replace("IV"))
    }

    @Test
    fun testReplaceRomanMCMXCIX() {
        assertEquals("one thousand nine hundred and ninety-nine", replacer.replace("MCMXCIX"))
    }

    // ==================== replace: integer (fallback) ====================

    @Test
    fun testReplaceInt123() {
        assertEquals("one hundred and twenty-three", replacer.replace("123"))
    }

    @Test
    fun testReplaceIntNegative() {
        assertEquals("minus five", replacer.replace("-5"))
    }

    @Test
    fun testReplaceIntLarge() {
        assertEquals("one million", replacer.replace("1000000"))
    }

    // ==================== mixed text ====================

    @Test
    fun testReplaceMixedFull() {
        val input = "In 2024, the temperature was -5°C, the score was 3:2, and he finished in 1st place."
        val expected = "In twenty twenty-four, the temperature was minus five degrees Celsius, the score was three to two, and he finished in first place."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceMixedCurrencyFraction() {
        val input = "The price is $9.99 and the discount is 1/4."
        val expected = "The price is nine dollars and ninety-nine cents and the discount is one quarter."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceNoNumbers() {
        assertEquals("Hello world", replacer.replace("Hello world"))
    }

    @Test
    fun testReplaceEmpty() {
        assertEquals("", replacer.replace(""))
    }

    @Test
    fun testReplacePureText() {
        assertEquals("The quick brown fox jumps", replacer.replace("The quick brown fox jumps"))
    }

    // ==================== priority conflicts ====================

    @Test
    fun testPriority_TimeOverScore() {
        assertEquals("fourteen thirty", replacer.replace("14:30"))
    }

    @Test
    fun testPriority_ScoreWhenTimeNotMatch() {
        assertEquals("three to two", replacer.replace("3:2"))
    }

    @Test
    fun testPriority_TempRangeOverRangeAndTemp() {
        assertEquals("twenty to thirty degrees Celsius", replacer.replace("20-30°C"))
    }

    @Test
    fun testPriority_PercentOverFloat() {
        assertEquals("fifty point five percent", replacer.replace("50.5%"))
    }

    @Test
    fun testPriority_TempOverFloat() {
        assertEquals("thirty-seven point five degrees Celsius", replacer.replace("37.5°C"))
    }

    @Test
    fun testPriority_CurrencyOverFloat() {
        assertEquals("nine dollars and ninety-nine cents", replacer.replace("$9.99"))
    }

    @Test
    fun testPriority_OrdinalSuffixOverInteger() {
        assertEquals("the first", replacer.replace("the 1st"))
    }

    @Test
    fun testPriority_DateOverYear() {
        assertEquals("March fifteenth, twenty twenty-four", replacer.replace("March 15, 2024"))
    }

    // ==================== edge cases ====================

    @Test
    fun testReplaceMultipleNumbers() {
        assertEquals("one hundred and two hundred and three hundred", replacer.replace("100 and 200 and 300"))
    }

    @Test
    fun testReplaceNumberInWord() {
        assertEquals("Chapter twelve is here", replacer.replace("Chapter 12 is here"))
    }

    @Test
    fun testReplaceStandalone4DigitYear() {
        assertEquals("the year twenty twenty-four", replacer.replace("the year 2024"))
    }

    @Test
    fun testReplace4DigitNotYearContext() {
        assertEquals("one thousand two hundred and thirty-four items", replacer.replace("1234 items"))
    }

    @Test
    fun testReplaceCurrencyWithWholeNumber() {
        assertEquals("fifty dollars", replacer.replace("$50"))
    }

    @Test
    fun testReplaceUSDCentsOnly() {
        assertEquals("ninety-nine cents", replacer.replace("$0.99"))
    }

    @Test
    fun testReplaceYearInSentence() {
        assertEquals("born in nineteen ninety-nine", replacer.replace("born in 1999"))
    }

    @Test
    fun testReplaceBetweenYear() {
        assertEquals("between twenty ten and twenty twenty", replacer.replace("between 2010 and 2020"))
    }

    @Test
    fun testReplacePhoneDots() {
        assertEquals("five five five one two three four five six seven", replacer.replace("555.123.4567"))
    }

    // ==================== intToEnglish: 补充十位数边界 ====================

    @Test
    fun testIntTwo() {
        assertEquals("two", EnNumberReplacer.intToEnglish(2))
    }

    @Test
    fun testIntFive() {
        assertEquals("five", EnNumberReplacer.intToEnglish(5))
    }

    @Test
    fun testIntSixty() {
        assertEquals("sixty", EnNumberReplacer.intToEnglish(60))
    }

    @Test
    fun testIntSeventy() {
        assertEquals("seventy", EnNumberReplacer.intToEnglish(70))
    }

    @Test
    fun testIntEighty() {
        assertEquals("eighty", EnNumberReplacer.intToEnglish(80))
    }

    @Test
    fun testIntForty() {
        assertEquals("forty", EnNumberReplacer.intToEnglish(40))
    }

    @Test
    fun testIntFifty() {
        assertEquals("fifty", EnNumberReplacer.intToEnglish(50))
    }

    @Test
    fun testIntSeventySeven() {
        assertEquals("seventy-seven", EnNumberReplacer.intToEnglish(77))
    }

    // ==================== intToEnglish: 整数倍边界 ====================

    @Test
    fun testInt500() {
        assertEquals("five hundred", EnNumberReplacer.intToEnglish(500))
    }

    @Test
    fun testInt5000() {
        assertEquals("five thousand", EnNumberReplacer.intToEnglish(5000))
    }

    @Test
    fun testInt50000() {
        assertEquals("fifty thousand", EnNumberReplacer.intToEnglish(50000))
    }

    @Test
    fun testInt500000() {
        assertEquals("five hundred thousand", EnNumberReplacer.intToEnglish(500000))
    }

    @Test
    fun testInt5Million() {
        assertEquals("five million", EnNumberReplacer.intToEnglish(5000000))
    }

    @Test
    fun testInt10023() {
        assertEquals("ten thousand and twenty-three", EnNumberReplacer.intToEnglish(10023))
    }

    @Test
    fun testInt100001() {
        assertEquals("one hundred thousand and one", EnNumberReplacer.intToEnglish(100001))
    }

    @Test
    fun testInt1000001() {
        assertEquals("one million and one", EnNumberReplacer.intToEnglish(1000001))
    }

    // ==================== intToEnglish: and 边界 ====================

    @Test
    fun testInt1001() {
        assertEquals("one thousand and one", EnNumberReplacer.intToEnglish(1001))
    }

    @Test
    fun testInt1010() {
        assertEquals("one thousand and ten", EnNumberReplacer.intToEnglish(1010))
    }

    @Test
    fun testInt11001() {
        assertEquals("eleven thousand and one", EnNumberReplacer.intToEnglish(11001))
    }

    @Test
    fun testInt10101() {
        assertEquals("ten thousand one hundred and one", EnNumberReplacer.intToEnglish(10101))
    }

    // ==================== ordinalToEnglish: 不规则序数补充 ====================

    @Test
    fun testOrdinal5() {
        assertEquals("fifth", EnNumberReplacer.ordinalToEnglish(5))
    }

    @Test
    fun testOrdinal8() {
        assertEquals("eighth", EnNumberReplacer.ordinalToEnglish(8))
    }

    @Test
    fun testOrdinal9() {
        assertEquals("ninth", EnNumberReplacer.ordinalToEnglish(9))
    }

    @Test
    fun testOrdinal6() {
        assertEquals("sixth", EnNumberReplacer.ordinalToEnglish(6))
    }

    @Test
    fun testOrdinal7() {
        assertEquals("seventh", EnNumberReplacer.ordinalToEnglish(7))
    }

    @Test
    fun testOrdinal10() {
        assertEquals("tenth", EnNumberReplacer.ordinalToEnglish(10))
    }

    @Test
    fun testOrdinal14() {
        assertEquals("fourteenth", EnNumberReplacer.ordinalToEnglish(14))
    }

    @Test
    fun testOrdinal16() {
        assertEquals("sixteenth", EnNumberReplacer.ordinalToEnglish(16))
    }

    @Test
    fun testOrdinal20() {
        assertEquals("twentieth", EnNumberReplacer.ordinalToEnglish(20))
    }

    @Test
    fun testOrdinal30() {
        assertEquals("thirtieth", EnNumberReplacer.ordinalToEnglish(30))
    }

    @Test
    fun testOrdinal42() {
        assertEquals("forty-second", EnNumberReplacer.ordinalToEnglish(42))
    }

    @Test
    fun testOrdinal55() {
        assertEquals("fifty-fifth", EnNumberReplacer.ordinalToEnglish(55))
    }

    @Test
    fun testOrdinal99() {
        assertEquals("ninety-ninth", EnNumberReplacer.ordinalToEnglish(99))
    }

    @Test
    fun testOrdinal111() {
        assertEquals("one hundred and eleventh", EnNumberReplacer.ordinalToEnglish(111))
    }

    @Test
    fun testOrdinal200() {
        assertEquals("two hundredth", EnNumberReplacer.ordinalToEnglish(200))
    }

    @Test
    fun testOrdinal999() {
        assertEquals("nine hundred and ninety-ninth", EnNumberReplacer.ordinalToEnglish(999))
    }

    // ==================== yearToEnglish: 补充边界 ====================

    @Test
    fun testYear1800() {
        assertEquals("eighteen hundred", EnNumberReplacer.yearToEnglish(1800))
    }

    @Test
    fun testYear2001() {
        assertEquals("two thousand and one", EnNumberReplacer.yearToEnglish(2001))
    }

    @Test
    fun testYear2005() {
        assertEquals("two thousand and five", EnNumberReplacer.yearToEnglish(2005))
    }

    @Test
    fun testYear2009() {
        assertEquals("two thousand and nine", EnNumberReplacer.yearToEnglish(2009))
    }

    @Test
    fun testYear2010() {
        assertEquals("twenty ten", EnNumberReplacer.yearToEnglish(2010))
    }

    @Test
    fun testYear1901() {
        assertEquals("nineteen one", EnNumberReplacer.yearToEnglish(1901))
    }

    @Test
    fun testYear10() {
        assertEquals("ten", EnNumberReplacer.yearToEnglish(10))
    }

    @Test
    fun testYear50() {
        assertEquals("fifty", EnNumberReplacer.yearToEnglish(50))
    }

    @Test
    fun testYear3000() {
        assertEquals("thirty hundred", EnNumberReplacer.yearToEnglish(3000))
    }

    // ==================== floatToEnglish: 补充边界 ====================

    @Test
    fun testFloat99_99() {
        assertEquals("ninety-nine point nine nine", EnNumberReplacer.floatToEnglish(99.99))
    }

    @Test
    fun testFloat1000_5() {
        assertEquals("one thousand point five", EnNumberReplacer.floatToEnglish(1000.5))
    }

    @Test
    fun testFloat0_001() {
        assertEquals("zero point zero zero one", EnNumberReplacer.floatToEnglish(0.001))
    }

    @Test
    fun testFloatNegative0_1() {
        assertEquals("minus zero point one", EnNumberReplacer.floatToEnglish(-0.1))
    }

    // ==================== fractionToEnglish: 补充边界 ====================

    @Test
    fun testFractionTwoHalves() {
        assertEquals("two halves", EnNumberReplacer.fractionToEnglish(2, 2))
    }

    @Test
    fun testFractionOneSixth() {
        assertEquals("one sixth", EnNumberReplacer.fractionToEnglish(1, 6))
    }

    @Test
    fun testFractionOneSeventh() {
        assertEquals("one seventh", EnNumberReplacer.fractionToEnglish(1, 7))
    }

    @Test
    fun testFractionOneEighth() {
        assertEquals("one eighth", EnNumberReplacer.fractionToEnglish(1, 8))
    }

    @Test
    fun testFractionOneNinth() {
        assertEquals("one ninth", EnNumberReplacer.fractionToEnglish(1, 9))
    }

    @Test
    fun testFractionFiveEighths() {
        assertEquals("five eighths", EnNumberReplacer.fractionToEnglish(5, 8))
    }

    @Test
    fun testFractionOneTwentieth() {
        assertEquals("one twentieth", EnNumberReplacer.fractionToEnglish(1, 20))
    }

    // ==================== replace: 序数词补充 ====================

    @Test
    fun testReplaceOrdinal5th() {
        assertEquals("the fifth element", replacer.replace("the 5th element"))
    }

    @Test
    fun testReplaceOrdinal8th() {
        assertEquals("on the eighth day", replacer.replace("on the 8th day"))
    }

    @Test
    fun testReplaceOrdinal9th() {
        assertEquals("the ninth inning", replacer.replace("the 9th inning"))
    }

    @Test
    fun testReplaceOrdinal30th() {
        assertEquals("the thirtieth of June", replacer.replace("the 30th of June"))
    }

    @Test
    fun testReplaceOrdinal42nd() {
        assertEquals("the forty-second president", replacer.replace("the 42nd president"))
    }

    @Test
    fun testReplaceOrdinal111th() {
        assertEquals("the one hundred and eleventh Congress", replacer.replace("the 111th Congress"))
    }

    @Test
    fun testReplaceOrdinal99th() {
        assertEquals("the ninety-ninth percentile", replacer.replace("the 99th percentile"))
    }

    @Test
    fun testReplaceStandalone1st() {
        assertEquals("finished in first", replacer.replace("finished in 1st"))
    }

    @Test
    fun testReplaceStandalone2nd() {
        assertEquals("came in second", replacer.replace("came in 2nd"))
    }

    @Test
    fun testReplaceStandalone3rd() {
        assertEquals("took third", replacer.replace("took 3rd"))
    }

    // ==================== replace: 日期补充 ====================

    @Test
    fun testReplaceDateSeptember11() {
        assertEquals("September eleventh", replacer.replace("September 11"))
    }

    @Test
    fun testReplaceDate11September() {
        assertEquals("the eleventh of September twenty twenty-four", replacer.replace("11 September 2024"))
    }

    @Test
    fun testReplaceDateJuly4_1776() {
        assertEquals("July fourth, seventeen seventy-six", replacer.replace("July 4, 1776"))
    }

    @Test
    fun testReplaceDate31Dec() {
        assertEquals("December thirty-first", replacer.replace("December 31"))
    }

    @Test
    fun testReplaceDate1Jan() {
        assertEquals("January first", replacer.replace("January 1"))
    }

    @Test
    fun testReplaceDate29Feb() {
        assertEquals("February twenty-ninth", replacer.replace("February 29"))
    }

    // ==================== replace: 年份上下文补充 ====================

    @Test
    fun testReplaceYearYearContext() {
        assertEquals("the year nineteen ninety-nine", replacer.replace("the year 1999"))
    }

    @Test
    fun testReplace4DigitWithoutContext() {
        assertEquals("one thousand nine hundred and ninety-nine things", replacer.replace("1999 things"))
    }

    @Test
    fun testReplaceYear2001Context() {
        assertEquals("in two thousand and one", replacer.replace("in 2001"))
    }

    @Test
    fun testReplaceYear1800Context() {
        assertEquals("since eighteen hundred", replacer.replace("since 1800"))
    }

    // ==================== replace: 时间补充 ====================

    @Test
    fun testReplaceTime0000() {
        assertEquals("zero o'clock", replacer.replace("0:00"))
    }

    @Test
    fun testReplaceTime1200() {
        assertEquals("twelve o'clock", replacer.replace("12:00"))
    }

    @Test
    fun testReplaceTime101() {
        assertEquals("one one", replacer.replace("1:01"))
    }

    @Test
    fun testReplaceTime235959() {
        assertEquals("twenty-three fifty-nine and fifty-nine seconds", replacer.replace("23:59:59"))
    }

    @Test
    fun testReplaceTime00_30() {
        assertEquals("zero thirty", replacer.replace("0:30"))
    }

    // ==================== replace: 温度补充 ====================

    @Test
    fun testReplaceTemp0C() {
        assertEquals("zero degrees Celsius", replacer.replace("0°C"))
    }

    @Test
    fun testReplaceTemp100F() {
        assertEquals("one hundred degrees Fahrenheit", replacer.replace("100°F"))
    }

    @Test
    fun testReplaceTempUnicodeC() {
        assertEquals("twenty-five degrees Celsius", replacer.replace("25℃"))
    }

    @Test
    fun testReplaceTempUnicodeF() {
        assertEquals("seventy-seven degrees Fahrenheit", replacer.replace("77℉"))
    }

    @Test
    fun testReplaceTempRangeFloat() {
        assertEquals("thirty-six point five to thirty-seven point five degrees Celsius", replacer.replace("36.5-37.5°C"))
    }

    // ==================== replace: 百分比补充 ====================

    @Test
    fun testReplacePercent100() {
        assertEquals("one hundred percent", replacer.replace("100%"))
    }

    @Test
    fun testReplacePercent0_5() {
        assertEquals("zero point five percent", replacer.replace("0.5%"))
    }

    @Test
    fun testReplacePercent99_99() {
        assertEquals("ninety-nine point nine nine percent", replacer.replace("99.99%"))
    }

    // ==================== replace: 货币补充 ====================

    @Test
    fun testReplaceUSD1() {
        assertEquals("one dollar", replacer.replace("$1"))
    }

    @Test
    fun testReplaceUSD0_01() {
        assertEquals("one cent", replacer.replace("$0.01"))
    }

    @Test
    fun testReplaceUSD0_50() {
        assertEquals("fifty cents", replacer.replace("$0.50"))
    }

    @Test
    fun testReplaceGBP0_50() {
        assertEquals("fifty pence", replacer.replace("£0.50"))
    }

    @Test
    fun testReplaceUSD999() {
        assertEquals("nine hundred and ninety-nine dollars", replacer.replace("$999"))
    }

    @Test
    fun testReplaceEuro1() {
        assertEquals("one euro", replacer.replace("€1"))
    }

    @Test
    fun testReplacePound1() {
        assertEquals("one pound", replacer.replace("£1"))
    }

    @Test
    fun testReplacePound1Penny() {
        assertEquals("one pound and one pence", replacer.replace("£1.01"))
    }

    // ==================== replace: 科学计数法补充 ====================

    @Test
    fun testReplaceSci2_5Eminus8() {
        assertEquals("two point five times ten to the minus eighth", replacer.replace("2.5E-8"))
    }

    @Test
    fun testReplaceSci1E6() {
        assertEquals("one times ten to the sixth", replacer.replace("1E6"))
    }

    @Test
    fun testReplaceSciNegativeBase() {
        assertEquals("minus two point five times ten to the third", replacer.replace("-2.5e3"))
    }

    // ==================== replace: 浮点数补充 ====================

    @Test
    fun testReplaceFloat0_5() {
        assertEquals("zero point five", replacer.replace("0.5"))
    }

    @Test
    fun testReplaceFloat99_99() {
        assertEquals("ninety-nine point nine nine", replacer.replace("99.99"))
    }

    @Test
    fun testReplaceFloat1000_0() {
        assertEquals("one thousand", replacer.replace("1000.0"))
    }

    // ==================== replace: 分数补充 ====================

    @Test
    fun testReplaceFraction1_2() {
        assertEquals("one half", replacer.replace("1/2"))
    }

    @Test
    fun testReplaceFraction5_8() {
        assertEquals("five eighths", replacer.replace("5/8"))
    }

    @Test
    fun testReplaceFraction22_7() {
        assertEquals("twenty-two sevenths", replacer.replace("22/7"))
    }

    // ==================== replace: 范围补充 ====================

    @Test
    fun testReplaceRangeFloat() {
        assertEquals("one point five to two point five", replacer.replace("1.5-2.5"))
    }

    @Test
    fun testReplaceRangeTilde() {
        assertEquals("one to ten", replacer.replace("1~10"))
    }

    @Test
    fun testReplaceRangeInContext() {
        assertEquals("pages ten to twenty", replacer.replace("pages 10-20"))
    }

    // ==================== replace: 比分补充 ====================

    @Test
    fun testReplaceScoreHighScore() {
        assertEquals("one hundred to ninety-nine", replacer.replace("100:99"))
    }

    @Test
    fun testReplaceScoreInContext() {
        assertEquals("the final score was seven to three", replacer.replace("the final score was 7:3"))
    }

    // ==================== replace: 罗马数字补充 ====================

    @Test
    fun testReplaceRomanI() {
        assertEquals("I", replacer.replace("I"))
    }

    @Test
    fun testReplaceRomanXL() {
        assertEquals("forty", replacer.replace("XL"))
    }

    @Test
    fun testReplaceRomanMM() {
        assertEquals("two thousand", replacer.replace("MM"))
    }

    @Test
    fun testReplaceRomanD() {
        assertEquals("five hundred", replacer.replace("D"))
    }

    // ==================== 产品级场景：新闻段落 ====================

    @Test
    fun testReplaceNewsParagraph() {
        val input = "On March 15, 2024, the stock rose 3.14% to $150.50. The temperature was 22°C with a range of 18-25°C."
        val expected = "On March fifteenth, twenty twenty-four, the stock rose three point one four percent to one hundred and fifty dollars and fifty cents. The temperature was twenty-two degrees Celsius with a range of eighteen to twenty-five degrees Celsius."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceSportsReport() {
        val input = "In the 1st half, the score was 3:2. He scored his 50th goal in the 90th minute."
        val expected = "In the first half, the score was three to two. He scored his fiftieth goal in the ninetieth minute."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceScienceArticle() {
        val input = "The speed of light is approximately 3e8 meters per second. Water freezes at 0°C and boils at 100°C."
        val expected = "The speed of light is approximately three times ten to the eighth meters per second. Water freezes at zero degrees Celsius and boils at one hundred degrees Celsius."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceRecipe() {
        val input = "Mix 1/2 cup of flour with 3/4 cup of sugar. Bake at 350°F for 25-30 minutes."
        val expected = "Mix one half cup of flour with three quarters cup of sugar. Bake at three hundred and fifty degrees Fahrenheit for twenty-five to thirty minutes."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceHistoryBook() {
        val input = "World War II ended in 1945. In the 21st century, technology advanced rapidly."
        val expected = "World War two ended in nineteen forty-five. In the twenty-first century, technology advanced rapidly."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceFinancialReport() {
        val input = "Revenue grew 15.7% to $2.5 million. Expenses were €1.2 million, down 3.2% from 2023."
        val expected = "Revenue grew fifteen point seven percent to two dollars and fifty cents million. Expenses were one euro and twenty cents million, down three point two percent from twenty twenty-three."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceNovelExcerpt() {
        val input = "It was July 3, 1999. The temperature was 98.6°F."
        val expected = "It was July third, nineteen ninety-nine. The temperature was ninety-eight point six degrees Fahrenheit."
        assertEquals(expected, replacer.replace(input))
    }

    // ==================== 边界：正号 ====================

    @Test
    fun testReplacePositiveSign() {
        assertEquals("five", replacer.replace("+5"))
    }

    @Test
    fun testReplacePositiveFloat() {
        assertEquals("three point one four", replacer.replace("+3.14"))
    }

    // ==================== 边界：多个同类场景 ====================

    @Test
    fun testReplaceMultipleOrdinals() {
        assertEquals("first, second, and third place", replacer.replace("1st, 2nd, and 3rd place"))
    }

    @Test
    fun testReplaceMultipleDates() {
        assertEquals("from January first to December thirty-first", replacer.replace("from January 1 to December 31"))
    }

    @Test
    fun testReplaceMultiplePercents() {
        assertEquals("fifty percent and seventy-five percent", replacer.replace("50% and 75%"))
    }

    // ==================== 边界：规则间不干扰 ====================

    @Test
    fun testReplaceTimeNotAffectedByScore() {
        assertEquals("The game starts at seven o'clock and ends at nine thirty", replacer.replace("The game starts at 7:00 and ends at 9:30"))
    }

    @Test
    fun testReplaceRangeNotAffectedByTemp() {
        assertEquals("ages twenty to sixty-five", replacer.replace("ages 20-65"))
    }

    @Test
    fun testReplaceCurrencyNotAffectedByFloat() {
        assertEquals("it costs ten dollars dollars", replacer.replace("it costs \$10 dollars"))
    }

    // ==================== 边界：连字符数字序数词 ====================

    @Test
    fun testReplaceOrdinalSuffixNotInHyphenatedWord() {
        assertEquals("twenty-first century", replacer.replace("21st century"))
    }

    // ==================== 边界：罗马数字不误匹配 ====================

    @Test
    fun testReplaceRomanNotMatchInWord() {
        assertEquals("Individual", replacer.replace("Individual"))
    }

    @Test
    fun testReplaceRomanNotMatchMixed() {
        assertEquals("one thousand and nine is a valid roman numeral", replacer.replace("MIX is a valid roman numeral"))
    }

    // ==================== 边界：大数字兜底 ====================

    @Test
    fun testReplaceInt9999() {
        assertEquals("nine thousand nine hundred and ninety-nine", replacer.replace("9999"))
    }

    @Test
    fun testReplaceInt10000() {
        assertEquals("ten thousand", replacer.replace("10000"))
    }

    @Test
    fun testReplaceInt100000() {
        assertEquals("one hundred thousand", replacer.replace("100000"))
    }

    // ==================== 边界：数字嵌入英文句子 ====================

    @Test
    fun testReplaceNumberBetweenWords() {
        assertEquals("there are five cats here", replacer.replace("there are 5 cats here"))
    }

    @Test
    fun testReplaceNumberAtStart() {
        assertEquals("one hundred people came", replacer.replace("100 people came"))
    }

    @Test
    fun testReplaceNumberAtEnd() {
        assertEquals("the total is fifty", replacer.replace("the total is 50"))
    }

    // ==================== 边界：空格和空白 ====================

    @Test
    fun testReplaceOnlySpaces() {
        assertEquals("  ", replacer.replace("  "))
    }

    @Test
    fun testReplaceTabs() {
        assertEquals("\t", replacer.replace("\t"))
    }

    @Test
    fun testReplaceNewline() {
        assertEquals("line one\nline two", replacer.replace("line one\nline two"))
    }

    // ==================== NEGATIVE: patterns that should NOT be converted ====================

    @Test
    fun testNegativeISBN() {
        assertEquals("ISBN nine hundred and seventy-eight to zero-one hundred and twenty-three to forty-five thousand six hundred and seventy-eight-nine", replacer.replace("ISBN 978-0-123-45678-9"))
    }

    @Test
    fun testNegativeVersionNumber() {
        assertEquals("vone point two.three", replacer.replace("v1.2.3"))
    }

    @Test
    fun testNegativeChemicalFormula() {
        assertEquals("H2O is water", replacer.replace("H2O is water"))
    }

    @Test
    fun testNegativeCO2() {
        assertEquals("CO2 emissions", replacer.replace("CO2 emissions"))
    }

    @Test
    fun testNegativeIPAddress() {
        assertEquals("one hundred and ninety-two point one six eight.one point one", replacer.replace("192.168.1.1"))
    }

    @Test
    fun testNegativeAlphanumeric() {
        assertEquals("3D movie", replacer.replace("3D movie"))
    }

    @Test
    fun testNegative4K() {
        assertEquals("4K resolution", replacer.replace("4K resolution"))
    }

    @Test
    fun testNegative5G() {
        assertEquals("5G network", replacer.replace("5G network"))
    }

    @Test
    fun testNegativeFlightNumber() {
        assertEquals("Flight AA1two hundred and thirty-four departs at gate A2three", replacer.replace("Flight AA1234 departs at gate A23"))
    }

    @Test
    fun testNegativeModelNumber() {
        assertEquals("Model T1zero", replacer.replace("Model T1000"))
    }

    @Test
    fun testNegativeBrand7Eleven() {
        assertEquals("seven-Eleven store", replacer.replace("7-Eleven store"))
    }

    @Test
    fun testNegativeWindows11() {
        assertEquals("Windows eleven", replacer.replace("Windows 11"))
    }

    @Test
    fun testNegativeAlreadySpelledOut() {
        assertEquals("one two three", replacer.replace("one two three"))
    }

    @Test
    fun testNegativeBracketedReference() {
        assertEquals("see [three] for details", replacer.replace("see [3] for details"))
    }

    @Test
    fun testNegativeFractionInModelNumber() {
        assertEquals("Athree hundred and twenty to two hundred", replacer.replace("A320-200"))
    }

    // ==================== PRODUCTION: real-world ebook paragraphs ====================

    @Test
    fun testProductionNovelOpening() {
        val input = "It was the best of times, it was the worst of times. In 1859, Charles Dickens published A Tale of Two Cities. The book sold 200 million copies worldwide."
        val expected = "It was the best of times, it was the worst of times. In eighteen fifty-nine, Charles Dickens published A Tale of Two Cities. The book sold two hundred million copies worldwide."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionScienceArticle() {
        val input = "Water (H2O) boils at 100°C at sea level. The Earth is 149.6 million km from the Sun. Light travels at 299792 km/s."
        val expected = "Water (H2O) boils at one hundred degrees Celsius at sea level. The Earth is one hundred and forty-nine point six million km from the Sun. Light travels at two hundred and ninety-nine thousand seven hundred and ninety-two km/s."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionCookingRecipe() {
        val input = "Preheat oven to 350°F. Mix 2 cups of flour with 1/4 teaspoon of salt. Bake for 30-35 minutes."
        val expected = "Preheat oven to three hundred and fifty degrees Fahrenheit. Mix two cups of flour with one quarter teaspoon of salt. Bake for thirty to thirty-five minutes."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionMedicalText() {
        val input = "Normal body temperature is 98.6°F. Blood pressure of 120/80 is considered healthy."
        val expected = "Normal body temperature is ninety-eight point six degrees Fahrenheit. Blood pressure of one hundred and twenty eightieths is considered healthy."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionFinancialReport() {
        val input = "Revenue was $15.7 million in 2023, up 23.5% from 2022. The company employs 5000 people across 30 countries."
        val expected = "Revenue was fifteen dollars and seventy cents million in twenty twenty-three, up twenty-three point five percent from twenty twenty-two. The company employs five thousand people across thirty countries."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionSportsReport() {
        val input = "The final score was 3:1. Player number 10 scored 2 goals in the 89th minute. Attendance was 45000."
        val expected = "The final score was three to one. Player number ten scored two goals in the eighty-ninth minute. Attendance was forty-five thousand."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionTravelGuide() {
        val input = "Paris is about 214 miles from London. The Eurostar train takes 2 hours and 20 minutes. A ticket costs about €80."
        val expected = "Paris is about two hundred and fourteen miles from London. The Eurostar train takes two hours and twenty minutes. A ticket costs about eighty euros."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionMixedContent() {
        val input = "On March 15, 2024, at 14:30, the temperature was 22°C. The event cost $50 per person. About 1/3 of the 500 attendees arrived early."
        val expected = "On March fifteenth, twenty twenty-four, at fourteen thirty, the temperature was twenty-two degrees Celsius. The event cost fifty dollars per person. About one third of the five hundred attendees arrived early."
        assertEquals(expected, replacer.replace(input))
    }

    // ==================== negative: accented character boundary ====================

    @Test
    fun testNegativeAccentedCafe() {
        assertEquals("We had café at three", replacer.replace("We had café at 3"))
    }

    @Test
    fun testNegativeAccentedResume() {
        assertEquals("The résumé lists five jobs", replacer.replace("The résumé lists 5 jobs"))
    }

    @Test
    fun testNegativeAccentedFiancee() {
        assertEquals("My fiancée is twenty-eight", replacer.replace("My fiancée is 28"))
    }

    @Test
    fun testNegativeAccentedNaive() {
        assertEquals("A naïve five-year-old", replacer.replace("A naïve 5-year-old"))
    }
}
