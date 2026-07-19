package com.wxn.reader.presentation.fonts.viewmodels

import com.wxn.reader.domain.use_case.font.FontListItem

data class FontManagementUiState(
    val fonts: List<FontListItem> = emptyList(),
    val expandedFontId: String? = null,
    val currentFontDir: String = "",
    val currentVariant: String = "regular",
    val isLoading: Boolean = false,
    val userMessage: String? = null
)
