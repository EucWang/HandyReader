package com.wxn.reader.presentation.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.bookReader.components.LanguageSettings
import com.wxn.reader.presentation.bookReader.components.TtsSettings
import com.wxn.reader.presentation.bookReader.components.VoiceSettings
import com.wxn.reader.presentation.bookReader.components.VoiceSettingsLocation
import com.wxn.reader.presentation.settings.TtsSettingsViewModel
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.LanguageUtil

private enum class TtsSettingsPage {
    Controls,
    Language,
    Voice,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsScreen(
    viewModel: TtsSettingsViewModel = hiltViewModel(),
) {
    val navController: NavHostController = LocalNavController.current
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val voicesUiState by viewModel.voicesUiState.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(TtsSettingsPage.Controls) }

    val language = LanguageInfo.fromCode(preferences.localeCode) ?: LanguageUtil.LANG_EN

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tts_set)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (page) {
                TtsSettingsPage.Controls -> TtsSettings(
                    heightAnimation = 1f,
                    speed = preferences.speed.toDouble(),
                    pitch = preferences.pitch.toDouble(),
                    bufferedParagraphs = preferences.bufferedParagraphs,
                    onSpeedChange = viewModel::setSpeed,
                    onPitchChange = viewModel::setPitch,
                    onBufferedParagraphsChange = viewModel::setBufferedParagraphs,
                    hideTtsSettings = { navController.navigateUp() },
                    showLanguageSettings = { page = TtsSettingsPage.Language },
                    showVoiceSettings = {
                        viewModel.loadVoices()
                        page = TtsSettingsPage.Voice
                    },
                )

                TtsSettingsPage.Language -> LanguageSettings(
                    heightAnimation = 1f,
                    currentLanguage = language,
                    useBookLanguage = preferences.useBookLanguage,
                    onLanguageChange = viewModel::setLanguage,
                    onUseBookLanguage = viewModel::useBookLanguage,
                    onClose = { page = TtsSettingsPage.Controls },
                )

                TtsSettingsPage.Voice -> VoiceSettings(
                    heightAnimation = 1f,
                    location = VoiceSettingsLocation.SettingsScreen,
                    voicesUiState = voicesUiState,
                    selectedVoiceName = preferences.voiceName,
                    onVoiceChange = viewModel::setVoice,
                    onPreviewVoice = viewModel::previewVoice,
                    onRetryVoices = viewModel::retryVoices,
                    onClose = { page = TtsSettingsPage.Controls },
                )
            }
        }
    }
}
