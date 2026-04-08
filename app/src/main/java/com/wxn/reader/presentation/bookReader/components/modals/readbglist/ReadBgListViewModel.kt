package com.wxn.reader.presentation.bookReader.components.modals.readbglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.util.Logger
import com.wxn.base.util.ToastUtil
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.reader.domain.model.ReadBgData
import com.wxn.reader.domain.repository.ReadBgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadBgListViewModel @Inject constructor(
    private val repository: ReadBgRepository,
    private val downloadManager: BgImageDownloadManager,
    private val readerPrefsUtil: ReaderPreferencesUtil
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReadBgImageUiState())
    val uiState: StateFlow<ReadBgImageUiState> = _uiState.asStateFlow()
    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadManager.downloadStates

    private val _readerPreferences = MutableStateFlow<ReaderPreferences?>(null)
    val readerPreferences: StateFlow<ReaderPreferences?> = _readerPreferences.asStateFlow()

    init {
        viewModelScope.launch {
            readerPrefsUtil.readerPrefsFlow.stateIn(viewModelScope).collect { pref ->
                _readerPreferences.value = pref
                Logger.d("MainReadViewModel::init readerPreferences[$pref],  pref.backgroundImage=${pref.backgroundImage}")
                _uiState.value = _uiState.value.copy(
                    currentBgTextureId = pref.backgroundImage
                )
            }
        }

        loadBgImages()
        observeDownloadedImages()
    }

    private fun observeDownloadedImages() {
        viewModelScope.launch {
            repository.getDownloadedReadBgs().collect { downloaded ->
                _uiState.value = _uiState.value.copy(
                    downloadedIds = downloaded.map { it.id }.toSet()
                )
            }
        }
    }

    fun loadBgImages() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.getReadBg(1, 10).fold(
                onSuccess = { (bgDatas, totalPages) ->
                    val bgs = bgDatas
                    val pages = totalPages
                    Logger.d("ReadBgListViewModel::loadBgImages, bgs=${bgs?.size}, pages=$pages")
                    if (bgs != null && pages != null) {
                        _uiState.value = _uiState.value.copy(
                            bgList = bgs,
                            isLoading = false,
                            currentPage = 1,
                            totalPages = pages //总页数
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun loadMoreImages() {
        val state = _uiState.value
        if (state.isLoadingMore || state.currentPage >= state.totalPages) return
        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)

            val nextPage = state.currentPage + 1
            repository.getReadBg(nextPage, 10).fold(
                onSuccess = { (bgList, bgListPages) ->
                    val bgs = bgList
                    val pages = bgListPages
                    Logger.d("ReadBgListViewModel::loadMoreImages, bgs=${bgs?.size}, pages=$pages")
                    if (bgs != null && pages != null) {
                        _uiState.value = _uiState.value.copy(
                            bgList = state.bgList + bgs,
                            isLoadingMore = false,
                            currentPage = nextPage,
                            totalPages = if (pages > state.totalPages) pages else state.totalPages //总页数
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

    fun downloadBgImage(bgData: ReadBgData, onComplated: (ReadBgData) -> Unit) {
        downloadManager.downloadBgImageData(
            bgDataItem = bgData,
            onCompleted = onComplated,
            onError = { error ->
                ToastUtil.show(error)
            }
        )
    }

    fun setAsBackground(newPreferences: ReaderPreferences, item: ReadBgData) {
        viewModelScope.launch {
            _readerPreferences.value = newPreferences
            _uiState.value = _uiState.value.copy(currentBgTextureId = item.id)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        downloadManager.release()
    }
}