package com.wxn.reader.presentation.bookReader.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAddCheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.sharp.Face
import androidx.compose.material.icons.sharp.Face2
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import com.wxn.reader.ui.theme.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.bean.TTSEngineType
import com.wxn.base.util.SherpaOnnxDeviceChecker
import com.wxn.base.util.ToastUtil
import com.wxn.base.util.toLocale
import com.wxn.bookread.data.model.preference.TtsPreferences
import com.wxn.reader.R
import com.wxn.reader.domain.model.TTSModelData
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.bookReader.util.TtsPlayerPanelStatus
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.format
import com.wxn.reader.util.tts.TtsNavigator
import com.wxn.base.bean.TtsPlaybackStatus
import com.wxn.bookread.ext.fmtToTime
import com.wxn.reader.util.tts.displayLocales

@Composable
fun TtsPlayer(
    viewModel: MainReadViewModel,
    areToolbarsVisible: Boolean,
    ttsPlayStatus: TtsPlaybackStatus,
    speed: Float,
    pitch: Float,
    playTimes: Float,
    language: LanguageInfo?,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onEnd: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onPlayTimeChange: (Float) -> Unit,
    onLanguageChange: (LanguageInfo) -> Unit,
    onSpeakerChange: (Int) -> Unit,
    onSkipToNextUtterance: () -> Unit,
    onSkipToPreviousUtterance: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    val heightAnimation by animateFloatAsState(
        targetValue = if (isExpanded && !areToolbarsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing), label = ""
    )
    val ttsPrefs by viewModel.ttsPrefs.collectAsStateWithLifecycle()

    //tts  面板控制, 0-关闭,
    // 1-打开显示主播放控制;
    // 2-显示TTS设置;
    // 3-显示语言选择界面;
    // 4-显示引擎切换选择界面;
    // 5-显示模型切换选择界面;
    // 6-显示语音切换选择界面
    val ttsPanelStatus by viewModel.ttsPanelStatus.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = ttsPanelStatus.value > 0, //总开关, 显示TTS播放/控制/设置面板
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
                                viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowPlayer)
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

                        //show Main Player
                        AnimatedVisibility(visible = (ttsPanelStatus == TtsPlayerPanelStatus.PanelShowPlayer)) {
                            MainTtsPlayer(
                                heightAnimation = heightAnimation,
                                onSkipToPreviousUtterance = onSkipToPreviousUtterance,
                                onSkipToNextUtterance = onSkipToNextUtterance,
                                ttsPlayStatus = ttsPlayStatus,
                                onPlay = onPlay,
                                onPause = onPause,
                                onEnd = onEnd,
                                showTtsSettings = {
                                    viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowSettings)
                                }
                            )
                        }

                        AnimatedVisibility(visible = (ttsPanelStatus == TtsPlayerPanelStatus.PanelShowSettings)) { //show TtsSettings
                            TtsSettings(
                                ttsPrefs = ttsPrefs,
                                heightAnimation = heightAnimation,
                                speed = speed,
                                pitch = pitch,
                                playTimes = playTimes,
                                onSpeedChange = onSpeedChange,
                                onPitchChange = onPitchChange,
                                onPlayTimeChange = onPlayTimeChange,
                                hideTtsSettings = {
                                    viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowPlayer)
                                },
                                showLanguageSettings = {
                                    if (ttsPrefs?.ttsEngineType == TTSEngineType.SYSTEM) {
                                        viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowLanguageSelect)
                                    } else {
                                        viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowVoiceSelect)
                                    }
                                },
                                showTtsEngineSettings = {
                                    viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowEngineSelect)
                                },
                                showTtsModelSettings = {
                                    viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowModelSelect)
                                    if (ttsPlayStatus.isPlaying) {
                                        viewModel.pauseTtsPlaying()
                                        ToastUtil.show(R.string.tts_paused_for_model_selection)
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(visible = ttsPanelStatus == TtsPlayerPanelStatus.PanelShowLanguageSelect) {
                            LanguageSettings(
                                viewModel = viewModel,
                                heightAnimation = heightAnimation,
                                currentLanguage = language,
                                onLanguageChange = onLanguageChange,
                                onClose = {
                                    viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowSettings)
                                },
                            )
                        }
                        AnimatedVisibility(visible = ttsPanelStatus == TtsPlayerPanelStatus.PanelShowVoiceSelect) {
                            SpeakerSettings(
                                currentSpeakerIndex = ttsPrefs?.selectedSpeaker ?: 0,
                                viewModel = viewModel,
                                heightAnimation = heightAnimation,
                                onSpeakerChange = onSpeakerChange,
                                onClose = {
                                    viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowSettings)
                                },
                            )
                        }

                        AnimatedVisibility(visible = ttsPanelStatus == TtsPlayerPanelStatus.PanelShowEngineSelect) {
                            val ctx = LocalContext.current
                            TTSEngineSelection(
                                heightAnimation = heightAnimation,
                                currentEngineType = ttsPrefs?.ttsEngineType ?: TTSEngineType.SYSTEM,
                                onItemCheck = { engineType ->
                                    if (engineType == TTSEngineType.OFFLINE_NEURAL_AI
                                        && !SherpaOnnxDeviceChecker.isDeviceSupported(ctx)
                                    ) {
                                        ToastUtil.show(R.string.err_device_not_support_ai_tts)
                                        viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowSettings)
                                    } else {
                                        viewModel.updateTTSEngineType(engineType)
                                        if (engineType == TTSEngineType.OFFLINE_NEURAL_AI) {
                                            viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowModelSelect)
                                        } else {
                                            viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowSettings)
                                        }
                                    }
                                },
                                onDismiss = {
                                    viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowSettings)
                                }
                            )
                        }

                        AnimatedVisibility(visible = ttsPanelStatus == TtsPlayerPanelStatus.PanelShowModelSelect) {
                            TTSModelsSelection(
                                heightAnimation = heightAnimation,
                                ttsPrefs?.selectedTTSModel.orEmpty(),
                                viewModel,
                                onModelClick = { ttsModelData ->
                                    viewModel.selectTtsModel(ttsModelData) {
                                        viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowVoiceSelect)
                                    }
                                },
                                onClose = {
                                    viewModel.setTtsPanelStatus(TtsPlayerPanelStatus.PanelShowSettings)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("UnrememberedMutableState")
@Composable
fun TTSModelsSelection(
    heightAnimation: Float,
    currentModel: String,
    viewModel: MainReadViewModel,
    onModelClick: (TTSModelData) -> Unit,
    onClose: () -> Unit
) {

    val navigator = LocalNavController.current

    val models by viewModel.localTTSModels.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadLocalTtsModels()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp * heightAnimation)
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (models.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(models) { model ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable{
                                    if (currentModel != model.name) {
                                        onModelClick(model)
                                    }
                                }
                        ) {
                            // Model header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = model.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style= MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = model.displayLocales(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.num_speakers, model.speakers_num),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    if (currentModel != model.name) {
                                        onModelClick(model)
                                    }
                                }) {
                                    Icon(
                                        imageVector = if (currentModel == model.name) Icons.Default.CheckCircleOutline else Icons.Default.RadioButtonUnchecked,
                                        tint = MaterialTheme.colorScheme.primary,
                                        contentDescription = if (currentModel == model.name) "Checked" else "UnCheck"
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable(onClick = {
                                    navigator.navigate(Screens.TTSModelsListPageScreen.route)
                                })
                            ) {

                                Text(stringResource(R.string.download_more),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                                Icon(
                                    imageVector = Icons.Default.ArrowCircleRight,
                                    tint = MaterialTheme.colorScheme.primary,
                                    contentDescription = "to download more.."
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.tts_model_download_hint_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tts_model_download_hint_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ElevatedButton(
                        modifier = Modifier.wrapContentSize(),
                        onClick = {
                            navigator.navigate(Screens.TTSModelsListPageScreen.route)
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)) {
                            Text(stringResource(R.string.go_to_download_model),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowCircleRight,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                                contentDescription = "to download more.."
                            )
                        }
                    }
                }
            }
        }

            // Action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
        }
    }
}


@Composable
fun MainTtsPlayer(
    heightAnimation: Float,
    onSkipToPreviousUtterance: () -> Unit,
    onSkipToNextUtterance: () -> Unit,
    ttsPlayStatus: TtsPlaybackStatus,
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
                .verticalScroll(rememberScrollState())
        ) {
            // Playback controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {


                ElevatedButton(
                    contentPadding = PaddingValues(0.dp),
                    onClick = onSkipToPreviousUtterance
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Skip backward"
                    )
                }

                ElevatedButton(
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(60.dp),
                    onClick = {
                        when (ttsPlayStatus) {
                            TtsPlaybackStatus.PLAYING -> onPause.invoke()
                            TtsPlaybackStatus.PAUSED, TtsPlaybackStatus.IDLE  -> onPlay.invoke()
                            else -> { /*nothing*/ }
                        }
                    }
                ) {
                    if (ttsPlayStatus == TtsPlaybackStatus.PENDING_PLAYING ||
                        ttsPlayStatus == TtsPlaybackStatus.PENDING_PAUSE) {
                        CircularProgressIndicator(modifier = Modifier.size(30.dp))
                    } else {
                        Icon(
                            imageVector = when (ttsPlayStatus) {
                                TtsPlaybackStatus.PLAYING -> Icons.Rounded.Pause
                                TtsPlaybackStatus.PAUSED, TtsPlaybackStatus.IDLE -> Icons.Rounded.PlayArrow
                                else -> Icons.Rounded.Pause
                            },
                            contentDescription = "play / pause",
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                ElevatedButton(
                    contentPadding = PaddingValues(0.dp),
                    onClick = onSkipToNextUtterance
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Skip forward"
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
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
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
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Stop TTS"
                    )
                    Text(
                        stringResource(R.string.close),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun TtsSettings(
    ttsPrefs: TtsPreferences?,
    heightAnimation: Float,
    speed: Float,
    pitch: Float,
    playTimes: Float,
    onSpeedChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onPlayTimeChange: (Float) -> Unit,
    hideTtsSettings: () -> Unit,
    showLanguageSettings: () -> Unit,
    showTtsEngineSettings: () -> Unit,
    showTtsModelSettings: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((if (ttsPrefs?.ttsEngineType != TTSEngineType.OFFLINE_NEURAL_AI) 320.dp else 270.dp) * heightAnimation)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()  .padding(16.dp)
        ) {

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f)
            ) {

                // Speed control
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.speed_x, speed.format(2)),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = speed.toFloat(),
                        onValueChange = onSpeedChange,
                        valueRange = TtsNavigator.TTS_MIN_SPEED..TtsNavigator.TTS_MAX_SPEED,
                        steps = 10,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (ttsPrefs?.ttsEngineType == TTSEngineType.SYSTEM) {
                    // Pitch control
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.pitch_x, pitch.format(2)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Slider(
                            value = pitch.toFloat(),
                            onValueChange = onPitchChange,
                            valueRange = TtsNavigator.TTS_MIN_PITCH..TtsNavigator.TTS_MAX_PITCH,
                            steps = 10,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Play time control
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.tts_set_times,
                            if (playTimes > 0) playTimes.fmtToTime() else stringResource(R.string.tts_time_unlimit)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = playTimes.toFloat(),
                        onValueChange = onPlayTimeChange,
                        valueRange = TtsNavigator.TTS_PLAY_MIN_TIMES.toFloat()..TtsNavigator.TTS_PLAY_MAX_TIMES.toFloat(),
                        steps = 7,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                //selection TTS engine
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    ElevatedButton(
                        contentPadding = PaddingValues(
                            vertical = 8.dp,
                            horizontal = 16.dp
                        ),
                        onClick = showTtsEngineSettings,
                    ) {
                        Icon(
                            imageVector =
                                if (ttsPrefs?.ttsEngineType == TTSEngineType.SYSTEM)
                                    Icons.Rounded.Apps
                                else Icons.Rounded.Api,
                            contentDescription = "Change tts engine"
                        )
                        Text(
                            text = stringResource(R.string.tts_set_engine),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp),
                            style= MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.size(6.dp))

                    if (ttsPrefs?.ttsEngineType != TTSEngineType.SYSTEM) {

                        ElevatedButton(
                            contentPadding = PaddingValues(
                                vertical = 8.dp,
                                horizontal = 16.dp
                            ),
                            onClick = showTtsModelSettings,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlaylistAddCheckCircle,
                                contentDescription = "Change tts engine model"
                            )
                            Text(
                                text = stringResource(R.string.select_tts_model),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

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
                // language button
                ElevatedButton(
                    contentPadding = PaddingValues(
                        vertical = 8.dp,
                        horizontal = 16.dp
                    ),
                    onClick = {
                        if (ttsPrefs?.ttsEngineType == TTSEngineType.SYSTEM || (
                                    ttsPrefs?.ttsEngineType == TTSEngineType.OFFLINE_NEURAL_AI &&
                                            !ttsPrefs.selectedTTSModel.isNullOrEmpty())
                        ) {
                            showLanguageSettings.invoke()
                        } else {
                            ToastUtil.show(R.string.no_model_selected)
                        }
                    },
                ) {
                    Icon(
                        imageVector =
                            if (ttsPrefs?.ttsEngineType == TTSEngineType.SYSTEM)
                                Icons.Rounded.Language else
                                Icons.Rounded.Speaker,
                        contentDescription = "Change tts language"
                    )
                    Text(
                        text =
                            if (ttsPrefs?.ttsEngineType == TTSEngineType.SYSTEM)
                                stringResource(R.string.language) else
                                stringResource(R.string.speaker),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}


@Composable
fun SpeakerSettings(
    currentSpeakerIndex: Int,
    viewModel: MainReadViewModel,
    onSpeakerChange: (Int) -> Unit,
    heightAnimation: Float,
    onClose: () -> Unit
) {
    val speakers by viewModel.currentSpeakers.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadLocalTtsSpeakers()
    }

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
            LazyColumn {
                items(speakers) { speaker ->
                    val isSelected = currentSpeakerIndex == speaker.index
                    ElevatedButton(
                        onClick = {
                            if (!isSelected) {
                                onSpeakerChange(speaker.index)
                                viewModel.selectSpeaker(speaker.index)
                            }
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

                        Row(modifier= Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start) {
                            Icon(
                                imageVector = if (speaker.gender == "female") Icons.Sharp.Face2 else Icons.Sharp.Face,
                                contentDescription = "face",
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column() {

                                Text(text = speaker.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(text = speaker.locale.toLocale()?.displayName.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "selected speaker",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier
                        .height(24.dp)
                        .fillMaxWidth())
                }
            }
        }

        ElevatedButton(
            contentPadding = PaddingValues(0.dp),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.BottomStart),
            onClick = onClose,
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Hide TTS settings",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}


@Composable
fun LanguageSettings(
    viewModel: MainReadViewModel,
    heightAnimation: Float,
    currentLanguage: LanguageInfo?,
    onLanguageChange: (LanguageInfo) -> Unit,
    onClose: () -> Unit
) {
    val languages = viewModel.getSupportedLanguages()

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

            LazyColumn {
                items(languages) { lang ->
                    val isSelected = lang.code == currentLanguage?.code
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
                        Text(text = lang.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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


@Composable
fun TTSEngineSelection(
    heightAnimation: Float,
    currentEngineType: TTSEngineType,
    onItemCheck: (engineType: TTSEngineType) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp * heightAnimation)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            TTSEngineType.entries.forEach { engineType ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onItemCheck(engineType)
                        }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentEngineType == engineType,
                        onClick = {
                            onItemCheck(engineType)
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (engineType) {
                            TTSEngineType.SYSTEM -> stringResource(R.string.tts_engine_system)
                            TTSEngineType.OFFLINE_NEURAL_AI -> stringResource(R.string.tts_engine_offline_ai)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                ElevatedButton(
                    contentPadding = PaddingValues(0.dp),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(48.dp),
                    onClick = onDismiss,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Hide TTS settings",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}