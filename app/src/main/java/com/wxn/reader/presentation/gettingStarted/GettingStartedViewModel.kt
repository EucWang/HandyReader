package com.wxn.reader.presentation.gettingStarted

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.data.source.local.AppPreferencesUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GettingStartedViewModel @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil,
    application: Application,
) : AndroidViewModel(application) {

    fun skipGettingStarted() {
        viewModelScope.launch {
            val prefs = appPreferencesUtil.appPrefsFlow.first()
            val updated = prefs.copy(isFirstLaunch = false)
            appPreferencesUtil.updateAppPreferences(updated)
        }
    }
}
