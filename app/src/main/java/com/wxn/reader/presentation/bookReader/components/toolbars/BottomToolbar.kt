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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ChromeReaderMode
import androidx.compose.material.icons.automirrored.sharp.ArrowBack
import androidx.compose.material.icons.automirrored.sharp.ArrowForward
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.util.Logger
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import com.wxn.reader.util.LogCompositions
import com.wxn.reader.util.OnLaunchFlow
import com.wxn.reader.util.consumeClick
import com.wxn.reader.util.format

@Composable
fun BottomToolbar(
    textPageFactory:  TextPageFactory?,
    showToolbar: Boolean,
    viewModel: MainReadViewModel,
    onToggleFontSettings: () -> Unit,
    onTogglePageSettings: () -> Unit,
    onToggleUISettings: () -> Unit,
    onToggleReaderSettings: () -> Unit
) {

    val progression by viewModel.readProgression.collectAsStateWithLifecycle()
    var sliderPosition by remember { mutableStateOf(progression) }
    LogCompositions("BottomToolbar:progression=${progression}")

    OnLaunchFlow(emitter = {progression}) {
        sliderPosition = progression
    }

    AnimatedVisibility(
        visible = showToolbar,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = Modifier
            .fillMaxWidth()
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
                        .background(DrawerDefaults.modalContainerColor.copy(alpha = 1f), RoundedCornerShape(50.dp))
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
                        .background(DrawerDefaults.modalContainerColor.copy(alpha = 0.95f), RoundedCornerShape(50.dp))
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
//                            colors = SliderDefaults.colors(
//                                thumbColor = MaterialTheme.colorScheme.primary,
//                                activeTrackColor = MaterialTheme.colorScheme.onSurface,
//                                inactiveTrackColor = MaterialTheme.colorScheme.surface)
//                            ,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            text = "100%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(
                    onClick = { textPageFactory?.moveToNext(true) },
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(50.dp))
                        .background(DrawerDefaults.modalContainerColor.copy(alpha = 1f), RoundedCornerShape(50.dp))
                        .padding(0.dp)

                ) {
                    Icon(Icons.AutoMirrored.Sharp.ArrowForward,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "Forward")
                }
            }


            // Buttons Row
            Row(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .shadow(
                        elevation = 8.dp,
                        clip = true
                    )
                    .offset(y = (15).dp)
                    .padding(bottom = 8.dp)
//                    .background(Color.White.copy(alpha = 1f))
                    .background(DrawerDefaults.modalContainerColor)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onToggleReaderSettings() }) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ChromeReaderMode,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "Reader Settings",
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { onToggleUISettings() }) {
                    Icon(
                        Icons.Filled.SettingsBrightness,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "UI Settings",
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { onTogglePageSettings() }) {
                    Icon(
                        Icons.Filled.FormatSize,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "Page Settings",
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(onClick = { onToggleFontSettings() }) {
                    Icon(
                        Icons.Filled.TextFormat,
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "Font Settings",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

        }
    }
}