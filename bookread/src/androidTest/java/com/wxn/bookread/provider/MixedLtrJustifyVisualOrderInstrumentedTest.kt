package com.wxn.bookread.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssTextAlign
import com.wxn.base.bean.ReaderText
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C5（U7 镜像修复）集成钉：LTR 基调段阿语行 justify 后 x 序 == bidi 视觉真值。
 * （方案 §5 W5-b；修复前 pureArabicMiddleLines 必红——x 序 = 逻辑序 = 镜像）
 *
 * 真值口径（审查 R2′）：行内全部词均为 RTL（level-1 单块）时 L2 = 词序严格逆序，
 * 期望序列 = 逻辑词序反转；行尾句点为 level-0 独立块，视觉钉在行末（最右）。
 * 不用 ICU 做断言：writeReordered(0) 的 token 切分与引擎 renderGroup 切分在
 * 「句点粘词」上不一致（诊断 dump 实测 C-3 行：ICU 尾 token=الممتدة.，引擎=الممتدة 与 . 两组）。
 */
@RunWith(AndroidJUnit4::class)
class MixedLtrJustifyVisualOrderInstrumentedTest {

    @Before
    fun setUp() {
        ChapterProvider.apply {
            viewWidth = 2400
            viewHeight = 4000
            paddingHorizontal = 40
            paddingVertical = 40
            visibleWidth = viewWidth - paddingHorizontal * 2
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

    private fun para(text: String, align: CssTextAlign) =
        ReaderText.Text(text).apply {
            segDirect = RTLSegmenter.segment(line)
            textCssInfo.textAlign = align
        }

    private fun linesOf(ch: TextChapter): List<TextLine> =
        ch.pages.flatMap { it.textLines }.filter { it.textChars.isNotEmpty() }

    /** 屏幕左→右词序（组盒 min(start) 升序；组内字符按数组序拼接 = 逻辑词形） */
    private fun xOrderWords(l: TextLine): List<String> =
        l.textChars.filter { !it.isImage }
            .groupBy { it.renderGroup }
            .values.map { g -> g.minOf { it.start } to g.joinToString("") { it.charData }.trim() }
            .sortedBy { it.first }
            .map { it.second }
            .filter { it.isNotEmpty() }

    private fun logicalWords(slice: String) =
        slice.trim().split(" ").filter { it.isNotBlank() }

    /**
     * token 归一（审查 R6 + 实施修正）：逻辑侧「——وصل」破折号粘词、段末「مقدمته.」句点粘词——
     * 剔除每 token 两端的非阿语字符取 AR 核（引擎侧 renderGroup 按空白/run/paint 边界切组，
     * 标点天然独立成组）。数字 token（300/500/42）核为空 → 自然剔除，与 AR-only 比较口径一致。
     */
    private fun arCoreTokens(words: List<String>): List<String> =
        words.map { it.trim { c -> c.code !in 0x0600..0x06FF } }.filter { it.isNotEmpty() }

    /** U7 C 段（纯 AR 长块） */
    private val paraC = "C 对照：纯阿语长块（不含任何数字与外文）跨行时应保持逻辑序——وهذه فقرة عربية طويلة لا تحتوي على أي أرقام أو كلمات أجنبية حتى تبقى مقطعا واحدا متصلا ثم تتوزع أسطرها بترتيب منطقي صحيح من اليمين إلى اليسار دون أي تبديل في مواضع الكلمات عبر الأسطر المتتالية في هذه الفقرة الممتدة المكتوبة خصيصا للتحقق من ثبات الترتيب."

    /** U7 A 段（含 300/500/42 混排） */
    private val paraA = "A 主探针：史书引述阿拉伯编年史原文如下——وصل الجيش الأول 300 فارس إلى المدينة في صباح يوم الخميس وتحرك الجيش الثاني 500 فارس نحو الشرق عبر الجبال العالية حتى بلغوا النهر الكبير عند الغروب ثم عادوا إلى المعسكر الرئيسي 42 مرة في تلك السنة الطويلة وقبل نهاية الفصل الذي ذكره المؤرخ في مقدمته."

    // ── W5-b-1：修复直接钉（修复前必红）──

    @Test
    fun pureArabicMiddleLines_justify_xOrderIsLogicalReversed() {
        val lines = linesOf(chapter(listOf(para(paraC, CssTextAlign.CssTextAlignJustify))))
        assertTrue("应 >=4 行: ${lines.size}", lines.size >= 4)
        val middles = lines.subList(1, lines.lastIndex)     // 首行 CJK、末行退化，均不进 justifyLine
        assertTrue(middles.isNotEmpty())
        middles.forEachIndexed { i, l ->
            assertTrue("中间行应为单块未重锚（修复目标形态）line${i + 1}", !l.spanReordered)
            val slice = paraC.substring(l.charStartOffset, l.charEndOffset)
            assertEquals("纯 AR 中间行 x 序应为逻辑逆序（line${i + 1}）",
                logicalWords(slice).reversed(), xOrderWords(l))
        }
    }

    // ── W5-b-2：末行 [AR][.] 防退化钉（修复前后均应绿）──

    @Test
    fun lastLine_arabicWithPeriod_periodPinnedRight() {
        val lines = linesOf(chapter(listOf(para(paraC, CssTextAlign.CssTextAlignJustify))))
        val last = lines.last()
        val slice = paraC.substring(last.charStartOffset, last.charEndOffset)
        val core = logicalWords(slice).map { it.trimEnd('.') }
        assertEquals(core.reversed() + listOf("."), xOrderWords(last))
    }

    // ── W5-b-3：重锚行（spanReordered）不被本修复破坏——AR 词组 x 序 = 逻辑逆序 ──

    @Test
    fun mixedDigitGluedSpanLines_arabicTokensReversedInX() {
        val lines = linesOf(chapter(listOf(para(paraA, CssTextAlign.CssTextAlignJustify))))
        val reord = lines.filter { it.spanReordered }
        assertTrue("应有重锚行（含数字粘合段）", reord.isNotEmpty())
        reord.forEach { l ->
            val slice = paraA.substring(l.charStartOffset, l.charEndOffset)
            val arLogical = arCoreTokens(logicalWords(slice))
            val arInX = arCoreTokens(xOrderWords(l))
            assertEquals("重锚行 AR 词组 x 序 = 逻辑逆序（line@${l.charStartOffset}）",
                arLogical.reversed(), arInX)
        }
    }
}
