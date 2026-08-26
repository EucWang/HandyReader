package com.wxn.bookread.provider

import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RTL 列表圆点锚点统一回归（plan-fix-rtl-list-dot-anchor-drift.md §6.1 T1-T7）。
 *
 * 缺陷：绘制层圆点 x 锚点取"实际文字边缘"（RTL=max(end)+30 / LTR=first.start-30），
 *   排版层按 text-align 移动文字 → Center 列表项圆点随居中文字漂移（EPUB-B
 *   "محاذاة خاصة داخل القائمة" 实机复现：justify/普通项圆点同 x，center 项漂到列中部）。
 *
 * 修复语义（浏览器 outside marker）：排版期（postProcessRtlLine）把锚点钉在内容盒
 *   阅读起始侧层级槽位（pre-indent），写入 TextLine.lineDot.anchorX；绘制期优先消费、
 *   NaN 回退 legacy 文字边缘推导（纯 LTR legacy 路径零影响）。
 *
 * 断言口径：configure(1000, 40) ⇒ bounds = [40, 1040]，
 *   右槽位 anchor = 1040 - mR - inkPad(ts)；左槽位 anchor = 40 + mL + inkPad(ts)。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.RtlListDotAnchorUniformInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class RtlListDotAnchorUniformInstrumentedTest {

    /** EPUB-B "محاذاة خاصة داخل القائمة" 三项同源（均短文本单行） */
    private val shortJustify = "عنصر قصير بمحاذاة مطرافية"
    private val shortCenter = "هذا العنصر في المنتصف"
    private val shortPlain = "عنصر عادي قصير"

    /** 长列表项（折行），复用首行缩进回归测试的长文本 */
    private val longRtl =
        "العنصر الثاني طويل جدا ويتوقع أن يلتف على أكثر من سطر واحد للتأكد من أن النقطة تظهر على السطر الأول فقط"

    /** LTR 基调混排（首强字符 'A' → 基调 LTR，走 RTL 引擎 baseLtr 分支） */
    private val shortLtrMixed = "A short LTR line with كلمة mixed 42"

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
            columnGapActual = 0
            columnWidth = 0
        }
    }

    private fun layoutPages(
        text: String, baseRtl: Boolean, indent: Float, align: CssTextAlign,
        mL: Float, mR: Float, width: Int, textSize: Float, offsetY: Float,
        isListRow: Boolean = true, listLevel: Int = 1
    ): ArrayList<TextPage> {
        configure(width, 40)
        val paint = TextPaint().apply {
            color = Color.BLACK
            this.textSize = textSize
            isAntiAlias = true
        }
        val seg = RTLSegmenter.segment(text)
        val textPages = arrayListOf(TextPage())
        TextLayoutProvider.layoutNormalTextRtl(
            text,
            null,
            seg,
            paint,
            marginLeft = mL,
            marginRight = mR,
            firstLineIndent = indent,
            isTitle = false,
            isListRow = isListRow,
            listLevel = listLevel,
            paragraphIndex = 0,
            textAlign = align,
            lineHeightParam = 1f,
            paragraph = ReaderText.Text(text),
            textPages = textPages,
            pageLines = arrayListOf(),
            pageLengths = arrayListOf(),
            stringBuilder = StringBuilder(),
            offsetY = offsetY,
            bounds = layoutBoundsPage(),
            chapterIsRtl = baseRtl,
            hasInlineImage = false
        )
        return textPages
    }

    private fun visibleLines(pages: List<TextPage>) =
        pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }

    private fun lineCount(pages: List<TextPage>): Int = visibleLines(pages).size

    /** 列表首行圆点锚点（含存在性/写入校验） */
    private fun dotAnchor(pages: List<TextPage>, lineIdx: Int = 0): Float {
        val lines = visibleLines(pages)
        assertTrue("行数不足: 需要 index=$lineIdx, 实际 ${lines.size} 行", lines.size > lineIdx)
        val line = lines[lineIdx]
        assertTrue("第 $lineIdx 行 lineDot 未设置（列表首行应设置）", line.lineDot != null)
        val ax = line.lineDot!!.anchorX
        assertTrue("第 $lineIdx 行 anchorX 未写入（NaN）", !ax.isNaN())
        return ax
    }

    /** 行的墨迹边缘（排除空白与图片）：与既有测试同口径 */
    private fun inkBounds(line: com.wxn.bookread.data.model.TextLine): Pair<Float, Float>? {
        val ink = line.textChars.filter {
            !it.isImage && it.charData.firstOrNull()?.isWhitespace() != true
        }
        if (ink.isEmpty()) return null
        return Pair(ink.minOf { it.start }, ink.maxOf { it.end })
    }

    private fun inkOf(pages: List<TextPage>, lineIdx: Int = 0): Pair<Float, Float> {
        val lines = visibleLines(pages)
        assertTrue("行数不足: 需要 index=$lineIdx, 实际 ${lines.size} 行", lines.size > lineIdx)
        val ink = inkBounds(lines[lineIdx])
        assertTrue("第 $lineIdx 行无墨迹（异常）", ink != null)
        return ink!!
    }

    // ───────── T1：槽位统一（主断言，红灯用例） ─────────
    // Center / Justify / Undefined(→Right) 三个单行列表段首行 anchorX 两两相等，
    // 且 = 右槽位 endX - mR - inkPad(ts)。修复前 Center 行 anchor 缺失/漂移。

    @Test
    fun rtl_dotAnchor_uniform_acrossAlignments() {
        for (textSize in floatArrayOf(48f, 64f)) {
            val mR = 60f
            val expected = ChapterProvider.visibleRight - mR - TextLayoutProvider.inkPad(textSize)

            val cases = mapOf(
                "Center" to (shortCenter to CssTextAlign.CssTextAlignCenter),
                "Justify" to (shortJustify to CssTextAlign.CssTextAlignJustify),
                "Undefined→Right" to (shortPlain to CssTextAlign.CssTextAlignUndefined)
            )

            val anchors = cases.map { (name, case) ->
                val pages = layoutPages(
                    case.first, true, 0f, case.second, 0f, mR, 1000, textSize, 40f
                )
                assertEquals("ts=$textSize $name 应恰为单行", 1, lineCount(pages))
                dotAnchor(pages) to name
            }

            anchors.forEach { (ax, name) ->
                assertTrue(
                    "ts=$textSize $name anchor=$ax 应≈右槽位 expected=$expected",
                    kotlin.math.abs(ax - expected) <= 0.5f
                )
            }
            assertTrue(
                "ts=$textSize 三种对齐 anchor 应一致: ${anchors.map { it.second to it.first }}",
                anchors.all { kotlin.math.abs(it.first - anchors.first().first) <= 0.5f }
            )
            println("[PASS] T1 ts=$textSize expected=$expected anchors=${anchors.map { it.first }}")
        }
    }

    // ───────── T2：justify 长段（折行）首行锚点与 T1 同槽位 ─────────

    @Test
    fun rtl_dotAnchor_justifyWrappedFirstLine_sameSlot() {
        val textSize = 48f
        val mR = 60f
        val pages = layoutPages(
            longRtl, true, 0f, CssTextAlign.CssTextAlignJustify, 0f, mR, 1000, textSize, 40f
        )
        assertTrue("长段必须折行(实际 ${lineCount(pages)} 行)", lineCount(pages) > 1)
        val expected = ChapterProvider.visibleRight - mR - TextLayoutProvider.inkPad(textSize)
        val ax = dotAnchor(pages)
        assertTrue(
            "justify 长段首行 anchor=$ax 应≈右槽位 expected=$expected",
            kotlin.math.abs(ax - expected) <= 0.5f
        )
        println("[PASS] T2 anchor=$ax expected=$expected")
    }

    // ───────── T3：锚点不受首行缩进影响（pre-indent 语义） ─────────

    @Test
    fun rtl_dotAnchor_independentOfFirstLineIndent() {
        for (textSize in floatArrayOf(48f, 64f)) {
            val mR = 60f
            val expected = ChapterProvider.visibleRight - mR - TextLayoutProvider.inkPad(textSize)

            // justify（默认路径）单行=首末行 → 退化 Right，文字被缩进内推，锚点不受影响
            val noIndent = layoutPages(
                shortJustify, true, 0f, CssTextAlign.CssTextAlignJustify, 0f, mR, 1000, textSize, 40f
            )
            val withIndent = layoutPages(
                shortJustify, true, 2f * textSize, CssTextAlign.CssTextAlignJustify,
                0f, mR, 1000, textSize, 40f
            )
            assertEquals("ts=$textSize 无缩进应单行", 1, lineCount(noIndent))
            assertEquals("ts=$textSize 带缩进应仍单行", 1, lineCount(withIndent))

            val axNo = dotAnchor(noIndent)
            val axInd = dotAnchor(withIndent)
            assertTrue(
                "ts=$textSize 缩进不应移动锚点: no=$axNo ind=$axInd",
                kotlin.math.abs(axNo - axInd) <= 0.5f
            )
            assertTrue(
                "ts=$textSize 带缩进 anchor=$axInd 应≈右槽位 expected=$expected",
                kotlin.math.abs(axInd - expected) <= 0.5f
            )
            // 对照：文字墨迹右缘确被缩进内推（证明缩进参数本身生效）
            val inkNo = inkOf(noIndent).second
            val inkInd = inkOf(withIndent).second
            assertTrue(
                "ts=$textSize 缩进后文字右缘应内移: no=$inkNo ind=$inkInd",
                inkInd < inkNo - 1f
            )
            println("[PASS] T3 ts=$textSize anchor=$axInd inkNo=$inkNo inkInd=$inkInd")
        }
    }

    // ───────── T4：baseLtr 混排列表行 → 左槽位 ─────────

    @Test
    fun ltrBase_dotAnchor_leftSlot() {
        val textSize = 48f
        val mL = 60f
        val pages = layoutPages(
            shortLtrMixed, false, 0f, CssTextAlign.CssTextAlignLeft, mL, 0f, 1000, textSize, 40f
        )
        assertEquals("LTR 混排应单行", 1, lineCount(pages))
        val expected = ChapterProvider.paddingHorizontal + mL + TextLayoutProvider.inkPad(textSize)
        val ax = dotAnchor(pages)
        assertTrue(
            "baseLtr anchor=$ax 应≈左槽位 expected=$expected",
            kotlin.math.abs(ax - expected) <= 0.5f
        )
        println("[PASS] T4 anchor=$ax expected=$expected")
    }

    // ───────── T5：非列表行不设置 lineDot ─────────

    @Test
    fun nonList_lineDotNotSet() {
        val pages = layoutPages(
            shortPlain, true, 0f, CssTextAlign.CssTextAlignUndefined,
            0f, 0f, 1000, 48f, 40f, isListRow = false, listLevel = 0
        )
        assertTrue("应至少一行", lineCount(pages) >= 1)
        visibleLines(pages).forEach {
            assertTrue("非列表行不应设置 lineDot（实际 ${it.lineDot}）", it.lineDot == null)
        }
        println("[PASS] T5 lineCount=${lineCount(pages)}")
    }

    // ───────── T6：Center 行文字位置不变（修复只钉圆点，不动文字） ─────────

    @Test
    fun rtl_centerText_stillCentered() {
        val textSize = 48f
        val mR = 60f
        val pages = layoutPages(
            shortCenter, true, 0f, CssTextAlign.CssTextAlignCenter, 0f, mR, 1000, textSize, 40f
        )
        assertEquals("Center 短行应单行", 1, lineCount(pages))
        val rawStart = ChapterProvider.paddingHorizontal.toFloat()
        val rawEnd = ChapterProvider.visibleRight - mR
        val ink = inkOf(pages)
        val inkCenter = (ink.first + ink.second) / 2f
        val boxCenter = (rawStart + rawEnd) / 2f
        assertTrue(
            "Center 行墨迹中心 $inkCenter 应≈内容盒中心 $boxCenter",
            kotlin.math.abs(inkCenter - boxCenter) <= 3f
        )
        // 证明确实发生了居中（文字远离右槽位），而非"贴右缘平凡通过"
        assertTrue(
            "Center 行墨迹右缘 ${ink.second} 应明显小于 rawEnd=$rawEnd（否则未真正居中）",
            ink.second < rawEnd - 100f
        )
        println("[PASS] T6 inkCenter=$inkCenter boxCenter=$boxCenter")
    }

    // ───────── T7：baseLtr + Center → 左槽位锚点 + 文字仍居中 ─────────

    @Test
    fun ltrBase_center_dotAnchorLeftSlot_textCentered() {
        val textSize = 48f
        val mL = 60f
        val pages = layoutPages(
            shortLtrMixed, false, 0f, CssTextAlign.CssTextAlignCenter, mL, 0f, 1000, textSize, 40f
        )
        assertEquals("baseLtr+Center 应单行", 1, lineCount(pages))
        val expected = ChapterProvider.paddingHorizontal + mL + TextLayoutProvider.inkPad(textSize)
        val ax = dotAnchor(pages)
        assertTrue(
            "baseLtr+Center anchor=$ax 应≈左槽位 expected=$expected",
            kotlin.math.abs(ax - expected) <= 0.5f
        )
        val rawStart = ChapterProvider.paddingHorizontal + mL
        val rawEnd = ChapterProvider.visibleRight.toFloat()
        val ink = inkOf(pages)
        val inkCenter = (ink.first + ink.second) / 2f
        val boxCenter = (rawStart + rawEnd) / 2f
        assertTrue(
            "baseLtr+Center 墨迹中心 $inkCenter 应≈内容盒中心 $boxCenter",
            kotlin.math.abs(inkCenter - boxCenter) <= 3f
        )
        // 证明确实发生了居中：与同文本 Left 对齐基线自比较（防不同设备字体宽度差异），
        // 居中行墨迹左缘应比左对齐行右移明显一截；若未真正居中两者将几乎重合
        val leftBase = layoutPages(
            shortLtrMixed, false, 0f, CssTextAlign.CssTextAlignLeft, mL, 0f, 1000, textSize, 40f
        )
        val leftInk = inkOf(leftBase).first
        assertTrue(
            "baseLtr+Center 墨迹左缘 ${ink.first} 应明显大于 Left 基线 $leftInk（否则未真正居中）",
            ink.first > leftInk + 30f
        )
        println("[PASS] T7 anchor=$ax inkCenter=$inkCenter boxCenter=$boxCenter")
    }
}
