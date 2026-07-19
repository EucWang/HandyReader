package com.wxn.reader.presentation.settings.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.data.dto.DownloadHistoryEntity
import com.wxn.reader.domain.use_case.download.GetDownloadHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class DownloadHistoryViewModel @Inject constructor(
    private val getDownloadHistoryUseCase: GetDownloadHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadHistoryUiState())
    val uiState: StateFlow<DownloadHistoryUiState> = _uiState.asStateFlow()

    init {
        loadDownloadHistory()
    }

    fun loadDownloadHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                getDownloadHistoryUseCase().collect { historyList ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        downloadHistory = historyList.sortedByDescending { it.downloadedAt }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
data class DownloadHistoryUiState(
    val isLoading: Boolean = false,
    val downloadHistory: List<DownloadHistoryEntity> = emptyList(),
    val error: String? = null
)