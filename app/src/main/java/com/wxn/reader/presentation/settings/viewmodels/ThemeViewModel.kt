package com.wxn.reader.presentation.settings.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.data.model.AppTheme
import com.wxn.reader.data.model.ThemePreferences
import com.wxn.reader.data.source.local.ThemePreferencesUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


sealed class ThemeUpdateEvent {
    data object ThemeUpdated : ThemeUpdateEvent()
    data class ColorSchemeUpdated(val colorScheme: String) : ThemeUpdateEvent()
    data class AppThemeUpdated(val appTheme: AppTheme) : ThemeUpdateEvent()
}

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreferencesUtil: ThemePreferencesUtil,
    application: Application,
) : AndroidViewModel(application) {

    private val _themePreferences = MutableStateFlow<ThemePreferences?>(null)
    val themePreferences: StateFlow<ThemePreferences?> = _themePreferences.asStateFlow()

    private val _updateEvent = MutableStateFlow<ThemeUpdateEvent?>(null)
    val updateEvent: StateFlow<ThemeUpdateEvent?> = _updateEvent.asStateFlow()

    init {
        observeAppPreferences()
    }


    private fun observeAppPreferences() {
        viewModelScope.launch {
            themePreferencesUtil.themePrefsFlow.collect { preferences ->
                _themePreferences.value = preferences
            }
        }
    }

    fun updateThemePreferences(newPreferences: ThemePreferences) {
        viewModelScope.launch {
            themePreferencesUtil.updateAppPreferences(newPreferences)
            _updateEvent.value = ThemeUpdateEvent.ThemeUpdated
        }
    }

    fun updateThemePreferences(newAppTheme: AppTheme, newColorScheme: String) {
        viewModelScope.launch {
            themePreferencesUtil.updateTheme(newAppTheme, newColorScheme)
            _updateEvent.value = ThemeUpdateEvent.ThemeUpdated
        }
    }

    fun updateColorSchemePreferences(newColorScheme: String) {
        viewModelScope.launch {
            themePreferencesUtil.updateColorTheme(newColorScheme)
            _updateEvent.value = ThemeUpdateEvent.ColorSchemeUpdated(newColorScheme)
        }
    }

    fun updateAppThemePreferences(newAppTheme: AppTheme) {
        viewModelScope.launch {
            themePreferencesUtil.updateAppTheme(newAppTheme)
            _updateEvent.value = ThemeUpdateEvent.AppThemeUpdated(newAppTheme)
        }
    }

    fun clearUpdateEvent() {
        _updateEvent.value = null
    }
}
