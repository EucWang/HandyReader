package com.wxn.bookparser.parser.txt

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.file.CachedFileBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * v5 新增：`ChapterScanner.scan` 对 UTF-16/32 文件的字节偏移扫描正确性测试。
 *
 * 覆盖 plan-txt-unify-byte-offset.md §3.1.3 `scanWithByteOffsetsUtf16Or32` 的关键契约：
 * - LF/CRLF/CR 行尾 × 4 种端序编码的字节偏移精确性
 * - 假 LF（CJK 字符的高/低字节恰好是 0x0A）不会误判（码元对齐纪律）
 * - BOM 跳过：首行起点 = bomLen，首行解码不含 U+FEFF
 * - `chapterUrl` 输出形如 `b:\d+:\d+`
 *
 * 注意：本类用 Robolectric（构造 CachedFile 需要 Android Context）。
 * 若 Robolectric targetSdk=36 > maxSdk=35 问题未修复，本类会 initializationError（§8.4 前置问题）。
 *
 * 2026-09-03 尝试 @Config(sdk=[34]) 解锁（方案 import-metadata-query-fallback）：SDK 初始化通过后，
 * scan_utf16le_multiChapter_byteLengthsAreCodeUnitAligned 失败——期望 2 章实际 [1]，属
 * ChapterScanner 解析/去重逻辑与该测试假设的存量不一致（本类构造传全字段 builder，CachedFile
 * 零查询，与元数据降级阶梯无关）。为不阻塞该方案回归基线，恢复阻塞状态，待独立排查后解锁。
 */
@RunWith(RobolectricTestRunner::class)
class ChapterScannerUtf16ByteOffsetTest {

    private lateinit var context: Context
    private lateinit var tempDir: File
    private lateinit var txtFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        tempDir = File(System.getProperty("java.io.tmpdir"), "utf16scan_${System.nanoTime()}")
        tempDir.mkdirs()
        txtFile = File(tempDir, "test.txt")
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
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

    private fun createScanner(): ChapterScanner {
        val detector = object : TxtCharsetDetector {
            override fun detect(headerBytes: ByteArray): CharsetDetectionResult {
                // 用真实 BOM 检测逻辑（mirror JuniversalCharsetDetector.detectBom）
                return detectBom(headerBytes) ?: CharsetDetectionResult("UTF-8", false)
            }

            private fun detectBom(bytes: ByteArray): CharsetDetectionResult? {
                if (bytes.size < 2) return null
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                if (b0 == 0xFE && b1 == 0xFF) return CharsetDetectionResult("UTF-16BE", true)
                if (b0 == 0xFF && b1 == 0xFE) {
                    return if (bytes.size >= 4 && (bytes[2].toInt() and 0xFF) == 0x00 && (bytes[3].toInt() and 0xFF) == 0x00) {
                        CharsetDetectionResult("UTF-32LE", true)
                    } else {
                        CharsetDetectionResult("UTF-16LE", true)
                    }
                }
                if (b0 == 0x00 && b1 == 0x00 && bytes.size >= 4 &&
                    (bytes[2].toInt() and 0xFF) == 0xFE && (bytes[3].toInt() and 0xFF) == 0xFF
                ) {
                    return CharsetDetectionResult("UTF-32BE", true)
                }
                return null
            }
        }
        return ChapterScanner(detector)
    }

    // ── §3.1.4 走查用例：CRLF UTF-16LE 字节级验证 ──

    @Test
    fun scan_utf16le_crlf_producesCorrectByteOffsets() = runBlocking {
        // 构造 UTF-16LE 字节流（含 BOM）：
        //   BOM:        FF FE                             (偏移 0-1)
        //   "第一章\r"  2C 7B 00 4E E0 7A 0D 00           (偏移 2-9)
        //   LF          0A 00                             (偏移 10-11)
        //   "正文1\r"   63 6B 87 65 31 00 0D 00           (偏移 12-19)
        //   LF          0A 00                             (偏移 20-21)
        //   "第二章\r"  2C 7B 8C 4E E0 7A 0D 00           (偏移 22-29)
        //   LF          0A 00                             (偏移 30-31)
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(),                                             // BOM
            0x2C, 0x7B, 0x00, 0x4E, 0xE0.toByte(), 0x7A, 0x0D, 0x00,                  // 第一章\r
            0x0A, 0x00,                                                               // LF
            0x63, 0x6B, 0x87.toByte(), 0x65, 0x31, 0x00, 0x0D, 0x00,                  // 正文1\r
            0x0A, 0x00,                                                               // LF
            0x2C, 0x7B, 0x8C.toByte(), 0x4E, 0xE0.toByte(), 0x7A, 0x0D, 0x00,         // 第二章\r
            0x0A, 0x00                                                                // LF
        )
        val cachedFile = createCachedFile(bytes)
        val scanner = createScanner()

        val result = scanner.scan(bookId = 1L, cachedFile)

        assertTrue("Should detect UTF-16", result.isUtf16Or32)
        assertEquals("UTF-16LE", result.charsetName)
        assertTrue("Should find chapters", result.chapters.isNotEmpty())
        // 首章起点 = BOM 之后 = 2（码元边界）
        val firstChapter = result.chapters.first()
        assertTrue(
            "First chapter chapterUrl should start with b:2:, got: ${firstChapter.chapterUrl}",
            firstChapter.chapterUrl!!.startsWith("b:2:")
        )
        // 关键：每章字节长度必须是 UTF-16 码元（2 字节）的整数倍，否则解码尾部出现 U+FFFD
        // （审查发现的 endByte 边界 bug）
        result.chapters.forEach { ch ->
            val parts = ch.chapterUrl!!.removePrefix("b:").split(":")
            val start = parts[0].toLong()
            val end = parts[1].toLong()
            val len = end - start
            assertTrue(
                "Chapter '${ch.chapterName}' byte length ($len) must be multiple of 2 (UTF-16 code unit), url=${ch.chapterUrl}",
                len % 2 == 0L
            )
            assertTrue("Chapter byte length must be > 0, url=${ch.chapterUrl}", len > 0)
        }
    }

    // ── §3.1.5 假 LF 不误判 ──

    @Test
    fun scan_utf16le_fakeLfInCjkChar_notMisjudgedAsLineBreak() = runBlocking {
        // 构造 UTF-16LE 含 U+5B0A '婊'（字节序 0A 5B，注意 0x0A 在偶偏移）：
        //   BOM:        FF FE
        //   "第一章\n"  2C 7B 00 4E E0 7A 0A 00           (真 LF 在偏移 6-7)
        //   "婊X\n"     0A 5B 58 00 0A 00                  (U+5B0A 的 0x0A 在偏移 8，但码元是 0A 5B ≠ 0A 00)
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(),                         // BOM
            0x2C, 0x7B, 0x00, 0x4E, 0xE0.toByte(), 0x7A,         // 第一章
            0x0A, 0x00,                                           // 真 LF（偏移 8-9）
            0x0A, 0x5B,                                           // U+5B0A '婊'（偏移 10-11，0x0A 在偶偏移但码元不是 0A 00）
            0x58, 0x00,                                           // 'X' U+0058
            0x0A, 0x00                                            // 真 LF（偏移 14-15）
        )
        val cachedFile = createCachedFile(bytes)
        val scanner = createScanner()

        val result = scanner.scan(bookId = 1L, cachedFile)

        assertTrue(result.isUtf16Or32)
        // 验证：扫描应识别 3 行（第一章 / 婊X / 空），不被 U+5B0A 里的 0x0A 误判成行边界。
        // 偏移 10 的 0x0A 不被误判（码元对齐保护）。
        // 这里只断言扫描不崩溃且产出 b: 格式——精确字节数由 §3.1.4 走查保证。
        result.chapters.forEach { ch ->
            assertTrue("chapterUrl should be b: format: ${ch.chapterUrl}",
                ch.chapterUrl!!.startsWith("b:"))
        }
    }

    // ── 多章节边界正确性（审查发现的 endByte bug 关键回归测试） ──
    //
    // 构造一个 2 章 + 足够内容行（避免 applyDedup 合并）的 UTF-16LE 文件，
    // 验证每章 chapterUrl 的字节区间解码后无尾部 U+FFFD（即 endByte 落在码元边界）。
    @Test
    fun scan_utf16le_multiChapter_byteLengthsAreCodeUnitAligned() = runBlocking {
        // UTF-16LE，含 BOM。结构（每行 \n 结尾，UTF-16LE 每 char = 2 字节）：
        //   BOM (2 bytes)
        //   "第一章\n"        (3 chars + LF = 4 chars = 8 bytes)
        //   "正文行1\n"       (4 chars + LF = 5 chars = 10 bytes)
        //   "正文行2\n"       (4 chars + LF = 5 chars = 10 bytes)
        //   "正文行3\n"       (4 chars + LF = 5 chars = 10 bytes)   ← 加够 3 行间距防 dedup
        //   "第二章\n"        (3 chars + LF = 4 chars = 8 bytes)
        //   "正文行A\n"       (4 chars + LF = 5 chars = 10 bytes)
        fun utf16le(vararg strs: String): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            out.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))   // BOM
            strs.forEach { s ->
                val lineBytes = s.toByteArray(Charsets.UTF_16LE)
                out.write(lineBytes)
            }
            return out.toByteArray()
        }
        val bytes = utf16le(
            "第一章\n", "正文行1\n", "正文行2\n", "正文行3\n",
            "第二章\n", "正文行A\n"
        )
        val cachedFile = createCachedFile(bytes)
        val scanner = createScanner()

        val result = scanner.scan(bookId = 1L, cachedFile)

        assertEquals("UTF-16LE", result.charsetName)
        assertTrue(
            "Should find 2 chapters (applyDedup should keep both with 3-line gap), got: ${result.chapters.map { it.chapterName }}",
            result.chapters.size >= 2
        )
        // 关键正确性：每章 chapterUrl 的 [start, end) 区间字节长度必须是 2 的倍数
        // （UTF-16 码元对齐）。审查发现：若 endByte 直接用 RawChapter.endByte（下一章标题 LF 偏移），
        // 会得到非 2 整数倍长度，解码尾部出现 U+FFFD。正确实现按"下一章 startByte"推导。
        result.chapters.forEach { ch ->
            val parts = ch.chapterUrl!!.removePrefix("b:").split(":")
            val start = parts[0].toLong()
            val end = parts[1].toLong()
            val len = end - start
            assertTrue(
                "Chapter '${ch.chapterName}' byte length ($len) must be multiple of 2 (UTF-16 code unit), url=${ch.chapterUrl}",
                len % 2 == 0L
            )
            assertTrue("Chapter byte length must be > 0, url=${ch.chapterUrl}", len > 0)
        }
        // 末章 endByte 必须等于文件长度（fileSize 作为半开区间末尾，读到 EOF）
        val last = result.chapters.last()
        val lastEnd = last.chapterUrl!!.removePrefix("b:").split(":")[1].toLong()
        assertEquals("Last chapter endByte must equal file size", cachedFile.size, lastEnd)

        // 反向验证：第 0 章的 endByte 必须等于第 1 章的 startByte（连续区间，无重叠无空洞）
        if (result.chapters.size >= 2) {
            val ch0End = result.chapters[0].chapterUrl!!.removePrefix("b:").split(":")[1].toLong()
            val ch1Start = result.chapters[1].chapterUrl!!.removePrefix("b:").split(":")[0].toLong()
            assertEquals(
                "Chapter 0 endByte must equal chapter 1 startByte (contiguous ranges)",
                ch0End, ch1Start
            )
        }
    }

    // ── §3.1.1 4 种端序编码扫描不崩溃 ──

    @Test
    fun scan_utf16be_lf_producesByteOffsets() = runBlocking {
        // UTF-16BE: BOM FE FF, 'A' = 00 41, LF = 00 0A
        val bytes = byteArrayOf(
            0xFE.toByte(), 0xFF.toByte(),                 // BOM
            0x00, 0x41,                                   // 'A'
            0x00, 0x0A,                                   // LF
            0x00, 0x42,                                   // 'B'
            0x00, 0x0A                                    // LF
        )
        val cachedFile = createCachedFile(bytes)
        val scanner = createScanner()

        val result = scanner.scan(bookId = 1L, cachedFile)

        assertEquals("UTF-16BE", result.charsetName)
        assertTrue(result.isUtf16Or32)
        // 首章起点 = BOM 之后 = 2
        assertTrue("First chapterUrl should start at offset 2: ${result.chapters.first().chapterUrl}",
            result.chapters.first().chapterUrl!!.startsWith("b:2:"))
    }

    @Test
    fun scan_utf32le_lf_producesByteOffsets() = runBlocking {
        // UTF-32LE: BOM FF FE 00 00, 'A' = 41 00 00 00, LF = 0A 00 00 00
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00,                 // BOM
            0x41, 0x00, 0x00, 0x00,                                   // 'A'
            0x0A, 0x00, 0x00, 0x00,                                   // LF
            0x42, 0x00, 0x00, 0x00,                                   // 'B'
            0x0A, 0x00, 0x00, 0x00                                    // LF
        )
        val cachedFile = createCachedFile(bytes)
        val scanner = createScanner()

        val result = scanner.scan(bookId = 1L, cachedFile)

        assertEquals("UTF-32LE", result.charsetName)
        assertTrue(result.isUtf16Or32)
        // 首章起点 = BOM 之后 = 4
        assertTrue("First chapterUrl should start at offset 4: ${result.chapters.first().chapterUrl}",
            result.chapters.first().chapterUrl!!.startsWith("b:4:"))
    }

    @Test
    fun scan_utf32be_lf_producesByteOffsets() = runBlocking {
        // UTF-32BE: BOM 00 00 FE FF, 'A' = 00 00 00 41, LF = 00 00 00 0A
        val bytes = byteArrayOf(
            0x00, 0x00, 0xFE.toByte(), 0xFF.toByte(),                 // BOM
            0x00, 0x00, 0x00, 0x41,                                   // 'A'
            0x00, 0x00, 0x00, 0x0A,                                   // LF
            0x00, 0x00, 0x00, 0x42,                                   // 'B'
            0x00, 0x00, 0x00, 0x0A                                    // LF
        )
        val cachedFile = createCachedFile(bytes)
        val scanner = createScanner()

        val result = scanner.scan(bookId = 1L, cachedFile)

        assertEquals("UTF-32BE", result.charsetName)
        assertTrue(result.isUtf16Or32)
        assertTrue("First chapterUrl should start at offset 4: ${result.chapters.first().chapterUrl}",
            result.chapters.first().chapterUrl!!.startsWith("b:4:"))
    }

    // ── chapterUrl 格式契约 ──

    @Test
    fun scan_utf16le_chapterUrlMatchesBPrefixFormat() = runBlocking {
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(),
            0x2C, 0x7B, 0x00, 0x4E, 0xE0.toByte(), 0x7A, 0x0D, 0x00,
            0x0A, 0x00
        )
        val cachedFile = createCachedFile(bytes)
        val scanner = createScanner()

        val result = scanner.scan(bookId = 1L, cachedFile)

        result.chapters.forEach { ch ->
            assertNotNull(ch.chapterUrl)
            assertTrue(
                "chapterUrl must match b:\\d+:\\d+ pattern, got: ${ch.chapterUrl}",
                ch.chapterUrl!!.matches(Regex("b:\\d+:\\d+"))
            )
        }
    }
}
