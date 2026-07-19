package com.wxn.bookread.provider

import android.text.StaticLayout
import android.text.TextPaint
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Spike：验证 Android [StaticLayout] 在不同 [StaticLayout.Builder.setHyphenationFrequency]
 * 配置下的断词行为，为「连字符开关」功能提供确定性结论。
 *
 * 要回答的 4 个问题：
 * 1. 普通英文文本（无软连字符）在 NORMAL 频率下，Android 是否自动断词？
 * 2. 源文本预置软连字符 `\u00AD` 时，断词点如何选择？NORMAL vs NONE 区别？
 * 3. `getLineEnd` 切出的 substring 里**是否包含连字符**（`-` 或 `\u00AD`）？
 *    → 决定 [com.wxn.bookread.data.model.TextChar] 序列是否被污染（影响选词/查词/TTS）。
 * 4. CJK 文本在 NORMAL 频率下是否异常？（验证语言门控必要性）
 *
 * 运行：`./gradlew :bookread:testDebugUnitTest --tests "*StaticLayoutHyphenationSpikeTest"`
 */
@RunWith(RobolectricTestRunner::class)
class StaticLayoutHyphenationSpikeTest {

    private lateinit var paint: TextPaint
    private val visibleWidth = 300 // px，故意窄以强制多行换行

    @Before
    fun setUp() {
        paint = TextPaint().apply {
            textSize = 40f // 较大字号，配合窄宽度强制频繁断行
        }
    }

    private fun build(text: String, frequency: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, visibleWidth)
            .setHyphenationFrequency(frequency)
            .build()

    /** 逐行打印 StaticLayout 的关键信息，并返回每行的 (start, end, substring, hasHyphen)。 */
    private fun dump(title: String, layout: StaticLayout): List<LineInfo> {
        println("\n========== $title ==========")
        println("lineCount=${layout.lineCount}, text.length=${layout.text.length}")
        val rows = mutableListOf<LineInfo>()
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            val line = layout.text.substring(start, end)
            // 行末视觉连字符探测：ASCII hyphen '-' (U+002D) 或 soft hyphen (U+00AD)
            val hasHardHyphen = line.endsWith('-')
            val hasSoftHyphen = line.contains('\u00AD')
            val trailingChar = if (line.isNotEmpty()) line.last().code.toString(16) else "(empty)"
            val info = LineInfo(i, start, end, line, hasHardHyphen, hasSoftHyphen, trailingChar)
            rows += info
            println(
                "  L${info.lineIndex}: [$start,$end) trailing=U+${info.trailingCharHex} " +
                    "hardHyphen=${info.hasHardHyphen} softHyphen=${info.hasSoftHyphen} | \"$line\""
            )
        }
        return rows
    }

    // ───────── 问题 1：纯英文，NORMAL 频率，是否自动断词 ─────────

    @Test
    fun `case1 plain english NORMAL frequency - check auto hyphenation`() {
        // 含长单词，期待在窄宽度下发生断词
        val text = "The internationalization of modern communication technologies " +
            "requires comprehensive understanding of transformational methodologies."
        val layout = build(text, StaticLayout.HYPHENATION_FREQUENCY_NORMAL)
        val rows = dump("Case1: 纯英文 / NORMAL / 无预置软连字符", layout)

        val anyAutoHyphen = rows.any { it.hasHardHyphen }
        println("\n>>> 结论 Case1: 普通文本在 NORMAL 下自动断词 = $anyAutoHyphen")
        println(">>> 若为 false，说明 Android 不做无字典提示的自动断词，需要在源文本预置 \\u00AD")
    }

    // ───────── 问题 2 & 3：预置软连字符，NORMAL vs NONE ─────────

    @Test
    fun `case2 pre-inserted soft hyphen NORMAL - hyphen appears in substring`() {
        // 预置软连字符 (U+00AD)：inter­nation­al­ization
        val text = "The inter\u00ADnation\u00ADal\u00ADization of modern " +
            "com\u00ADmuni\u00ADcation technologies requires under\u00ADstanding."
        val layout = build(text, StaticLayout.HYPHENATION_FREQUENCY_NORMAL)
        val rows = dump("Case2: 预置 \\u00AD / NORMAL", layout)

        val linesWithHyphen = rows.filter { it.hasHardHyphen }
        val linesWithSoft = rows.filter { it.hasSoftHyphen }
        println("\n>>> 结论 Case2a: 行末出现可见连字符 '-' 的行数 = ${linesWithHyphen.size}")
        println(">>> 结论 Case2b: 行内残留软连字符 \\u00AD 的行数 = ${linesWithSoft.size}")
        println(">>> 关键：若 Case2a>0 且 Case2b==0，说明断词处软连字符被替换为可见 '-'，会进入 substring")
    }

    @Test
    fun `case3 pre-inserted soft hyphen NONE - soft hyphen suppressed`() {
        // 同样的文本，频率 NONE 作对照
        val text = "The inter\u00ADnation\u00ADal\u00ADization of modern " +
            "com\u00ADmuni\u00ADcation technologies requires under\u00ADstanding."
        val layout = build(text, StaticLayout.HYPHENATION_FREQUENCY_NONE)
        val rows = dump("Case3: 预置 \\u00AD / NONE (对照)", layout)

        val linesWithHyphen = rows.filter { it.hasHardHyphen }
        val linesWithSoft = rows.filter { it.hasSoftHyphen }
        println("\n>>> 结论 Case3: NONE 频率下，可见连字符行数 = ${linesWithHyphen.size}，软连字符残留行数 = ${linesWithSoft.size}")
        println(">>> 期待：NONE 时软连字符既不显示也不残留（被吞掉）")
    }

    // ───────── 问题 4：CJK 文本在 NORMAL 下是否异常 ─────────

    @Test
    fun `case4 CJK text NORMAL - verify behavior for gating decision`() {
        val text = "汉字在连字符模式下应该按字符断行，不会也不应该被插入连字符。" +
            "中文排版依赖标点禁则而非音节断词，这是验证语言门控必要性的依据。"
        val layout = build(text, StaticLayout.HYPHENATION_FREQUENCY_NORMAL)
        val rows = dump("Case4: CJK / NORMAL（验证门控必要性）", layout)

        val anyHyphen = rows.any { it.hasHardHyphen || it.hasSoftHyphen }
        val anyGarbled = rows.any { line ->
            // 简单乱码探测：行内既有 CJK 又被插入了不该有的 '-'
            line.line.any { it.code > 0x4E00 } && line.hasHardHyphen
        }
        println("\n>>> 结论 Case4: CJK 行出现任何连字符 = $anyHyphen, CJK+硬连字符(疑似乱码) = $anyGarbled")
        println(">>> 即便 Android 对 CJK 不主动断词，仍应在引擎层强制门控，避免边界 case")
    }

    // ───────── 额外：break strategy 组合（HIGH + NORMAL 是最佳断词质量） ─────────

    @Test
    fun `case5 break strategy HIGH plus NORMAL - best quality combo`() {
        val text = "The inter\u00ADnation\u00ADal\u00ADization of modern " +
            "com\u00ADmuni\u00ADcation technologies requires under\u00ADstanding."
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, visibleWidth)
            .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NORMAL)
            .build()
        val rows = dump("Case5: BREAK_STRATEGY_HIGH + NORMAL（推荐组合）", layout)

        val hyphenCount = rows.count { it.hasHardHyphen }
        println("\n>>> 结论 Case5: 高质量断行+连字符组合下，可见连字符行数 = $hyphenCount")
        println(">>> 这将是生产环境采用的组合，需确认与 ChapterProvider 现有断行质量差异是否可接受")
    }

    private data class LineInfo(
        val lineIndex: Int,
        val start: Int,
        val end: Int,
        val line: String,
        val hasHardHyphen: Boolean,
        val hasSoftHyphen: Boolean,
        val trailingCharHex: String
    )
}
