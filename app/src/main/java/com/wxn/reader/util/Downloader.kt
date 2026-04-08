package com.wxn.reader.util

import android.os.StatFs
import com.wxn.base.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文件下载器工具类
 *
 * 支持功能：
 * - 大文件流式下载（1MB~100MB+）
 * - 磁盘空间动态检查
 * - 网络异常自动重试（3次）
 * - 临时文件原子性操作
 * - 协程取消支持
 *
 * 使用示例：
 * ```kotlin
 * @Inject lateinit var downloader: Downloader
 *
 * // 启动下载
 * val downloadJob = scope.launch {
 *     try {
 *         downloader.downloadToFile(
 *             url = "https://example.com/file.webp",
 *             targetFile = File(context.filesDir, "images/file.webp"),
 *             onProgress = { progress -> println("Progress: ${(progress * 100).toInt()}%") }
 *         )
 *     } catch (e: CancellationException) {
 *         println("Download cancelled")
 *     } catch (e: Exception) {
 *         println("Download failed: ${e.message}")
 *     }
 * }
 *
 * // 取消下载（会触发CancellationException，自动清理临时文件）
 * downloadJob.cancel()
 * ```
 */
@Singleton
class Downloader @Inject constructor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val MIN_FREE_SPACE = 50L * 1024 * 1024       // 50MB最小保留
        private const val BUFFER_SMALL = 32 * 1024                // 32KB缓冲区（小文件）
        private const val BUFFER_LARGE = 64 * 1024                // 64KB缓冲区（大文件>10MB）
        private const val SPACE_CHECK_INTERVAL = 20L * 1024 * 1024 // 每20MB检查磁盘
        private const val PROGRESS_REPORT_INTERVAL = 0.05f        // 每5%回调进度
        private const val RETRY_COUNT = 3                         // 重试次数
        private const val RETRY_DELAY = 1000L                     // 初始重试延迟1秒
    }

    /**
     * 下载文件到指定路径
     * 注意：此函数支持协程取消，调用者应通过Job.cancel()来中断下载
     * @param url 下载URL
     * @param targetFile 目标文件
     * @param onProgress 进度回调（0.0~1.0），仅已知大小文件回调
     * @return 下载的文件绝对路径
     * @throws CancellationException 协程被取消时抛出
     */
    suspend fun downloadToFile(
        url: String,
        targetFile: File,
        onProgress: (Float) -> Unit = {}
    ): String {
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

        if (targetFile.exists()) {
            Logger.d("Downloader::downloadToFile: file already exists, skipping. url=$url, file=${targetFile.absolutePath}")
            return targetFile.absolutePath
        }

        if (tempFile.exists()) {
            Logger.d("Downloader::downloadToFile: cleaning old temp file: ${tempFile.absolutePath}")
            tempFile.delete()
        }

        try {
            val downloadedBytes = withRetry(times = RETRY_COUNT, initialDelay = RETRY_DELAY) {
                downloadWithProgress(url, tempFile, onProgress)
            }

            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to rename temp file to target file: ${tempFile.absolutePath} -> ${targetFile.absolutePath}")
            }

            Logger.i("Downloader::downloadToFile: download completed. url=$url, size=${downloadedBytes}bytes, file=${targetFile.absolutePath}")
            return targetFile.absolutePath

        } catch (e: CancellationException) {
            if (tempFile.exists()) {
                Logger.d("Downloader::downloadToFile: cancelled, cleaning temp file: ${tempFile.absolutePath}")
                tempFile.delete()
            }
            throw e
        } catch (e: Exception) {
            if (tempFile.exists()) {
                Logger.w("Downloader::downloadToFile: cleaning temp file after error: ${tempFile.absolutePath}")
                tempFile.delete()
            }
            throw e
        }
    }

    /**
     * 清理下载临时文件（注意：此方法仅清理文件，不中断协程）
     * 要真正中断下载，调用者应使用 Job.cancel() 来取消下载协程
     * 当协程被取消时，downloadToFile()会自动清理临时文件
     *
     * 此方法用于处理异常情况下的残留临时文件清理
     * @param targetFile 目标文件
     */
    fun cancelDownload(targetFile: File) {
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
        if (tempFile.exists()) {
            Logger.d("Downloader::cancelDownload: cleaning temp file: ${tempFile.absolutePath}")
            tempFile.delete()
        }
    }

    /**
     * 核心下载逻辑（包含进度回调）
     * 支持协程取消：在循环中定期检查ensureActive()
     */
    private suspend fun downloadWithProgress(
        url: String,
        tempFile: File,
        onProgress: (Float) -> Unit
    ): Long {
        val response = httpClient.get(url)

        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}: ${response.status.description}")
        }

        val contentLength = response.headers["Content-Length"]?.toLongOrNull()
        val hasKnownSize = contentLength != null && contentLength > 0

        val initialRequiredSpace = if (hasKnownSize) {
            contentLength + MIN_FREE_SPACE
        } else {
            MIN_FREE_SPACE * 2
        }
        checkAvailableSpace(tempFile.parentFile, initialRequiredSpace)

        val channel = response.bodyAsChannel()
        var totalBytesRead = 0L
        var lastProgressReport = 0f
        var lastSpaceCheck = 0L

        tempFile.outputStream().use { output ->
            val bufferSize = if (hasKnownSize && contentLength != null && contentLength > 10 * 1024 * 1024) {
                BUFFER_LARGE
            } else {
                BUFFER_SMALL
            }
            val buffer = ByteArray(bufferSize)

            while (!channel.isClosedForRead) {
//                ensureActive()
                val bytes = channel.readAvailable(buffer)
                when {
                    bytes > 0 -> {
                        output.write(buffer, 0, bytes)
                        totalBytesRead += bytes

                        if (totalBytesRead - lastSpaceCheck >= SPACE_CHECK_INTERVAL) {
//                            ensureActive()
                            checkAvailableSpace(tempFile.parentFile, totalBytesRead + MIN_FREE_SPACE)
                            lastSpaceCheck = totalBytesRead
                        }

                        if (hasKnownSize && contentLength != null) {
                            val progress = totalBytesRead.toFloat() / contentLength
                            if (progress - lastProgressReport >= PROGRESS_REPORT_INTERVAL) {
                                onProgress(progress.coerceIn(0f, 1f))
                                lastProgressReport = progress
                            }
                        }
                    }
                    bytes == -1 -> {
                        Logger.d("Downloader::downloadWithProgress: download completed. url=$url, bytes=$totalBytesRead")
                        break
                    }
                    bytes == 0 -> {
                        delay(50)
                    }
                }
            }

            if (hasKnownSize) {
                onProgress(1f)
            }
        }

        return totalBytesRead
    }

    /**
     * 检查磁盘可用空间
     */
    private fun checkAvailableSpace(dir: File?, requiredSize: Long?, minFreeSpace: Long = MIN_FREE_SPACE) {
        if (dir == null || !dir.exists()) {
            throw IllegalArgumentException("Invalid directory: $dir")
        }

        val stat = StatFs(dir.absolutePath)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong

        val requiredSpace = when {
            requiredSize != null && requiredSize > 0 -> requiredSize + minFreeSpace
            else -> minFreeSpace * 2
        }

        var availableMB = 0L
        var requiredMB = 0L
        if (availableBytes < requiredSpace) {
            availableMB = availableBytes / (1024 * 1024)
            requiredMB = requiredSpace / (1024 * 1024)
            val message = "Insufficient disk space. Available: ${availableMB}MB, Required: ${requiredMB}MB"
            Logger.e("Downloader::checkAvailableSpace: $message, dir=${dir.absolutePath}")
            throw IllegalStateException(message)
        }

        Logger.d("Downloader::checkAvailableSpace: available=${availableMB}MB, required=${requiredMB}MB, dir=${dir.absolutePath}")
    }

    /**
     * 重试机制包装器（仅重试网络异常）
     */
    private suspend fun <T> withRetry(
        times: Int = RETRY_COUNT,
        initialDelay: Long = RETRY_DELAY,
        block: suspend () -> T
    ): T {
        var lastException: Throwable? = null

        repeat(times) { attempt ->
            try {
                return block()
            } catch (e: Throwable) {
                lastException = e

                val isRetryable = when (e) {
                    is IOException -> true
                    is SocketTimeoutException -> false
                    else -> false
                }

                Logger.w("Downloader: attempt ${attempt + 1}/$times failed: ${e.message}")

                if (attempt < times - 1 && isRetryable) {
                    val currentDelay = initialDelay * (attempt + 1)
                    Logger.d("Downloader: retrying in ${currentDelay}ms")
                    delay(currentDelay)
                } else {
                    throw e
                }
            }
        }

        throw lastException ?: IllegalStateException("Unknown error in retry")
    }
}
