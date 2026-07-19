package com.wxn.bookparser.parser.txt

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class JuniversalCharsetDetectorTest {

    private val detector = JuniversalCharsetDetector()

    @Test
    fun detect_utf8Bom() {
        val bytes = byteArrayOf(
            0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(),
            0xE4.toByte(), 0xB8.toByte(), 0xAD.toByte(),
            0xE6.toByte(), 0x96.toByte(), 0x87.toByte()
        )
        val result = detector.detect(bytes)
        assertEquals("UTF-8", result.charsetName)
        assertFalse(result.isUtf16Or32)
    }

    @Test
    fun detect_utf16BeBom() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x00, 0x61)
        val result = detector.detect(bytes)
        assertEquals("UTF-16BE", result.charsetName)
        assertTrue(result.isUtf16Or32)
    }

    @Test
    fun detect_utf16LeBom() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x61, 0x00)
        val result = detector.detect(bytes)
        assertEquals("UTF-16LE", result.charsetName)
        assertTrue(result.isUtf16Or32)
    }

    @Test
    fun detect_utf32LeBom() {
        val bytes = byteArrayOf(
            0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00,
            0x61, 0x00, 0x00, 0x00
        )
        val result = detector.detect(bytes)
        assertEquals("UTF-32LE", result.charsetName)
        assertTrue(result.isUtf16Or32)
    }

    @Test
    fun detect_utf32BeBom() {
        val bytes = byteArrayOf(
            0x00, 0x00, 0xFE.toByte(), 0xFF.toByte(),
            0x00, 0x00, 0x00, 0x61
        )
        val result = detector.detect(bytes)
        assertEquals("UTF-32BE", result.charsetName)
        assertTrue(result.isUtf16Or32)
    }

    @Test
    fun detect_emptyBytes_fallbackToUtf8() {
        val bytes = byteArrayOf()
        val result = detector.detect(bytes)
        assertEquals("UTF-8", result.charsetName)
        assertFalse(result.isUtf16Or32)
    }

    @Test
    fun detect_utf8NoBom() {
        val text = "Hello, World! 你好，世界！"
        val bytes = text.toByteArray(Charset.forName("UTF-8"))
        val result = detector.detect(bytes)
        assertTrue("Expected UTF-8 but got ${result.charsetName}",
            result.charsetName.equals("UTF-8", ignoreCase = true))
    }

    @Test
    fun detect_gbk() {
        val text = "第一章 测试文本。这里是更长的中文文本内容，用于编码检测。"
        val bytes = text.toByteArray(Charset.forName("GBK"))
        val result = detector.detect(bytes)
        assertTrue("Expected GBK/GB18030 but got ${result.charsetName}",
            result.charsetName.uppercase().let { it == "GBK" || it == "GB18030" })
    }

    @Test
    fun detect_shiftJis() {
        val text = "日本語のテキスト"
        val bytes = text.toByteArray(Charset.forName("Shift_JIS"))
        val result = detector.detect(bytes)
        assertTrue("Expected Shift_JIS but got ${result.charsetName}",
            result.charsetName.equals("Shift_JIS", ignoreCase = true) ||
                    result.charsetName.equals("SHIFT_JIS", ignoreCase = true))
    }

    @Test
    fun detect_eucKr() {
        val text = "한국어 텍스트"
        val bytes = text.toByteArray(Charset.forName("EUC-KR"))
        val result = detector.detect(bytes)
        assertTrue("Expected EUC-KR but got ${result.charsetName}",
            result.charsetName.equals("EUC-KR", ignoreCase = true))
    }

    @Test
    fun detect_big5() {
        val text = "繁體中文測試。這是更長的繁體中文文本內容，用於編碼檢測。"
        val bytes = text.toByteArray(Charset.forName("Big5"))
        val result = detector.detect(bytes)
        assertTrue("Expected BIG5 but got ${result.charsetName}",
            result.charsetName.uppercase() == "BIG5")
    }

    @Test
    fun detect_windows1252() {
        val text = "Caf\u00E9 r\u00E9sum\u00E9 na\u00EFve fa\u00E7ade"
        val bytes = text.toByteArray(Charset.forName("windows-1252"))
        val result = detector.detect(bytes)
        assertTrue("Expected WINDOWS-1252 but got ${result.charsetName}",
            result.charsetName.equals("WINDOWS-1252", ignoreCase = true))
    }

    @Test
    fun detect_iso8859_1() {
        val text = "Caf\u00E9 r\u00E9sum\u00E9 na\u00EFve"
        val bytes = text.toByteArray(Charset.forName("ISO-8859-1"))
        val result = detector.detect(bytes)
        assertTrue("Expected ISO-8859-1 or WINDOWS-1252 but got ${result.charsetName}",
            result.charsetName.equals("ISO-8859-1", ignoreCase = true) ||
            result.charsetName.equals("WINDOWS-1252", ignoreCase = true))
    }

    @Test
    fun detect_ascii() {
        val text = "Hello World! This is plain ASCII text."
        val bytes = text.toByteArray(Charset.forName("US-ASCII"))
        val result = detector.detect(bytes)
        assertTrue("Expected ASCII/WINDOWS-1252 but got ${result.charsetName}",
            result.charsetName.equals("US-ASCII", ignoreCase = true) ||
                    result.charsetName.equals("WINDOWS-1252", ignoreCase = true) ||
                    result.charsetName.equals("ASCII", ignoreCase = true))
    }

    @Test
    fun isUtf16Or32_false_forUtf8() {
        val bytes = "Hello".toByteArray(Charset.forName("UTF-8"))
        val result = detector.detect(bytes)
        assertFalse(result.isUtf16Or32)
    }

    @Test
    fun isUtf16Or32_true_forUtf16Be() {
        val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte(), 0x00, 0x48)
        val result = detector.detect(bytes)
        assertTrue(result.isUtf16Or32)
    }

    @Test
    fun isUtf16Or32_true_forUtf16Le() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x48, 0x00)
        val result = detector.detect(bytes)
        assertTrue(result.isUtf16Or32)
    }
}
