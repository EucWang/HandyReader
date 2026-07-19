package com.wxn.reader.util.download

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.util.Coroutines
import com.wxn.base.util.PathUtil
import com.wxn.reader.data.source.local.dao.DownloadHistoryDao
import com.wxn.reader.domain.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.wxn.base.util.Logger as BaseLogger

/***
 * 通用的下载管理器
 */
@Singleton
class FileDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val zipExtractor: ZipExtractor,
    private val downloadHistoryDao: DownloadHistoryDao
) {

    companion object {
        private const val TAG_DOWNLOAD = "download"

        private const val MAX_CONCURRENT_EXTRACTIONS = 1  // 严格串行
    }

    /***
     * 当前进行中的下载任务
     */
    private val _activeWorkIds = MutableStateFlow<Set<String>>(emptySet())
    val activeWorkIds: StateFlow<Set<String>> = _activeWorkIds.asStateFlow()

    /***
     * 下载状态
     */
    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    private val waitingQueue = ConcurrentLinkedQueue<DownloadRequest>()
    private val queueScope = Coroutines.scope()
    private var queueObserverJob: Job? = null


    private val _downloadCompleteEvent = MutableSharedFlow<Pair<String,String>>()
    //下载完成事件
    val downloadCompleteEvent = _downloadCompleteEvent.asSharedFlow()

    // 解压队列相关
    private val extractionQueue = ConcurrentLinkedQueue<ExtractionRequest>()
    private val extractionMutex = Mutex()  // 确保严格串行
    private var extractionProcessorJob: Job? = null
    private val _extractionCompleteEvent = MutableSharedFlow<ExtractionResult>()
    val extractionCompleteEvent = _extractionCompleteEvent.asSharedFlow()

    /***
     * 进入下载队列
     */
    fun enqueueDownload(
        fileId: String,
        url: String,
        fileType: DownloadFileType = DownloadFileType.BG_IMAGE,
        fileName: String? = null,
        extraData: Any? = null,
        authHeader: String? = null,
    ): String {
        BaseLogger.d("FileDownloadManager::enqueueDownload::fileId=$fileId, url=$url, fileType=$fileType,fileName=$fileName")

        val request = DownloadRequest(fileId, url, fileType, fileName, extraData, authHeader)
        synchronized(this) {
            // Check if already in pending downloads
            if (isInQueue(fileId)) {
                return fileId
            }

            // Check if already in active downloads
            if (_activeWorkIds.value.contains(fileId)) {
                return fileId // Return dummy UUID for existing
            }

            // Check if can start immediately
            if (_activeWorkIds.value.size < MAX_CONCURRENT_DOWNLOADS) {
                return startDownload(request)
            }

            // Add to waiting queue
            waitingQueue.offer(request)
            _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                put(fileId, DownloadState(
                    id = fileId,
                    isPendingDownload = true,
                    isDownloading = false,
                    isCompleted = false
                ))
            }

            // Start queue observer if not running
            if (queueObserverJob == null) {
                startQueueObserver()
            }

            return fileId // Return dummy UUID for queued
        }
    }

    private fun startDownload(request: DownloadRequest): String {
        return startDownload(request.fileId, request.url, request.fileType, request.fileName, request.extraData, request.authHeader)
    }

    private fun startQueueObserver() {
        queueObserverJob = queueScope.launch {
            // Observe active work count changes
            workManager.getWorkInfosByTagLiveData(TAG_DOWNLOAD).asFlow().collect { workInfos ->
                val activeCount = workInfos.count { it.state == WorkInfo.State.RUNNING }
                val availableSlots = MAX_CONCURRENT_DOWNLOADS - activeCount

                // Start queued downloads if slots available
                repeat(availableSlots) {
                    val nextRequest = waitingQueue.poll()
                    if (nextRequest != null) {
                        startDownload(nextRequest)
                    }
                }

                // Stop observer if queue is empty
                if (waitingQueue.isEmpty()) {
                    queueObserverJob?.cancel()
                    queueObserverJob = null
                }
            }
        }
    }

    /***
     * 执行下载任务
     */
    private fun startDownload(
        fileId: String,
        url: String,
        fileType: DownloadFileType,
        fileName: String?,
        extraData: Any? = null,
        authHeader: String? = null
    ): String {
        val targetPath = getTargetPath(fileId, fileType, fileName)
        val startedAt = System.currentTimeMillis()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()
        //封装传递给WorkManager的下载数据
        val inputData = workDataOf(
            DownloadWorker.KEY_FILE_ID to fileId,
            DownloadWorker.KEY_URL to url,
            DownloadWorker.KEY_TARGET_PATH to targetPath,
            DownloadWorker.KEY_FILE_TYPE to fileType.name,
            DownloadWorker.KEY_FILE_NAME to fileName,
            DownloadWorker.KEY_STARTED_AT to startedAt,
            DownloadWorker.KEY_AUTH_HEADER to authHeader
        )
        //封装任务请求数据
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30_000,
                TimeUnit.MILLISECONDS
            )
            .addTag(TAG_DOWNLOAD)
            .addTag(fileId)
            .build()

        //将任务交给WorkManager执行
        workManager.enqueue(workRequest)
        _activeWorkIds.value = _activeWorkIds.value.toMutableSet().apply { add(fileId) }
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(fileId, DownloadState(
                id = fileId,
                isPendingDownload = false,
                isDownloading = true
            ))
        }
        observeWorkProgress(workRequest.id, fileId, targetPath, fileType, fileName, url, extraData)
        return workRequest.id.toString()
    }

    private fun getTargetPath(fileId: String, fileType: DownloadFileType, fileName: String?): String {
        return PathUtil.getDownloadFilePath(context, fileType, fileName, fileId)
    }

    /***
     * 观测下载进度
     */
    private fun observeWorkProgress(workId: UUID, fileId: String, targetPath: String, fileType: DownloadFileType, fileName: String?, url: String, extraData: Any?) {
        BaseLogger.d("FileDownloadManager:observeWorkProgress::workId[$workId], fileId[$fileId]")
        queueScope.launch {
            workManager.getWorkInfoByIdLiveData(workId).asFlow().collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
                        BaseLogger.d("FileDownloadManager:observeWorkProgress:RUNNING:progress:${progress},fileId=$fileId")
                        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                            put(fileId, DownloadState(
                                id = fileId,
                                progress = progress / 100f,
                                isPendingDownload = false,
                                isDownloading = true,
                                isCompleted = false
                            ))
                        }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        BaseLogger.d("FileDownloadManager:observeWorkProgress:SUCCEEDED,fileId=$fileId")
                        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                            put(fileId, DownloadState(
                                id = fileId,
                                progress = 1f,
                                isDownloading = false,
                                isCompleted = true
                            ))
                        }
                        _activeWorkIds.value = _activeWorkIds.value.toMutableSet().apply {
                            remove(fileId)
                        }
                        _downloadCompleteEvent.emit(fileId to targetPath)

                        if (shouldExtract(fileType)) { // 自动触发解压队列
                            enqueueExtraction(
                                ExtractionRequest(
                                    fileId = fileId,
                                    url = url,
                                    zipPath = targetPath,
                                    fileType = fileType,
                                    extraData = extraData
                                )
                            )
                        }
                    }

                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        BaseLogger.d("FileDownloadManager:observeWorkProgress:CANCELLED,fileId=$fileId")
                        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
                            put(fileId, DownloadState(
                                id = fileId,
                                progress = 0f,
                                isPendingDownload = false,
                                isDownloading = false,
                                isCompleted = false,
                                error = workInfo.outputData.getString(DownloadWorker.KEY_ERROR)
                            ))
                        }
                        _activeWorkIds.value = _activeWorkIds.value.toMutableSet().apply {
                            remove(fileId)
                        }
                    }

                    else -> {
                        BaseLogger.d("FileDownloadManager:observeWorkProgress:other state = ${workInfo?.state}")
                    }
                }
            }
        }
    }

    /****
     * 取消下载任务
     */
    fun cancelDownload(fileId: String) {
        removeFromQueue(fileId)
        workManager.cancelAllWorkByTag(fileId)
        _activeWorkIds.value = _activeWorkIds.value.toMutableSet().apply { remove(fileId) }
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            remove(fileId)
        }
        queueScope.launch {
            downloadHistoryDao.updateStatusToFileId(fileId, DownloadStatus.CANCELLED)
        }
    }

    fun getQueueSize(): Int {
        return waitingQueue.size
    }
    fun getActiveDownloadCount(): Int {
        return _activeWorkIds.value.size
    }
    fun isInQueue(fileId: String): Boolean {
        return waitingQueue.any { it.fileId == fileId }
    }
    fun removeFromQueue(fileId: String): Boolean {
        return waitingQueue.removeIf { it.fileId == fileId }
    }

    //----------------------------------------

    /***
     * 加入一个解压缩任务
     */
    private fun enqueueExtraction(request: ExtractionRequest): Boolean {
        // 检查是否已在队列中
        if (extractionQueue.any { it.fileId == request.fileId }) {
            return false
        }

        extractionQueue.offer(request)
        updateDownloadState(request.fileId, isExtracting = false, isQueued = true)

        // 启动队列处理器（如果未运行）
        if (extractionProcessorJob == null) {
            startExtractionProcessor()
        }
        return true
    }

    /***
     * 串行执行解压缩任务
     */
    private fun startExtractionProcessor() {
        extractionProcessorJob = queueScope.launch {
            while (isActive) {
                extractionMutex.withLock {
                    val request = extractionQueue.poll() ?: return@withLock

                    try {
                        // 更新状态：解压中
                        updateDownloadState(request.fileId, isExtracting = true)

                        // 执行解压
                        val result = executeExtraction(request)

                        if (result.isSuccess) {
                            // 解压成功，更新状态
                            updateDownloadState(request.fileId, isExtracting = false)

                            // 触发完成事件
                            _extractionCompleteEvent.emit(
                                ExtractionResult(
                                    fileId = request.fileId,
                                    url = request.url,
                                    zipPath = request.zipPath,
                                    targetDir = request.targetDir,
                                    fileType = request.fileType,
                                    success = true,
                                    error = null,
                                    extraData = request.extraData
                                )
                            )
                        } else {
                            // 解压失败
                            val error = result.exceptionOrNull()?.message ?: "Extraction failed"
                            updateDownloadState(request.fileId, isExtracting = false, error = error)

                            // 仍然触发完成事件，但标记为失败
                            _extractionCompleteEvent.emit(
                                ExtractionResult(
                                    fileId = request.fileId,
                                    url = request.url,
                                    zipPath = request.zipPath,
                                    targetDir = request.targetDir,
                                    fileType = request.fileType,
                                    success = false,
                                    error = error
                                )
                            )


                        }
                    } catch (e: Exception) {
                        // 处理失败，继续下一个任务
                        BaseLogger.d("FileDownloadManager: Extraction processor error: ${e.message}")
                        updateDownloadState(request.fileId, isExtracting = false, error = e.message)

                        // 触发失败事件
                        _extractionCompleteEvent.emit(
                            ExtractionResult(
                                fileId = request.fileId,
                                url = request.url,
                                zipPath = request.zipPath,
                                targetDir = request.targetDir,
                                fileType = request.fileType,
                                success = false,
                                error = e.message
                            )
                        )
                    }
                }

                // 队列空时延迟检查，避免CPU空转
                if (extractionQueue.isEmpty()) {
                    delay(100)
                }
            }
        }
    }

    /****
     * 解压缩时,更新下载状态
     */
    private fun updateDownloadState(
        fileId: String,
        isExtracting: Boolean = false,
        isQueued: Boolean = false,
        error: String? = null
    ) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            val current = get(fileId) ?: DownloadState(id = fileId)
            put(fileId, current.copy(
                isPendingDownload = false,
                isExtracting = isExtracting,
                error = error ?: current.error
            ))
        }
    }

    private fun shouldExtract(fileType: DownloadFileType): Boolean {
        return fileType == DownloadFileType.TTS_MODEL ||
                fileType == DownloadFileType.TTS_DEPENDENCY
    }

    /***
     * 执行解压缩任务
      */
    private suspend fun executeExtraction(request: ExtractionRequest, delteZipWhenSuccess:Boolean = true): Result<Unit> {
        return try {
            BaseLogger.d("FileDownloadManager: Starting extraction: ${request.fileId}")

            // 1. 验证ZIP文件存在
            val zipFile = java.io.File(request.zipPath)
            if (!zipFile.exists()) {
                BaseLogger.e("FileDownloadManager: ZIP file not found: ${request.zipPath}")
                return Result.failure(Exception("ZIP file not found: ${request.zipPath}"))
            }

            // 2. 确保目标目录存在
            val targetDir = java.io.File(request.targetDir)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
                BaseLogger.d("FileDownloadManager: Created target directory: ${request.targetDir}")
            }

            // 3. 检查磁盘空间（ZIP解压后通常膨胀10-20%）
            val availableSpace = targetDir.freeSpace
            val requiredSpace = (zipFile.length() * 1.3).toLong() // 预估需要130%的空间
            if (availableSpace < requiredSpace) {
                val errorMsg = "Insufficient disk space: need ${requiredSpace / (1024*1024)}MB, " +
                        "available ${availableSpace / (1024*1024)}MB"
                BaseLogger.e("FileDownloadManager: $errorMsg")
                return Result.failure(Exception(errorMsg))
            }

            // 4. 执行解压
            BaseLogger.d("FileDownloadManager: Extracting ${zipFile.name} (${zipFile.length() / (1024*1024)}MB) to ${request.targetDir}")

            val startTime = System.currentTimeMillis()
            val result = zipExtractor.extract(request.zipPath, request.targetDir)
            val elapsedTime = System.currentTimeMillis() - startTime

            if (result.isSuccess) {
                BaseLogger.d("FileDownloadManager: Extraction completed in ${elapsedTime}ms: ${request.fileId}")

                //解压缩成功, 删除zip压缩包
                if (delteZipWhenSuccess) {
                    zipFile.delete()
                }

                Result.success(Unit)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Extraction failed")
                BaseLogger.e("FileDownloadManager: Extraction failed for ${request.fileId}: ${error.message}")
                Result.failure(error)
            }

        } catch (e: Exception) {
            BaseLogger.e("FileDownloadManager: Unexpected error during extraction: ${e.message}")
            Result.failure(e)
        }
    }
}