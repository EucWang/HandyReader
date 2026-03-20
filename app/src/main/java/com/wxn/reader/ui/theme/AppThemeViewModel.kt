package com.wxn.reader.ui.theme

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.data.model.ThemePreferences
import com.wxn.reader.data.source.local.ThemePreferencesUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppThemeViewModel @Inject constructor(
    private val themePreferencesUtil: ThemePreferencesUtil,
    application: Application,
) : AndroidViewModel(application) {

    private val _themePreferences = MutableStateFlow<ThemePreferences?>(null)
    val themePreferences: StateFlow<ThemePreferences?> = _themePreferences.asStateFlow()

    init {
        viewModelScope.launch {
            themePreferencesUtil.themePrefsFlow.collect { preferences ->
                _themePreferences.value = preferences
            }
        }
    }
}