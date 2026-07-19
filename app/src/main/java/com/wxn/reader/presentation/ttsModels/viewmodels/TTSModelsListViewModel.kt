package com.wxn.reader.presentation.ttsModels.viewmodels

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.util.Logger
import com.wxn.base.util.ToastUtil
import com.wxn.base.util.launchIO
import com.wxn.bookread.data.model.preference.FirstHintPrefsUtil
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.reader.R
import com.wxn.reader.domain.model.TTSModelData
import com.wxn.reader.domain.repository.DownloadRepository
import com.wxn.reader.domain.repository.TTSModelsRepository
import com.wxn.reader.ui.theme.stringResource
import com.wxn.reader.util.TtsServiceController
import com.wxn.reader.util.download.DownloadState
import com.wxn.reader.util.download.FileDownloadManager
import com.wxn.reader.util.engineModelDir
import com.wxn.reader.util.tts.data.Speaker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class TTSModelsListViewModel @OptIn(UnstableApi::class)
@Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TTSModelsRepository,
    private val downloadManager: FileDownloadManager,
    private val ttsPrefsUtil: TtsPreferencesUtil,
    private val firstHintPrefsUtil: FirstHintPrefsUtil,
    private val sampleVoicePlayer: SampleVoicePlayer,
    private val baseModelManager: TTSModelsManager,
    private val ttsServiceController: TtsServiceController,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TTSModelsUiState())
    val uiState: StateFlow<TTSModelsUiState> = _uiState.asStateFlow()

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadManager.downloadStates

    private val _showBaseModelDownloadDailog: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showBaseModelDownloadDailog: StateFlow<Boolean> = _showBaseModelDownloadDailog.asStateFlow()


    private val _showBaseModelDownloadMission: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val showBaseModelDownloadMission: StateFlow<Boolean> =
        _showBaseModelDownloadMission.asStateFlow()


    private val _showModelCard: MutableStateFlow<ModelCardShowType> = MutableStateFlow(
        ModelCardShowType.ModelCardHidden
    )
    val showModelCard: StateFlow<ModelCardShowType> = _showModelCard.asStateFlow()

    private val _modelCardInfo: MutableStateFlow<String> = MutableStateFlow("")
    val modelCardInfo: StateFlow<String> = _modelCardInfo.asStateFlow()

    private val _showCurrentSpeakers: MutableStateFlow<List<Speaker>> =
        MutableStateFlow(emptyList())
    val showCurrentSpeakers: StateFlow<List<Speaker>> = _showCurrentSpeakers.asStateFlow()

    var pendingDownloadModel: TTSModelData? = null

    var firstLoadLocal = true

    init {
        loadModels()
        observeDownloadedModels()

        viewModelScope.launch {
            sampleVoicePlayer.currentlyPlayingUrl.stateIn(viewModelScope).collect { url ->
                if (url.isNullOrEmpty()) {
                    _uiState.update { state ->
                        state.copy(currentlyPlayingSpeaker = "")
                    }
                }
            }
        }

        viewModelScope.launch {
            firstHintPrefsUtil.firstHintFlow.stateIn(viewModelScope).collect { prefs ->
                _uiState.update {
                    it.copy(
                        hasShownSwipeToDeleteHint = prefs.hasShownSwipeToDeleteHint
                    )
                }
            }
        }
    }

    private fun selectedSpeakers(selectedModelName: String?, models: List<TTSModelData>?) {
        if (!selectedModelName.isNullOrEmpty()) {
            models?.firstOrNull { item ->
                item.name == selectedModelName
            }?.let { item ->
                _showCurrentSpeakers.value = item.speakers
            }
        }
    }

    private fun observeDownloadedModels() {
        viewModelScope.launch {
            combine(
                ttsPrefsUtil.ttsPreferencesFlow,
                repository.getDownloadedModels()
            ) { prefs, models ->
                prefs to models
            }.collect { (prefs, models) ->
                val downloadedNames = models.map { it.name }.toSet()
                val oldDownloadNames = _uiState.value.downloadedModels
                val addedNames = downloadedNames - oldDownloadNames

                val newHints =
                    if (addedNames.isNotEmpty() && !_uiState.value.hasShownSwipeToDeleteHint) {
                        _uiState.value.newlyDownloadedModels + addedNames
                    } else {
                        _uiState.value.newlyDownloadedModels
                    }
                _uiState.update {
                    it.copy(
                        downloadedModels = downloadedNames,
                        newlyDownloadedModels = newHints
                    )
                }

                if (firstLoadLocal && models.isNotEmpty() && _uiState.value.modelList.isEmpty()) { //第一次时,先从本地加载
                    _uiState.update { state ->
                        state.copy(
                            selectedModel = prefs.selectedTTSModel,
                            selectedSpeaker = prefs.selectedSpeaker,
                            modelList = models,
                        )
                    }
                    firstLoadLocal = false
                } else {
                    _uiState.update { state ->
                        state.copy(
                            selectedModel = prefs.selectedTTSModel,
                            selectedSpeaker = prefs.selectedSpeaker,
                        )
                    }
                }
            }
        }
    }

    fun loadModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.getModels(1, 10).fold(
                onSuccess = { (models, totalPages) ->
                    if (models != null && totalPages != null) {
                        _uiState.value = _uiState.value.copy(
                            modelList = models,
                            isLoading = false,
                            isLoadingMore = false,
                            currentPage = 1,
                            totalPages = totalPages
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun loadMoreModels() {
        val state = _uiState.value
        if (state.isLoadingMore || state.currentPage >= state.totalPages) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            repository.getModels(state.currentPage + 1, 10).fold(
                onSuccess = { (models, totalPages) ->
                    if (models != null && totalPages != null) {
                        _uiState.value = _uiState.value.copy(
                            modelList = state.modelList + models,
                            isLoadingMore = false,
                            currentPage = state.currentPage + 1,
                            totalPages = totalPages
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = e.message
                    )
                }
            )
        }
    }


    /****
     * 0. 判断是否有依赖的zip包没有下载(espeak_url, vocos_16khz_univ_url), 如果没有, 下载依赖的zip包
     * 1. 启动文件下载器下载model对应的zip包: FileDownloadManager
     * 2. 解压缩zip包,
     * 3. 删除zip包
     * 4. 将解压缩之后的model信息, 保存到 数据库中 TTSModelsRepository
     */
    fun downloadModel(model: TTSModelData) {

        val baseDatas = model.base.orEmpty()
        if (baseDatas.isEmpty()) {
            downloadManager.enqueueDownload(
                fileId = model.name,
                url = model.url,
                fileType = DownloadFileType.TTS_MODEL,
                fileName = model.name,
                extraData = model
            )
            return
        }

        viewModelScope.launch {
            val baseModels = mutableSetOf<BaseModelStatus>()
            for (baseData in baseDatas) {
                val downloaded = baseModelManager.isDependencyDownloaded(baseData.url)
                if (!downloaded) {
                    baseModels.add(
                        BaseModelStatus(
                            fileId = baseData.name,
                            url = baseData.url,
                            status = DownloadState(
                                id = baseData.name,
                                isDownloading = false,
                                isCompleted = false,
                                isExtracting = false
                            )
                        )
                    )
                }
            }

            if (baseModels.isEmpty()) {
                downloadManager.enqueueDownload(
                    fileId = model.name,
                    url = model.url,
                    fileType = DownloadFileType.TTS_MODEL,
                    fileName = model.name,
                    extraData = model
                )
                return@launch
            } else {
                Logger.d("TTSModelsListViewModel::downloadModel::baseModels[$baseModels]")
                _uiState.value = _uiState.value.copy(
                    downloadBaseModels = baseModels
                )
                _showBaseModelDownloadDailog.value = true
                pendingDownloadModel = model
            }
        }
    }

    fun toggleModelExpansion(modelName: String) {
        val currentlyExpanded = _uiState.value.expandedModels
        val newExpanded = if (currentlyExpanded.contains(modelName)) {
            currentlyExpanded - modelName
        } else {
            currentlyExpanded + modelName
        }
        _uiState.value = _uiState.value.copy(expandedModels = newExpanded)
    }

    fun playSampleVoice(url: String) {
        viewModelScope.launch {
            sampleVoicePlayer.play(url)
        }
        _uiState.value = _uiState.value.copy(
            currentlyPlayingSpeaker = url
        )
    }

    fun pauseSampleVoice() {
        viewModelScope.launch {
            sampleVoicePlayer.pause()
        }
        _uiState.value = _uiState.value.copy(
            currentlyPlayingSpeaker = ""
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            sampleVoicePlayer.stop()
        }
    }

    fun dismissBaseModelDownloadDialog() {
        _showBaseModelDownloadDailog.value = false
    }

    fun cancelBaseModelMission() {
        _uiState.value = _uiState.value.copy(
            downloadBaseModels = emptySet()
        )
        _showBaseModelDownloadMission.value = false
        pendingDownloadModel = null
    }

    /****
     * 开始基础模型下载任务
     */
    fun startBaseModelDownloadMission() {
        _showBaseModelDownloadMission.value = true
        val baseModels = _uiState.value.downloadBaseModels
        if (baseModels.isEmpty()) return

        baseModels.forEach { (name, url) ->
            downloadManager.enqueueDownload(
                fileId = name,
                url = url,
                fileType = DownloadFileType.TTS_DEPENDENCY,
                fileName = name
            )
        }

        pendingDownloadModel?.let { model ->
            downloadManager.enqueueDownload(
                fileId = model.name,
                url = model.url,
                fileType = DownloadFileType.TTS_MODEL,
                fileName = model.name,
                model
            )
            pendingDownloadModel = null
        }
    }

    @OptIn(UnstableApi::class)
    fun deleteModel(model: TTSModelData) {
        viewModelScope.launchIO {
            try {
                val runningModel = ttsServiceController.getRunningModel(context)
                if (model.name == runningModel) {
                    ToastUtil.show(stringResource(R.string.cannot_delete_running_model))
                    return@launchIO
                }

                val selectedModel =
                    ttsPrefsUtil.ttsPreferencesFlow.firstOrNull()?.selectedTTSModel ?: ""
                if (selectedModel == model.name) {
                    ttsPrefsUtil.resetSelectedModel()
                }

                val modelDirPath = model.engineModelDir(context)
                val modelDir = File(modelDirPath)
                if (modelDir.exists()) {
                    modelDir.deleteRecursively()
                }

                repository.deleteModel(model.name)
                downloadRepository.markDownloadAsDeleted(model.name)
            } catch (e: Exception) {
                Logger.e("TTSModelsListViewModel::deleteModel::error: ${e.message}")
            }
        }
    }

    fun clearHintAnimation(modelName: String, clearFlag: Boolean) {
        _uiState.update { it.copy(newlyDownloadedModels = it.newlyDownloadedModels - modelName) }
        if (clearFlag) {
            viewModelScope.launch { firstHintPrefsUtil.clearSwipeToDelHint() }
        }
    }

    fun showModelCard(model: TTSModelData) {
        model.licenseUrl?.let { url ->
            _showModelCard.value = ModelCardShowType.ModelCardLoadding

            viewModelScope.launchIO {
                val result = repository.getModelCard(url)
                if (result.isSuccess) {
                    _modelCardInfo.value = result.getOrNull().orEmpty()
                    _showModelCard.value = ModelCardShowType.ModelCardShow
                } else {
                    _modelCardInfo.value = stringResource(R.string.tts_error_network_error)
                    _showModelCard.value = ModelCardShowType.ModelCardError
                }
            }
        }
    }

    fun hiddenModelCard() {
        viewModelScope.launchIO {
            _showModelCard.value = ModelCardShowType.ModelCardHidden
            _modelCardInfo.value = ""
        }
    }
}

enum class ModelCardShowType {
    ModelCardHidden,
    ModelCardLoadding,
    ModelCardShow,
    ModelCardError
}