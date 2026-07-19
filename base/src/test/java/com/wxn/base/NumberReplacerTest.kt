package com.wxn.base

import com.wxn.base.util.numReplacer.ZhNumberReplacer
import org.junit.Assert.assertEquals
import org.junit.Test

class NumberReplacerTest {

    private val replacer = ZhNumberReplacer()

    // ==================== intToChinese: 基础边界 ====================

    @Test
    fun testIntZero() {
        assertEquals("零", ZhNumberReplacer.intToChinese(0))
    }

    @Test
    fun testIntOne() {
        assertEquals("一", ZhNumberReplacer.intToChinese(1))
    }

    @Test
    fun testIntNine() {
        assertEquals("九", ZhNumberReplacer.intToChinese(9))
    }

    // ==================== intToChinese: 10~19（十省略一） ====================

    @Test
    fun testInt10() {
        assertEquals("十", ZhNumberReplacer.intToChinese(10))
    }

    @Test
    fun testInt11() {
        assertEquals("十一", ZhNumberReplacer.intToChinese(11))
    }

    @Test
    fun testInt15() {
        assertEquals("十五", ZhNumberReplacer.intToChinese(15))
    }

    @Test
    fun testInt19() {
        assertEquals("十九", ZhNumberReplacer.intToChinese(19))
    }

    // ==================== intToChinese: 20~99 ====================

    @Test
    fun testInt20() {
        assertEquals("二十", ZhNumberReplacer.intToChinese(20))
    }

    @Test
    fun testInt21() {
        assertEquals("二十一", ZhNumberReplacer.intToChinese(21))
    }

    @Test
    fun testInt30() {
        assertEquals("三十", ZhNumberReplacer.intToChinese(30))
    }

    @Test
    fun testInt99() {
        assertEquals("九十九", ZhNumberReplacer.intToChinese(99))
    }

    // ==================== intToChinese: 100~999（含零位） ====================

    @Test
    fun testInt100() {
        assertEquals("一百", ZhNumberReplacer.intToChinese(100))
    }

    @Test
    fun testInt101() {
        assertEquals("一百零一", ZhNumberReplacer.intToChinese(101))
    }

    @Test
    fun testInt110() {
        assertEquals("一百一十", ZhNumberReplacer.intToChinese(110))
    }

    @Test
    fun testInt111() {
        assertEquals("一百一十一", ZhNumberReplacer.intToChinese(111))
    }

    @Test
    fun testInt200() {
        assertEquals("二百", ZhNumberReplacer.intToChinese(200))
    }

    @Test
    fun testInt201() {
        assertEquals("二百零一", ZhNumberReplacer.intToChinese(201))
    }

    @Test
    fun testInt210() {
        assertEquals("二百一十", ZhNumberReplacer.intToChinese(210))
    }

    @Test
    fun testInt999() {
        assertEquals("九百九十九", ZhNumberReplacer.intToChinese(999))
    }

    // ==================== intToChinese: 1000~9999（含零位） ====================

    @Test
    fun testInt1000() {
        assertEquals("一千", ZhNumberReplacer.intToChinese(1000))
    }

    @Test
    fun testInt1001() {
        assertEquals("一千零一", ZhNumberReplacer.intToChinese(1001))
    }

    @Test
    fun testInt1010() {
        assertEquals("一千零一十", ZhNumberReplacer.intToChinese(1010))
    }

    @Test
    fun testInt1100() {
        assertEquals("一千一百", ZhNumberReplacer.intToChinese(1100))
    }

    @Test
    fun testInt1234() {
        assertEquals("一千二百三十四", ZhNumberReplacer.intToChinese(1234))
    }

    @Test
    fun testInt2000() {
        assertEquals("二千", ZhNumberReplacer.intToChinese(2000))
    }

    @Test
    fun testInt2001() {
        assertEquals("二千零一", ZhNumberReplacer.intToChinese(2001))
    }

    @Test
    fun testInt2010() {
        assertEquals("二千零一十", ZhNumberReplacer.intToChinese(2010))
    }

    @Test
    fun testInt9999() {
        assertEquals("九千九百九十九", ZhNumberReplacer.intToChinese(9999))
    }

    // ==================== intToChinese: 万级（组单位+组间零） ====================

    @Test
    fun testInt10000() {
        assertEquals("一万", ZhNumberReplacer.intToChinese(10000))
    }

    @Test
    fun testInt10001() {
        assertEquals("一万零一", ZhNumberReplacer.intToChinese(10001))
    }

    @Test
    fun testInt10010() {
        assertEquals("一万零十", ZhNumberReplacer.intToChinese(10010))
    }

    @Test
    fun testInt10100() {
        assertEquals("一万零一百", ZhNumberReplacer.intToChinese(10100))
    }

    @Test
    fun testInt10101() {
        assertEquals("一万零一百零一", ZhNumberReplacer.intToChinese(10101))
    }

    @Test
    fun testInt11000() {
        assertEquals("一万一千", ZhNumberReplacer.intToChinese(11000))
    }

    @Test
    fun testInt20001() {
        assertEquals("二万零一", ZhNumberReplacer.intToChinese(20001))
    }

    @Test
    fun testInt12345() {
        assertEquals("一万二千三百四十五", ZhNumberReplacer.intToChinese(12345))
    }

    @Test
    fun testInt99999() {
        assertEquals("九万九千九百九十九", ZhNumberReplacer.intToChinese(99999))
    }

    // ==================== intToChinese: 亿级（组单位丢失回归） ====================

    @Test
    fun testInt100000() {
        assertEquals("十万", ZhNumberReplacer.intToChinese(100000))
    }

    @Test
    fun testInt100001() {
        assertEquals("十万零一", ZhNumberReplacer.intToChinese(100001))
    }

    @Test
    fun testInt1000000() {
        assertEquals("一百万", ZhNumberReplacer.intToChinese(1000000))
    }

    @Test
    fun testInt10000000() {
        assertEquals("一千万", ZhNumberReplacer.intToChinese(10000000))
    }

    @Test
    fun testInt100000000() {
        assertEquals("一亿", ZhNumberReplacer.intToChinese(100000000))
    }

    @Test
    fun testInt1000000000() {
        assertEquals("十亿", ZhNumberReplacer.intToChinese(1000000000))
    }

    @Test
    fun testInt100000001() {
        assertEquals("一亿零一", ZhNumberReplacer.intToChinese(100000001))
    }

    @Test
    fun testInt100010000() {
        assertEquals("一亿零一万", ZhNumberReplacer.intToChinese(100010000))
    }

    @Test
    fun testInt100001000() {
        assertEquals("一亿零一千", ZhNumberReplacer.intToChinese(100001000))
    }

    @Test
    fun testInt100100000() {
        assertEquals("一亿零十万", ZhNumberReplacer.intToChinese(100100000))
    }

    @Test
    fun testInt123456789() {
        assertEquals("一亿二千三百四十五万六千七百八十九", ZhNumberReplacer.intToChinese(123456789))
    }

    @Test
    fun testInt1234567890() {
        assertEquals("十二亿三千四百五十六万七千八百九十", ZhNumberReplacer.intToChinese(1234567890))
    }

    // ==================== intToChinese: 负数 ====================

    @Test
    fun testIntNegative1() {
        assertEquals("负一", ZhNumberReplacer.intToChinese(-1))
    }

    @Test
    fun testIntNegative12() {
        assertEquals("负十二", ZhNumberReplacer.intToChinese(-12))
    }

    @Test
    fun testIntNegative100() {
        assertEquals("负一百", ZhNumberReplacer.intToChinese(-100))
    }

    @Test
    fun testIntNegative10000() {
        assertEquals("负一万", ZhNumberReplacer.intToChinese(-10000))
    }

    // ==================== floatToChinese ====================

    @Test
    fun testFloatZero() {
        assertEquals("零", ZhNumberReplacer.floatToChinese(0.0))
    }

    @Test
    fun testFloatOne() {
        assertEquals("一", ZhNumberReplacer.floatToChinese(1.0))
    }

    @Test
    fun testFloatPointFive() {
        assertEquals("零点五", ZhNumberReplacer.floatToChinese(0.5))
    }

    @Test
    fun testFloat314() {
        assertEquals("三点一四", ZhNumberReplacer.floatToChinese(3.14))
    }

    @Test
    fun testFloatNegativePointFive() {
        assertEquals("负零点五", ZhNumberReplacer.floatToChinese(-0.5))
    }

    @Test
    fun testFloatNegative314() {
        assertEquals("负三点一四", ZhNumberReplacer.floatToChinese(-3.14))
    }

    @Test
    fun testFloatPointZeroOne() {
        assertEquals("零点零一", ZhNumberReplacer.floatToChinese(0.01))
    }

    @Test
    fun testFloat123Point456() {
        assertEquals("一百二十三点四五六", ZhNumberReplacer.floatToChinese(123.456))
    }

    // ==================== phoneToChinese ====================

    @Test
    fun testPhoneAllDigits() {
        assertEquals("幺三八零零幺三八零零零", ZhNumberReplacer.phoneToChinese("13800138000"))
    }

    @Test
    fun testPhoneStartWith15() {
        assertEquals("幺五八九幺三四五六七八", ZhNumberReplacer.phoneToChinese("15891345678"))
    }

    // ==================== romanToInt ====================

    @Test
    fun testRomanBasic() {
        assertEquals(1, ZhNumberReplacer.romanToInt("I"))
        assertEquals(4, ZhNumberReplacer.romanToInt("IV"))
        assertEquals(5, ZhNumberReplacer.romanToInt("V"))
        assertEquals(9, ZhNumberReplacer.romanToInt("IX"))
        assertEquals(10, ZhNumberReplacer.romanToInt("X"))
        assertEquals(12, ZhNumberReplacer.romanToInt("XII"))
        assertEquals(40, ZhNumberReplacer.romanToInt("XL"))
        assertEquals(50, ZhNumberReplacer.romanToInt("L"))
        assertEquals(90, ZhNumberReplacer.romanToInt("XC"))
        assertEquals(100, ZhNumberReplacer.romanToInt("C"))
        assertEquals(400, ZhNumberReplacer.romanToInt("CD"))
        assertEquals(500, ZhNumberReplacer.romanToInt("D"))
        assertEquals(900, ZhNumberReplacer.romanToInt("CM"))
        assertEquals(1000, ZhNumberReplacer.romanToInt("M"))
        assertEquals(1999, ZhNumberReplacer.romanToInt("MCMXCIX"))
        assertEquals(3999, ZhNumberReplacer.romanToInt("MMMCMXCIX"))
    }

    // ==================== replace: 序号 ====================

    @Test
    fun testReplaceOrdinal1() {
        assertEquals("第一章", replacer.replace("第1章"))
    }

    @Test
    fun testReplaceOrdinal12() {
        assertEquals("第十二章", replacer.replace("第12章"))
    }

    @Test
    fun testReplaceOrdinal100() {
        assertEquals("第一百章", replacer.replace("第100章"))
    }

    @Test
    fun testReplaceOrdinal1000() {
        assertEquals("第一千章", replacer.replace("第1000章"))
    }

    @Test
    fun testReplaceOrdinalMultiple() {
        assertEquals("第一章和第十二章", replacer.replace("第1章和第12章"))
    }

    // ==================== replace: 年份 ====================

    @Test
    fun testReplaceYear2024() {
        assertEquals("二零二四年", replacer.replace("2024年"))
    }

    @Test
    fun testReplaceYear1999() {
        assertEquals("一九九九年", replacer.replace("1999年"))
    }

    @Test
    fun testReplaceYear0001() {
        assertEquals("零零零一年", replacer.replace("0001年"))
    }

    @Test
    fun testReplaceYearNotMatch5Digits() {
        assertEquals("一万九千九百九十九年", replacer.replace("19999年"))
    }

    // ==================== replace: 日期 ====================

    @Test
    fun testReplaceDate3_15() {
        assertEquals("三月十五日", replacer.replace("3月15日"))
    }

    @Test
    fun testReplaceDate12_1() {
        assertEquals("十二月一日", replacer.replace("12月1日"))
    }

    @Test
    fun testReplaceDate1_1() {
        assertEquals("一月一日", replacer.replace("1月1日"))
    }

    // ==================== replace: 时间 ====================

    @Test
    fun testReplaceTimeOnTheHour() {
        assertEquals("三点整", replacer.replace("3:00"))
    }

    @Test
    fun testReplaceTime14_30() {
        assertEquals("十四点三十分", replacer.replace("14:30"))
    }

    @Test
    fun testReplaceTimeWithSeconds() {
        assertEquals("十点五分三十秒", replacer.replace("10:05:30"))
    }

    @Test
    fun testReplaceTimeInChineseContext() {
        assertEquals("下午三点整开会", replacer.replace("下午3:00开会"))
    }

    @Test
    fun testReplaceTime23_59() {
        assertEquals("二十三点五十九分", replacer.replace("23:59"))
    }

    // ==================== replace: 电话号码 ====================

    @Test
    fun testReplacePhoneInContext() {
        assertEquals("电话幺三八零零幺三八零零零", replacer.replace("电话13800138000"))
    }

    @Test
    fun testReplacePhoneStart() {
        assertEquals("幺五九幺二三四五六七八", replacer.replace("15912345678"))
    }

    @Test
    fun testReplacePhoneAtStartOfText() {
        assertEquals("幺三九幺二三四五六七八是我的号码", replacer.replace("13912345678是我的号码"))
    }

    // ==================== replace: 百分比 ====================

    @Test
    fun testReplacePercent50() {
        assertEquals("百分之五十", replacer.replace("50%"))
    }

    @Test
    fun testReplacePercent100() {
        assertEquals("百分之一百", replacer.replace("100%"))
    }

    @Test
    fun testReplacePercentFloat() {
        assertEquals("百分之三点一四", replacer.replace("3.14%"))
    }

    @Test
    fun testReplacePercent99_9() {
        assertEquals("百分之九十九点九", replacer.replace("99.9%"))
    }

    @Test
    fun testReplacePercent1() {
        assertEquals("百分之一", replacer.replace("1%"))
    }

    // ==================== replace: 温度（摄氏/华氏/Unicode） ====================

    @Test
    fun testReplaceTempCelsius() {
        assertEquals("二十摄氏度", replacer.replace("20°C"))
    }

    @Test
    fun testReplaceTempCelsiusUnicode() {
        assertEquals("二十摄氏度", replacer.replace("20℃"))
    }

    @Test
    fun testReplaceTempFahrenheit() {
        assertEquals("六十八华氏度", replacer.replace("68°F"))
    }

    @Test
    fun testReplaceTempFahrenheitUnicode() {
        assertEquals("六十八华氏度", replacer.replace("68℉"))
    }

    @Test
    fun testReplaceTempNegative() {
        assertEquals("负五摄氏度", replacer.replace("-5°C"))
    }

    @Test
    fun testReplaceTempFloat() {
        assertEquals("负五点五摄氏度", replacer.replace("-5.5°C"))
    }

    @Test
    fun testReplaceTempZero() {
        assertEquals("零摄氏度", replacer.replace("0°C"))
    }

    @Test
    fun testReplaceTemp100() {
        assertEquals("一百摄氏度", replacer.replace("100°C"))
    }

    // ==================== replace: 温度范围 ====================

    @Test
    fun testReplaceTempRangeCelsius() {
        assertEquals("二十到三十摄氏度", replacer.replace("20-30°C"))
    }

    @Test
    fun testReplaceTempRangeFahrenheit() {
        assertEquals("六十八到八十华氏度", replacer.replace("68-80°F"))
    }

    @Test
    fun testReplaceTempRangeTilde() {
        assertEquals("二十到三十摄氏度", replacer.replace("20~30°C"))
    }

    @Test
    fun testReplaceTempRangeNegative() {
        assertEquals("负五到五摄氏度", replacer.replace("-5~5°C"))
    }

    // ==================== replace: 货币 ====================

    @Test
    fun testReplaceCNY_Integer() {
        assertEquals("一百元", replacer.replace("¥100"))
    }

    @Test
    fun testReplaceCNY_Float() {
        assertEquals("一百点五元", replacer.replace("¥100.5"))
    }

    @Test
    fun testReplaceCNY_FullWidth() {
        assertEquals("五十元", replacer.replace("￥50"))
    }

    @Test
    fun testReplaceUSD_Integer() {
        assertEquals("五十美元", replacer.replace("$50"))
    }

    @Test
    fun testReplaceUSD_Float() {
        assertEquals("九点九九美元", replacer.replace("$9.99"))
    }

    // ==================== replace: 科学计数法 ====================

    @Test
    fun testReplaceSciFloat() {
        assertEquals("一点五乘以十的十次方", replacer.replace("1.5e10"))
    }

    @Test
    fun testReplaceSciInt() {
        assertEquals("三乘以十的五次方", replacer.replace("3e5"))
    }

    @Test
    fun testReplaceSciNegativeExp() {
        assertEquals("一乘以十的负三次方", replacer.replace("1e-3"))
    }

    // ==================== replace: 浮点数 ====================

    @Test
    fun testReplaceFloat314() {
        assertEquals("三点一四", replacer.replace("3.14"))
    }

    @Test
    fun testReplaceFloatNegative() {
        assertEquals("负五点五", replacer.replace("-5.5"))
    }

    @Test
    fun testReplaceFloatPointOne() {
        assertEquals("零点一", replacer.replace("0.1"))
    }

    @Test
    fun testReplaceFloatInContext() {
        assertEquals("价格三点一四元", replacer.replace("价格3.14元"))
    }

    // ==================== replace: 分数 ====================

    @Test
    fun testReplaceFraction1_3() {
        assertEquals("三分之一", replacer.replace("1/3"))
    }

    @Test
    fun testReplaceFraction2_5() {
        assertEquals("五分之二", replacer.replace("2/5"))
    }

    @Test
    fun testReplaceFraction22_7() {
        assertEquals("七分之二十二", replacer.replace("22/7"))
    }

    // ==================== replace: 范围 ====================

    @Test
    fun testReplaceRange20_30() {
        assertEquals("二十到三十页", replacer.replace("20-30页"))
    }

    @Test
    fun testReplaceRange100_200() {
        assertEquals("一百到二百", replacer.replace("100-200"))
    }

    @Test
    fun testReplaceRangeTilde() {
        assertEquals("一到一百", replacer.replace("1~100"))
    }

    // ==================== replace: 比分 ====================

    @Test
    fun testReplaceScore3_2() {
        assertEquals("三比二", replacer.replace("3:2"))
    }

    @Test
    fun testReplaceScore0_0() {
        assertEquals("零比零", replacer.replace("0:0"))
    }

    @Test
    fun testReplaceScore10_9() {
        assertEquals("十比九", replacer.replace("10:9"))
    }

    // ==================== replace: 罗马数字 ====================

    @Test
    fun testReplaceRomanXII() {
        assertEquals("十二", replacer.replace("XII"))
    }

    @Test
    fun testReplaceRomanIV() {
        assertEquals("四", replacer.replace("IV"))
    }

    @Test
    fun testReplaceRomanI() {
        assertEquals("I", replacer.replace("I"))
    }

    @Test
    fun testReplaceRomanMCMXCIX() {
        assertEquals("一千九百九十九", replacer.replace("MCMXCIX"))
    }

    // ==================== replace: 量词 ====================

    @Test
    fun testReplaceMeasure2Zhi() {
        assertEquals("两只猫", replacer.replace("2只猫"))
    }

    @Test
    fun testReplaceMeasure1Ben() {
        assertEquals("一本书", replacer.replace("1本书"))
    }

    @Test
    fun testReplaceMeasure3Ge() {
        assertEquals("三个人", replacer.replace("3个人"))
    }

    @Test
    fun testReplaceMeasure10Ge() {
        assertEquals("十个人", replacer.replace("10个人"))
    }

    @Test
    fun testReplaceMeasure100Zhang() {
        assertEquals("一百张纸", replacer.replace("100张纸"))
    }

    @Test
    fun testReplaceMeasure2Bei() {
        assertEquals("两杯水", replacer.replace("2杯水"))
    }

    @Test
    fun testReplaceMeasure3Jian() {
        assertEquals("三件衣服", replacer.replace("3件衣服"))
    }

    @Test
    fun testReplaceMeasure5Wei() {
        assertEquals("五位客人", replacer.replace("5位客人"))
    }

    @Test
    fun testReplaceMeasure2Tai() {
        assertEquals("两台电脑", replacer.replace("2台电脑"))
    }

    @Test
    fun testReplaceMeasure3Liang() {
        assertEquals("三辆车", replacer.replace("3辆车"))
    }

    @Test
    fun testReplaceMeasure2Zhi2() {
        assertEquals("两支笔", replacer.replace("2支笔"))
    }

    @Test
    fun testReplaceMeasure1Shuang() {
        assertEquals("一双鞋", replacer.replace("1双鞋"))
    }

    @Test
    fun testReplaceMeasure2Dui() {
        assertEquals("两对耳环", replacer.replace("2对耳环"))
    }

    @Test
    fun testReplaceMeasure3Ba() {
        assertEquals("三把椅子", replacer.replace("3把椅子"))
    }

    @Test
    fun testReplaceMeasure2Kuai() {
        assertEquals("两块蛋糕", replacer.replace("2块蛋糕"))
    }

    @Test
    fun testReplaceMeasure1Dao() {
        assertEquals("一道题", replacer.replace("1道题"))
    }

    @Test
    fun testReplaceMeasure2Chang() {
        assertEquals("两场比赛", replacer.replace("2场比赛"))
    }

    @Test
    fun testReplaceMeasure1Men() {
        assertEquals("一门课", replacer.replace("1门课"))
    }

    @Test
    fun testReplaceMeasure1Shou() {
        assertEquals("一首歌", replacer.replace("1首歌"))
    }

    @Test
    fun testReplaceMeasure1Ke() {
        assertEquals("一棵树", replacer.replace("1棵树"))
    }

    @Test
    fun testReplaceMeasure3Ke2() {
        assertEquals("三颗糖", replacer.replace("3颗糖"))
    }

    @Test
    fun testReplaceMeasure1Jia() {
        assertEquals("一架飞机", replacer.replace("1架飞机"))
    }

    @Test
    fun testReplaceMeasure1Zuo() {
        assertEquals("一座山", replacer.replace("1座山"))
    }

    @Test
    fun testReplaceMeasure2Ceng() {
        assertEquals("两层楼", replacer.replace("2层楼"))
    }

    @Test
    fun testReplaceMeasure1Gen() {
        assertEquals("一根绳子", replacer.replace("1根绳子"))
    }

    @Test
    fun testReplaceMeasure1Duo() {
        assertEquals("一朵花", replacer.replace("1朵花"))
    }

    @Test
    fun testReplaceMeasure1Jian2() {
        assertEquals("一间房", replacer.replace("1间房"))
    }

    // ==================== replace: 整数兜底 ====================

    @Test
    fun testReplaceInt123() {
        assertEquals("一百二十三", replacer.replace("123"))
    }

    @Test
    fun testReplaceIntNegative() {
        assertEquals("负五", replacer.replace("-5"))
    }

    @Test
    fun testReplaceIntPositive() {
        assertEquals("五", replacer.replace("+5"))
    }

    @Test
    fun testReplaceInt100000() {
        assertEquals("十万", replacer.replace("100000"))
    }

    // ==================== Bug 回归测试 ====================

    @Test
    fun testBugA_100000_GroupUnitLoss() {
        assertEquals("十万", replacer.replace("100000"))
    }

    @Test
    fun testBugA_1000000000_GroupUnitLoss() {
        assertEquals("十亿", replacer.replace("1000000000"))
    }

    @Test
    fun testBugA_100100000_GroupUnitLoss() {
        assertEquals("一亿零十万", replacer.replace("100100000"))
    }

    @Test
    fun testBugB_10_ShouldBeShi() {
        assertEquals("十", replacer.replace("10"))
    }

    @Test
    fun testBugB_12_ShouldBeShiEr() {
        assertEquals("十二", replacer.replace("12"))
    }

    @Test
    fun testBugB_Ordinal12() {
        assertEquals("第十二章", replacer.replace("第12章"))
    }

    @Test
    fun testBugB_11WithMeasureWord() {
        assertEquals("十一个", replacer.replace("11个"))
    }

    @Test
    fun testBugC_PercentFloat() {
        assertEquals("百分之三点一四", replacer.replace("3.14%"))
    }

    @Test
    fun testBugC_TemperatureFloat() {
        assertEquals("负五点五摄氏度", replacer.replace("-5.5°C"))
    }

    @Test
    fun testBugC_TemperatureRange() {
        assertEquals("二十到三十摄氏度", replacer.replace("20-30°C"))
    }

    // ==================== 混合文本 ====================

    @Test
    fun testReplaceMixedFull() {
        val input = "今天温度-5°C，下午3:00开会，第1章介绍了2024年3月15日，电话13800138000。"
        val expected = "今天温度负五摄氏度，下午三点整开会，第一章介绍了二零二四年三月十五日，电话幺三八零零幺三八零零零。"
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceMixedScoreCurrencyFraction() {
        val input = "成绩3:2，价格¥100.5，分数1/3，范围20-30页，2只猫。"
        val expected = "成绩三比二，价格一百点五元，分数三分之一，范围二十到三十页，两只猫。"
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceNoNumbers() {
        assertEquals("你好世界", replacer.replace("你好世界"))
    }

    @Test
    fun testReplaceEmpty() {
        assertEquals("", replacer.replace(""))
    }

    @Test
    fun testReplaceOnlySpaces() {
        assertEquals("  ", replacer.replace("  "))
    }

    @Test
    fun testReplacePureChineseText() {
        assertEquals("这本书非常好", replacer.replace("这本书非常好"))
    }

    // ==================== 边界：数字紧跟中文 ====================

    @Test
    fun testReplaceNumberBeforeChinese() {
        assertEquals("共一百二十三页", replacer.replace("共123页"))
    }

    @Test
    fun testReplaceNumberAfterChinese() {
        assertEquals("共有一百二十三", replacer.replace("共有123"))
    }

    @Test
    fun testReplaceNumberBetweenChinese() {
        assertEquals("共一百二十三章内容", replacer.replace("共123章内容"))
    }

    // ==================== 边界：多个同类数字 ====================

    @Test
    fun testReplaceMultipleOrdinals() {
        assertEquals("第一章第二章第三章", replacer.replace("第1章第2章第3章"))
    }

    @Test
    fun testReplaceMultipleIntegers() {
        assertEquals("一百和二百和三百", replacer.replace("100和200和300"))
    }

    // ==================== 边界：规则优先级竞争 ====================

    @Test
    fun testPriority_TimeOverScore() {
        assertEquals("十四点三十分", replacer.replace("14:30"))
    }

    @Test
    fun testPriority_ScoreWhenTimeNotMatch() {
        assertEquals("三比二", replacer.replace("3:2"))
    }

    @Test
    fun testPriority_TempRangeOverRangeAndTemp() {
        assertEquals("二十到三十摄氏度", replacer.replace("20-30°C"))
    }

    @Test
    fun testPriority_PercentOverFloat() {
        assertEquals("百分之五十点五", replacer.replace("50.5%"))
    }

    @Test
    fun testPriority_TempOverFloat() {
        assertEquals("三十七点五摄氏度", replacer.replace("37.5°C"))
    }

    @Test
    fun testPriority_OrdinalOverInteger() {
        assertEquals("第一百章", replacer.replace("第100章"))
    }

    @Test
    fun testPriority_YearOverInteger() {
        assertEquals("二零二四年", replacer.replace("2024年"))
    }

    @Test
    fun testPriority_DateOverInteger() {
        assertEquals("三月十五日", replacer.replace("3月15日"))
    }

    @Test
    fun testPriority_MeasureWordOverInteger() {
        assertEquals("两只", replacer.replace("2只"))
    }

    // ==================== 边界：零值 ====================

    @Test
    fun testReplaceZeroInteger() {
        assertEquals("零", replacer.replace("0"))
    }

    @Test
    fun testReplaceZeroTemp() {
        assertEquals("零摄氏度", replacer.replace("0°C"))
    }

    @Test
    fun testReplaceZeroScore() {
        assertEquals("零比零", replacer.replace("0:0"))
    }

    // ==================== 边界：负号位置 ====================

    @Test
    fun testReplaceNegativeInt() {
        assertEquals("负一百", replacer.replace("-100"))
    }

    @Test
    fun testReplaceNegativeFloat() {
        assertEquals("负一点五", replacer.replace("-1.5"))
    }

    // ==================== 边界：特殊上下文 ====================

    @Test
    fun testReplacePhoneNotAtStart() {
        assertEquals("联系幺三九幺二三四五六七八电话", replacer.replace("联系13912345678电话"))
    }

    @Test
    fun testReplaceTempInSentence() {
        assertEquals("今天气温三十七点五摄氏度很高", replacer.replace("今天气温37.5°C很高"))
    }

    @Test
    fun testReplaceRangeWithPageUnit() {
        assertEquals("请阅读二十到五十页", replacer.replace("请阅读20-50页"))
    }

    @Test
    fun testReplaceCurrencyInPrice() {
        assertEquals("这本书三十五点九元", replacer.replace("这本书¥35.9"))
    }

    // ==================== 边界：大数字完整性 ====================

    @Test
    fun testReplaceLargeNumber_12345678() {
        assertEquals("一千二百三十四万五千六百七十八", replacer.replace("12345678"))
    }

    @Test
    fun testReplaceLargeNumber_10000000() {
        assertEquals("一千万", replacer.replace("10000000"))
    }

    @Test
    fun testReplaceLargeNumber_100000000() {
        assertEquals("一亿", replacer.replace("100000000"))
    }

    // ==================== NEGATIVE: patterns that should NOT be converted ====================

    @Test
    fun testNegativeISBN() {
        assertEquals("ISBN 九百七十八到七-一百二十三到四万五千六百七十八负九", replacer.replace("ISBN 978-7-123-45678-9"))
    }

    @Test
    fun testNegativeVersionNumber() {
        assertEquals("v一点二.三版本", replacer.replace("v1.2.3版本"))
    }

    @Test
    fun testNegativeChemicalFormula() {
        assertEquals("H2O是水", replacer.replace("H2O是水"))
    }

    @Test
    fun testNegativeCO2() {
        assertEquals("CO2排放量", replacer.replace("CO2排放量"))
    }

    @Test
    fun testNegativeIPAddress() {
        assertEquals("一百九十二点一六八.一点一", replacer.replace("192.168.1.1"))
    }

    @Test
    fun testNegativeAlphanumeric() {
        assertEquals("3D电影", replacer.replace("3D电影"))
    }

    @Test
    fun testNegativeModelNumber() {
        assertEquals("型号T1零", replacer.replace("型号T1000"))
    }

    @Test
    fun testNegativeFlightNumber() {
        assertEquals("CA1二百三十四航班", replacer.replace("CA1234航班"))
    }

    @Test
    fun testNegativeBracketedReference() {
        assertEquals("参见[三]了解详情", replacer.replace("参见[3]了解详情"))
    }

    @Test
    fun testNegativeIDCard() {
        assertEquals("身份证号十一京零一百零一兆一千九百九十亿零一百零一万一千二百三十四", replacer.replace("身份证号110101199001011234"))
    }

    @Test
    fun testNegativeAlreadyChinese() {
        assertEquals("一百二十三", replacer.replace("一百二十三"))
    }

    // ==================== PRODUCTION: real-world ebook paragraphs ====================

    @Test
    fun testProductionNovelOpening() {
        val input = "2024年3月15日，北京气温达到了28°C。全市共有500万市民参与了义务植树活动。活动从上午9:00开始，持续到下午17:00。"
        val expected = "二零二四年三月十五日，北京气温达到了二十八摄氏度。全市共有五百万市民参与了义务植树活动。活动从上午九点整开始，持续到下午十七点整。"
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionScienceArticle() {
        val input = "光速约为每秒30万公里，水的沸点是100°C。地球距离太阳约1.5亿公里。"
        val expected = "光速约为每秒三十万公里，水的沸点是一百摄氏度。地球距离太阳约一点五亿公里。"
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionFinancialNews() {
        val input = "2024年第一季度GDP增长了5.3%，全国居民消费价格上涨0.7%。"
        val expected = "二零二四年第一季度GDP增长了百分之五点三，全国居民消费价格上涨百分之零点七。"
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionHistoryText() {
        val input = "公元前221年，秦始皇统一六国，建立了中国历史上第一个大一统王朝。"
        val expected = "公元前二百二十一年，秦始皇统一六国，建立了中国历史上第一个大一统王朝。"
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionRecipe() {
        val input = "将烤箱预热至180°C。把500克面粉和1/2茶匙盐混合。烤制25-30分钟。"
        val expected = "将烤箱预热至一百八十摄氏度。把五百克面粉和二分之一茶匙盐混合。烤制二十五到三十分钟。"
        assertEquals(expected, replacer.replace(input))
    }

    // ==================== negative: accented character boundary ====================

    @Test
    fun testNegativeAccentedCafeInChinese() {
        assertEquals("在café喝了三杯咖啡", replacer.replace("在café喝了3杯咖啡"))
    }

    @Test
    fun testNegativeAccentedResumeInChinese() {
        assertEquals("他的résumé有五页", replacer.replace("他的résumé有5页"))
    }

    // ==================== chineseToInt: 反向反查（章节号解析用） ====================

    @Test
    fun testChineseToInt_empty_returnsNull() {
        assertEquals(null, ZhNumberReplacer.chineseToInt(""))
    }

    @Test
    fun testChineseToInt_singleDigit() {
        assertEquals(1, ZhNumberReplacer.chineseToInt("一"))
        assertEquals(9, ZhNumberReplacer.chineseToInt("九"))
    }

    @Test
    fun testChineseToInt_tenVariants() {
        assertEquals(10, ZhNumberReplacer.chineseToInt("十"))
        assertEquals(11, ZhNumberReplacer.chineseToInt("十一"))
        assertEquals(19, ZhNumberReplacer.chineseToInt("十九"))
        assertEquals(20, ZhNumberReplacer.chineseToInt("二十"))
    }

    @Test
    fun testChineseToInt_hundred() {
        assertEquals(100, ZhNumberReplacer.chineseToInt("一百"))
        assertEquals(105, ZhNumberReplacer.chineseToInt("一百零五"))
        assertEquals(234, ZhNumberReplacer.chineseToInt("二百三十四"))
    }

    @Test
    fun testChineseToInt_thousand() {
        assertEquals(1001, ZhNumberReplacer.chineseToInt("一千零一"))
        assertEquals(9999, ZhNumberReplacer.chineseToInt("九千九百九十九"))
    }

    @Test
    fun testChineseToInt_wan() {
        assertEquals(10000, ZhNumberReplacer.chineseToInt("一万"))
        assertEquals(99999, ZhNumberReplacer.chineseToInt("九万九千九百九十九"))
    }

    @Test
    fun testChineseToInt_overflow_returnsNull() {
        assertEquals(null, ZhNumberReplacer.chineseToInt("十万"))
        assertEquals(null, ZhNumberReplacer.chineseToInt("一百万"))
    }

    @Test
    fun testChineseToInt_nonCjk_returnsNull() {
        // 含 ASCII / 罗马字符 → null（快速排除）
        assertEquals(null, ZhNumberReplacer.chineseToInt("abc"))
        assertEquals(null, ZhNumberReplacer.chineseToInt("3十2"))   // 混合数字
        assertEquals(null, ZhNumberReplacer.chineseToInt("IV"))
    }

    @Test
    fun testChineseToInt_cacheHit() {
        // 连续两次调用应返回相同结果（验证 ConcurrentHashMap 缓存）
        assertEquals(23, ZhNumberReplacer.chineseToInt("二十三"))
        assertEquals(23, ZhNumberReplacer.chineseToInt("二十三"))
    }
}
