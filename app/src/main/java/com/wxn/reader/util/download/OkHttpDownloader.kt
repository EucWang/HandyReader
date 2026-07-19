package com.wxn.reader.util.download

import com.wxn.base.util.Logger
import com.wxn.reader.util.download.DownloaderHelper.checkAvailableSpace
import com.wxn.reader.util.download.DownloaderHelper.withRetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.ensureActive

class OkHttpDownloader @Inject constructor(
    @Named("DownloadOkHttpClient") private val okHttpClient: OkHttpClient
) : IDownloader {

    /**
     * 下载文件到指定路径
     * 注意：此函数支持协程取消，调用者应通过Job.cancel()来中断下载
     * @param url 下载URL
     * @param targetFile 目标文件
     * @param onProgress 进度回调（0.0~1.0），仅已知大小文件回调
     * @return 下载的文件绝对路径
     * @throws kotlinx.coroutines.CancellationException 协程被取消时抛出
     */
    override suspend fun downloadToFile(
        url: String,
        targetFile: File,
        headers: Map<String, String>?,
        onProgress: (Float) -> Unit
    ): String {
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

        if (targetFile.exists()) {
            Logger.d("OkHttpDownloader::downloadToFile: file already exists, skipping. url=$url, file=${targetFile.absolutePath}")
            return targetFile.absolutePath
        }

        if (tempFile.exists()) {
            Logger.d("OkHttpDownloader::downloadToFile: cleaning old temp file: ${tempFile.absolutePath}")
            tempFile.delete()
        }

        try {
            val downloadedBytes = withRetry(times = RETRY_COUNT, initialDelay = RETRY_DELAY) {
                downloadWithProgress(url, tempFile, headers, onProgress)
            }

            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("Failed to rename temp file to target file: ${tempFile.absolutePath} -> ${targetFile.absolutePath}")
            }

            Logger.i("OkHttpDownloader::downloadToFile: download completed. url=$url, size=${downloadedBytes}bytes, file=${targetFile.absolutePath}")
            return targetFile.absolutePath

        } catch (e: CancellationException) {
            if (tempFile.exists()) {
                Logger.d("OkHttpDownloader::downloadToFile: cancelled, cleaning temp file: ${tempFile.absolutePath}")
                tempFile.delete()
            }
            throw e
        } catch (e: Exception) {
            if (tempFile.exists()) {
                Logger.w("OkHttpDownloader::downloadToFile: cleaning temp file after error: ${tempFile.absolutePath}")
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
    override fun cancelDownload(targetFile: File) {
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
        if (tempFile.exists()) {
            Logger.d("OkHttpDownloader::cancelDownload: cleaning temp file: ${tempFile.absolutePath}")
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
        headers: Map<String, String>?,
        onProgress: (Float) -> Unit
    ): Long {
        val requestBuilder = Request.Builder().url(url)
        headers?.forEach { (key, value) -> requestBuilder.header(key, value) }
        val request = requestBuilder.build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) {
            response.close()
            throw IllegalStateException("HTTP ${response.code}: ${response.message}")
        }

        val body = response.body ?: throw IllegalStateException("Response body is null")
        val contentLength = body.contentLength()
        val hasKnownSize = contentLength > 0
        val isSizeUnknown = contentLength == -1L

        val initialRequiredSpace = if (hasKnownSize) {
            contentLength + MIN_FREE_SPACE
        } else {
            MIN_FREE_SPACE * 2
        }
        checkAvailableSpace(tempFile.parentFile, initialRequiredSpace)

        var totalBytesRead = 0L
        var lastProgressReport = 0f
        var lastSpaceCheck = 0L
        val bufferSize = if (hasKnownSize && contentLength > 10 * 1024 * 1024) {
            BUFFER_LARGE
        } else {
            BUFFER_SMALL
        }

        try {
            tempFile.outputStream().use { output ->
                body.byteStream().use { input ->

                    val buffer = ByteArray(bufferSize)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val bytes = input.read(buffer)
                        when {
                            bytes > 0 -> {
                                output.write(buffer, 0, bytes)
                                totalBytesRead += bytes

                                if (totalBytesRead - lastSpaceCheck >= SPACE_CHECK_INTERVAL) {
                                    checkAvailableSpace(
                                        tempFile.parentFile,
                                        totalBytesRead + MIN_FREE_SPACE
                                    )
                                    lastSpaceCheck = totalBytesRead
                                }

                                if (hasKnownSize) {
                                    val progress = totalBytesRead.toFloat() / contentLength
                                    if (progress - lastProgressReport >= PROGRESS_INTERVAL_SMALL) {
                                        onProgress(progress.coerceIn(0f, 1f))
                                        lastProgressReport = progress
                                    }
                                }
                            }

                            bytes == -1 -> {
                                Logger.d("OkHttpDownloader::downloadWithProgress: download completed. url=$url, bytes=$totalBytesRead")
                                break
                            }
                        }
                    }

                    if (hasKnownSize) {
                        onProgress(1f)
                    }
                }
            }
        } finally {
            response.close()
        }
        return totalBytesRead
    }


}