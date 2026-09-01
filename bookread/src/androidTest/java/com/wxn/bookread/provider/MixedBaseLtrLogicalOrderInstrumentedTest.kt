package com.wxn.bookread.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextLine
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
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
 *  - 断言5：C4 后重锚行恢复 justify（右缘粗检 + distributeWords 分布签名细检）；
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
        // C3/C4 集成钉：发生过粘合段重锚 → spanReordered（重锚标记；C4 起 justify 不再据此跳过）
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

    // ── 断言5：C4 后行为——重锚中间行恢复 justify ──
    // 口径说明（实施期实证修正，详见 docs/tests/2026-09-01-test-c4-automated.md）：
    //  - assembly 中间行被引擎打包到容量级满行，分布余量常为 ε 级 ⇒ 右缘两级 oracle 只能粗检；
    //  - post-hoc 重算 resolveJustifyPlan 无证据力（分布后余量恒 ≈0，循环论证）；
    //  - C4 硬证据 = distributeWords 分布签名：组间 gap 全等（游标恒等推进，1e-4 级全等）
    //    + 首组左缘/末组右缘与 effStart/effEnd 精确贴合（分布恒等式）。摘守卫前该行未被
    //    分布（右缘短缺 + gap 自然抖动）⇒ 此断言必红。
    @Test
    fun justify_reorderedMiddleLineStretched_rightEdgeFilled() {
        // fixture 设计：粘合段（AR+数字）后接长中文尾，使含数字的重锚行以中文收尾
        // （非空格收尾 ⇒ 行尾不被空格补丁吸收，分布签名可观测）。
        val ar = "وصل الجيش الأول 300 فارس إلى المدينة وتحرك الجيش الثاني 500 فارس نحو الشرق "
        val text = "这是一段足够长的中文前缀用来占据第一行的绝大部分宽度并且确保折行发生在中文内部为两端对齐场景铺垫出足够的行数 " +
                ar +
                "而这段足够长的中文后缀将确保粘合段所在的行以中文收尾并且折行发生在中文内部从而留下真实的拉伸余量而不是被行尾空格补丁完全吸收掉这样重锚行就能与普通行同权进入两端对齐的分布流程"
        val lines = linesOf(chapter(listOf(para(text, CssTextAlign.CssTextAlignJustify))))

        assertTrue("应 >=4 行（首尾行 justify 退化，C4 只影响中间行）: ${lines.size}", lines.size >= 4)
        // 全部重锚中间行（spanReordered 由 reorderGluedSpans 置位 ⇒ 粘合段形态）
        val reordMiddles = lines.withIndex()
            .filter { (i, l) -> i in 1 until lines.lastIndex && l.spanReordered }
            .map { (_, l) -> l }
        if (reordMiddles.isEmpty()) {
            val dbg = lines.withIndex().joinToString("\n") { (i, l) ->
                "line$i span=${l.spanReordered} text=${l.text.take(24)}"
            }
            throw AssertionError("应存在 spanReordered 的中间行\n$dbg")
        }
        // fixture 意图钉：重锚行确为含数字混排形态（[AR][数字][AR] 邻接块）
        assertTrue(
            "重锚行应含 300/500 混排形态",
            reordMiddles.any { it.text.contains("300") || it.text.contains("500") }
        )

        val textSize = ChapterProvider.contentPaint.textSize
        val ink = TextLayoutProvider.inkPad(textSize)
        val effStart = ChapterProvider.paddingHorizontal + ink
        val effEnd = ChapterProvider.visibleRight - ink

        // ── 粗检（LtrJustifyFill 口径）：每个重锚中间行右缘到位 ──
        reordMiddles.forEachIndexed { li, target ->
            val inkRight = target.textChars.filter {
                !it.isImage && it.charData.firstOrNull()?.isWhitespace() != true
            }.maxOf { it.end }
            var tail = 0f
            for (i in target.textChars.indices.reversed()) {
                val ch = target.textChars[i]
                if (ch.isImage || ch.charData.firstOrNull()?.isWhitespace() != true) break
                tail += ch.end - ch.start
            }
            val gap = effEnd - inkRight
            // 分布行：墨迹右缘=effEnd−尾空白±2px；ε 余量行/边际行：缺口 ≤1em 同样视为到位
            assertTrue(
                "重锚行[$li] 右缘未到位: inkRight=$inkRight effEnd=$effEnd tail=$tail gap=$gap",
                gap <= tail + 2f || gap <= textSize
            )
        }

        // ── 细检（C4 分布签名）：至少一行被 distributeWords 实际分布 ──
        val distributed = reordMiddles.any { l ->
            val boxes = l.textChars.filter { !it.isImage }
                .groupBy { it.renderGroup }
                .values.map { g -> g.minOf { it.start } to g.maxOf { it.end } }
                .sortedBy { it.first }
            boxes.size >= 2 &&
                    (0 until boxes.size - 1)
                        .map { boxes[it + 1].first - boxes[it].second }
                        .let { it.maxOf { g -> g } - it.minOf { g -> g } <= 0.01f } &&
                    abs(boxes.first().first - effStart) <= 0.01f &&
                    abs(boxes.last().second - effEnd) <= 0.01f
        }
        assertTrue(
            "至少一行重锚中间行应带 distributeWords 分布签名" +
                    "（gap 全等 + 盒缘 [${effStart},${effEnd}] 精确贴合）；" +
                    "未命中则 C4 未生效或引擎分布行为变更，须排查",
            distributed
        )
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

    // ── N-Q1 段落级防御钉（plan nq1-nq2 Phase 1 改动点 1-3 契约，审查 R20）：
    //    混排段（LTR 基调含 RTL run）与 RTL 基调段的全部行携带 letterSpacingZeroed=true，
    //    渲染侧据此镜像置零——标志丢失即静默复发 N-Q1 布局/渲染分叉。
    @Test
    fun letterSpacingZeroedFlag_mixedAndRtlParagraphs_pinnedTrue() {
        // LTR 基调混排段（含 RTL run）：谓词 true → 全部行盖章
        val mixed = linesOf(chapter(listOf(para("如下——وصل الجيش الأول 300 فارس إلى"))))
        assertTrue("混排段应成行", mixed.isNotEmpty())
        assertTrue(
            "LTR 基调混排段全部行应携带置零标志（渲染镜像依据）",
            mixed.all { it.letterSpacingZeroed }
        )

        // RTL 基调段：baseRtl=true → 同样盖章
        val rtl = linesOf(chapter(listOf(para("نص عربي قصير مع كلمات لتغطية أكثر من سطر"))))
        assertTrue("RTL 基调段全部行应携带置零标志", rtl.all { it.letterSpacingZeroed })
    }
}
