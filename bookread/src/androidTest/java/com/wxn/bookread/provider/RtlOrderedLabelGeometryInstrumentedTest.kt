package com.wxn.bookread.provider

import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.ui.ListDotRenderer
import com.wxn.bookread.ui.RenderResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RTL / 混排段落有序序号几何契约（plan §3 C-5.2，
 * docs/plans/2026-08-27-plan-rtl-mixed-list-marker-hardening.md）。
 *
 * 缺口 G-1：既有 T1-T7（RtlListDotAnchorUniformInstrumentedTest）的 layoutPages 不传 listOrder，
 *   有序序号 ".N" 在 RTL/混合下的几何从未被仪器测试锁定——本套补齐：
 *   R-O1 markerRtl 方向字段（= 排版段落基调；legacy 不写 → 缺省 false）；
 *   R-O2 同预留下序号锚点槽统一（anchorX 共线 + LO-5 公式 ⇒ 点号列共线成单竖线，外伸放置）；
 *   R-O3 序号列预留充分性（真机 measureText，未封顶域）；
 *   R-O4 折行长项仅首行落序号、锚点与单行项同槽；
 *   R-O5 四对齐矩阵锚点不变（Left 输入在 RTL 基调被 effAlign 映射为 Right，
 *        用例名 leftMappedToRight，审查 R-8）；
 *   R-O6 同输入两次独立布局逐字段确定性（不稳定 = 新缺陷信号）。
 *
 * 断言口径：configure(1000, 40) ⇒ bounds = [40, 1040]，右槽位 anchor = 1040 - mR - inkPad(ts)。
 *
 * 运行（MIUI 约束，禁 connectedDebugAndroidTest；Windows 原生终端）:
 *   gradlew :bookread:assembleDebugAndroidTest
 *   adb install -r -t bookread\build\outputs\apk\androidTest\debug\bookread-debug-androidTest.apk
 *   adb shell am instrument -w -e class com.wxn.bookread.provider.RtlOrderedLabelGeometryInstrumentedTest com.wxn.bookread.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class RtlOrderedLabelGeometryInstrumentedTest {

    /** 与 T 系列（RtlListDotAnchorUniformInstrumentedTest）同源文本，保证口径可比 */
    private val shortArabic = "عنصر قصير بمحاذاة مطرافية"
    private val shortLtrMixed = "A short LTR line with كلمة mixed 42"
    private val longRtl =
        "العنصر الثاني طويل جدا ويتوقع أن يلتف على أكثر من سطر واحد للتأكد من أن النقطة تظهر على السطر الأول فقط"

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

    /** T 系列 helper 骨架 + listOrder 形参（G-1 补口；layoutNormalTextRtl:41 形参已存在，产品码零改动） */
    private fun layoutPages(
        text: String, baseRtl: Boolean, indent: Float, align: CssTextAlign,
        mL: Float, mR: Float, width: Int, textSize: Float, offsetY: Float,
        isListRow: Boolean = true, listLevel: Int = 1, listOrder: Int = 0
    ): ArrayList<TextPage> {
        configure(width, 40)
        // 产线同源（ChapterProvider:646，D-2 = 1.0×）：序号画笔字号随正文
        RenderResources.listMarkerPaint.textSize = textSize
        val paint = TextPaint().apply {
            color = Color.BLACK
            this.textSize = textSize
            isAntiAlias = true
        }
        val seg = RTLSegmenter.segment(text)
        val textPages = arrayListOf(TextPage())
        TextLayoutProvider.layoutNormalTextRtl(
            text, null, seg, paint,
            marginLeft = mL, marginRight = mR,
            firstLineIndent = indent,
            isTitle = false,
            isListRow = isListRow,
            listLevel = listLevel,
            listOrder = listOrder,
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

    /** 列表首行 lineDot（含存在性校验） */
    private fun firstDot(pages: List<TextPage>) =
        checkNotNull(visibleLines(pages).firstOrNull()?.lineDot) { "首行 lineDot 未设置" }

    /** 指定行锚点（含 lineDot/anchorX 写入校验） */
    private fun dotAnchor(pages: List<TextPage>, lineIdx: Int = 0): Float {
        val lines = visibleLines(pages)
        assertTrue("行数不足: 需要 index=$lineIdx, 实际 ${lines.size} 行", lines.size > lineIdx)
        val dot = lines[lineIdx].lineDot
        assertTrue("第 $lineIdx 行 lineDot 未设置", dot != null)
        val ax = dot!!.anchorX
        assertTrue("第 $lineIdx 行 anchorX 未写入（NaN）", !ax.isNaN())
        return ax
    }

    // ───────── R-O1：markerRtl = 排版段落基调（显式方向契约核心） ─────────

    @Test
    fun ro1_markerRtl_followsParagraphBaseDirection() {
        // RTL 基调（纯阿拉伯短句，强 R 字符 → runs 覆盖）
        val rtl = layoutPages(shortArabic, true, 0f, CssTextAlign.CssTextAlignJustify,
            0f, 60f, 1000, 48f, 40f, listOrder = 5)
        assertEquals("RTL 基调应恰为单行", 1, lineCount(rtl))
        val dotR = firstDot(rtl)
        assertTrue("RTL 基调 markerRtl 应为 true", dotR.markerRtl)
        assertEquals("order 传递无损", 5, dotR.order)

        // baseLtr 混排（首强字符 'A' → 走 RTL 引擎 baseLtr 分支，锚左槽）
        val ltr = layoutPages(shortLtrMixed, false, 0f, CssTextAlign.CssTextAlignLeft,
            60f, 0f, 1000, 48f, 40f, listOrder = 5)
        assertEquals("LTR 混排应单行", 1, lineCount(ltr))
        val dotL = firstDot(ltr)
        assertFalse("baseLtr 混排 markerRtl 应为 false", dotL.markerRtl)
        assertEquals(5, dotL.order)
        println("[PASS] R-O1 rtlMarkerRtl=${dotR.markerRtl} ltrMarkerRtl=${dotL.markerRtl}")
    }

    // ───────── R-O2：序号槽统一（主断言） ─────────
    // 同 maxOrder 预留（mR=60）下 order=1/5/12 三次独立布局：anchorX 两两相等（±0.5px）
    // 且 = 右槽位 1040 - mR - inkPad(ts)。点号列共线推论：anchor 共线 + LO-5
    // （RTL 左缘 = anchor + offset，与 w 无关）⇒ 点号列共线成单竖线（外伸放置）。

    @Test
    fun ro2_orderAnchorSlot_uniformAcrossOrderMagnitude() {
        val textSize = 48f
        val mR = 60f
        val expected = ChapterProvider.visibleRight - mR - TextLayoutProvider.inkPad(textSize)

        val anchors = intArrayOf(1, 5, 12).map { order ->
            val pages = layoutPages(shortArabic, true, 0f, CssTextAlign.CssTextAlignJustify,
                0f, mR, 1000, textSize, 40f, listOrder = order)
            assertEquals("order=$order 应恰为单行", 1, lineCount(pages))
            dotAnchor(pages)
        }
        anchors.forEach { ax ->
            assertTrue("anchor=$ax 应≈右槽位 expected=$expected",
                kotlin.math.abs(ax - expected) <= 0.5f)
        }
        assertTrue(
            "order=1/5/12 锚点应共线（±0.5px）: $anchors",
            kotlin.math.abs(anchors[0] - anchors[1]) <= 0.5f &&
                kotlin.math.abs(anchors[1] - anchors[2]) <= 0.5f
        )
        println("[PASS] R-O2 expected=$expected anchors=$anchors")
    }

    // ───────── R-O3：序号列预留充分性（真机 measureText，未封顶域） ─────────
    // calcListIndent(1, ts, W, w) ≥ w + dotOffsetPx(ts)：序号列预留可完整容纳
    // 「标签宽 + 锚点偏移」。预留侧放置即外伸放置：标签占 [anchor+offset, anchor+offset+w]，
    // 本不等式保证其完整落在预留区内（间隙 ≥ offset，构造性无压字；fix 文档 §7 几何冲突
    // 已由外伸放置消解，方案 docs/plans/2026-08-27-plan-rtl-ordered-marker-outstretched.md）。

    @Test
    fun ro3_reservation_coversLabelPlusOffset() {
        for (ts in floatArrayOf(48f, 64f)) {
            RenderResources.listMarkerPaint.textSize = ts
            val w = RenderResources.listMarkerPaint.measureText(".12")
            assertTrue("域守卫（R-11）：w=$w 应未触 15% 封顶", w <= 1000f * 0.15f)
            val indent = ListDotRenderer.calcListIndent(1, ts, 1000f, w)
            val required = w + ListDotRenderer.dotOffsetPx(ts)
            assertTrue("ts=$ts w=$w：indent=$indent 应 ≥ 标签宽+偏移 $required", indent >= required)
            println("[PASS] R-O3 ts=$ts w=$w indent=$indent required=$required")
        }
    }

    // ───────── R-O4：折行长项仅首行落序号、锚点同槽 ─────────

    @Test
    fun ro4_wrappedItem_onlyFirstLineCarriesOrder_sameSlot() {
        val textSize = 48f
        val mR = 60f
        val pages = layoutPages(longRtl, true, 0f, CssTextAlign.CssTextAlignJustify,
            0f, mR, 1000, textSize, 40f, listOrder = 7)
        assertTrue("长段必须折行（实际 ${lineCount(pages)} 行）", lineCount(pages) > 1)

        val lines = visibleLines(pages)
        val first = lines[0]
        assertTrue("首行应有 lineDot", first.lineDot != null)
        assertEquals("首行 order 应为 7", 7, first.lineDot!!.order)
        assertTrue("首行 markerRtl 应为 true", first.lineDot!!.markerRtl)
        lines.drop(1).forEach {
            assertNull("折行次行不应有 lineDot", it.lineDot)
        }

        val expected = ChapterProvider.visibleRight - mR - TextLayoutProvider.inkPad(textSize)
        val ax = dotAnchor(pages)
        assertTrue("折行首行 anchor=$ax 应≈右槽位 expected=$expected",
            kotlin.math.abs(ax - expected) <= 0.5f)
        println("[PASS] R-O4 lines=${lines.size} anchor=$ax expected=$expected")
    }

    // ───────── R-O5：四对齐矩阵锚点不变（RTL 基调） ─────────

    @Test
    fun ro5_anchorInvariant_acrossFourAlignments() {
        val textSize = 48f
        val mR = 60f
        val expected = ChapterProvider.visibleRight - mR - TextLayoutProvider.inkPad(textSize)

        val cases = mapOf(
            "Center" to CssTextAlign.CssTextAlignCenter,
            "Justify" to CssTextAlign.CssTextAlignJustify,
            "leftMappedToRight" to CssTextAlign.CssTextAlignLeft,
            "Undefined→Right" to CssTextAlign.CssTextAlignUndefined
        )
        cases.forEach { (name, align) ->
            val pages = layoutPages(shortArabic, true, 0f, align,
                0f, mR, 1000, textSize, 40f, listOrder = 3)
            assertEquals("$name 应恰为单行", 1, lineCount(pages))
            val dot = firstDot(pages)
            assertTrue("$name markerRtl 应恒为 true", dot.markerRtl)
            val ax = dot.anchorX
            assertTrue("$name anchor=$ax 应≈右槽位 expected=$expected",
                kotlin.math.abs(ax - expected) <= 0.5f)
        }
        println("[PASS] R-O5 expected=$expected")
    }

    // ───────── R-O6：重排确定性（同输入两次独立布局逐字段相等） ─────────

    @Test
    fun ro6_relayoutDeterminism_fieldExactEquality() {
        // RTL 基调 order=12
        val a1 = layoutPages(shortArabic, true, 0f, CssTextAlign.CssTextAlignJustify,
            0f, 60f, 1000, 48f, 40f, listOrder = 12)
        val a2 = layoutPages(shortArabic, true, 0f, CssTextAlign.CssTextAlignJustify,
            0f, 60f, 1000, 48f, 40f, listOrder = 12)
        val d1 = firstDot(a1)
        val d2 = firstDot(a2)
        // 浮点逐位相等（方案：同输入同代码路径应确定性——不稳定 = 新缺陷信号，停线报告）
        assertEquals("RTL 两轮 anchorX 应逐位相等", d1.anchorX, d2.anchorX, 0f)
        assertEquals("RTL 两轮 order 相等", d1.order, d2.order)
        assertTrue("RTL 两轮 markerRtl 相等", d1.markerRtl == d2.markerRtl)

        // baseLtr 混排 order=5
        val b1 = layoutPages(shortLtrMixed, false, 0f, CssTextAlign.CssTextAlignLeft,
            60f, 0f, 1000, 48f, 40f, listOrder = 5)
        val b2 = layoutPages(shortLtrMixed, false, 0f, CssTextAlign.CssTextAlignLeft,
            60f, 0f, 1000, 48f, 40f, listOrder = 5)
        val e1 = firstDot(b1)
        val e2 = firstDot(b2)
        assertEquals("LTR 两轮 anchorX 应逐位相等", e1.anchorX, e2.anchorX, 0f)
        assertEquals(5, e2.order)
        assertTrue("LTR 两轮 markerRtl 相等", e1.markerRtl == e2.markerRtl)
        println("[PASS] R-O6 rtlAnchor=${d1.anchorX} ltrAnchor=${e1.anchorX}")
    }
}
