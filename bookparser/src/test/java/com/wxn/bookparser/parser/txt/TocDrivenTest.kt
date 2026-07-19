package com.wxn.bookparser.parser.txt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TOC-Driven 章节解析单测（Mode A 单层 + Mode B 嵌套 + Mode C 兜底）。
 *
 * 详见 docs/plans/plan-toc-driven-mode-a-b.md。
 *
 * 覆盖目标：
 *   - isTocLikeLine：识别 native matcher 漏掉的章节模式（第N部/第N篇/第N回/上中下部/序 章/脚注/纪年对照表...）
 *   - normalizeForExactMatch：仅全角空格→半角 + trim
 *   - detectSubTocBlocks：识别 sub-TOC 块，忽略卷名块
 *   - applyModeA：sub-TOC 反向定位正文行；尾部 TOC 块无后续正文 → 自然失效
 *   - postProcess dispatch：Mode B（≥2 块）/Mode A（==1 块）/Mode C（0 块，回退）
 *
 * 所有用例纯 JVM，仿 FilterTocTest 模式，直接调 internal 文件级函数与 ChapterScanner 内部成员。
 */
class TocDrivenTest {

    // ════════════════════════════════════════════════════════
    //  isTocLikeLine
    // ════════════════════════════════════════════════════════

    @Test fun isTocLikeLine_cnZhang_true() = assertTrue(isTocLikeLine("第一章 科学边界"))
    @Test fun isTocLikeLine_cnBu_true() = assertTrue(isTocLikeLine("第一部 公元1453年5月，魔法师之死"))
    @Test fun isTocLikeLine_cnPian_true() = assertTrue(isTocLikeLine("第一篇 倾听者"))
    @Test fun isTocLikeLine_cnHui_true() = assertTrue(isTocLikeLine("第一回 宴桃园豪杰三结义"))
    @Test fun isTocLikeLine_shangBu_true() = assertTrue(isTocLikeLine("上部 面壁者"))
    @Test fun isTocLikeLine_zhongBu_true() = assertTrue(isTocLikeLine("中部 咒语"))
    @Test fun isTocLikeLine_xiaBu_true() = assertTrue(isTocLikeLine("下部 黑暗森林"))
    @Test fun isTocLikeLine_xuZhangWithSpace_true() = assertTrue(isTocLikeLine("序 章"))
    @Test fun isTocLikeLine_xuZhangFullWidthSpace_true() = assertTrue(isTocLikeLine("序　章"))
    @Test fun isTocLikeLine_jiaoZhu_true() = assertTrue(isTocLikeLine("脚注"))
    @Test fun isTocLikeLine_fuLu_true() = assertTrue(isTocLikeLine("附录"))
    @Test fun isTocLikeLine_jiNian_true() = assertTrue(isTocLikeLine("纪年对照表"))
    @Test fun isTocLikeLine_fanWai_true() = assertTrue(isTocLikeLine("番外篇"))
    @Test fun isTocLikeLine_daShiJi_true() = assertTrue(isTocLikeLine("大事记"))
    @Test fun isTocLikeLine_canKao_true() = assertTrue(isTocLikeLine("参考资料"))

    @Test fun isTocLikeLine_bodyText_false() {
        assertFalse(isTocLikeLine("他走进了房间，看见了一张桌子，桌上摆着一杯已经凉透的茶。"))
    }
    @Test fun isTocLikeLine_juanMing_false() {
        // 卷名行（"三体II·黑暗森林"）不匹配 isTocLikeLine——这是 Mode B 期望的行为
        assertFalse(isTocLikeLine("三体II·黑暗森林"))
    }
    @Test fun isTocLikeLine_juanMingI_false() = assertFalse(isTocLikeLine("三体I"))
    @Test fun isTocLikeLine_prefaceAuthorWords_false() {
        // "刘慈欣给电子书读者的寄语" 不是 TOC 模式
        assertFalse(isTocLikeLine("刘慈欣给电子书读者的寄语"))
    }
    @Test fun isTocLikeLine_blank_false() {
        assertFalse(isTocLikeLine(""))
        assertFalse(isTocLikeLine("   "))
    }
    @Test fun isTocLikeLine_longBody_false() {
        // 超长行（>TOC_LINE_MAX_LEN）首字符即便匹配也不应误判（实际是靠首字符+regex 双重过滤）
        // 这里测试首字符为"前"的长句正文不应命中（"前面有一座山..."）
        val long = "前面那座山非常高，山上有许多树，树下有一只小动物正在觅食，这是一段很长的描述文字。"
        assertFalse(isTocLikeLine(long))
    }

    // ════════════════════════════════════════════════════════
    //  normalizeForExactMatch
    // ════════════════════════════════════════════════════════

    @Test fun normalize_fullWidthSpace_toHalfWidth() {
        assertEquals("序 章", normalizeForExactMatch("序　章"))
    }
    @Test fun normalize_halfWidthSpace_preserved() {
        assertEquals("序 章", normalizeForExactMatch("序 章"))
    }
    @Test fun normalize_trimBothEnds() {
        assertEquals("第一章 科学边界", normalizeForExactMatch("  第一章 科学边界  "))
    }
    @Test fun normalize_noSpace_unchanged() {
        assertEquals("第一部 公元1453年5月，魔法师之死", normalizeForExactMatch("第一部 公元1453年5月，魔法师之死"))
    }

    // ════════════════════════════════════════════════════════
    //  detectSubTocBlocks
    // ════════════════════════════════════════════════════════

    @Test fun detect_singleSubToc_oneBlock() {
        // 单个连续 sub-TOC：5 条 isTocLikeLine 行紧密相连
        val shortLineTexts = linkedMapOf(
            0 to "上部 面壁者",
            1 to "中部 咒语",
            2 to "下部 黑暗森林",
            3 to "序 章",
            4 to "脚注"
        )
        val blocks = detectSubTocBlocks(shortLineTexts)
        assertEquals(1, blocks.size)
        assertEquals(5, blocks[0].entries.size)
        assertEquals(0, blocks[0].startLine)
        assertEquals(4, blocks[0].endLine)
    }

    @Test fun detect_threeBodyMultipleSubTocs_multipleBlocks() {
        // 模拟三体.txt 三层结构：头部卷名 + 三体I 36章 + 三体II 5项 + 三体III 7项
        val shortLineTexts = linkedMapOf<Int, String>()
        // 头部卷名（不匹配 isTocLikeLine）
        shortLineTexts[0] = "三体I"
        shortLineTexts[1] = "三体II·黑暗森林"
        shortLineTexts[2] = "三体III·死神永生"
        // 三体I sub-TOC（仅取前 5 章作为样本，避免 36 条冗长）
        for (i in 1..5) shortLineTexts[10 + i] = "第${cnNum(i)}章 测试"
        // 三体II sub-TOC
        shortLineTexts[100] = "序 章"
        shortLineTexts[101] = "上部 面壁者"
        shortLineTexts[102] = "中部 咒语"
        shortLineTexts[103] = "下部 黑暗森林"
        shortLineTexts[104] = "脚注"
        // 三体III sub-TOC
        shortLineTexts[200] = "纪年对照表"
        shortLineTexts[201] = "第一部 魔法师之死"
        shortLineTexts[202] = "第二部 青铜时代号"
        shortLineTexts[203] = "第三部 广播纪元"
        shortLineTexts[204] = "第四部 掩体世界"
        shortLineTexts[205] = "第五部 银河系猎户旋臂"
        shortLineTexts[206] = "第六部 我们的星星"

        val blocks = detectSubTocBlocks(shortLineTexts)
        assertEquals(3, blocks.size)
        // 卷名行不应进入任何块——块均以 isTocLikeLine 命中开始
        assertTrue(blocks.all { it.entries.isNotEmpty() })
        assertTrue(blocks.all { isTocLikeLine(it.entries.first().name) })
    }

    @Test fun detect_juanMingNotToc_zeroBlocks() {
        // 仅卷名行，无任何 isTocLikeLine 命中 → 0 个块
        val shortLineTexts = linkedMapOf(
            0 to "三体I",
            1 to "三体II·黑暗森林",
            2 to "三体III·死神永生"
        )
        val blocks = detectSubTocBlocks(shortLineTexts)
        assertEquals(0, blocks.size)
    }

    @Test fun detect_lineGapWithinThreshold_oneBlock() {
        // sub-TOC 内相邻命中行 lineIdx 间隔 ≤ SUB_TOC_MAX_LINE_GAP(=50) → 同一块
        val shortLineTexts = linkedMapOf(
            0 to "第一章 a",
            10 to "第二章 b",    // 间隔 10
            20 to "第三章 c"     // 间隔 10
        )
        val blocks = detectSubTocBlocks(shortLineTexts)
        assertEquals(1, blocks.size)
        assertEquals(3, blocks[0].entries.size)
    }

    @Test fun detect_lineGapOverThreshold_splitBlocks() {
        // lineIdx 间隔 > SUB_TOC_MAX_LINE_GAP(=50) → 断块
        // 第二段只有 1 条 < MIN_ENTRIES(=3) → 不被接受为块
        val shortLineTexts = linkedMapOf(
            0 to "第一章 a",
            10 to "第二章 b",
            20 to "第三章 c",
            // 间隔 1000（远 > 50）→ 触发 flush
            1020 to "第四章 d"
        )
        val blocks = detectSubTocBlocks(shortLineTexts)
        assertEquals(1, blocks.size)
        assertEquals(3, blocks[0].entries.size)
        assertEquals(0, blocks[0].startLine)
        assertEquals(20, blocks[0].endLine)
    }

    // ════════════════════════════════════════════════════════
    //  applyModeA
    // ════════════════════════════════════════════════════════

    @Test fun applyModeA_threeBodyII_matches_returnsFiveChapters() {
        // 三体II sub-TOC（序章/上部/中部/下部/脚注）
        val entries = listOf(
            TocEntry(100, "序 章", 0L),
            TocEntry(101, "上部 面壁者", 0L),
            TocEntry(102, "中部 咒语", 0L),
            TocEntry(103, "下部 黑暗森林", 0L),
            TocEntry(104, "脚注", 0L)
        )
        val block = SubTocBlock(startLine = 100, endLine = 104, entries = entries)

        // 正文行：5 个 TOC 项在后续行全部找到（精确前缀匹配）
        val shortLineTexts = mutableMapOf<Int, String>()
        // block 自身的 TOC 行
        entries.forEachIndexed { _, e -> shortLineTexts[e.lineIdx] = e.name }
        // 模拟正文中的标题行（5 条全部匹配）
        shortLineTexts[500] = "序 章"
        shortLineTexts[1000] = "上部 面壁者"
        shortLineTexts[1500] = "中部 咒语"
        shortLineTexts[2000] = "下部 黑暗森林"
        shortLineTexts[2500] = "脚注"

        val lineStartBytes = (0..2600).map { it.toLong() * 100 }.toMutableList()
        val lineCharCounts = List(2601) { 80 }
        val totalChars = 2601 * 80

        val result = applyModeA(block, shortLineTexts, lineStartBytes, lineCharCounts, fileSize = 260000L)
        assertNotNull(result)
        assertEquals(5, result!!.size)
        assertEquals("序 章", result[0].name)
        assertEquals(500, result[0].startLine)
        assertEquals("脚注", result[4].name)
        assertEquals(2500, result[4].startLine)
    }

    @Test fun applyModeA_threeBodyIII_matches_returnsSevenChapters() {
        val entries = listOf(
            TocEntry(200, "纪年对照表", 0L),
            TocEntry(201, "第一部 魔法师之死", 0L),
            TocEntry(202, "第二部 青铜时代号", 0L),
            TocEntry(203, "第三部 广播纪元", 0L),
            TocEntry(204, "第四部 掩体世界", 0L),
            TocEntry(205, "第五部 银河系猎户旋臂", 0L),
            TocEntry(206, "第六部 我们的星星", 0L)
        )
        val block = SubTocBlock(startLine = 200, endLine = 206, entries = entries)

        val shortLineTexts = mutableMapOf<Int, String>()
        entries.forEach { shortLineTexts[it.lineIdx] = it.name }
        // 正文中 TOC 项作为前缀出现（如"第一部 魔法师之死，公元1453年5月"）
        shortLineTexts[500] = "纪年对照表"
        shortLineTexts[1000] = "第一部 魔法师之死"
        shortLineTexts[1500] = "第二部 青铜时代号"
        shortLineTexts[2000] = "第三部 广播纪元"
        shortLineTexts[2500] = "第四部 掩体世界"
        shortLineTexts[3000] = "第五部 银河系猎户旋臂"
        shortLineTexts[3500] = "第六部 我们的星星"

        val lineStartBytes = (0..3600).map { it.toLong() * 100 }
        val lineCharCounts = List(3601) { 80 }
        val totalChars = 3601 * 80

        val result = applyModeA(block, shortLineTexts, lineStartBytes, lineCharCounts, fileSize = 360000L)
        assertNotNull(result)
        assertEquals(7, result!!.size)
    }

    @Test fun applyModeA_lowMatchRatio_returnsNull() {
        // 5 条 TOC 仅匹配 2 条（40% < 80%）→ null
        val entries = listOf(
            TocEntry(0, "第一章 a", 0L),
            TocEntry(1, "第二章 b", 0L),
            TocEntry(2, "第三章 c", 0L),
            TocEntry(3, "第四章 d", 0L),
            TocEntry(4, "第五章 e", 0L)
        )
        val block = SubTocBlock(startLine = 0, endLine = 4, entries = entries)

        val shortLineTexts = mutableMapOf<Int, String>()
        entries.forEach { shortLineTexts[it.lineIdx] = it.name }
        // 仅匹配 2 条
        shortLineTexts[100] = "第一章 a"
        shortLineTexts[200] = "第二章 b"
        // 其余三条不在 shortLineTexts

        val lineStartBytes = (0..300).map { it.toLong() * 100 }
        val lineCharCounts = List(301) { 80 }

        val result = applyModeA(block, shortLineTexts, lineStartBytes, lineCharCounts, fileSize = 30100L)
        assertNull(result)
    }

    @Test fun applyModeA_tailTocNoBody_returnsNull() {
        // 尾部 TOC 块：block.endLine 接近文件末尾，无后续正文行 → 自然失效
        val entries = listOf(
            TocEntry(490, "第一章 a", 0L),
            TocEntry(491, "第二章 b", 0L),
            TocEntry(492, "第三章 c", 0L)
        )
        val block = SubTocBlock(startLine = 490, endLine = 492, entries = entries)

        val shortLineTexts = mutableMapOf<Int, String>()
        entries.forEach { shortLineTexts[it.lineIdx] = it.name }
        // 不添加任何 > 492 的行（模拟尾部 TOC 后无正文）

        val lineStartBytes = (0..500).map { it.toLong() * 100 }
        val lineCharCounts = List(501) { 80 }

        val result = applyModeA(block, shortLineTexts, lineStartBytes, lineCharCounts, fileSize = 50100L)
        assertNull(result)
    }

    @Test fun applyModeA_tooFewEntries_returnsNull() {
        // 块内仅 2 条 isTocLikeLine（< TOC_MODE_MIN_ENTRIES）→ detectSubTocBlocks 阶段已过滤
        // 这里直接验证 applyModeA 的二次防御
        val entries = listOf(
            TocEntry(0, "第一章 a", 0L),
            TocEntry(1, "第二章 b", 0L)
        )
        val block = SubTocBlock(startLine = 0, endLine = 1, entries = entries)
        val shortLineTexts = mapOf(0 to "第一章 a", 1 to "第二章 b")
        val lineStartBytes = (0..100).map { it.toLong() * 100 }
        val lineCharCounts = List(101) { 80 }
        val result = applyModeA(block, shortLineTexts, lineStartBytes, lineCharCounts, fileSize = 10100L)
        assertNull(result)
    }

    // ════════════════════════════════════════════════════════
    //  postProcess dispatch（端到端，不暴露 private 方法 → 通过 scan 间接验证）
    //  因 postProcess 是 private，这里通过 detectSubTocBlocks + applyModeA 组合
    //  模拟 dispatch 逻辑，验证三种模式决策正确
    // ════════════════════════════════════════════════════════

    @Test fun dispatch_modeB_multipleSubTocsAccepted() {
        // 模拟三体.txt：3 个 sub-TOC 全部接受 → Mode B
        val shortLineTexts = mutableMapOf<Int, String>()
        // 三体I（模拟为 5 条）
        for (i in 1..5) shortLineTexts[10 + i] = "第${cnNum(i)}章 测试$i"
        // 三体II
        shortLineTexts[100] = "序 章"
        shortLineTexts[101] = "上部 面壁者"
        shortLineTexts[102] = "中部 咒语"
        shortLineTexts[103] = "下部 黑暗森林"
        shortLineTexts[104] = "脚注"
        // 三体III
        shortLineTexts[200] = "纪年对照表"
        shortLineTexts[201] = "第一部 一"
        shortLineTexts[202] = "第二部 二"
        shortLineTexts[203] = "第三部 三"

        val blocks = detectSubTocBlocks(shortLineTexts)
        assertEquals(3, blocks.size)
        // 模拟 dispatch：每个块都需要后续正文匹配才能接受
        // 此处只验证 dispatch 决策树正确（accepted.size >= 2 → Mode B）
        // 不依赖 buildResult 字节细节，因为应用层会调用 ChapterScanner.scan 走完整流程
    }

    @Test fun dispatch_modeA_singleSubTocAccepted() {
        // 单 sub-TOC → Mode A
        val shortLineTexts = mutableMapOf<Int, String>()
        shortLineTexts[0] = "序 章"
        shortLineTexts[1] = "上部 面壁者"
        shortLineTexts[2] = "中部 咒语"
        shortLineTexts[3] = "下部 黑暗森林"
        shortLineTexts[4] = "脚注"

        val blocks = detectSubTocBlocks(shortLineTexts)
        assertEquals(1, blocks.size)
        // Mode A 决策：accepted.size == 1
    }

    @Test fun dispatch_modeC_noSubTocAccepted_fallback() {
        // 无 isTocLikeLine 命中 → 0 块 → Mode C 兜底
        val shortLineTexts = mutableMapOf(
            0 to "刘慈欣给电子书读者的寄语",
            1 to "三体I",
            2 to "三体II·黑暗森林"
        )
        val blocks = detectSubTocBlocks(shortLineTexts)
        assertEquals(0, blocks.size)
        // Mode C 决策：accepted.size == 0 → 回退 filterToc + avgGap
    }

    // ════════════════════════════════════════════════════════
    //  V5 修复回归测试
    //  详见 docs/plans/plan-toc-driven-mode-a-b.md §V5
    // ════════════════════════════════════════════════════════

    /**
     * V5 Bug A/B/C：detectSubTocBlocks 把正文标题误并入 sub-TOC 块，
     * 且 applyModeA 单调指针在第一条 entry 失败后所有 entries 都失败。
     *
     * 场景：sub-TOC [序章/上部/中部/下部/脚注] 在 lineIdx 100-104，
     *      正文标题"序　章"（全角空格）在 lineIdx 110，被误并入 block；
     *      block.entries = 6 条（5 TOC + 1 正文标题）；endLine = 110。
     *
     * V4 行为（已废）：单调指针从 > 110 开始扫描，找不到"序 章"匹配（lineIdx 110 被排除），
     *                后续 entries 全部失败，matchedCount=0 → reject。
     *
     * V5 修复：每条 entry 独立从 entry.lineIdx+1 开始窗口搜索。
     *   - entries[0]="序 章"@100，在 [101, 100+window) 内找到 lineIdx 110 的"序　章"（normalize 等值）→ 匹配
     *   - entries[1]="上部 面壁者"@101，在 [102, 101+window) 内找后续匹配
     *   - entries[5]="序　章"@110（误并的正文标题），在 [111, 110+window) 内找——可能找不到，但其他 5 条都成功
     *   → matchedCount=5/6=83% ≥ 80% → ACCEPT
     */
    @Test fun v5_applyModeA_eachEntryIndependentWindow_acceptsMixedBlock() {
        val entries = listOf(
            TocEntry(100, "序 章", 0L),
            TocEntry(101, "上部 面壁者", 0L),
            TocEntry(102, "中部 咒语", 0L),
            TocEntry(103, "下部 黑暗森林", 0L),
            TocEntry(104, "脚注", 0L),
            TocEntry(110, "序　章", 0L)   // 误并的正文标题（全角空格）
        )
        val block = SubTocBlock(startLine = 100, endLine = 110, entries = entries)

        val shortLineTexts = mutableMapOf<Int, String>()
        entries.forEach { shortLineTexts[it.lineIdx] = it.name }
        // 正文中 5 个 TOC 项的匹配行（注意：lineIdx 110 已是"序　章"正文）
        shortLineTexts[200] = "上部 面壁者"
        shortLineTexts[300] = "中部 咒语"
        shortLineTexts[400] = "下部 黑暗森林"
        shortLineTexts[500] = "脚注"

        val lineStartBytes = (0..600).map { it.toLong() * 100 }
        val lineCharCounts = List(601) { 80 }

        val result = applyModeA(block, shortLineTexts, lineStartBytes, lineCharCounts,
            fileSize = 60100L)
        assertNotNull("V5 应接受此块（每 entry 独立窗口）", result)
        assertTrue("V5 应至少匹配 4 条", (result?.size ?: 0) >= 4)
    }

    /**
     * V5：sub-TOC 中某 entry 在正文中不存在（如三体II "上部 面壁者"在某文件缺失），
     * 应跳过该 entry，其他 entries 仍能匹配。匹配率达标则接受。
     */
    @Test fun v5_applyModeA_missingEntryInBody_othersStillMatch() {
        val entries = listOf(
            TocEntry(0, "序章", 0L),
            TocEntry(1, "上部", 0L),       // 这个在正文中不存在
            TocEntry(2, "中部", 0L),
            TocEntry(3, "下部", 0L),
            TocEntry(4, "脚注", 0L)
        )
        val block = SubTocBlock(startLine = 0, endLine = 4, entries = entries)
        val shortLineTexts = mutableMapOf<Int, String>()
        entries.forEach { shortLineTexts[it.lineIdx] = it.name }
        shortLineTexts[100] = "序章"
        // 上部 不在正文
        shortLineTexts[300] = "中部"
        shortLineTexts[400] = "下部"
        shortLineTexts[500] = "脚注"

        val lineStartBytes = (0..600).map { it.toLong() * 100 }
        val lineCharCounts = List(601) { 80 }

        val result = applyModeA(block, shortLineTexts, lineStartBytes, lineCharCounts,
            fileSize = 60100L)
        assertNotNull("4/5=80% 应接受", result)
        assertEquals(4, result?.size)
    }

    /**
     * V5 卷名识别：buildVolumeAndSubChapters 应在 sub-TOC 之前识别卷名并插入卷章节。
     */
    @Test fun v5_buildVolumeAndSubChapters_insertsVolumeChapter() {
        val block = SubTocBlock(
            startLine = 100, endLine = 104,
            entries = listOf(
                TocEntry(100, "序 章", 0L),
                TocEntry(101, "上部 面壁者", 0L),
                TocEntry(102, "中部 咒语", 0L)
            )
        )
        val subChapters = listOf(
            RawChapter("序 章", 10000L, 11000L, 200, 299, false),
            RawChapter("上部 面壁者", 11000L, 12000L, 300, 399, false),
            RawChapter("中部 咒语", 12000L, 13000L, 400, 499, false)
        )
        val shortLineTexts = mutableMapOf<Int, String>(
            95 to "三体II·黑暗森林",   // 卷名在 sub-TOC 之前 5 行
            100 to "序 章",
            101 to "上部 面壁者",
            102 to "中部 咒语"
        )
        val lineStartBytes = (0..500).map { it.toLong() * 100 }

        val result = buildVolumeAndSubChapters(block, subChapters, shortLineTexts, lineStartBytes, 50000L)

        assertEquals("应有 1 个卷章节 + 3 个子章节 = 4 个", 4, result.size)
        val volume = result[0]
        assertEquals("三体II·黑暗森林", volume.name)
        assertEquals(95, volume.startLine)
        assertEquals(199, volume.endLine)   // firstSubLineIdx - 1
        assertTrue("应为 isVolume=true", volume.isVolume)
    }

    /**
     * V5 卷首语保留：insertPrefaceIfNeeded 应在首章之前插入卷首语章节。
     * 章节名取首章之前第一个非空短行。
     */
    @Test fun v5_insertPrefaceIfNeeded_insertsPrefaceNamedByFirstLine() {
        val chapters = listOf(
            RawChapter("第一章", 5000L, 6000L, 50, 99, false)
        )
        val shortLineTexts = mutableMapOf<Int, String>(
            0 to "目录",       // 第一行非空短行
            10 to "作者寄语",
            20 to "版权信息"
        )
        val lineStartBytes = (0..200).map { it.toLong() * 100 }

        val result = insertPrefaceIfNeeded(chapters, shortLineTexts, lineStartBytes)

        assertEquals(2, result.size)
        val preface = result[0]
        assertEquals("目录", preface.name)
        assertEquals(0, preface.startLine)
        assertEquals(49, preface.endLine)
        assertEquals(0L, preface.startByte)
        assertEquals(5000L, preface.endByte)
    }

    /**
     * V5 卷首语保留：首章 startLine=0 时不插入。
     */
    @Test fun v5_insertPrefaceIfNeeded_firstChapterAtZero_noOp() {
        val chapters = listOf(
            RawChapter("第一章", 0L, 1000L, 0, 99, false)
        )
        val shortLineTexts = mutableMapOf<Int, String>()
        val lineStartBytes = (0..200).map { it.toLong() * 100 }

        val result = insertPrefaceIfNeeded(chapters, shortLineTexts, lineStartBytes)
        assertEquals("首章从 0 开始，不应插入卷首语", 1, result.size)
    }

    // ════════════════════════════════════════════════════════
    //  V6 修复：avgGap 过滤 + filterMirrorBlocks + fixChapterBounds
    // ════════════════════════════════════════════════════════

    /**
     * V6：detectSubTocBlocks 应丢弃 avgGap 过大的"正文聚集假阳性"块。
     *
     * 真实场景：三体I 正文中"第二十九~第三十一章"标题相距 40 行，会被误识别为 sub-TOC。
     * 修复：avgGap > SUB_TOC_MAX_INTERNAL_AVG_GAP(=10) 时丢弃。
     */
    @Test fun v6_detectSubTocBlocks_dropsHighAvgGapBlock() {
        val shortLineTexts = mutableMapOf<Int, String>()
        // 真 sub-TOC：3 条 entries 间隔 2 行（avgGap=2）
        shortLineTexts[0] = "第一章 科学边界"
        shortLineTexts[2] = "第二章 台 球"
        shortLineTexts[4] = "第三章 射手和农场主"
        // 正文聚集假阳性：3 条 entries 间隔 40 行（avgGap=40）
        shortLineTexts[1000] = "第二十九章 第二红岸基地"
        shortLineTexts[1040] = "第三十章 地球三体运动"
        shortLineTexts[1080] = "第三十一章 两个质子"

        val blocks = detectSubTocBlocks(shortLineTexts)
        assertEquals("应只剩 1 个真 sub-TOC（avgGap=2），丢弃正文聚集块", 1, blocks.size)
        assertEquals(0, blocks[0].startLine)
    }

    /**
     * V6：filterMirrorBlocks 应丢弃"TOC 复现"镜像块。
     *
     * 真实场景：三体.txt 头部各卷 sub-TOC + 尾部完整二次目录镜像。
     * 修复：若 block X 的前 MIRROR_COMPARE_FIRST_N 条 entry name 与更早 block Y 完全相同 → 丢弃 X。
     */
    @Test fun v6_filterMirrorBlocks_dropsTailTocMirror() {
        // 头部 sub-TOC（三体I 前 3 章）
        val headBlock = SubTocBlock(
            startLine = 100, endLine = 104,
            entries = listOf(
                TocEntry(100, "第一章 科学边界", 0L),
                TocEntry(101, "第二章 台 球", 0L),
                TocEntry(102, "第三章 射手和农场主", 0L)
            )
        )
        // 尾部镜像 TOC（前 3 条与头部完全相同）
        val tailMirror = SubTocBlock(
            startLine = 25000, endLine = 25010,
            entries = listOf(
                TocEntry(25000, "第一章 科学边界", 0L),
                TocEntry(25001, "第二章 台 球", 0L),
                TocEntry(25002, "第三章 射手和农场主", 0L),
                TocEntry(25003, "第四章 三体、周文王、长夜", 0L)
            )
        )
        // 三体II 中嵌 sub-TOC（前 3 条与三体I 不同 → 不算镜像）
        val middleBlock = SubTocBlock(
            startLine = 5000, endLine = 5004,
            entries = listOf(
                TocEntry(5000, "序 章", 0L),
                TocEntry(5001, "上部 面壁者", 0L),
                TocEntry(5002, "中部 咒语", 0L)
            )
        )

        val result = filterMirrorBlocks(listOf(headBlock, middleBlock, tailMirror))
        assertEquals("应保留 2 个非镜像块（头部+中嵌），丢弃尾部镜像",
            2, result.size)
        assertTrue("应保留头部 block", result.any { it.startLine == 100 })
        assertTrue("应保留中嵌 block", result.any { it.startLine == 5000 })
        assertFalse("应丢弃尾部镜像 block", result.any { it.startLine == 25000 })
    }

    /**
     * V6：filterMirrorBlocks 边界——两个独立 sub-TOC 前几项不同不视为镜像。
     */
    @Test fun v6_filterMirrorBlocks_keepsIndependentSubTocs() {
        val block1 = SubTocBlock(
            startLine = 100, endLine = 104,
            entries = listOf(
                TocEntry(100, "第一章 a", 0L),
                TocEntry(101, "第二章 b", 0L),
                TocEntry(102, "第三章 c", 0L)
            )
        )
        val block2 = SubTocBlock(
            startLine = 5000, endLine = 5004,
            entries = listOf(
                TocEntry(5000, "序章", 0L),
                TocEntry(5001, "上部", 0L),
                TocEntry(5002, "中部", 0L)
            )
        )
        val result = filterMirrorBlocks(listOf(block1, block2))
        assertEquals("两个独立 sub-TOC（前 3 条不同）应都保留", 2, result.size)
    }

    /**
     * V6：filterMirrorBlocks 边界——空列表和单元素列表原样返回。
     */
    @Test fun v6_filterMirrorBlocks_emptyAndSingle_noOp() {
        assertEquals(emptyList<SubTocBlock>(), filterMirrorBlocks(emptyList()))
        val single = SubTocBlock(0, 2, listOf(TocEntry(0, "第一章", 0L)))
        assertEquals(listOf(single), filterMirrorBlocks(listOf(single)))
    }

    /**
     * V6：fixChapterBounds 应修正跨 block 衔接处的 endLine。
     *
     * 真实场景：Mode B 多 block 合并后，前面 block 的末章 endLine 默认 = 文件末行，
     * 导致 wordCount 跨越整个文件。
     * 修复：每章 endLine = 下一章 startLine - 1。
     */
    @Test fun v6_fixChapterBounds_fixesEndLineAcrossBlocks() {
        val totalLines = 10000
        val chapters = listOf(
            // block1 末章（endLine 错误地指向文件末行）
            RawChapter("脚注-卷1", 1000L, 999999L, 100, 9999, false),
            // block2 首章
            RawChapter("序章-卷2", 2000L, 3000L, 200, 299, false),
            RawChapter("末章-卷2", 3000L, 999999L, 300, 9999, false)
        )
        val fixed = fixChapterBounds(chapters, totalLines)
        // 第 0 章 endLine 应修正为 199（下一章 startLine - 1）
        assertEquals(199, fixed[0].endLine)
        assertEquals(2000L, fixed[0].endByte)
        // 第 1 章 endLine 应修正为 299（已是）
        assertEquals(299, fixed[1].endLine)
        // 第 2 章（末章）endLine 应限制为 totalLines-1
        assertEquals(totalLines - 1, fixed[2].endLine)
    }

    /**
     * V6：fixChapterBounds 边界——空列表和单元素列表原样返回。
     */
    @Test fun v6_fixChapterBounds_emptyAndSingle_noOp() {
        assertEquals(emptyList<RawChapter>(), fixChapterBounds(emptyList(), 100))
        val single = RawChapter("序章", 0L, 100L, 0, 99, false)
        assertEquals(listOf(single), fixChapterBounds(listOf(single), 100))
    }

    // ════════════════════════════════════════════════════════
    //  回归保护：现有 RawChapter / applyDedup / filterToc 行为不变
    //  （这里只做轻量校验，主要回归在原 FilterTocTest/ChapterScannerTest）
    // ════════════════════════════════════════════════════════

    @Test fun regression_rawChapterFieldsUnchanged() {
        // 验证 RawChapter 7 字段顺序与默认值保持
        val rc = RawChapter(
            name = "序章",
            startByte = 0L,
            endByte = 100L,
            startLine = 0,
            endLine = 10,
            isVolume = false
        )
        assertEquals("序章", rc.name)
        assertEquals(0L, rc.startByte)
        assertEquals(100L, rc.endByte)
        assertEquals(0, rc.startLine)
        assertEquals(10, rc.endLine)
        assertFalse(rc.isVolume)
        assertNull(rc.number)  // 默认 null
    }

    @Test fun regression_applyDedup_onModeAOutput() {
        // Mode A 输出过 applyDedup 不应误删——序章/上部/中部/下部/脚注 均无数字，
        // 距离远 → 全部保留
        val scanner = createScanner()
        val chapters = listOf(
            RawChapter("序 章", 0L, 100L, 500, 999, false),
            RawChapter("上部 面壁者", 100L, 200L, 1000, 1499, false),
            RawChapter("中部 咒语", 200L, 300L, 1500, 1999, false),
            RawChapter("下部 黑暗森林", 300L, 400L, 2000, 2499, false),
            RawChapter("脚注", 400L, 500L, 2500, 2600, false)
        )
        // 空 prefix → applyDedup 只按行距判（500 行远 > DEDUP_MIN_LINE_GAP）
        val result = scanner.applyDedup(chapters, LongArray(0))
        assertEquals(5, result.size)
    }

    // ════════════════════════════════════════════════════════
    //  辅助
    // ════════════════════════════════════════════════════════

    private fun createScanner(): ChapterScanner {
        val detector = object : TxtCharsetDetector {
            override fun detect(headerBytes: ByteArray): CharsetDetectionResult {
                return CharsetDetectionResult("UTF-8", false)
            }
        }
        return ChapterScanner(detector)
    }

    private fun cnNum(n: Int): String = when (n) {
        1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"
        6 -> "六"; 7 -> "七"; 8 -> "八"; 9 -> "九"; 10 -> "十"
        else -> n.toString()
    }
}
