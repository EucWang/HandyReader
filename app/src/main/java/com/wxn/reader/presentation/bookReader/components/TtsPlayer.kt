package com.wxn.reader.presentation.bookReader.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.LanguageUtil
import com.wxn.reader.util.tts.TtsVoicesUiState

enum class VoiceSettingsLocation {
    BottomSheet,
    SettingsScreen,
}

enum class VoiceSettingsHeightMode {
    Fixed,
    FillAvailable;

    companion object {
        fun forLocation(location: VoiceSettingsLocation): VoiceSettingsHeightMode {
            return when (location) {
                VoiceSettingsLocation.BottomSheet -> Fixed
                VoiceSettingsLocation.SettingsScreen -> FillAvailable
            }
        }
    }
}

@Composable
fun TtsPlayer(
    areToolbarsVisible: Boolean,
    isTtsOn: Boolean,
    isTtsPlaying: Boolean,
    speed: Double,
    pitch: Double,
    bufferedParagraphs: Int,
    language: LanguageInfo,
    useBookLanguage: Boolean,
    voicesUiState: TtsVoicesUiState,
    selectedVoiceName: String,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onEnd: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onBufferedParagraphsChange: (Int) -> Unit,
    onLanguageChange: (LanguageInfo) -> Unit,
    onUseBookLanguage: () -> Unit,
    onLoadVoices: () -> Unit,
    onRetryVoices: () -> Unit,
    onVoiceChange: (String) -> Unit,
    onPreviewVoice: (String) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(true) }
    val heightAnimation by animateFloatAsState(
        targetValue = if (isExpanded && !areToolbarsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing), label = ""
    )


    var showTtsSettings by remember { mutableStateOf(false) }
    var showLanguageSettings by remember { mutableStateOf(false) }
    var showVoiceSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = isTtsOn,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                isExpanded = delta < 0
                                showTtsSettings = false
                            }
                        )
                ) {
                    Column {
                        // Drag handle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        ) {
                            HorizontalDivider(
                                thickness = 4.dp,
                                modifier = Modifier
                                    .width(50.dp)
                                    .align(Alignment.Center)
                                    .clip(MaterialTheme.shapes.extraLarge)
                            )
                        }


                        // Main content
                        AnimatedVisibility(
                            visible = !showTtsSettings && !showLanguageSettings && !showVoiceSettings
                        ) {
                            MainTtsPlayer(
                                heightAnimation = heightAnimation,
                                isTtsPlaying = isTtsPlaying,
                                onPlay = onPlay,
                                onPause = onPause,
                                onEnd = onEnd,
                                showTtsSettings = {
                                    onLoadVoices()
                                    showTtsSettings = true
                                }
                            )
                        }


                        AnimatedVisibility(
                            visible = showTtsSettings
                        ) {
                            TtsSettings(
                                heightAnimation = heightAnimation,
                                speed = speed,
                                pitch = pitch,
                                bufferedParagraphs = bufferedParagraphs,
                                onSpeedChange = onSpeedChange,
                                onPitchChange = onPitchChange,
                                onBufferedParagraphsChange = onBufferedParagraphsChange,
                                hideTtsSettings = { showTtsSettings = false },
                                showLanguageSettings = {
                                    showTtsSettings = false
                                    showLanguageSettings = true
                                },
                                showVoiceSettings = {
                                    onLoadVoices()
                                    showTtsSettings = false
                                    showVoiceSettings = true
                                },
                            )
                        }



                        AnimatedVisibility(
                            visible = showLanguageSettings
                        ) {
                            LanguageSettings(
                                heightAnimation = heightAnimation,
                                currentLanguage = language,
                                useBookLanguage = useBookLanguage,
                                onLanguageChange = onLanguageChange,
                                onUseBookLanguage = onUseBookLanguage,
                                onClose = {
                                    showLanguageSettings = false
                                    showTtsSettings = true
                                },
                            )
                        }

                        AnimatedVisibility(
                            visible = showVoiceSettings
                        ) {
                            VoiceSettings(
                                heightAnimation = heightAnimation,
                                voicesUiState = voicesUiState,
                                selectedVoiceName = selectedVoiceName,
                                onVoiceChange = onVoiceChange,
                                onPreviewVoice = onPreviewVoice,
                                onRetryVoices = onRetryVoices,
                                onClose = {
                                    showVoiceSettings = false
                                    showTtsSettings = true
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun MainTtsPlayer(
    heightAnimation: Float,
    isTtsPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onEnd: () -> Unit,
    showTtsSettings: () -> Unit,
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp * heightAnimation)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                ElevatedButton(
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(60.dp),
                    onClick = if (isTtsPlaying) onPause else onPlay
                ) {
                    Icon(
                        imageVector = if (isTtsPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "play / pause",
                        modifier = Modifier.size(38.dp)
                    )
                }

            }



            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ElevatedButton(
                    contentPadding = PaddingValues(
                        vertical = 8.dp,
                        horizontal = 16.dp
                    ),
                    onClick = showTtsSettings,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Show Tts settings"
                    )
                    Text(
                        stringResource(R.string.settings),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }


                // Stop button
                ElevatedButton(
                    contentPadding = PaddingValues(
                        vertical = 8.dp,
                        horizontal = 16.dp
                    ),
                    onClick = onEnd,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Stop TTS"
                    )
                    Text(
                        stringResource(R.string.stop),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun TtsSettings(
    heightAnimation: Float,
    speed: Double,
    pitch: Double,
    bufferedParagraphs: Int,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onBufferedParagraphsChange: (Int) -> Unit,
    hideTtsSettings: () -> Unit,
    showLanguageSettings: () -> Unit,
    showVoiceSettings: () -> Unit,
) {


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp * heightAnimation)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Speed control
            Text(
                text = stringResource(R.string.speed_x, speed.format(2)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = speed.toFloat(),
                onValueChange = onSpeedChange,
                valueRange = 0.25f..1.75f,
                steps = 5,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pitch control
            Text(
                text = stringResource(R.string.pitch_x, pitch.format(2)),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = pitch.toFloat(),
                onValueChange = onPitchChange,
                valueRange = 0.25f..1.75f,
                steps = 5,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.tts_buffered_paragraphs, bufferedParagraphs),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = bufferedParagraphs.toFloat(),
                onValueChange = { onBufferedParagraphsChange(it.toInt()) },
                valueRange = 3f..7f,
                steps = 3,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ElevatedButton(
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(48.dp),
                    onClick = hideTtsSettings,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Hide TTS settings",
                        modifier = Modifier.size(24.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElevatedButton(
                        contentPadding = PaddingValues(
                            vertical = 8.dp,
                            horizontal = 12.dp
                        ),
                        onClick = showLanguageSettings,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = "Change tts language"
                        )
                        Text(
                            text = stringResource(R.string.language),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    ElevatedButton(
                        contentPadding = PaddingValues(
                            vertical = 8.dp,
                            horizontal = 12.dp
                        ),
                        onClick = showVoiceSettings,
                    ) {
                        Text(stringResource(R.string.tts_voice))
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceSettings(
    heightAnimation: Float,
    location: VoiceSettingsLocation = VoiceSettingsLocation.BottomSheet,
    voicesUiState: TtsVoicesUiState,
    selectedVoiceName: String,
    onVoiceChange: (String) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onRetryVoices: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val heightMode = VoiceSettingsHeightMode.forLocation(location)
    var search by remember { mutableStateOf("") }
    val voices = (voicesUiState as? TtsVoicesUiState.Available)?.voices

    Box(
        modifier = (if (heightMode == VoiceSettingsHeightMode.FillAvailable) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .fillMaxWidth()
                .height(500.dp * heightAnimation)
        })
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        if (voices == null) {
            VoiceSettingsStatus(
                isLoading = voicesUiState == TtsVoicesUiState.Loading,
                onRetry = onRetryVoices,
                onClose = onClose,
            )
        } else {
            val visibleVoices = voices.filter { voice ->
                search.isBlank() || listOf(voice.name, voice.localeTag, voice.localeDisplayName)
                    .any { it.contains(search, ignoreCase = true) }
            }

            Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                ElevatedButton(
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(48.dp),
                    onClick = onClose,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Back to TTS settings",
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.tts_voice),
                    style = MaterialTheme.typography.titleMedium,
                )
                ElevatedButton(
                    onClick = {
                        val ttsSettingsIntent = Intent("com.android.settings.TTS_SETTINGS")
                        val intent = if (ttsSettingsIntent.resolveActivity(context.packageManager) != null) {
                            ttsSettingsIntent
                        } else {
                            Intent(Settings.ACTION_SETTINGS)
                        }
                        context.startActivity(intent)
                    },
                ) {
                    Text(stringResource(R.string.tts_system_settings))
                }
            }

            TextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.tts_search_voices)) },
            )

            ElevatedButton(
                onClick = { onVoiceChange("") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = if (selectedVoiceName.isBlank()) {
                    ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    ButtonDefaults.elevatedButtonColors()
                },
            ) {
                Text(stringResource(R.string.tts_voice_automatic))
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(visibleVoices, key = { it.name }) { voice ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ElevatedButton(
                            enabled = voice.isInstalled,
                            onClick = { onVoiceChange(voice.name) },
                            modifier = Modifier.weight(1f),
                            colors = if (selectedVoiceName == voice.name) {
                                ButtonDefaults.elevatedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                ButtonDefaults.elevatedButtonColors()
                            },
                        ) {
                            Column {
                                Text(voice.name)
                                Text(
                                    text = voice.details,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        ElevatedButton(
                            enabled = voice.isInstalled,
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            onClick = { onPreviewVoice(voice.name) },
                        ) {
                            Text(stringResource(R.string.tts_preview))
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun VoiceSettingsStatus(
    isLoading: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (isLoading) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.tts_voices_loading),
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            Text(text = stringResource(R.string.tts_voices_error))
            ElevatedButton(
                onClick = onRetry,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.tts_retry))
            }
        }
        ElevatedButton(
            onClick = onClose,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.close))
        }
    }
}


@Composable
fun LanguageSettings(
    heightAnimation: Float,
    currentLanguage: LanguageInfo,
    useBookLanguage: Boolean,
    onLanguageChange: (LanguageInfo) -> Unit,
    onUseBookLanguage: () -> Unit,
    onClose: () -> Unit
) {
    val languages = LanguageUtil.languageMaps.values.toList()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp * heightAnimation)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                ElevatedButton(
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(48.dp),
                    onClick = onClose,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Hide TTS settings",
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .wrapContentSize(Alignment.Center),
                    text = "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }

            ElevatedButton(
                onClick = {
                    onUseBookLanguage()
                    onClose()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = if (useBookLanguage) {
                    ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    ButtonDefaults.elevatedButtonColors()
                },
            ) {
                Text(stringResource(R.string.tts_language_from_book))
                if (useBookLanguage) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "selected language",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            LazyColumn {
                items(languages) { lang ->
                    val isSelected = !useBookLanguage && lang.code == currentLanguage.code
                    ElevatedButton(
                        onClick = {
                            onLanguageChange(lang)
                            onClose()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = if (isSelected) {
                            ButtonDefaults.elevatedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            ButtonDefaults.elevatedButtonColors()
                        }
                    ) {
                        Text(text = lang.displayName)
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "selected language",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


// Helper function to format Double to 2 decimal places
fun Double.format(digits: Int) = "%.${digits}f".format(this)
