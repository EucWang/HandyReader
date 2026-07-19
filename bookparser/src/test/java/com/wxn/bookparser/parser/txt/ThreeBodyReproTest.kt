package com.wxn.bookparser.parser.txt

import org.junit.Assert
import org.junit.Test

/**
 * 端到端复现 三体.txt 真机报告的三个回归问题。
 *
 * 不依赖 Android Context / CachedFile，直接调用 internal 函数：
 *   - detectSubTocBlocks
 *   - applyModeA
 *   - ChapterScanner.applyDedup（仿 postProcess dispatch）
 *
 * 用户报告：
 *   1. 章节从 "第一章 科学边界" 开始，行 1-192 内容被删
 *   2. 所有章节只有章节名，无正文（blocker）
 *   3. 三体II/III 卷名未被识别为章节
 */
class ThreeBodyReproTest {

    private fun createScanner(): ChapterScanner {
        val detector = object : TxtCharsetDetector {
            override fun detect(headerBytes: ByteArray): CharsetDetectionResult {
                return CharsetDetectionResult("UTF-8", false)
            }
        }
        return ChapterScanner(detector)
    }

    /**
     * 模拟真实 三体.txt 的 shortLineTexts 内容（≤30 字符的短行）。
     *
     * 真实文件结构（基于读取 三体.txt）：
     *   lineIdx 0:    目录
     *   lineIdx 3:    刘慈欣给电子书读者的寄语
     *   lineIdx 7:    刘慈欣2018克拉克奖获奖感言
     *   lineIdx 11:   三体I                    ← 卷名
     *   lineIdx 15:   三体II·黑暗森林           ← 卷名
     *   lineIdx 19:   三体III·死神永生          ← 卷名
     *   lineIdx 25:   刘慈欣给电子书读者的寄语（正文标题）
     *   lineIdx 46:   版权信息
     *   lineIdx 70:   刘慈欣2018克拉克奖获奖感言（正文）
     *   lineIdx 109:  三体I                    ← 第二处卷名
     *   lineIdx 112:  第一章 科学边界          ← TOC 列表第 1 条
     *   ... 36 章 TOC 列表 ...
     *   lineIdx 184:  后 记                    ← TOC
     *   lineIdx 186:  脚注                     ← TOC
     *   lineIdx 192:  第一章 科学边界          ← 正文标题
     *   lineIdx 196:  汪淼觉得，来找他的这四个人是一个奇怪的组合  ← 正文短句
     *   ...
     *   lineIdx 5909: 三体II·黑暗森林          ← 第二处卷名
     *   lineIdx 5912: 序 章                    ← 三体II TOC 列表第 1 条
     *   lineIdx 5914: 上部 面壁者
     *   lineIdx 5916: 中部 咒语
     *   lineIdx 5918: 下部 黑暗森林
     *   lineIdx 5920: 脚注
     *   lineIdx 5926: 序　章                  ← 三体II 正文标题（全角空格）
     */
    @Test
    fun repro_printSubTocBlocks_and_ApplyModeA() {
        val shortLineTexts = mutableMapOf<Int, String>()

        // ── 头部（lineIdx 0-50）──
        shortLineTexts[0] = "目录"
        shortLineTexts[3] = "刘慈欣给电子书读者的寄语"
        shortLineTexts[7] = "刘慈欣2018克拉克奖获奖感言"
        shortLineTexts[11] = "三体I"
        shortLineTexts[15] = "三体II·黑暗森林"
        shortLineTexts[19] = "三体III·死神永生"
        shortLineTexts[25] = "刘慈欣给电子书读者的寄语"
        shortLineTexts[46] = "版权信息"
        shortLineTexts[70] = "刘慈欣2018克拉克奖获奖感言"

        // ── 头部三体I 卷名 + TOC 列表（lineIdx 109-186）──
        shortLineTexts[109] = "三体I"
        val chapterNames = listOf(
            "第一章 科学边界",
            "第二章 台 球",
            "第三章 射手和农场主",
            "第四章 三体、周文王、长夜",
            "第五章 叶文洁",
            "第六章 宇宙闪烁之一",
            "第七章 疯狂年代",
            "第八章 寂静的春天",
            "第九章 红岸之一",
            "第十章 宇宙闪烁之二",
            "第十一章 大 史",
            "第十二章 三体、墨子、烈焰",
            "第十三章 红岸之二",
            "第十四章 红岸之三",
            "第十五章 红岸之四",
            "第十六章 三体、哥白尼、宇宙橄榄球、三日凌空",
            "第十七章 三体问题",
            "第十八章 三体、牛顿、冯·诺伊曼、秦始皇、三日连珠",
            "第十九章 聚 会",
            "第二十章 三体、爱因斯坦、单摆、大撕裂",
            "第二十一章 三体、远征",
            "第二十二章 地球叛军",
            "第二十三章 红岸之五",
            "第二十四章 红岸之六",
            "第二十五章 叛 乱",
            "第二十六章 雷志成、杨卫宁之死",
            "第二十七章 无人忏悔",
            "第二十八章 伊文斯",
            "第二十九章 第二红岸基地",
            "第三十章 地球三体运动",
            "第三十一章 两个质子",
            "第三十二章 古筝行动",
            "第三十三章 监听员",
            "第三十四章 智 子",
            "第三十五章 虫 子",
            "第三十六章 尾声·遗址",
            "后 记",
            "脚注"
        )
        // 36 章在 lineIdx 112, 114, 116, ..., 184 + 后记 186 + 脚注 188（隔 2 行）
        chapterNames.forEachIndexed { i, name ->
            shortLineTexts[112 + i * 2] = name
        }

        // ── 三体I 正文区（lineIdx 192-5890，正文行 + 章节标题）──
        // 正文标题需与头部 TOC 列表一一对应
        chapterNames.forEachIndexed { i, name ->
            shortLineTexts[200 + i * 150] = name  // 200, 350, 500, ..., 200+35*150=5450
        }
        // 加入一些正文短句
        shortLineTexts[202] = "汪淼觉得，来找他的这四个人是一个奇怪的组合"
        shortLineTexts[352] = "推开丁仪那套崭新的三居室的房门"
        shortLineTexts[502] = "第二天是周末"

        // ── 三体II 头部（lineIdx 5890-5925）──
        shortLineTexts[5891] = "版权信息"
        shortLineTexts[5909] = "三体II·黑暗森林"
        // 三体II TOC 列表
        val iiToc = listOf("序 章", "上部 面壁者", "中部 咒语", "下部 黑暗森林", "脚注")
        iiToc.forEachIndexed { i, name ->
            shortLineTexts[5912 + i * 2] = name
        }
        // 三体II 正文标题（注意：序　章 用全角空格）
        shortLineTexts[5926] = "序　章"
        shortLineTexts[6100] = "上部 面壁者"
        shortLineTexts[7500] = "中部 咒语"
        shortLineTexts[9000] = "下部 黑暗森林"
        shortLineTexts[10500] = "脚注"

        // ── 三体III 头部 + 正文（lineIdx > 10500）──
        shortLineTexts[10700] = "三体III·死神永生"
        val iiiToc = listOf(
            "纪年对照表",
            "第一部 魔法师之死",
            "第二部 青铜时代号",
            "第三部 广播纪元",
            "第四部 掩体世界",
            "第五部 银河系猎户旋臂",
            "第六部 我们的星星"
        )
        iiiToc.forEachIndexed { i, name ->
            shortLineTexts[10703 + i * 2] = name
        }
        shortLineTexts[10800] = "纪年对照表"
        shortLineTexts[11500] = "第一部 魔法师之死"
        shortLineTexts[13000] = "第二部 青铜时代号"
        shortLineTexts[14500] = "第三部 广播纪元"
        shortLineTexts[16000] = "第四部 掩体世界"
        shortLineTexts[17500] = "第五部 银河系猎户旋臂"
        shortLineTexts[19000] = "第六部 我们的星星"

        // === 第 1 步：检测 sub-TOC 块 ===
        val blocks = detectSubTocBlocks(shortLineTexts)
        println("\n========== detectSubTocBlocks 结果 ==========")
        println("检测到 ${blocks.size} 个 sub-TOC 块：")
        blocks.forEach { block ->
            println("  block[${block.startLine}..${block.endLine}] entries=${block.entries.size}")
            block.entries.take(3).forEach { e ->
                println("    lineIdx=${e.lineIdx}  name='${e.name}'")
            }
            if (block.entries.size > 3) println("    ...（共 ${block.entries.size} 条）")
        }

        // === 第 2 步：构造 lineStartBytes / lineCharCounts ===
        val totalLines = 19200
        val lineStartBytes = (0 until totalLines).map { it.toLong() * 100 }.toList()
        val lineCharCounts = List(totalLines) { 80 }
        val totalChars = totalLines * 80
        val fileSize = totalLines.toLong() * 100

        // === 第 3 步：对每个 block 跑 applyModeA（V6：无窗口，前置 filterMirrorBlocks）===
        println("\n========== applyModeA 结果（每个 block） ===")
        val nonMirrorBlocks = filterMirrorBlocks(blocks)
        println("filterMirrorBlocks: ${blocks.size} -> ${nonMirrorBlocks.size} blocks")
        val acceptedWithBlock = nonMirrorBlocks.mapNotNull { block ->
            val r = applyModeA(
                block, shortLineTexts, lineStartBytes, lineCharCounts, fileSize
            )
            if (r != null) {
                println("  block[${block.startLine}..${block.endLine}] entries=${block.entries.size} " +
                    "matched=${r.size} ratio=${"%.2f".format(r.size.toDouble() / block.entries.size)} → ACCEPT")
                block to r
            } else {
                println("  block[${block.startLine}..${block.endLine}] entries=${block.entries.size} " +
                    "→ REJECT")
                null
            }
        }
        println("accepted=${acceptedWithBlock.size} 个块")

        // === 第 4 步：dispatch 决策（V5：调 buildVolumeAndSubChapters + insertPrefaceIfNeeded）===
        val scanner = createScanner()
        val prefix = LongArray(lineCharCounts.size).also {
            var acc = 0L
            for (i in lineCharCounts.indices) { acc += lineCharCounts[i]; it[i] = acc }
        }

        val finalChapters: List<RawChapter> = when {
            acceptedWithBlock.size >= 2 -> {
                println("\n>>> Mode B 触发：${acceptedWithBlock.size} 个 sub-TOC")
                val withVolumes = acceptedWithBlock.flatMap { (block, chapters) ->
                    buildVolumeAndSubChapters(block, chapters, shortLineTexts, lineStartBytes, fileSize)
                }.sortedBy { it.startLine }
                val withPreface = insertPrefaceIfNeeded(withVolumes, shortLineTexts, lineStartBytes)
                val deduped = scanner.applyDedup(withPreface, prefix)
                fixChapterBounds(deduped, lineCharCounts.size)
            }
            acceptedWithBlock.size == 1 -> {
                println("\n>>> Mode A 触发：单个 sub-TOC")
                val (block, chapters) = acceptedWithBlock[0]
                val withVolume = buildVolumeAndSubChapters(block, chapters, shortLineTexts, lineStartBytes, fileSize)
                val withPreface = insertPrefaceIfNeeded(withVolume, shortLineTexts, lineStartBytes)
                val deduped = scanner.applyDedup(withPreface, prefix)
                fixChapterBounds(deduped, lineCharCounts.size)
            }
            else -> {
                println("\n>>> Mode C 兜底")
                emptyList()
            }
        }

        println("\n========== 最终章节列表（前 10 + 末 5） ==========")
        finalChapters.take(10).forEachIndexed { i, ch ->
            val next = if (i + 1 < finalChapters.size) finalChapters[i + 1].startByte else fileSize
            val len = next - ch.startByte
            println("  [$i] name='${ch.name}' startLine=${ch.startLine} startByte=${ch.startByte} byteLen=$len")
        }
        if (finalChapters.size > 15) {
            println("  ... 共 ${finalChapters.size} 章 ...")
            finalChapters.takeLast(5).forEachIndexed { i, ch ->
                val realIdx = finalChapters.size - 5 + i
                val next = if (realIdx + 1 < finalChapters.size) finalChapters[realIdx + 1].startByte else fileSize
                val len = next - ch.startByte
                println("  [$realIdx] name='${ch.name}' startLine=${ch.startLine} startByte=${ch.startByte} byteLen=$len")
            }
        }

        // === 第 5 步：验证（V5 修复后） ===
        println("\n========== 验证 ==========")
        val firstCh = finalChapters.firstOrNull()
        if (firstCh != null) {
            println("问题1：首章 startLine=${firstCh.startLine} name='${firstCh.name}' (期望 = 0，卷首语)")
        }

        // 问题 2：检查空内容章节
        var emptyCount = 0
        finalChapters.forEachIndexed { i, ch ->
            val next = if (i + 1 < finalChapters.size) finalChapters[i + 1].startByte else fileSize
            val len = next - ch.startByte
            if (len <= 0) {
                println("问题2：[$i] '${ch.name}' 内容字节数 = $len ❌")
                emptyCount++
            }
        }
        println("问题2：空内容章节数 = $emptyCount / ${finalChapters.size}")

        // 问题 3：三体II/III 卷名识别
        val volIi = finalChapters.find { it.name.contains("三体II") }
        val volIii = finalChapters.find { it.name.contains("三体III") }
        println("问题3：三体II 卷名章节 = ${volIi?.name}@lineIdx=${volIi?.startLine}；" +
            "三体III = ${volIii?.name}@lineIdx=${volIii?.startLine}")

        // ── V5 修复断言 ──
        // 1. 卷首语保留
        Assert.assertNotNull("应有章节", firstCh)
        Assert.assertEquals("问题1：首章应从 lineIdx=0 开始（卷首语）", 0, firstCh?.startLine)

        // 2. 无空内容章节
        Assert.assertEquals("问题2：不应有空内容章节", 0, emptyCount)

        // 3. 三体I 卷名识别
        val volI = finalChapters.find { it.name == "三体I" }
        Assert.assertNotNull("问题3：应识别'三体I'卷名章节", volI)
        Assert.assertTrue("三体I 应为 isVolume", volI?.isVolume == true)

        // 4. 三体II 卷名识别
        Assert.assertNotNull("问题3：应识别'三体II·黑暗森林'卷名章节", volIi)
        Assert.assertTrue("三体II 应为 isVolume", volIi?.isVolume == true)

        // 5. 三体III 卷名识别
        Assert.assertNotNull("问题3：应识别'三体III·死神永生'卷名章节", volIii)
        Assert.assertTrue("三体III 应为 isVolume", volIii?.isVolume == true)

        // 6. 关键子章节都存在
        val expectedSubChapters = listOf(
            "第一章 科学边界",      // 三体I 第一章
            "第三十六章 尾声·遗址", // 三体I 末章
            "序 章",               // 三体II 子章节（sub-TOC 条目名）
            "纪年对照表",          // 三体III 子章节
            "第一部 魔法师之死"
        )
        expectedSubChapters.forEach { expected ->
            val found = finalChapters.any { it.name == expected }
            if (!found) println("缺失子章节：'$expected'")
            Assert.assertTrue("应包含子章节 '$expected'", found)
        }

        // 7. 总章节数合理（三体I 36 + 后记 + 脚注 + 三体I 卷 + 三体II 5 + 三体II 卷 + 三体III 7 + 三体III 卷 + 卷首语 = 54）
        println("总章节数 = ${finalChapters.size}")
        Assert.assertTrue("总章节数应在 50-60 之间，实际 ${finalChapters.size}",
            finalChapters.size in 50..60)
    }

    /**
     * V6 端到端复现：真机报告的"所有章节 chapterUrl 都集中在文件末尾 4.5KB"回归。
     *
     * 真实文件结构（三体.txt）：
     *   lineIdx 0-108:     卷首语（"目录"锚点 + 作者寄语 + 版权）
     *   lineIdx 109-187:   三体I 头部 sub-TOC（39 entries）
     *   lineIdx 192+:      三体I 正文
     *   lineIdx 5909-5926: 三体II 卷名 + 头部 sub-TOC（6 entries）
     *   lineIdx 5927+:     三体II 正文
     *   lineIdx 10700-10715: 三体III 卷名 + 头部 sub-TOC（7 entries）
     *   lineIdx 10716+:    三体III 正文
     *   lineIdx 25900-26063: **尾部 TOC 镜像块 1**（51 entries，三体I+II+III 所有章节）
     *   lineIdx 26129-26235: **尾部 TOC 镜像块 2**（51 entries，与镜像块 1 完全相同）
     *
     * V5 真机回归现象：
     *   - 头部三体I sub-TOC 因 searchWindow 过窄 ratio=51% 被拒绝
     *   - 尾部镜像块 1 的 entries 在镜像块 2 中找到同名行 → ACCEPT
     *   - 所有章节 chapterUrl 落在文件末尾 4.5KB，正文内容全无
     *
     * V6 修复期望：
     *   - filterMirrorBlocks 丢弃 2 个尾部镜像块
     *   - 头部 3 个 sub-TOC 全部 ACCEPT
     *   - 所有章节 byteLen 合理（既不全部挤在末尾，也不出现负值）
     */
    @Test fun v6_endToEnd_withTailTocMirror_chaptersNotConcentratedAtTail() {
        val shortLineTexts = mutableMapOf<Int, String>()

        // 头部卷首语（lineIdx 0-108）
        shortLineTexts[0] = "目录"
        shortLineTexts[3] = "刘慈欣给电子书读者的寄语"
        shortLineTexts[11] = "三体I"
        shortLineTexts[15] = "三体II·黑暗森林"
        shortLineTexts[19] = "三体III·死神永生"

        // 三体I 头部 sub-TOC（lineIdx 109-187，36 章 + 后记 + 脚注 = 38 entries）
        shortLineTexts[109] = "三体I"
        val sanTiIChapters = listOf(
            "第一章 科学边界", "第二章 台 球", "第三章 射手和农场主",
            "第四章 三体、周文王、长夜", "第五章 叶文洁", "第六章 宇宙闪烁之一",
            "第七章 疯狂年代", "第八章 寂静的春天", "第九章 红岸之一",
            "第十章 宇宙闪烁之二", "第十一章 大 史", "第十二章 三体、墨子、烈焰",
            "第十三章 红岸之二", "第十四章 红岸之三", "第十五章 红岸之四",
            "第十六章 三体、哥白尼、宇宙橄榄球、三日凌空", "第十七章 三体问题",
            "第十八章 三体、牛顿、冯·诺伊曼、秦始皇、三日连珠", "第十九章 聚 会",
            "第二十章 三体、爱因斯坦、单摆、大撕裂", "第二十一章 三体、远征",
            "第二十二章 地球叛军", "第二十三章 红岸之五", "第二十四章 红岸之六",
            "第二十五章 叛 乱", "第二十六章 雷志成、杨卫宁之死", "第二十七章 无人忏悔",
            "第二十八章 伊文斯", "第二十九章 第二红岸基地", "第三十章 地球三体运动",
            "第三十一章 两个质子", "第三十二章 古筝行动", "第三十三章 监听员",
            "第三十四章 智 子", "第三十五章 虫 子", "第三十六章 尾声·遗址",
            "后 记", "脚注"
        )
        sanTiIChapters.forEachIndexed { i, name -> shortLineTexts[111 + i * 2] = name }

        // 三体I 正文标题（lineIdx 500+，间隔 100 行避免与头部 sub-TOC 合并）
        sanTiIChapters.forEachIndexed { i, name -> shortLineTexts[500 + i * 100] = name }

        // 三体II 头部 sub-TOC（lineIdx 5909-5926）
        shortLineTexts[5909] = "三体II·黑暗森林"
        val sanTiIIChapters = listOf("序 章", "上部 面壁者", "中部 咒语", "下部 黑暗森林", "脚注")
        sanTiIIChapters.forEachIndexed { i, name -> shortLineTexts[5912 + i * 2] = name }
        // 三体II 正文（含全角空格变体）
        shortLineTexts[6000] = "序　章"
        shortLineTexts[6100] = "上部　面壁者"
        shortLineTexts[7000] = "中部　咒语"
        shortLineTexts[8000] = "下部　黑暗森林"
        shortLineTexts[9000] = "脚注"

        // 三体III 头部 sub-TOC（lineIdx 10700-10715）
        shortLineTexts[10700] = "三体III·死神永生"
        val sanTiIIIChapters = listOf(
            "纪年对照表",
            "第一部 魔法师之死", "第二部 青铜时代号", "第三部 广播纪元",
            "第四部 掩体世界", "第五部 银河系猎户旋臂", "第六部 我们的星星",
            "脚注"
        )
        sanTiIIIChapters.forEachIndexed { i, name -> shortLineTexts[10702 + i * 2] = name }
        // 三体III 正文
        shortLineTexts[10800] = "纪年对照表"
        shortLineTexts[11000] = "第一部 公元1453年5月，魔法师之死"
        shortLineTexts[12000] = "第二部 威慑纪元12年，青铜世纪号"
        shortLineTexts[13000] = "第三部 广播纪元7年，程心"
        shortLineTexts[14000] = "第四部 掩体纪元11年，掩体世界"
        shortLineTexts[15000] = "第五部 掩体纪元67年，银河系猎户旋臂"
        shortLineTexts[16000] = "第六部 银河纪元409年，我们的星星"
        shortLineTexts[17000] = "脚注"

        // ★ 尾部 TOC 镜像块 1（lineIdx 25900-26063）：三体I+II+III 所有章节列表
        val allChapters = sanTiIChapters + sanTiIIChapters + sanTiIIIChapters
        allChapters.forEachIndexed { i, name -> shortLineTexts[25900 + i * 3] = name }

        // ★ 尾部 TOC 镜像块 2（lineIdx 26129-26235）：与镜像块 1 完全相同
        allChapters.forEachIndexed { i, name -> shortLineTexts[26129 + i * 2] = name }

        // 模拟 lineStartBytes（每行 100 字节）
        val totalLines = 26300
        val fileSize = totalLines.toLong() * 100
        val lineStartBytes = (0 until totalLines).map { it.toLong() * 100 }
        val lineCharCounts = List(totalLines) { 80 }

        // === 执行 V6 全流程 ===
        val rawBlocks = detectSubTocBlocks(shortLineTexts)
        println("V6 detectSubTocBlocks: ${rawBlocks.size} blocks")
        rawBlocks.forEach { blk ->
            println("  block[L${blk.startLine}..L${blk.endLine}] entries=${blk.entries.size}")
        }

        val nonMirrorBlocks = filterMirrorBlocks(rawBlocks)
        println("V6 filterMirrorBlocks: ${rawBlocks.size} -> ${nonMirrorBlocks.size} blocks")

        val acceptedWithBlock = nonMirrorBlocks.mapNotNull { block ->
            applyModeA(block, shortLineTexts, lineStartBytes, lineCharCounts, fileSize)
                ?.let { block to it }
        }
        println("V6 accepted: ${acceptedWithBlock.size} blocks")

        Assert.assertTrue("V6 应至少接受 3 个头部 sub-TOC",
            acceptedWithBlock.size >= 3)
        Assert.assertTrue("V6 应丢弃 2 个尾部镜像块（actual=${rawBlocks.size - nonMirrorBlocks.size}）",
            rawBlocks.size - nonMirrorBlocks.size >= 2)

        // dispatch Mode B
        val scanner = createScanner()
        val prefix = LongArray(lineCharCounts.size).also {
            var acc = 0L
            for (i in lineCharCounts.indices) { acc += lineCharCounts[i]; it[i] = acc }
        }
        val withVolumes = acceptedWithBlock.flatMap { (block, chapters) ->
            buildVolumeAndSubChapters(block, chapters, shortLineTexts, lineStartBytes, fileSize)
        }.sortedBy { it.startLine }
        val withPreface = insertPrefaceIfNeeded(withVolumes, shortLineTexts, lineStartBytes)
        val deduped = scanner.applyDedup(withPreface, prefix)
        val finalChapters = fixChapterBounds(deduped, lineCharCounts.size)

        println("V6 final chapters: ${finalChapters.size}")
        finalChapters.take(5).forEach { ch ->
            val next = if (finalChapters.indexOf(ch) + 1 < finalChapters.size)
                finalChapters[finalChapters.indexOf(ch) + 1].startByte else fileSize
            println("  '${ch.name}' L${ch.startLine} byte[${ch.startByte}..$next] len=${next - ch.startByte}")
        }

        // === V6 关键断言 ===
        // 1. 不应所有章节都集中在文件末尾（V5 真机回归核心现象）
        val tailThreshold = fileSize * 9 / 10  // 末尾 10% 区域
        val tailConcentration = finalChapters.count { it.startByte >= tailThreshold }
        println("位于文件末尾 10% 的章节数：$tailConcentration / ${finalChapters.size}")
        Assert.assertTrue("V6 反回归：不应有超过 1/3 章节集中在末尾 10%（V5 此值接近 100%）",
            tailConcentration < finalChapters.size / 3)

        // 2. 不应有负 byteLen（V5 末章跨 block 衔接 bug）
        val negCount = finalChapters.count { ch ->
            val idx = finalChapters.indexOf(ch)
            val next = if (idx + 1 < finalChapters.size) finalChapters[idx + 1].startByte else fileSize
            next - ch.startByte < 0
        }
        Assert.assertEquals("V6 反回归：不应有负 byteLen 章节", 0, negCount)

        // 3. 三体I 第一章应在文件前部（lineIdx < 1000），不应在末尾
        val firstChapter = finalChapters.find { it.name == "第一章 科学边界" }
        Assert.assertNotNull("V6 应有'第一章 科学边界'", firstChapter)
        Assert.assertTrue("V6 '第一章' 应在文件前部（实际 lineIdx=${firstChapter?.startLine}）",
            (firstChapter?.startLine ?: -1) < 1000)

        // 4. 总章节数合理（不应因为尾部镜像块产生额外的 51+51 章节重复）
        Assert.assertTrue("V6 总章节数应在 50-60 之间（实际 ${finalChapters.size}）",
            finalChapters.size in 50..60)
    }
}
