package com.wxn.reader.presentation.settings.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.util.Logger
import com.wxn.reader.data.model.AppTheme
import com.wxn.reader.data.model.ThemePreferences
import com.wxn.reader.data.source.local.ThemePreferencesUtil
import com.wxn.reader.ui.theme.ColorSchemeOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


sealed class ThemeUpdateEvent {
    data object ThemeUpdated : ThemeUpdateEvent()
    data class ColorSchemeUpdated(val colorScheme: ColorSchemeOption) : ThemeUpdateEvent()
    /** Emitted when persisting a color-scheme change fails (e.g. disk full / IO error). */
    data object ColorSchemeUpdateFailed : ThemeUpdateEvent()
    data class AppThemeUpdated(val appTheme: AppTheme) : ThemeUpdateEvent()
}

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themePreferencesUtil: ThemePreferencesUtil,
    application: Application,
) : AndroidViewModel(application) {

    private val _themePreferences = MutableStateFlow<ThemePreferences?>(null)
    val themePreferences: StateFlow<ThemePreferences?> = _themePreferences.asStateFlow()

    /**
     * Emitted as a SharedFlow (replay = 0, extraBufferCapacity = 1) so rapid taps coalesce: a fast
     * A→B→C chain only surfaces the final event, and `tryEmit` drops overflow instead of
     * suspending/stacking snackbars.
     */
    private val _updateEvent = MutableSharedFlow<ThemeUpdateEvent>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val updateEvent: SharedFlow<ThemeUpdateEvent> = _updateEvent.asSharedFlow()

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
            _updateEvent.tryEmit(ThemeUpdateEvent.ThemeUpdated)
        }
    }

    fun updateThemePreferences(newAppTheme: AppTheme, newColorScheme: ColorSchemeOption) {
        viewModelScope.launch {
            themePreferencesUtil.updateTheme(newAppTheme, newColorScheme)
            _updateEvent.tryEmit(ThemeUpdateEvent.ThemeUpdated)
        }
    }

    fun updateColorSchemePreferences(newColorScheme: ColorSchemeOption) {
        viewModelScope.launch {
            try {
                themePreferencesUtil.updateColorTheme(newColorScheme)
                _updateEvent.tryEmit(ThemeUpdateEvent.ColorSchemeUpdated(newColorScheme))
            } catch (e: Exception) {
                // K4: surface a generic failure instead of silently swallowing it. DataStore flow
                // keeps the UI on the previous value, so no rollback is needed.
                Logger.e("ThemeViewModel: failed to persist colorScheme", e)
                _updateEvent.tryEmit(ThemeUpdateEvent.ColorSchemeUpdateFailed)
            }
        }
    }

    fun updateAppThemePreferences(newAppTheme: AppTheme) {
        viewModelScope.launch {
            themePreferencesUtil.updateAppTheme(newAppTheme)
            _updateEvent.tryEmit(ThemeUpdateEvent.AppThemeUpdated(newAppTheme))
        }
    }
}
