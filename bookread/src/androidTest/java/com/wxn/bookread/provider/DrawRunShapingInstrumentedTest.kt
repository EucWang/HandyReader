package com.wxn.bookread.provider

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.TextPaint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.ui.RenderResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

/**
 * E8 绘制层整组整形标志位——仪器化测试（方案 §5.2）。
 *
 * 被测对象是 ShapedRunBuffer 的分流逻辑：
 *  - needsRunShaping==false 的逐字安全字符（CJK/拉丁）逐字 drawText，justify 组内分布可见
 *    （E7 修复的像素级验收：左对齐与两端对齐渲染可区分）；
 *  - needsRunShaping==true 的整组整形字符（阿拉伯语、emoji 污染 run）走原整组路径，零回归。
 *
 * 绘制 harness 约定（review W-5，必须遵守）：逐字符调用
 * `RenderResources.shapedRunBuffer.draw(canvas, ch, baseY, paint, next)`，与生产调用点
 * ContentTextView.kt:768（bookread）、ContinuousScrollReaderView.kt:1302（app 模块）完全同形；
 * 禁止测试自行逐字 canvas.drawText（那会绕过被测代码恒绿）。
 *
 * 无需 RenderResources.init / ChapterProvider.init：shapedRunBuffer 为 object 内即刻初始化的
 * val（init 只影响颜色/尺寸 fallback，draw 的 livePaint 由测试传入）；ChapterProvider 静态
 * 字段直接配置（与 P3 诊断测试同款、真机已验证的 harness）。
 *
 * 像素断言口径说明：Bitmap 墨迹扫描测的是"字形墨迹"，而 effEnd 是"步进坐标"——行末字符
 * （尤其句号。）的墨迹不达其步进右缘，故墨迹级断言采用【同内容左对齐对照】作基线：
 * justify 与 left 渲染的同一行末字符是同一字形，其墨迹右移量 = justify 组内分布总量
 * （= effEnd − left 自然右缘），这一等式对字形形状不敏感，±2px 容差内稳定成立。
 *
 * 运行：`gradlew :bookread:connectedDebugAndroidTest --tests "*DrawRunShapingInstrumentedTest*"`
 */
@RunWith(AndroidJUnit4::class)
class DrawRunShapingInstrumentedTest {

    // 真书原文（道德经第二章，与 E7 验收缺陷同源文本）
    private val para1 =
        "是以圣人之治，虚其心，实其腹，弱其志，强其骨。常使民无知无欲。使夫智者不敢为也。" +
                "为无为，则无不治。道冲，而用之或不盈。渊兮，似万物之宗。"

    private class LayoutResult(val lines: List<TextLine>, val paint: TextPaint)

    private fun configProvider(width: Int) {
        ChapterProvider.apply {
            paddingHorizontal = 40
            paddingVertical = 40
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

    private fun layout(
        text: String,
        indent: Float,
        textSize: Float,
        width: Int,
        align: CssTextAlign
    ): LayoutResult {
        configProvider(width)
        val paint = TextPaint().apply {
            color = Color.BLACK
            this.textSize = textSize
            isAntiAlias = true
        }
        val pages = arrayListOf(TextPage())
        TextLayoutProvider.layoutNormalTextRtl(
            text, null, RTLSegmenter.segment(text), paint,
            marginLeft = 0f, marginRight = 0f, firstLineIndent = indent,
            isTitle = false, isListRow = false, listLevel = 0, paragraphIndex = 0,
            textAlign = align, lineHeightParam = 1f,
            paragraph = ReaderText.Text(text), textPages = pages,
            pageLines = arrayListOf(), pageLengths = arrayListOf(),
            stringBuilder = StringBuilder(), offsetY = 40f,
            bounds = layoutBoundsPage(), chapterIsRtl = false, hasInlineImage = false
        )
        return LayoutResult(pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }, paint)
    }

    /** 逐字符走生产分流逻辑画到位图（harness 核心，禁止绕过） */
    private fun drawToBitmap(result: LayoutResult): Bitmap {
        val width = ChapterProvider.visibleRight + 10
        val height = ceil(result.lines.maxOf { it.lineBottom }).toInt() + 10
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val buffer = RenderResources.shapedRunBuffer
        buffer.clear()
        result.lines.forEach { line ->
            val chars = line.textChars
            chars.forEachIndexed { i, ch ->
                if (ch.isImage) return@forEachIndexed
                buffer.draw(canvas, ch, line.lineBase, result.paint, chars.getOrNull(i + 1))
            }
        }
        return bmp
    }

    /** 行带内墨迹最右像素列（-1 = 无墨迹）；从右向左逐行扫描，R<120 视为墨迹 */
    private fun rightmostInk(bmp: Bitmap, yTop: Int, yBottom: Int): Int {
        var maxInkX = -1
        val yLo = yTop.coerceAtLeast(0)
        val yHi = yBottom.coerceAtMost(bmp.height - 1)
        for (y in yLo..yHi) {
            for (x in bmp.width - 1 downTo maxInkX + 1) {
                if (Color.red(bmp.getPixel(x, y)) < 120) {
                    if (x > maxInkX) maxInkX = x
                    break
                }
            }
        }
        return maxInkX
    }

    private fun nonWsChars(line: TextLine) =
        line.textChars.filter { !it.isImage && it.charData.firstOrNull()?.isWhitespace() != true }

    // ─────────────────────────────────────────────────────────────

    /** 场景 1（核心，E7 像素级验收）：justify 非末行右缘拉满 effEnd，且墨迹相对左对齐右移分布量 */
    @Test
    fun cjk_justify_ink_fills_to_effEnd() {
        val ts = 57f
        val indent = 2f * ts
        val width = 906
        val justify = layout(para1, indent, ts, width, CssTextAlign.CssTextAlignJustify)
        val left = layout(para1, indent, ts, width, CssTextAlign.CssTextAlignLeft)
        val bmp = drawToBitmap(justify)
        val bmpLeft = drawToBitmap(left)

        assertEquals("两套对齐的断行必须一致（justify 只移动 x 坐标）", left.lines.size, justify.lines.size)
        val ink = TextLayoutProvider.inkPad(ts)
        val effEnd = ChapterProvider.visibleRight - ink

        val nonLast = 0 until justify.lines.size - 1
        assertTrue("样例应产生多行段落（单行无法验收 justify）", nonLast.count() >= 2)
        for (i in nonLast) {
            val js = nonWsChars(justify.lines[i])
            val ls = nonWsChars(left.lines[i])
            // 坐标层：justify 后内容右缘 = effEnd（CJK 单组全量分布，见方案 §1）
            assertEquals("L$i 非末行 justify 右缘应拉满 effEnd", effEnd, js.maxOf { it.end }, 0.5f)
            // 像素层：同一行末字形，justify 墨迹右移量 = effEnd − left 自然右缘
            val shortfall = effEnd - ls.maxOf { it.end }
            val yTop = justify.lines[i].lineTop.toInt()
            val yBottom = justify.lines[i].lineBottom.toInt()
            val inkJustify = rightmostInk(bmp, yTop, yBottom)
            val inkLeft = rightmostInk(bmpLeft, yTop, yBottom)
            assertTrue("L$i justify 墨迹必须比左对齐更靠右", inkJustify > inkLeft)
            assertEquals(
                "L$i 墨迹右移量应等于组内分布总量（shortfall=${shortfall}）",
                shortfall, (inkJustify - inkLeft).toFloat(), 2f
            )
        }
    }

    /** 场景 2（E7 症状反向断言）：同一文本左对齐与两端对齐的整幅渲染可区分（修复前逐像素相同） */
    @Test
    fun cjk_left_vs_justify_render_differ() {
        val ts = 57f
        val justify = layout(para1, 2f * ts, ts, 906, CssTextAlign.CssTextAlignJustify)
        val left = layout(para1, 2f * ts, ts, 906, CssTextAlign.CssTextAlignLeft)
        val bmpJustify = drawToBitmap(justify)
        val bmpLeft = drawToBitmap(left)
        assertFalse(
            "E7 缺陷即\"两设置渲染逐像素相同\"——修复后位图必须可区分",
            bmpJustify.sameAs(bmpLeft)
        )
    }

    /** 场景 3：Left 行逐字坐标 = 自然排布（首字在起始边、相邻字无缝衔接），逐字绘制不引入错位 */
    @Test
    fun cjk_left_natural_positions_preserved() {
        val ts = 57f
        val indent = 2f * ts
        val left = layout(para1, indent, ts, 906, CssTextAlign.CssTextAlignLeft)
        val ink = TextLayoutProvider.inkPad(57f)
        left.lines.forEachIndexed { i, line ->
            val cs = nonWsChars(line)
            assertTrue(cs.isNotEmpty())
            // 行盒每行两侧各留 inkPad 墨迹安全内边距（TextLayoutProvider :759 effStart = rawStart + inkSize，
            // 左对齐经 anchorLine 锚定到 effStart），首字步进起点 = 行盒起点 + inkPad
            val rawStart = ChapterProvider.paddingHorizontal + (if (i == 0) indent else 0f)
            assertEquals("L$i 首字应贴行盒起始边（含墨迹内边距）", rawStart + ink, cs.first().start, 0.5f)
            for (k in 1 until cs.size) {
                assertEquals(
                    "L$i 第$k 字应自然衔接前一字右缘（无重叠/间隙）",
                    cs[k - 1].end, cs[k].start, 0.5f
                )
            }
        }
    }

    /** 场景 4：白名单按 run 级分类——CJK/拉丁逐字安全（false），阿拉伯语整组整形（true） */
    @Test
    fun classification_flags_by_script() {
        val mixed = layout("中文abc مرحبا", 0f, 40f, 906, CssTextAlign.CssTextAlignLeft)
        var sawArabic = false
        mixed.lines.flatMap { it.textChars }.forEach { ch ->
            val code = ch.charData.firstOrNull()?.code ?: return@forEach
            if (code in 0x0600..0x06FF) {
                sawArabic = true
                assertTrue("阿拉伯字符应整组整形", ch.needsRunShaping)
            } else if (!ch.charData.first().isWhitespace()) {
                assertFalse("CJK/拉丁字符（0x%04X）应逐字安全".format(code), ch.needsRunShaping)
            }
        }
        assertTrue("样例应包含阿拉伯字符", sawArabic)

        val pureCjk = layout(para1, 0f, 40f, 906, CssTextAlign.CssTextAlignLeft)
        assertTrue(
            "纯 CJK 段全部逐字安全",
            pureCjk.lines.flatMap { it.textChars }.none { it.needsRunShaping }
        )

        val pureArabic = layout("سلام عليكم عليكم", 0f, 40f, 906, CssTextAlign.CssTextAlignLeft)
        val arChars = pureArabic.lines.flatMap { it.textChars }.filter {
            it.charData.firstOrNull()?.code in 0x0600..0x06FF
        }
        assertTrue(arChars.isNotEmpty())
        assertTrue("纯阿拉伯段全部整组整形", arChars.all { it.needsRunShaping })
    }

    /** 场景 5（保守缺省）：emoji 混入 → run 级 OR 退回整组整形，绘制不崩溃、墨迹在行盒内 */
    @Test
    fun emoji_run_falls_back_to_group() {
        val result = layout("中文\uD83D\uDE00测试", 0f, 40f, 906, CssTextAlign.CssTextAlignJustify)
        val chars = result.lines.flatMap { it.textChars }.filter { !it.isImage }
        assertTrue(chars.isNotEmpty())
        assertTrue(
            "emoji 污染整 run：run 内所有字符（含 CJK）都应整组整形",
            chars.all { it.needsRunShaping }
        )
        val bmp = drawToBitmap(result)
        val ink = rightmostInk(bmp, 0, bmp.height - 1)
        assertTrue("绘制应产生墨迹", ink >= 0)
        assertTrue("墨迹不得超出行盒右缘", ink <= ChapterProvider.visibleRight)
    }

    /** 场景 6（零回归红线）：阿拉伯语 justify 走词距分布，连写整组路径照常工作 */
    @Test
    fun arabic_justify_word_level_smoke() {
        val result = layout("سلام عليكم", 0f, 57f, 906, CssTextAlign.CssTextAlignJustify)
        val cs = result.lines.flatMap { it.textChars }.filter { !it.isImage }
        assertTrue(cs.isNotEmpty())
        assertTrue("阿拉伯字符应全部整组整形", cs.all { it.needsRunShaping })
        val groups = cs.map { it.renderGroup }.toSortedSet()
        assertTrue("两词行至少应有 2 个分词组", groups.size >= 2)
        val bmp = drawToBitmap(result)
        assertTrue("绘制应产生墨迹", rightmostInk(bmp, 0, bmp.height - 1) >= 0)
    }
}
