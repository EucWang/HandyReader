package com.wxn.bookread.provider

import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 矩阵 spike v2：在真实设备上探查 StaticLayout 的「测量 + 标记」API。
 *
 * 背景：SDK 中 getEndHyphenEdit/getStartHyphenEdit 不可用（android-34 无此方法），
 *   且 Paint.HYPHEN_EDIT_* 旧常量已重命名为 Paint.END_HYPHEN_EDIT_*（API 28+）。
 *   本测试改为探查真正可用的 API：
 *   - [Layout.getLineEnd] vs [Layout.getLineVisibleEnd] —— 二者差值是否提示连字符？
 *   - [Paint.END_HYPHEN_EDIT_*] 常量（API 28+）运行时取值。
 *
 * 运行：
 *   adb shell am instrument -w -e class com.wxn.bookread.provider.HyphenationMatrixSpikeTest \
 *     com.wxn.bookread.test/androidx.test.runner.AndroidJUnitRunner
 *   adb logcat -d | grep "System.out"
 */
@RunWith(AndroidJUnit4::class)
class HyphenationMatrixSpikeTest {

    private val visibleWidth = 300
    private val paint: TextPaint = TextPaint().apply {
        color = Color.BLACK
        textSize = 40f
        isAntiAlias = true
    }

    /** 标准测试文本：含 3 个预置软连字符 (U+00AD)。 */
    private val textWithSoftHyphen = "The inter\u00ADnation\u00ADal\u00ADization of modern " +
        "com\u00ADmuni\u00ADcation technologies."

    private fun analyze(title: String, layout: StaticLayout) {
        println("\n========== $title ==========")
        println("lineCount=${layout.lineCount}")
        for (i in 0 until layout.lineCount) {
            val start = layout.getLineStart(i)
            val end = layout.getLineEnd(i)
            val visibleEnd = layout.getLineVisibleEnd(i)
            val line = layout.text.substring(start, end)
            val visibleLine = layout.text.substring(start, visibleEnd)
            val endEdit = runCatching {
                // 探查 Paint 上运行时的 hyphen edit 能力（这些常量 API 28+）
                val m = Paint::class.java.getMethod("getEndHyphenEdit")
                m.invoke(paint) as? Int
            }.getOrNull()
            val tailHex = if (line.isNotEmpty()) line.last().code.toString(16) else "(empty)"
            println(
                "  L$i: start=$start end=$end visibleEnd=$visibleEnd diff=${end - visibleEnd} " +
                    "tail=U+$tailHex | end=\"${line}\" | visEnd=\"${visibleLine}\"" +
                    (if (endEdit != null) " | paintEndHyphenEdit=$endEdit" else "")
            )
        }
    }

    private fun strategyName(s: Int) = when (s) {
        Layout.BREAK_STRATEGY_SIMPLE -> "SIMPLE"
        Layout.BREAK_STRATEGY_HIGH_QUALITY -> "HIGH_Q"
        Layout.BREAK_STRATEGY_BALANCED -> "BALANCED"
        else -> "?"
    }

    private fun freqName(f: Int) = when (f) {
        Layout.HYPHENATION_FREQUENCY_NONE -> "NONE"
        Layout.HYPHENATION_FREQUENCY_NORMAL -> "NORMAL"
        Layout.HYPHENATION_FREQUENCY_FULL -> "FULL"
        else -> "?"
    }

    // ───────── 矩阵：3 break strategy × 3 frequency（文本含软连字符）─────────

    @Test
    fun matrix_breakStrategy_x_hyphenationFrequency() {
        val strategies = intArrayOf(
            Layout.BREAK_STRATEGY_SIMPLE,
            Layout.BREAK_STRATEGY_HIGH_QUALITY,
            Layout.BREAK_STRATEGY_BALANCED
        )
        val freqs = intArrayOf(
            Layout.HYPHENATION_FREQUENCY_NONE,
            Layout.HYPHENATION_FREQUENCY_NORMAL,
            Layout.HYPHENATION_FREQUENCY_FULL
        )

        println("\n############ 矩阵 v2 开始（文本含 3 个 \\u00AD）############")
        println("文本=\"$textWithSoftHyphen\"")
        println("【关键观察：end vs visibleEnd 差值；substring 行尾是否出现可见 '-'】\n")

        val summary = StringBuilder()
        var idx = 0
        for (s in strategies) {
            for (f in freqs) {
                idx++
                val layout = StaticLayout.Builder
                    .obtain(textWithSoftHyphen, 0, textWithSoftHyphen.length, paint, visibleWidth)
                    .setBreakStrategy(s)
                    .setHyphenationFrequency(f)
                    .build()
                analyze("M$idx ${strategyName(s)} + ${freqName(f)}", layout)

                // 汇总每行：是否有可见 '-'
                var visibleHyphenLines = 0
                var visibleEndLessEndLines = 0
                for (i in 0 until layout.lineCount) {
                    val end = layout.getLineEnd(i)
                    val visEnd = layout.getLineVisibleEnd(i)
                    val lineSeg = layout.text.substring(layout.getLineStart(i), end)
                    if (lineSeg.endsWith('-')) visibleHyphenLines++
                    if (visEnd < end) visibleEndLessEndLines++
                }
                summary.append(
                    "M$idx ${strategyName(s)}+${freqName(f)}: " +
                        "可见'-'行数=$visibleHyphenLines, visibleEnd<end行数=$visibleEndLessEndLines\n"
                )
            }
        }

        println("\n############ 矩阵汇总 ############")
        println(summary)
        println(">>> 判读：")
        println("  - 可见 '-' 行数>0 → substring 含连字符（引擎层可见）")
        println("  - visibleEnd<end 行数>0 → 行尾有被裁掉的字符（可能是软连字符）")
    }

    // ───────── 关键对比：getLineEnd vs getLineVisibleEnd（HIGH + NORMAL vs NONE）─────────

    @Test
    fun compare_lineEnd_vs_lineVisibleEnd() {
        println("\n############ getLineEnd vs getLineVisibleEnd 对比 ############")
        for (freq in intArrayOf(Layout.HYPHENATION_FREQUENCY_NONE, Layout.HYPHENATION_FREQUENCY_NORMAL)) {
            val layout = StaticLayout.Builder
                .obtain(textWithSoftHyphen, 0, textWithSoftHyphen.length, paint, visibleWidth)
                .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                .setHyphenationFrequency(freq)
                .build()
            println("\n--- HIGH_QUALITY + ${freqName(freq)} ---")
            for (i in 0 until layout.lineCount) {
                val s = layout.getLineStart(i)
                val end = layout.getLineEnd(i)
                val visEnd = layout.getLineVisibleEnd(i)
                val endLine = layout.text.substring(s, end)
                val visLine = if (visEnd > s) layout.text.substring(s, visEnd) else ""
                println("  L$i: end=[$s,$end)\"$endLine\" | visibleEnd=[$s,$visEnd)\"$visLine\" | diff=${end - visEnd}")
            }
        }
    }
}
