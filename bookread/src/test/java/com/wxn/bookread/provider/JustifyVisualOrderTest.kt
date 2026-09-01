package com.wxn.bookread.provider

import com.wxn.bookread.data.model.TextChar
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * C5（U7 镜像修复）JVM 钉：justify 分发视觉序（方案 §5 W5-a）。
 * 直测前提同 GluedSpanReorderTest / JustifyGapResolverTest：object 无 Android 初始化器路径。
 * C4 追加：重锚形态（glued span，块序 ≠ x 序 ≠ 简单逆序）视觉序钉。
 */
class JustifyVisualOrderTest {

    private val adv = 10f

    /** 等宽词 fixture：数组序 = [words] 传入序（逻辑序），每组词盒 x = [xPerWord]（视觉槽位） */
    private fun mirroredLine(words: List<String>, xPerWord: List<Float>): ArrayList<TextChar> {
        val out = ArrayList<TextChar>()
        words.forEachIndexed { wi, w ->
            val x0 = xPerWord[wi]
            w.forEachIndexed { ci, c ->
                out.add(TextChar(c.toString(), start = x0 + ci * adv, end = x0 + (ci + 1) * adv, renderGroup = wi + 1))
            }
        }
        return out
    }

    private fun groupWords(chars: List<TextChar>, rtl: Boolean) =
        TextLayoutProvider.justifyWordGroups(chars, rtl).map { g -> g.joinToString("") { it.charData } }

    // ── 修复目标形态：LTR 基调纯 AR 行（数组序 = 逻辑序 = 视觉序的逆）──

    @Test
    fun ltrPureArabicLine_wordGroups_visualOrder() {
        // 逻辑 [فارس نحو بلغوا]；视觉 L→R = [بلغوا نحو فارس]（逻辑首词最右）
        val chars = mirroredLine(listOf("فارس", "نحو", "بلغوا"), xPerWord = listOf(120f, 60f, 0f))
        assertEquals(listOf("بلغوا", "نحو", "فارس"), groupWords(chars, rtl = false))
    }

    @Test
    fun rtlBaseLine_wordGroups_descendingEqualsArrayOrder() {
        // 同一 fixture 走 RTL 行：x 降序 = 数组序（RTL 基调零回归钉）
        val chars = mirroredLine(listOf("فارس", "نحو", "بلغوا"), xPerWord = listOf(120f, 60f, 0f))
        assertEquals(listOf("فارس", "نحو", "بلغوا"), groupWords(chars, rtl = true))
    }

    @Test
    fun pureLtrLine_identity() {
        // 纯 LTR：x 升序 ≡ 数组序 → 输出 = 输入序（零回归钉）
        val chars = mirroredLine(listOf("abc", "def", "ghi"), xPerWord = listOf(0f, 30f, 60f))
        assertEquals(listOf("abc", "def", "ghi"), groupWords(chars, rtl = false))
        // 零宽并列 x：稳定排序保持数组序（审查 R3）
        val tied = mirroredLine(listOf("ab", "cd"), xPerWord = listOf(0f, 0f))
        assertEquals(listOf("ab", "cd"), groupWords(tied, rtl = false))
    }

    @Test
    fun imageLine_wordGroups_arrayOrderUnchanged() {
        // 含图行：保持数组首现序（JustifyChecker hasImage 现状守卫口径，零行为变化，审查 R1）。
        // 断言含图 token 本身：图组(renderGroup=9)在数组首位 → 输出首位仍在首；
        // 若误走 x 排序，文本组会变 ["cd","ab"]（数组序 = 逻辑序，x 降序）→ 红。
        val chars = mirroredLine(listOf("ab", "cd"), xPerWord = listOf(60f, 0f)).apply {
            add(0, TextChar("img/x.png", start = 40f, end = 55f, isImage = true, renderGroup = 9))
        }
        assertEquals(listOf("img/x.png", "ab", "cd"), groupWords(chars, rtl = false))
    }

    @Test
    fun charsInVisualOrder_ltrAsc_rtlDesc() {
        val chars = mirroredLine(listOf("ab", "cd", "ef"), xPerWord = listOf(40f, 20f, 0f))
        assertEquals(listOf("e", "f", "c", "d", "a", "b"),
            TextLayoutProvider.charsInVisualOrder(chars, lineIsRtl = false).map { it.charData })
        // 本 fixture 词内字符 x 升序（LTR 词形），严格 x 降序连词内字符一起反排 → [b,a,d,c,f,e]；
        // 真实 RTL 行词内 x 本为降序 → 输出 ≡ 数组序（零回归，W5-b 仪器钉覆盖）
        assertEquals(listOf("b", "a", "d", "c", "f", "e"),
            TextLayoutProvider.charsInVisualOrder(chars, lineIsRtl = true).map { it.charData })
    }

    @Test
    fun distributeJustifyChars_visualInput_xOrderUnmirrored() {
        // 修复后的 CHAR 路径链路等价形态：视觉序入参 → 摆放后屏幕 L→R = 视觉序（不翻转）
        val chars = mirroredLine(listOf("فارس", "نحو", "بلغوا"), xPerWord = listOf(120f, 60f, 0f))
        val ordered = TextLayoutProvider.charsInVisualOrder(chars, lineIsRtl = false)
        // 盒宽配套：12 字符 ×10 + 11 相邻对 ×8 = 208（fixture 无空白，首尾无剔除）
        TextLayoutProvider.distributeJustifyChars(ordered, 8f, 0f, 0f, 208f, lineIsRtl = false, hybridGroups = false)
        assertEquals(208f, ordered.last().end, 0.01f)
        val groupsByX = ordered.groupBy { it.renderGroup }.values
            .map { g -> g.minOf { it.start } to g.joinToString("") { it.charData } }
            .sortedBy { it.first }.map { it.second }
        assertEquals(listOf("بلغوا", "نحو", "فارس"), groupsByX)
    }

    // ── C4：重锚形态（块序 ≠ x 序 ≠ 简单逆序）——粘合段组的视觉序分发 ──
    @Test
    fun reanchoredGluedSpanLine_wordGroups_visualOrder() {
        // 逻辑/数组序 [CJK][AR1][300][AR2]（粘合段 = AR1+300+AR2 三个 level>=1 块）；
        // 视觉槽位（LTR 基调，屏幕 L→R）：[CJK][AR2][300][AR1]——粘合段内部逻辑逆序，
        // 数字居中不动（MixedBaseLtr singleLine 真值形态的同构缩样：
        // [如下——][إلى][فارس][300][الأول][الجيش][وصل]）
        val chars = mirroredLine(
            listOf("如下", "وصل", "300", "فارس"),
            xPerWord = listOf(0f, 150f, 110f, 70f)
        )
        assertEquals(
            listOf("如下", "فارس", "300", "وصل"),
            groupWords(chars, rtl = false)
        )
    }
}
