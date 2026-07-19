package com.wxn.bookparser.parser.txt

import android.content.Context
import android.os.Build
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.base.exception.NotTextFileException
import com.wxn.base.util.Logger
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.exts.clearAllMarkdown
import com.wxn.bookparser.parser.base.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TXT_TAG = "TxtTextParser"
private const val CHAPTER_CACHE_MAX_CHAPTERS = 8

/**
 * 单本书扫描结果 memo 的最大保留数。
 *
 * §3.4.4 ScanMemo 含 ScanResult.chapters（数百章 × 字符串），永久持有会堆积内存。
 * 取 3 覆盖"双开/分屏 + 切换"场景；LRU 超过时移除最早未活动的条目。
 */
private const val SCAN_MEMO_MAX_BOOKS = 3

/**
 * TXT 解析器（v5 重构）。
 *
 * 架构变更（`docs/plans/plan-txt-unify-byte-offset.md`）：
 * - **单一真相源**：DB 是 TXT 章节位置信息的唯一持久化载体。删除 `.idx` 影子缓存。
 * - **单一路径**：所有 TXT（含 UTF-16/32、老书）统一走字节偏移读取路径（`chapterUrl = "b:startByte:endByte"`），
 *   不保留行偏移读取路径。老书打开时由 `MainReadViewModel` 守卫（[needsRescanForMigration]）触发
 *   一次性重扫升级，避免永久背 O(N) 跳行惩罚。
 * - **charset 持久化**：[resolveCharsetName] 优先读 [txtBookMetaStore]（`BookEntity.txtCharset`），
 *   仅为 null 时现场探测并回填。
 *
 * PR1 保留（dead code，待 PR2 删除）：`TxtIndexStore` 类、`getOrScanBookInfo`/`fullScanAndCache`/
 * `readChapterByOffset`/`readChapterByLine`/`currentBookInfo`/`TxtBookInfo`——因
 * `parseChapterInfo`/`getWordCount`/`parsedChapterData` 已不再调用它们，编译器告 unused 但不报错。
 */
@Singleton
class TxtTextParser @Inject constructor(
    private val markdownParser: MarkdownParser,
    private val chapterScanner: ChapterScanner,
    private val context: Context,
    private val txtBookMetaStore: TxtBookMetaStore,
    private val txtCharsetDetector: TxtCharsetDetector,
) : TextParser {

    /**
     * 章节内容缓存：按 bookId 分桶，每桶 LRU（accessOrder=true）。
     *
     * v2 修复（review §O2）：原实现 key 是 `chapterIndex`（无 bookId 维度），用户从书 A 切书 B 翻
     * 第 1 章会命中书 A 第 1 章内容。改为 `Map<bookId, LRU<chapterIndex, content>>`。
     *
     * `computeIfAbsent` + `synchronized(bookCache)` 保证多协程并发访问安全。TxtTextParser 是
     * @Singleton，多本书可能并发访问不同桶——分桶后无跨桶竞争。
     */
    private val chapterCaches = ConcurrentHashMap<Long, LinkedHashMap<Int, List<ReaderText>>>()

    // ── §3.4.4 轻量 memoization（按 bookId 分桶 + per-book Mutex）──
    //
    // v2 修复（review §O1）：原实现是 `@Volatile var lastScan: ScanMemo?` 单字段，双开/分屏模式下
    // 多本书的 memo 互相覆盖，导致 `getWordCount` 回退路径每次都重新全文件扫描。
    // 改为 `Map<bookId, ScanMemo>` + per-book Mutex（双重检查避免并发双扫）。
    //
    // LRU 实现：ScanMemo 带 lastAccessMs 时间戳，每次命中（lastScanCharsetName 或后续 scanWithMemo
    // 的快路径）更新。淘汰时按 lastAccessMs 升序选最旧的（ConcurrentHashMap 不保留插入顺序，
    // 不能依赖 keys 迭代顺序做 LRU——v2 代码审查发现此 bug）。
    private data class ScanMemo(
        val bookId: Long,
        val fileSize: Long,
        val lastModified: Long,
        val result: ScanResult,
        val lastAccessMs: Long = System.currentTimeMillis()
    )

    private val scanMemos = ConcurrentHashMap<Long, ScanMemo>()
    private val scanMutexes = ConcurrentHashMap<Long, Mutex>()
    private val scanMemosLock = Any()  // 仅用于 LRU 淘汰的扫描互斥（避免与 scanMutexes 冲突）

    private suspend fun scanWithMemo(bookId: Long, cachedFile: CachedFile): ScanResult {
        // 快路径：memo 命中（锁外， ConcurrentHashMap 读安全）
        scanMemos[bookId]?.let { memo ->
            if (memo.fileSize == cachedFile.size &&
                memo.lastModified == cachedFile.lastModified
            ) {
                // 刷新访问时间（renew 一个新 ScanMemo 替换）——无锁更新，竞态下最坏情况是丢一次更新
                scanMemos[bookId] = memo.copy(lastAccessMs = System.currentTimeMillis())
                return memo.result
            }
        }
        // 慢路径：per-book Mutex 保护，避免并发双扫
        val mutex = scanMutexes.computeIfAbsent(bookId) { Mutex() }
        return mutex.withLock {
            // 双重检查：等待锁期间可能已被其他协程填入
            scanMemos[bookId]?.let { existing ->
                if (existing.fileSize == cachedFile.size &&
                    existing.lastModified == cachedFile.lastModified
                ) {
                    return@withLock existing.result
                }
            }
            chapterScanner.scan(bookId, cachedFile).also { result ->
                scanMemos[bookId] = ScanMemo(bookId, cachedFile.size, cachedFile.lastModified, result)
                // LRU 淘汰：超过 SCAN_MEMO_MAX_BOOKS 时移除访问时间最早的非当前条目
                synchronized(scanMemosLock) {
                    if (scanMemos.size > SCAN_MEMO_MAX_BOOKS) {
                        val oldestKey = scanMemos.entries
                            .filter { it.key != bookId }
                            .minByOrNull { it.value.lastAccessMs }
                            ?.key
                        if (oldestKey != null) {
                            scanMemos.remove(oldestKey)
                        }
                    }
                }
            }
        }
    }

    /**
     * 供 `MainReadViewModel` 在 `replaceChaptersByBookIdUseCase` 后调用，回填 `BookEntity.txtCharset`。
     * 返回最近一次扫描得到的 charsetName（若 memo 命中且 bookId 匹配），否则 null。
     *
     * v2 修复（review §O1）：原实现读单字段 `lastScan`，多本书场景下 memo 已被覆盖 → 返回 null。
     * 改为读 `scanMemos[bookId]`，按 bookId 隔离。
     */
    override fun lastScanCharsetName(bookId: Long): String? {
        val memo = scanMemos[bookId] ?: return null
        return memo.result.charsetName
    }

    // ── parseChapterInfo ──

    override suspend fun parseChapterInfo(bookId: Long, cachedFile: CachedFile): List<BookChapter> {
        return try {
            scanWithMemo(bookId, cachedFile).chapters
        } catch (e: NotTextFileException) {
            // ★ 二进制守卫命中：明确上抛，让 MainReadViewModel 映射到 BookReaderUiState.Error
            // 向用户提示「不是文本文件」，而不是被吞成 emptyList 导致白屏。
            throw e
        } catch (e: Exception) {
            Logger.e("$TXT_TAG: parseChapterInfo failed for book $bookId: ${e.message}")
            emptyList()
        }
    }

    // ── parsedChapterData ──

    override suspend fun parsedChapterData(
        bookId: Long,
        cachedFile: CachedFile,
        chapter: BookChapter
    ): List<ReaderText> {
        return try {
            // v2 修复（review §O2）：chapterCache 按 bookId 分桶，避免跨书命中错误数据
            val bookCache = chapterCaches.computeIfAbsent(bookId) {
                LinkedHashMap(0, 0.75f, true)
            }
            // 1. 内存缓存命中则直接返回
            synchronized(bookCache) {
                bookCache[chapter.chapterIndex]?.let { return it }
            }

            // 2. 解析 charset 并按 chapterUrl 路由
            val charsetName = resolveCharsetName(bookId, cachedFile)
            // 注：外层 TextParserImpl.parsedChapterData 已 withContext(Dispatchers.IO)，
            // 此处的 withContext(IO) 是冗余的二次切换（review §O7）——保留原行为以缩小本次 PR 范围，
            // 由下迭代 PR-4 P2-3 统一删除。
            val result = withContext(Dispatchers.IO) {
                readChapterByUrl(cachedFile, chapter, charsetName)
            }

            // 3. 写入缓存 + LRU 淘汰（仅缓存非空结果——空结果可能是非 b: 格式的临时告警，
            //    缓存空会让下次打开也命中空，失去重试机会）
            if (result.isNotEmpty()) {
                synchronized(bookCache) {
                    bookCache[chapter.chapterIndex] = result
                    if (bookCache.size > CHAPTER_CACHE_MAX_CHAPTERS) {
                        val oldest = bookCache.keys.firstOrNull()
                        if (oldest != null) bookCache.remove(oldest)
                    }
                }
            }
            result
        } catch (e: Exception) {
            Logger.e("$TXT_TAG: parsedChapterData failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * 解析 `chapterUrl` 的 `b:` 前缀字节偏移，走 RandomAccessFile.seek（O(1)）。
     *
     * `chapterUrl` 永远是 `"b:startByte:endByte"` 格式——老书在 `MainReadViewModel` 守卫
     * 处已被检测到并触发重扫升级（见 [needsRescanForMigration]），到这里的 `chapterUrl`
     * 必然是 `b:` 格式。若出现非 `b:` 格式（理论上不应发生，除非数据被外部篡改），
     * 返回 emptyList，上层 [chapterCache] 不缓存空结果，下次打开重试。
     */
    private fun readChapterByUrl(
        cachedFile: CachedFile,
        chapter: BookChapter,
        charsetName: String
    ): List<ReaderText> {
        val url = chapter.chapterUrl ?: return emptyList()
        if (!url.startsWith("b:")) {
            Logger.w("$TXT_TAG: unexpected non-b: chapterUrl: $url, returning empty")
            return emptyList()
        }
        val parts = url.removePrefix("b:").split(":")
        val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val end = parts.getOrNull(1)?.toLongOrNull() ?: cachedFile.size
        return readByByteRange(cachedFile, charsetName, start, end, chapter.chapterName)
    }

    // ── getWordCount / close ──

    override suspend fun getWordCount(bookId: Long, cachedFile: CachedFile): List<Triple<Int, Int, Int>> {
        return try {
            // §3.5.2：优先 DB 查询（BookChapter.wordCount 已在 buildResult 填充并随章节写入 DB）。
            // DB 列是 Long，这里收窄为 Int（与原 Triple<Int,Int,Int> 签名一致；章节字数不会超过 Int 范围）。
            val fromDb = txtBookMetaStore.getChaptersWithWordCount(bookId)
            if (fromDb.isNotEmpty()) {
                return fromDb.mapIndexed { idx, wc -> Triple(idx + 1, wc.toInt(), 0) } +
                    Triple(-1, fromDb.sum().toInt(), 0)
            }
            // 回退：DB 未命中（章节尚未持久化，如首次打开未触达 replaceChaptersByBookIdUseCase 的边界）。
            // 走 scanWithMemo——若 parseChapterInfo 已被调用过且 memo 命中，则避免重复扫描（§3.4.4）。
            // 注意：这是 plan §3.5.2 提到的"回退方案"，正常路径不会走到（首次导入时 MainReadViewModel
            // 会先 replaceChaptersByBookIdUseCase 写章节，再 loadChapterWords 调 getWordCount）。
            val scanned = scanWithMemo(bookId, cachedFile)
            scanned.wordCounts
        } catch (e: Exception) {
            Logger.e("$TXT_TAG: getWordCount failed: ${e.message}")
            emptyList()
        }
    }

    override suspend fun close(bookId: Long, cachedFile: CachedFile) {
        // v2 修复（review §O2 + §O1）：按 bookId 精准清理，不影响其他书。
        // 原实现 `chapterCache.clear()` 清掉了所有书的缓存，多本书场景下会误伤。
        chapterCaches.remove(bookId)
        scanMemos.remove(bookId)
        scanMutexes.remove(bookId)
    }

    // ── §3.3.4 老书迁移触发 ──

    override fun needsRescanForMigration(chapters: List<BookChapter>): Boolean =
        // 转发到顶层 fun——纯函数（不读实例字段），让纯 JVM 单元测试可直接调用顶层实现，
        // 避免 TxtTextParserMigrationTest 用"镜像纯逻辑"反模式（详见
        // docs/reviews/2026-07-18-txt-chapter-scanner-review-of-review.md §D2）。
        com.wxn.bookparser.parser.txt.needsRescanForMigration(chapters)

    // ── §3.3.3 charset 来源切换（含回填） ──

    /**
     * charsetName 来源优先级：
     * 1. [txtBookMetaStore.getCharset]（v12 新增，底层是 `BookEntity.txtCharset`）
     * 2. 兜底：现场探测 + 回填（仅在 txtCharset 为 null 时，例如老书首次升级后未回填）
     *
     * 回填的意义：老用户升级后 txtCharset 全为 null。若不回填，每次打开都要
     * 读文件头探测一次（虽廉价，但无谓）。回填后第二次打开起即 O(1) 命中。
     */
    private suspend fun resolveCharsetName(bookId: Long, cachedFile: CachedFile): String {
        val stored = txtBookMetaStore.getCharset(bookId)
        if (!stored.isNullOrEmpty()) return stored

        // 兜底：现场探测（与 ChapterScanner.scan 入口的探测逻辑一致）
        val detected = cachedFile.openInputStream()?.use { input ->
            val buf = ByteArray(65536)
            val n = input.read(buf)
            txtCharsetDetector.detect(if (n > 0) buf.copyOf(n) else ByteArray(0)).charsetName
        } ?: "UTF-8"

        // 回填：让下次打开直接命中，不再重复探测
        if (detected.isNotEmpty()) {
            runCatching { txtBookMetaStore.updateCharset(bookId, detected) }
            // runCatching：DB 写失败不影响本次读取（已拿到 detected），下次打开再尝试回填
        }
        return detected
    }

    // ── §3.3.1 字节偏移读取 ──

    private fun readByByteRange(
        cachedFile: CachedFile,
        charsetName: String,
        startByte: Long,
        endByte: Long,
        chapterName: String
    ): List<ReaderText> {
        if (startByte < 0 || endByte <= startByte) return emptyList()
        val charset = Charset.forName(charsetName)

        val bytes = cachedFile.rawFile?.let { rawFile ->
            // v2 修复（review §一阶遗漏 #6）：加文件长度截断。
            // 原实现直接 readFully(buf)，若文件被外部 App 截断（用户用其他编辑器改了 TXT），
            // endByte 仍是扫描时记录的旧值，可能超出新文件长度 → EOFException → 被外层 catch 吞成
            // emptyList → 用户白屏且每次打开重试。改为按 raf.length() 截断 + 日志告警。
            RandomAccessFile(rawFile, "r").use { raf ->
                val actualEnd = endByte.coerceAtMost(raf.length())
                if (actualEnd <= startByte) {
                    Logger.w("$TXT_TAG: file truncated, chapter '$chapterName' out of range " +
                        "(startByte=$startByte, fileLength=${raf.length()})")
                    return emptyList()
                }
                raf.seek(startByte)
                val len = (actualEnd - startByte)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
                val buf = ByteArray(len)
                raf.readFully(buf)
                buf
            }
        } ?: cachedFile.openInputStream()?.use { ins ->
            // SAF fallback（rawFile 为 null 时，缓存失败的少数场景）
            // v2 修复（review §X2）：API 23/24+ 都用 read(buf, off, len) 丢弃 startByte 字节，
            // 不再调 InputStream.skip——文档明确不保证 skip 实际 n 字节（可能返回 0 但未 EOF），
            // 会导致读到错误位置。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ins.skipNBytes(startByte)
            } else {
                // 用 read 替代 skip：每次最多 64 KiB，遇到 EOF 立即返回
                var remaining = startByte
                val dump = ByteArray(64 * 1024)
                while (remaining > 0) {
                    val toRead = minOf(remaining, dump.size.toLong()).toInt()
                    val n = ins.read(dump, 0, toRead)
                    if (n <= 0) {
                        Logger.w("$TXT_TAG: SAF stream EOF during skip, chapter '$chapterName' " +
                            "(remaining=$remaining of startByte=$startByte)")
                        return emptyList()
                    }
                    remaining -= n
                }
            }
            val len = (endByte - startByte).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val buf = ByteArray(len)
            var total = 0
            while (total < len) {
                val n = ins.read(buf, total, len - total)
                if (n < 0) {
                    // 文件被截断：返回已读部分（外层 parseContent 处理残缺字符串）
                    if (total == 0) return emptyList()
                    Logger.w("$TXT_TAG: SAF stream truncated, got $total/$len bytes for '$chapterName'")
                    break
                }
                total += n
            }
            if (total < len) buf.copyOf(total) else buf
        } ?: run {
            // 双 null 兜底（rawFile 和 stream 都打不开）——打日志并返回空。
            // v5 已删除 readChapterByLine 兼容路径，此处无回退，下次打开重试。
            Logger.e("$TXT_TAG: cannot open byte range for chapter '$chapterName', both rawFile and stream null")
            return emptyList()
        }

        val content = String(bytes, charset)
        return parseContent(content, chapterName)
    }

    private fun parseContent(content: String, chapterName: String): List<ReaderText> {
        val result = mutableListOf<ReaderText>()
        var titleAdded = false

        for (rawLine in content.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue
            when {
                isSeparator(trimmed) -> result.add(ReaderText.Separator)
                !titleAdded && rawLine.clearAllMarkdown().trim() == chapterName -> {
                    result.add(ReaderText.Chapter(title = rawLine.clearAllMarkdown().trim(), nested = false))
                    titleAdded = true
                }
                else -> result.add(ReaderText.Text(line = markdownParser.parse(rawLine).toString()))
            }
        }

        return result
    }

    private fun isSeparator(line: String): Boolean {
        val t = line.trim()
        if (t.length < 3) return false
        val first = t[0]
        if (first !in setOf('*', '-', '\u2014', '=')) return false
        return t.all { it == first || it == ' ' }
    }
}
