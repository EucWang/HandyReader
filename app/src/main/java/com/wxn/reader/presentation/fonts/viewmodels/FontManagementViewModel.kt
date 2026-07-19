package com.wxn.reader.presentation.fonts.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.reader.R
import com.wxn.reader.data.dto.FontEntity
import com.wxn.reader.data.dto.FontFileEntity
import com.wxn.reader.data.model.FontCatalogItem
import com.wxn.reader.domain.repository.FontRepository
import com.wxn.reader.domain.use_case.font.DeleteFontUseCase
import com.wxn.reader.domain.use_case.font.DownloadFontUseCase
import com.wxn.reader.domain.use_case.font.FontListItem
import com.wxn.reader.util.download.DownloadState
import com.wxn.reader.util.download.FileDownloadManager
import com.wxn.reader.util.FontFamilyAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject


@HiltViewModel
class FontManagementViewModel @Inject constructor(
    private val fontRepository: FontRepository,
    private val downloadFontUseCase: DownloadFontUseCase,
    private val deleteFontUseCase: DeleteFontUseCase,
    private val readerPrefsUtil: ReaderPreferencesUtil,
    private val downloadManager: FileDownloadManager,
    val context: Application,
) : AndroidViewModel(context) {

    private val _uiState = MutableStateFlow(FontManagementUiState())
    val uiState: StateFlow<FontManagementUiState> = _uiState.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    /***
     * 从json中解析出font列表数据
     */
    private var catalog = emptyList<FontCatalogItem>()

    init {
        loadFonts()
        observeDownloadComplete()
        observeDownloadStates()
    }

    private fun observeDownloadStates() {
        viewModelScope.launchIO {
            downloadManager.downloadStates.collect { states ->
                val fontStates = hashMapOf<String, ArrayList<DownloadState>>()
                for (state in states) {
                    val fontId = state.key.substringBeforeLast("_")
                    fontStates.getOrPut(fontId) { arrayListOf() }.add(state.value)
                }
                val targetStates = hashMapOf<String, DownloadState>()
                for (fontState in fontStates) {
                    val fontId = fontState.key
                    val fontStateList = fontState.value
                    if (fontStateList.isEmpty()) continue
                    if (fontStateList.size == 1) {
                        targetStates[fontId] = fontStateList.first()
                    } else {
                        var progress = 0.0f
                        var error: String? = null
                        val size = fontStateList.size
                        var pendingCount = 0
                        var downloadingCount = 0
                        var completedCount = 0
                        for (state in fontStateList) {
                            if (state.isPendingDownload) {
                                pendingCount++
                            } else if (state.isDownloading) {
                                downloadingCount++
                                progress += state.progress / size
                            } else if (state.isCompleted) {
                                completedCount++
                                progress += (1.0f / size)
                            } else {
                                error = state.error
                                break
                            }
                        }
                        if (!error.isNullOrEmpty()) {
                            targetStates[fontId] = DownloadState(fontId, error = error)
                        } else if (pendingCount == size) {
                            targetStates[fontId] = DownloadState(fontId, isPendingDownload = true)
                        } else if (downloadingCount > 0) {
                            targetStates[fontId] =
                                DownloadState(fontId, progress = progress, isDownloading = true)
                        } else if (completedCount == size) {
                            targetStates[fontId] =
                                DownloadState(fontId, progress = 1.0f, isCompleted = true)
                        }
                    }
                }
                _downloadStates.value = targetStates
            }
        }
    }


    private fun loadFonts() {
        viewModelScope.launchIO {
            _uiState.update { it.copy(isLoading = true) }

            catalog = fontRepository.getCatalog()

            combine(
                fontRepository.getAllFonts(),
                readerPrefsUtil.readerPrefsFlow
            ) { dbFonts, prefs ->
                val fontMap = dbFonts.associateBy { it.id }
                catalog.map { item ->
                    val entity = fontMap[item.id]
                    FontListItem(
                        catalogItem = item.copy(variants = item.variants.sortedBy { FontFamilyAnalyzer.variantWeight(it.variant) }),
                        fontEntity = entity,
                        isDownloaded = entity?.localDir != null,
                        totalVariants = item.variants.size,
                        localDir = entity?.localDir
                    )
                } to prefs
            }.collect { (fonts, prefs) ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        fonts = fonts,
                        currentFontDir = prefs.font,
                        currentVariant = prefs.fontVariant
                    )
                }
            }
        }
    }

    /**
     * 应用下载状态观察
     */
    private fun observeDownloadComplete() {
        viewModelScope.launch {
            downloadManager.downloadCompleteEvent.collect { (fileId, targetPath) ->
                val fontId = fileId.substringBeforeLast("_")
                val variant = fileId.substringAfterLast("_")
                Logger.d("FontManagementViewModel::downloadComplete fileId=$fileId, fontId=$fontId, variant=$variant")

                try {
                    if (!isAllVariantsCompleted(fontId)) return@collect

                    val catalogItem = catalog.find { it.id == fontId } ?: return@collect
                    val existing = fontRepository.getFontById(fontId)
                    if (existing != null && existing.downloadedAt != null) return@collect

                    val localDir = File(targetPath).parent ?: return@collect
                    val now = System.currentTimeMillis()

                    val fontEntity = FontEntity(
                        id = catalogItem.id,
                        displayName = catalogItem.displayName,
                        category = catalogItem.category,
                        language = catalogItem.language,
                        dirName = catalogItem.dirName,
                        localDir = localDir,
                        downloadedAt = now
                    )

                    val fileEntities = catalogItem.variants.map { variantItem ->
                        val variantFileId = "${fontId}_${variantItem.variant}"
                        val localFile = File(localDir, variantItem.localFileName)
                        FontFileEntity(
                            id = variantFileId,
                            fontId = fontId,
                            variant = variantItem.variant,
                            name = variantItem.name,
                            url = variantItem.url,
                            fileName = variantItem.fileName,
                            localFileName = variantItem.localFileName,
                            localPath = localFile.absolutePath,
                            downloadedAt = now
                        )
                    }

                    fontRepository.saveDownloadedFont(fontEntity, fileEntities)
                    showMessage(context.getString(R.string.font_download_success, catalogItem.displayName))
                } catch (e: Exception) {
                    Logger.e("FontManagementViewModel::downloadComplete error: ${e.message}")
                }
            }
        }
    }

    private fun isAllVariantsCompleted(fontId: String): Boolean {
        val catalogItem = catalog.find { it.id == fontId } ?: return false
        return catalogItem.variants.all { variantItem ->
            val variantFileId = "${fontId}_${variantItem.variant}"
            val state = downloadManager.downloadStates.value[variantFileId]
            state?.isCompleted == true
        }
    }

    fun downloadFont(fontId: String) {
        viewModelScope.launch {
            //如果存在旧的下载状态，对其进行删除
            val states = _downloadStates.value.toMutableMap()
            states.remove(fontId)
            _downloadStates.value = states

            try {
                downloadFontUseCase(fontId).onFailure { e ->
                    showMessage(context.getString(R.string.font_download_failed, e.message))
                }
            } catch (e: Exception) {
                Logger.e("FontManagementViewModel::downloadFont error: ${e.message}")
                showMessage(context.getString(R.string.font_download_failed, e.message))
            }
        }
    }

    fun deleteFont(fontId: String) {
        viewModelScope.launch {
            try {
                val currentFontDir = _uiState.value.currentFontDir
                val isCurrentFont = deleteFontUseCase.isCurrentFont(fontId, currentFontDir)
                if (isCurrentFont) {
                    val prefs = readerPrefsUtil.rawReaderPrefsFlow.firstOrNull()
                    if (prefs != null) {
                        readerPrefsUtil.updateFontPrefs("serif", "regular")
                    }
                }
                deleteFontUseCase(fontId).onFailure { e ->
                    showMessage(context.getString(R.string.font_delete_failed, e.message))
                }
            } catch (e: Exception) {
                Logger.e("FontManagementViewModel::deleteFont error: ${e.message}")
                showMessage(context.getString(R.string.font_delete_failed, e.message))
            }
        }
    }

//    fun selectFont(fontId: String, variant: String = "regular") {
//        viewModelScope.launch {
//            try {
//                val entity = fontRepository.getFontById(fontId) ?: return@launch
//                val fontDir = entity.localDir ?: return@launch
//
//                val prefs = readerPrefsUtil.readerPrefsFlow.firstOrNull() ?: return@launch
//                readerPrefsUtil.updatePreferences(
//                    prefs.copy(
//                        font = fontDir,
//                        fontVariant = variant
//                    )
//                )
//            } catch (e: Exception) {
//                Logger.e("FontManagementViewModel::selectFont error: ${e.message}")
//            }
//        }
//    }

    fun toggleExpand(fontId: String) {
        _uiState.update { state ->
            state.copy(
                expandedFontId = if (state.expandedFontId == fontId) null else fontId
            )
        }
    }

    fun cancelDownload(fontId: String) {
        catalog.find { it.id == fontId }?.variants?.forEach { variant ->
            val fileId = "${fontId}_${variant.variant}"
            downloadManager.cancelDownload(fileId)
        }
    }

    fun isFontSelected(fontId: String): Boolean {
        val currentDir = _uiState.value.currentFontDir
        val entity = _uiState.value.fonts.find { it.catalogItem.id == fontId }?.fontEntity
        return entity?.localDir == currentDir
    }

    fun isVariantSelected(fontId: String, variant: String): Boolean {
        return isFontSelected(fontId) && _uiState.value.currentVariant == variant
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(userMessage = message) }
    }
}
