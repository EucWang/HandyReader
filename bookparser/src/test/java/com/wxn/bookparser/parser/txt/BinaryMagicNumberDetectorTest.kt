package com.wxn.bookparser.parser.txt

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

/**
 * [BinaryMagicNumberDetector] 单元测试。
 *
 * 覆盖：各二进制魔数命中、真实文本不误判、空/短 header 边界、MOBI 偏移 60 场景。
 * 纯 JVM 测试，无 Android / Robolectric 依赖。
 */
class BinaryMagicNumberDetectorTest {

    // ── 命中：二进制格式 ──

    @Test
    fun detect_jpeg() {
        // FF D8 FF E0 ...（Book 60 实际就是 JPEG，FF D8 FF 后跟任意 marker 字节均合法）
        val header = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, 0x4A, 0x46, 0x49, 0x46   // ...JFIF
        )
        assertEquals("JPEG", BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_jpeg_withExifMarker() {
        // FF D8 FF E1（EXIF）也应命中
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte())
        assertEquals("JPEG", BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_png() {
        val header = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        )
        assertEquals("PNG", BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_gif87a() {
        val header = "GIF87a".toByteArray(Charsets.US_ASCII) + ByteArray(10)
        assertEquals("GIF", BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_gif89a() {
        val header = "GIF89a".toByteArray(Charsets.US_ASCII) + ByteArray(10)
        assertEquals("GIF", BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_webp() {
        // RIFF....WEBP
        val header = byteArrayOf(
            0x52, 0x49, 0x46, 0x46,             // "RIFF"
            0x00, 0x00, 0x00, 0x00,             // file size (placeholder)
            0x57, 0x45, 0x42, 0x50              // "WEBP" at offset 8
        )
        assertEquals("WebP", BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_pdf() {
        val header = "%PDF-1.7\n%.txt rest".toByteArray(Charsets.US_ASCII)
        assertEquals("PDF", BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_zip_localFileHeader() {
        // PK\x03\x04（EPUB / 普通 ZIP 本地文件头）
        val header = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(20)
        assertEquals("ZIP", BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_zip_spanned() {
        val header = byteArrayOf(0x50, 0x4B, 0x07, 0x08) + ByteArray(20)
        assertEquals("ZIP", BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_zip_empty() {
        val header = byteArrayOf(0x50, 0x4B, 0x05, 0x06) + ByteArray(20)
        assertEquals("ZIP", BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_mobi_bookAtOffset60() {
        // PalmDB 头部 60 字节填充 + offset 60 处 "BOOKMOBI"
        val header = ByteArray(68)
        val bookMobi = "BOOKMOBI".toByteArray(Charsets.US_ASCII)
        System.arraycopy(bookMobi, 0, header, 60, bookMobi.size)
        assertEquals("MOBI", BinaryMagicNumberDetector.detect(header))
    }

    // ── 不误判：真实文本 header ──

    @Test
    fun detect_utf8Text_returnsNull() {
        val header = "第一章 开始\n正文内容 hello world".toByteArray(Charsets.UTF_8)
        assertNull(BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_gbkText_returnsNull() {
        val header = "第一章 开始\n正文内容".toByteArray(Charset.forName("GBK"))
        assertNull(BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_big5Text_returnsNull() {
        val header = "第一章 開始\n正文內容".toByteArray(Charset.forName("Big5"))
        assertNull(BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_pureAscii_returnsNull() {
        val header = "Chapter 1\nHello world\n".toByteArray(Charsets.US_ASCII)
        assertNull(BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_utf16LeBom_returnsNull() {
        // UTF-16LE 文本：FF FE 是 BOM，不是 JPEG/PNG 魔数，不应误判
        val header = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x61, 0x00, 0x62, 0x00)
        assertNull(BinaryMagicNumberDetector.detect(header))
    }

    @Test
    fun detect_utf8Bom_returnsNull() {
        // UTF-8 BOM EF BB BF 不是任何已知二进制魔数
        val header = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "正文".toByteArray(Charsets.UTF_8)
        assertNull(BinaryMagicNumberDetector.detect(header))
    }

    // ── 边界：空 / 短 header ──

    @Test
    fun detect_emptyArray_returnsNull() {
        assertNull(BinaryMagicNumberDetector.detect(ByteArray(0)))
    }

    @Test
    fun detect_shortHeader_returnsNull() {
        // 短于任何魔数长度，无法判定 → null（不抛越界异常）
        assertNull(BinaryMagicNumberDetector.detect(byteArrayOf(0x41, 0x42)))  // "AB"
    }

    @Test
    fun detect_shortHeader_lessThanMobiOffset_returnsNull() {
        // 只读到 30 字节，不足以到 MOBI offset 60 → 不命中 MOBI（也不应越界）
        val header = ByteArray(30) { 0x00 }
        assertNull(BinaryMagicNumberDetector.detect(header))
    }
}
