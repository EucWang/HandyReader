package com.wxn.reader.presentation.bookReader

sealed class BookReaderUiState {
    data object Loading : BookReaderUiState()
    data object LOAD_SUCCESS : BookReaderUiState()
    data class Error(val message: String) : BookReaderUiState()
}
