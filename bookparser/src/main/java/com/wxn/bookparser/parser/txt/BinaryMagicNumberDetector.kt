package com.wxn.bookparser.parser.txt

/**
 * 二进制文件魔数检测器：判断一段字节头部是否属于常见二进制格式（图片 / 电子书 / 文档）。
 *
 * **用途**：TXT 解析器在 [ChapterScanner.scan] 入口处对文件头做一次魔数扫描，命中则抛
 * [com.wxn.base.exception.NotTextFileException]，避免把 JPEG / PNG / PDF / ZIP / MOBI
 * 等二进制文件按文本强行解码产生大段乱码（典型场景：来源不可靠的 .txt 实为图片或他格式电子书）。
 *
 * **设计要点**：
 * - 纯 JVM 工具类（无 Android 依赖），便于单元测试；
 * - 只覆盖中文电子书场景常见的二进制格式（图片 + PDF/ZIP/EPUB/MOBI），不含 ELF/PE/RAR；
 * - 输入是文件头字节（调用方通常读前 64 KiB），魔数检查只看前几十字节，开销可忽略；
 * - 不命中返回 null（调用方据此放行走文本路径），命中返回类型名供 UI/日志精确提示。
 *
 * 参考既有 `com.wxn.reader.util.download.FileValidator`（app 模块，走 File + 扩展名分派）；
 * 本类独立放 bookparser（走 ByteArray + 无 Android 依赖）。
 */
internal object BinaryMagicNumberDetector {

    /**
     * 检测 [header] 是否为已知二进制格式。
     *
     * @param header 文件头字节（建议至少 64 字节；MOBI 检测需读到偏移 60）。短于所需长度时按可检测范围判定。
     * @return 命中时返回类型名（如 `"JPEG"` / `"PNG"` / `"PDF"` / `"ZIP"` / `"MOBI"`）；
     *         不命中或无法判定时返回 null。
     */
    fun detect(header: ByteArray): String? {
        if (header.isEmpty()) return null

        // ── 图片：前缀魔数 ──
        if (startsWith(header, JPEG)) return "JPEG"
        if (startsWith(header, PNG)) return "PNG"
        if (startsWith(header, GIF87A) || startsWith(header, GIF89A)) return "GIF"
        // WebP：RIPP....WEBP —— 偏移 0 是 RIFF，偏移 8 是 WEBP
        if (startsWith(header, RIFF) && startsAt(header, 8, WEBP)) return "WebP"

        // ── 文档 / 电子书 ──
        if (startsWith(header, PDF)) return "PDF"
        if (startsWith(header, ZIP_LOCAL) || startsWith(header, ZIP_SPAN) || startsWith(header, ZIP_EMPTY)) {
            // ZIP 既可能是 EPUB 也可能是普通压缩包，统一标记 ZIP（UI 不区分）
            return "ZIP"
        }
        // MOBI / AZW3：BOOKMOBI 标记位于 PalmDB 头部偏移 60 处
        if (startsAt(header, 60, BOOK_MOBI)) return "MOBI"

        return null
    }

    // ── 魔数常量 ──

    // JPEG SOI + APP0 marker 起始（FF D8 FF；不限定第 3 字节，FF E0/FF E1 等均合法）
    private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val PNG = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A  // \x89PNG\r\n\x1A\n
    )
    private val GIF87A = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x37, 0x61) // "GIF87a"
    private val GIF89A = byteArrayOf(0x47, 0x49, 0x46, 0x38, 0x39, 0x61) // "GIF89a"
    private val RIFF = byteArrayOf(0x52, 0x49, 0x46, 0x46)               // "RIFF"
    private val WEBP = byteArrayOf(0x57, 0x45, 0x42, 0x50)               // "WEBP"
    private val PDF = byteArrayOf(0x25, 0x50, 0x44, 0x46)                // "%PDF"
    // ZIP 三种签名：本地文件头 / 跨卷 / 空归档
    private val ZIP_LOCAL = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val ZIP_SPAN = byteArrayOf(0x50, 0x4B, 0x07, 0x08)
    private val ZIP_EMPTY = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
    // MOBI/AZW3：PalmDB 偏移 60 处的 "BOOKMOBI"
    private val BOOK_MOBI = byteArrayOf(
        0x42, 0x4F, 0x4F, 0x4B, 0x4D, 0x4F, 0x42, 0x49
    )

    // ── 字节匹配辅助 ──

    private fun startsWith(header: ByteArray, sig: ByteArray): Boolean =
        startsAt(header, 0, sig)

    /** 从 [offset] 起比较 [sig]；偏移越界或剩余字节不足时返回 false（不抛越界异常）。 */
    private fun startsAt(header: ByteArray, offset: Int, sig: ByteArray): Boolean {
        if (offset < 0 || header.size - offset < sig.size) return false
        for (i in sig.indices) {
            if (header[offset + i] != sig[i]) return false
        }
        return true
    }
}
