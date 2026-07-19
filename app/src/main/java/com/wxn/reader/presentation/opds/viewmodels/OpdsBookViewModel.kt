package com.wxn.reader.presentation.opds.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.util.Logger
import com.wxn.reader.R
import com.wxn.reader.data.model.opds.OpdsEntry
import com.wxn.reader.data.model.opds.OpdsLink
import com.wxn.reader.data.source.local.GuidePrefUtil
import com.wxn.reader.domain.use_case.opds.DownloadOpdsBookUseCase
import com.wxn.reader.domain.use_case.opds.OpdsBookMappingUseCase
import com.wxn.reader.navigation.buildReaderRoute
import com.wxn.reader.presentation.opds.OpdsBookUiState
import com.wxn.reader.util.download.DownloadState
import com.wxn.reader.util.download.FileDownloadManager
import com.wxn.reader.util.download.FileValidationException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class OpdsBookViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val downloadBookUseCase: DownloadOpdsBookUseCase,
    private val fileDownloadManager: FileDownloadManager,
    private val opdsBookMappingUseCase: OpdsBookMappingUseCase,
    private val guidePrefUtil: GuidePrefUtil
) : ViewModel() {

    private val catalogId: Long = savedStateHandle["catalogId"] ?: -1L

    private var currentFileId: String?
        get() = savedStateHandle["currentFileId"]
        set(value) { savedStateHandle["currentFileId"] = value }

    private var persistedTargetPath: String?
        get() = savedStateHandle["targetPath"]
        set(value) { savedStateHandle["targetPath"] = value }

    private var opdsDownloadLocation: String = "app_internal"
    private var opdsSafTreeUri: String = ""
    private var hasMadeFirstChoice: Boolean = false
    private var pendingEntry: OpdsEntry? = null
    private var pendingLink: OpdsLink? = null

    private data class ImportedBookInfo(
        val bookId: Long,
        val filePath: String,
        val fileType: String
    )

    private val formatImportCache = mutableMapOf<Int, ImportedBookInfo>()
    private val pendingMappings = mutableMapOf<String, String>()
    private var restoreJob: Job? = null
    private var expectedFileSize: Long? = null
    private var lastDownloadLink: OpdsLink? = null
    private var isEnqueuing = false

    private val _uiState = MutableStateFlow(OpdsBookUiState(catalogId = catalogId))
    val uiState: StateFlow<OpdsBookUiState> = _uiState.asStateFlow()

    init {
        observeDownloadProgress()
        observeDownloadComplete()
        viewModelScope.launch {
            val prefs = guidePrefUtil.guidePrefsFlow.first()
            opdsDownloadLocation = prefs.opdsDownloadLocation
            opdsSafTreeUri = prefs.opdsSafTreeUri
            hasMadeFirstChoice = prefs.hasOpdsFirstDownloadChoiceMade
        }
    }

    fun setEntry(entry: OpdsEntry) {
        val currentId = _uiState.value.entry?.id
        if (currentId == entry.id) return

        restoreJob?.cancel()
        persistedTargetPath = null
        currentFileId = null
        expectedFileSize = null
        lastDownloadLink = null
        formatImportCache.clear()
        pendingMappings.clear()
        isEnqueuing = false
        _uiState.update { it.copy(
            entry = entry,
            isLoading = false,
            downloadState = null,
            isImporting = false,
            importedBookId = 0L,
            importedFilePath = null,
            importedFileType = null,
            selectedFormatIndex = 0,
            error = null,
            showCorruptedDialog = false,
            showFirstDownloadDialog = false
        )}
        if (entry.acquisitionLinks.isNotEmpty()) {
            restoreJob = viewModelScope.launch { restoreForFormat(entry, 0) }
        }
    }

    private suspend fun restoreForFormat(entry: OpdsEntry, index: Int) {
        val link = entry.acquisitionLinks.getOrNull(index) ?: return
        val fileId = buildFileId(entry, link)
        val remoteUrl = link.href

        formatImportCache[index]?.let { info ->
            if (info.bookId > 0) {
                currentFileId = fileId
                _uiState.update { it.copy(
                    importedBookId = info.bookId,
                    importedFilePath = info.filePath,
                    importedFileType = info.fileType,
                    downloadState = null
                )}
                return
            }
        }

        val dmState = fileDownloadManager.downloadStates.value[fileId]
        if (dmState != null && (dmState.isDownloading || dmState.isPendingDownload)) {
            currentFileId = fileId
            _uiState.update { it.copy(downloadState = dmState) }
            return
        }

        opdsBookMappingUseCase.findImported(remoteUrl, catalogId)?.let { info ->
            currentFileId = fileId
            val cacheInfo = ImportedBookInfo(info.bookId, info.filePath, info.fileType)
            formatImportCache[index] = cacheInfo
            _uiState.update { it.copy(
                importedBookId = info.bookId,
                importedFilePath = info.filePath,
                importedFileType = info.fileType,
                downloadState = null
            )}
        }
    }

    fun selectFormat(index: Int) {
        val entry = _uiState.value.entry ?: return
        if (index == _uiState.value.selectedFormatIndex) return
        if (_uiState.value.downloadState?.isDownloading == true) return
        if (_uiState.value.isImporting) return

        restoreJob?.cancel()
        persistedTargetPath = null
        pendingMappings.clear()

        val cached = formatImportCache[index]
        if (cached != null && cached.bookId > 0) {
            val link = entry.acquisitionLinks.getOrNull(index)
            link?.let { currentFileId = buildFileId(entry, it) }
            _uiState.update { it.copy(
                selectedFormatIndex = index,
                importedBookId = cached.bookId,
                importedFilePath = cached.filePath,
                importedFileType = cached.fileType,
                downloadState = null,
                error = null,
                showCorruptedDialog = false
            )}
            return
        }

        _uiState.update { it.copy(
            selectedFormatIndex = index,
            downloadState = null,
            importedBookId = 0L,
            importedFilePath = null,
            importedFileType = null,
            error = null,
            showCorruptedDialog = false
        )}
        restoreJob = viewModelScope.launch { restoreForFormat(entry, index) }
    }

    fun downloadBook() {
        val entry = _uiState.value.entry ?: return
        if (isEnqueuing) return
        if (_uiState.value.downloadState?.isDownloading == true) return
        if (_uiState.value.isImporting) return
        if (!hasMadeFirstChoice) {
            pendingEntry = entry
            pendingLink = entry.acquisitionLinks.getOrNull(_uiState.value.selectedFormatIndex)
            _uiState.update { it.copy(showFirstDownloadDialog = true) }
            return
        }

        val link = entry.acquisitionLinks.getOrNull(_uiState.value.selectedFormatIndex) ?: return
        enqueueAndImport(entry, link)
    }

    fun downloadSample() {
        val entry = _uiState.value.entry ?: return
        val sampleLink = entry.sampleLink ?: return
        if (!hasMadeFirstChoice) {
            pendingEntry = entry
            pendingLink = sampleLink
            _uiState.update { it.copy(showFirstDownloadDialog = true) }
            return
        }
        enqueueAndImport(entry, sampleLink)
    }

    fun borrowBook() {
        val entry = _uiState.value.entry ?: return
        val borrowLink = entry.borrowLink ?: entry.acquisitionLinks.firstOrNull() ?: return
        if (!hasMadeFirstChoice) {
            pendingEntry = entry
            pendingLink = borrowLink
            _uiState.update { it.copy(showFirstDownloadDialog = true) }
            return
        }
        enqueueAndImport(entry, borrowLink)
    }

    fun onFirstDownloadChoiceAppInternal() {
        _uiState.update { it.copy(showFirstDownloadDialog = false) }
        viewModelScope.launch {
            guidePrefUtil.setOpdsDownloadPrefs("app_internal", "")
            guidePrefUtil.setOpdsFirstDownloadChoiceMade()
            opdsDownloadLocation = "app_internal"
            opdsSafTreeUri = ""
            hasMadeFirstChoice = true
            retryAfterChoice()
        }
    }

    fun onFirstDownloadChoiceSafTree(safTreeUri: String) {
        _uiState.update { it.copy(showFirstDownloadDialog = false) }
        viewModelScope.launch {
            guidePrefUtil.setOpdsDownloadPrefs("saf_tree", safTreeUri)
            guidePrefUtil.setOpdsFirstDownloadChoiceMade()
            opdsDownloadLocation = "saf_tree"
            opdsSafTreeUri = safTreeUri
            hasMadeFirstChoice = true
            retryAfterChoice()
        }
    }

    fun dismissFirstDownloadDialog() {
        _uiState.update { it.copy(showFirstDownloadDialog = false) }
        pendingEntry = null
        pendingLink = null
    }

    private fun retryAfterChoice() {
        val entry = pendingEntry ?: return
        val link = pendingLink ?: return
        pendingEntry = null
        pendingLink = null
        enqueueAndImport(entry, link)
    }

    private fun enqueueAndImport(entry: OpdsEntry, link: OpdsLink) {
        isEnqueuing = true
        currentFileId?.let { fileDownloadManager.cancelDownload(it) }
        persistedTargetPath?.let { path ->
            val file = File(path)
            if (file.exists()) {
                val deleted = file.delete()
                Logger.d("OpdsBookViewModel::enqueueAndImport: cleanup file: $path, deleted=$deleted")
            }
        }

        val result = downloadBookUseCase.enqueueDownload(entry, link, catalogId)
        currentFileId = result.fileId
        persistedTargetPath = result.targetPath
        expectedFileSize = link.length
        lastDownloadLink = link
        pendingMappings[result.fileId] = link.href
        _uiState.update { it.copy(error = null, showCorruptedDialog = false) }

        if (File(result.targetPath).exists()) {
            _uiState.update { it.copy(
                downloadState = DownloadState(id = result.fileId, isCompleted = true),
                isImporting = true
            )}
            isEnqueuing = false
            viewModelScope.launch { importBook(result.targetPath) }
        } else {
            _uiState.update { it.copy(
                downloadState = DownloadState(id = result.fileId, isPendingDownload = true)
            )}
            isEnqueuing = false
        }
    }

    fun navigateToReader(): String? {
        val state = _uiState.value
        val bookId = state.importedBookId
        val filePath = state.importedFilePath
        val fileType = state.importedFileType
        val title = state.importedBookTitle
        val author = state.importedBookAuthor
        if (bookId <= 0 || filePath.isNullOrBlank() || fileType.isNullOrBlank()) {
            Logger.w("OpdsBookViewModel::navigateToReader: invalid state bookId=$bookId filePath=$filePath fileType=$fileType")
            return null
        }
        // OPDS 封面是远程 URL，从 entry 取（导入后 entry 仍保留在 state 中）
        val coverUrl = state.entry?.coverUrl
        return buildReaderRoute(bookId, fileType, filePath, coverUrl, title, author)
    }

    private fun observeDownloadProgress() {
        viewModelScope.launch {
            fileDownloadManager.downloadStates.collect { states ->
                val fileId = currentFileId ?: return@collect
                val state = states[fileId] ?: return@collect
                _uiState.update { it.copy(downloadState = state) }
            }
        }
    }

    private fun observeDownloadComplete() {
        viewModelScope.launch {
            fileDownloadManager.downloadCompleteEvent.collect { (fileId, targetPath) ->
                if (fileId == currentFileId) {
                    _uiState.update { it.copy(isImporting = true) }
                    importBook(targetPath)
                }
            }
        }
    }

    private suspend fun importBook(targetPath: String) {
        val fileId = currentFileId
        val remoteUrl = fileId?.let { pendingMappings.remove(it) }
            ?: lastDownloadLink?.href
        downloadBookUseCase.importDownloadedBook(targetPath, expectedFileSize).fold(
            onSuccess = { book ->
                val isSafMode = opdsDownloadLocation == "saf_tree" && opdsSafTreeUri.isNotBlank()
                val finalBook = if (isSafMode) {
                    val safResult = downloadBookUseCase.copyToSafAndUpdateBook(book, targetPath, opdsSafTreeUri)
                    safResult.getOrElse { err ->
                        Logger.e("OpdsBookViewModel::importBook: SAF copy failed: $err")
                        book
                    }
                } else {
                    book
                }

                val formatIndex = _uiState.value.selectedFormatIndex
                formatImportCache[formatIndex] = ImportedBookInfo(finalBook.id, finalBook.filePath, finalBook.fileType)
                if (remoteUrl != null) {
                    opdsBookMappingUseCase.saveMapping(remoteUrl, catalogId, finalBook.id)
                }
                _uiState.update { it.copy(
                    isImporting = false,
                    importedBookId = finalBook.id,
                    importedFilePath = finalBook.filePath,
                    importedFileType = finalBook.fileType,
                    importedBookTitle = finalBook.title,
                    importedBookAuthor = finalBook.author,
                    downloadState = null,
                    error = null
                )}
            },
            onFailure = { e ->
                val isCorrupted = e is FileValidationException
                val errorRes = if (isCorrupted) R.string.opds_download_corrupted
                               else R.string.opds_import_failed
                _uiState.update { it.copy(
                    isImporting = false,
                    error = context.getString(errorRes),
                    showCorruptedDialog = isCorrupted
                )}
            }
        )
    }

    private fun buildFileId(entry: OpdsEntry, link: OpdsLink): String {
        return "opds_${catalogId}_${entry.id.hashCode().toUInt()}_${link.href.hashCode().toUInt()}"
    }

    fun retryAfterCorruption() {
        if (_uiState.value.downloadState?.isDownloading == true) return
        if (_uiState.value.isImporting) return
        val entry = _uiState.value.entry ?: return
        val link = lastDownloadLink ?: return
        _uiState.update { it.copy(showCorruptedDialog = false, error = null) }
        enqueueAndImport(entry, link)
    }

    fun dismissCorruptedDialog() {
        _uiState.update { it.copy(showCorruptedDialog = false) }
    }

    fun cancelDownload() {
        val fileId = currentFileId ?: return
        val targetPath = persistedTargetPath

        fileDownloadManager.cancelDownload(fileId)

        targetPath?.let { path ->
            File("$path.tmp").delete()
            File("$path.meta").delete()
            File(path).delete()
        }

        pendingMappings.remove(fileId)
        currentFileId = null
        persistedTargetPath = null
        expectedFileSize = null
        lastDownloadLink = null

        _uiState.update { it.copy(
            downloadState = null,
            error = null,
            showCorruptedDialog = false
        )}
    }
}
