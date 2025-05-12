package com.wxn.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.data.model.AppLanguage
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.navigation.Screens
import com.wxn.reader.util.LanguageHelper
import com.wxn.base.util.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil,
    private val languageHelper: LanguageHelper,
    application: Application,
) : AndroidViewModel(application) {


    private val _appPreferences = MutableStateFlow(AppPreferencesUtil.defaultPreferences)
    val appPreferences: StateFlow<AppPreferences> = _appPreferences.asStateFlow()

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()


    init {
        viewModelScope.launch {
            try {
                val initialPreferences = appPreferencesUtil.appPreferencesFlow.first()
                Logger.d("SplashViewModel:Initial preferences: $initialPreferences")
                _appPreferences.value = initialPreferences
                languageHelper.changeLanguage(
                    getApplication(),
                    AppLanguage.fromCode(initialPreferences.language)
                )
                determineStartDestination(initialPreferences)
            } catch (e: Exception) {
                Logger.e("SplashViewModel:Initialization error,$e")
            }
        }
    }


    private fun determineStartDestination(prefs: AppPreferences) {
        _startDestination.value = if (prefs.isFirstLaunch) {
            Screens.GettingStartedScreen.route
        } else {
            Screens.HomeScreen.route
        }
        _isLoading.value = false
    }
}



