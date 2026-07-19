package com.wxn.reader.presentation.ttsModels.viewmodels

import com.wxn.reader.domain.model.TTSModelData
import com.wxn.reader.util.download.DownloadState
data class TTSModelsUiState(
    val modelList: List<TTSModelData> = emptyList(),
    val downloadedModels: Set<String> = emptySet(),

    val downloadBaseModels: Set<BaseModelStatus> = emptySet(), //基础模型的下载
    val selectedModel: String? = null,      // 新增
    val selectedSpeaker: Int = 0,       // 新增
    val expandedModels: Set<String> = emptySet(),

    val currentlyPlayingSpeaker: String = "",

    val isLoading: Boolean = false,

    val isLoadingMore: Boolean = false,

    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val error: String? = null,

    val newlyDownloadedModels: Set<String> = emptySet(),      // 本次会话刚下载完成的模型

    val hasShownSwipeToDeleteHint: Boolean = false             // 是否已播放过提示动画(来自持久化)
)


data class BaseModelStatus(
    val fileId : String,
    val url: String,
    val status: DownloadState
) {

}