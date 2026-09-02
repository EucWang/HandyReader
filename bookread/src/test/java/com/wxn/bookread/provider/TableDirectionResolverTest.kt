package com.wxn.bookread.provider

import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 表格方向预扫描分组语义（方案 §5.1 / 任务 B1.1，判定函数注入 lambda——segment 走 JNI）。
 * 覆盖：D1 整表拼接判定 / 无强字符兜底 LTR / 分组边界（Separator、签名变化、非表格段）/
 * 段落索引落值 / 匿名表 / contents[j] 修复回归（曾误用 contents[i] 导致整章吞并）。
 */
class TableDirectionResolverTest {
    // 判定注入：含任意阿语区强字符 → RTL，否则 LTR（模拟 first-strong）。
    // ★ 不能只认 'ا'（alef）：如 "جدول"（jadwal）不含 alef，会漏判（首版测试数据缺陷，已修）
    private val fakeJudge: (String) -> Boolean =
        { it.any { ch -> ch in '\u0600'..'\u06FF' } }
    private fun row(line: String, cols: Int = 2, rows: Int = 3, percents: String = "50;50") =
        ReaderText.Text(line).also { it.annotations = listOf(
            TextTag(uuid = "tr", name = "tr", start = 0, end = 0),
            TextTag(uuid = "table", name = "table", start = 0, end = 0,
                params = "cols=$cols&rows=$rows&table_percent=$percents")) }

    @Test fun `整表拼接判定：首行数字数据行阿语 → RTL`() {
        val map = TableDirectionResolver.preScan(listOf(row("1 2"), row("العمود")), fakeJudge)
        assertEquals(2, map.size); assertTrue(map[0]!! && map[1]!!)
    }
    @Test fun `全表无强字符 → LTR`() {
        val map = TableDirectionResolver.preScan(listOf(row("123"), row("456")), fakeJudge)
        assertFalse(map[0]!! || map[1]!!)
    }
    @Test fun `非表格段落分隔后同签名表独立判定`() {
        val map = TableDirectionResolver.preScan(
            listOf(row("جدول"), ReaderText.Separator, row("table")), fakeJudge)
        assertEquals(2, map.size); assertTrue(map[0]!!); assertFalse(map[2]!!)
    }
    @Test fun `非相邻同签名异方向表互不覆盖（段落索引落值）`() {
        val map = TableDirectionResolver.preScan(listOf(
            row("جدول"), ReaderText.Separator, row("first"), row("data")), fakeJudge)
        assertEquals(3, map.size); assertTrue(map[0]!!); assertFalse(map[3]!!)
    }
    @Test fun `签名变化视为不同表`() {
        val map = TableDirectionResolver.preScan(listOf(
            row("جدول", percents = "50;50"), row("t", percents = "30;70")), fakeJudge)
        assertTrue(map[0]!!); assertFalse(map[1]!!)
    }
    @Test fun `匿名表连续行同组判定`() {
        val anon = ReaderText.Text("جدول").also { it.annotations = listOf(
            TextTag(uuid = "tr", name = "tr", start = 0, end = 0)) }
        val map = TableDirectionResolver.preScan(listOf(anon, anon), fakeJudge)
        assertEquals(2, map.size); assertTrue(map[0]!! && map[1]!!)
    }
    @Test fun `分组游标回归-分组不越过非表格段落且第二表独立判定`() {
        // ★ 修复回归：内层循环曾误用 contents[i]（恒为组首行）→ 分组条件恒真吞并到章节尾，
        //   第二表丢失独立条目（消费端 ?: false 兜底成 LTR）——阿语第二表被当 LTR 排
        val map = TableDirectionResolver.preScan(
            listOf(row("جدول"), ReaderText.Text("plain"), row("1 2"), row("data")), fakeJudge)
        assertEquals(3, map.size)           // 只有 3 个表格行有条目
        assertFalse(map.containsKey(1))     // 普通段落不留条目
        assertTrue(map[0]!!)                // 第一表 RTL
        assertFalse(map[2]!!)               // 第二表（"1 2 data" 无强字符）LTR，未被第一表覆盖
    }
    @Test fun `相邻同签名异方向表共享方向-已接受限制R3行为固化`() {
        // R3 已接受限制的契约测试：紧邻且签名相同的两「表」并组共享一个方向判定
        // （仅方向共享，布局结构仍逐段独立）。固化现状，防止将来被无意改变而无测试报警。
        val map = TableDirectionResolver.preScan(
            listOf(row("مرحبا"), row("data")), fakeJudge)   // 同签名相邻
        assertEquals(2, map.size)
        assertTrue("并组后整组按拼接文本（含阿语）判 RTL，两行同值", map[0]!! && map[1]!!)
    }
}
