package com.wxn.reader.util.io

import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext

/**
 * 备份还原封面同步专用 IO 工具。
 *
 * 红线：
 * - 所有封面字节流式读写，禁止 `readBytes()` 整文件入内存。
 * - 所有 copy 操作必须可被协程取消（每 buffer `ensureActive`）。
 * - 所有 ZIP entry 落地必须经 [safeCoverFile] 做 ZipSlip 校验。
 */
object CoverSyncIO {

    /** 单封面字节流的 buffer 大小（8KB）。 */
    private const val BUFFER_SIZE = 8 * 1024

    /**
     * 流式 copy，每 buffer 检查协程取消（红线 #9）。
     *
     * 注意：标准库 `InputStream.copyTo` 不检查取消，5MB 封面 copy 期间取消按钮会卡死。
     * 本函数每个 buffer 周期 `ensureActive`，取消响应延迟 ≤ 1 个 buffer 周期。
     * `CancellationException` 向上传播，由调用方决定是否清理临时文件。
     */
    suspend fun copyToCancellable(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int = BUFFER_SIZE,
    ) {
        val buf = ByteArray(bufferSize)
        while (true) {
            coroutineContext.ensureActive()
            val n = input.read(buf)
            if (n <= 0) break
            output.write(buf, 0, n)
        }
    }

    /**
     * ZipSlip 校验：确认 [entryName] 落地到 `filesDir/covers/` 之下，不越界。
     *
     * 防御恶意/损坏 ZIP 构造 `covers/../../evil.jpg` 写到 covers 目录之外，覆盖任意私有文件。
     * 用 `canonicalFile` 解析 `..` 与符号链接，再 `startsWith` 比对根目录。
     *
     * @return 校验通过的目标文件；否则 null（调用方静默跳过 + warning）。
     */
    fun safeCoverFile(filesDir: File, entryName: String): File? {
        if (!entryName.startsWith("covers/")) return null
        if (entryName.contains("..")) return null // 双保险，canonical 已防，但显式更清晰
        val coversRoot = File(filesDir, "covers").apply { mkdirs() }.canonicalFile
        val target = File(filesDir, entryName).canonicalFile
        return if (target.startsWith(coversRoot)) target else null
    }
}
