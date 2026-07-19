package com.wxn.reader.presentation.ttsModels.viewmodels

import android.content.Context
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.util.Coroutines
import com.wxn.reader.domain.model.TTSModelData
import com.wxn.reader.domain.repository.TTSModelsRepository
import com.wxn.reader.util.download.DownloadState
import com.wxn.reader.util.download.ExtractionResult
import com.wxn.reader.util.download.FileDownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/****
 * Sherpa model 的依赖包管理器
 */
@Singleton
class TTSModelsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: FileDownloadManager,
    private val metadataManager: DependencyMetadataManager,
    private val repository: TTSModelsRepository
) {
    private val scope = Coroutines.scope()

    init {
        scope.launch {
            //监控是否解压完成
            downloadManager.extractionCompleteEvent.collect { result ->
                if (result.success) {
                    if (result.fileType == DownloadFileType.TTS_DEPENDENCY) {
                        handleTtsDependency(result)
                    } else if (result.fileType == DownloadFileType.TTS_MODEL) {
                        handleTtsModels(result)
                    }
                }
            }
        }
    }

    suspend fun getDownloadedBaseModels() : Set<BaseModelStatus> {
        val dependencies = metadataManager.loadIndex().dependencies
        val baseModels = hashSetOf<BaseModelStatus>()
        if (dependencies.isNotEmpty()) {
            dependencies.forEach { string, metadata ->
                baseModels.add(
                    BaseModelStatus(
                        fileId = metadata.fileName,
                        url = metadata.url,
                        status = DownloadState(
                            id = metadata.fileName,
                            isDownloading = true,
                            isExtracting = true,
                        ))
                )
            }
        }
        return baseModels
    }

    /**
     * 检查依赖是否已下载并解压
     */
    suspend fun isDependencyDownloaded(url: String): Boolean {
        return metadataManager.isDependencyDownloaded(url)
    }

    private fun handleTtsDependency(result: ExtractionResult) {
        scope.launch {
            // 将基础模型信息保存起来
            metadataManager.updateDependencyStatus(result.url, result.fileId, "completed")
        }
    }

    /***
     * 完成解压缩之后的model包, 需要保存信息到数据库中
     */
    private fun handleTtsModels(result: ExtractionResult) {
        scope.launch {
            result.extraData?.let { data ->
                (data as? TTSModelData?)?.let { model ->
                    repository.saveModel(model)
                }
            }
        }
    }
}