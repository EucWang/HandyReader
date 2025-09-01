package com.wxn.reader.presentation.shelves

import com.wxn.reader.domain.model.Shelf

sealed class ShelvesState {
    data object Loading : ShelvesState()
    data class Error(val message: String) : ShelvesState()
    data class Success(val shelves: List<Shelf>) : ShelvesState()
}