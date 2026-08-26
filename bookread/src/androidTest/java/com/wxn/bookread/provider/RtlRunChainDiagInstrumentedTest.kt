package com.wxn.bookread.provider

import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.RunLayout
import com.wxn.bookread.data.model.TextPage
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/**
 * 诊断（instrumented）：残留整行起始缘溢出的【逐 run 像素归因】。
 *
 * 背景：Part 1/2 已实施后 sweep 仍测到 RTL 12/18 配置整行内容比栏宽 5~60px，
 * 右缘钉在 effEnd、左缘外溢；真机 152% 大字号表现为 URL→'L'、https→'tps' 截断。
 *
 * 本测试 1:1 复刻 TextLayoutProvider.layoutNormalTextRtl 的 run 循环
 * （同 StaticLayout 参数 / 同 wrap-back 判据 / 同 placeChars 定位公式 / 同 shift 逻辑），
 * 逐 run 打印：
 *   - cursorIn / box(firstLineWidth) / lw=getLineWidth(0)（墨迹宽，剥尾随空白）
 *   - 放置块总宽 blockW / 块内墨迹宽 inkW / 块内空格宽 spaceW / 尾字符 patch 信息
 * 逐行汇总 contentW 与 fullWidth 的差值，并按
 *   空格宽 + patch 差 + 2×inkPad 逐项归因。
 *
 * 同时调用真实引擎 layoutNormalTextRtl 输出最终行边界做保真交叉验证：
 * 若复刻行宽 ≈ 真实行宽（±1px），则复刻内部数据可信。
 *
 * 运行（需连接设备）:
 *   gradlew.bat :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.RtlRunChainDiagInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class RtlRunChainDiagInstrumentedTest {

    private val liText =
        "العنصر الثالث مع رقم 42 وكلمة English واختصار URL مثل https://example.com"

    /** 真正的 LTR 基调文本（首强 'A'）——liText 首强是阿语，segmenter 恒判 RTL 基调 */
    private val ltrText =
        "A mixed English paragraph with Arabic كلمة and number 42 and URL https://example.com inside"

    private fun isWordChar(ch: Char): Boolean {
        if (!ch.isLetter()) return false
        val code = ch.code
        return !(code in 0x2E80..0x9FFF ||
                code in 0xAC00..0xD7FF ||
                code in 0x1100..0x11FF ||
                code in 0xA960..0xA97F ||
                code in 0xF900..0xFAFF ||
                code in 0xFF66..0xFF9D)
    }

    private class DChar(val ch: String, var start: Float, var end: Float) {
        val isSpace get() = ch.firstOrNull()?.isWhitespace() == true
    }

    private fun esc(s: String) = s.replace(" ", "␣").replace("\n", "\\n")

    private fun configure(width: Int, padding: Int) {
        ChapterProvider.apply {
            paddingHorizontal = padding
            paddingVertical = padding
            visibleWidth = width
            visibleHeight = 20000
            visibleRight = paddingHorizontal + visibleWidth
            visibleBottom = paddingVertical + visibleHeight
            lineSpacingExtra = 1.2f
            dualColumnEnabled = false
        }
    }

    /** 真实引擎输出（保真对照 + 真实行边界 dump） */
    private fun runRealEngine(
        text: String, baseRtl: Boolean, paint: TextPaint,
        mL: Float, mR: Float
    ): ArrayList<TextPage> {
        val seg = RTLSegmenter.segment(text)
        val textPages = arrayListOf(TextPage())
        TextLayoutProvider.layoutNormalTextRtl(
            text, null, seg, paint,
            marginLeft = mL, marginRight = mR,
            firstLineIndent = 0f,
            isTitle = false, isListRow = true, listLevel = 1,
            paragraphIndex = 0,
            textAlign = com.wxn.base.bean.CssTextAlign.CssTextAlignUndefined,
            lineHeightParam = 1f,
            paragraph = com.wxn.base.bean.ReaderText.Text(text),
            textPages = textPages,
            pageLines = arrayListOf(),
            pageLengths = arrayListOf(),
            stringBuilder = StringBuilder(),
            offsetY = ChapterProvider.paddingVertical.toFloat(),
            bounds = layoutBoundsPage(),
            chapterIsRtl = baseRtl,
            hasInlineImage = false
        )
        return textPages
    }

    private fun diag(
        tag: String, text: String, textSize: Float,
        width: Int, padding: Int, marginStart: Float, baseRtl: Boolean = true
    ) {
        configure(width, padding)
        val paint = TextPaint().apply {
            color = Color.BLACK
            this.textSize = textSize
            isAntiAlias = true
        }
        val mL = if (baseRtl) 0f else marginStart
        val mR = if (baseRtl) marginStart else 0f
        val startXCol = layoutBoundsPage().startX.toFloat() + mL
        val endXCol = layoutBoundsPage().endX.toFloat() - mR
        val fullWidth = layoutBoundsPage().width - mL.roundToInt() - mR.roundToInt()
        val inkPad = TextLayoutProvider.inkPad(textSize)

        println("\n########## $tag ts=$textSize w=$width baseRtl=$baseRtl col=[$startXCol,$endXCol] fullWidth=$fullWidth inkPad=$inkPad")

        // ── 真实引擎（对照）──
        val realWidths = mutableListOf<Float>()
        val realPages = runRealEngine(text, baseRtl, paint, mL, mR)
        for ((li, line) in realPages.flatMap { it.textLines }.withIndex()) {
            if (line.textChars.isEmpty()) continue
            val l = line.textChars.minOf { it.start }
            val r = line.textChars.maxOf { it.end }
            realWidths += (r - l)
            println("[REAL] L$li isRtl=${line.isRtl} content=[$l,$r] w=${r - l} text='${esc(line.text)}'")
        }

        // ── 复刻 run 循环 ──
        val seg = RTLSegmenter.segment(text)
        val runs = if (seg.runs.isEmpty()) listOf(RunLayout(seg.baseRtl, 0, text.length)) else seg.runs
        val ordered = if (baseRtl) runs.asReversed() else runs

        // 对齐引擎 :88-98：初始 lineIsRtl = segDirect.baseRtl（有 Run 时），非外部参数
        var lineIsRtl = seg.baseRtl
        var cursor = if (lineIsRtl) endXCol else startXCol
        val lines = mutableListOf<MutableList<Pair<String, MutableList<DChar>>>>()
        var curLine = mutableListOf<Pair<String, MutableList<DChar>>>()

        for ((ri, r) in ordered.withIndex()) {
            val runText = text.subSequence(r.offset, r.offset + r.length).toString()
            if (runText.isBlank()) continue

            val atEdge = if (lineIsRtl) cursor >= endXCol - 0.5f else cursor <= startXCol + 0.5f
            val firstLineWidth = if (atEdge) fullWidth
            else (if (lineIsRtl) cursor - startXCol else endXCol - cursor)
                .roundToInt().coerceAtLeast(1)
            var sharedLine = !atEdge && curLine.isNotEmpty()
            val sharedLineIndent = fullWidth - firstLineWidth
            val leftIndentArr = if (lineIsRtl) intArrayOf(0, 0) else intArrayOf(sharedLineIndent, 0)
            val rightIndentArr = if (lineIsRtl) intArrayOf(sharedLineIndent, 0) else intArrayOf(0, 0)

            var layout = StaticLayout.Builder.obtain(runText, 0, runText.length, paint, fullWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(
                    if (r.isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
                ).setIncludePad(false)
                .setIndents(leftIndentArr, rightIndentArr)
                .build()

            val line0End = layout.getLineEnd(0)
            val midWord = line0End < runText.length &&
                    isWordChar(runText[line0End - 1]) && isWordChar(runText[line0End])
            val lw0 = layout.getLineWidth(0)
            val wrapBack = sharedLine && layout.lineCount > 0 && (lw0 > firstLineWidth + 1f || midWord)
            if (wrapBack) {
                sharedLine = false
                layout = StaticLayout.Builder.obtain(runText, 0, runText.length, paint, fullWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(
                        if (r.isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
                    ).setIncludePad(false)
                    .setIndents(intArrayOf(0, 0), intArrayOf(0, 0))
                    .build()
            }
            println("  RUN$ri(${if (r.isRtl) "RTL" else "LTR"}) shared=$sharedLine wrapBack=$wrapBack midWord=$midWord cursorIn=$cursor box=$firstLineWidth lw0=$lw0 text='${esc(runText)}'")

            for (li in 0 until layout.lineCount) {
                val lineShared = sharedLine && li == 0
                if (!lineShared) {
                    lineIsRtl = seg.baseRtl
                    cursor = if (lineIsRtl) endXCol else startXCol
                    if (curLine.isNotEmpty()) {
                        lines += curLine
                        curLine = mutableListOf()
                    }
                }
                val lineStart = layout.getLineStart(li)
                val lineEnd = layout.getLineEnd(li)
                val isLastLayoutLine = li == layout.lineCount - 1

                val block = mutableListOf<DChar>()
                var off = lineStart
                var patchedLast = ""
                while (off < lineEnd) {
                    val cp = Character.codePointAt(runText, off)
                    val cc = Character.charCount(cp)
                    val nextOff = off + cc
                    val ch = runText.substring(off, nextOff)
                    val chW = paint.measureText(ch)
                    val localStart = layout.getPrimaryHorizontal(off)
                    val isLineEndCh = nextOff >= lineEnd
                    val patched = isLineEndCh && !isLastLayoutLine
                    val localEnd = if (patched) {
                        if (r.isRtl) localStart - chW else localStart + chW
                    } else layout.getPrimaryHorizontal(nextOff)
                    if (patched) patchedLast = ch
                    block += DChar(ch, startXCol + minOf(localStart, localEnd), startXCol + maxOf(localStart, localEnd))
                    off = nextOff
                }

                var bMin = Float.POSITIVE_INFINITY
                var bMax = Float.NEGATIVE_INFINITY
                block.forEach { bMin = minOf(bMin, it.start); bMax = maxOf(bMax, it.end) }
                val shift = if (lineIsRtl) cursor - bMax else cursor - bMin
                block.forEach { it.start += shift; it.end += shift }
                val nMin = bMin + shift
                val nMax = bMax + shift
                cursor = if (lineIsRtl) nMin else nMax

                val ink = block.filter { !it.isSpace }
                val inkW = if (ink.isEmpty()) 0f else ink.maxOf { it.end } - ink.minOf { it.start }
                val blockW = nMax - nMin
                val spaceChars = block.filter { it.isSpace }
                val leadSp = spaceChars.filter { it.start < (ink.minOfOrNull { c -> c.start } ?: Float.MAX_VALUE) }.sumOf { (it.end - it.start).toDouble() }.toFloat()
                val tailSp = (spaceChars.sumOf { (it.end - it.start).toDouble() }.toFloat()) - leadSp
                println("    R$ri.L$li shared=$lineShared lw=${layout.getLineWidth(li)} patchedLast='$patchedLast' blockW=$blockW inkW=$inkW leadSp=$leadSp tailSp=$tailSp block=[$nMin,$nMax] str='${esc(runText.substring(lineStart, lineEnd))}'")

                curLine += ("R$ri.L$li" to block)
            }
        }
        if (curLine.isNotEmpty()) lines += curLine

        // ── T2 对照断言（方案 §9.4）：复刻链与真实引擎逐行等宽（|Δ|≤1px），
        //    且复刻行墨迹落在有效列内（头侧严格 / 尾侧允许尾随空格悬挂，§9.6 D1）。
        val replicaWidths = mutableListOf<Float>()
        val headTol = 2.5f
        val tailTol = inkPad + textSize * 0.3f + 2.5f
        for ((i, ln) in lines.withIndex()) {
            val all = ln.flatMap { it.second }
            val cL = all.minOf { it.start }
            val cR = all.maxOf { it.end }
            val cW = cR - cL
            replicaWidths += cW
            val ink = all.filter { !it.isSpace }
            val inkMsg: String
            if (ink.isNotEmpty()) {
                val iL = ink.minOf { it.start }
                val iR = ink.maxOf { it.end }
                inkMsg = "ink=[$iL,$iR]"
                if (baseRtl) {
                    org.junit.Assert.assertTrue(
                        "$tag LINE$i 墨迹越有效列: ink=[$iL,$iR] eff=[$startXCol,$endXCol] headTol=$headTol tailTol=$tailTol",
                        iR <= endXCol + headTol && iL >= startXCol - tailTol
                    )
                } else {
                    org.junit.Assert.assertTrue(
                        "$tag LINE$i 墨迹越有效列: ink=[$iL,$iR] eff=[$startXCol,$endXCol] headTol=$headTol tailTol=$tailTol",
                        iL >= startXCol - headTol && iR <= endXCol + tailTol
                    )
                }
            } else {
                inkMsg = "ink=(none)"
            }
            val inkW = if (ink.isEmpty()) 0f else ink.maxOf { it.end } - ink.minOf { it.start }
            val spW = cW - inkW
            val spill = cW - (fullWidth - 2f * inkPad)
            println("  [LINE$i] blocks=${ln.size} content=[$cL,$cR] $inkMsg contentW=$cW inkW=$inkW spaceW=$spW fullWidth=$fullWidth → 预计锚定后起始缘溢出=$spill (内容宽 - (栏宽-2×inkPad))")
        }
        org.junit.Assert.assertEquals(
            "$tag 行数不一致: real=${realWidths.size} replica=${replicaWidths.size}",
            realWidths.size, replicaWidths.size
        )
        for (i in realWidths.indices) {
            org.junit.Assert.assertTrue(
                "$tag L$i 行宽偏差过大: real=${realWidths[i]} replica=${replicaWidths[i]}",
                kotlin.math.abs(realWidths[i] - replicaWidths[i]) <= 1f
            )
        }
        println("  [T2] $tag 复刻/真实 行宽对照通过 (${realWidths.size} 行)")
    }

    @Test
    fun diag_rtlBase_selected() {
        // §8.4 已知失败配置 900/64（实测溢出 63px）+ 截图量级（大字号）
        diag("diagRtl-900-64", liText, textSize = 64f, width = 900, padding = 40, marginStart = 60f)
        diag("diagRtl-1000-64", liText, textSize = 64f, width = 1000, padding = 40, marginStart = 60f)
        diag("diagRtl-1000-72", liText, textSize = 72f, width = 1000, padding = 40, marginStart = 60f)
        diag("diagRtl-1080-80", liText, textSize = 80f, width = 1080, padding = 40, marginStart = 60f)
        // 真正的 LTR 基调用 ltrText（首强 'A'）——liText 首强是阿语，恒判 RTL 基调
        diag("diagLtr-1000-64", ltrText, textSize = 64f, width = 1000, padding = 40, marginStart = 60f, baseRtl = false)
    }
}
