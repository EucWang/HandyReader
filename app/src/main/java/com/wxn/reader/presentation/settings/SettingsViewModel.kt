package com.wxn.reader.presentation.settings

import kotlinx.coroutines.flow.stateIn
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.bean.TTSEngineType
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.model.GuidePreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.data.source.local.GuidePrefUtil
import com.wxn.reader.domain.repository.PermissionRepository
import com.wxn.base.util.Logger
import com.wxn.base.util.launchMain
import com.wxn.bookread.data.model.preference.TtsPreferences
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.reader.BookApplication
import com.wxn.reader.domain.use_case.books.DirectoryDeleteResult
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.LanguageUtil
import com.wxn.reader.domain.use_case.books.RemoveScanDirectoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DeleteDirectoryState {
    data object Idle : DeleteDirectoryState()
    data class Confirming(
        val directoryUri: String,
        val directoryName: String,
        val bookCount: Int
    ) : DeleteDirectoryState()
    data class Deleting(
        val directoryUri: String,
        val directoryName: String,
        val current: Int,
        val total: Int,
        val bookTitle: String
    ) : DeleteDirectoryState()
    data class TtsBlocked(val directoryName: String) : DeleteDirectoryState()
    data class Completed(
        val directoryName: String,
        val deletedBooks: Int,
        val totalBooks: Int,
        val failedBooks: Int = 0
    ) : DeleteDirectoryState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil,
    private val permissionRepository: PermissionRepository,
    private val ttsPreferencesUtil: TtsPreferencesUtil,
    private val removeScanDirectoryUseCase: RemoveScanDirectoryUseCase,
    private val guidePrefUtil: GuidePrefUtil,
    application: Application,
) : AndroidViewModel(application) {


    private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
    val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()

    private val _ttsPreferences = MutableStateFlow<TtsPreferences?>(null)
    val ttsPreferences: StateFlow<TtsPreferences?> = _ttsPreferences.asStateFlow()

    val opdsDownloadPrefs: StateFlow<GuidePreferences> = guidePrefUtil.guidePrefsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, GuidePreferences())

    private val _deleteDirectoryState = MutableStateFlow<DeleteDirectoryState>(DeleteDirectoryState.Idle)
    val deleteDirectoryState: StateFlow<DeleteDirectoryState> = _deleteDirectoryState.asStateFlow()

    init {
        viewModelScope.launch {
            // Continue collecting preferences updates
            appPreferencesUtil.appPrefsFlow.stateIn(viewModelScope).collect { preferences ->
                _appPreferences.value = preferences
            }
        }

        viewModelScope.launch {
            ttsPreferencesUtil.ttsPreferencesFlow.stateIn(viewModelScope).collect { ttsPrefs ->
                _ttsPreferences.value = ttsPrefs
            }
        }
    }


    fun updatePdfSupport(isPdfSupported: Boolean) {
        viewModelScope.launch {
            val prefs = _appPreferences.value ?: return@launch
            appPreferencesUtil.updateAppPreferences(prefs.copy(enablePdfSupport = isPdfSupported))
        }
    }


    fun addScanDirectory(uri: Uri) {
        viewModelScope.launch {
            val prefs = appPreferences.value ?: return@launch
            val currentDirectories = prefs.scanDirectories
            val directory = uri.toString()
            permissionRepository.grantPersistableUriPermission(uri)
            if (!currentDirectories.contains(directory)) {
                val updatedDirectories = currentDirectories + directory
                Logger.d("SettingsViewModel:addScanDirectory:the Settings viewModel")
                appPreferencesUtil.updateAppPreferences(prefs.copy(scanDirectories = updatedDirectories))
            }
        }
    }
//    fun addScanDirectory(directory: String) {
//        viewModelScope.launch {
//            val currentDirectories = appPreferences.value.scanDirectories
//            if (!currentDirectories.contains(directory)) {
//                val updatedDirectories = currentDirectories + directory
//                Log.d("it's me", "the Settings viewModel")
//                appPreferencesUtil.updateAppPreferences(appPreferences.value.copy(scanDirectories = updatedDirectories))
//            }
//        }
//    }

    fun removeScanDirectory(directory: String) {
        viewModelScope.launch {
            val prefs = _appPreferences.value ?: return@launch
            val updatedDirectories = prefs.scanDirectories - directory
            Logger.d("removeScanDirectory::$directory")
            appPreferencesUtil.updateAppPreferences(prefs.copy(scanDirectories = updatedDirectories))
            permissionRepository.releasePersistableUriPermission(Uri.parse(directory))
        }
    }

    fun updateOpdsDownloadPrefs(location: String, safTreeUri: String = "") {
        viewModelScope.launch {
            guidePrefUtil.setOpdsDownloadPrefs(location, safTreeUri)
        }
    }

    fun updateOpdsSafTreeUri(safTreeUri: String) {
        viewModelScope.launch {
//            val prefs = guidePrefUtil.guidePrefsFlow.first()
            guidePrefUtil.setOpdsDownloadPrefs("saf_tree", safTreeUri)
        }
    }

    fun prepareDeleteDirectory(directoryUri: String) {
        viewModelScope.launch {
            val uri = android.net.Uri.parse(directoryUri)
            val directoryName = uri.lastPathSegment?.substringAfter(":") ?: directoryUri
            val bookCount = removeScanDirectoryUseCase.getBookCountInDirectory(directoryUri)
            _deleteDirectoryState.value = DeleteDirectoryState.Confirming(
                directoryUri = directoryUri,
                directoryName = directoryName,
                bookCount = bookCount
            )
        }
    }

    fun confirmDeleteDirectory() {
        val state = _deleteDirectoryState.value
        if (state !is DeleteDirectoryState.Confirming) return

        val directoryUri = state.directoryUri
        val directoryName = state.directoryName

        _deleteDirectoryState.value = DeleteDirectoryState.Deleting(
            directoryUri = directoryUri,
            directoryName = directoryName,
            current = 0,
            total = 0,
            bookTitle = ""
        )

        viewModelScope.launch {
            try {
                val result = removeScanDirectoryUseCase.execute(
                    directoryUri = directoryUri,
                    onProgress = { progress ->
                        when (progress) {
                            is RemoveScanDirectoryUseCase.DeleteProgress.DeletingBook ->
                                _deleteDirectoryState.value = DeleteDirectoryState.Deleting(
                                    directoryUri = directoryUri,
                                    directoryName = directoryName,
                                    current = progress.current,
                                    total = progress.total,
                                    bookTitle = progress.bookTitle
                                )
                            is RemoveScanDirectoryUseCase.DeleteProgress.Completed -> {
                                when (val r = progress.result) {
                                    is DirectoryDeleteResult.TtsBlocked ->
                                        _deleteDirectoryState.value = DeleteDirectoryState.TtsBlocked(directoryName)
                                    is DirectoryDeleteResult.Success ->
                                        _deleteDirectoryState.value = DeleteDirectoryState.Completed(
                                            directoryName = directoryName,
                                            deletedBooks = r.deletedBooks,
                                            totalBooks = r.totalBooks,
                                            failedBooks = r.failedBooks
                                        )
                                    is DirectoryDeleteResult.Empty -> {}
                                }
                            }
                            else -> {}
                        }
                    }
                )
                when (result) {
                    is DirectoryDeleteResult.Empty ->
                        _deleteDirectoryState.value = DeleteDirectoryState.Completed(
                            directoryName = directoryName,
                            deletedBooks = 0,
                            totalBooks = 0
                        )
                    is DirectoryDeleteResult.TtsBlocked ->
                        _deleteDirectoryState.value = DeleteDirectoryState.TtsBlocked(directoryName)
                    else -> {}
                }
            } catch (e: Exception) {
                Logger.e("confirmDeleteDirectory: ${e.message}")
                _deleteDirectoryState.value = DeleteDirectoryState.Idle
            }
        }
    }

    fun resetDeleteDirectoryState() {
        _deleteDirectoryState.value = DeleteDirectoryState.Idle
    }

    fun updateLanguage(language: LanguageInfo) {
        LanguageUtil.changeLanguage(getApplication(), language.code)
        viewModelScope.launchMain {
            delay(200)
            getApplication<BookApplication>().onLanguageChange()
        }
    }

    fun updateAutoOpenLastRead(autoOpen:Boolean) {
        viewModelScope.launch {
            val currentPreferences = _appPreferences.value ?: return@launch
            if (currentPreferences.autoOpenLastRead != autoOpen) {
                val updatedPreferences = currentPreferences.copy(autoOpenLastRead = autoOpen)
                appPreferencesUtil.updateAppPreferences(updatedPreferences)
                _appPreferences.value = updatedPreferences
            }
            Logger.d("SettingsViewModel::updateAutoOpenLastRead::autoOpen[$autoOpen]")
        }
    }


    /***
     * 更新引擎类型
     */
    fun updateTTSEngineType(engineType: TTSEngineType) {
        viewModelScope.launch {
            val ttsPrefs = _ttsPreferences.value ?: return@launch
            // Clear model selection when switching to system TTS
            val (newModel, newSpeaker) = if (engineType == TTSEngineType.SYSTEM) {
                null to 0
            } else {
                ttsPrefs.selectedTTSModel to ttsPrefs.selectedSpeaker
            }

            ttsPreferencesUtil.updatePreferences(
                ttsPrefs.copy(
                    ttsEngineType = engineType,
                    selectedTTSModel = newModel,
                    selectedSpeaker = newSpeaker,
                    isFirstAiTtsSelection = if (engineType == TTSEngineType.OFFLINE_NEURAL_AI) false else ttsPrefs.isFirstAiTtsSelection
                )
            )
        }
    }
}