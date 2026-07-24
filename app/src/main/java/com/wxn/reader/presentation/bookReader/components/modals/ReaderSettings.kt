package com.wxn.reader.presentation.bookReader.components.modals

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.wxn.reader.ui.theme.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wxn.base.util.BrightnessHelper
import com.wxn.reader.R
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.ext.roundWithDot
import com.wxn.reader.presentation.bookReader.components.DictionaryPickerSheet
import com.wxn.reader.presentation.bookReader.components.TranslatePickerSheet
import com.wxn.reader.presentation.mainReader.MainReadViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderSettings(
    viewModel: MainReadViewModel,
    readerPreferences: ReaderPreferences,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val translatorPrefs by viewModel.translatorPrefs.collectAsState()
    val translatorItems by viewModel.translatorItems.collectAsState()
    var showTranslatorPicker by remember { mutableStateOf(false) }

    val dictionaryPrefs by viewModel.dictionaryPrefs.collectAsState()
    val dictionaryItems by viewModel.dictionaryItems.collectAsState()
    var showDictionaryPicker by remember { mutableStateOf(false) }

    val brightness by viewModel.brightness.collectAsState()

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
                        viewModel.updateKeepScreenOn(isKeepScreenOn)
                    }
                )

                // Brightness Set
                val context = LocalContext.current
                DisposableEffect(readerPreferences.brightnessSet) {
                    if (!readerPreferences.brightnessSet) {
                        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                            override fun onChange(selfChange: Boolean) {
                                val sliderVal = BrightnessHelper.getSystemBrightnessSliderValue(
                                    context.contentResolver, fallback = viewModel.brightness.value
                                )
                                viewModel.updateBrightnessFromSystem(sliderVal)
                            }
                        }
                        context.contentResolver.registerContentObserver(
                            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                            false, observer
                        )
                        val handler = Handler(Looper.getMainLooper())
                        val pollingRunnable = object : Runnable {
                            override fun run() {
                                val sliderVal = BrightnessHelper.getSystemBrightnessSliderValue(
                                    context.contentResolver, fallback = viewModel.brightness.value
                                )
                                viewModel.updateBrightnessFromSystem(sliderVal)
                                handler.postDelayed(this, 3000L)
                            }
                        }
                        handler.postDelayed(pollingRunnable, 1000L)
                        onDispose {
                            context.contentResolver.unregisterContentObserver(observer)
                            handler.removeCallbacks(pollingRunnable)
                        }
                    } else {
                        onDispose { }
                    }
                }

                SettingsSlider(
                    title = stringResource(R.string.brightness_set),
                    value = brightness,
                    onValueChange = { newValue ->
                        viewModel.updateBrightness(newValue)
                    },
                    onValueChangeFinished = {
                        viewModel.commitBrightness()
                    },
                    liveDrag = true,
                    valueRange = 0.0f..1.0f,
                    valueDisplay = {
                        it.roundWithDot(1)
                    }
                )

                // Volume Key Page Turning
                SettingsSwitch(
                    title = stringResource(R.string.volume_key_page_turning),
                    checked = readerPreferences.volumeKeyPageTurning,
                    onCheckedChange = { isVolumeKeyPageTurning ->
                        viewModel.updateVolumeKeyPageTurning(isVolumeKeyPageTurning)
                    }
                )

                // v5 双列显示（dual-column）。与连续垂直滚动（scroll=6）互斥：
                // - scroll=6 时双列开关置灰（连续滚动不分页，无「列」概念）
                // - 双列开启时 scroll=6 按钮置灰（见下方 FlowRow 的 enabled 判断）
                val isScroll6 = readerPreferences.scroll == 6
                SettingsSwitch(
                    title = stringResource(R.string.dual_column),
                    checked = readerPreferences.columns == 2,
                    onCheckedChange = { enabled ->
                        if (!isScroll6) viewModel.updateDualColumn(enabled)
                    },
                    enabled = !isScroll6
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

                    // v5：双列开启时 scroll=6（连续滚动）置灰——两者语义互斥
                    val isDualCol = readerPreferences.columns == 2
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            0 to stringResource(R.string.no_page_trans_anim),
                            1 to stringResource(R.string.page_trans_anim_cover_horizontal),
                            2 to stringResource(R.string.page_trans_anim_slide_horizontal),
                            3 to stringResource(R.string.page_trans_anim_simulation),
                            4 to stringResource(R.string.page_trans_anim_cover_vertical),
                            5 to stringResource(R.string.page_trans_anim_slide_vertical),
                            6 to stringResource(R.string.page_trans_anim_scroll),
                        ) .forEach {  (id, label) ->
                            // v5 S6：scroll=6 在双列开启时置灰；补 disabledContainerColor/disabledContentColor
                            // 对齐 M3 规范，避免默认灰底与自定义配色冲突
                            val isDisabled = (id == 6 && isDualCol)
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
                                    },
                                    // v5 S6：补 disabled 样式
                                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                ),
                                enabled = !isDisabled,
                                onClick = {
                                    viewModel.updateScrollType(scrollType = id)
                                }
                            ) {
                                Text(text = label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (readerPreferences.scroll != 6) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Animation Speed
                    SettingsSlider(
                        title = stringResource(R.string.animation_speed),
                        value = readerPreferences.animationSpeed.toFloat(),
                        onValueChange = { newValue ->
                            viewModel.updateAnimSpeed(newValue.toInt())
                        },
                        valueRange = 50f..800f,
                        valueDisplay = { "${it.toInt()}ms" },
                        enabled = readerPreferences.scroll != 6
                    )
                }

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
                            FilledTonalButton(
                                onClick = {
                                    viewModel.updateClickAreaMode(0)
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
                                    viewModel.updateClickAreaMode(1)
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

                if (readerPreferences.scroll != 6) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Left-handed Mode
                    SettingsSwitch(
                        title = stringResource(R.string.left_handed_mode),
                        checked = readerPreferences.leftHandedMode,
                        onCheckedChange = { isLeftHandedMode ->
                            viewModel.updateLeftHandMode(isLeftHandedMode)
                            val clickAreadMode = readerPreferences.clickAreaMode
                            viewModel.showClickAreaMode(clickAreadMode, isLeftHandedMode)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Translation Service
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.showTranslatePickerForSettings()
                            showTranslatorPicker = true
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.translation_service),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (translatorPrefs.lastSelectedTranslator.isNotBlank()) {
                                val currentItem = translatorItems.find { it.id == translatorPrefs.lastSelectedTranslator }
                                currentItem?.name ?: translatorPrefs.lastSelectedTranslator
                            } else {
                                stringResource(R.string.none)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Dictionary Service
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.showDictionaryPickerForSettings()
                            showDictionaryPicker = true
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.dictionary_service),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (dictionaryPrefs.defaultLookupApp.isNotBlank()) {
                                val currentItem = dictionaryItems.find { it.id == dictionaryPrefs.defaultLookupApp }
                                currentItem?.name ?: stringResource(R.string.built_in_dictionary_name)
                            } else {
                                stringResource(R.string.none)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))

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

        if (showTranslatorPicker) {
            TranslatePickerSheet(
                items = translatorItems,
                currentSelectedId = translatorPrefs.lastSelectedTranslator,
                isSetAsDefault = translatorPrefs.lastSelectedTranslator.isNotBlank(),
                onConfirm = { translatorId, setAsDefault ->
                    if (setAsDefault) {
                        viewModel.updateDefaultTranslator(translatorId)
                    } else {
                        viewModel.clearDefaultTranslator()
                    }
                    showTranslatorPicker = false
                },
                onDismiss = {
                    showTranslatorPicker = false
                }
            )
        }

        if (showDictionaryPicker) {
            DictionaryPickerSheet(
                items = dictionaryItems,
                currentSelectedId = dictionaryPrefs.defaultLookupApp,
                isSetAsDefault = dictionaryPrefs.defaultLookupApp.isNotBlank(),
                onConfirm = { dictId, setAsDefault ->
                    if (setAsDefault) {
                        viewModel.updateDefaultDictionary(dictId)
                    } else {
                        viewModel.clearDefaultDictionary()
                    }
                    showDictionaryPicker = false
                },
                onDismiss = {
                    showDictionaryPicker = false
                }
            )
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true   // v5：新增 enabled 参数，参照 SettingsSlider 的 disabled 写法
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            // v5：对齐 SettingsSlider:569 的 disabled 文本色
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
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
    valueDisplay: (Float) -> String,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    // When false (default), [onValueChange] fires only once — on release. Use this for values
    // whose commit is expensive (font size, spacing, margins…): each such commit persists to
    // DataStore and triggers a full chapter repagination, so firing it on every drag increment
    // causes lag and mis-sized flashing. Set true only for cheap live-preview values (e.g.
    // brightness) that should update continuously while dragging; those must commit/persist in
    // [onValueChangeFinished].
    liveDrag: Boolean = false,
    modifier : Modifier = Modifier.fillMaxWidth()
) {
    // The thumb tracks the finger via purely-local state so the label + thumb move smoothly even
    // when the commit is deferred to release. `remember(value)` re-seeds the local state whenever
    // the persisted value changes from outside (theme switch, reset-to-default), keeping the thumb
    // in sync with external updates.
    var sliderValue by remember(value) { mutableStateOf(value) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    if (liveDrag) onValueChange(it)
                },
                onValueChangeFinished = {
                    if (!liveDrag) onValueChange(sliderValue)
                    onValueChangeFinished?.invoke()
                },
                valueRange = valueRange,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(32.dp),
            )

            Text(
                text = valueDisplay(sliderValue),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.wrapContentWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
