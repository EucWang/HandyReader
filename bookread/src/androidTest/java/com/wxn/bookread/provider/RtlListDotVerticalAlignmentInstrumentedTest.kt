package com.wxn.bookread.provider

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.LineDot
import com.wxn.bookread.data.model.ListDotShape
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.ui.ListDotRenderer
import com.wxn.bookread.ui.RenderResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 列表圆点垂直对齐回归（plan §6.2 T1-T5，
 * docs/plans/2026-08-26-plan-fix-list-dot-vertical-alignment.md）。
 *
 * 缺陷：绘制层圆点垂直中心取行盒几何中心 (lineTop+lineBottom)/2，而行距全部
 *   堆在基线上方（lineBase = lineBottom − descent），行盒中心随行距系数 S
 *   系统性高于字符带中心 (S−1)·(A−D)/2 → 圆点"偏上"。
 * 修复：ListDotRenderer.centerY = lineBase + (ascent+descent)/2（AOSP BulletSpan
 *   语义），与 S 无关。
 *
 * 断言口径：harness 复用 RtlListDotAnchorUniformInstrumentedTest
 *   （configure + layoutNormalTextRtl + visibleLines），configure 增加行距参数。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.RtlListDotVerticalAlignmentInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class RtlListDotVerticalAlignmentInstrumentedTest {

    private val shortPlain = "عنصر عادي قصير"                       // RTL 基调
    private val shortLtrMixed = "A short LTR line with كلمة mixed 42"  // 首强字符 'A' → 基调 LTR

    private fun configure(width: Int, padding: Int, spacing: Float) {
        ChapterProvider.apply {
            paddingHorizontal = padding
            paddingVertical = padding
            visibleWidth = width
            visibleHeight = 20000
            visibleRight = paddingHorizontal + visibleWidth
            visibleBottom = paddingVertical + visibleHeight
            lineSpacingExtra = spacing
            dualColumnEnabled = false
            columnGapActual = 0
            columnWidth = 0
        }
    }

    private fun layoutPages(
        text: String, baseRtl: Boolean, spacing: Float, textSize: Float
    ): ArrayList<TextPage> {
        configure(1000, 40, spacing)
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
            marginLeft = 0f,
            marginRight = 60f,
            firstLineIndent = 0f,
            isTitle = false,
            isListRow = true,
            listLevel = 1,
            paragraphIndex = 0,
            textAlign = CssTextAlign.CssTextAlignUndefined,
            lineHeightParam = 1f,
            paragraph = ReaderText.Text(text),
            textPages = textPages,
            pageLines = arrayListOf(),
            pageLengths = arrayListOf(),
            stringBuilder = StringBuilder(),
            offsetY = 40f,
            bounds = layoutBoundsPage(),
            chapterIsRtl = baseRtl,
            hasInlineImage = false
        )
        return textPages
    }

    private fun testPaint(textSize: Float): TextPaint = TextPaint().apply {
        color = Color.BLACK
        this.textSize = textSize
        isAntiAlias = true
    }

    /** 首个带圆点的可见行（列表首行） */
    private fun dotLine(pages: List<TextPage>): com.wxn.bookread.data.model.TextLine {
        val line = pages.flatMap { it.textLines }
            .filter { it.textChars.isNotEmpty() }
            .firstOrNull { it.lineDot?.enable == true && (it.lineDot?.level ?: 0) > 0 }
        assertNotNull("列表首行 lineDot 未设置", line)
        return line!!
    }

    // ───────── T1：字符带内 + 公式接线 ─────────

    @Test
    fun t1_dotCenter_insideBand_andMatchesFormula() {
        for (textSize in floatArrayOf(48f, 64f)) {
            val pages = layoutPages(shortPlain, true, spacing = 1.2f, textSize = textSize)
            val line = dotLine(pages)
            val paint = testPaint(textSize)
            val fm = paint.fontMetrics

            val cy = ListDotRenderer.centerY(line, paint)
            assertTrue(
                "ts=$textSize cy=$cy 应在字符带内 (${line.lineBase + fm.ascent}, ${line.lineBase + fm.descent})",
                cy > line.lineBase + fm.ascent && cy < line.lineBase + fm.descent
            )
            assertEquals(
                "ts=$textSize 公式接线",
                line.lineBase + (fm.ascent + fm.descent) / 2f, cy, 0.001f
            )
            println("[PASS] T1 ts=$textSize lineBase=${line.lineBase} cy=$cy bandOffset=${cy - line.lineBase}")
        }
    }

    // ───────── T2：行距不变性 + 旧行为漂移复现（红灯用例） ─────────

    @Test
    fun t2_spacingInvariance_oldFormulaDrifts() {
        val textSize = 48f
        val paint = testPaint(textSize)

        data class Metrics(val newOffset: Float, val oldOffset: Float)

        fun metrics(spacing: Float): Metrics {
            val line = dotLine(layoutPages(shortPlain, true, spacing, textSize))
            val newCy = ListDotRenderer.centerY(line, paint)
            val oldCy = (line.lineTop + line.lineBottom) / 2f
            return Metrics(newCy - line.lineBase, oldCy - line.lineBase)
        }

        val m12 = metrics(1.2f)
        val m16 = metrics(1.6f)

        // 新公式：相对基线偏移与行距无关
        assertEquals("新公式偏移应与行距无关", m12.newOffset, m16.newOffset, 0.001f)
        // 旧公式：行盒中心相对基线随行距增大而升得更高（根因复现；offset 为负=高于基线，取幅值比较），
        // 且在 S=1.2 已高于字符带中心。实测线性 oldOffset = −S·(A−D)/2。
        assertTrue(
            "旧公式应随行距上浮: |old(1.6)|=${kotlin.math.abs(m16.oldOffset)} 应 > |old(1.2)|=${kotlin.math.abs(m12.oldOffset)}",
            kotlin.math.abs(m16.oldOffset) > kotlin.math.abs(m12.oldOffset)
        )
        assertTrue(
            "旧公式 S=1.2 已偏上: |old|=${kotlin.math.abs(m12.oldOffset)} 应 > |new|=${kotlin.math.abs(m12.newOffset)}",
            kotlin.math.abs(m12.oldOffset) > kotlin.math.abs(m12.newOffset)
        )
        println("[PASS] T2 new(1.2)=${m12.newOffset} new(1.6)=${m16.newOffset}; old(1.2)=${m12.oldOffset} old(1.6)=${m16.oldOffset}")
    }

    // ───────── T3：S=1 等价性（最小行距零视觉变化） ─────────

    @Test
    fun t3_spacingOne_newEqualsOldPixelwise() {
        val textSize = 48f
        val paint = testPaint(textSize)
        val line = dotLine(layoutPages(shortPlain, true, 1.0f, textSize))

        val newCy = ListDotRenderer.centerY(line, paint)
        val oldCy = (line.lineTop + line.lineBottom) / 2f
        // 代数上 S=1 时两式相等；实测容差覆盖 fallback 字体度量噪声：
        // 阿拉伯文行 StaticLayout 度量 ≠ 基准 paint fm，实测差 0.288px@48px(0.006em)
        assertEquals("S=1 时新旧公式应一致（亚像素级）", oldCy, newCy, 0.5f)
        println("[PASS] T3 oldCy=$oldCy newCy=$newCy")
    }

    // ───────── T4：方向同构（RTL / LTR 基调同偏移） ─────────

    @Test
    fun t4_directionInvariant() {
        val textSize = 48f
        val paint = testPaint(textSize)

        val rtl = dotLine(layoutPages(shortPlain, true, 1.2f, textSize))
        val ltr = dotLine(layoutPages(shortLtrMixed, false, 1.2f, textSize))

        val offRtl = ListDotRenderer.centerY(rtl, paint) - rtl.lineBase
        val offLtr = ListDotRenderer.centerY(ltr, paint) - ltr.lineBase
        assertEquals("公式与方向无关", offRtl, offLtr, 0.001f)
        println("[PASS] T4 rtl=$offRtl ltr=$offLtr")
    }

    // ───────── T5：draw() 接线（构造式：形状×方向×帧参数×守卫） ─────────

    /** 扫描非透明像素的包围盒 */
    private fun inkRect(bitmap: Bitmap): Rect? {
        var minx = Int.MAX_VALUE; var miny = Int.MAX_VALUE; var maxx = -1; var maxy = -1
        loop@ for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if ((bitmap.getPixel(x, y) ushr 24) != 0) {
                    if (x < minx) minx = x
                    if (x > maxx) maxx = x
                    if (y < miny) miny = y
                    if (y > maxy) maxy = y
                }
            }
        }
        return if (maxx < 0) null else Rect(minx, miny, maxx, maxy)
    }

    private fun constructedLine(level: Int, isRtl: Boolean): TextLine {
        val line = TextLine(isTitle = false, paragraphIndex = 0, isRtl = isRtl)
        line.lineTop = 100f
        line.lineBottom = 140f
        line.lineBase = 130f
        line.textChars.add(TextChar("A", 150f, 160f))
        // anchorX 默认 NaN → 走文字边缘回退；C-3 契约迁移：标记方向 = dot.markerRtl
        // （plan-rtl-mixed-list-marker-hardening §3：绘制期禁止用 line.isRtl 推断方向）
        line.lineDot = LineDot(enable = true, level = level, markerRtl = isRtl)
        return line
    }

    @Test
    fun t5_draw_wiring_shapesDirectionsFrameAndGuards() {
        // draw 的 band/尺寸取 contentPaint —— 测试与断言同源
        ChapterProvider.contentPaint.textSize = 48f
        ChapterProvider.contentPaint.isAntiAlias = true
        RenderResources.listDotStrokePaint.color = Color.BLACK   // L2=空心圆：描边画笔需可见
        val fm = ChapterProvider.contentPaint.fontMetrics
        val expectedDotY = 130f + (fm.ascent + fm.descent) / 2f
        val offset = ListDotRenderer.dotOffsetPx(48f)     // 21.6（0.45em，实现内聚取值）

        // 规范映射三输出全覆盖：1=DISC、2=CIRCLE_HOLLOW、3=SQUARE（封顶）
        for (level in intArrayOf(1, 2, 3)) {
            for (isRtl in booleanArrayOf(false, true)) {
                val bmp = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
                ListDotRenderer.draw(Canvas(bmp), constructedLine(level, isRtl))
                val rect = inkRect(bmp)
                assertNotNull("level=$level rtl=$isRtl 应有墨迹", rect)

                val expectedCx = if (isRtl) 160f + offset else 150f - offset  // NaN 回退：文字边缘 ±offset
                val cx = (rect!!.left + rect.right) / 2f
                val cy = (rect.top + rect.bottom) / 2f
                assertTrue(
                    "level=$level rtl=$isRtl cx=$cx 应≈$expectedCx rect=$rect",
                    kotlin.math.abs(cx - expectedCx) <= 2f
                )
                assertTrue("level=$level rtl=$isRtl cy=$cy 应≈$expectedDotY", kotlin.math.abs(cy - expectedDotY) <= 2f)

                // 期望统一由形状几何派生：DISC d=2r、HOLLOW 外径 d=2r、SQUARE 边=2×半宽（抗锯齿 ±3）
                val shape = ListDotShape.shapeForLevel(level)
                val expected = 2f * ListDotRenderer.shapeHalfWidthPx(shape, 48f)
                val size = maxOf(rect.width(), rect.height())
                assertTrue("level=$level rtl=$isRtl ink=$size 应≈$expected", kotlin.math.abs(size - expected) <= 3)
            }
        }

        // 显式 lineBaseY 帧参数：整体平移
        val bmp2 = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        ListDotRenderer.draw(Canvas(bmp2), constructedLine(1, false), lineBaseY = 150f)
        val rect2 = inkRect(bmp2)!!
        val cy2 = (rect2.top + rect2.bottom) / 2f
        assertTrue("lineBaseY=150 时 cy=$cy2 应≈${expectedDotY + 20f}", kotlin.math.abs(cy2 - (expectedDotY + 20f)) <= 2f)

        // 守卫早退：无 dot / 未启用 / level=0 → 全透明
        val guards = arrayOf(
            constructedLine(1, false).apply { lineDot = null },
            constructedLine(1, false).apply { lineDot = LineDot(enable = false, level = 1) },
            constructedLine(1, false).apply { lineDot = LineDot(enable = true, level = 0) }
        )
        for ((i, g) in guards.withIndex()) {
            val bmp = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
            ListDotRenderer.draw(Canvas(bmp), g)
            assertNull("守卫#$i 应无墨迹", inkRect(bmp))
        }
        println("[PASS] T5 shapes/directions/frame/guards expectedDotY=$expectedDotY")
    }

    // ───────── T6a：生产路径空心圆（level=2 规范映射直出） ─────────

    @Test
    fun t6a_hollowCircle_productionPath() {
        ChapterProvider.contentPaint.textSize = 48f
        RenderResources.listDotStrokePaint.color = Color.BLACK
        val r = ListDotRenderer.dotRadiusPx(48f)          // 7.68
        val s = ListDotRenderer.strokePx(48f)                // 1.92

        for (isRtl in booleanArrayOf(false, true)) {
            val bmp = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
            ListDotRenderer.draw(Canvas(bmp), constructedLine(2, isRtl))
            val rect = inkRect(bmp)
            assertNotNull("L2 rtl=$isRtl 空心圆应有墨迹", rect)

            val cx = ((rect!!.left + rect.right) / 2f).toInt()
            val cy = ((rect.top + rect.bottom) / 2f).toInt()
            // 外径 = 2r（绘制半径 r−s/2，墨迹外缘 r；抗锯齿 ±3）
            assertEquals(
                "L2 rtl=$isRtl 外径应≈2r",
                2f * r, maxOf(rect.width(), rect.height()).toFloat(), 3f
            )
            // 中心全透明（孔径 2(r−s)=11.52px，圆心远离墨迹）
            assertEquals("L2 rtl=$isRtl 中心应全透明", 0, bmp.getPixel(cx, cy) ushr 24)
            // 环上有墨（描边带上缘，半径 r−s/2 处）
            val ringY = (cy - (r - s / 2f)).toInt()
            assertTrue("L2 rtl=$isRtl 环上应有墨迹", (bmp.getPixel(cx, ringY) ushr 24) != 0)
        }
        println("[PASS] T6a hollow production path r=$r s=$s")
    }

    // ───────── T6b：注入路径（DASH 无 level 映射 + shape 覆盖 level 推导） ─────────

    @Test
    fun t6b_injection_dashAndShapeOverride() {
        ChapterProvider.contentPaint.textSize = 48f
        RenderResources.listDotPaint.color = Color.BLACK
        RenderResources.listDotStrokePaint.color = Color.BLACK

        // DASH 注入：细长条 24×3.84（@48px）
        val dash = constructedLine(1, false).apply {
            lineDot = LineDot(enable = true, level = 1, shape = ListDotShape.DASH)
        }
        val bmp = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        ListDotRenderer.draw(Canvas(bmp), dash)
        val rect = inkRect(bmp)
        assertNotNull("DASH 应有墨迹", rect)
        assertTrue("DASH w=${rect!!.width()} 应≈24", kotlin.math.abs(rect.width() - 24) <= 3)
        assertTrue("DASH h=${rect.height()} 应≈3.84", kotlin.math.abs(rect.height() - 3.84f) <= 3)

        // override 优先级：level=1 本应推导 DISC（实心），注入 CIRCLE_HOLLOW → 中心必须透明。
        // 若 dot.shape ?: 推导优先级失效走实心圆，中心必有墨 → 断言失败。
        val hollow = constructedLine(1, false).apply {
            lineDot = LineDot(enable = true, level = 1, shape = ListDotShape.CIRCLE_HOLLOW)
        }
        val bmp2 = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        ListDotRenderer.draw(Canvas(bmp2), hollow)
        val rect2 = inkRect(bmp2)
        assertNotNull("override 空心圆应有墨迹", rect2)
        val cx = ((rect2!!.left + rect2.right) / 2f).toInt()
        val cy = ((rect2.top + rect2.bottom) / 2f).toInt()
        assertEquals(
            "override 后中心应透明（证明 dot.shape 优先于 level 推导）",
            0, bmp2.getPixel(cx, cy) ushr 24
        )
        println("[PASS] T6b dash=${rect.width()}x${rect.height()} override=center-transparent")
    }

    // ───────── T7：序号点号列共线 / 不压字（外伸放置，plan-rtl-ordered-marker-outstretched） ─────────

    @Test
    fun t7_orderedLabel_dotColumnColinear_noOverlap() {
        ChapterProvider.contentPaint.textSize = 48f
        RenderResources.listMarkerPaint.textSize = 48f      // 测试独立设定，不依赖 :644 同步链
        RenderResources.listMarkerPaint.color = Color.BLACK
        val offset = ListDotRenderer.dotOffsetPx(48f)       // drawOrderedLabel 以 paint.textSize 取 offset
        val paint = RenderResources.listMarkerPaint

        // 墨迹对墨迹的同源断言：drawText 按 advance 定位，句点的 advance 右缘 ≠ 墨迹右缘
        //（差 = trailing bearing，字体相关，实测 Roboto ≈4.4px）——期望必须用 getTextBounds 推算
        fun expectedInkRight(label: String, drawX: Float): Float {
            val b = Rect()
            paint.getTextBounds(label, 0, label.length, b)
            return drawX + b.right
        }

        // LTR：order=1 与 order=12，右缘都 = 150 − offset − advance + 墨迹右缘（个位对齐）且 < 150 不压字。
        // 修复前（drawX 漏减 textWidth）右缘 ≈ 150 − 21.6 + ~38 ≈ 166+，必越过 150 → 红灯用例
        // 注：本段「个位对齐」字样按外伸方案 §2 构造等价保留（LTR 点在末位，右缘共线 = 点号列共线）
        val ltrOrders = intArrayOf(1, 12)
        val rights = ltrOrders.map { order ->
            val line = constructedLine(1, false).apply {
                lineDot = LineDot(enable = true, level = 1, order = order)
            }
            val bmp = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
            ListDotRenderer.draw(Canvas(bmp), line)
            inkRect(bmp)!!.right.toFloat()
        }
        for ((i, order) in ltrOrders.withIndex()) {
            val label = ListDotRenderer.orderedLabel(order, false)
            // 与实现同源的完整 drawX = anchorX − offset − advance（右对齐），再加墨迹右缘
            val expected = expectedInkRight(label, 150f - offset - paint.measureText(label))
            assertTrue(
                "order#$order right=${rights[i]} 应≈$expected (w=${paint.measureText(label)})",
                kotlin.math.abs(rights[i] - expected) <= 2
            )
            assertTrue("order#$order right=${rights[i]} 应 < 150（不压正文）", rights[i] < 150)
        }
        // 个位对齐：两序号同以 '.' 结尾，advance 对齐 → 墨迹右缘一致（±2）
        assertTrue("个位应对齐（两序号右缘一致 ±2）", kotlin.math.abs(rights[0] - rights[1]) <= 2)

        // RTL：label ".N"（点在左，浏览器 RTL marker 语义），外伸放置 drawX = 160 + offset：
        // 标签整体在正文右缘（=160）外侧，位数向页缘（右）外伸，间隙 ≥ offset。
        // 双序号 {1,12}：墨迹左缘共线 = 点号列单竖线（镜像 LTR 的右缘共线）且 ≥ 160 不压正文。
        val rtlOrders = intArrayOf(1, 12)
        val lefts = rtlOrders.map { order ->
            val line = constructedLine(1, true).apply {
                lineDot = LineDot(enable = true, level = 1, order = order, markerRtl = true)
            }
            val bmp = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
            ListDotRenderer.draw(Canvas(bmp), line)
            inkRect(bmp)!!.left.toFloat()
        }
        for ((i, order) in rtlOrders.withIndex()) {
            val label = ListDotRenderer.orderedLabel(order, true)
            val b = Rect()
            paint.getTextBounds(label, 0, label.length, b)
            // 墨迹左缘 = drawX + 前轴承（两序号首字符同为 '.'，轴承一致 ⇒ 共线）
            val expectedLeft = 160f + offset + b.left
            assertTrue(
                "RTL order#$order left=${lefts[i]} 应≈$expectedLeft",
                kotlin.math.abs(lefts[i] - expectedLeft) <= 2
            )
            assertTrue("RTL order#$order left=${lefts[i]} 应 ≥ 160（不压正文）", lefts[i] >= 160f)
        }
        // 点号列对齐：两序号墨迹左缘一致（±2）
        assertTrue("点号列应共线（两序号左缘一致 ±2）", kotlin.math.abs(lefts[0] - lefts[1]) <= 2)

        // 标签格式单一来源：RTL 点在左
        assertEquals("1.", ListDotRenderer.orderedLabel(1, false))
        assertEquals(".1", ListDotRenderer.orderedLabel(1, true))
        println("[PASS] T7 rights=$rights rtlLefts=$lefts")
    }
}
