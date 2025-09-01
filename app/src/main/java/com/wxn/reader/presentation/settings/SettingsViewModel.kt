package com.wxn.reader.presentation.settings

import kotlinx.coroutines.flow.stateIn
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.repository.PermissionRepository
import com.wxn.base.util.Logger
import com.wxn.base.util.launchMain
import com.wxn.reader.BookApplication
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.LanguageUtil
import com.wxn.reader.util.PurchaseHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil,
    private val permissionRepository: PermissionRepository,
    application: Application,
) : AndroidViewModel(application) {


    private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
    val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()


    init {
        viewModelScope.launch {
            // Continue collecting preferences updates
            appPreferencesUtil.appPrefsFlow.stateIn(viewModelScope).collect { preferences ->
                _appPreferences.value = preferences
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

    fun updateLanguage(language: LanguageInfo) {
        LanguageUtil.changeLanguage(getApplication(), language.lang)
        viewModelScope.launchMain {
            delay(200)
            getApplication<BookApplication>().onLanguageChange()
        }
    }

    fun purchasePremium(purchaseHelper: PurchaseHelper) {
        purchaseHelper.makePurchase()
        viewModelScope.launch {
            purchaseHelper.isPremium.collect { isPremium ->
                updatePremiumStatus(isPremium)
            }
        }
    }

    private fun updatePremiumStatus(isPremium: Boolean) {
        viewModelScope.launch {
            val prefs = _appPreferences.value ?: return@launch
            if (prefs.isPremium != isPremium) {
                val updatedPreferences = prefs.copy(isPremium = isPremium)
                appPreferencesUtil.updateAppPreferences(updatedPreferences)
                _appPreferences.value = updatedPreferences
            }
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

}