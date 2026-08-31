package com.wxn.bookread.provider

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.String.format

/**
 * 诊断（临时，不进回归）：方案 C 整段路径 + 双钳制（start/end step-length guard）
 * 全公式复刻。U7 1748xx 轮出现「词距异常大 + 行右缘溢出裁切」，本测试在真机
 * dump：①逐行 raw ph 不连续点；②钳制触发序列（哪个字符、哪个分支）；
 * ③shiftRunLineToCursor 整行平移量；④词组最终位置/词距/溢出。
 *
 * 运行（需连接设备）:
 *   gradlew.bat :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.WholeLayoutBoxDiagInstrumentedTest
 * 输出: adb logcat -d -s WholeLayoutBoxDiag:D
 */
@RunWith(AndroidJUnit4::class)
class WholeLayoutBoxDiagInstrumentedTest {

    private fun isRtlCp(cp: Int): Boolean {
        if (cp > 0xFFFF) return false
        val d = Character.getDirectionality(cp.toChar())
        return d == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
    }

    private fun build(text: String, paint: TextPaint, width: Int, indent0: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.LTR)
            .setIncludePad(false)
            .setIndents(intArrayOf(indent0, 0), intArrayOf(0, 0))
            .build()

    /** 完整复刻 placeCharsFromLayout 当前公式（含双钳制），返回每行模拟结果 */
    private fun simulate(
        tag: String, text: String, paint: TextPaint, width: Int,
        indent0: Int, startX: Float, marginLeft: Float
    ) {
        val layout = build(text, paint, width, indent0)
        val out = StringBuilder()
        out.append(format("%s: paraLen=%d lineCount=%d%n", tag, text.length, layout.lineCount))
        for (li in 0 until layout.lineCount) {
            val s = layout.getLineStart(li)
            val e = layout.getLineEnd(li)
            val isLast = li == layout.lineCount - 1
            out.append(format(
                "LINE %d [%d,%d) w=%.1f L=%.1f R=%.1f%n",
                li, s, e, layout.getLineWidth(li), layout.getLineLeft(li), layout.getLineRight(li)))

            // ── 逐字符复刻引擎公式 ──
            data class ChInfo(
                val str: String, val rawPh: Float, val rawPhN: Float,
                val absLeft: Float, val absRight: Float, val branch: String
            )
            val infos = ArrayList<ChInfo>()
            var prevEdge = Float.NaN
            var off = s
            while (off < e) {
                val cp = Character.codePointAt(text, off)
                val n = off + Character.charCount(cp)
                val chS = text.substring(off, n)
                val chW = paint.measureText(chS)
                val isLineEnd = n >= e
                val rtl = isRtlCp(cp)   // run.isRtl=false（整段合成 run）
                var localStart = layout.getPrimaryHorizontal(off)
                var localEnd = if (isLineEnd && !isLast) {
                    if (rtl) localStart - chW else localStart + chW
                } else {
                    layout.getPrimaryHorizontal(n)
                }
                var branch = "-"
                if (!prevEdge.isNaN() && kotlin.math.abs(localStart - prevEdge) > (chW * 2f + 1f)) {
                    localStart = prevEdge
                    localEnd = if (rtl) localStart - chW else localStart + chW
                    branch = "START"
                } else if (kotlin.math.abs(localEnd - localStart) > (chW * 2f + 1f)) {
                    localEnd = if (rtl) localStart - chW else localStart + chW
                    branch = "END"
                }
                val absLeft = startX + minOf(localStart, localEnd)
                val absRight = startX + maxOf(localStart, localEnd)
                infos.add(ChInfo(chS, layout.getPrimaryHorizontal(off),
                    if (isLineEnd && !isLast) Float.NaN else layout.getPrimaryHorizontal(n),
                    absLeft, absRight, branch))
                prevEdge = if (rtl) absLeft else absRight
                off = n
            }

            // raw ph 不连续点（与钳制无关的客观事实）
            for (i in 1 until infos.size) {
                val d = kotlin.math.abs(infos[i].rawPh - infos[i - 1].rawPh)
                val prevW = paint.measureText(infos[i - 1].str)
                if (d > prevW * 2f + 1f) {
                    out.append(format(
                        "  PHJUMP [%d]'%s'→[%d]'%s' Δ=%.1f (prevW=%.1f) ph %.1f→%.1f%n",
                        s + i - 1, infos[i - 1].str.replace(" ", "␣"),
                        s + i, infos[i].str.replace(" ", "␣"),
                        d, prevW, infos[i - 1].rawPh, infos[i].rawPh))
                }
            }
            // 钳制触发点
            val clamped = infos.withIndex().filter { it.value.branch != "-" }
            out.append(format("  CLAMPS: %d%n", clamped.size))
            for ((i, ci) in clamped.take(12)) {
                out.append(format(
                    "  %s[%d] '%s' rawPh=%.1f → box[%.1f,%.1f]%n",
                    ci.branch, s + i, ci.str.replace(" ", "␣"), ci.rawPh, ci.absLeft, ci.absRight))
            }

            // shiftRunLineToCursor 复刻（lineIsRtl=false, cursor=startX+marginLeft）
            var blockMin = Float.POSITIVE_INFINITY
            var blockMax = Float.NEGATIVE_INFINITY
            for (ci in infos) {
                if (ci.absLeft < blockMin) blockMin = ci.absLeft
                if (ci.absRight > blockMax) blockMax = ci.absRight
            }
            val cursor = startX + marginLeft
            val shift = cursor - blockMin
            out.append(format(
                "  SHIFT: blockMin=%.1f blockMax=%.1f cursor=%.1f shift=%+.1f → final[%.1f,%.1f] (page right≈%.0f)%n",
                blockMin, blockMax, cursor, shift, blockMin + shift, blockMax + shift, startX + width))

            // 词组（renderGroup=空白分词）最终位置/词距/重叠
            var groupMin = Float.NaN
            var groupSb = StringBuilder()
            var prevRight = Float.NaN
            var prevWord = ""
            fun flush() {
                val str = groupSb.toString().trim()
                if (str.isNotEmpty() && !groupMin.isNaN()) {
                    val adv = paint.measureText(str)
                    val fL = groupMin + shift
                    val fR = fL + adv
                    val rel = if (!prevWord.isEmpty())
                        format("gap=%+.1f%s", fL - prevRight,
                            if (fL < prevRight) " ←OVERLAP" else "")
                    else ""
                    out.append(format(
                        "  WORD '%s' final=[%.1f,%.1f] %s%n",
                        if (str.length > 12) str.substring(0, 12) + "…" else str, fL, fR, rel))
                    prevRight = fR
                    prevWord = str
                }
                groupSb = StringBuilder()
                groupMin = Float.NaN
            }
            for (ci in infos) {
                groupSb.append(ci.str)
                if (groupMin.isNaN() || ci.absLeft < groupMin) groupMin = ci.absLeft
                if (ci.str.firstOrNull()?.isWhitespace() == true) flush()
            }
            flush()
        }
        println(out.toString())
        android.util.Log.d("WholeLayoutBoxDiag", out.toString())
    }

    @Test
    fun dump_u7_currentFormula() {
        val startX = 44f
        val marginLeft = 0f
        val width = 1040

        // ① h1（真机截图右缘裁切：'（LT' 后被切）
        val h1 = "U7 残留缺陷探针（LTR 基调混排）"
        val titlePaint = TextPaint().apply {
            textSize = 76f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        simulate("H1", h1, titlePaint, width, 0, startX, marginLeft)

        // ② intro 段（真机截图第 2 行：'C' 被推至右缘，'的目标场' 溢出裁切）
        val intro = "本书用于复现「中文/英文开头段落内嵌长阿语块」的折行语序问题（方案 C 的目标场景）。请逐段观察阿语内容跨行时行与行之间的词序是否连贯。"
        val paint = TextPaint().apply {
            textSize = 54f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        simulate("INTRO", intro, paint, width, 108, startX, marginLeft)

        // ③ A 主探针（整段混排核心样本）
        val a = "A 主探针：史书引述阿拉伯编年史原文如下——وصل الجيش الأول 300 فارس إلى المدينة في صباح يوم الخميس وتحرك الجيش الثاني 500 فارس نحو الشرق عبر الجبال العالية حتى بلغوا النهر الكبير عند الغروب ثم عادوا إلى المعسكر الرئيسي 42 مرة في تلك السنة الطويلة وقبل نهاية الفصل الذي ذكره المؤرخ في مقدمته."
        simulate("PARA-A", a, paint, width, 108, startX, marginLeft)
    }
}
