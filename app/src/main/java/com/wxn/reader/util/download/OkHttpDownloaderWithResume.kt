package com.wxn.reader.util.download

import com.wxn.base.util.Logger
import com.wxn.reader.domain.model.DownloadMetadata
import com.wxn.reader.util.download.DownloaderHelper.checkAvailableSpace
import com.wxn.reader.util.download.DownloaderHelper.parseTotalSizeFromContentRange
import com.wxn.reader.util.download.DownloaderHelper.withRetry
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.coroutineContext

class OkHttpDownloaderWithResume  @Inject constructor(
    @Named("DownloadOkHttpClient") private val okHttpClient: OkHttpClient
) : IDownloaderWithResume {

    override suspend fun downloadWithResume(
        url: String,
        targetFile: File,
        metadata: DownloadMetadata?,
        capabilities: ServerCapabilities,
        headers: Map<String, String>?,
        onProgress: (Float) -> Unit
    ): String {
        Logger.i("OkHttpDownloaderWithResume:downloadWithResume:url=$url,targetFile=${targetFile.absolutePath},metadata=$metadata, capabilities=$capabilities")
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
        val metaFile = File(targetFile.parent, "${targetFile.name}.meta")

        // 1. 检查目标文件是否已存在, 即已经下载完成, 则直接返回
        if (targetFile.exists()) {
            Logger.d("OkHttpDownloaderWithResume: file already exists, skipping: ${targetFile.absolutePath}")
            metaFile.delete()  // 清理元数据文件
            return targetFile.absolutePath //即已经下载完成, 则直接返回
        }

        // 2. 读取或创建元数据
        val currentMetadata = metadata ?: DownloadMetadata.fromFile(metaFile)
        val isResuming = currentMetadata != null && tempFile.exists() //临时文件存在,并且元数据也不为空, 则为恢复断点续传
        Logger.d("OkHttpDownloaderWithResume:downloadWithResume:isResuming=$isResuming,currentMetadata=$currentMetadata")

        // 4. 确定下载策略
        val totalSize = capabilities.contentLength ?: currentMetadata?.totalBytes
        val isLargeFile = totalSize != null && totalSize > LARGE_FILE_THRESHOLD
        val progressInterval = if (isLargeFile) PROGRESS_INTERVAL_LARGE else PROGRESS_INTERVAL_SMALL
        Logger.d("OkHttpDownloaderWithResume:downloadWithResume:totalSize=$totalSize,isLargeFile=$isLargeFile,progresInterval=$progressInterval")

        // 5. 执行下载（支持断点续传）
        return withRetry(times = RETRY_COUNT, initialDelay = INITIAL_RETRY_DELAY) {
            downloadWithRangeSupport(
                url = url,
                tempFile = tempFile,
                metaFile = metaFile,
                currentMetadata = currentMetadata,
                capabilities = capabilities,
                headers = headers,
                isResuming = isResuming,
                progressInterval = progressInterval,
                onProgress = onProgress
            )
        }
    }

    private suspend fun downloadWithRangeSupport(
        url: String,
        tempFile: File,
        metaFile: File,
        currentMetadata: DownloadMetadata?,
        capabilities: ServerCapabilities,
        headers: Map<String, String>?,
        isResuming: Boolean,
        progressInterval: Float,
        onProgress: (Float) -> Unit
    ): String {
        Logger.d("OkHttpDownloaderWithResume:downloadWithRangeSupport:url=$url")
        val startByte = if (isResuming && capabilities.supportsRange) {
            tempFile.length()
        } else {
            0L
        }

        Logger.d("OkHttpDownloaderWithResume:downloadWithRangeSupport:startByte=$startByte")
        val requestBuilder = Request.Builder().url(url)
        headers?.forEach { (key, value) -> requestBuilder.header(key, value) }
        if (startByte > 0 && capabilities.supportsRange) {
            requestBuilder.header("Range", "bytes=$startByte-")
        }

        val request = requestBuilder.build()
        val response: Response = okHttpClient.newCall(request).execute()
        Logger.d("OkHttpDownloaderWithResume:downloadWithRangeSupport:: code=${response.code},  message=${response.message}")

        try {
            when (response.code) {
                206 -> {
                    // 206 Partial Content - 服务器支持断点续传
                    processPartialContent(response, tempFile, metaFile, currentMetadata,
                        startByte, progressInterval, onProgress)
                }
                200 -> {
                    // 200 OK - 服务器不支持Range或需要重新下载
                    if (isResuming) {
                        Logger.d("OkHttpDownloaderWithResume: server doesn't support range, restarting download")
                        tempFile.delete()
                        metaFile.delete()
                    }
                    processFullContent(response, tempFile, metaFile, progressInterval, onProgress)
                }
                else -> {
                    throw IOException("HTTP ${response.code}: ${response.message}")
                }
            }
        } finally {
            response.close()
        }

        // 重命名临时文件
        val targetFile = File(tempFile.parent, tempFile.name.removeSuffix(".tmp"))
        if (!tempFile.renameTo(targetFile)) {
            throw IOException("Failed to rename temp file to target")
        }

        // 删除元数据文件
        metaFile.delete()

        return targetFile.absolutePath
    }

    private suspend fun processPartialContent(
        response: Response,
        tempFile: File,
        metaFile: File,
        currentMetadata: DownloadMetadata?,
        startByte: Long,
        progressInterval: Float,
        onProgress: (Float) -> Unit
    ) {
        Logger.d("OkHttpDownloaderWithResume:processPartialContent:startByte=$startByte")
        // 解析Content-Range头
        // 解析Content-Range头
        val contentRange = response.header("Content-Range")
        val totalSize = parseTotalSizeFromContentRange(contentRange)

        // 更新元数据
        val metadata = currentMetadata?.copy(
            totalBytes = totalSize,
            supportsRange = true,
            lastUpdated = System.currentTimeMillis()
        ) ?: DownloadMetadata(
            fileId = "",  // 需要调用者提供
            url = response.request.url.toString(),
            targetPath = tempFile.absolutePath.removeSuffix(".tmp"),
            tempPath = tempFile.absolutePath,
            totalBytes = totalSize,
            downloadedBytes = startByte,
            supportsRange = true,
            createdAt = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis()
        )

        // 下载并定期更新元数据
        downloadStream(
            response = response,
            file = tempFile,
            startFrom = startByte,
            totalSize = totalSize,
            progressInterval = progressInterval,
            onProgress = onProgress,
            onChunkDownloaded = { downloadedBytes ->
                // 每5MB更新一次元数据
                if (downloadedBytes % (UPDATE_METADATA_SIZE) == 0L) {
                    val updated = metadata.copy(downloadedBytes = downloadedBytes)
                    DownloadMetadata.saveToFile(updated, metaFile)
                }
            }
        )
    }

    private suspend fun processFullContent(
        response: Response,
        tempFile: File,
        metaFile: File,
        progressInterval: Float,
        onProgress: (Float) -> Unit
    ) {
        Logger.i("OkHttpDownloaderWithResume:processFullContent")
        val totalSize = response.header("Content-Length")?.toLongOrNull()

        downloadStream(
            response = response,
            file = tempFile,
            startFrom = 0L,
            totalSize = totalSize,
            progressInterval = progressInterval,
            onProgress = onProgress
        )
    }

    private suspend fun downloadStream(
        response: Response,
        file: File,
        startFrom: Long,
        totalSize: Long?,
        progressInterval: Float,
        onProgress: (Float) -> Unit,
        onChunkDownloaded: (Long) -> Unit = {}
    ) {
        Logger.i("OkHttpDownloaderWithResume:downloadStream:startFrom=$startFrom, totalSize=$totalSize")
        val netInputStream = response.body.byteStream() ?: throw IOException("Response body is null")
        var totalBytesRead = startFrom
        var lastProgressReport = 0f
        var lastSpaceCheck = 0L
        val bufferSize = if (totalSize != null && totalSize > LARGE_FILE_THRESHOLD) {
            BUFFER_LARGE
        } else {
            BUFFER_SMALL
        }

        file.outputStream().use { output ->
            if (startFrom > 0) {
                output.channel.truncate(startFrom)
                output.channel.position(startFrom)
            }
            val buffer = ByteArray(bufferSize)
            netInputStream.use { input ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val bytes = input.read(buffer)
                    when {
                        bytes > 0 -> {
                            output.write(buffer, 0, bytes)
                            totalBytesRead += bytes
                            // 定期检查磁盘空间
                            if (totalBytesRead - lastSpaceCheck >= SPACE_CHECK_INTERVAL) {
                                checkAvailableSpace(file.parentFile, totalBytesRead + MIN_FREE_SPACE)
                                lastSpaceCheck = totalBytesRead
                            }
                            // 进度回调
                            if (totalSize != null) {
                                val progress = totalBytesRead.toFloat() / totalSize
                                if (progress - lastProgressReport >= progressInterval) {
                                    onProgress(progress.coerceIn(0f, 1f))
                                    lastProgressReport = progress
                                }
                            }
                            // 块下载回调
                            onChunkDownloaded(totalBytesRead)
                        }
                        bytes == -1 -> break
                        bytes == 0 -> delay(50)
                    }
                }
            }
            if (totalSize != null) {
                onProgress(1f)
            }
        }
    }
}