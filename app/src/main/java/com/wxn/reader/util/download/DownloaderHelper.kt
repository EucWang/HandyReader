package com.wxn.reader.util.download

import android.content.Context
import android.os.StatFs
import com.wxn.base.util.Logger
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException

object DownloaderHelper {


    /**
     * 重试机制包装器（仅重试网络异常）
     * @throws IllegalStateException || IOException || SocketTimeoutException || Exception
     */
    suspend fun <T> withRetry(
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



    /**
     * 检查磁盘可用空间
     * @throws IllegalArgumentException || IllegalStateException
     */
    fun checkAvailableSpace(
        dir: File?,
        requiredSize: Long?,
        minFreeSpace: Long = MIN_FREE_SPACE
    ) {
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
            val message =
                "Insufficient disk space. Available: ${availableMB}MB, Required: ${requiredMB}MB"
            Logger.e("OKHttpDownloader::checkAvailableSpace: $message, dir=${dir.absolutePath}")
            throw IOException(message)
        }

        Logger.d("OKHttpDownloader::checkAvailableSpace: available=${availableMB}MB, required=${requiredMB}MB, dir=${dir.absolutePath}")
    }

    fun parseTotalSizeFromContentRange(contentRange: String?): Long? {
        if (contentRange == null) return null
        val pattern = """bytes \d+-\d+/(\d+)""".toRegex()
        return pattern.find(contentRange)?.groupValues?.get(1)?.toLongOrNull()
    }


    /**
     * 检查可用存储空间是否足够
     * @param context Context
     * @param requiredBytes 需要的字节数
     * @return true 如果空间足够
     */
    fun hasEnoughStorage(context: Context, requiredBytes: Long): Boolean {
        val availableBytes = getAvailableStorageBytes(context)
        return availableBytes >= requiredBytes
    }

    /**
     * 获取可用存储空间（字节）
     */
    fun getAvailableStorageBytes(context: Context): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBlocksLong * stat.blockSizeLong
            } else {
                @Suppress("DEPRECATION")
                stat.availableBlocks.toLong() * stat.blockSize
            }
        } catch (e: Exception) {
            Long.MAX_VALUE // 出错时假设空间足够
        }
    }

    /**
     * 格式化文件大小显示
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
        if (bytes < 1024 * 1024 * 1024) return "${bytes / (1024 * 1024)} MB"
        return "${bytes / (1024 * 1024 * 1024)} GB"
    }


    /**
     * 从URL提取文件名
     */
    fun extractFileNameFromUrl(url: String): String {
        val name = url.substringAfterLast("/").substringBefore("?")
        return name.substringBeforeLast(".")
    }

    fun getOptimalBufferSize(fileSize: Long): Int {
        return when {
            fileSize > 50 * 1024 * 1024 -> 128 * 1024  // 128KB for >50MB
            fileSize > 10 * 1024 * 1024 -> 64 * 1024   // 64KB for >10MB
            else -> 32 * 1024                          // 32KB default
        }
    }
}