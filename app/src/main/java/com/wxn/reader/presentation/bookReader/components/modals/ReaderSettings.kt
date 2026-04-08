package com.wxn.reader.presentation.bookReader.components.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.reader.presentation.mainReader.MainReadViewModel


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettings(
    viewModel: MainReadViewModel,
    readerPreferences: ReaderPreferences,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)


    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.reader_settings),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(
                    onClick = {
                        viewModel.resetReaderPreferences()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)

                ) {
                    Text(
                        text = stringResource(R.string.reset),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Keep Screen On
                SettingsSwitch(
                    title = stringResource(R.string.keep_screen_on),
                    checked = readerPreferences.keepScreenOn,
                    onCheckedChange = { isKeepScreenOn ->
                        viewModel.updateReaderPreferences(
                            readerPreferences.copy(
                                keepScreenOn = isKeepScreenOn,
                            )
                        )
                    }
                )

                // Volume Key Page Turning
                SettingsSwitch(
                    title = stringResource(R.string.volume_key_page_turning),
                    checked = readerPreferences.volumeKeyPageTurning,
                    onCheckedChange = { isVolumeKeyPageTurning ->
                        viewModel.updateReaderPreferences(
                            readerPreferences.copy(
                                volumeKeyPageTurning = isVolumeKeyPageTurning,
                            )
                        )
                    }
                )


                // Scroll Mode
    //                title = stringResource(R.string.scroll_mode),
    //                checked = readerPreferences.scroll,
    //                onCheckedChange = { isScrollMode ->
    //                    viewModel.updateReaderPreferences(
    //                        readerPreferences.copy(
    //                            scroll = isScrollMode,
    //                            tapNavigation = if (isScrollMode) false else readerPreferences.tapNavigation
    //                        )
    //                    )
    //                }
    //            )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(stringResource(R.string.scroll_mode), style = MaterialTheme.typography.titleMedium)

                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            0 to stringResource(R.string.no_page_trans_anim),
                            1 to stringResource(R.string.page_trans_anim_cover_horizontal),
                            2 to stringResource(R.string.page_trans_anim_slide_horizontal),
                            3 to stringResource(R.string.page_trans_anim_simulation),
                            4 to stringResource(R.string.page_trans_anim_cover_vertical),
                            5 to stringResource(R.string.page_trans_anim_slide_vertical),
                        ) .forEach {  (id, label) ->
                            FilledTonalButton(
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (readerPreferences.scroll == id) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    contentColor = if (readerPreferences.scroll == id) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                ),
                                onClick = {
                                    viewModel.updateReaderPreferences(readerPreferences.copy(scroll = id))
                                }
                            ) {
                                Text(text = label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Animation Speed
                SettingsSlider(
                    title = stringResource(R.string.animation_speed),
                    value = readerPreferences.animationSpeed.toFloat(),
                    onValueChange = { newValue ->
                        viewModel.updateReaderPreferences(
                            readerPreferences.copy(animationSpeed = newValue.toInt())
                        )
                    },
                    valueRange = 50f..800f,
                    valueDisplay = { "${it.toInt()}ms" }
                )

                // Click Area Mode
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = stringResource(R.string.click_area_mode),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        /**FilledTonalButton(
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (readerPreferences.scroll == id) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                contentColor = if (readerPreferences.scroll == id) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                            onClick = {
                                viewModel.updateReaderPreferences(readerPreferences.copy(scroll = id))
                            }
                        ) {
                            Text(text = label, style = MaterialTheme.typography.bodySmall)
                        }*/

                        FilledTonalButton(
                            onClick = {
                                viewModel.updateReaderPreferences(
                                    readerPreferences.copy(clickAreaMode = 0)
                                )
                                viewModel.showClickAreaMode(0)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (readerPreferences.clickAreaMode == 0) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                contentColor = if (readerPreferences.clickAreaMode == 0) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                        ) {
                            Text(stringResource(R.string.click_area_center), style = MaterialTheme.typography.bodySmall)
                        }
                        FilledTonalButton(
                            onClick = {
                                viewModel.updateReaderPreferences(
                                    readerPreferences.copy(clickAreaMode = 1)
                                )
                                viewModel.showClickAreaMode(1)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (readerPreferences.clickAreaMode == 1) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                contentColor = if (readerPreferences.clickAreaMode == 1) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        ) {
                            Text(stringResource(R.string.click_area_top), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Left-handed Mode
                SettingsSwitch(
                    title = stringResource(R.string.left_handed_mode),
                    checked = readerPreferences.leftHandedMode,
                    onCheckedChange = { isLeftHandedMode ->
                        viewModel.updateReaderPreferences(
                            readerPreferences.copy(
                                leftHandedMode = isLeftHandedMode,
                            )
                        )
                        val clickAreadMode = readerPreferences.clickAreaMode
                        viewModel.showClickAreaMode(clickAreadMode, isLeftHandedMode)
                    }
                )
                // Tap Navigation
    //            SettingsSwitch(
    //                title = stringResource(R.string.tap_navigation),
    //                checked = readerPreferences.tapNavigation,
    //                onCheckedChange = { isTapNavigation ->
    //                    viewModel.updateReaderPreferences(
    //                        readerPreferences.copy(
    //                            tapNavigation = isTapNavigation,
    ////                            scroll = if (isTapNavigation) false else readerPreferences.scroll
    //                        )
    //                    )
    //                }
    //            )

                //Reading Progression
    //            Column(
    //                modifier = Modifier.fillMaxWidth(),
    //                horizontalAlignment = Alignment.CenterHorizontally,
    //                verticalArrangement = Arrangement.spacedBy(6.dp)
    //            ) {
    //                Text(stringResource(R.string.reading_progression), style = MaterialTheme.typography.titleMedium)
    //                Row(
    //                    modifier = Modifier,
    //                    horizontalArrangement = Arrangement.spacedBy(8.dp)
    //                ) {
    //                    listOf(
    //                        ConfigReadingProgression.LTR to stringResource(R.string.left_to_right),
    //                        ConfigReadingProgression.RTL to stringResource(R.string.right_to_left),
    //                    ).forEach { (readingProgression, label) ->
    //                        FilledTonalButton(
    //                            colors = ButtonDefaults.buttonColors(
    //                                containerColor = if (readerPreferences.readingProgression == readingProgression) {
    //                                    MaterialTheme.colorScheme.primaryContainer
    //                                } else {
    //                                    MaterialTheme.colorScheme.surfaceVariant
    //                                },
    //                                contentColor = if (readerPreferences.readingProgression == readingProgression) {
    //                                    MaterialTheme.colorScheme.onPrimaryContainer
    //                                } else {
    //                                    MaterialTheme.colorScheme.onSurfaceVariant
    //                                }
    //                            ),
    //                            onClick = {
    //                                viewModel.updateReaderPreferences(readerPreferences.copy(readingProgression = readingProgression))
    //                            }
    //                        ) {
    //                            Text(text = label, style = MaterialTheme.typography.bodySmall)
    //                        }
    //                    }
    //                }
    //            }

    //            // Vertical Text
    //            SettingsSwitch(
    //                title = stringResource(R.string.vertical_text),
    //                checked = readerPreferences.verticalText,
    //                onCheckedChange = { viewModel.updateReaderPreferences(readerPreferences.copy(verticalText = it)) }
    //            )
    //
    //            // Publisher Styles
    //            SettingsSwitch(
    //                title = stringResource(R.string.publisher_styles),
    //                checked = readerPreferences.publisherStyles,
    //                onCheckedChange = { viewModel.updateReaderPreferences(readerPreferences.copy(publisherStyles = it)) }
    //            )
    //
    //            // Text Normalisation
    //            SettingsSwitch(
    //                title = stringResource(R.string.text_normalization),
    //                checked = readerPreferences.textNormalization,
    //                onCheckedChange = { viewModel.updateReaderPreferences(readerPreferences.copy(textNormalization = it)) }
    //            )
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: (Float) -> String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.weight(1f).height(32.dp),
            )

            Text(
                text = valueDisplay(value),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.wrapContentWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
