package com.wxn.reader.presentation.bookReader.components.modals.readbglist

import com.wxn.reader.domain.model.ReadBgData


data class ReadBgImageUiState(
    val bgList: List<ReadBgData> = emptyList(),
    val downloadedIds: Set<String> = emptySet(),
    val currentBgTextureId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val error: String? = null
)
