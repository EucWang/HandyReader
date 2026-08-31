package com.wxn.bookread.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * D+ 集成回归：首强 LTR 基调混排段（U7 形态）——逻辑序消费 + 行关闭粘合段重锚
 * （docs/plans/2026-08-31-plan-u5-route-dplus-glued-span-line-assembly.md §W4-b）。
 *
 * 走真实引擎（ChapterProvider.getTextChapter → TextLayoutProvider.layoutNormalTextRtl，
 * 含 SheenBidi JNI 分段），断言序与结构、不依赖具体断点位置（对字体度量漂移鲁棒）：
 *  - 断言1：跨行阅读序 = 逻辑连续切片（各行 charStartOffset 区间首尾相接，并集 = [0, len)）；
 *  - 断言2：行文本拼接 = 逻辑全文（textLine.text 逻辑序 → TTS/复制/搜索对位同源修复钉）；
 *  - 断言3：粘合段行内视觉序 = 平台行级 L2（renderGroup 分组按 min(start) 升序，
 *           RTL 词的逻辑首字符视觉在词盒最右，不可用「首字符坐标」判序）；
 *  - 断言4：段尾 [AR尾][Eng] 无镜像回归钉；
 *  - 断言5：C4 justify 精确守卫——重锚行不被拉伸；
 *  - 断言6'：RTL 基调零回归 inline 钉（assembly 不启用；完整回归由既有 RTL 套件承担）。
 *
 * 运行（需连接设备，Windows 原生终端）:
 *   gradlew :bookread:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.wxn.bookread.provider.MixedBaseLtrLogicalOrderInstrumentedTest
 */
@RunWith(AndroidJUnit4::class)
class MixedBaseLtrLogicalOrderInstrumentedTest {

    @Before
    fun setUp() {
        ChapterProvider.apply {
            viewWidth = 2400
            viewHeight = 4000
            paddingHorizontal = 40
            paddingVertical = 40
            visibleWidth = viewWidth - paddingHorizontal * 2      // 2320
            visibleHeight = viewHeight - paddingVertical * 2
            visibleRight = paddingHorizontal + visibleWidth
            visibleBottom = paddingVertical + visibleHeight
            lineSpacingExtra = 1.2f
            dualColumnEnabled = false
        }
        ChapterProvider.contentPaint.textSize = 48f
        ChapterProvider.titlePaint.textSize = 80f
    }

    private fun chapter(contents: List<ReaderText>) = runBlocking {
        ChapterProvider.getTextChapter(
            chapter = BookChapter(bookId = 1L, chapterIndex = 0, chapterName = "t"),
            contents = contents,
            chapterSize = 1
        )!!
    }

    private fun para(text: String, align: CssTextAlign = CssTextAlign.CssTextAlignUndefined) =
        ReaderText.Text(text).apply {
            segDirect = RTLSegmenter.segment(line)
            textCssInfo.textAlign = align
        }

    private fun linesOf(ch: TextChapter): List<TextLine> =
        ch.pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }

    private fun isArabic(s: String) =
        s.isNotEmpty() && Character.getDirectionality(s[0]) ==
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC

    private fun isLatin(s: String) = s.isNotEmpty() && s[0] in 'A'..'z'

    /** 组间最大 gap（renderGroup 分组按 min(start) 排序后相邻组左缘 - 前组右缘） */
    private fun lineMaxGap(l: TextLine): Float {
        val groups = l.textChars.filter { !it.isImage }
            .groupBy { it.renderGroup }
            .values.map { g -> g.minOf { it.start } to g.maxOf { it.end } }
            .sortedBy { it.first }
        return (0 until groups.size - 1).maxOf { groups[it + 1].first - groups[it].second }
    }

    // ── 断言3 主形态（§4.4 演算）：单行粘合段 [如下——][AR1][300][AR2] ──
    @Test
    fun singleLine_gluedSpan_visualWordOrder_matchesPlatformTruth() {
        val text = "如下——وصل الجيش الأول 300 فارس إلى"
        val lines = linesOf(chapter(listOf(para(text))))

        assertEquals("宽度足够应单行", 1, lines.size)
        val line = lines.first()
        // 断言2 单行形态：行文本 = 逻辑全文
        assertEquals(text, line.text)
        assertEquals(0, line.charStartOffset)
        assertEquals(text.length, line.charEndOffset)
        // C3/C4 集成钉：发生过粘合段重锚 → spanReordered（justify 守卫的唯一开关）
        assertTrue("粘合段行应标记 spanReordered", line.spanReordered)

        // 断言3（R4 口径）：组序列按组盒 min(start) 升序 = 平台视觉词序
        //（§4.4：[如下——][إلى][فارس][300][الأول][الجيش][وصل]，diag-1748 真值同构）。
        // 过滤纯空白组：run 边界处的空格自成 renderGroup（无词义），词序断言只看词组
        val words = line.textChars.filter { !it.isImage }
            .groupBy { it.renderGroup }
            .values.map { g -> g.minOf { it.start } to g.joinToString("") { it.charData }.trim() }
            .sortedBy { it.first }
            .map { it.second }
            .filter { it.isNotEmpty() }
        assertEquals(
            listOf("如下——", "إلى", "فارس", "300", "الأول", "الجيش", "وصل"),
            words
        )
    }

    // ── 断言1/2/4：跨行阅读序 + 逻辑全文拼接 + 段尾镜像回归钉 ──
    @Test
    fun multiLine_readingOrder_textConcat_tailMirrorPin() {
        val ar = "وصل الجيش الأول والمدينة الكبيرة وفارس الشرق العالي ونهر الفرات الواسع إلى المعسكر الرئيسي"
        val text = "这是一段足够长的中文前缀用来占据第一行的绝大部分宽度并且确保折行发生在中文内部这样阿语内容就会从第二行开始进入排版流程 " +
                ar + " End Tail"
        val lines = linesOf(chapter(listOf(para(text))))

        assertTrue("应多行: ${lines.size}", lines.size >= 3)

        // 断言1：跨行阅读序 = 逻辑连续切片（首尾相接，并集 = [0, len)）
        var expect = 0
        for (l in lines) {
            assertEquals("行起点应首尾相接", expect, l.charStartOffset)
            expect = l.charEndOffset
        }
        assertEquals(text.length, expect)

        // 断言2：行文本拼接 = 逻辑全文（视觉序消费时代的错序拼接已修复）
        assertEquals(text, lines.joinToString("") { it.text })

        // 断言4：段尾镜像回归钉——尾行 Eng 块在 AR 尾块右侧（platform truth [AR尾][Eng]）
        val last = lines.last()
        val arChars = last.textChars.filter { !it.isImage && isArabic(it.charData) }
        val latChars = last.textChars.filter { !it.isImage && isLatin(it.charData) }
        assertTrue("尾行应含阿语字符", arChars.isNotEmpty())
        assertTrue("尾行应含拉丁字符", latChars.isNotEmpty())
        assertTrue(
            "Eng 块应在 AR 尾块右侧（无段尾镜像）: eng=${latChars.minOf { it.start }} ar=${arChars.maxOf { it.end }}",
            latChars.minOf { it.start } > arChars.maxOf { it.end }
        )
    }

    // ── 断言5：C4 justify 精确守卫——重锚中间行不被拉伸 ──
    @Test
    fun justify_reorderedMiddleLineSkipped_notStretched() {
        val ar = "وصل الجيش الأول 300 فارس إلى المدينة في صباح يوم الخميس وتحرك الجيش الثاني 500 فارس نحو الشرق عبر الجبال العالية حتى بلغوا النهر الكبير عند الغروب ثم عادوا إلى المعسكر الرئيسي في المساء. وفي روايات أخرى من كتب التاريخ القديمة أن المعركة الحاسمة وقعت عند الفجر من اليوم التالي بعد أن استعدت القوات المدد اللازمة ونظمت صفوفها من جديد على ضفاف النهر الشرقية حيث خيم الجيش لحين وصول العون المنتظر من السلطان."
        val text = "这是一段足够长的中文前缀用来占据第一行的绝大部分宽度并且确保折行发生在中文内部为两端对齐场景铺垫出足够的行数 " + ar
        val lines = linesOf(chapter(listOf(para(text, CssTextAlign.CssTextAlignJustify))))

        assertTrue("应 >=4 行（首尾行 justify 退化，守卫只作用于中间行）: ${lines.size}", lines.size >= 4)
        // 含数字的混排行 = [AR][数字][AR] 邻接块 → 必重锚
        val target = lines.firstOrNull {
            (it.text.contains("300") || it.text.contains("500")) && it.spanReordered
        } ?: run {
            val dbg = lines.withIndex().joinToString("\n") { (i, l) ->
                "line$i span=${l.spanReordered} text=${l.text.take(24)}"
            }
            throw AssertionError("应存在 spanReordered 的含数字中间行\n$dbg")
        }
        // 守卫生效：不在首/尾退化区
        val idx = lines.indexOf(target)
        assertTrue("目标行应为中间行: idx=$idx", idx in 1 until lines.lastIndex)
        // 重锚行 justify 被跳过 → 组间 gap 保持自然空格宽（~12-16px @48f）；
        // 若守卫失效被拉伸，gap ≈ (effWidth-content)/n ≈ 150px+
        val maxGap = lineMaxGap(target)
        assertTrue("重锚行不应被 justify 拉伸: maxGap=$maxGap", maxGap < 60f)
    }

    // ── 断言6'：RTL 基调零回归 inline 钉（不启用重锚）──
    @Test
    fun rtlParagraph_zeroRegressionPin() {
        val lines = linesOf(chapter(listOf(para("نص عربي قصير مع كلمات لتغطية أكثر من سطر"))))
        assertTrue(lines.isNotEmpty())
        assertTrue("RTL 段行方向应为 RTL", lines.first().isRtl)
        assertFalse(
            "RTL 基调段不启用重锚（assembly=null，零回归红线）",
            lines.any { it.spanReordered }
        )
    }
}
