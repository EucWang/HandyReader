package com.wxn.reader.presentation.gettingStarted

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GettingStartedViewModel @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil,
    application: Application,
) : AndroidViewModel(application) {

    private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
    val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()

    private val _isButtonsEnabled = MutableStateFlow(true)
    val isButtonsEnabled: StateFlow<Boolean> = _isButtonsEnabled.asStateFlow()


    init {
        observeAppPreferences()
    }

    private fun observeAppPreferences() {
        viewModelScope.launch {
            appPreferencesUtil.appPrefsFlow.collect { preferences ->
                _appPreferences.value = preferences
            }
        }
    }


    fun updateAppPreferences(newPreferences: AppPreferences) {
        viewModelScope.launch {
            _isButtonsEnabled.value = false
            appPreferencesUtil.updateAppPreferences(newPreferences)
            _appPreferences.value = newPreferences
        }
    }

    fun skipGettingStarted() {
        viewModelScope.launch {
            val prefs = _appPreferences.value ?: return@launch
            val updatedPreferences = prefs.copy(isFirstLaunch = false)
            updateAppPreferences(updatedPreferences)
        }
    }
}