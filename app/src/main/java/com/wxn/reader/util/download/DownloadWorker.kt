package com.wxn.reader.util.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.reader.data.dto.DownloadHistoryEntity
import com.wxn.reader.data.source.local.dao.DownloadHistoryDao
import com.wxn.reader.domain.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Named

/****
 * 运行在 WorkManager中的下载任务,
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    @Named("DownloadOkHttpClient") private val okClient: OkHttpClient,
    private val downloadHistoryDao: DownloadHistoryDao,
    private val okHttpDownloader: IDownloader,
    private val okHttpDownloaderWithResume: IDownloaderWithResume
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_FILE_ID = "file_id"
        const val KEY_URL = "url"
        const val KEY_TARGET_PATH = "target_path"
        const val KEY_FILE_TYPE = "file_type"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_PROGRESS = "progress"
        const val KEY_LOCAL_PATH = "local_path"
        const val KEY_ERROR = "error"
        const val KEY_AUTH_HEADER = "auth_header"
    }

    private val scope = Coroutines.scope()

    override suspend fun doWork(): Result {
        // 读取输入参数
        val fileId = inputData.getString(KEY_FILE_ID) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val targetPath = inputData.getString(KEY_TARGET_PATH) ?: return Result.failure()
        val fileType = inputData.getString(KEY_FILE_TYPE) ?: "BG_IMAGE"
        val fileName = inputData.getString(KEY_FILE_NAME)
        val startedAt = inputData.getLong(KEY_STARTED_AT, System.currentTimeMillis())
        val authHeader = inputData.getString(KEY_AUTH_HEADER)
        return try {
            // 记录下载开始
            recordDownloadHistory(
                fileId = fileId,
                url = url,
                fileType = fileType,
                targetPath = targetPath,
                fileSize = 0,
                status = DownloadStatus.INIT,  // 临时状态
                startedAt = startedAt,
                completedAt = null
            )
            val targetFile = File(targetPath)

            // 检查文件是否已存在
            if (targetFile.exists()) {
                Logger.d("DownloadWorker: file already exists, skipping: $targetPath")

                updateHistoryAsCompleted(
                    fileId = fileId,
                    localPath = targetPath,
                    fileSize = targetFile.length(),
                    completedAt = System.currentTimeMillis(),
                )

                return Result.success(
                    workDataOf(
                        KEY_LOCAL_PATH to targetPath,
                        KEY_FILE_ID to fileId
                    )
                )
            }
            // 决定使用哪种下载策略
            val detector = ServerCapabilityDetector(okClient)
            val capabilities = detector.detectCapabilities(url)
            val totalSize = capabilities.contentLength

            val localPath = if (totalSize != null && totalSize > LARGE_FILE_THRESHOLD) {
                okHttpDownloaderWithResume.downloadWithResume(
                    url,
                    targetFile,
                    null,
                    capabilities,
                    headers = if (authHeader != null) mapOf("Authorization" to authHeader) else null
                ) { progress ->
                    Logger.d("DownloadWorker::downloadWithResume:progress=$progress")
                    scope.launch {
                        setProgress(
                            workDataOf(
                                KEY_PROGRESS to (progress * 100).toInt(),
                                KEY_FILE_ID to fileId
                            )
                        )
                    }
                }
            } else {
                okHttpDownloader.downloadToFile(
                    url,
                    targetFile,
                    headers = if (authHeader != null) mapOf("Authorization" to authHeader) else null
                ) { progress ->
                    Logger.d("DownloadWorker::downloadToFile:progress=$progress, fileId=$fileId")
                    scope.launch {
                        setProgress(
                            workDataOf(
                                KEY_PROGRESS to (progress * 100).toInt(),
                                KEY_FILE_ID to fileId
                            )
                        )
                    }
                }
            }
            // 更新下载历史为完成
            updateHistoryAsCompleted(
                fileId = fileId,
                localPath = localPath,
                fileSize = File(localPath).length(),
                completedAt = System.currentTimeMillis()
            )
            Result.success(
                workDataOf(
                    KEY_LOCAL_PATH to localPath,
                    KEY_FILE_ID to fileId
                )
            )

        } catch (e: Exception) {
//            Logger.e("DownloadWorker failed for file $fileId", e)
            Logger.e("DownloadWorker failed for file $fileId, $e")

            // 记录失败历史
            updateHistoryAsFailed(
                fileId = fileId,
                errorMessage = e.message ?: "Unknown error",
                completedAt = System.currentTimeMillis()
            )

            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(
                        KEY_ERROR to e.message,
                        KEY_FILE_ID to fileId
                    )
                )
            }
        }
    }

    private suspend fun recordDownloadHistory(
        fileId: String,
        url: String,
        fileType: String,
        targetPath: String,
        fileSize: Long,
        status: DownloadStatus,
        startedAt: Long,
        completedAt: Long?
    ) {
        Logger.d("DownloadWorker: recordDownloadHistory for file $fileId, $url, $fileType, $targetPath, $fileSize, $status, $startedAt, $completedAt")
        val history = DownloadHistoryEntity(
            fileId = fileId,
            url = url,
            fileType = fileType,
            fileName = File(targetPath).name,
            localPath = targetPath,
            fileSize = fileSize,
            status = status,
            errorMessage = null,
            startedAt = startedAt,
            completedAt = completedAt,
            downloadedAt = completedAt ?: System.currentTimeMillis()
        )
        downloadHistoryDao.insert(history)
    }

    private suspend fun updateHistoryAsCompleted(
        fileId: String,
        localPath: String,
        fileSize: Long,
        completedAt: Long
    ) {
        Logger.d("DownloadWorker: updateHistoryAsCompleted for file $fileId, $localPath, $fileSize, $completedAt")
        // 这里需要更新现有的历史记录
        // 由于DownloadHistoryDao使用REPLACE策略，我们可以直接插入新记录
        val existing = downloadHistoryDao.getByFileId(fileId)
        val history = DownloadHistoryEntity(
            fileId = fileId,
            url = existing?.url ?: "",
            fileType = existing?.fileType ?: "BG_IMAGE",
            fileName = File(localPath).name,
            localPath = localPath,
            fileSize = fileSize,
            status = DownloadStatus.COMPLETED,
            errorMessage = null,
            startedAt = existing?.startedAt ?: completedAt,
            completedAt = completedAt,
            downloadedAt = completedAt
        )
        downloadHistoryDao.insert(history)
    }

    private suspend fun updateHistoryAsFailed(
        fileId: String,
        errorMessage: String,
        completedAt: Long
    ) {
        Logger.d("DownloadWorker: updateHistoryAsFailed for file $fileId, $errorMessage, $completedAt")
        val existing = downloadHistoryDao.getByFileId(fileId)
        val history = DownloadHistoryEntity(
            fileId = fileId,
            url = existing?.url ?: "",
            fileType = existing?.fileType ?: "BG_IMAGE",
            fileName = existing?.fileName,
            localPath = existing?.localPath ?: "",
            fileSize = existing?.fileSize ?: 0,
            status = DownloadStatus.FAILED,
            errorMessage = errorMessage,
            startedAt = existing?.startedAt ?: completedAt,
            completedAt = completedAt,
            downloadedAt = completedAt
        )
        downloadHistoryDao.insert(history)
    }
}