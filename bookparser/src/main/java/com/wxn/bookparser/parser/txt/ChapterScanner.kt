package com.wxn.bookparser.parser.txt

import com.wxn.base.bean.BookChapter
import com.wxn.base.exception.NotTextFileException
import com.wxn.base.util.Logger
import com.wxn.base.util.numReplacer.ZhNumberReplacer
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.exts.clearAllMarkdown
import com.wxn.mobi.inative.NativeLib
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val SCANNER_TAG = "ChapterScanner"
private const val UNIFORM_SPLIT_CHARS = 150000
private const val AVG_GAP_MIN = 500
private const val AVG_GAP_MAX = 500000
private const val DEDUP_MIN_LINE_GAP = 3
private const val DEDUP_CHAR_GAP = 150L
private const val BUF_SIZE = 65536
private const val MAX_CHAPTER_NUMBER = 99999
private const val TOC_ANCHOR_MAX_LINES = 200

// v2 修复（review §一阶遗漏 #7 + §X3）：单行字节上限，防御 OOM。
// 用户可能导入"完全没有换行的 10 MB TXT"——lineBuf 会无限累积导致 OOM。
// 超限时只保留头部 MAX_LINE_KEEP_BYTES 字节（足够章节标题识别），其余正文丢弃。
// 阈值依据：最长合法行（短段落 + Markdown）通常 < 16 KB；1 MiB 足够保守。
private const val MAX_LINE_BYTES = 1 * 1024 * 1024  // 1 MiB
private const val MAX_LINE_KEEP_BYTES = 256         // 截断时保留头部字节数（够章节标题匹配）

// ── TOC-Driven 解析（Mode A 单层 + Mode B 嵌套）参数 ──
// 详见 docs/plans/plan-toc-driven-mode-a-b.md
private const val TOC_LINE_MAX_LEN = 30              // 仅记录 ≤30 字符的短行到 shortLineTexts
private const val TOC_MODE_MIN_ENTRIES = 3           // sub-TOC 至少 3 条才算候选块
private const val TOC_MODE_MIN_MATCH_RATIO = 0.8     // 块内 TOC 条目与正文行的匹配率阈值
private const val TOC_MODE_MIN_MATCH_COUNT = 3       // 即便 ratio 高，匹配数 ≥3 才接受（防小伪 TOC）
// sub-TOC 内相邻 isTocLikeLine 命中行距上限：超过则认为跨越了正文，断开成不同块。
// 实际 sub-TOC 内条目间隔通常 ≤3（含空行装饰），sub-TOC 之间至少数百行正文。
private const val SUB_TOC_MAX_LINE_GAP = 50
// V6：真 sub-TOC 内部 entries 的平均行距上限。
// 真 TOC（head/中嵌/尾部镜像）条目密集排列，间隔通常 2-4 行（仅含空行装饰）；
// 正文章节标题自然聚集（如三体I 第二十九~三十一章正文相距 40 行）avgGap≥40。
// 阈值 10 容忍 padding，避免误杀略稀疏的真 TOC。
private const val SUB_TOC_MAX_INTERNAL_AVG_GAP = 10
// V6：镜像块检测时比较的 entry 数。
// 同一 TOC 在文件多处复现（如三体.txt 头部 sub-TOC + 尾部二次目录），
// 前 3 条 entry 名完全相同几乎不可能是巧合。
private const val MIRROR_COMPARE_FIRST_N = 3

data class ScanResult(
    val charsetName: String,
    val isUtf16Or32: Boolean,
    val chapters: List<BookChapter>,
    val wordCounts: List<Triple<Int, Int, Int>>,
    val chapterByteOffsets: LongArray?
)

data class RawChapter(
    val name: String,
    val startByte: Long,
    val endByte: Long,
    val startLine: Int,
    val endLine: Int,
    val isVolume: Boolean,
    val number: Int? = null          // 章节号（反查得来），默认 null 保持向后兼容
)

// ── TOC-Driven 数据结构 ──
// TocEntry：sub-TOC 中识别出的一条章节标题项（detectSubTocBlocks 阶段不查 startByte，留 0L）
internal data class TocEntry(
    val lineIdx: Int,
    val name: String,
    val startByte: Long
)

// SubTocBlock：文件中一段连续 isTocLikeLine 命中构成的子目录块（如三体.txt 中三体II 的 5 项 sub-TOC）
internal data class SubTocBlock(
    val startLine: Int,
    val endLine: Int,
    val entries: List<TocEntry>
)

internal fun isChapterCandidate(line: String): Boolean {
    if (line.isEmpty()) return false
    val c = line.first()
    val cp = c.code
    return when {
        c == '第' || c == '序' || c == '楔' || c == '前' ||
        c == '后' || c == '尾' || c == '引' || c == '跋' -> true
        // 英文预过滤：仅对短行放行进入 JNI。native matchEnRe 是整串耗尽语义
        // （Chapter|Chap|Section|Scene|Part|Introduction + \s+ + [0-9IVXLCDM]+），
        // 合法标题必短；超长英文行 native 本就返回 0，加长度上限可省掉一次无谓的 JNI 往返。
        // CJK/俄语等分支不受影响。
        // 'f' 用于 matchEnSpecialRe 的 Foreword；c/p/e/s/i 覆盖 matchEnRe 与 special 词表。
        c.lowercaseChar() in "cpefksvbait" && line.length <= 60 -> true
        cp in 0x0410..0x044F -> true
        cp in 0x0900..0x097F -> true
        cp in 0x0600..0x06FF -> true
        c == '제' -> true
        else -> false
    }
}

// ── 章节号解析（parseChapterNumber / extractNumberSegment） ──
//
// 设计目标：从已命中的章节标题中提取数字，供 applyDedup 的「数字连续递增豁免」
// 与「同号近距离折叠」使用。支持范围 1..99999，超出或解析失败返回 null。
//
// 支持语种：
//   - ASCII 数字（0-9）：所有语种通用
//   - 罗马数字（IVXLCDM）：英/法/西/葡/德/俄等
//   - 中文数字：中/日（复用 ZhNumberReplacer.chineseToInt 反查，零歧义）
// 不支持（matcher 仍识别，本函数返回 null，applyDedup 降级为距离去重）：
//   - Arabic-Indic ٠-٩ / Devanagari ๐-๙
//   - 中文/ASCII 混合数字（如 第3十2章）

private val RE_ASCII_DIGIT = Regex("[0-9]+")
private val RE_CJK_DIGIT = Regex("[零一二三四五六七八九十百千万两]+")
// 罗马段必须两侧都是非字母（防止 "Chapter" 的 C 被当成罗马数字 C=100 吞掉）
private val RE_ROMAN = Regex("(?<![A-Za-z])[IVXLCDM]+(?![A-Za-z])")

/**
 * 数字段提取：按优先级 ASCII > CJK > 罗马。
 * 同一 title 中 ASCII 与 CJK 同时出现（如 第3十2章）→ 返回 null（混合降级）。
 */
internal fun extractNumberSegment(title: String): String? {
    val ascii = RE_ASCII_DIGIT.find(title)?.value
    val cjk = RE_CJK_DIGIT.find(title)?.value
    val roman = RE_ROMAN.find(title)?.value

    return when {
        ascii != null && cjk != null -> null          // 混合数字降级
        ascii != null -> ascii
        cjk != null -> cjk
        roman != null -> roman
        else -> null
    }
}

private fun sanitizeChapterNumber(n: Int): Int? =
    if (n in 1..MAX_CHAPTER_NUMBER) n else null

/**
 * 解析章节标题中的数字。
 *
 * 范围限制 1..99999，超出或解析失败返回 null。
 * 中文数字复用 ZhNumberReplacer.chineseToInt 反查，零歧义。
 *
 * @param title 已被 matcher 命中的完整标题（如 "第二十三章" / "Chapter IV" / "第3章"）
 * @return 章节号 [1, 99999]，或 null
 */
internal fun parseChapterNumber(title: String): Int? {
    val segment = extractNumberSegment(title) ?: return null

    // 1. ASCII 数字（所有语种通用）
    segment.toIntOrNull()?.let { return sanitizeChapterNumber(it) }

    // 2. 纯罗马数字（大小写不敏感）
    if (segment.all { it in "IVXLCDMivxlcdm" }) {
        val n = ZhNumberReplacer.romanToInt(segment.uppercase())
        return sanitizeChapterNumber(n)
    }

    // 3. 中文数字：反查 intToChinese
    val n = ZhNumberReplacer.chineseToInt(segment)
    if (n != null) return sanitizeChapterNumber(n)

    return null  // 含 Arabic-Indic / Devanagari / 未识别格式
}

// ── 标题规范化（normalizeChapterName） ──
//
// 唯一目的：让「TOC 中的同一章节」与「正文中的同一章节」产生相同 canonical key，
// 供 filterToc 检测「第一个重复章节」。规范化产物是结构化 key，不是字符串前缀：
//   "CN:章:1"  — 中文章节（第X章节卷）
//   "EN:chapter:1" — 英文章节（Chapter|Chap|Section|Scene|Part|Introduction + 数字）
//   "SP:序"  — 特殊词（序/序章/序言 归一；楔子；引子；等）
//   "RAW:<原文>" — 兜底
//
// 两条互斥约束：
//   - 同章不同写法 → 同 key（如 第一章 初遇 vs 第1章 → 都为 CN:章:1）
//   - 异章不同内容 → 不同 key（如 第一章 vs 第一节 → CN:章:1 vs CN:节:1）

private val RE_CN_CHAPTER = Regex("^(第)([零一二三四五六七八九十百千万两0-9]+)([章节卷])")
private val RE_EN_CHAPTER = Regex(
    "^(Chapter|Chap|Section|Scene|Part|Introduction)[\\s　]+([0-9IVXLCDM]+)",
    RegexOption.IGNORE_CASE
)

/**
 * 章节标题规范化为 canonical key。详见上方设计说明。
 */
internal fun normalizeChapterName(name: String): String {
    val s = name.trim()
    if (s.isEmpty()) return s

    // 1. 中文章节：第 + 数字 + 章/节/卷
    RE_CN_CHAPTER.find(s)?.let { m ->
        val unit = m.groupValues[3]
        val n = parseChapterNumber(m.value)
        return if (n != null) "CN:$unit:$n" else "CN:$unit:${m.groupValues[2]}"
    }

    // 2. 英文章节：关键词 + 数字（含罗马）
    RE_EN_CHAPTER.find(s)?.let { m ->
        val kw = m.groupValues[1].lowercase()
        val n = parseChapterNumber(m.value)
        return if (n != null) "EN:$kw:$n" else "EN:$kw:${m.groupValues[2].lowercase()}"
    }

    // 3. 特殊词：序/序章/序言 归一到 SP:序；其他各自归一
    val specialCanonical = when {
        s.startsWith("序章") || s.startsWith("序言") || s == "序" -> "SP:序"
        s.startsWith("楔子") -> "SP:楔子"
        s.startsWith("前言") -> "SP:前言"
        s.startsWith("引子") || s.startsWith("引言") -> "SP:引"
        s.startsWith("后记") || s == "跋" -> "SP:后记"
        s.startsWith("尾声") -> "SP:尾声"
        s.equals("Prologue", ignoreCase = true) ||
            s.startsWith("Prologue", ignoreCase = true) -> "SP:prologue"
        s.equals("Epilogue", ignoreCase = true) ||
            s.startsWith("Epilogue", ignoreCase = true) -> "SP:epilogue"
        s.equals("Preface", ignoreCase = true) ||
            s.startsWith("Preface", ignoreCase = true) -> "SP:preface"
        s.equals("Foreword", ignoreCase = true) ||
            s.startsWith("Foreword", ignoreCase = true) -> "SP:foreword"
        s.equals("Afterword", ignoreCase = true) ||
            s.startsWith("Afterword", ignoreCase = true) -> "SP:afterword"
        else -> null
    }
    if (specialCanonical != null) return specialCanonical

    // 4. 兜底：剥尾部标点（保留原文，至少不会假阳性）
    var t = s
    while (t.isNotEmpty() && t.last() in "。，！？；：、…～~.!?;:\"'") t = t.dropLast(1)
    return "RAW:$t"
}

// ── TOC-Driven 识别（Mode A/B）──
//
// 与 normalizeChapterName 的区别：
//   - normalizeChapterName 用于 filterToc 的「重名信号 A」——需要把多种写法归一到同一 canonical key。
//   - normalizeForExactMatch 用于 Mode A 的「精确匹配」——只做最小必要的字符规整（全角空格→半角），
//     不做语义归一，保留原文以供与正文行字面比较。
//
// isTocLikeLine 是独立启发式，**不依赖 native matcher**：
//   - native matcher（chapter_matcher.h）只识别 第N章/节/卷 + 序章/楔子/前言/后记/尾声/引子/跋。
//   - isTocLikeLine 额外覆盖 native 漏掉的模式：第N部/第N篇/第N回、上中下部、序 章(带空格)、
//     脚注、附录、纪年对照表、大事记、番外、参考资料、导读。
//   - 该函数只在 postProcess 内部使用，scan 路径完全不调用，避免影响 raw 内容与现有测试。

private val TOC_LINE_FIRST_CHARS = setOf(
    '第', '序', '楔', '前', '后', '尾', '引', '跋',
    '上', '中', '下',
    '脚', '附', '纪', '番', '大', '参', '导'
)

private val RE_TOC_LINE = Regex(
    "^(" +
        // 含 native 漏掉的 部/篇/回
        "第[零一二三四五六七八九十百千万两0-9]+[章节卷部篇回]|" +
        // 上部/中部/下部（含 上/中/下 单字 + 部）
        "[上中下]+部|" +
        // 含带空格的 序 章 / 楔 子 / 引 子 / 序 言（native 漏空格变体）
        // \s 不含全角空格 U+3000，必须显式加入字符集
        "序[\\s\u3000]*[章言]|楔[\\s\u3000]*子|引[\\s\u3000]*[子言]|" +
        // special words（部分 native 已覆盖，加入无冲突）
        "前言|后[\\s\u3000]*[记序]|尾声|跋|" +
        // native 完全未覆盖
        "脚注|附录|纪年对照表|大事记|番外篇?|参考资料|导读" +
    ")"
)

/**
 * 是否为「TOC-like」行（章节/卷/特殊词/附件类标题）。
 *
 * 首字符白名单快速过滤 → 正则匹配，避免对每个短行都跑一次正则。
 * 空行直接返回 false。
 */
internal fun isTocLikeLine(line: String): Boolean {
    if (line.isBlank()) return false
    val trimmed = line.trim()
    val first = trimmed.firstOrNull() ?: return false
    if (first !in TOC_LINE_FIRST_CHARS) return false
    return RE_TOC_LINE.containsMatchIn(trimmed)
}

/**
 * Mode A/B 精确匹配用最小规范化：仅全角空格 U+3000 → 半角 + trim。
 * 不做语义归一（不调 normalizeChapterName），保留原文以供字面比较。
 */
internal fun normalizeForExactMatch(name: String): String =
    name.replace('\u3000', ' ').trim()

/**
 * 检测文件中所有「子目录块」（sub-TOC）。
 *
 * 一个 sub-TOC = 连续命中 [isTocLikeLine] 的行，且：
 *   - 相邻命中行的 lineIdx 间隔 ≤[SUB_TOC_MAX_LINE_GAP]（同块）
 *   - 总命中数 ≥[TOC_MODE_MIN_ENTRIES]
 *
 * 注意：shortLineTexts 只包含 ≤[TOC_LINE_MAX_LEN] 字符的短行，正文长行不进表。
 * 因此不能依赖"遇到非空非 TOC 行就断块"——两个 sub-TOC 之间的长正文行根本不在迭代序列里，
 * 看到的只是 lineIdx 跳跃（如从 lineIdx=5 直接跳到 lineIdx=100）。所以用 **lineIdx 间隔**
 * 作为块边界判据，间隔 >[SUB_TOC_MAX_LINE_GAP] 立即断开。
 *
 * 卷名行（如"三体I"/"三体II·黑暗森林"）不匹配 isTocLikeLine，**不会被识别为 sub-TOC**——
 * 这正是 Mode B 期望的行为：头部 TOC 的卷名块自动忽略，只关心各卷内部的章节清单。
 *
 * 头部 TOC（tocAnchorLine 之后的章名块）可能被识别为 sub-TOC——这是允许的，
 * 因为头部 TOC 的章节在正文里同样存在，applyModeA 会成功匹配。
 */
internal fun detectSubTocBlocks(shortLineTexts: Map<Int, String>): List<SubTocBlock> {
    if (shortLineTexts.isEmpty()) return emptyList()
    val sortedLines = shortLineTexts.keys.sorted()
    val blocks = mutableListOf<SubTocBlock>()
    var currentEntries = mutableListOf<TocEntry>()
    var blockStartLine = -1
    var lastEntryLine = -1

    fun flushBlock() {
        if (currentEntries.size >= TOC_MODE_MIN_ENTRIES) {
            // V6：avgGap 过滤——真 sub-TOC 条目密集（间隔 ≤10 行），
            // 正文章节标题自然聚集（avgGap≥40）应被丢弃。
            val avgGap = if (currentEntries.size >= 2) {
                val totalGap = (1 until currentEntries.size).sumOf {
                    currentEntries[it].lineIdx - currentEntries[it - 1].lineIdx
                }
                totalGap.toDouble() / (currentEntries.size - 1)
            } else 0.0
            if (avgGap <= SUB_TOC_MAX_INTERNAL_AVG_GAP) {
                blocks.add(SubTocBlock(
                    startLine = blockStartLine,
                    endLine = lastEntryLine,
                    entries = currentEntries.toList()
                ))
            } else {
                Logger.i("$SCANNER_TAG: TOC-detect drop block[$blockStartLine..$lastEntryLine] " +
                    "entries=${currentEntries.size} avgGap=${"%.1f".format(avgGap)} " +
                    "> $SUB_TOC_MAX_INTERNAL_AVG_GAP (正文聚集非 TOC)")
            }
        }
        currentEntries = mutableListOf()
        blockStartLine = -1
        lastEntryLine = -1
    }

    for (lineIdx in sortedLines) {
        val text = shortLineTexts[lineIdx] ?: continue
        if (isTocLikeLine(text)) {
            // 块边界判据：lineIdx 间隔 > 阈值 → 当前块结束，开新块
            // （shortLineTexts 不含长正文行，必须靠 lineIdx 跳跃识别 sub-TOC 之间的间隔）
            if (blockStartLine >= 0 && lineIdx - lastEntryLine > SUB_TOC_MAX_LINE_GAP) {
                flushBlock()
            }
            if (blockStartLine < 0) blockStartLine = lineIdx
            // startByte 在 detectSubTocBlocks 阶段不可得（无 lineStartBytes），填 0L 占位；
            // applyModeA 内用 lineStartBytes[entry.lineIdx] 取真实值
            currentEntries.add(TocEntry(lineIdx, text.trim(), 0L))
            lastEntryLine = lineIdx
        } else {
            // 非 TOC 行（包括空行、卷名、短正文）：若块已开始且 lineIdx 跨度过大，
            // 上面的间隔判据会在下一次 isTocLikeLine 命中时触发；
            // 这里不需要主动断块——避免误把头部 TOC 中夹杂的空行当作块结束
        }
    }
    flushBlock()
    return blocks
}

/**
 * V6：镜像块检测——丢弃 TOC 复现（如三体.txt 尾部的二次完整目录）。
 *
 * 判据：若 block X 的前 [MIRROR_COMPARE_FIRST_N] 条 entry name 与某个更早 block Y 的
 * 前 N 条 entry name 完全相同（同序列），则 X 是 Y 的镜像（TOC 复现），丢弃 X。
 *
 * 真实场景：三体.txt 头部各卷 sub-TOC（三体I L112-192 + 三体II L5912-5926 + 三体III L15842-15862）
 * + 尾部完整二次目录（L25855-26063 + L26129-26235）。前 3 条 entry name 完全相同 → 镜像。
 *
 * 不影响 head TOC 与中嵌 sub-TOC：它们 entries 序列不同（如三体II sub-TOC 第一条是 "序 章"，
 * 三体I sub-TOC 第一条是 "第一章 科学边界"），不会触发镜像检测。
 */
internal fun filterMirrorBlocks(blocks: List<SubTocBlock>): List<SubTocBlock> {
    if (blocks.size < 2) return blocks
    val sorted = blocks.sortedBy { it.startLine }
    val dropped = BooleanArray(sorted.size)
    for (b in 1 until sorted.size) {
        if (dropped[b]) continue
        val keyB = sorted[b].entries
            .take(MIRROR_COMPARE_FIRST_N)
            .joinToString("|") { it.name }
        for (a in 0 until b) {
            if (dropped[a]) continue
            val keyA = sorted[a].entries
                .take(MIRROR_COMPARE_FIRST_N)
                .joinToString("|") { it.name }
            if (keyA == keyB && keyA.isNotEmpty()) {
                // b 是 a 的镜像（TOC 复现），丢弃 b
                dropped[b] = true
                Logger.i("$SCANNER_TAG: TOC-mirror drop block[L${sorted[b].startLine}..L${sorted[b].endLine}] " +
                    "as mirror of block[L${sorted[a].startLine}..L${sorted[a].endLine}] " +
                    "(first $MIRROR_COMPARE_FIRST_N entries identical: '$keyA')")
                break
            }
        }
    }
    return sorted.filterIndexed { idx, _ -> !dropped[idx] }
}

/**
 * Mode A 单 sub-TOC 驱动解析：用 sub-TOC 条目反向定位正文行，构造章节列表。
 *
 * **V6 关键修正：移除搜索窗口**。
 *
 * V5 曾用 `searchWindowLines = totalChars/subTocCount*2 / avgCharsPerLine` 限制每条 entry
 * 在 `entry.lineIdx+1 .. entry.lineIdx+window` 内查找同名正文行，意图作为"尾部 TOC 自然失效"
 * 的自洁机制。但实践证明这是错误设计：
 *   1. **窗口估算无可靠依据**：subTocCount 在有镜像块时虚高，估算偏小。
 *   2. **头部 sub-TOC 被误伤**：三体I 头部 sub-TOC 39 entries 跨 5000 行，窗口仅 3503 行 →
 *      第二十二章起全部漏匹配 → ratio=51% 被错误拒绝（V5 真机回归根因之一）。
 *   3. **尾部 TOC 不会自然失效**：尾部紧跟镜像 TOC（同序列章节列表），窗口内能找到同名行 →
 *      反而 ACCEPT，把所有章节定位到文件末尾（V5 真机回归另一根因）。
 *
 * **正确的"块是否可信"判据是结构性的，由前置阶段承担**：
 *   - [detectSubTocBlocks] 的 avgGap 过滤：真 sub-TOC entries 间距 ≤10，正文聚集 ≥40。
 *   - [filterMirrorBlocks] 的镜像检测：前 N 条 entry 同序列 → TOC 复现，丢弃。
 *
 * 这两个判据到位后，applyModeA 收到的每个 block 都是"可信 sub-TOC"，每条 entry 在
 * `[entry.lineIdx+1, 文件末尾]` 范围内查找首个同名短行即可——不需要窗口。
 *
 * 匹配规则：TOC 条目名（normalizeForExactMatch 后）等于正文行（normalizeForExactMatch 后），
 * 或作为正文行的前缀（覆盖"第一部 公元1453年5月，魔法师之死"这类正文行带额外描述的情况）。
 *
 * 末章 endLine 默认 = `lineCharCounts.lastIndex`，跨 block 衔接由 [fixChapterBounds] 修正。
 *
 * @return 接受则返回构造好的章节列表；匹配率过低则返回 null
 */
internal fun applyModeA(
    block: SubTocBlock,
    shortLineTexts: Map<Int, String>,
    lineStartBytes: List<Long>,
    lineCharCounts: List<Int>,
    fileSize: Long
): List<RawChapter>? {
    val entries = block.entries
    if (entries.size < TOC_MODE_MIN_ENTRIES) return null

    // 预排序短行 lineIdx，避免每条 entry 都 sort 一次
    val sortedShortLineIdxs = shortLineTexts.keys.sorted()

    val matches = mutableListOf<Pair<TocEntry, Int>>()   // (entry, matchLineIdx)
    for (entry in entries) {
        val target = normalizeForExactMatch(entry.name)
        if (target.isEmpty()) continue
        var foundLineIdx = -1
        // 从 entry.lineIdx+1 扫描到文件末尾，找首个同名短行（startsWith 覆盖正文带后缀的情况）
        for (lineIdx in sortedShortLineIdxs) {
            if (lineIdx <= entry.lineIdx) continue
            val lineText = shortLineTexts[lineIdx] ?: ""
            val normalized = normalizeForExactMatch(lineText)
            if (normalized == target || normalized.startsWith(target)) {
                foundLineIdx = lineIdx
                break
            }
        }
        if (foundLineIdx >= 0) matches.add(entry to foundLineIdx)
    }

    val matchedCount = matches.size
    val ratio = if (entries.isEmpty()) 0.0 else matchedCount.toDouble() / entries.size
    if (matchedCount < TOC_MODE_MIN_MATCH_COUNT || ratio < TOC_MODE_MIN_MATCH_RATIO) {
        Logger.i("$SCANNER_TAG: TOC-Mode A reject block[${block.startLine}..${block.endLine}] " +
            "entries=${entries.size} matched=$matchedCount ratio=${"%.2f".format(ratio)}")
        return null
    }
    Logger.i("$SCANNER_TAG: TOC-Mode A accept block[${block.startLine}..${block.endLine}] " +
        "entries=${entries.size} matched=$matchedCount ratio=${"%.2f".format(ratio)}")

    val lastLineIdx = lineCharCounts.lastIndex
    return matches.mapIndexed { i, (entry, lineIdx) ->
        val nextLineIdx = if (i + 1 < matches.size) matches[i + 1].second else lastLineIdx + 1
        val endLine = nextLineIdx - 1
        val startByte = lineStartBytes.getOrElse(lineIdx) { 0L }
        val endByte = if (i + 1 < matches.size)
            lineStartBytes.getOrElse(matches[i + 1].second) { fileSize }
        else fileSize
        RawChapter(
            name = entry.name,
            startByte = startByte,
            endByte = endByte,
            startLine = lineIdx,
            endLine = endLine,
            // sub-TOC 内的条目都视为章级别（卷信息由头部 TOC 卷名行承载，不进 Mode A/B 输出）
            isVolume = false,
            number = parseChapterNumber(entry.name)
        )
    }
}

// ── V5：卷名识别与卷首语保留 ──

/**
 * 卷名搜索窗口：sub-TOC 第一条 entry 之前多少行内查找卷名。
 *
 * 真实数据：三体.txt 中卷名"三体II·黑暗森林"在 lineIdx 5910，sub-TOC 第一条"序 章"在 lineIdx 5913，
 * gap=3。设 10 容忍 padding 行。
 */
private const val VOLUME_NAME_LOOKBACK_LINES = 10

/**
 * 为一个 accepted sub-TOC 块构造章节列表：先识别卷名章节（如有），再追加子章节。
 *
 * 卷名识别规则：在 sub-TOC 第一条 entry 之前的 [VOLUME_NAME_LOOKBACK_LINES] 行窗口内，
 * 找最近的"非空非 TOC 短行"——它是卷名（如"三体II·黑暗森林"）。
 *
 * 卷章节内容区间 = [卷名行 lineIdx, 第一个子章节匹配行 lineIdx)，即 sub-TOC 列表本身
 * 是卷章节的"正文"（用户明确要求：sub-TOC 列表是卷章节的内容）。
 *
 * @param block sub-TOC 块（原始检测的块）
 * @param subChapters applyModeA 返回的子章节列表（已匹配正文）
 * @param fileSize 文件大小（用于卷章节 endByte 兜底）
 * @return 卷章节（可选） + 子章节的列表，按 lineIdx 升序
 */
internal fun buildVolumeAndSubChapters(
    block: SubTocBlock,
    subChapters: List<RawChapter>,
    shortLineTexts: Map<Int, String>,
    lineStartBytes: List<Long>,
    fileSize: Long
): List<RawChapter> {
    if (subChapters.isEmpty()) return subChapters

    val firstSubLineIdx = subChapters.first().startLine
    // 在 sub-TOC 起点之前 VOLUME_NAME_LOOKBACK_LINES 行窗口内，找最近的非空非 TOC 短行
    val windowStart = (block.startLine - VOLUME_NAME_LOOKBACK_LINES).coerceAtLeast(0)
    val volumeEntry = shortLineTexts.entries
        .filter { it.key in windowStart until block.startLine }
        .filter { it.value.isNotBlank() && !isTocLikeLine(it.value) }
        .maxByOrNull { it.key }  // 最近的优先

    val result = mutableListOf<RawChapter>()
    if (volumeEntry != null) {
        val volumeLineIdx = volumeEntry.key
        val volumeName = volumeEntry.value.trim()
        // 卷章节区间：[卷名行, 第一个子章节匹配行) —— sub-TOC 列表本身是卷章节的"正文"
        val startByte = lineStartBytes.getOrElse(volumeLineIdx) { 0L }
        val endByte = lineStartBytes.getOrElse(firstSubLineIdx) { fileSize }
        result.add(RawChapter(
            name = volumeName,
            startByte = startByte,
            endByte = endByte,
            startLine = volumeLineIdx,
            endLine = firstSubLineIdx - 1,
            isVolume = true,
            number = null
        ))
        Logger.i("$SCANNER_TAG: TOC-Volume '$volumeName' at lineIdx=$volumeLineIdx " +
            "covers [$volumeLineIdx, $firstSubLineIdx)")
    }
    result.addAll(subChapters)
    return result
}

/**
 * 若首章 startLine > 0，插入一个"卷首语"章节覆盖 [0, 首章.startLine - 1]。
 *
 * 章节名取该范围内第一个非空短行的 trim 文本（如三体.txt 的"目录"）；
 * 找不到时 fallback "序言"。
 *
 * 用户要求：保留头部 TOC 之前的卷首语/作者寄语/版权页等内容，章节名根据实际内容填充。
 */
internal fun insertPrefaceIfNeeded(
    chapters: List<RawChapter>,
    shortLineTexts: Map<Int, String>,
    lineStartBytes: List<Long>
): List<RawChapter> {
    if (chapters.isEmpty()) return chapters
    val first = chapters.first()
    if (first.startLine <= 0) return chapters

    // 在 [0, first.startLine) 内找首个非空短行作为章节名
    val prefaceEntry = shortLineTexts.entries
        .filter { it.key in 0 until first.startLine }
        .filter { it.value.isNotBlank() }
        .minByOrNull { it.key }

    val prefaceName = prefaceEntry?.value?.trim() ?: "序言"
    val prefaceLineIdx = prefaceEntry?.key ?: 0
    val preface = RawChapter(
        name = prefaceName,
        startByte = 0L,
        endByte = first.startByte,
        startLine = 0,
        endLine = first.startLine - 1,
        isVolume = false,
        number = null
    )
    Logger.i("$SCANNER_TAG: TOC-Preface '$prefaceName' at lineIdx=$prefaceLineIdx " +
        "covers [0, ${first.startLine})")
    return listOf(preface) + chapters
}

/**
 * V6：跨 block 衔接处的 endLine / endByte 修正。
 *
 * **背景**：applyModeA 单 block 输出时，末章 endLine 默认 = `lineCharCounts.lastIndex`（文件末行），
 * endByte 默认 = `fileSize`。单 block 场景这正确；但 Mode B 多 block 合并后，前面 block 的末章
 * 会跨越到文件末尾，导致 wordCount 跨越整个文件。
 *
 * **修正**：按 startLine 升序遍历，每章 endLine = 下一章 startLine - 1；末章 endLine = totalLines - 1。
 * 同时 endByte = 下一章 startByte；末章 endByte 保持原值（应已是 fileSize）。
 *
 * **幂等**：单 block 场景下，原 endLine 已是 lastLineIdx，重算后 endLine = next.startLine - 1，
 * 结果一致（最后一章 endLine 仍 = lastLineIdx）。Mode C 不调用本函数，行为不变。
 *
 * @param totalLines 文件总行数（= lineCharCounts.size），用于末章 endLine 兜底
 */
internal fun fixChapterBounds(chapters: List<RawChapter>, totalLines: Int): List<RawChapter> {
    if (chapters.size < 2) return chapters
    return chapters.mapIndexed { i, ch ->
        if (i + 1 < chapters.size) {
            val next = chapters[i + 1]
            ch.copy(endLine = (next.startLine - 1).coerceAtLeast(ch.startLine),
                endByte = next.startByte)
        } else {
            // 末章：endLine 兜底为 totalLines - 1（applyModeA 默认已是 lastLineIdx）
            ch.copy(endLine = ch.endLine.coerceAtMost(totalLines - 1))
        }
    }
}

@Singleton
class ChapterScanner @Inject constructor(
    private val txtCharsetDetector: TxtCharsetDetector
) {

    private val nativeAvailable by lazy { NativeLib.tryLoad() }

    private fun matchChapterTitle(title: String): Int {
        return if (nativeAvailable) {
            try {
                NativeLib.matchChapterTitle(title)
            } catch (e: Exception) {
                Logger.w("$SCANNER_TAG: native matchChapterTitle failed: ${e.message}")
                0
            }
        } else 0
    }

    suspend fun scan(bookId: Long, cachedFile: CachedFile): ScanResult {
        if (!cachedFile.canAccess()) {
            throw IllegalStateException("Cannot access file: ${cachedFile.name}")
        }

        val headerBytes = cachedFile.openInputStream()?.use { input ->
            val buf = ByteArray(BUF_SIZE)
            val n = input.read(buf)
            if (n > 0) buf.copyOf(n) else ByteArray(0)
        } ?: ByteArray(0)

        // ★ 二进制守卫：检测文件头是否为常见二进制格式（JPEG/PNG/PDF/ZIP/MOBI 等）。
        // 命中则抛 NotTextFileException——典型场景：来源不可靠的 .txt 实为图片或其他电子书格式，
        // 强行按文本解码会产生大段乱码。由 TxtTextParser.parseChapterInfo 上抛、MainReadViewModel
        // 映射到 BookReaderUiState.Error 向用户提示「不是文本文件」。
        BinaryMagicNumberDetector.detect(headerBytes)?.let { detectedType ->
            throw NotTextFileException(cachedFile.name, detectedType)
        }

        val charsetResult = txtCharsetDetector.detect(headerBytes)

        return if (charsetResult.isUtf16Or32) {
            scanWithByteOffsetsUtf16Or32(bookId, cachedFile, charsetResult.charsetName)
        } else {
            scanWithByteOffsets(bookId, cachedFile, charsetResult.charsetName)
        }
    }

    // ── UTF-16/32 path: byte-level code-unit scan mirroring scanWithByteOffsets ──
    //
    // v4 终审核心修正：放弃 "CountingInputStream + Reader 双轨" 设计——InputStreamReader
    // 内部 8 KiB 解码缓冲会让 cis.count 领先解码光标数千字节，行首字节快照毫无意义。
    // 改为直接读原始字节、按定宽码元匹配完整 LF 字节模式、逐行 String(bytes, charset) 解码。
    // 详见 docs/plans/plan-txt-unify-byte-offset.md §3.1.2 / §3.1.3。

    private suspend fun scanWithByteOffsetsUtf16Or32(
        bookId: Long,
        cachedFile: CachedFile,
        charsetName: String
    ): ScanResult {
        val charset = Charset.forName(charsetName)

        // 定宽码元参数：unit=码元字节数；bomLen=BOM 字节数；isLf=完整码元 LF 判定
        // UTF-16LE/UTF-32LE 等显式端序名不剥 BOM，必须手动跳过前 2/4 字节，
        // 否则 String(bytes, "UTF-16LE") 会把 BOM 解成 U+FEFF 污染首行。
        val unit: Int
        val bomLen: Int
        val isLf: (ByteArray, Int) -> Boolean
        when (charsetName) {
            "UTF-16LE" -> {
                unit = 2; bomLen = 2
                isLf = { b, o -> (b[o].toInt() and 0xFF) == 0x0A && (b[o + 1].toInt() and 0xFF) == 0x00 }
            }
            "UTF-16BE" -> {
                unit = 2; bomLen = 2
                isLf = { b, o -> (b[o].toInt() and 0xFF) == 0x00 && (b[o + 1].toInt() and 0xFF) == 0x0A }
            }
            "UTF-32LE" -> {
                unit = 4; bomLen = 4
                isLf = { b, o -> (b[o].toInt() and 0xFF) == 0x0A && (b[o + 1].toInt() and 0xFF) == 0x00 &&
                    (b[o + 2].toInt() and 0xFF) == 0x00 && (b[o + 3].toInt() and 0xFF) == 0x00 }
            }
            "UTF-32BE" -> {
                unit = 4; bomLen = 4
                isLf = { b, o -> (b[o].toInt() and 0xFF) == 0x00 && (b[o + 1].toInt() and 0xFF) == 0x00 &&
                    (b[o + 2].toInt() and 0xFF) == 0x00 && (b[o + 3].toInt() and 0xFF) == 0x0A }
            }
            // 不应到达：scan() 仅在 isUtf16Or32=true 时调本方法
            else -> {
                unit = 1; bomLen = 0
                isLf = { b, o -> (b[o].toInt() and 0xFF) == 0x0A }
            }
        }

        val raw = mutableListOf<RawChapter>()
        val lineCharCounts = mutableListOf<Int>()
        val lineStartBytes = mutableListOf<Long>()
        // TOC-Driven 用：仅记录 ≤TOC_LINE_MAX_LEN 字符的短行，供 postProcess 检测 sub-TOC
        val shortLineTexts = mutableMapOf<Int, String>()

        var curName = ""
        var curNumber: Int? = null          // 当前章节号（parseChapterNumber 反查得来）
        var curStartLine = 0
        var curStartByte = bomLen.toLong()
        var totalChars = 0
        var lineIdx = 0
        var lineStartByte = bomLen.toLong()    // 首行起点 = BOM 之后
        var tocAnchorLine = -1              // 目录/Contents 锚点行号，-1 未发现

        val inputStream = cachedFile.openInputStream()
            ?: throw IllegalStateException("Cannot open file: ${cachedFile.name}")

        CountingInputStream(inputStream).use { cis ->
            val buf = ByteArray(BUF_SIZE)
            val lineBuf = ByteArrayOutputStream()

            // 跳过并丢弃 BOM。cis.count 递增到 bomLen，lineStartByte 初始化正确
            if (bomLen > 0) {
                var skipped = 0
                while (skipped < bomLen) {
                    val s = cis.read(buf, 0, bomLen - skipped)
                    if (s <= 0) break
                    skipped += s
                }
            }

            // carry：上一次 read 末尾不足以构成一个完整码元的残余字节
            // （码元跨两次 read() 缓冲时发生，64 KiB BUF_SIZE 下依然可能）
            var carry = 0

            while (true) {
                // 拆成两步读，避免 `cis.read(...) + carry` 在 EOF 返回 -1 时被误算成 `carry - 1`
                // （会把最后一次的残余字节悄悄丢掉）。先读新字节到 buf[carry..]，再合并 carry 计数
                val newRead = cis.read(buf, carry, buf.size - carry)
                if (newRead <= 0) {
                    // EOF：本次没有新字节，但 buf[0..<carry] 里可能还有残余码元，
                    // 交给循环后的"无 LF 结尾的最后一行"分支处理（下方 lineBuf.size() > 0 分支）
                    break
                }
                val n = carry + newRead   // 本次循环处理的字节总数（含上次残余）

                var i = 0
                while (i + unit <= n) {
                    if (isLf(buf, i)) {
                        val lfStart = cis.count - n + i           // 本码元首字节绝对偏移
                        val byteEnd = lfStart + unit - 1           // 包含 LF 码元末字节
                        // v2 修复（review §一阶遗漏 #7）：单行字节超限截断，防御 OOM
                        truncateLineBufIfTooLong(lineBuf, lineIdx)
                        val lineBytes = lineBuf.toByteArray()
                        lineBuf.reset()
                        val line = String(lineBytes, charset).trimEnd('\r')   // CR 码元已进 lineBuf，解码后剥掉

                        val cc = if (line.isNotBlank()) line.length else 0
                        lineCharCounts.add(cc)
                        lineStartBytes.add(lineStartByte)
                        totalChars += cc

                        val clean = line.clearAllMarkdown().trim()
                        // TOC-Driven：累积短行供 postProcess 检测 sub-TOC（≤TOC_LINE_MAX_LEN 字符）
                        if (clean.length <= TOC_LINE_MAX_LEN) shortLineTexts[lineIdx] = clean
                        // TOC 锚点识别（仅前 TOC_ANCHOR_MAX_LINES 行；5% 位置约束在 filterToc 内做）
                        if (tocAnchorLine < 0 && lineIdx < TOC_ANCHOR_MAX_LINES &&
                            (clean == "目录" || clean.equals("Contents", ignoreCase = true))) {
                            tocAnchorLine = lineIdx
                        }
                        if (clean.isNotEmpty() && isChapterCandidate(clean)) {
                            val r = matchChapterTitle(clean)
                            if (r != 0) {
                                if (lineIdx > curStartLine) {
                                    raw.add(RawChapter(curName, curStartByte, byteEnd,
                                        curStartLine, lineIdx - 1, r == 2, curNumber))
                                }
                                curName = clean
                                curNumber = parseChapterNumber(clean)
                                curStartLine = lineIdx; curStartByte = lineStartByte
                            }
                        }

                        lineStartByte = lfStart + unit            // 下一行首字节 = LF 码元之后
                        lineIdx++
                        if (lineIdx % 1000 == 0) yield()
                        i += unit
                    } else {
                        lineBuf.write(buf, i, unit)               // 非换行：整码元写入行缓冲
                        i += unit
                    }
                }
                // 末尾残余码元搬回缓冲区头部，下次 read() 续上
                if (i < n) {
                    System.arraycopy(buf, i, buf, 0, n - i)
                    carry = n - i
                } else {
                    carry = 0
                }
            }

            // 文件末尾没有 LF 结尾的最后一行（镜像 UTF-8 路径 L212-230）
            if (lineBuf.size() > 0) {
                // v2 修复（review §一阶遗漏 #7）：单行字节超限截断，防御 OOM
                truncateLineBufIfTooLong(lineBuf, lineIdx)
                val lineBytes = lineBuf.toByteArray()
                val line = String(lineBytes, charset).trimEnd('\r')
                val cc = if (line.isNotBlank()) line.length else 0
                lineCharCounts.add(cc)
                lineStartBytes.add(lineStartByte)
                totalChars += cc

                val clean = line.clearAllMarkdown().trim()
                if (clean.length <= TOC_LINE_MAX_LEN) shortLineTexts[lineIdx] = clean
                if (tocAnchorLine < 0 && lineIdx < TOC_ANCHOR_MAX_LINES &&
                    (clean == "目录" || clean.equals("Contents", ignoreCase = true))) {
                    tocAnchorLine = lineIdx
                }
                if (clean.isNotEmpty() && isChapterCandidate(clean)) {
                    val r = matchChapterTitle(clean)
                    if (r != 0) {
                        if (lineIdx > curStartLine) {
                            raw.add(RawChapter(curName, curStartByte, cis.count,
                                curStartLine, lineIdx - 1, r == 2, curNumber))
                        }
                    }
                }
                lineIdx++
            }
        }

        // 末尾兜底：补提交最后一个未闭合章节（首章卷标记修复延后到独立 PR，此处仍 isVolume=false）
        if (curName.isNotEmpty()) {
            raw.add(RawChapter(curName, curStartByte, cachedFile.size,
                curStartLine, max(0, lineIdx - 1), false, curNumber))
        }

        val finalChapters = postProcess(
            raw, lineCharCounts, lineStartBytes, totalChars, tocAnchorLine, cachedFile.size, shortLineTexts
        )

        val offsets = buildOffsets(finalChapters, cachedFile.size)
        return buildResult(bookId, charsetName, finalChapters, isUtf16Or32 = true,
            offsets = offsets, lineCharCounts = lineCharCounts)
    }

    // ── Normal path: byte-level 0x0A scan with offset tracking ──

    private suspend fun scanWithByteOffsets(
        bookId: Long,
        cachedFile: CachedFile,
        charsetName: String
    ): ScanResult {
        val charset = Charset.forName(charsetName)
        val raw = mutableListOf<RawChapter>()
        val lineCharCounts = mutableListOf<Int>()
        val lineStartBytes = mutableListOf<Long>()
        // TOC-Driven 用：仅记录 ≤TOC_LINE_MAX_LEN 字符的短行，供 postProcess 检测 sub-TOC
        val shortLineTexts = mutableMapOf<Int, String>()

        var curName = ""
        var curNumber: Int? = null
        var curStartLine = 0
        var curStartByte = 0L
        var totalChars = 0
        var lineIdx = 0
        var lineStartByte = 0L
        var tocAnchorLine = -1

        val inputStream = cachedFile.openInputStream()
            ?: throw IllegalStateException("Cannot open file: ${cachedFile.name}")

        CountingInputStream(inputStream).use { cis ->
            val buf = ByteArray(BUF_SIZE)
            val lineBuf = ByteArrayOutputStream()

            while (true) {
                val n = cis.read(buf)
                if (n <= 0) break

                for (i in 0 until n) {
                    if (buf[i] == 0x0A.toByte()) {
                        val byteEnd = cis.count - n + i
                        // v2 修复（review §一阶遗漏 #7）：单行字节超限截断，防御 OOM
                        truncateLineBufIfTooLong(lineBuf, lineIdx)
                        val lineBytes = lineBuf.toByteArray()
                        lineBuf.reset()
                        val line = String(lineBytes, charset).trimEnd('\r')

                        val cc = if (line.isNotBlank()) line.length else 0
                        lineCharCounts.add(cc)
                        lineStartBytes.add(lineStartByte)
                        totalChars += cc

                        val clean = line.clearAllMarkdown().trim()
                        // TOC-Driven：累积短行供 postProcess 检测 sub-TOC（≤TOC_LINE_MAX_LEN 字符）
                        if (clean.length <= TOC_LINE_MAX_LEN) shortLineTexts[lineIdx] = clean
                        if (tocAnchorLine < 0 && lineIdx < TOC_ANCHOR_MAX_LINES &&
                            (clean == "目录" || clean.equals("Contents", ignoreCase = true))) {
                            tocAnchorLine = lineIdx
                        }
                        if (clean.isNotEmpty() && isChapterCandidate(clean)) {
                            val r = matchChapterTitle(clean)
                            if (r != 0) {
                                if (lineIdx > curStartLine) {
                                    raw.add(RawChapter(curName, curStartByte, byteEnd,
                                        curStartLine, lineIdx - 1, r == 2, curNumber))
                                }
                                curName = clean
                                curNumber = parseChapterNumber(clean)
                                curStartLine = lineIdx; curStartByte = lineStartByte
                            }
                        }

                        lineStartByte = cis.count - n + i + 1
                        lineIdx++
                        if (lineIdx % 1000 == 0) yield()
                    } else {
                        lineBuf.write(buf[i].toInt())
                    }
                }
            }

            if (lineBuf.size() > 0) {
                // v2 修复（review §一阶遗漏 #7）：单行字节超限截断，防御 OOM
                truncateLineBufIfTooLong(lineBuf, lineIdx)
                val lineBytes = lineBuf.toByteArray()
                val line = String(lineBytes, charset).trimEnd('\r')
                val cc = if (line.isNotBlank()) line.length else 0
                lineCharCounts.add(cc)
                lineStartBytes.add(lineStartByte)
                totalChars += cc

                val clean = line.clearAllMarkdown().trim()
                if (clean.length <= TOC_LINE_MAX_LEN) shortLineTexts[lineIdx] = clean
                if (tocAnchorLine < 0 && lineIdx < TOC_ANCHOR_MAX_LINES &&
                    (clean == "目录" || clean.equals("Contents", ignoreCase = true))) {
                    tocAnchorLine = lineIdx
                }
                if (clean.isNotEmpty() && isChapterCandidate(clean)) {
                    val r = matchChapterTitle(clean)
                    if (r != 0) {
                        if (lineIdx > curStartLine) {
                            raw.add(RawChapter(curName, curStartByte, cis.count,
                                curStartLine, lineIdx - 1, r == 2, curNumber))
                        }
                    }
                }
                lineIdx++
            }
        }

        if (curName.isNotEmpty()) {
            raw.add(RawChapter(curName, curStartByte, lineStartByte,
                curStartLine, max(0, lineIdx - 1), false, curNumber))
        }

        val finalChapters = postProcess(
            raw, lineCharCounts, lineStartBytes, totalChars, tocAnchorLine, cachedFile.size, shortLineTexts
        )

        val offsets = buildOffsets(finalChapters, cachedFile.size)
        return buildResult(bookId, charsetName, finalChapters, isUtf16Or32 = false,
            offsets = offsets, lineCharCounts = lineCharCounts)
    }

    // ── Post-processing pipeline ──
    //
    // 优先级：TOC-Driven（Mode A/B）→ 4-Pass 兜底（Mode C：filterToc → applyDedup → avgGap）。
    // 前缀和（prefix）算一次，复用给 filterToc 与 applyDedup，避免 O(n) 重复扫描。
    //
    // Mode A/B 在前置短路段执行：命中即返回；未命中继续走 Mode C（Mode C 代码与改造前完全一致）。
    // 详见 docs/plans/plan-toc-driven-mode-a-b.md。

    private fun postProcess(
        raw: List<RawChapter>,
        lineCharCounts: List<Int>,
        lineStartBytes: List<Long>,
        totalChars: Int,
        tocAnchorLine: Int,
        fileSize: Long,
        shortLineTexts: Map<Int, String>
    ): List<RawChapter> {
        val prefix = LongArray(lineCharCounts.size)
        var acc = 0L
        for (i in lineCharCounts.indices) { acc += lineCharCounts[i]; prefix[i] = acc }

        // === TOC-Driven 短路（Mode A/B）===
        // 检测所有 sub-TOC 块，逐个跑 applyModeA；按接受块数决策模式。
        // V6：detectSubTocBlocks 内置 avgGap 过滤（丢弃正文聚集假阳性），
        //     filterMirrorBlocks 丢弃 TOC 复现（如尾部二次目录镜像头部 sub-TOC）。
        val rawBlocks = detectSubTocBlocks(shortLineTexts)
        val subTocBlocks = filterMirrorBlocks(rawBlocks)
        if (rawBlocks.size != subTocBlocks.size) {
            Logger.i("$SCANNER_TAG: TOC-block filter ${rawBlocks.size} -> ${subTocBlocks.size} " +
                "(avgGap+mirror)")
        }
        // V5：保留 block 与 accepted 的对应关系，便于卷名章节插入
        val acceptedWithBlock = subTocBlocks.mapNotNull { block ->
            applyModeA(block, shortLineTexts, lineStartBytes, lineCharCounts, fileSize)
                ?.let { block to it }
        }
        when {
            // Mode B：多个 sub-TOC 接受（如三体.txt 三卷各有 sub-TOC）→ 卷名章节 + 子章节 + applyDedup
            acceptedWithBlock.size >= 2 -> {
                Logger.i("$SCANNER_TAG: TOC-Mode B accepted=${acceptedWithBlock.size} blocks " +
                    "(anchor=$tocAnchorLine, detected=${subTocBlocks.size})")
                val withVolumes = acceptedWithBlock.flatMap { (block, chapters) ->
                    buildVolumeAndSubChapters(block, chapters, shortLineTexts, lineStartBytes, fileSize)
                }.sortedBy { it.startLine }
                val withPreface = insertPrefaceIfNeeded(withVolumes, shortLineTexts, lineStartBytes)
                val deduped = applyDedup(withPreface, prefix)
                // V6：跨 block 衔接修正——每个 block 的末章 endLine 默认 = 文件末行，
                // 多 block 合并后会导致 wordCount 跨越整个文件。重算 endLine = 下一章 startLine - 1。
                return fixChapterBounds(deduped, lineCharCounts.size)
            }
            // Mode A：单个 sub-TOC 接受
            acceptedWithBlock.size == 1 -> {
                Logger.i("$SCANNER_TAG: TOC-Mode A accepted=1 block " +
                    "(anchor=$tocAnchorLine, detected=${subTocBlocks.size})")
                val (block, chapters) = acceptedWithBlock[0]
                val withVolume = buildVolumeAndSubChapters(block, chapters, shortLineTexts, lineStartBytes, fileSize)
                val withPreface = insertPrefaceIfNeeded(withVolume, shortLineTexts, lineStartBytes)
                val deduped = applyDedup(withPreface, prefix)
                return fixChapterBounds(deduped, lineCharCounts.size)
            }
            // Mode C：无 sub-TOC 接受 → 回落到现有 4-Pass 流水线（下方代码零改动）
            else -> {
                Logger.i("$SCANNER_TAG: TOC-Mode C fallback " +
                    "(reason: 0 accepted, blocks=${subTocBlocks.size}, anchor=$tocAnchorLine)")
            }
        }

        // === Mode C：现有 4-Pass 流水线（与改造前一致）===
        val beforeTocCount = raw.size
        val tocFiltered = filterToc(raw, prefix, totalChars, tocAnchorLine)
        val tocStrippedCount = beforeTocCount - tocFiltered.size

        val deduped = applyDedup(tocFiltered, prefix)

        val avgGap = if (deduped.isNotEmpty()) totalChars / deduped.size else 0

        return when {
            deduped.isEmpty() -> {
                Logger.w("$SCANNER_TAG: empty after dedup, uniform split fallback")
                uniformSplit(lineCharCounts, lineStartBytes, totalChars, fileSize)
            }
            // TOC 剥离成功，信任结果（即使 avgGap 偏大也不切分）
            tocStrippedCount >= 3 && avgGap > AVG_GAP_MAX -> {
                Logger.i("$SCANNER_TAG: TOC stripped $tocStrippedCount items, keep dedup result")
                deduped
            }
            // 候选数 > 50 说明是短章节书，不是误判
            avgGap < AVG_GAP_MIN && deduped.size > 50 -> {
                Logger.i("$SCANNER_TAG: short chapters (count=${deduped.size}), keep dedup result")
                deduped
            }
            avgGap < AVG_GAP_MIN || avgGap > AVG_GAP_MAX -> {
                Logger.w("$SCANNER_TAG: avgGap=$avgGap out of bounds, uniform split fallback")
                uniformSplit(lineCharCounts, lineStartBytes, totalChars, fileSize)
            }
            else -> deduped
        }
    }


    // ── Post-processing ──

    /**
     * 顺位感知去重。规则（按优先级）：
     *   1. curr.number == prev.number + 1 → 必保留（豁免距离，解决连续短章节被吞）
     *   2. 近距离（< [DEDUP_MIN_LINE_GAP] 行 OR < [DEDUP_CHAR_GAP] 字符）才进入去重判断
     *   3. 近距离 + 同号 → 真重复识别（markdown/空白差异），丢弃 curr；
     *      若 curr 是卷而 prev 是章，则用卷替换章
     *   4. 近距离 + 其他情况（无 number，或 number 跳跃）→ 保留
     *
     * 规则 4 不再保留 v2 的「无数字兜底丢弃」：matcher 边界已核实精确
     * （matchChapterRe 单位字仅 章/节/卷 + 尾部约束），不存在「对话误判」风险。
     *
     * @param prefix 调用方算好的行字符数前缀和（[postProcess] 复用），空数组则只按行距判距离
     */
    internal fun applyDedup(
        matches: List<RawChapter>,
        prefix: LongArray = LongArray(0)
    ): List<RawChapter> {
        if (matches.isEmpty()) return matches

        // charGap 边界语义：返回 [prev.endLine + 1, curr.startLine - 1] 闭区间内的字符数
        fun charGap(prev: RawChapter, curr: RawChapter): Long {
            if (prefix.isEmpty()) return Long.MAX_VALUE
            val fromLine = (prev.endLine + 1).coerceAtLeast(0)
            val toLine = (curr.startLine - 1).coerceAtLeast(0)
            if (toLine < fromLine) return 0L  // 相邻行，无间隙
            val hi = prefix[toLine.coerceAtMost(prefix.lastIndex)]
            val lo = if (fromLine > 0) prefix[(fromLine - 1).coerceAtMost(prefix.lastIndex)] else 0L
            return hi - lo
        }

        // 双阈值 OR 语义：< 3 行 OR < 150 字符
        fun isClose(prev: RawChapter, curr: RawChapter): Boolean {
            if (curr.startLine - prev.endLine < DEDUP_MIN_LINE_GAP) return true
            if (prefix.isNotEmpty() && charGap(prev, curr) < DEDUP_CHAR_GAP) return true
            return false
        }

        val result = mutableListOf<RawChapter>()
        result.add(matches.first())
        for (i in 1 until matches.size) {
            val prev = result.last()
            val curr = matches[i]

            // 规则 1：数字连续递增 → 必保留（解决连续短章节被吞）
            if (curr.number != null && prev.number != null &&
                curr.number == prev.number + 1) {
                result.add(curr); continue
            }

            // 规则 2：近距离才进入去重判断
            if (!isClose(prev, curr)) {
                result.add(curr); continue
            }

            // 规则 3：近距离 + 同号 → 真重复识别，丢弃 curr（卷替换章除外）
            if (curr.number != null && prev.number != null &&
                curr.number == prev.number) {
                if (!prev.isVolume && curr.isVolume) {
                    result[result.lastIndex] = curr
                }
                continue
            }

            // 规则 4：近距离 + 其他情况 → 保留
            result.add(curr)
        }
        return result
    }

    /**
     * TOC 检测：识别并剥离文件开头的目录区域。
     *
     * 信号 A（主）：第一个重复章节名（带 5% 间隔约束，防御番外/上下篇重置误判）
     * 信号 B（辅助）：[tocAnchorLine] 须在前 5% 位置；
     *                仅用于「修正信号 A 找到的边界」，不独立判定
     *
     * @param prefix 行字符数前缀和（用于计算行字符偏移）
     * @param totalChars 文件总字符数（扫描期累加得到）
     * @param tocAnchorLine 扫描期记录的「目录」/「Contents」锚点行号，-1 未发现
     */
    internal fun filterToc(
        matches: List<RawChapter>,
        prefix: LongArray,
        totalChars: Int,
        tocAnchorLine: Int
    ): List<RawChapter> {
        if (matches.size < 2) return matches

        fun charOffsetOfLine(line: Int): Long =
            if (line in prefix.indices) prefix[line]
            else if (prefix.isNotEmpty()) prefix.last() else 0L

        // ===== 信号 A：第一个重复章节名（带 5% 间隔约束）=====
        val namePositions = mutableMapOf<String, MutableList<Int>>()
        matches.forEachIndexed { idx, ch ->
            val n = normalizeChapterName(ch.name)
            namePositions.getOrPut(n) { mutableListOf() }.add(idx)
        }
        val maxRegionChars = (totalChars * 0.05).toLong()

        var signalATocStart = -1
        var signalABodyStart = -1
        for ((_, positions) in namePositions) {
            if (positions.size < 2) continue
            val first = positions[0]
            val second = positions[1]
            val firstOff = charOffsetOfLine(matches[first].startLine)
            val secondOff = charOffsetOfLine(matches[second].startLine)
            if (secondOff - firstOff > maxRegionChars) continue  // 间隔过大非 TOC
            if (signalATocStart == -1 || first < signalATocStart) {
                signalATocStart = first
                signalABodyStart = second
            }
        }

        if (signalATocStart != -1) {
            val effectiveTocStart = refineTocStartWithAnchor(
                matches, prefix, totalChars,
                tocAnchorLine, signalATocStart, signalABodyStart
            )
            return matches.filterIndexed { idx, _ ->
                idx < effectiveTocStart || idx >= signalABodyStart
            }
        }

        // 信号 A 未命中时，信号 B 不独立触发
        return matches
    }

    /**
     * 信号 B 辅助修正：若 [tocAnchorLine] 在前 5% 位置且早于 [signalATocStart]，
     * 将 TOC 起点修正为 anchor 之后第一个候选（包含锚点紧邻的标题）。
     * 若 tocAnchorLine 不在前 5%，视为无效锚点，不修正。
     *
     * ★ raw[0] 保留约束（修复「三体.txt 前置寄语/版权丢失」回归）：
     *   raw[0] 是扫描循环在遇到第一个章节候选之前累积的内容载体，覆盖了
     *   `[文件开头, 第一个章节标题)` 的所有文本——前置正文（作者寄语、版权、感言、
     *   目录锚点本身等）。即便 anchor 落在 raw[0] 覆盖范围内（tocAnchorLine=0 的常见情况），
     *   也不能把 raw[0] 划进 TOC 剥离范围——那样会丢掉所有前置正文。
     *
     *   实测回归：三体.txt 第 0 行「目录」→ anchorIdx=0 → effectiveTocStart=0 →
     *   raw[0]（无名首章，承载寄语+版权+感言+目录锚点）被剥，最终首章直跳正文第一章，
     *   前置内容全部无法读取。
     *
     *   规则：effectiveTocStart 下界为 1，永远保留 raw[0]。
     *   例外：signalATocStart 本身为 0 时，说明 raw[0] 就是第一个重复章节
     *   （文件开头直接是 TOC 第一项，无前置正文），此时允许 effectiveTocStart=0。
     */
    private fun refineTocStartWithAnchor(
        matches: List<RawChapter>,
        prefix: LongArray,
        totalChars: Int,
        tocAnchorLine: Int,
        signalATocStart: Int,
        signalABodyStart: Int
    ): Int {
        if (tocAnchorLine < 0) return signalATocStart

        fun charOffsetOfLine(line: Int): Long =
            if (line in prefix.indices) prefix[line]
            else if (prefix.isNotEmpty()) prefix.last() else 0L

        val anchorOff = charOffsetOfLine(tocAnchorLine)
        if (anchorOff > totalChars * 0.05) return signalATocStart  // 锚点不在前 5%，无效

        val bodyOff = charOffsetOfLine(matches[signalABodyStart].startLine)
        if (anchorOff >= bodyOff) return signalATocStart  // 锚点在正文之后，无意义

        val anchorIdx = matches.indexOfFirst { it.startLine >= tocAnchorLine }
        val refined = if (anchorIdx in 0 until signalATocStart) anchorIdx else signalATocStart
        // raw[0] 保留约束：effectiveTocStart 下界 1（除非 signalATocStart 本身就是 0）
        return if (signalATocStart > 0) refined.coerceAtLeast(1) else refined
    }

    internal fun uniformSplit(
        lineCharCounts: List<Int>,
        lineStartBytes: List<Long>,
        totalChars: Int,
        fileSize: Long                  // ← 新增：修复原 endByte=0L bug（UTF-8 路径未暴露，但统一字节偏移后两条路径都被下游 readByByteRange 使用）
    ): List<RawChapter> {
        if (lineCharCounts.isEmpty()) return emptyList()

        val prefix = LongArray(lineCharCounts.size)
        var acc = 0L
        for (i in lineCharCounts.indices) { acc += lineCharCounts[i]; prefix[i] = acc }

        val segCount = max(1, totalChars / UNIFORM_SPLIT_CHARS)
        val splits = mutableListOf(0)

        for (seg in 1 until segCount) {
            val target = seg.toLong() * UNIFORM_SPLIT_CHARS
            val lo = max(0, target - 5000)
            val hi = min(prefix.last(), target + 5000)
            val startLine = firstGeq(prefix, lo)
            val endLine = firstGeq(prefix, hi)
            val best = pickBoundary(lineCharCounts, prefix, startLine, endLine, target)
            if (best > splits.last() && best < lineCharCounts.size) {
                splits.add(best)
            }
        }

        return splits.mapIndexed { i, sl ->
            val el = if (i + 1 < splits.size) splits[i + 1] - 1 else lineCharCounts.size - 1
            RawChapter(
                "${i + 1}",
                lineStartBytes.getOrElse(sl) { 0L },
                lineStartBytes.getOrElse(el + 1) { fileSize },   // ← 下一章起点或文件末尾（原 0L bug 修复）
                sl, max(sl, el), false
            )
        }
    }

    private fun firstGeq(prefix: LongArray, target: Long): Int {
        var lo = 0; var hi = prefix.size - 1
        while (lo < hi) { val m = (lo + hi) / 2; if (prefix[m] >= target) hi = m else lo = m + 1 }
        return lo
    }

    private fun pickBoundary(
        lineCharCounts: List<Int>,
        prefix: LongArray,
        startLine: Int,
        endLine: Int,
        target: Long
    ): Int {
        var bestLine = startLine
        var bestDist = Int.MAX_VALUE
        for (l in startLine..endLine) {
            val dist = abs(prefix[l] - target).toInt()
            if (dist < bestDist) {
                bestDist = dist; bestLine = l
            }
        }
        return bestLine
    }

    // ── Result build ──

    private fun buildOffsets(chapters: List<RawChapter>, fileSize: Long): LongArray {
        val offsets = LongArray(chapters.size + 1) {
            if (it < chapters.size) chapters[it].startByte else fileSize
        }
        return offsets
    }

    private fun buildResult(
        bookId: Long,
        charsetName: String,
        matches: List<RawChapter>,
        isUtf16Or32: Boolean,
        offsets: LongArray?,
        lineCharCounts: List<Int> = emptyList()
    ): ScanResult {
        val prefixSums = LongArray(lineCharCounts.size).also {
            var acc = 0L
            for (i in lineCharCounts.indices) { acc += lineCharCounts[i]; it[i] = acc }
        }

        // ★ v5 关键正确性：chapterUrl 的 endByte 必须按"下一章 startByte"推导，**不能**用
        // RawChapter.endByte。原因：RawChapter.endByte 在扫描循环里记录的是"触发本章节闭合的那条
        // 标题行的 LF 字节偏移"——即下一章标题的 LF，而非本章节内容的真实结尾。
        // 直接用 RawChapter.endByte 会读到 [本章标题, 下一章标题LF) 的错位区间，
        // 且对 UTF-16/32 还会有码元不对齐的尾部字节（plan 审查发现，见 docs §3.1.3）。
        //
        // 正确的章节边界语义（与 v5 之前 .idx 缓存的 buildOffsets 行为一致）：
        //   chapter[i] 区间 = [chapter[i].startByte, chapter[i+1].startByte)
        //   末章 endByte = fileSize
        // 这保证 readByByteRange 读出的字节按 charset 解码后包含"本章节标题 + 正文 + 直到下一章
        // 标题前的所有行"，且字节长度是码元整数倍（startByte 都落在码元边界）。
        val fileEnd = if (offsets != null && offsets.size > matches.size) offsets[matches.size] else 0L
        val chapters = matches.mapIndexed { idx, m ->
            val wc = computeWordCount(m, prefixSums)
            val endByte = if (idx + 1 < matches.size) matches[idx + 1].startByte else fileEnd
            BookChapter(
                bookId = bookId,
                chapterIndex = idx,
                chapterName = m.name.ifEmpty { "${idx + 1}" },
                // v5：chapterUrl 统一为字节偏移格式 "b:startByte:endByte"（半开区间，readByByteRange 用
                // endByte - startByte 作长度）。老书 chapterUrl 非 b: 前缀会被 MainReadViewModel 守卫
                // （needsRescanForMigration）检测到并触发一次性重扫升级，详见 plan §3.3.4。
                chapterUrl = "b:${m.startByte}:$endByte",
                wordCount = wc,
                count = wc
            )
        }

        val wordCounts = chapters.mapIndexed { idx, ch ->
            Triple(idx + 1, ch.wordCount.toInt(), 0)
        }
        val totalChars = chapters.sumOf { it.wordCount }.toInt()
        val allWordCounts = wordCounts + Triple(-1, totalChars, 0)

        val updated = chapters.map { it.copy(chaptersSize = chapters.size) }
        return ScanResult(charsetName, isUtf16Or32, updated, allWordCounts, offsets)
    }

    internal fun computeWordCount(chapter: RawChapter, prefixSums: LongArray): Long {
        if (prefixSums.isEmpty() || chapter.startLine > chapter.endLine) return 0L
        if (chapter.startLine >= prefixSums.size) return 0L
        val endIdx = chapter.endLine.coerceAtMost(prefixSums.size - 1)
        val startIdx = chapter.startLine.coerceAtLeast(0)
        val prevSum = if (startIdx > 0) prefixSums[startIdx - 1] else 0L
        return prefixSums[endIdx] - prevSum
    }
}

// ── 顶层：章节迁移守卫判定 ──
//
// 抽成顶层 internal fun 的目的：让纯 JVM 单元测试（TxtTextParserMigrationTest）可直接调用，
// 避免"镜像纯逻辑"反模式——历史上该反模式曾导致 P0 bug 长期未被发现（详见
// docs/reviews/2026-07-18-txt-chapter-scanner-review-of-review.md §D2）。
//
// 调用链：顶层 [needsRescanForMigration] ← TxtTextParser.needsRescanForMigration override
// ← TextParserImpl 转发 ← MainReadViewModel 打开书守卫调用。

/**
 * v2 修复（review §一阶遗漏 #7 + §X3）：单行字节超限截断辅助。
 *
 * 调用方（ChapterScanner 扫描循环内）在 `lineBuf.toByteArray()` 之前调用本函数：
 * - 未超限：no-op
 * - 超限：保留头部 [MAX_LINE_KEEP_BYTES] 字节（够章节标题识别），丢弃其余正文 + 日志告警
 *
 * 调用方需在调用前已 `lineBuf.reset()` 不会发生（本函数处理 lineBuf 内容后由调用方 reset），
 * 调用方语义：截断 → reset → 把 truncated 写回 lineBuf，让后续 line.toByteArray() 取截断后的版本。
 *
 * @param lineBuf 扫描循环内的 ByteArrayOutputStream
 * @param lineIdx 当前行号（仅用于日志）
 * @return 是否触发了截断（true = 已截断，调用方需用 lineBuf 的最新内容）
 */
internal fun truncateLineBufIfTooLong(
    lineBuf: java.io.ByteArrayOutputStream,
    lineIdx: Int
): Boolean {
    if (lineBuf.size() <= MAX_LINE_BYTES) return false
    Logger.w("$SCANNER_TAG: line $lineIdx exceeds $MAX_LINE_BYTES bytes, truncating to $MAX_LINE_KEEP_BYTES")
    val truncated = lineBuf.toByteArray().copyOf(MAX_LINE_KEEP_BYTES)
    lineBuf.reset()
    lineBuf.write(truncated)
    return true
}

/**
 * 是否需要因格式迁移而重扫章节（TXT 专用）。
 *
 * 判定依据：同本书的 chapterUrl 是原子写入的（格式一致），查第一条即可（O(1)）。
 *   - chapterUrl 为空/null：首次导入尚未写入，不算迁移目标（上层 isEmpty 守卫会兜底）
 *   - chapterUrl 不以 `b:` 开头：老格式（行偏移 `"startLine:endLine"` 或老 .idx 重建格式），触发迁移重扫
 *   - chapterUrl 以 `b:` 开头：已是当前格式，不需要迁移
 *
 * 非 TXT 格式不调用本函数（[com.wxn.bookparser.TextParser] 接口默认实现返回 false）。
 */
internal fun needsRescanForMigration(chapters: List<BookChapter>): Boolean {
    if (chapters.isEmpty()) return false
    val firstUrl = chapters.first().chapterUrl
    return !firstUrl.isNullOrEmpty() && !firstUrl.startsWith("b:")
}


