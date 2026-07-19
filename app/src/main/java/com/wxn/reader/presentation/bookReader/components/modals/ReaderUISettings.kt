package com.wxn.reader.presentation.bookReader.components.modals

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PhotoSizeSelectActual
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.elixer.palette.constraints.HorizontalAlignment
import com.elixer.palette.constraints.VerticalAlignment
import com.wxn.base.ext.toComposeColor
import com.wxn.base.util.ToastUtil
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.reader.R
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.domain.use_case.font.FontListItem
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.mainReader.MainReadViewModel
import com.wxn.reader.util.ColorPicker
import com.wxn.reader.util.PermissionHandler
import com.wxn.reader.util.LoadingPanel
import com.wxn.reader.util.Presets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.ui.graphics.Color as ComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderUISettings(
    appPreferences: AppPreferences,
    viewModel: MainReadViewModel,
    readerPreferences: ReaderPreferences,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showFontName by viewModel.showFontName.collectAsStateWithLifecycle()

    // 预设色板：从 ReaderThemePresets.ALL 派生（单一数据源，根治 Q-01 手抄不同步）。
    // 详见 PredefinedColors.ALL，ColorSection 按 effectiveIsDark + asBackground 过滤显示。

    var showDialog by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val readingBgPath = viewModel.updateReadingBgImage(context, uri)
            if (readingBgPath != null) {
                viewModel.updateReaderBgImage(readingBgPath)
            }
        }
    }
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val readingBgPath = viewModel.updateReadingBgImage(context, uri)
            if (readingBgPath != null) {
                viewModel.updateReaderBgImage(readingBgPath)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.READ_MEDIA_IMAGES, false) ||
                    permissions.getOrDefault(Manifest.permission.READ_EXTERNAL_STORAGE, false) -> {
                imagePicker.launch("image/*")
            }
        }
    }
    val uiScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(0) { 2 }
    val readUiEditType by viewModel.editingType.collectAsStateWithLifecycle()
    // * 号标识：被微调过的主题集合
    val modifiedThemeIds by viewModel.modifiedThemeIds.collectAsStateWithLifecycle()
    // ★ v11 per-book："仅本书生效"开关状态 + 首次提示
    val isPerBookEnabled by viewModel.isPerBookEnabled.collectAsStateWithLifecycle()
    val showPerBookTip by viewModel.showPerBookTip.collectAsStateWithLifecycle()
    val currentBookId by viewModel.currentBookId.collectAsStateWithLifecycle()
    // 当前阅读模式
    val readerThemeMode = readerPreferences.readerThemeMode
    // 系统暗色信号（AUTO 模式实际生效值；Compose 自动响应系统变化 recompose）
    val systemInDarkTheme = isSystemInDarkTheme()
    // 有效暗色：LIGHT→false，DARK→true，AUTO→系统当前
    val effectiveIsDark = when (readerThemeMode) {
        com.wxn.bookread.data.model.preference.ReaderThemeMode.LIGHT -> false
        com.wxn.bookread.data.model.preference.ReaderThemeMode.DARK -> true
        com.wxn.bookread.data.model.preference.ReaderThemeMode.AUTO -> systemInDarkTheme
    }
    // AUTO 模式：系统明暗变化时切配对主题（debounce 500ms 防抖）
    LaunchedEffect(systemInDarkTheme) {
        if (readerThemeMode == com.wxn.bookread.data.model.preference.ReaderThemeMode.AUTO) {
            delay(500)
            viewModel.applyAutoModeSwitch(systemInDarkTheme)
        }
    }
    // 面板打开时刷新 * 号标识
    LaunchedEffect(Unit) {
        viewModel.refreshModifiedThemeIds()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
                contentAlignment = Alignment.Center
            ) {
                // ★ v11 per-book："全局 / 本书"切换（仅 page 0 显示，标题栏在 pager 外用 page 判定）
                // 药丸 Chip：icon + 文字整体可点击（M3 组合交互元素原则），紧凑省标题栏空间。
                if (pagerState.currentPage == 0 && currentBookId != null) {
                    val perBookLabel = stringResource(
                        if (isPerBookEnabled) R.string.per_book_label else R.string.global_label
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(MaterialTheme.shapes.extraLarge)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                currentBookId?.let {
                                    viewModel.togglePerBookOverride(it, !isPerBookEnabled)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (!isPerBookEnabled) {
                                Icons.Default.Public
                            } else {
                                Icons.Default.PublicOff
                            },
                            contentDescription = perBookLabel,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = perBookLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.reader_ui_settings),
                    style = MaterialTheme.typography.titleMedium,
                )

                TextButton(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onClick = {
                        when (pagerState.currentPage) {
                            0 -> viewModel.resetCurrentTheme()
                            else -> {
                                uiScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when (pagerState.currentPage) {
                            0 -> stringResource(R.string.reset)
                            else -> stringResource(R.string.back)
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ★ v11 per-book：首次开启提示 Overlay（淡入淡出，首次 OFF→ON 弹一次）
            if (showPerBookTip == true) {
                PerBookOverrideTipOverlay(
                    onDismiss = { viewModel.markPerBookTipShown() }
                )
            }

            HorizontalPager(
                userScrollEnabled = false,
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
            ) { index ->
                when (index) {
                    0 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {




                            // 阅读主题模式切换器（需求 3）：浅色/深色/自动
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 32.dp)
                            ) {
                                val modes = listOf(
                                    com.wxn.bookread.data.model.preference.ReaderThemeMode.LIGHT to R.string.reader_theme_mode_light,
                                    com.wxn.bookread.data.model.preference.ReaderThemeMode.DARK to R.string.reader_theme_mode_dark,
                                    com.wxn.bookread.data.model.preference.ReaderThemeMode.AUTO to R.string.reader_theme_mode_auto,
                                )
                                modes.forEachIndexed { index, (mode, labelRes) ->
                                    SegmentedButton(
                                        selected = readerThemeMode == mode,
                                        onClick = { viewModel.updateReaderThemeMode(mode) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                                    ) {
                                        Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 阅读主题选择器（置顶，先选主题再细调排版）
                            ReaderThemeSelector(
                                selectedThemeId = readerPreferences.readerThemeId,
                                onSelectTheme = { viewModel.switchTheme(it) },
                                isDarkMode = effectiveIsDark,
                                modifiedThemeIds = modifiedThemeIds
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            SectionHeader(title = stringResource(R.string.font_settings))

                            SettingsSlider(
                                title = stringResource(R.string.font_size),
                                value = (readerPreferences.fontSize * 100).toFloat(),
                                onValueChange = {
                                    viewModel.updateFontSize((it / 100).toDouble())
                                },
                                valueRange = 50f..200f,
                                valueDisplay = { "${it.toInt()}%" },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )

                            SettingsSlider(
                                title = stringResource(R.string.line_height),
                                value = readerPreferences.lineHeight.toFloat(),
                                onValueChange = {
                                    viewModel.updateLineHeight(it.toDouble())
                                },
                                valueRange = 1.0f..3.0f,
                                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) },
                                enabled = !readerPreferences.publisherStyles,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )

                            SettingsSlider(
                                title = stringResource(R.string.letter_spacing),
                                value = readerPreferences.letterSpacing.toFloat(),
                                onValueChange = {
                                    viewModel.updateLetterSpacing(it.toDouble())
                                },
                                valueRange = 0.0f..1.0f,
                                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) },
                                enabled = !readerPreferences.publisherStyles,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )

                            ColorSection(
                                title = stringResource(R.string.text_color),
                                currentColor = readerPreferences.textColor.toComposeColor(),
                                predefinedColors = com.wxn.reader.ui.theme.PredefinedColors.ALL,
                                effectiveIsDark = effectiveIsDark,
                                asBackground = false,
                                onColorSelected = { color ->
                                    viewModel.updateTextColor(color.toArgb())
                                },
                                onCustomColorClicked = {
                                    viewModel.setReadUiEditType(ReadUiEditType.ColorType_TEXT)
                                    uiScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

//                            val navController = LocalNavController.current
                            ListItem(
                                headlineContent = {
                                    Text(stringResource(R.string.font_management))
                                },
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = showFontName,
                                            maxLines = 1,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = ComposeColor.Transparent
                                ),
                                modifier = Modifier.clickable {
                                    viewModel.setReadUiEditType(ReadUiEditType.FontType)
                                    uiScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(8.dp))

                            SectionHeader(title = stringResource(R.string.page_settings))

                            // --- Text Alignment Override ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.text_align_override),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = readerPreferences.forceAlignOverride,
                                    onCheckedChange = { viewModel.updateTextAlign(it, readerPreferences.userTextAlign) }
                                )
                            }

                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                val alignOptions = listOf(
                                    1 to R.string.text_align_left,
                                    2 to R.string.text_align_right,
                                    3 to R.string.text_align_center,
                                    4 to R.string.text_align_justify,
                                )
                                alignOptions.forEachIndexed { index, (value, labelRes) ->
                                    SegmentedButton(
                                        selected = readerPreferences.userTextAlign == value,
                                        onClick = { viewModel.updateTextAlign(readerPreferences.forceAlignOverride, value) },
                                        enabled = true,
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = alignOptions.size)
                                    ) {
                                        Text(
                                            text = stringResource(labelRes),
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            SettingsSlider(
                                title = stringResource(R.string.page_horizontal_margins),
                                value = readerPreferences.pageHorizontalMargins.toFloat(),
                                onValueChange = {
                                    viewModel.updatePageHorizontalMargins(it.toDouble())
                                },
                                valueRange = 0.0f..5.0f,
                                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )

                            SettingsSlider(
                                title = stringResource(R.string.page_vertial_margins),
                                value = readerPreferences.pageVerticalMargins.toFloat(),
                                onValueChange = {
                                    viewModel.updatePageVerticalMargins(it.toDouble())
                                },
                                valueRange = 0.0f..5.0f,
                                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )

                            SettingsSlider(
                                title = stringResource(R.string.paragraph_indent),
                                value = readerPreferences.paragraphIndent.toFloat(),
                                onValueChange = {
                                    viewModel.updateParagraphIndent(it.toDouble())
                                },
                                valueRange = 0.0f..3.0f,
                                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) },
                                enabled = !readerPreferences.publisherStyles,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )

                            SettingsSlider(
                                title = stringResource(R.string.paragraph_spacing),
                                value = readerPreferences.paragraphSpacing.toFloat(),
                                onValueChange = {
                                    viewModel.updateParagraphSpacing(it.toDouble())
                                },
                                valueRange = 0.0f..3.0f,
                                valueDisplay = { String.format(Locale.getDefault(), "%.1f", it) },
                                enabled = !readerPreferences.publisherStyles,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            )

                            ColorSection(
                                title = stringResource(R.string.background_color),
                                currentColor = readerPreferences.backgroundColor.toComposeColor(),
                                predefinedColors = com.wxn.reader.ui.theme.PredefinedColors.ALL,
                                effectiveIsDark = effectiveIsDark,
                                asBackground = true,
                                onColorSelected = { color ->
                                     viewModel.updateBgColorWithNonImage(color.toArgb())
                                },
                                onCustomColorClicked = {
                                    viewModel.setReadUiEditType(ReadUiEditType.ColorType_BACKGROUND)
                                    uiScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                            )
                            // 背景图片独立设置项（需求 5：从 ColorSection 剥离，提升可发现性）
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.background_image)) },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Default.PhotoSizeSelectActual,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (readerPreferences.backgroundImage.isEmpty()) {
                                                stringResource(R.string.none)
                                            } else {
                                                ""
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = ComposeColor.Transparent
                                ),
                                modifier = Modifier.clickable { showDialog = true }
                            )
                        }
                    }

                    1 -> {
                        if (readUiEditType != ReadUiEditType.FontType) { //颜色卡片
                            ColorPicker(
                                defaultColor = when (readUiEditType) {
                                    ReadUiEditType.ColorType_BACKGROUND -> readerPreferences.backgroundColor.toComposeColor()
                                    ReadUiEditType.ColorType_TEXT -> readerPreferences.textColor.toComposeColor()
                                    else -> readerPreferences.backgroundColor.toComposeColor()
                                },
                                buttonSize = 70.dp,
                                swatches = Presets.material(),
                                innerRadius = 200f,
                                strokeWidth = 80f,
                                spacerRotation = 0f,
                                spacerOutward = 3f,
                                verticalAlignment = VerticalAlignment.Bottom,
                                horizontalAlignment = HorizontalAlignment.End,
                                onColorSelected = { color ->
                                    when (readUiEditType) {
                                        ReadUiEditType.ColorType_BACKGROUND ->
                                            viewModel.updateBgColorWithNonImage(color.toArgb())

                                        ReadUiEditType.ColorType_TEXT ->
                                            viewModel.updateTextColor(color.toArgb())

                                        else ->
                                            viewModel.updateBgColor(color.toArgb())
                                    }
                                    uiScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                }
                            )
                        } else {  //选择字体卡片
                            FontSelectionPanel(viewModel)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ReadBgSelectionDialog(context, {
            showDialog = false
        }) { type ->
            when (type) {
                1 -> {
                    viewModel.showReadBgList(true)
                }

                2 -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        if (PermissionHandler.hasPermissions(context)) {
                            imagePicker.launch("image/*")
                        } else {
                            PermissionHandler.requestPermissions(permissionLauncher)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

private data class SystemFontItem(
    val name: String,
    val displayNameRes: Int,
)

@Composable
private fun FontSelectionPanel(viewModel: MainReadViewModel) {
    val navController = LocalNavController.current
    val readerPreferences by viewModel.readerPreferences.collectAsStateWithLifecycle()
    val downloadedFonts by viewModel.downloadedFonts.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    var expandedFontIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var previousDownloadedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showImportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(downloadedFonts) {
        val currentIds = downloadedFonts.map { it.catalogItem.id }.toSet()
        if (previousDownloadedIds != currentIds) {
            val newIds = currentIds - previousDownloadedIds
            if (newIds.isNotEmpty()) {
                expandedFontIds = expandedFontIds + newIds
            }
            expandedFontIds = expandedFontIds.intersect(currentIds)
            previousDownloadedIds = currentIds
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.importFontFile(it) }
    }

    val importDirLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        uri?.let { viewModel.importFontDirectory(it) }
    }

    val systemFonts = remember {
        listOf(
            SystemFontItem("serif", R.string.font_serif),
            SystemFontItem("sans_serif", R.string.font_sans_serif),
            SystemFontItem("monospace", R.string.font_monospace),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SectionHeader(title = stringResource(R.string.system_fonts))
            }

            items(
                items = systemFonts,
                key = { it.name }
            ) { font ->
                SystemFontCard(
                    displayName = stringResource(font.displayNameRes),
                    isSelected = readerPreferences.font == font.name,
                    onSelect = { viewModel.selectSystemFont(font.name) }
                )
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionHeader(title = stringResource(R.string.loaded_font))
                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            if (!isImporting) showImportDialog = true
                        },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.load),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (downloadedFonts.isNotEmpty()) {
                items(
                    items = downloadedFonts,
                    key = { it.catalogItem.id }
                ) { fontItem ->
                    DownloadedFontCard(
                        fontItem = fontItem,
                        isExpanded = fontItem.catalogItem.id in expandedFontIds,
                        isSelected = readerPreferences.font == fontItem.localDir,
                        currentVariant = if (readerPreferences.font == fontItem.localDir) readerPreferences.fontVariant else null,
                        onSelect = { variant ->
                            fontItem.localDir?.let { dir ->
                                viewModel.selectDownloadedFont(dir, variant)
                            }
                        },
                        onToggleExpand = {
                            expandedFontIds = if (fontItem.catalogItem.id in expandedFontIds) {
                                expandedFontIds - fontItem.catalogItem.id
                            } else {
                                expandedFontIds + fontItem.catalogItem.id
                            }
                        }
                    )
                }
            }
//            item {
//                Spacer(modifier = Modifier.height(4.dp))
//                ListItem(
//                    headlineContent = {
//                        Text(stringResource(R.string.import_font))
//                    },
//                    trailingContent = {
//                        Icon(
//                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
//                            contentDescription = null
//                        )
//                    },
//                    colors = ListItemDefaults.colors(
//                        containerColor = ComposeColor.Transparent
//                    ),
//                    modifier = Modifier.clickable {
//                        if (!isImporting) showImportDialog = true
//                    }
//                )
//            }
//
//            item {
//                Spacer(modifier = Modifier.height(4.dp))
//                ListItem(
//                    headlineContent = {
//                        Text(stringResource(R.string.load_more_fonts))
//                    },
//                    trailingContent = {
//                        Icon(
//                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
//                            contentDescription = null
//                        )
//                    },
//                    colors = ListItemDefaults.colors(
//                        containerColor = ComposeColor.Transparent
//                    ),
//                    modifier = Modifier.clickable {
//                        navController.navigate(Screens.FontManagementScreen.route)
//                    }
//                )
//            }
        }

        if (showImportDialog) {
            FontImportModeDialog(
                onDismiss = { showImportDialog = false },
                onSelectFile = {
                    importFileLauncher.launch(arrayOf("*/*"))
                },
                onSelectDirectory = {
                    try {
                        importDirLauncher.launch(null)
                    } catch (_: ActivityNotFoundException) {
                        ToastUtil.show(R.string.no_file_manager_found)
                    }
                }
            )
        }

        if (isImporting) {
            LoadingPanel(text = stringResource(R.string.font_importing))
        }
    }
}

@Composable
private fun SystemFontCard(
    displayName: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.system_default),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun DownloadedFontCard(
    fontItem: FontListItem,
    isExpanded: Boolean,
    isSelected: Boolean,
    currentVariant: String?,
    onSelect: (String) -> Unit,
    onToggleExpand: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (fontItem.totalVariants > 1) {
                            onToggleExpand()
                        } else {
                            onSelect(fontItem.catalogItem.variants.firstOrNull()?.variant ?: "regular")
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fontItem.catalogItem.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (fontItem.source == "import") {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.import_source),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = fontItem.catalogItem.category,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = fontItem.catalogItem.language,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (fontItem.totalVariants > 1) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            if (fontItem.totalVariants > 1) {
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            fontItem.catalogItem.variants.forEach { variantItem ->
                                val variantSelected = currentVariant == variantItem.variant
                                FilterChip(
                                    selected = variantSelected,
                                    onClick = { onSelect(variantItem.variant) },
                                    label = { Text(variantItem.name) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ★ v11 per-book：首次开启"仅本书生效"时的淡入淡出提示 Overlay（仿 HomeScreen 的 FabGuideTooltip）。
 *
 * 用 Popup 浮层，居中显示在 BottomSheet 内容上方，点击或 3 秒后自动消失（markPerBookTipShown 关闭）。
 */
@Composable
private fun PerBookOverrideTipOverlay(onDismiss: () -> Unit) {
    var fading by remember { mutableStateOf(false) }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (fading) 0f else 1f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 300),
        label = "perBookTipFade"
    )
    LaunchedEffect(Unit) {
        delay(3000)
        fading = true
        delay(300)
        onDismiss()
    }
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        )
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer { this.alpha = alpha }
                .clickable {
                    fading = true
                }
        ) {
            Text(
                text = stringResource(R.string.per_book_first_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}