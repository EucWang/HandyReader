package com.wxn.bookparser.parser.txt

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.wxn.base.bean.ReaderText
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.file.CachedFileBuilder
import com.wxn.bookparser.parser.base.MarkdownParser
import kotlinx.coroutines.runBlocking
import org.commonmark.parser.Parser
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.charset.Charset

@RunWith(RobolectricTestRunner::class)
class TxtTextParserTest {

    private lateinit var parser: TxtTextParser
    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var txtFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val markdownParser = MarkdownParser(Parser.builder().build())
        val detector = object : TxtCharsetDetector {
            override fun detect(headerBytes: ByteArray): CharsetDetectionResult {
                return CharsetDetectionResult("UTF-8", false)
            }
        }
        // v5：TxtTextParser 构造函数新增 txtBookMetaStore + txtCharsetDetector 两个注入。
        // 测试用 in-memory fake：getCharset 返回 null（触发现场探测），updateCharset/getChaptersWithWordCount no-op。
        // 注意：本测试类仍受 Robolectric targetSdk=36 > maxSdk=35 问题阻塞（见方案 §8.4），
        // 实际无法运行；此处仅保证编译通过。新增用例请放到非 Robolectric 的纯 JVM 测试类。
        val metaStore = object : TxtBookMetaStore {
            override suspend fun getCharset(bookId: Long): String? = null
            override suspend fun updateCharset(bookId: Long, charset: String) {}
            override suspend fun getChaptersWithWordCount(bookId: Long): List<Long> = emptyList()
        }
        parser = TxtTextParser(markdownParser, ChapterScanner(detector), context, metaStore, detector)

        tempDir = File(System.getProperty("java.io.tmpdir"), "txtparser_${System.nanoTime()}")
        tempDir.mkdirs()
        txtFile = File(tempDir, "test.txt")
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private fun createCachedFile(content: String): CachedFile {
        txtFile.writeText(content)
        return CachedFile(
            context = context,
            uri = Uri.fromFile(txtFile),
            builder = CachedFileBuilder(
                name = txtFile.name,
                path = txtFile.absolutePath,
                size = txtFile.length(),
                lastModified = txtFile.lastModified(),
                isDirectory = false
            )
        )
    }

    private fun createCachedFile(bytes: ByteArray): CachedFile {
        txtFile.writeBytes(bytes)
        return CachedFile(
            context = context,
            uri = Uri.fromFile(txtFile),
            builder = CachedFileBuilder(
                name = txtFile.name,
                path = txtFile.absolutePath,
                size = txtFile.length(),
                lastModified = txtFile.lastModified(),
                isDirectory = false
            )
        )
    }

    private fun createParserWithRealDetector(): TxtTextParser {
        val markdownParser = MarkdownParser(Parser.builder().build())
        val detector = JuniversalCharsetDetector()
        val metaStore = object : TxtBookMetaStore {
            override suspend fun getCharset(bookId: Long): String? = null
            override suspend fun updateCharset(bookId: Long, charset: String) {}
            override suspend fun getChaptersWithWordCount(bookId: Long): List<Long> = emptyList()
        }
        return TxtTextParser(markdownParser, ChapterScanner(detector), context, metaStore, detector)
    }

    private fun assertContentContainsText(data: List<ReaderText>, vararg keywords: String) {
        val textEntries = data.filterIsInstance<ReaderText.Text>()
        assertTrue("No ReaderText.Text entries found", textEntries.isNotEmpty())
        for (keyword in keywords) {
            val found = textEntries.any { it.line.contains(keyword) }
            assertTrue("Expected keyword '$keyword' not found in content", found)
        }
    }

    // ── P1-1: Index miss → scan ──

    @Test
    fun parseChapterInfo_indexMiss_scansAndReturnsChapters() = runBlocking {
        val content = buildString {
            appendLine("第一章 开始")
            appendLine("这是一些正文内容。")
            appendLine("第二章 继续")
            appendLine("更多的正文内容。")
        }
        val cachedFile = createCachedFile(content)

        val chapters = parser.parseChapterInfo(1L, cachedFile)
        assertTrue("Should find chapters", chapters.isNotEmpty())
        assertTrue("Chapter 0 name should be non-blank", chapters[0].chapterName.isNotBlank())
    }

    // ── P1-3: Word count computed ──
    // v5：getWordCount 优先走 DB 查询（TxtBookMetaStore.getChaptersWithWordCount），DB 空时回退到
    // scanWithMemo（plan §3.5.2 回退方案）。本测试 fake metaStore 返回 emptyList → 走扫描回退路径。

    @Test
    fun getWordCount_scansAndReturnsNonZeroCounts() = runBlocking {
        val content = buildString {
            appendLine("第一章 开始")
            appendLine("这是一些正文内容。")
            appendLine("第二章 继续")
            appendLine("更多的正文内容。")
        }
        val cachedFile = createCachedFile(content)

        val counts = parser.getWordCount(2L, cachedFile)
        // DB 空 → 回退到 scanWithMemo → 返回扫描结果
        assertTrue("Should return word counts (via scan fallback)", counts.isNotEmpty())
        val total = counts.last()
        assertEquals(-1, total.first)
        assertTrue("Total chars should be > 0 (via scan fallback)", total.second > 0)
    }

    // ── P1-1: parseChapterInfo then getWordCount reuse ──

    @Test
    fun parseChapterInfo_thenGetWordCount_reusesCache() = runBlocking {
        val content = buildString {
            appendLine("第一章 开始")
            appendLine("这是一些正文内容。")
            appendLine("第二章 继续")
            appendLine("更多的正文内容。")
        }
        val cachedFile = createCachedFile(content)

        val chapters = parser.parseChapterInfo(3L, cachedFile)
        val counts = parser.getWordCount(3L, cachedFile)

        assertTrue(chapters.isNotEmpty())
        assertTrue(counts.isNotEmpty())
    }

    // ── P1-1: Index hit (cross-session simulation) ──

    @Test
    fun parsedChapterData_afterRescan_indexHit_usesByteOffsetRead() = runBlocking {
        val content = buildString {
            appendLine("第一章 开始")
            appendLine("这是一些正文内容,用于测试。")
            appendLine("第二章 继续")
            appendLine("更多的正文内容,继续测试。")
        }
        val cachedFile = createCachedFile(content)

        // First pass: scan + write index
        parser.parseChapterInfo(10L, cachedFile)
        parser.close(10L, cachedFile)

        // Second pass: should hit index → minimal info → byte-offset read works
        val chapters = parser.parseChapterInfo(10L, cachedFile)
        assertTrue(chapters.isNotEmpty())

        val chapter = chapters.first()
        val data = parser.parsedChapterData(10L, cachedFile, chapter)
        assertNotNull(data)
        assertTrue("Chapter data should not be empty", data.isNotEmpty())
        assertContentContainsText(data, "开始", "正文")
    }

    // ── P1-2: clearAllMarkdown before title match ──

    @Test
    fun parsedChapterData_chapterTitleWithMarkdown_strippedBeforeMatch() = runBlocking {
        val content = buildString {
            appendLine("***第一章***")
            appendLine("正文内容。")
        }
        val cachedFile = createCachedFile(content)

        val chapters = parser.parseChapterInfo(20L, cachedFile)
        assertTrue(chapters.isNotEmpty())

        val chapter = chapters.first()
        val data = parser.parsedChapterData(20L, cachedFile, chapter)
        assertNotNull(data)
        assertTrue("Should have content", data.isNotEmpty())
        assertContentContainsText(data, "正文内容")
    }

    // ── Normal content reading ──

    @Test
    fun parsedChapterData_normalContent_returnsText() = runBlocking {
        val content = buildString {
            appendLine("第一章 测试")
            appendLine("这是正文。")
            appendLine("第二行。")
        }
        val cachedFile = createCachedFile(content)

        val chapters = parser.parseChapterInfo(21L, cachedFile)
        assertTrue(chapters.isNotEmpty())

        val data = parser.parsedChapterData(21L, cachedFile, chapters.first())
        assertTrue(data.isNotEmpty())
        assertContentContainsText(data, "这是正文")
    }

    // ── close removes cache ──

    @Test
    fun close_removesBookFromCache() = runBlocking {
        val content = "第一章\n正文\n"
        val cachedFile = createCachedFile(content)

        parser.parseChapterInfo(30L, cachedFile)
        parser.close(30L, cachedFile)

        // After close, re-scan should still work
        val chapters = parser.parseChapterInfo(30L, cachedFile)
        assertNotNull(chapters)
    }

    // ── Empty file edge case ──

    @Test
    fun parseChapterInfo_emptyFile_doesNotCrash() = runBlocking {
        val content = ""
        val cachedFile = createCachedFile(content)

        val chapters = parser.parseChapterInfo(40L, cachedFile)
        assertNotNull(chapters)
    }

    // ════════════════════════════════════════════════════════════════
    // Batch P0: Charset encoding end-to-end tests
    // ════════════════════════════════════════════════════════════════

    @Test
    fun testGbkCjk_throughFullPipeline() = runBlocking {
        val text = "第一章 开始\n正文内容。\n第二章 继续\n更多测试文本。\n"
        val bytes = text.toByteArray(Charset.forName("GBK"))
        val cachedFile = createCachedFile(bytes)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(100L, cachedFile)
        assertTrue("GBK: Should find chapters", chapters.isNotEmpty())
        assertTrue("GBK: Chapter name should be non-blank", chapters[0].chapterName.isNotBlank())

        val data = p.parsedChapterData(100L, cachedFile, chapters.first())
        assertNotNull("GBK: Content should not be null", data)
        assertContentContainsText(data, "正文")

        val counts = p.getWordCount(100L, cachedFile)
        assertTrue("GBK: Total chars should > 0", counts.last().second > 0)

        p.close(100L, cachedFile)
        val chapters2 = p.parseChapterInfo(100L, cachedFile)
        assertTrue("GBK: Reopen should find chapters", chapters2.isNotEmpty())
    }

    @Test
    fun testBig5Cjk_throughFullPipeline() = runBlocking {
        val text = "第一章 開始\n正體中文內容。\n第二章 繼續\n更多測試內容。\n"
        val bytes = text.toByteArray(Charset.forName("Big5"))
        val cachedFile = createCachedFile(bytes)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(101L, cachedFile)
        assertTrue("Big5: Should find chapters", chapters.isNotEmpty())
        assertTrue("Big5: Chapter name should be non-blank", chapters[0].chapterName.isNotBlank())

        val data = p.parsedChapterData(101L, cachedFile, chapters.first())
        assertNotNull("Big5: Content should not be null", data)
        assertContentContainsText(data, "正體中文")

        val counts = p.getWordCount(101L, cachedFile)
        assertTrue("Big5: Total chars should > 0", counts.last().second > 0)

        p.close(101L, cachedFile)
        val chapters2 = p.parseChapterInfo(101L, cachedFile)
        assertTrue("Big5: Reopen should find chapters", chapters2.isNotEmpty())
    }

    @Test
    fun testShiftJisCjk_throughFullPipeline() = runBlocking {
        val text = "第1章 テスト\n日本語の本文です。\n第2章 続き\nさらに本文が続きます。\n"
        val bytes = text.toByteArray(Charset.forName("Shift_JIS"))
        val cachedFile = createCachedFile(bytes)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(102L, cachedFile)
        assertTrue("Shift_JIS: Should find chapters", chapters.isNotEmpty())
        assertTrue("Shift_JIS: Chapter name should be non-blank", chapters[0].chapterName.isNotBlank())

        val data = p.parsedChapterData(102L, cachedFile, chapters.first())
        assertNotNull("Shift_JIS: Content should not be null", data)
        assertContentContainsText(data, "日本語")

        val counts = p.getWordCount(102L, cachedFile)
        assertTrue("Shift_JIS: Total chars should > 0", counts.last().second > 0)

        p.close(102L, cachedFile)
        val chapters2 = p.parseChapterInfo(102L, cachedFile)
        assertTrue("Shift_JIS: Reopen should find chapters", chapters2.isNotEmpty())
    }

    @Test
    fun testEucKrCjk_throughFullPipeline() = runBlocking {
        val text = "제1장 테스트\n한국어 본문 내용입니다.\n제2장 계속\n더 많은 본문 내용입니다.\n"
        val bytes = text.toByteArray(Charset.forName("EUC-KR"))
        val cachedFile = createCachedFile(bytes)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(103L, cachedFile)
        assertTrue("EUC-KR: Should find chapters", chapters.isNotEmpty())
        assertTrue("EUC-KR: Chapter name should be non-blank", chapters[0].chapterName.isNotBlank())

        val data = p.parsedChapterData(103L, cachedFile, chapters.first())
        assertNotNull("EUC-KR: Content should not be null", data)
        assertContentContainsText(data, "한국어")

        val counts = p.getWordCount(103L, cachedFile)
        assertTrue("EUC-KR: Total chars should > 0", counts.last().second > 0)

        p.close(103L, cachedFile)
        val chapters2 = p.parseChapterInfo(103L, cachedFile)
        assertTrue("EUC-KR: Reopen should find chapters", chapters2.isNotEmpty())
    }

    @Test
    fun testWindows1252_throughFullPipeline() = runBlocking {
        val text = "Chapter One\nCaf\u00E9 r\u00E9sum\u00E9 na\u00EFve fa\u00E7ade.\nChapter Two\nMore accented text.\n"
        val bytes = text.toByteArray(Charset.forName("windows-1252"))
        val cachedFile = createCachedFile(bytes)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(104L, cachedFile)
        assertTrue("windows-1252: Should find chapters", chapters.isNotEmpty())

        val data = p.parsedChapterData(104L, cachedFile, chapters.first())
        assertNotNull("windows-1252: Content should not be null", data)
        assertContentContainsText(data, "Caf\u00E9")

        val counts = p.getWordCount(104L, cachedFile)
        assertTrue("windows-1252: Total chars should > 0", counts.last().second > 0)

        p.close(104L, cachedFile)
        val chapters2 = p.parseChapterInfo(104L, cachedFile)
        assertTrue("windows-1252: Reopen should find chapters", chapters2.isNotEmpty())
    }

    // ── UTF-16/32: scanWithReader path ──

    @Test
    fun testUtf16Le_throughFullPipeline() = runBlocking {
        val text = "第一章 开始\n正文内容。\n第二章 继续\n更多内容。\n"
        val contentBytes = text.toByteArray(Charset.forName("UTF-16LE"))
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val fileBytes = bom + contentBytes
        val cachedFile = createCachedFile(fileBytes)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(105L, cachedFile)
        assertTrue("UTF-16LE: Should find chapters", chapters.isNotEmpty())

        val data = p.parsedChapterData(105L, cachedFile, chapters.first())
        assertNotNull("UTF-16LE: Content should not be null", data)
        assertContentContainsText(data, "正文")

        val counts = p.getWordCount(105L, cachedFile)
        assertTrue("UTF-16LE: Total chars should > 0", counts.last().second > 0)
    }

    @Test
    fun testUtf16Be_throughFullPipeline() = runBlocking {
        val text = "第一章 开始\n正文内容。\n第二章 继续\n更多内容。\n"
        val contentBytes = text.toByteArray(Charset.forName("UTF-16BE"))
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val fileBytes = bom + contentBytes
        val cachedFile = createCachedFile(fileBytes)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(106L, cachedFile)
        assertTrue("UTF-16BE: Should find chapters", chapters.isNotEmpty())

        val data = p.parsedChapterData(106L, cachedFile, chapters.first())
        assertNotNull("UTF-16BE: Content should not be null", data)
        assertContentContainsText(data, "正文")

        val counts = p.getWordCount(106L, cachedFile)
        assertTrue("UTF-16BE: Total chars should > 0", counts.last().second > 0)
    }

    @Test
    fun testUtf32Le_throughFullPipeline() = runBlocking {
        val text = "第一章 开始\n正文内容。\n第二章 继续\n更多内容。\n"
        val contentBytes = text.toByteArray(Charset.forName("UTF-32LE"))
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
        val fileBytes = bom + contentBytes
        val cachedFile = createCachedFile(fileBytes)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(107L, cachedFile)
        assertTrue("UTF-32LE: Should find chapters", chapters.isNotEmpty())

        val data = p.parsedChapterData(107L, cachedFile, chapters.first())
        assertNotNull("UTF-32LE: Content should not be null", data)
        assertContentContainsText(data, "正文")

        val counts = p.getWordCount(107L, cachedFile)
        assertTrue("UTF-32LE: Total chars should > 0", counts.last().second > 0)
    }

    @Test
    fun testUtf32Be_throughFullPipeline() = runBlocking {
        val text = "第一章 开始\n正文内容。\n第二章 继续\n更多内容。\n"
        val contentBytes = text.toByteArray(Charset.forName("UTF-32BE"))
        val bom = byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())
        val fileBytes = bom + contentBytes
        val cachedFile = createCachedFile(fileBytes)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(108L, cachedFile)
        assertTrue("UTF-32BE: Should find chapters", chapters.isNotEmpty())

        val data = p.parsedChapterData(108L, cachedFile, chapters.first())
        assertNotNull("UTF-32BE: Content should not be null", data)
        assertContentContainsText(data, "正文")

        val counts = p.getWordCount(108L, cachedFile)
        assertTrue("UTF-32BE: Total chars should > 0", counts.last().second > 0)
    }

    // ── P1-9: GBK + Markdown + Separator ──

    @Test
    fun testGbkWithMarkdownAndSeparators() = runBlocking {
        val text = "第一章 开始\n**粗体文字**\n*斜体文字*\n***\n更多内容。\n"
        val bytes = text.toByteArray(Charset.forName("GBK"))
        val cachedFile = createCachedFile(bytes)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(109L, cachedFile)
        assertTrue("Should find chapters", chapters.isNotEmpty())

        val data = p.parsedChapterData(109L, cachedFile, chapters.first())
        assertNotNull(data)
        assertContentContainsText(data, "粗体")
    }

    // ── P4-1: Uniform split fallback ──

    @Test
    fun uniformSplit_fallback_triggered_byNoChapters() = runBlocking {
        val text = "这是一段普通的文本内容，没有任何章节标题标记。\n".repeat(20)
        val cachedFile = createCachedFile(text)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(120L, cachedFile)
        assertTrue("Should produce uniform split segments", chapters.isNotEmpty())
    }

    // ── P4-3: Large file with yield ──

    @Test
    fun largeFile_2000Lines_scanCompletes() = runBlocking {
        val sb = StringBuilder()
        for (i in 1..2000) {
            sb.appendLine("这是第 $i 行的测试内容。")
        }
        val cachedFile = createCachedFile(sb.toString())
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(122L, cachedFile)
        assertTrue("Large file: Should produce chapters", chapters.isNotEmpty())
    }

    // ── P4-4: Separator lines ──

    @Test
    fun parsedChapterData_separatorLines_handledCorrectly() = runBlocking {
        val text = "第一章\n正文内容\n***\n更多内容\n---\n结束\n"
        val cachedFile = createCachedFile(text)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(123L, cachedFile)
        assertTrue(chapters.isNotEmpty())

        val data = p.parsedChapterData(123L, cachedFile, chapters.first())

        val separators = data.filterIsInstance<ReaderText.Separator>()
        assertTrue("Should have at least one Separator", separators.isNotEmpty())
    }

    // ── P4-5: Markdown syntax ──

    @Test
    fun parsedChapterData_markdownSyntax_renderedWithoutCrash() = runBlocking {
        val text = "**粗体** *斜体* # 标题 普通文字\n第二行内容。\n"
        val cachedFile = createCachedFile(text)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(124L, cachedFile)
        assertTrue(chapters.isNotEmpty())

        val data = p.parsedChapterData(124L, cachedFile, chapters.first())
        assertNotNull(data)
        assertContentContainsText(data, "粗体")
    }

    // ── P1-10: No artificial chapter header when title not in content ──

    @Test
    fun parsedChapterData_titleNotFound_noArtificialHeader() = runBlocking {
        val text = "这只是一段普通文本\n没有检测到章节标题。\n"
        val cachedFile = createCachedFile(text)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(125L, cachedFile)
        assertTrue(chapters.isNotEmpty())

        val data = p.parsedChapterData(125L, cachedFile, chapters.first())
        assertNotNull(data)
        assertTrue("Content should have entries", data.isNotEmpty())
        val chapterEntries = data.filterIsInstance<ReaderText.Chapter>()
        assertTrue("Should NOT prepend artificial Chapter entry", chapterEntries.isEmpty())
    }

    // ── P4-7: Sequential book switches ──

    @Test
    fun sequentialBooks_differentBookIds_work() = runBlocking {
        val text = "第一章\n正文。\n"
        val p = createParserWithRealDetector()

        for (bid in 200L until 205L) {
            val bytes = text.toByteArray(Charset.forName("GBK"))
            txtFile.writeBytes(bytes)
            val cf = CachedFile(
                context = context,
                uri = Uri.fromFile(txtFile),
                builder = CachedFileBuilder(
                    name = txtFile.name,
                    path = txtFile.absolutePath,
                    size = txtFile.length(),
                    lastModified = txtFile.lastModified(),
                    isDirectory = false
                )
            )
            val chapters = p.parseChapterInfo(bid, cf)
            assertTrue("Book $bid should have chapters", chapters.isNotEmpty())
        }

        val bytes = text.toByteArray(Charset.forName("GBK"))
        txtFile.writeBytes(bytes)
        val cf5 = CachedFile(
            context = context,
            uri = Uri.fromFile(txtFile),
            builder = CachedFileBuilder(
                name = txtFile.name,
                path = txtFile.absolutePath,
                size = txtFile.length(),
                lastModified = txtFile.lastModified(),
                isDirectory = false
            )
        )
        val chapters5 = p.parseChapterInfo(205L, cf5)
        assertTrue("Book 205 should have chapters", chapters5.isNotEmpty())
    }

    // ── P1-12: File modification invalidates index ──

    @Test
    fun testGbkFileSizeChanges_indexInvalidated() = runBlocking {
        // text1: short file with distinctive content
        val text1 = "第一章\n仅在第一版出现。\n"
        val bytes1 = text1.toByteArray(Charset.forName("GBK"))
        txtFile.writeBytes(bytes1)
        val cf1 = CachedFile(
            context = context,
            uri = Uri.fromFile(txtFile),
            builder = CachedFileBuilder(
                name = txtFile.name,
                path = txtFile.absolutePath,
                size = txtFile.length(),
                lastModified = txtFile.lastModified(),
                isDirectory = false
            )
        )
        val p = createParserWithRealDetector()
        val chapters1 = p.parseChapterInfo(111L, cf1)
        assertTrue("First parse should find chapters", chapters1.isNotEmpty())
        p.close(111L, cf1)

        // text2: different file with different size
        Thread.sleep(200)
        val text2 = "这是修改版的内容，仅在新版出现。\n".repeat(5)
        val bytes2 = text2.toByteArray(Charset.forName("GBK"))
        assertNotEquals("File sizes should differ", bytes1.size, bytes2.size)
        txtFile.writeBytes(bytes2)
        val cf2 = CachedFile(
            context = context,
            uri = Uri.fromFile(txtFile),
            builder = CachedFileBuilder(
                name = txtFile.name,
                path = txtFile.absolutePath,
                size = txtFile.length(),
                lastModified = txtFile.lastModified(),
                isDirectory = false
            )
        )

        val chapters2 = p.parseChapterInfo(111L, cf2)
        assertTrue("Re-parse should find chapters", chapters2.isNotEmpty())

        val data = p.parsedChapterData(111L, cf2, chapters2.first())
        assertNotNull(data)
        assertContentContainsText(data, "修改版", "新版")
        p.close(111L, cf2)
    }

    // ── P4-6: Byte-offset reading preserves content correctly ──

    @Test
    fun parsedChapterData_byteOffsetRead_contentCorrect() = runBlocking {
        val lines = listOf(
            "第一章 开始",
            "正文内容。",
            "特殊字符: é ü ñ å",
            "---",
            "分隔符后内容。"
        )
        val content = lines.joinToString("\n") + "\n"
        val cachedFile = createCachedFile(content)

        val chapters = parser.parseChapterInfo(127L, cachedFile)
        assertTrue(chapters.isNotEmpty())

        val data = parser.parsedChapterData(127L, cachedFile, chapters.first())
        assertContentContainsText(data, "正文内容")
        assertContentContainsText(data, "é", "ü", "ñ", "å")

        val separators = data.filterIsInstance<ReaderText.Separator>()
        assertTrue("Should detect separator in byte-offset path", separators.isNotEmpty())
    }

    // ── F2: Separator pattern matching ──

    @Test
    fun separators_dashEmDashEqualsStar_allDetected() = runBlocking {
        val text = "第一章\n正文\n---\n更多\n———\n结束\n=======\n* * *\n"
        val cachedFile = createCachedFile(text)
        val p = createParserWithRealDetector()

        val chapters = p.parseChapterInfo(130L, cachedFile)
        assertTrue(chapters.isNotEmpty())

        val data = p.parsedChapterData(130L, cachedFile, chapters.first())
        val separators = data.filterIsInstance<ReaderText.Separator>()
        assertTrue("Should detect at least 3 separators, got ${separators.size}", separators.size >= 3)
    }

    // ── F5: Index hit with chapter names ──

    @Test
    fun indexHit_returnsFullChapterInfo() = runBlocking {
        val content = buildString {
            appendLine("第一章 开始")
            appendLine("内容一。")
            appendLine("第二章 继续")
            appendLine("内容二。")
        }
        val cachedFile = createCachedFile(content)

        parser.parseChapterInfo(140L, cachedFile)
        parser.close(140L, cachedFile)

        val chapters = parser.parseChapterInfo(140L, cachedFile)
        assertTrue("Index hit should return chapters", chapters.isNotEmpty())
        assertTrue("Chapter 0 name should be non-blank", chapters[0].chapterName.isNotBlank())
        assertTrue("Chapter word count should be > 0", chapters[0].wordCount > 0)
    }
}
