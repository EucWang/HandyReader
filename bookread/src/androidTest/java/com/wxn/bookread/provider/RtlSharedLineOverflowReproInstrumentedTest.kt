package com.wxn.bookread.provider

import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Bug C 复现（instrumented）→ Part 3 后硬断言化（方案 §9.4 T1/T1b）。
 *
 * Part 3（堆叠坐标系统一）修复后，本测试从「诊断模式 dump」升级为「硬断言」：
 *  - 断言组：原始列缘硬界 / 有效列缘（缩进不被吞）、行向==基调、阿拉伯词内不跨行截断、
 *    词多重集合守恒；
 *  - 场景矩阵（§9.4）：S1 RTL列表 / S2 LTR混排列表 / S3 CSS双边margin /
 *    S4、S5 零margin回归基线；
 *  - T1b：双列模式（不越列缘、不横跨两列）；
 *  - 跨页：小 visibleHeight 强制换页，覆盖 A5（processMixedLine 换页 cursor 重置）路径。
 *
 * ★ 边缘断言基于【墨迹】（排除空白字符）：
 *   空白字符无墨迹、按标准排版语义允许悬挂到列缘外（StaticLayout 的 getLineWidth
 *   本就剥除视觉行尾空白）；含空白的全宽越界只打印诊断不判失败。
 *   原始列缘容差 = inkPad+1.5（anchorLine 对满宽行允许 box 溢出 ≤1×inkSize，
 *   方案 §9.6 D1 设计余量）。
 *
 * 阿拉伯字母判定用码点区间（禁 Character.UnicodeScript——API 24，minSdk 23，§8.3）。
 *
 * 运行（需连接设备）:
 *   gradlew.bat :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.RtlSharedLineOverflowReproInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class RtlSharedLineOverflowReproInstrumentedTest {

    /** LD-B 多级列表里出问题的混排 li 原文（用户复制粘贴的地面真值） */
    private val liText =
        "العنصر الثالث مع رقم 42 وكلمة English واختصار URL مثل https://example.com"

    /** LTR 基调混排（LD-C 场景：英文为主 + 阿拉伯词 + 数字 + URL；首强字符 'A' → 基调恒 LTR） */
    private val ltrText =
        "A mixed English paragraph with Arabic كلمة and number 42 and URL https://example.com inside"

    private data class Scenario(
        val tag: String, val text: String, val baseRtl: Boolean,
        val mL: Float, val mR: Float
    )

    private val scenarios = listOf(
        Scenario("S1-rtlLi", liText, baseRtl = true, mL = 0f, mR = 60f),
        Scenario("S2-ltrMixedLi", ltrText, baseRtl = false, mL = 60f, mR = 0f),
        Scenario("S3-rtlCssMargin", liText, baseRtl = true, mL = 40f, mR = 40f),
        Scenario("S4-rtlPlain", liText, baseRtl = true, mL = 0f, mR = 0f),
        Scenario("S5-ltrPlain", ltrText, baseRtl = false, mL = 0f, mR = 0f)
    )

    /** 阿拉伯字母：字母 + 阿拉伯语系码点区间（无 UnicodeScript，API 24） */
    private fun isArabicLetter(c: Char): Boolean {
        if (!c.isLetter()) return false
        val code = c.code
        return code in 0x0600..0x06FF || code in 0x0750..0x077F ||
                code in 0x08A0..0x08FF || code in 0xFB50..0xFDFF ||
                code in 0xFE70..0xFEFF
    }

    private fun configure(width: Int, padding: Int, height: Int, dual: Boolean) {
        ChapterProvider.apply {
            paddingHorizontal = padding
            paddingVertical = padding
            visibleWidth = width
            visibleHeight = height
            visibleRight = paddingHorizontal + visibleWidth
            visibleBottom = paddingVertical + visibleHeight
            lineSpacingExtra = 1.2f
            dualColumnEnabled = dual
            // 复刻 recomputeDerivedSizes() 的双列几何（生产由视图尺寸驱动，测试手动设置）
            if (dual) {
                columnGapActual = (visibleWidth * 0.06f).toInt()   // DUAL_COLUMN_GAP_RATIO=0.06（private）
                columnWidth = (visibleWidth - columnGapActual) / 2
            } else {
                columnGapActual = 0
                columnWidth = 0
            }
        }
    }

    private fun layoutPages(
        scenario: Scenario, width: Int, padding: Int, height: Int, dual: Boolean,
        textSize: Float
    ): ArrayList<TextPage> {
        configure(width, padding, height, dual)
        val paint = TextPaint().apply {
            color = Color.BLACK
            this.textSize = textSize
            isAntiAlias = true
        }
        val seg = RTLSegmenter.segment(scenario.text)
        val textPages = arrayListOf(TextPage())
        TextLayoutProvider.layoutNormalTextRtl(
            scenario.text,
            null,
            seg,
            paint,
            marginLeft = scenario.mL,
            marginRight = scenario.mR,
            firstLineIndent = 0f,
            isTitle = false,
            isListRow = true,
            listLevel = 1,
            paragraphIndex = 0,
            textAlign = CssTextAlign.CssTextAlignUndefined,
            lineHeightParam = 1f,
            paragraph = ReaderText.Text(scenario.text),
            textPages = textPages,
            pageLines = arrayListOf(),
            pageLengths = arrayListOf(),
            stringBuilder = StringBuilder(),
            offsetY = ChapterProvider.paddingVertical.toFloat(),
            bounds = if (dual) {
                // 双列：与引擎切列逻辑一致，起始列 = 章节方向决定（RTL 右列起）
                if (scenario.baseRtl) layoutBoundsRightColumn() else layoutBoundsLeftColumn()
            } else layoutBoundsPage(),
            chapterIsRtl = scenario.baseRtl,
            hasInlineImage = false
        )
        return textPages
    }

    /** 行的墨迹边缘（排除空白与图片）：空白无墨迹，允许悬挂（标准排版语义） */
    private fun inkBounds(line: TextLine): Pair<Float, Float>? {
        val ink = line.textChars.filter {
            !it.isImage && it.charData.firstOrNull()?.isWhitespace() != true
        }
        if (ink.isEmpty()) return null
        return Pair(ink.minOf { it.start }, ink.maxOf { it.end })
    }

    /** 断言组（单列）：头侧（锚定/缩进侧）严格；尾侧允许尾随空白悬挂（标准排版语义） */
    private fun assertLines(
        lines: List<TextLine>, scenario: Scenario,
        rawStart: Float, rawEnd: Float, textSize: Float, cfg: String
    ) {
        val effStart = rawStart + scenario.mL
        val effEnd = rawEnd - scenario.mR
        // 头侧（阅读起始/锚定/缩进侧）严格容差；尾侧允许：尾随空格悬挂（链式预算
        // 累积上限 = 单块尾空格 ≈0.3em，实测 954.6 = box940+14.6）+ D1 满宽行
        // inkPad 余量（实测 S4 零 margin 3.2px，与 Part 3 无关的既有设计余量）
        val headTol = 2.5f
        val tailTol = TextLayoutProvider.inkPad(textSize) + textSize * 0.3f + 2.5f
        val textLines = lines.filter { it.textChars.isNotEmpty() }
        assertTrue("$cfg ${scenario.tag}: 应产生至少一行", textLines.isNotEmpty())

        for ((li, line) in textLines.withIndex()) {
            val contentLeft = line.textChars.minOf { it.start }
            val contentRight = line.textChars.maxOf { it.end }
            val ink = inkBounds(line)
            // 含空白全宽越界：诊断打印（悬挂空白属标准行为，不判失败）
            if (contentLeft < rawStart - 0.5f || contentRight > rawEnd + 0.5f) {
                println("[HANG] $cfg ${scenario.tag} L$li full=[$contentLeft,$contentRight] ink=$ink raw=[$rawStart,$rawEnd]")
            }
            assertTrue(
                "$cfg ${scenario.tag} L$li 无墨迹行（异常）: full=[$contentLeft,$contentRight]",
                ink != null
            )
            val (iL, iR) = ink!!
            if (scenario.baseRtl) {
                // RTL：头=右（锚定/缩进侧），尾=左
                assertTrue("$cfg ${scenario.tag} L$li 墨迹侵入起始侧缩进: ink=[$iL,$iR] eff=[$effStart,$effEnd]", iR <= effEnd + headTol)
                assertTrue("$cfg ${scenario.tag} L$li 墨迹尾部越界: ink=[$iL,$iR] eff=[$effStart,$effEnd] tailTol=$tailTol", iL >= effStart - tailTol)
                assertTrue("$cfg ${scenario.tag} L$li 墨迹尾部越原始列缘: ink=[$iL,$iR] raw=[$rawStart,$rawEnd]", iL >= rawStart - tailTol)
            } else {
                // LTR：头=左，尾=右
                assertTrue("$cfg ${scenario.tag} L$li 墨迹侵入起始侧缩进: ink=[$iL,$iR] eff=[$effStart,$effEnd]", iL >= effStart - headTol)
                assertTrue("$cfg ${scenario.tag} L$li 墨迹尾部越界: ink=[$iL,$iR] eff=[$effStart,$effEnd] tailTol=$tailTol", iR <= effEnd + tailTol)
                assertTrue("$cfg ${scenario.tag} L$li 墨迹尾部越原始列缘: ink=[$iL,$iR] raw=[$rawStart,$rawEnd]", iR <= rawEnd + tailTol)
            }
            assertTrue(
                "$cfg ${scenario.tag} L$li 行向错误: isRtl=${line.isRtl} 期望=${scenario.baseRtl}",
                line.isRtl == scenario.baseRtl
            )
        }

        // 阿拉伯词内跨行截断（相邻行：前行末字符与后行首字符均为阿拉伯字母）
        for (i in 0 until textLines.size - 1) {
            val prev = textLines[i].text
            val next = textLines[i + 1].text
            if (prev.isNotEmpty() && next.isNotEmpty()) {
                assertTrue(
                    "$cfg ${scenario.tag} L$i→L${i + 1} 阿拉伯词内跨行截断: '...${prev.takeLast(3)}' + '${next.take(3)}...'",
                    !(isArabicLetter(prev.last()) && isArabicLetter(next.first()))
                )
            }
        }

        // 词多重集合守恒（词内字符为逻辑序，视觉序只影响词间顺序）
        fun wordsOf(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .groupingBy { it }.eachCount()
        val joined = textLines.joinToString("") { it.text }
        assertEquals(
            "$cfg ${scenario.tag} 词集合不守恒", wordsOf(scenario.text), wordsOf(joined)
        )
    }

    // ───────── T1：单列 5 场景 × 36 配置 全量硬断言 ─────────

    @Test
    fun sweep_allScenarios_singleColumn() {
        for (width in intArrayOf(900, 1000, 1080)) {
            for (textSize in floatArrayOf(40f, 48f, 56f, 64f, 72f, 80f)) {
                for (sc in scenarios) {
                    val pages = layoutPages(sc, width, 40, 20000, dual = false, textSize)
                    val lines = pages.flatMap { it.textLines }
                    assertLines(lines, sc, 40f, (40 + width).toFloat(), textSize, "w$width/ts$textSize")
                    println("[PASS] w=$width ts=$textSize ${sc.tag} lines=${lines.size}")
                }
            }
        }
    }

    // ───────── T1b：双列（不越列缘、不横跨两列、列内缩进不被吞） ─────────

    @Test
    fun dualColumn_bothBases_marginScenarios() {
        val dualCases = listOf(
            Scenario("dual-rtlLi", liText, true, 0f, 60f),
            Scenario("dual-ltrMixedLi", ltrText, false, 60f, 0f)
        )
        for (width in intArrayOf(1000, 1080)) {
            for (textSize in floatArrayOf(48f, 64f)) {
                for (sc in dualCases) {
                    // visibleHeight 小值强制段中切列（覆盖 A5 换页/换列 cursor 重置路径）
                    val lines = layoutPages(sc, width, 40, 300, dual = true, textSize)
                        .flatMap { it.textLines }
                    assertTrue("dual ${sc.tag} 应产生多行", lines.count { it.textChars.isNotEmpty() } > 1)

                    val left = layoutBoundsLeftColumn()
                    val right = layoutBoundsRightColumn()
                    val cols = listOf(
                        left.startX.toFloat() to left.endX.toFloat(),
                        right.startX.toFloat() to right.endX.toFloat()
                    )
                    val slack = TextLayoutProvider.inkPad(textSize) + 1.5f
                    val headTol = 2.5f
                    val tailTol = TextLayoutProvider.inkPad(textSize) + textSize * 0.3f + 2.5f
                    for ((li, line) in lines.withIndex()) {
                        if (line.textChars.isEmpty()) continue
                        val center = line.textChars.map { (it.start + it.end) / 2f }.average().toFloat()
                        val col = cols.firstOrNull { center >= it.first && center <= it.second }
                        assertTrue(
                            "dual ${sc.tag} L$li 不属于任何列: center=$center",
                            col != null
                        )
                        val (cs, ce) = col!!
                        val ink = inkBounds(line)
                        assertTrue("dual ${sc.tag} L$li 无墨迹行（异常）", ink != null)
                        val (iL, iR) = ink!!
                        if (sc.baseRtl) {
                            assertTrue("dual ${sc.tag} L$li 墨迹侵入列起始侧缩进: ink=[$iL,$iR] col=[$cs,$ce]", iR <= (ce - sc.mR) + headTol)
                            assertTrue("dual ${sc.tag} L$li 墨迹尾部越列缘: ink=[$iL,$iR] col=[$cs,$ce] tailTol=$tailTol", iL >= (cs + sc.mL) - tailTol && iL >= cs - slack)
                        } else {
                            assertTrue("dual ${sc.tag} L$li 墨迹侵入列起始侧缩进: ink=[$iL,$iR] col=[$cs,$ce]", iL >= (cs + sc.mL) - headTol)
                            assertTrue("dual ${sc.tag} L$li 墨迹尾部越列缘: ink=[$iL,$iR] col=[$cs,$ce] tailTol=$tailTol", iR <= (ce - sc.mR) + tailTol && iR <= ce + slack)
                        }
                    }
                    println("[PASS] dual w=$width ts=$textSize ${sc.tag} lines=${lines.size}")
                }
            }
        }
    }

    // ───────── 跨页（单列小 visibleHeight，覆盖 A5 换页 cursor 重置路径） ─────────

    @Test
    fun pageCross_singleColumn_marginScenarios() {
        val crossCases = listOf(
            Scenario("cross-rtlLi", liText, true, 0f, 60f),
            Scenario("cross-ltrMixedLi", ltrText, false, 60f, 0f),
            Scenario("cross-rtlCss", liText, true, 40f, 40f)
        )
        for (textSize in floatArrayOf(48f, 64f)) {
            for (sc in crossCases) {
                // visibleHeight≈一行多 → 段落必然跨页，每页首行来自 A5 重置路径
                val pages = layoutPages(sc, 1000, 40, 110, dual = false, textSize)
                val lines = pages.flatMap { it.textLines }
                assertLines(lines, sc, 40f, 1040f, textSize, "cross/ts$textSize")
                assertTrue(
                    "cross ${sc.tag} 未触发跨页（未覆盖换页路径）pages=${pages.size}",
                    pages.size >= 2
                )
                println("[PASS] cross ts=$textSize ${sc.tag} pages=${pages.size} lines=${lines.size}")
            }
        }
    }
}
