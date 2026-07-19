package com.wxn.bookparser.parser.txt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ChapterScanner.filterToc] 单测：验证 TOC 区域检测与剥离。
 *
 * 信号 A（主）：第一个重复章节名（5% 间隔约束）
 * 信号 B（辅助）：tocAnchorLine 须在前 5% 位置
 */
class FilterTocTest {

    private fun createScanner(): ChapterScanner {
        val detector = object : TxtCharsetDetector {
            override fun detect(headerBytes: ByteArray): CharsetDetectionResult {
                return CharsetDetectionResult("UTF-8", false)
            }
        }
        return ChapterScanner(detector)
    }

    /**
     * 构造行字符数前缀和：每行 lineChars 字符，共 lineCount 行。
     */
    private fun prefixUniform(lineChars: Int, lineCount: Int): LongArray {
        val prefix = LongArray(lineCount)
        var acc = 0L
        for (i in 0 until lineCount) {
            acc += lineChars
            prefix[i] = acc
        }
        return prefix
    }

    // ── 信号 A：第一个重复章节 ──

    @Test
    fun filterToc_firstDuplicate_dropsTocRange() {
        // 文件布局：TOC 列出 10 章（行 0-9，每章 1 行，位于文件前部），
        //          正文 10 章（行 10-19，每章约 100 行）
        // totalChars = 10 * 10 + 10 * 100 = 1100；TOC 区域 100 字符 < 5% * 1100 = 55？ 不，
        // 设 TOC 每章 5 字符共 50；正文每章 200 字符共 2000；total = 2050；5% = 102.5
        // 两次「第一章」间隔 = 行 10 的偏移 - 行 0 的偏移 ≈ 50 字符（TOC 内）< 102.5 → 触发
        val scanner = createScanner()
        // 构造：行 0-9 是 TOC（每行 5 字符 = "第一章"），行 10-19 是正文（每行 200 字符）
        val lineCount = 200
        val prefix = LongArray(lineCount)
        var acc = 0L
        for (i in 0 until lineCount) {
            acc += if (i < 10) 5L else 200L
            prefix[i] = acc
        }
        // 10 个 TOC 章节（第一章..第十章）+ 10 个正文章节（第一章..第十章）
        val matches = (1..10).map { i ->
            RawChapter("第${i}章", 0, 0, i - 1, i - 1, false, number = i)   // TOC 行 0-9
        } + (1..10).map { i ->
            RawChapter("第${i}章", 0, 0, 10 + i - 1, 10 + i - 1, false, number = i)  // 正文 行 10-19
        }
        val totalChars = (acc).toInt()
        val result = scanner.filterToc(matches, prefix, totalChars, tocAnchorLine = -1)
        // 期望：TOC 10 章剥离，保留正文 10 章
        assertEquals(10, result.size)
        // 第一个保留的章节 startLine 应在正文区（≥ 10）
        assertTrue("first kept startLine=${result[0].startLine} should be >= 10",
            result[0].startLine >= 10)
    }

    @Test
    fun filterToc_noDuplicate_returnsSame() {
        // 无重复章节名 → 不剥离
        val scanner = createScanner()
        val prefix = prefixUniform(100, 50)
        val matches = (1..10).map { i ->
            RawChapter("第${i}章", 0, 0, i * 2, i * 2 + 1, false, number = i)
        }
        val result = scanner.filterToc(matches, prefix, 5000, tocAnchorLine = -1)
        assertEquals(10, result.size)
    }

    // ── 5% 间隔约束 ──

    @Test
    fun filterToc_farRangeDuplicate_notToc() {
        // 第一次出现@5%，第二次@90% → 间隔远超 5%，不剥离（防御番外/上下篇重置误判）
        val scanner = createScanner()
        val prefix = LongArray(100) { (it + 1) * 100L }   // 每行 100 字符
        val matches = listOf(
            RawChapter("第一章", 0, 0, 5, 6, false, number = 1),       // @5% (行 5 = 500)
            RawChapter("第一章", 0, 0, 90, 91, false, number = 1)      // @90% (行 90 = 9000)
        )
        val totalChars = 10000
        val result = scanner.filterToc(matches, prefix, totalChars, tocAnchorLine = -1)
        assertEquals("间隔 > 5% 不应剥离", 2, result.size)
    }

    @Test
    fun filterToc_volumeReset_notToc() {
        // 上篇第1章@10% + 下篇第1章@60% → 间隔 50% > 5%，不剥离
        val scanner = createScanner()
        val prefix = LongArray(100) { (it + 1) * 100L }
        val matches = listOf(
            RawChapter("上篇 第一章", 0, 0, 10, 11, false, number = 1),
            RawChapter("下篇 第一章", 0, 0, 60, 61, false, number = 1)
        )
        val result = scanner.filterToc(matches, prefix, 10000, tocAnchorLine = -1)
        assertEquals("上下篇重置不应被误判为 TOC", 2, result.size)
    }

    // ── 信号 B：目录锚点辅助 ──

    @Test
    fun filterToc_anchorRefinesBoundary() {
        // tocAnchorLine 在第一个重复章节之前，且在前 5% → 修正 TOC 起点
        val scanner = createScanner()
        val prefix = LongArray(100) { (it + 1) * 10L }   // 每行 10 字符
        val matches = listOf(
            RawChapter("第一章", 0, 0, 5, 6, false, number = 1),    // 行 5
            RawChapter("第一章", 0, 0, 6, 7, false, number = 1)     // 行 6
        )
        val totalChars = 1000
        // anchor 在行 0（前 5%），第一个 TOC 候选行 0 之后是行 5 → 修正 tocStart 为 0
        val result = scanner.filterToc(matches, prefix, totalChars, tocAnchorLine = 0)
        assertEquals(1, result.size)   // 保留第二个（正文）
    }

    @Test
    fun filterToc_anchorBeyond5pct_ignored() {
        // tocAnchorLine 不在前 5% → 锚点无效，但信号 A 仍可独立触发
        val scanner = createScanner()
        val prefix = LongArray(100) { (it + 1) * 10L }
        val matches = listOf(
            RawChapter("第一章", 0, 0, 5, 6, false, number = 1),
            RawChapter("第一章", 0, 0, 6, 7, false, number = 1)
        )
        // anchor 在行 50（50% 位置）→ 超出 5%，无效
        val result = scanner.filterToc(matches, prefix, 1000, tocAnchorLine = 50)
        assertEquals(1, result.size)
    }

    @Test
    fun filterToc_anchorOnly_signalANotTriggered() {
        // 信号 A 未命中（无重复）→ 即使有 anchor 也不独立触发
        val scanner = createScanner()
        val prefix = prefixUniform(100, 50)
        val matches = (1..5).map { i ->
            RawChapter("第${i}章", 0, 0, i, i, false, number = i)
        }
        val result = scanner.filterToc(matches, prefix, 5000, tocAnchorLine = 0)
        assertEquals(5, result.size)
    }

    // ── 边界 ──

    @Test
    fun filterToc_empty_returnsEmpty() {
        val scanner = createScanner()
        val result = scanner.filterToc(emptyList(), LongArray(0), 0, -1)
        assertTrue(result.isEmpty())
    }

    @Test
    fun filterToc_single_returnsSame() {
        val scanner = createScanner()
        val single = listOf(RawChapter("第一章", 0, 0, 0, 1, false, number = 1))
        val result = scanner.filterToc(single, prefixUniform(100, 10), 1000, -1)
        assertEquals(1, result.size)
    }

    // ── 真实回归：三体.txt 同构样本 ──
    //
    // 三体书结构（行号从 0 计）：
    //   行 0     = "目录"                              ← tocAnchorLine=0
    //   行 1-100  = 寄语 / 版权 / 感言（非章节正文）       ← 收进 raw[0]（无名首章）
    //   行 100-186 = TOC 列表 36 章                     ← 收进 raw[1..36]
    //   行 193+  = 正文 36 章                           ← 收进 raw[37..72]
    //
    // 扫描循环产生 raw = [无名首章(line=0..99), TOC第一章, ..., TOC第三十六章, 正文第一章, ...]
    // 即 raw[0].name == "" 且 startLine=0。
    //
    // Bug 现象：filterToc 因 anchor=0（行0"目录"）将 effectiveTocStart 修正为 0，
    // 把 raw[0]（前置正文载体）也当作 TOC 一起剥掉。下游 buildResult 用下一章 startByte 作
    // 前一章 endByte，最终第一章 = 正文第一章，前置寄语/版权/目录全部无法读取。
    //
    // 预期：raw[0] 无名首章必须保留（它是前置正文的载体），不能被 TOC 剥离一起丢。
    @Test
    fun filterToc_prefaceChapterBeforeToc_keepsPreface() {
        val scanner = createScanner()
        // 模拟大文件：行 0..1000 每行 10 字符（前置+TOC，紧凑）；
        // 行 1000..9000 每行 100 字符（正文）。
        // totalChars ≈ 1000*10 + 8000*100 = 810000，5% = 40500
        // 两次"第一章"间隔 = 行 1100 偏移 - 行 100 偏移 ≈ 100*10 + 1000*100 - 100*10 = 100000？
        // 不对，重新算：把两次第一章都放在前置区附近，gap < 40500 才会触发信号A。
        //
        // 简化模型：1000 行，每行 30 字符。totalChars = 30000，5% = 1500。
        // 行 0=目录锚点；行 1-99=寄语版权；行 100=TOC第一章；行 200=正文第一章；
        // gap = (200-100)*30 = 3000 > 1500？仍太大。
        //
        // 用更密的行：每行 5 字符，1000 行 totalChars = 5000，5% = 250。
        // gap = 100*5 = 500 仍超 250。
        //
        // 正确建模：三体书两次第一章间隔仅 391 字符，totalChars 876159，5% = 43808。
        // 关键是"间隔"非常小但"文件"非常大。复现：让总字符数足够大即可。
        val lineCount = 2000
        val prefix = LongArray(lineCount)
        var acc = 0L
        for (i in 0 until lineCount) {
            acc += if (i < 200) 10L else 450L       // 行 0..199 紧凑（前置+TOC），行 200..1999 正文
            prefix[i] = acc
        }
        // totalChars = 200*10 + 1800*450 = 822000；5% = 41100
        // 行 100 偏移 = 100*10 = 1000；行 110 偏移 = 110*10 = 1100；gap = 100 < 41100 ✓ 信号A命中
        val totalChars = acc.toInt()

        val matches = listOf(
            RawChapter("", 0, 0, 0, 99, false, number = null),                  // 无名首章（前置正文）
            RawChapter("第一章 科学边界", 0, 0, 100, 100, false, number = 1),   // TOC
            RawChapter("第一章 科学边界", 0, 0, 110, 110, false, number = 1),   // 正文（紧邻 TOC，gap 小）
        )

        val result = scanner.filterToc(matches, prefix, totalChars, tocAnchorLine = 0)

        // 必须保留无名首章 + 正文第一章，共 2 项
        assertEquals("无名首章（前置正文）必须保留，不能被 TOC 剥离一起丢",
            2, result.size)
        assertEquals(0, result[0].startLine)               // 无名首章
        assertEquals(110, result[1].startLine)             // 正文第一章
    }

    /**
     * 完整三体书同构样本（端到端验证）：1 个无名首章 + 36 个 TOC + 36 个正文。
     *
     * 预期：filterToc 后保留 [无名首章, 正文第一章, ..., 正文第三十六章]，共 37 项。
     * 这个测试模拟真实三体书的 matches 数组形态（行号、章节名都按真实数据建模）。
     */
    @Test
    fun filterToc_fullThreeBodyStructure_keepsPrefaceAndStripsToc() {
        val scanner = createScanner()
        // 行号按真实三体.txt：行0=目录锚点；行 4..40=寄语/感言/版权；行 110..186=TOC 36章；
        // 行 193..=正文 36 章。这里简化行号，保留"前置段紧凑 + 正文段稀疏"的形态。
        //
        // 行字符数：前置段（0..200）每行 10 字符；正文段（200..4000）每行 200 字符。
        // totalChars = 200*10 + 3800*200 = 762000；5% = 38100
        // TOC 第一章@行 110 偏移 1100；正文第一章@行 200 偏移 2000+8800...
        // 不对，重新建模：TOC 都在前置段，正文都在正文段。
        // 两次"第一章"间隔 = 行 200 偏移 - 行 110 偏移 = (200*10) - (110*10) = 900 < 38100 ✓
        val lineCount = 4000
        val prefix = LongArray(lineCount)
        var acc = 0L
        for (i in 0 until lineCount) {
            acc += if (i < 200) 10L else 200L
            prefix[i] = acc
        }
        val totalChars = acc.toInt()

        // raw[0] = 无名首章（覆盖寄语+版权+目录锚点）
        val matches = mutableListOf<RawChapter>()
        matches.add(RawChapter("", 0, 0, 0, 109, false, number = null))

        // raw[1..36] = TOC 36 章（第一章..第三十六章，行 110..145）
        for (n in 1..36) {
            matches.add(RawChapter("第${n}章", 0, 0, 109 + n, 109 + n, false, number = n))
        }
        // raw[37..72] = 正文 36 章（行 200 起，每章约 100 行）
        for (n in 1..36) {
            matches.add(RawChapter("第${n}章", 0, 0, 200 + (n - 1) * 100, 200 + n * 100 - 1, false, number = n))
        }

        val result = scanner.filterToc(matches, prefix, totalChars, tocAnchorLine = 0)

        // 期望：1 个无名首章 + 36 个正文章节 = 37
        assertEquals("应保留无名首章 + 36 个正文章节（TOC 36 章被剥）",
            37, result.size)
        // 首项是无名首章
        assertEquals(0, result[0].startLine)
        assertTrue("无名首章必须保留", result[0].name.isEmpty())
        // 第二项起是正文第一章..第三十六章
        for (i in 1..36) {
            assertEquals("正文章节顺序错误 at index $i",
                200 + (i - 1) * 100, result[i].startLine)
        }
    }
}
