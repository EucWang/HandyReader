package com.wxn.reader.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.bookread.data.model.preference.TtsPreferences
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.tts.TtsEngineState
import com.wxn.reader.util.tts.TtsNavigator
import com.wxn.reader.util.tts.TtsVoicesUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TtsSettingsViewModel @Inject constructor(
    private val ttsNavigator: TtsNavigator,
) : ViewModel() {

    private val _preferences = MutableStateFlow(TtsPreferencesUtil.defaultPreferences.copy())
    val preferences: StateFlow<TtsPreferences> = _preferences.asStateFlow()

    private val _voicesUiState = MutableStateFlow<TtsVoicesUiState>(TtsVoicesUiState.Loading)
    val voicesUiState: StateFlow<TtsVoicesUiState> = _voicesUiState.asStateFlow()

    init {
        viewModelScope.launch {
            ttsNavigator.preferencesFlow.collect { preferences ->
                _preferences.value = preferences
            }
        }
        viewModelScope.launch {
            ttsNavigator.engineState.collect(::updateVoicesUiState)
        }
    }

    fun setSpeed(speed: Float) {
        _preferences.value = _preferences.value.copy(speed = speed)
        ttsNavigator.setSpeed(speed)
    }

    fun setPitch(pitch: Float) {
        _preferences.value = _preferences.value.copy(pitch = pitch)
        ttsNavigator.setPitch(pitch)
    }

    fun setBufferedParagraphs(bufferedParagraphs: Int) {
        val safeBufferSize = bufferedParagraphs.coerceIn(3, 7)
        _preferences.value = _preferences.value.copy(bufferedParagraphs = safeBufferSize)
        ttsNavigator.setBufferedParagraphs(safeBufferSize)
    }

    fun setLanguage(language: LanguageInfo) {
        if (ttsNavigator.setLanguage(language, useBookLanguage = false)) {
            _preferences.value = _preferences.value.copy(
                localeCode = language.code,
                useBookLanguage = false,
            )
        }
    }

    fun useBookLanguage() {
        _preferences.value = _preferences.value.copy(useBookLanguage = true)
        ttsNavigator.useBookLanguage()
    }

    fun loadVoices() {
        updateVoicesUiState(ttsNavigator.engineState.value)
    }

    fun retryVoices() {
        ttsNavigator.retryEngine()
    }

    fun setVoice(voiceName: String) {
        if (ttsNavigator.setVoice(voiceName)) {
            _preferences.value = _preferences.value.copy(voiceName = voiceName)
        }
    }

    fun previewVoice(voiceName: String) {
        ttsNavigator.previewVoice(voiceName, replaceCurrentSpeech = true)
    }

    private fun updateVoicesUiState(engineState: TtsEngineState) {
        _voicesUiState.value = when (engineState) {
            TtsEngineState.Ready -> TtsVoicesUiState.Available(ttsNavigator.availableVoices())
            else -> TtsVoicesUiState.forEngine(engineState)
        }
    }
}
