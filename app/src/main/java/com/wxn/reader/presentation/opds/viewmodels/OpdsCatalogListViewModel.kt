package com.wxn.reader.presentation.opds.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.util.Logger
import com.wxn.reader.domain.use_case.opds.GetPredefinedCatalogsUseCase
import com.wxn.reader.domain.use_case.opds.ManageOpdsCatalogUseCase
import com.wxn.reader.presentation.opds.OpdsCatalogListUiState
import com.wxn.reader.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OpdsCatalogListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manageCatalogUseCase: ManageOpdsCatalogUseCase,
    private val getPredefinedCatalogsUseCase: GetPredefinedCatalogsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OpdsCatalogListUiState())
    val uiState: StateFlow<OpdsCatalogListUiState> = _uiState.asStateFlow()

    init {
        loadCatalogs()
        syncPredefinedCatalogs()
    }

    private fun loadCatalogs() {
        viewModelScope.launch {
            manageCatalogUseCase.getAllCatalogs().collectLatest { catalogs ->
                val (predefined, custom) = catalogs.partition { it.isPredefined }
                _uiState.update {
                    it.copy(
                        predefinedCatalogs = predefined.sortedWith(compareBy({ it.sortOrder }, { it.createdAt })),
                        customCatalogs = custom.sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
                    )
                }
            }
        }
    }

    private fun syncPredefinedCatalogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val result = getPredefinedCatalogsUseCase()
                getPredefinedCatalogsUseCase.syncToDatabase(result.catalogs)
                if (result.isRemoteError) {
                    _uiState.update { it.copy(syncError = true) }
                }
            } catch (e: Exception) {
                Logger.e("OpdsCatalogListViewModel: Failed to sync predefined catalogs: $e")
                _uiState.update { it.copy(syncError = true) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearSyncError() {
        _uiState.update { it.copy(syncError = false) }
    }

    fun addCatalog(name: String, url: String, username: String? = null, password: String? = null) {
        viewModelScope.launch {
            val authType = if (!username.isNullOrBlank()) "BASIC" else "NONE"
            manageCatalogUseCase.addCatalog(name, url, authType = authType, username = username, password = password)
                .onSuccess {
                    _uiState.update { it.copy(userMessage = context.getString(R.string.opds_catalog_added)) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(userMessage = context.getString(R.string.opds_validation_error, e.message ?: "")) }
                }
        }
    }

    fun deleteCatalog(id: Long) {
        viewModelScope.launch {
            manageCatalogUseCase.deleteCatalog(id)
            _uiState.update { it.copy(userMessage = context.getString(R.string.opds_catalog_removed)) }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
