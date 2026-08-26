package com.wxn.bookread.provider

import android.graphics.Color
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spike（instrumented）：在真实 Android 设备上验证 [StaticLayout] 的连字符断词行为。
 *
 * 用 instrumented test 而非 Robolectric，是因为 [StaticLayout] 是 native 实现，
 * Robolectric 的 shadow 不做真实文本测量（已验证：lineCount 恒为 1、行为无意义）。
 *
 * 要回答的问题：
 * 1. 普通英文文本（无软连字符）在 NORMAL 频率下，Android 是否自动断词？
 * 2. 源文本预置软连字符 `\u00AD` 时，NORMAL 频率是否在行末显示连字符？
 * 3. `getLineEnd` 切出的 substring 里是否包含连字符（污染 TextChar / 选词 / TTS）？
 * 4. CJK 文本在 NORMAL 频率下是否被错误插入连字符（门控必要性）？
 *
 * 运行：`./gradlew :bookread:connectedDebugAndroidTest --tests
 *        "com.wxn.bookread.provider.StaticLayoutHyphenationSpikeInstrumentedTest"`
 */
@RunWith(AndroidJUnit4::class)
class StaticLayoutHyphenationSpikeInstrumentedTest {

    private lateinit var paint: TextPaint
    private val visibleWidth = 300 // px，故意窄以强制多行换行

    @Before
    fun setUp() {
        paint = TextPaint().apply {
            color = Color.BLACK
            textSize = 40f // 较大字号，配合窄宽度强制频繁断行
        }
    }

    private fun build(text: String, frequency: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, visibleWidth)
            .setHyphenationFrequency(frequency)
            .build()

    private data class LineInfo(
        val lineIndex: Int, val start: Int, val end: Int, val line: String,
        val hasHardHyphen: Boolean, val hasSoftHyphen: Boolean
    )

    private fun dump(title: String, layout: StaticLayout): List<LineInfo> {
        println("\n========== $title ==========")
        println("lineCount=${layout.lineCount}, text.length=${layout.text.length}")
        val rows = mutableListOf<LineInfo>()
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            val line = layout.text.substring(start, end)
            val hasHardHyphen = line.endsWith('-')
            val hasSoftHyphen = line.contains('\u00AD')
            val info = LineInfo(i, start, end, line, hasHardHyphen, hasSoftHyphen)
            val trailingHex = if (line.isNotEmpty()) line.last().code.toString(16) else "(empty)"
            println(
                "  L${info.lineIndex}: [$start,$end) trailing=U+$trailingHex " +
                    "hardHyphen=$hasHardHyphen softHyphen=$hasSoftHyphen | \"$line\""
            )
            rows += info
        }
        return rows
    }

    // ───────── 问题 1：纯英文，NORMAL 频率，是否自动断词 ─────────

    @Test
    fun case1_plainEnglish_normalFrequency_autoHyphenation() {
        val text = "The internationalization of modern communication technologies " +
            "requires comprehensive understanding of transformational methodologies."
        val layout = build(text, StaticLayout.HYPHENATION_FREQUENCY_NORMAL)
        val rows = dump("Case1: 纯英文 / NORMAL / 无预置软连字符", layout)

        val autoHyphenCount = rows.count { it.hasHardHyphen }
        println(">>> Case1 结论: 普通文本在 NORMAL 下自动断词行数 = $autoHyphenCount")
        // 不做硬断言——只记录事实，结果决定是否需要源文本预置 \u00AD
        assertTrue("布局至少应多行换行（lineCount>1）", layout.lineCount > 1)
    }

    // ───────── 问题 2 & 3：预置软连字符，NORMAL vs NONE ─────────

    @Test
    fun case2_softHyphen_normalFrequency_hyphenInSubstring() {
        val text = "The inter\u00ADnation\u00ADal\u00ADization of modern " +
            "com\u00ADmuni\u00ADcation technologies requires under\u00ADstanding."
        val layout = build(text, StaticLayout.HYPHENATION_FREQUENCY_NORMAL)
        val rows = dump("Case2: 预置 \\u00AD / NORMAL", layout)

        val hardHyphenRows = rows.filter { it.hasHardHyphen }
        val softHyphenRows = rows.filter { it.hasSoftHyphen }
        println(">>> Case2a 结论: 行末可见连字符 '-' 的行数 = ${hardHyphenRows.size}")
        println(">>> Case2b 结论: 行内残留 \\u00AD 的行数 = ${softHyphenRows.size}")
        // 关键断言（平台实测事实，方案 §4.3-14，Mi 10 / Android 13）：
        //   连字符 '-' 是绘制层装饰，substring 永不插入；
        //   断词点的 U+00AD 保留在 substring 行尾（trailing=U+ad）→ 会流入
        //   TextChar/选词/TTS（污染源证据，另立任务处理）。
        val softTotal = rows.sumOf { it.line.count { c -> c == '\u00AD' } }
        assertTrue("NORMAL 不应向 substring 插入可见连字符 '-'（连字符是绘制装饰）", hardHyphenRows.isEmpty())
        assertTrue("U+00AD 应全部残留（断词点保留 + 行内保留）", softTotal == text.count { it == '\u00AD' })
        assertTrue("布局必须多行换行", layout.lineCount > 1)
    }

    @Test
    fun case3_softHyphen_noneFrequency_suppressed() {
        val text = "The inter\u00ADnation\u00ADal\u00ADization of modern " +
            "com\u00ADmuni\u00ADcation technologies requires under\u00ADstanding."
        val layout = build(text, StaticLayout.HYPHENATION_FREQUENCY_NONE)
        val rows = dump("Case3: 预置 \\u00AD / NONE (对照)", layout)

        val hardHyphenRows = rows.filter { it.hasHardHyphen }
        val softHyphenRows = rows.filter { it.hasSoftHyphen }
        println(">>> Case3 结论: NONE 频率下可见连字符行数 = ${hardHyphenRows.size}，软连字符残留行数 = ${softHyphenRows.size}")
        // 断言（平台实测事实）：NONE 不显示连字符、也不剥离 U+00AD（原样残留行内）
        assertTrue("NONE 频率不应有可见连字符", hardHyphenRows.isEmpty())
        val softTotal = rows.sumOf { it.line.count { c -> c == '\u00AD' } }
        assertTrue("NONE 频率下 U+00AD 应全部残留（平台不剥离）", softTotal == text.count { it == '\u00AD' })
    }

    // ───────── 问题 4：CJK 文本在 NORMAL 下是否异常 ─────────

    @Test
    fun case4_cjk_normalFrequency_noHyphenInserted() {
        val text = "汉字在连字符模式下应该按字符断行，不会也不应该被插入连字符。" +
            "中文排版依赖标点禁则而非音节断词，这是验证语言门控必要性的依据。"
        val layout = build(text, StaticLayout.HYPHENATION_FREQUENCY_NORMAL)
        val rows = dump("Case4: CJK / NORMAL（验证门控必要性）", layout)

        val anyHyphen = rows.any { it.hasHardHyphen || it.hasSoftHyphen }
        println(">>> Case4 结论: CJK 行出现任何连字符 = $anyHyphen")
        assertTrue("CJK 文本不应被插入任何连字符", !anyHyphen)
        assertTrue("CJK 布局必须多行换行", layout.lineCount > 1)
    }

    // ───────── 额外：break strategy HIGH + NORMAL（推荐生产组合） ─────────

    @Test
    fun case5_breakStrategyHigh_normalFrequency() {
        val text = "The inter\u00ADnation\u00ADal\u00ADization of modern " +
            "com\u00ADmuni\u00ADcation technologies requires under\u00ADstanding."
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, visibleWidth)
            .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
            .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NORMAL)
            .build()
        val rows = dump("Case5: BREAK_STRATEGY_HIGH + NORMAL（推荐组合）", layout)

        val hyphenCount = rows.count { it.hasHardHyphen }
        println(">>> Case5 结论: HIGH+NORMAL 组合下可见连字符行数 = $hyphenCount")
        assertTrue("布局必须多行换行", layout.lineCount > 1)
    }
}
