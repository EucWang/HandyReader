package com.wxn.bookread.provider

import com.wxn.bookread.data.model.TextChar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * justify 分布决策 + 线性重排执行 单测（plan docs/plans/2026-08-29-plan-justify-max-gap-hybrid-fix.md §8.1）。
 *
 * 两个测试类：
 *  - [JustifyGapResolverTest]：[JustifyChecker.resolveJustifyPlan] 纯决策（20 例）；
 *  - [JustifyApplierTest]：[TextLayoutProvider.distributeJustifyChars] 执行坐标（4 例）。
 *
 * 口径：textSize=100f → 词距上限 50f、字距上限 10f、CJK 逐字上限 25f。
 * 构造行：等宽字符 adv=10f；renderGroup 与引擎一致（空格挂前词组，组号随后自增）；
 * 坐标顺序累加（span=Σadv）——resolver 按「行跨度」算 contentWidth，坐标必须连续。
 */
class JustifyGapResolverTest {

    private fun ch(c: String, group: Int, adv: Float = 10f) =
        TextChar(c, start = 0f, end = adv, renderGroup = group)

    /** words=词列表（每字符 adv=10f），trailing=行尾拖尾一个空格。 */
    private fun line(words: List<String>, trailing: Boolean = false): ArrayList<TextChar> {
        val out = ArrayList<TextChar>()
        var g = 1
        var x = 0f
        fun add(c: String) {
            out.add(TextChar(c, start = x, end = x + 10f, renderGroup = g))
            x += 10f
        }
        words.forEachIndexed { wi, w ->
            w.forEach { add(it.toString()) }
            if (wi < words.lastIndex || trailing) {
                add(" ")
                g++
            }
        }
        return out
    }

    private fun resolve(chars: List<TextChar>, effWidth: Float, textSize: Float = 100f) =
        JustifyChecker.resolveJustifyPlan(chars, effWidth, textSize)

    // ── B3 混合分布（修复本体）──

    @Test
    fun hybrid_longNextWordFullLine() {
        // 5 词 35 字符（31 字母 + 4 空格），contentWidth=350；eff=600：gapT=62.5>50
        val chars = line(listOf("aaaaaaa", "bbbbbbbb", "ccc", "ddddddddd", "eeee"))
        val plan = resolve(chars, 600f)
        assertEquals(JustifyPlan.Mode.HYBRID, plan.mode)
        assertEquals(50f, plan.wordGap, 0f)
        assertEquals((600 - 350 - 4 * 50) / 30f, plan.perChar, 0.01f)   // ≈1.67（within=30）
    }

    @Test
    fun hybrid_trailingSpaceJudgedTrimmed() {
        // "aaa bbbbbb␣"：contentWidth=110，contentBase=100（剔尾空格）
        val chars = line(listOf("aaa", "bbbbbb"), trailing = true)
        // eff=160：剔空格 gapT=(160-100)/1=60>50 → HYBRID；不剔则 =50 → 会被判 WORD（证伪点）
        val plan = resolve(chars, 160f)
        assertEquals(JustifyPlan.Mode.HYBRID, plan.mode)
        assertEquals(50f, plan.wordGap, 0f)
        assertEquals(0f, plan.perChar, 0f)   // 剩余恰 = 封顶词距总额，纯词距即拉满（F8）
        // eff=161：within=8（合格 10 字符-1 对-1 跨组对），perChar=1/8
        val plan2 = resolve(chars, 161f)
        assertEquals(JustifyPlan.Mode.HYBRID, plan2.mode)
        assertEquals(0.125f, plan2.perChar, 0.001f)
    }

    // ── B2 现状路径 ──

    @Test
    fun word_normalLeftoverUsesRawGap() {
        val chars = line(listOf("aaaaaaa", "bbbbbbbb", "ccc", "ddddddddd", "eeee"))
        val plan = resolve(chars, 480f)   // gapT=(480-350)/4=32.5 ≤ 50
        assertEquals(JustifyPlan.Mode.WORD_DISTRIBUTE, plan.mode)
        assertEquals(32.5f, plan.wordGap, 0f)
    }

    @Test
    fun word_gapWidthTExactCapStaysWord() {
        val chars = line(listOf("aaaaa", "bbbbbb"))   // contentWidth=120
        val plan = resolve(chars, 170f)   // gapT=(170-120)/1=50 恰等于上限 → WORD（F10）
        assertEquals(JustifyPlan.Mode.WORD_DISTRIBUTE, plan.mode)
        assertEquals(50f, plan.wordGap, 0f)
    }

    @Test
    fun skip_negativeGapAfterTrim_noCompression() {
        // contentWidth=110 > eff=105 > contentBase=100：判定有余、分布为负 → 禁负压缩（F4）
        val chars = line(listOf("aaa", "bbbbbb"), trailing = true)
        val plan = resolve(chars, 105f)
        assertEquals(JustifyPlan.Mode.SKIP, plan.mode)
    }

    @Test
    fun skip_overwide() {
        val chars = line(listOf("aaaa", "bbbb", "cccc"))
        assertEquals(JustifyPlan.Mode.SKIP, resolve(chars, 50f).mode)   // gapT<0
    }

    // ── B3 真·短行 / 连写脚本纯词距 ──

    @Test
    fun word_trueShortLine_fillsViaUncappedWordGap() {
        // 原 skip_twoWordPathologicalLine：行为反转见 docs/plans/2026-09-01-plan-justify-true-shortline-wordfill.md
        // "aa" + 30 字母超长词：within=31，perChar=(900-330-50)/31≈16.8 > 10
        // → 不再放弃对齐，回退无上限纯词距：raw=(900-330)/1=570（与连写脚本 R1 同型）
        val chars = line(listOf("aa", "wwwwwwwwwwwwwwwwwwwwwwwwwwwwww"))
        val plan = resolve(chars, 900f)
        assertEquals(JustifyPlan.Mode.WORD_DISTRIBUTE, plan.mode)
        assertEquals(570f, plan.wordGap, 0.01f)
    }

    @Test
    fun skip_perCharExactCapStaysHybrid_fillFavored() {
        // contentWidth=120，within=10，across=1：perChar=10 恰等于上限 → HYBRID（F10）
        val chars = line(listOf("aaaaa", "bbbbbb"))
        val plan = resolve(chars, 270f)   // (270-120-50)/10=10
        assertEquals(JustifyPlan.Mode.HYBRID, plan.mode)
        assertEquals(10f, plan.perChar, 0f)
        // 超 0.1em 上限 → 无上限纯词距回退（不再 SKIP）：raw=(271-120)/1=151
        val fallback = resolve(chars, 271f)   // 10.1 > 10
        assertEquals(JustifyPlan.Mode.WORD_DISTRIBUTE, fallback.mode)
        assertEquals(151f, fallback.wordGap, 0.01f)
    }

    @Test
    fun word_arabicConnectedScript_uncappedWordOnly() {
        // 14 字符 contentWidth=140；eff=400：gapT=(400-140)/2=130>50 → 连写脚本 → 纯词距 raw=130（无上限，R1）
        val chars = line(listOf("aaaa", "بbbb", "cccc"))
        val plan = resolve(chars, 400f)
        assertEquals(JustifyPlan.Mode.WORD_DISTRIBUTE, plan.mode)
        assertEquals(130f, plan.wordGap, 0f)
    }

    @Test
    fun skip_arabic_overwideStaysSkipped() {
        val chars = line(listOf("aaaa", "بbbb", "cccc"))
        assertEquals(JustifyPlan.Mode.SKIP, resolve(chars, 100f).mode)   // B1 gapT=(100-140)/2<0
    }

    // ── [A] 单组行 ──

    @Test
    fun skip_singleGroupArabicLongWord() {
        // 单组连写行：无词间隙可分布 → 左对齐（FBReader/浏览器 spaceCounter=0 同行为）
        val chars = line(listOf("بببببببب"))
        assertEquals(JustifyPlan.Mode.SKIP, resolve(chars, 900f).mode)
    }

    @Test
    fun skip_singleGroupMixedCjkArabic() {
        // 单组中阿混排（无空白，如「中文ببب」）：绝不逐字拆开连写（R6）
        val chars = line(listOf("中中ببب"))
        assertEquals(JustifyPlan.Mode.SKIP, resolve(chars, 900f).mode)
    }

    @Test
    fun hybrid_latinLineStillHybrid() {
        // 无连写脚本的普通行（contentWidth=140）：eff=300 → gapT=80>50，perChar=(300-140-100)/11≈5.45
        val chars = line(listOf("aaaa", "bbbb", "cccc"))
        assertEquals(JustifyPlan.Mode.HYBRID, resolve(chars, 300f).mode)
    }

    @Test
    fun skip_singleGroupLatinWordOrUrlFragment() {
        val chars = line(listOf("abcdefghij"))
        assertEquals(JustifyPlan.Mode.SKIP, resolve(chars, 900f).mode)
    }

    @Test
    fun charDistribute_singleGroupCjk() {
        val chars = line(listOf("中中中中中"))
        val plan = resolve(chars, 140f)   // gap=(140-50)/4=22.5 ≤ 25
        assertEquals(JustifyPlan.Mode.CHAR_DISTRIBUTE, plan.mode)
        assertEquals(22.5f, plan.wordGap, 0f)
    }

    @Test
    fun skip_singleGroupCjk_overCap() {
        val chars = line(listOf("中中中中中"))
        assertEquals(JustifyPlan.Mode.SKIP, resolve(chars, 170f).mode)   // gap=30 > 25
    }

    @Test
    fun charDistribute_cjkTrailingSpaceTrimmed() {
        // "中中中中␣"：contentBase=40；gap=(100-40)/3=20（不剔则 (100-50)/4=12.5，证伪点）
        val chars = line(listOf("中中中中"), trailing = true)
        val plan = resolve(chars, 100f)
        assertEquals(JustifyPlan.Mode.CHAR_DISTRIBUTE, plan.mode)
        assertEquals(20f, plan.wordGap, 0f)
    }

    // ── 图片行 / 退化输入 ──

    @Test
    fun word_imageLineKeepsLegacyRawGuard() {
        val chars = arrayListOf(
            TextChar("a", start = 0f, end = 10f, renderGroup = 1),
            TextChar("a", start = 10f, end = 20f, renderGroup = 1),
            TextChar("img/x.png", start = 20f, end = 50f, isImage = true, renderGroup = 0),
            TextChar("b", start = 50f, end = 60f, renderGroup = 2),
            TextChar("b", start = 60f, end = 70f, renderGroup = 2)
        )
        val plan = resolve(chars, 100f)   // raw=(100-70)/2=15 ∈ (0,50] → 现状 WORD
        assertEquals(JustifyPlan.Mode.WORD_DISTRIBUTE, plan.mode)
        assertEquals(15f, plan.wordGap, 0f)
        assertEquals(JustifyPlan.Mode.SKIP, resolve(chars, 40f).mode)   // raw<0
    }

    @Test
    fun skip_imageOnlyOrEmptyOrAllWs() {
        val imgOnly = arrayListOf(TextChar("img/x.png", start = 0f, end = 30f, isImage = true, renderGroup = 0))
        assertEquals(JustifyPlan.Mode.SKIP, resolve(imgOnly, 900f).mode)
        assertEquals(JustifyPlan.Mode.SKIP, resolve(emptyList(), 900f).mode)
        val ws = arrayListOf(ch(" ", 1), ch(" ", 2))
        assertEquals(JustifyPlan.Mode.SKIP, resolve(ws, 900f).mode)
    }

    @Test
    fun skip_withinZero_paintBoundarySplitNoWhitespace() {
        // 无空白、paint 边界切两组：within=0 → 无摊入位
        val chars = arrayListOf(ch("a", 1), ch("b", 2))
        assertEquals(JustifyPlan.Mode.SKIP, resolve(chars, 900f).mode)
    }
}

/**
 * [TextLayoutProvider.distributeJustifyChars] 纯几何执行单测：直接断言坐标、C1 宽度不变、盒内不越界。
 */
class JustifyApplierTest {

    /** 坐标顺序累加的等宽行（同 resolver 测试构造），可选行尾拖尾空格 */
    private fun line(words: List<String>, trailing: Boolean = false): ArrayList<TextChar> {
        val out = ArrayList<TextChar>()
        var g = 1
        var x = 0f
        fun add(c: String) {
            out.add(TextChar(c, start = x, end = x + 10f, renderGroup = g))
            x += 10f
        }
        words.forEachIndexed { wi, w ->
            w.forEach { add(it.toString()) }
            if (wi < words.lastIndex || trailing) {
                add(" ")
                g++
            }
        }
        return out
    }

    @Test
    fun hybrid_fillsBox_exactPositions() {
        // 与 resolver T1 同参：5 词 35 字符，effWidth=600，wordGap=50，perChar=50/30
        // （空格挂前词组：跨组对是「空格→下一词首字符」）
        val chars = line(listOf("aaaaaaa", "bbbbbbbb", "ccc", "ddddddddd", "eeee"))
        TextLayoutProvider.distributeJustifyChars(chars, 50f, 50f / 30f, 0f, 600f, false, true)
        // 恒等式：末字符右缘精确到盒右缘
        assertEquals(600f, chars.last().end, 0.01f)
        // C1：宽度不变
        chars.forEach { assertEquals(10f, it.end - it.start, 1e-4f) }
        // 首字符贴 effStart；组内字距 / 跨组词距抽查
        assertEquals(0f, chars.first().start, 1e-4f)
        assertEquals(50f / 30f, chars[1].start - chars[0].end, 0.01f)       // 组内（词1）
        assertEquals(50f, chars[8].start - chars[7].end, 0.01f)             // 跨组（space→词2 首）
        assertEquals(50f, chars[17].start - chars[16].end, 0.01f)           // 跨组（space→词3 首）
    }

    @Test
    fun hybrid_trailingSpaceInsideBox() {
        // "aaa bbbbbb␣" eff=160：尾空格贴附带 [150,160]，末字母右缘=150
        val chars = line(listOf("aaa", "bbbbbb"), trailing = true)
        TextLayoutProvider.distributeJustifyChars(chars, 50f, 0f, 0f, 160f, false, true)
        assertEquals(150f, chars[9].end, 0.01f)     // 最后一个合格字符（b）
        assertEquals(150f, chars[10].start, 0.01f)  // 尾空格贴附
        assertEquals(160f, chars[10].end, 0.01f)    // 盒内不越界
        chars.forEach { assertEquals(10f, it.end - it.start, 1e-4f) }
    }

    @Test
    fun charDistribute_cjkPerCharGap() {
        // 盒宽与参数配套：3 字符 ×10 + 2 对 ×20 = 70
        val chars = line(listOf("中中中"))
        TextLayoutProvider.distributeJustifyChars(chars, 20f, 0f, 0f, 70f, false, false)
        assertEquals(70f, chars.last().end, 0.01f)
        assertEquals(20f, chars[1].start - chars[0].end, 0.01f)
        assertEquals(20f, chars[2].start - chars[1].end, 0.01f)
    }

    @Test
    fun hybrid_rtlMirror() {
        // RTL：阅读序首字贴盒右缘、末字贴盒左缘。盒宽与参数配套：9×10 + 1 跨组对 ×50 = 140
        val chars = line(listOf("aaaa", "bbbb"))
        TextLayoutProvider.distributeJustifyChars(chars, 50f, 0f, 100f, 240f, true, true)
        assertEquals(240f, chars.first().end, 0.01f)      // 阅读首字贴右
        assertEquals(100f, chars.last().start, 0.01f)     // 阅读末字贴左
        assertEquals(50f, chars[4].start - chars[5].end, 0.01f)   // 空格→下一词（视觉向左推进）
        chars.forEach { assertEquals(10f, it.end - it.start, 1e-4f) }
    }
}
