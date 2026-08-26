package com.wxn.bookread.provider

import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 原语级 Spike（instrumented）：StaticLayout 在「首行缩进（box）小于首个不可断词」时的行为。
 *
 * 回答的问题：共享行的 run 首词放不进剩余宽度时，StaticLayout 到底怎么处理？
 * （Bug C 根因链第 ② 步的实测修正）
 *
 * 实测结论（Mi 10 / Android 13，textSize=56，fullWidth=840）：
 *  - P1 单词 box=70 → lineCount=2，line0W=74：词被【字符/簇级拆开】，碎片可略超 box（+4px）。
 *    它不会"整词溢出不折行"，也不会"整词推到第二行"——贪心 + 每行至少一个断行单元，
 *    单元装不下就按簇切分，切分粒度导致碎片可超 box 数 px。
 *  - P2 两词 box=55 → line0='كل'（كلمة 的前半，阿拉伯连写被拆），line0W=65 > box。
 *  - P3 LTR 镜像：'English' box=92 → line0=碎片 65px ≤ box（装得下，但仍是词内截断）。
 *  - P4 对照：box=280 足够 → 正常按词折行，line0W=217 ≤ box。
 *
 * 对引擎的含义（Bug C 真实链路）：
 *  ① 共享 run 的碎片可超 box 数 px → blockMin 越过列起始缘；
 *  ② cursor < startX 后 box 被 coerce 成 1px → line0 = 单个字素（30~60px）钉在已越界的
 *     cursor 上 → 级联放大（引擎级 dump 行尾的 'E'/'U'/'UR' 碎片即此）；
 *  ③ 即使不溢出，词内截断（阿拉伯连写拆开跨行）本身也是排版缺陷。
 *
 * StaticLayout 是 native 实现，必须真机测量（Robolectric shadow 不做真实文本测量）。
 *
 * 运行（需连接设备）:
 *   gradlew.bat :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.StaticLayoutSharedIndentPrimitiveTest
 */
@RunWith(AndroidJUnit4::class)
class StaticLayoutSharedIndentPrimitiveTest {

    private val paint = TextPaint().apply {
        color = Color.BLACK
        textSize = 56f
        isAntiAlias = true
    }
    private val fullWidth = 840

    private fun build(
        text: String,
        leftIndent: Int = 0,
        rightIndent: Int = 0,
        rtl: Boolean
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, fullWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(
                if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
            )
            .setIncludePad(false)
            .setIndents(intArrayOf(leftIndent, 0), intArrayOf(rightIndent, 0))
            .build()

    /**
     * P1 单词 + box=词宽一半（RTL，右缩进）：
     * 实测行为 = 词被簇级拆成 2 行，且 line0 碎片可超 box（簇粒度超宽）。
     * 这 disproves"整词溢出"假设，也 disproves"整词换行"期望。
     */
    @Test
    fun P1_singleWord_boxSmallerThanWord_charBrokenAndMayExceedBox() {
        val word = "وكلمة"
        val wordW = paint.measureText(word)
        val remaining = (wordW / 2f).toInt()
        val layout = build(word, rightIndent = fullWidth - remaining, rtl = true)

        println("P1 wordW=$wordW remaining=$remaining lineCount=${layout.lineCount} line0W=${layout.getLineWidth(0)}")
        assertEquals(2, layout.lineCount)
        // 词被拆开：line0 与 line1 都非空
        assertTrue(layout.getLineEnd(0) > 0)
        assertTrue(layout.getLineEnd(1) > layout.getLineEnd(0))
        // 碎片仍可能超 box（本次实测 +4px）——不作为硬断言（簇粒度因字体而异），
        // 但打印供分析；引擎侧由 getLineWidth(0) > box 判据捕获。
        println("P1 overshoot=${layout.getLineWidth(0) - remaining}")
    }

    /**
     * P2 两词 + box 小于第一词（RTL，右缩进）：
     * line0 = 第一词的碎片（'كل'，كلمة 被拆，连写断裂）——词内截断的直接证据。
     */
    @Test
    fun P2_twoWords_boxSmallerThanFirstWord_line0IsWordFragment() {
        val text = "كلمة عبارة"
        val firstW = paint.measureText("كلمة")
        val remaining = (firstW / 2f).toInt()
        val layout = build(text, rightIndent = fullWidth - remaining, rtl = true)

        val line0Text = text.substring(layout.getLineStart(0), layout.getLineEnd(0))
        println("P2 firstW=$firstW remaining=$remaining lineCount=${layout.lineCount} line0W=${layout.getLineWidth(0)} line0Text='$line0Text'")
        assertEquals(2, layout.lineCount)
        // line0 既不是完整第一词（被截断），也不是空
        assertTrue("line0 应为词碎片而非整词: '$line0Text'", line0Text.trim().length < "كلمة".length)
        assertTrue(line0Text.isNotBlank())
    }

    /**
     * P3 LTR 镜像（左缩进）：'English' box=92 → line0 = 碎片（≤ box，不超宽）。
     * 说明「词内截断」可以不伴随「超宽」——修复判据需同时覆盖两者。
     */
    @Test
    fun P3_ltrMirror_leftIndent_charBrokenButMayFit() {
        val word = "English"
        val wordW = paint.measureText(word)
        val remaining = (wordW / 2f).toInt()
        val layout = build(word, leftIndent = fullWidth - remaining, rtl = false)

        println("P3 wordW=$wordW remaining=$remaining lineCount=${layout.lineCount} line0W=${layout.getLineWidth(0)} line0End=${layout.getLineEnd(0)}")
        assertEquals(2, layout.lineCount)
        // 词被拆（line0 < 全词）
        assertTrue(layout.getLineEnd(0) < word.length)
    }

    /**
     * P4 对照组：box 大于首词（= 共享行剩余宽度足够）：
     * 多词文本正常按词折行，line0 实宽 ≤ box——缩进技巧在"放得下"时完全正常。
     */
    @Test
    fun P4_control_boxLargerThanFirstWord_wrapsNormally() {
        val text = "كلمة عبارة طويلة جدا"
        val remaining = fullWidth / 3   // 远大于首词
        val layout = build(text, rightIndent = fullWidth - remaining, rtl = true)

        println("P4 remaining=$remaining lineCount=${layout.lineCount} line0W=${layout.getLineWidth(0)}")
        assertTrue(layout.lineCount >= 2)
        assertTrue("line0 实宽(${layout.getLineWidth(0)}) 应 ≤ box($remaining)", layout.getLineWidth(0) <= remaining)
    }
}
