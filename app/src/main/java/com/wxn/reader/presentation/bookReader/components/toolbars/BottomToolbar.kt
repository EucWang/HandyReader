package com.wxn.reader.presentation.bookReader.components.toolbars

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.ArrowForward
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.util.Logger
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.R
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import com.wxn.reader.ui.theme.Slider
import com.wxn.reader.util.HeightSpace
import com.wxn.reader.util.OnLaunchFlow
import com.wxn.reader.util.consumeClick
import com.wxn.reader.util.format

@Composable
fun BottomToolbar(
    textPageFactory: TextPageFactory?,
    showToolbar: Boolean,
    viewModel: MainReadViewModel,
    onToggleAppearanceSettings: () -> Unit,
    onToggleReaderSettings: () -> Unit,
    textToSpeech: () -> Unit
) {
    val ttsPlayStatus by viewModel.ttsPlayStatus.collectAsStateWithLifecycle()

    val showProgressBar by viewModel.showProgressBar.collectAsStateWithLifecycle()

    val progression by viewModel.readProgression.collectAsStateWithLifecycle()
    var sliderPosition by remember { mutableStateOf(progression) }
    val density = LocalDensity.current
    val navBarHeight = with(density) {
        WindowInsets.navigationBars.getBottom(density).toDp()
    }

    OnLaunchFlow(emitter = { progression }) {
        sliderPosition = progression
    }

    AnimatedVisibility(
        visible = showToolbar,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent)
                .consumeClick()

        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(
                    onClick = { textPageFactory?.moveToPrev(true) },
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(50.dp))
                        .background(
                            DrawerDefaults.modalContainerColor.copy(alpha = 1f),
                            RoundedCornerShape(50.dp)
                        )
                        .padding(0.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Sharp.ArrowBack,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "Back",
                    )
                }

                Box(
                    modifier = Modifier
                        .height(46.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(50.dp))
                        .background(
                            DrawerDefaults.modalContainerColor.copy(alpha = 0.95f),
                            RoundedCornerShape(50.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = Color.Transparent,
                            shape = RoundedCornerShape(50.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 0.dp)
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (showProgressBar) {
                            Text(
                                text = "${(sliderPosition * 100).format(2, false)}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Slider(
                                value = sliderPosition.toFloat(),
                                onValueChange = { newValue ->
                                    Logger.d("BottomToolbar::onValueChange:newValue=$newValue")
                                    sliderPosition = newValue.toDouble()
                                },
                                onValueChangeFinished = {
                                    if (!viewModel.changePageByProgress(sliderPosition)) {
                                        sliderPosition = progression
                                    }
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Text(
                                text = stringResource(R.string.zoom_percentage, 100),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.Center),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = { textPageFactory?.moveToNext(true) },
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(50.dp))
                        .background(
                            DrawerDefaults.modalContainerColor.copy(alpha = 1f),
                            RoundedCornerShape(50.dp)
                        )
                        .padding(0.dp)

                ) {
                    Icon(
                        Icons.AutoMirrored.Sharp.ArrowForward,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "Forward"
                    )
                }
            }

            HeightSpace(6.dp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = navBarHeight)   // 底部留出导航栏高度，背景延伸覆盖
                ) {
                    // Buttons Row
                    Row(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            viewModel.showReaderDrawer(true)
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                tint = MaterialTheme.colorScheme.onSurface,
                                contentDescription = "Chapters",
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(onClick = {
                            textToSpeech()
                        }) {
                            if (ttsPlayStatus.isPending
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (ttsPlayStatus.isPlaying)
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    else Icons.AutoMirrored.Filled.VolumeUp,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    contentDescription = "Chapters",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        IconButton(onClick = { onToggleReaderSettings() }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ChromeReaderMode,
                                tint = MaterialTheme.colorScheme.onSurface,
                                contentDescription = "Reader Settings",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = { onToggleAppearanceSettings() }) {
                            Icon(
                                Icons.Filled.TextFormat,
                                tint = MaterialTheme.colorScheme.onSurface,
                                contentDescription = "Appearance Settings",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                }
            }
        }
    }
}