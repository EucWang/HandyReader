package com.wxn.bookparser.parser.txt

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.wxn.base.exception.NotTextFileException
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.file.CachedFileBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

class ChapterScannerTest {

    // ── isChapterCandidate ──

    @Test
    fun isChapterCandidate_empty_false() {
        assertFalse(isChapterCandidate(""))
    }

    @Test
    fun isChapterCandidate_chineseMarkers_true() {
        assertTrue(isChapterCandidate("第一章 测试"))
        assertTrue(isChapterCandidate("序言"))
        assertTrue(isChapterCandidate("楔子"))
        assertTrue(isChapterCandidate("前言"))
        assertTrue(isChapterCandidate("后记"))
        assertTrue(isChapterCandidate("尾声"))
        assertTrue(isChapterCandidate("引子"))
        assertTrue(isChapterCandidate("跋文"))
    }

    @Test
    fun isChapterCandidate_englishMarkers_true() {
        assertTrue(isChapterCandidate("Chapter 1"))
        assertTrue(isChapterCandidate("Chapter One"))
        assertTrue(isChapterCandidate("Prologue"))
        assertTrue(isChapterCandidate("Epilogue"))
        assertTrue(isChapterCandidate("Preface"))
        assertTrue(isChapterCandidate("Chapter 10"))
    }

    @Test
    fun isChapterCandidate_englishLowercase_true() {
        assertTrue(isChapterCandidate("chapter 1"))
        assertTrue(isChapterCandidate("prologue"))
        assertTrue(isChapterCandidate("epilogue"))
    }

    @Test
    fun isChapterCandidate_cyrillic_true() {
        assertTrue(isChapterCandidate("\u0413\u043B\u0430\u0432\u0430 1"))
    }

    @Test
    fun isChapterCandidate_devanagari_true() {
        assertTrue(isChapterCandidate("\u092A\u094D\u0930\u0938\u094D\u0924\u093E\u0935\u0928\u093E"))
    }

    @Test
    fun isChapterCandidate_arabic_true() {
        assertTrue(isChapterCandidate("\u0627\u0644\u0641\u0635\u0644 \u0627\u0644\u0623\u0648\u0644"))
    }

    @Test
    fun isChapterCandidate_korean_true() {
        assertTrue(isChapterCandidate("\uC81C1\uC7A5"))
    }

    @Test
    fun isChapterCandidate_nonMatching_false() {
        assertFalse(isChapterCandidate("123456"))
        assertFalse(isChapterCandidate("   leading spaces"))
        assertFalse(isChapterCandidate("· · ·"))
        assertFalse(isChapterCandidate("……"))
    }

    @Test
    fun isChapterCandidate_commonWords_passFilter_thenJniRejects() {
        // These pass the loose pre-filter but would be rejected by the JNI matcher.
        // The pre-filter is intentionally broad to avoid missing valid chapter titles.
        assertTrue(isChapterCandidate("This is just a paragraph"))
        assertTrue(isChapterCandidate("Some random text"))
        assertTrue(isChapterCandidate("But this is not a chapter"))
        assertTrue(isChapterCandidate("A man walked into a bar"))
    }

    @Test
    fun isChapterCandidate_blankAfterTrim_false() {
        assertFalse(isChapterCandidate("   "))
    }

    // ── P2-8: extended English markers ──

    @Test
    fun isChapterCandidate_englishAllMarkers_true() {
        assertTrue(isChapterCandidate("Section 1"))
        assertTrue(isChapterCandidate("Scene 2"))
        assertTrue(isChapterCandidate("Subtitle"))
        assertTrue(isChapterCandidate("Volume 1"))
        assertTrue(isChapterCandidate("Book One"))
        assertTrue(isChapterCandidate("Act III"))
        assertTrue(isChapterCandidate("Appendix A"))
        assertTrue(isChapterCandidate("Article 5"))
        assertTrue(isChapterCandidate("Index"))
        assertTrue(isChapterCandidate("Introduction"))
        assertTrue(isChapterCandidate("Interlude"))
        assertTrue(isChapterCandidate("Track 1"))
        assertTrue(isChapterCandidate("Title"))
    }

    // ── P1-3: computeWordCount ──

    @Test
    fun computeWordCount_normalRange_correctSum() {
        val scanner = createScanner()
        val prefixSums = longArrayOf(10, 30, 45, 60, 80, 100)
        val chapter = RawChapter("Ch1", 0, 0, startLine = 1, endLine = 3, false)
        // lines 1..3 => 30+45+60 = 135... wait, prefixSums = cumulative
        // prefixSums[3] - prefixSums[0] = 60 - 10 = 50
        assertEquals(50L, scanner.computeWordCount(chapter, prefixSums))
    }

    @Test
    fun computeWordCount_singleLine_correct() {
        val scanner = createScanner()
        val prefixSums = longArrayOf(10, 25, 40)
        val chapter = RawChapter("Ch1", 0, 0, startLine = 1, endLine = 1, false)
        // prefixSums[1] - prefixSums[0] = 25 - 10 = 15
        assertEquals(15L, scanner.computeWordCount(chapter, prefixSums))
    }

    @Test
    fun computeWordCount_startAtZero_correct() {
        val scanner = createScanner()
        val prefixSums = longArrayOf(10, 25, 40)
        val chapter = RawChapter("Ch1", 0, 0, startLine = 0, endLine = 2, false)
        // prefixSums[2] - 0 = 40
        assertEquals(40L, scanner.computeWordCount(chapter, prefixSums))
    }

    @Test
    fun computeWordCount_emptyPrefixSums_returnsZero() {
        val scanner = createScanner()
        val prefixSums = LongArray(0)
        val chapter = RawChapter("Ch1", 0, 0, startLine = 0, endLine = 5, false)
        assertEquals(0L, scanner.computeWordCount(chapter, prefixSums))
    }

    @Test
    fun computeWordCount_startLineGreaterThanEndLine_returnsZero() {
        val scanner = createScanner()
        val prefixSums = longArrayOf(10, 25, 40)
        val chapter = RawChapter("Ch1", 0, 0, startLine = 5, endLine = 2, false)
        assertEquals(0L, scanner.computeWordCount(chapter, prefixSums))
    }

    @Test
    fun computeWordCount_endLineOutOfBounds_coerced() {
        val scanner = createScanner()
        val prefixSums = longArrayOf(10, 25, 40)
        val chapter = RawChapter("Ch1", 0, 0, startLine = 1, endLine = 100, false)
        // endIdx coerced to 2, startIdx = 1
        // prefixSums[2] - prefixSums[0] = 40 - 10 = 30
        assertEquals(30L, scanner.computeWordCount(chapter, prefixSums))
    }

    @Test
    fun computeWordCount_startLineOutOfBounds_returnsZero() {
        val scanner = createScanner()
        val prefixSums = longArrayOf(10, 25, 40)
        val chapter = RawChapter("Ch1", 0, 0, startLine = 5, endLine = 100, false)
        assertEquals(0L, scanner.computeWordCount(chapter, prefixSums))
    }

    // ── applyDedup ──

    @Test
    fun applyDedup_empty_returnsEmpty() {
        val scanner = createScanner()
        val result = scanner.applyDedup(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun applyDedup_single_returnsSame() {
        val scanner = createScanner()
        val item = RawChapter("Chapter 1", 0, 100, 0, 5, false)
        val result = scanner.applyDedup(listOf(item))
        assertEquals(1, result.size)
        assertEquals("Chapter 1", result[0].name)
    }

    @Test
    fun applyDedup_consecutiveNumbers_keepsBoth() {
        // v4 修订：连续递增章节号 → 两者保留（豁免距离）
        // 取代旧的 applyDedup_adjacentChapters_keepsFirst（该测试固化了"连续短章节被吞"bug）
        val scanner = createScanner()
        val items = listOf(
            RawChapter("第一章", 0, 50, 0, 1, false, number = 1),
            RawChapter("第二章", 51, 100, 2, 3, false, number = 2)
        )
        val result = scanner.applyDedup(items)
        assertEquals(2, result.size)
    }

    @Test
    fun applyDedup_sameNumberClose_dropsSecond() {
        // 近距离 + 同号 → 真重复识别（markdown/空白差异），丢弃 curr
        val scanner = createScanner()
        val items = listOf(
            RawChapter("第一章", 0, 50, 0, 1, false, number = 1),
            RawChapter("第一章", 51, 100, 2, 3, false, number = 1)
        )
        val result = scanner.applyDedup(items)
        assertEquals(1, result.size)
    }

    @Test
    fun applyDedup_sameNumberFar_keepsBoth() {
        // 距离 ≥ 3 行 AND ≥ 150 字符 → 不同章节巧合同号（如上下篇第一章），保留
        val scanner = createScanner()
        // 行 0-5 和 行 10-15，行距 = 10 - 5 = 5 ≥ 3；构造字符距也 ≥ 150
        val prefix = LongArray(20) { (it + 1) * 50L }   // 每行 50 字符，行 5 = 300，行 10 = 500，gap = 200
        val items = listOf(
            RawChapter("第一章", 0, 250, 0, 5, false, number = 1),
            RawChapter("第一章", 251, 750, 10, 15, false, number = 1)
        )
        val result = scanner.applyDedup(items, prefix)
        assertEquals(2, result.size)
    }

    @Test
    fun applyDedup_numberJump_keepsBoth() {
        // 数字跳跃（1 → 3，跳过 2）→ 保守保留
        val scanner = createScanner()
        val items = listOf(
            RawChapter("第一章", 0, 50, 0, 1, false, number = 1),
            RawChapter("第三章", 51, 100, 2, 3, false, number = 3)
        )
        val result = scanner.applyDedup(items)
        assertEquals(2, result.size)
    }

    @Test
    fun applyDedup_noNumberClose_keepsBoth() {
        // 近距离 + 无 number → 保留（matcher 边界精确，不存在对话误判）
        val scanner = createScanner()
        val items = listOf(
            RawChapter("序章", 0, 50, 0, 1, false),
            RawChapter("楔子", 51, 100, 2, 3, false)
        )
        val result = scanner.applyDedup(items)
        assertEquals(2, result.size)
    }

    @Test
    fun applyDedup_asciiConsecutive_keepsBoth() {
        // 英文章节连续递增（Chapter 1 → Chapter 2）
        val scanner = createScanner()
        val items = listOf(
            RawChapter("Chapter 1", 0, 50, 0, 1, false, number = 1),
            RawChapter("Chapter 2", 51, 100, 2, 3, false, number = 2)
        )
        val result = scanner.applyDedup(items)
        assertEquals(2, result.size)
    }

    @Test
    fun applyDedup_sameNumberCloseByChars_dropsSecond() {
        // 行距 ≥ 3 但字符距 < 150 → OR 语义判为近距离 + 同号 → 丢弃 curr
        val scanner = createScanner()
        // 行距：行 5 到 行 10 = 5 ≥ 3 OK；但每行只有 10 字符 → gap ≈ 50 < 150
        val prefix = LongArray(20) { (it + 1) * 10L }
        val items = listOf(
            RawChapter("第一章", 0, 50, 0, 5, false, number = 1),
            RawChapter("第一章", 51, 100, 10, 15, false, number = 1)
        )
        val result = scanner.applyDedup(items, prefix)
        assertEquals(1, result.size)
    }

    @Test
    fun applyDedup_sameNumberCloseByLines_dropsSecond() {
        // 行距 < 3 即近距离（OR 语义），同号 → 丢弃 curr
        // 这条验证：字符距 ≥ 150 不影响 OR 判定，行距 < 3 已满足近距离
        val scanner = createScanner()
        val prefix = LongArray(10) { (it + 1) * 200L }
        val items = listOf(
            RawChapter("第一章", 0, 200, 0, 1, false, number = 1),
            RawChapter("第一章", 201, 400, 2, 3, false, number = 1)
        )
        val result = scanner.applyDedup(items, prefix)
        assertEquals("行距 < 3 即近距离，同号丢弃", 1, result.size)
    }

    @Test
    fun applyDedup_separatedChapters_keepsBoth() {
        val scanner = createScanner()
        val items = listOf(
            RawChapter("Chapter 1", 0, 50, 0, 5, false),
            RawChapter("Chapter 2", 51, 100, 10, 15, false)
        )
        val result = scanner.applyDedup(items)
        assertEquals(2, result.size)
    }

    @Test
    fun applyDedup_volumeReplacesChapter() {
        // v4：同号近距离 + curr 是卷 / prev 是章 → 卷替换章
        val scanner = createScanner()
        val items = listOf(
            RawChapter("第一章", 0, 50, 0, 2, false, number = 1),
            RawChapter("第一卷 测试", 50, 100, 2, 5, true, number = 1)
        )
        val result = scanner.applyDedup(items)
        assertEquals(1, result.size)
        assertTrue(result[0].isVolume)
    }

    @Test
    fun applyDedup_volumeDoesNotReplaceVolume() {
        // v4 修订：原测试期望「第一卷 + 第二卷」近距离吞第二卷，这是 bug 行为。
        // 不同卷号（1 → 2）应保留两者（数字递增豁免）。
        val scanner = createScanner()
        val items = listOf(
            RawChapter("第一卷", 0, 50, 0, 1, true, number = 1),
            RawChapter("第二卷", 50, 100, 2, 3, true, number = 2)
        )
        val result = scanner.applyDedup(items)
        assertEquals(2, result.size)   // v4：连续递增豁免
        assertTrue(result[0].isVolume)
        assertTrue(result[1].isVolume)
    }

    // ── uniformSplit ──

    @Test
    fun uniformSplit_empty_returnsEmpty() {
        val scanner = createScanner()
        val result = scanner.uniformSplit(emptyList(), emptyList(), 0, 0L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun uniformSplit_smallFile_returnsSingleSegment() {
        val scanner = createScanner()
        val charCounts = listOf(100, 200, 150)
        val byteOffsets = listOf(0L, 100L, 300L)
        val result = scanner.uniformSplit(charCounts, byteOffsets, 450, 450L)
        assertEquals(1, result.size)
        assertEquals("1", result[0].name)
        assertEquals(0, result[0].startLine)
        assertEquals(2, result[0].endLine)
    }

    @Test
    fun uniformSplit_largeFile_multipleSegments() {
        val scanner = createScanner()
        val charCounts = (1..10).map { 30000 }
        val byteOffsets = (1..10).map { it * 30000L }
        val result = scanner.uniformSplit(charCounts, byteOffsets, 300000, 300000L)
        assertEquals(2, result.size)
        assertEquals("1", result[0].name)
    }

    @Test
    fun uniformSplit_singleLine() {
        val scanner = createScanner()
        val charCounts = listOf(500)
        val byteOffsets = listOf(0L)
        val result = scanner.uniformSplit(charCounts, byteOffsets, 500, 500L)
        assertEquals(1, result.size)
        assertEquals(0, result[0].startLine)
        assertEquals(0, result[0].endLine)
    }

    @Test
    fun uniformSplit_verySmallLines() {
        val scanner = createScanner()
        val charCounts = (1..100).map { 10 }
        val byteOffsets = (1..100).map { it * 10L }
        val total = 1000
        val result = scanner.uniformSplit(charCounts, byteOffsets, total, total.toLong())
        assertEquals(1, result.size)
    }

    @Test
    fun uniformSplit_exactBoundary_singleSegment() {
        val scanner = createScanner()
        val charCounts = listOf(150000, 150000)
        val byteOffsets = listOf(0L, 150000L)
        val total = 300000
        val result = scanner.uniformSplit(charCounts, byteOffsets, total, total.toLong())
        assertEquals(1, result.size)
        assertEquals(0, result[0].startLine)
        assertEquals(1, result[0].endLine)
    }

    private fun createScanner(): ChapterScanner {
        val detector = object : TxtCharsetDetector {
            override fun detect(headerBytes: ByteArray): CharsetDetectionResult {
                return CharsetDetectionResult("UTF-8", false)
            }
        }
        return ChapterScanner(detector)
    }
}

// ── §3 二进制守卫：scan() 对伪装成 .txt 的二进制文件抛 NotTextFileException ──
//
// 这组测试需要构造真实 CachedFile（依赖 Android Context/Uri），用 Robolectric 跑。
// 与 TxtTextParserTest 同样受 targetSdk=36 > maxSdk=35 限制：如 Robolectric 在本机阻塞，
// 本组用例编译通过但不执行——核心守卫逻辑已被 BinaryMagicNumberDetectorTest（20 个纯 JVM 用例）完整覆盖，
// scan() 里只是 3 行直通的 detect() 调用。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChapterScannerBinaryGuardTest {

    private val context: Context by lazy {
        ApplicationProvider.getApplicationContext()
    }
    private val tempDir: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "scanner_guard_${System.nanoTime()}")
            .also { it.mkdirs() }
    }

    @Test
    fun scan_jpegBytes_throwsNotTextFileException() {
        val file = File(tempDir, "fake.txt")
        // 真实 JPEG 头：FF D8 FF E0 00 10 JFIF ... + 足够的占位字节
        file.writeBytes(
            byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
            ) + ByteArray(100)
        )
        val scanner = ChapterScanner(JuniversalCharsetDetector())
        val cachedFile = buildCachedFile(file)

        val ex = assertThrows(NotTextFileException::class.java) {
            runBlocking { scanner.scan(1L, cachedFile) }
        }
        assertEquals("JPEG", ex.detectedType)
    }

    @Test
    fun scan_pngBytes_throwsNotTextFileException() {
        val file = File(tempDir, "fake.txt")
        file.writeBytes(
            byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
            ) + ByteArray(100)
        )
        val scanner = ChapterScanner(JuniversalCharsetDetector())
        val cachedFile = buildCachedFile(file)

        val ex = assertThrows(NotTextFileException::class.java) {
            runBlocking { scanner.scan(1L, cachedFile) }
        }
        assertEquals("PNG", ex.detectedType)
    }

    @Test
    fun scan_pdfBytes_throwsNotTextFileException() {
        val file = File(tempDir, "fake.txt")
        file.writeBytes("%PDF-1.7\n%binary content here".toByteArray(Charsets.US_ASCII))
        val scanner = ChapterScanner(JuniversalCharsetDetector())
        val cachedFile = buildCachedFile(file)

        assertThrows(NotTextFileException::class.java) {
            runBlocking { scanner.scan(1L, cachedFile) }
        }
    }

    @Test
    fun scan_realTextFile_doesNotThrow() {
        // 回归：真 UTF-8 文本不应触发守卫（防误伤）
        val file = File(tempDir, "real.txt")
        file.writeBytes("第一章 开始\n正文内容 hello\n".toByteArray(Charsets.UTF_8))
        val scanner = ChapterScanner(JuniversalCharsetDetector())
        val cachedFile = buildCachedFile(file)

        // 不抛异常即通过（章节内容正确性由其他测试覆盖，这里只验证守卫不误伤）
        runBlocking {
            val result = scanner.scan(1L, cachedFile)
            assertNotNull(result)
        }
    }

    private fun buildCachedFile(file: File): CachedFile {
        return CachedFile(
            context = context,
            uri = Uri.fromFile(file),
            builder = CachedFileBuilder(
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                isDirectory = false
            )
        )
    }
}
