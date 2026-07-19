package com.wxn.reader.presentation.mainReader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.wxn.base.ext.toAndroidColor
import com.wxn.base.ext.toCompatibleArgb
import com.wxn.base.util.Logger
import com.wxn.base.util.ToastUtil
import com.wxn.base.util.launchIO
import com.wxn.bookread.ui.PageView
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.bookReader.BookReaderUiState
import com.wxn.reader.presentation.bookReader.components.BatteryOptimizationDialog
import com.wxn.reader.presentation.bookReader.components.NavigationLoadingOverlay
import com.wxn.reader.presentation.bookReader.components.ReaderGuideOverlay
import com.wxn.reader.presentation.bookReader.components.ReaderGuideOverlay2
import com.wxn.reader.presentation.bookReader.components.SearchFAB
import com.wxn.reader.presentation.bookReader.components.SearchResultsBottomSheet
import com.wxn.reader.presentation.bookReader.components.TextToolbar
import com.wxn.reader.presentation.bookReader.components.TimerExpiredLayer
import com.wxn.reader.presentation.bookReader.components.TtsPlayer
import com.wxn.reader.presentation.bookReader.components.TranslatePanel
import com.wxn.reader.presentation.bookReader.components.DictionaryPanel
import com.wxn.reader.presentation.bookReader.components.DictionaryPickerSheet
import com.wxn.reader.presentation.bookReader.components.TranslatePickerSheet
import com.wxn.reader.presentation.bookReader.components.dialogs.NoteContent
import com.wxn.reader.presentation.bookReader.components.dialogs.NoteDialog
import com.wxn.reader.presentation.bookReader.components.drawers.ReaderDrawer
import com.wxn.reader.presentation.bookReader.components.modals.ReaderSettings
import com.wxn.reader.presentation.bookReader.components.modals.ReaderUISettings
import com.wxn.reader.presentation.bookReader.components.modals.readbglist.ReadBgListPage
import com.wxn.reader.presentation.bookReader.components.toolbars.BottomToolbar
import com.wxn.reader.presentation.bookReader.components.toolbars.TopToolbar
import com.wxn.reader.presentation.shareQuoteCard.ShareQuoteCardDialog
import com.wxn.reader.util.TopPopupPositionProvider
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderView(
    viewModel: MainReadViewModel,
) {
    val navController = LocalNavController.current
    val book by viewModel.book.collectAsStateWithLifecycle()
    val areToolbarsVisible by viewModel.showMenu.collectAsStateWithLifecycle()
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val showReaderGuide by viewModel.showReaderGuide.collectAsStateWithLifecycle()
    val showTimerExpired by viewModel.showTimerExpired.collectAsStateWithLifecycle()
    val showClickAreaReaderGuide by viewModel.showClickAreaMode.collectAsStateWithLifecycle()
    val leftHandMode by viewModel.leftHandMode.collectAsStateWithLifecycle()
    val readerPreferences by viewModel.readerPreferences.collectAsStateWithLifecycle()

    val isDrawerOpen by viewModel.isDrawerOpen.collectAsStateWithLifecycle()

    val showTextToolbar by viewModel.showTextToolbar.collectAsStateWithLifecycle()
    val textToolbarRect by viewModel.textToolbarRect.collectAsStateWithLifecycle()

    val showColorSelectionPanel by viewModel.showColorSelectionPanel.collectAsStateWithLifecycle()

    val showReaderUISettings by viewModel.showReaderUISettings.collectAsStateWithLifecycle()
    val showReaderSettings by viewModel.showReaderSettings.collectAsStateWithLifecycle()
    val showNoteDialog by viewModel.showNoteDialog.collectAsStateWithLifecycle()
    val noteDialogSelectedText by viewModel.noteDialogSelectedText.collectAsStateWithLifecycle()
    val showReadBgList by viewModel.showReadBgList.collectAsStateWithLifecycle()

    val selectedNote by viewModel.selectedNote.collectAsStateWithLifecycle()

    val selectedAnnotation by viewModel.selectedAnnotation.collectAsStateWithLifecycle()

    val clickedLinkContent by viewModel.clickedLinkContent.collectAsStateWithLifecycle()

    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()

    val ttsPlayStatus by viewModel.ttsPlayStatus.collectAsStateWithLifecycle()
    val ttsSpeed by viewModel.ttsSpeed.collectAsStateWithLifecycle()
    val ttsPitch by viewModel.ttsPitch.collectAsStateWithLifecycle()
    val ttsLanguage by viewModel.ttsLanguage.collectAsStateWithLifecycle()
    val ttsPlayTimes by viewModel.ttsPlayTimes.collectAsStateWithLifecycle()

    val showBatteryDialog by viewModel.showBatteryOptimizationDialog.collectAsStateWithLifecycle()

    val outHref by viewModel.outHref.collectAsStateWithLifecycle()
    val showOutHrefDialog by viewModel.showOutHrefDialog.collectAsStateWithLifecycle()

    val showTranslatePanel by viewModel.showTranslatePanel.collectAsStateWithLifecycle()
    val translateSelectedText by viewModel.translateSelectedText.collectAsStateWithLifecycle()
    val translateTargetLang by viewModel.targetLang.collectAsStateWithLifecycle()
    val translatedTextContent by viewModel.translatedText.collectAsStateWithLifecycle()
    val supportedLanguages by viewModel.supportedLanguages.collectAsStateWithLifecycle()
    val translateStatus by viewModel.translateStatus.collectAsStateWithLifecycle()

    val showDictionaryPanel by viewModel.showDictionaryPanel.collectAsStateWithLifecycle()
    val dictionaryStatus by viewModel.dictionaryStatus.collectAsStateWithLifecycle()

    val showTranslatePicker by viewModel.showTranslatePicker.collectAsStateWithLifecycle()
    val translatorItems by viewModel.translatorItems.collectAsStateWithLifecycle()
    val translatorPrefs by viewModel.translatorPrefs.collectAsStateWithLifecycle()

    val showDictionaryPicker by viewModel.showDictionaryPicker.collectAsStateWithLifecycle()
    val dictionaryItems by viewModel.dictionaryItems.collectAsStateWithLifecycle()
    val dictionaryPrefs by viewModel.dictionaryPrefs.collectAsStateWithLifecycle()

    val searchSheetState by viewModel.searchSheetState.collectAsStateWithLifecycle()
    val searchProgress by viewModel.searchProgress.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val returnLocatorState by viewModel.returnLocator.collectAsStateWithLifecycle()
    val returnChapterName by viewModel.returnChapterName.collectAsStateWithLifecycle()
    val showSearchFabGuide by viewModel.showSearchFabGuide.collectAsStateWithLifecycle()
    val navigationLoading by viewModel.navigationLoading.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showQuoteDialog by rememberSaveable { mutableStateOf(false) }

    // 权限状态管理
    var showPermissionDeniedDialog = remember { mutableStateOf(false) }
    var waitingForPermission = remember { mutableStateOf(false) }
    var showPermissionExplanationDialog = remember { mutableStateOf(false) }

    // 权限请求 launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予，启动 TTS
            viewModel.toggleTts()
        } else {
            // 权限被拒绝，显示提示对话框
            showPermissionDeniedDialog.value = true
        }
    }

    // 注意：WRITE_EXTERNAL_STORAGE 权限申请已移至 ShareQuoteCardDialog 内部，
    // 仅在用户点"保存到相册"且 API<29 时按需申请。分享本身不需要任何存储权限。

    // 监听应用生命周期，处理从设置返回的情况
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 分享 chooser 关闭后重置 phase，避免按钮永久 disabled（S2）
                viewModel.resetPhaseIfSharing()
                if (waitingForPermission.value) {
                    // 从设置返回，检查权限状态
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            // 权限已授予，自动启动 TTS
                            viewModel.toggleTts()
                            waitingForPermission.value = false
                        } else {
                            // 权限仍未授予，重置标志
                            waitingForPermission.value = false
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 检查是否需要显示阅读引导页
    LaunchedEffect(Unit) {
        viewModel.checkAndShowReaderGuide()
    }

    fun navigateToHref(href: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(href))
            val chooser = Intent.createChooser(intent, context.getString(R.string.search_with)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: SecurityException) {
            ToastUtil.show(R.string.action_launch_failed)
        }
    }

    if (appPreferences != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (readerPreferences.scroll != 6) {
                AndroidView(
                    factory = { context ->
                        PageView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            viewModel.pageController.pageFactory =
                                TextPageFactory(this, viewModel.pageController)
                            this.dataProvider = viewModel.pageController
                            viewModel.pageController.callBack = this
                            viewModel.pageController.clickListener = viewModel
                            viewModel.pageController.navigationLoadingListener = object : PageViewController.OnNavigationLoadingListener {
                                override fun onNavigationLoadingStart(targetChapterIndex: Int, immediate: Boolean) {
                                    viewModel.viewModelScope.launch(Dispatchers.Main.immediate) {
                                        viewModel.handleNavigationLoadingStart(targetChapterIndex, immediate)
                                    }
                                }

                                override fun onNavigationLoadingComplete(chapterIndex: Int) {
                                    viewModel.viewModelScope.launch(Dispatchers.Main.immediate) {
                                        viewModel.handleNavigationLoadingComplete(chapterIndex)
                                    }
                                }

                                override fun onNavigationLoadingError(chapterIndex: Int) {
                                    viewModel.viewModelScope.launch(Dispatchers.Main.immediate) {
                                        viewModel.handleNavigationLoadingError(chapterIndex)
                                    }
                                }
                            }
                            setSelectTextCallback(viewModel.pageController)

                            // 修复：从连续垂直滚动(scroll==6)切换到其他翻页模式时，新创建的 PageView
                            // 需要主动触发内容显示。factory 依赖 onSizeChanged 异步触发 loadContent，
                            // 但时序竞态可能导致内容未及时显示（ContentTextView.drawPage 中 pageFactory 为 null 时不绘制）。
                            // 此时 curTextChapter 仍然存在（onDispose 未清空），upContent 可立即显示内容。
                            // 注意：curTextChapter 可能是 scroll 模式下创建的（offsetY 不同），
                            // 紧接着的 loadContent 会用正确的 isVScrollMode 重新分页并覆盖。
                            if (viewModel.pageController.isInitFinish
                                && viewModel.pageController.curTextChapter != null) {
                                upContent()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        Logger.d("ReaderView::update by AndroidView")

                        // 只在 book 对象改变时更新，避免每次重组都触发 loadContent
                        val currentBook = view.dataProvider?.book
                        if (currentBook != book) {
                            Logger.d("ReaderView::book changed, updating view")
                            view.dataProvider?.book = book
                            view.upStyle()
                            view.upTipStyle()
                            view.upBg()
                            view.upStatusBar()
                        }
                    }
                )
            } else {
                ContinuousScrollReaderView(viewModel)
            }

            val curChapterName by viewModel.curChapterName.collectAsStateWithLifecycle()

            NavigationLoadingOverlay(visible = navigationLoading.isLoading)

            AnimatedVisibility(
                visible = areToolbarsVisible,
                enter = slideInVertically(initialOffsetY = { -it }),
                exit = slideOutVertically(targetOffsetY = { -it })
            ) {
                TopToolbar(
                    isBookmarked = isBookmarked,
                    navController = navController,
                    book = book,
                    bookTitle = book?.title,
                    currentChapter = curChapterName,
                    bookmark = {
                        if (isBookmarked) {
                            viewModel.deleteBookmark()
                        } else {
                            viewModel.addBookmark()
                        }
                    },
                )
            }

            TtsPlayer(
                viewModel = viewModel,
                areToolbarsVisible = areToolbarsVisible,
                ttsPlayStatus = ttsPlayStatus,
                speed = ttsSpeed,
                pitch = ttsPitch,
                playTimes = ttsPlayTimes,
                language = ttsLanguage,
                onPlay = {
                    viewModel.resumeTtsPlaying()
                },
                onPause = {
                    viewModel.pauseTtsPlaying()
                },
                onEnd = {
                    viewModel.stopTts()
                },
                onSpeedChange = { viewModel.setTtsSpeed(it) },
                onPitchChange = { viewModel.setTtsPitch(it) },
                onLanguageChange = { viewModel.setTtsLanguage(it) },
                onSpeakerChange = { viewModel.setTtsSpeakerIndex(it) },
                onPlayTimeChange = { viewModel.setTtsPlayTime(it) },
                onSkipToNextUtterance = { viewModel.skipToNextUtterance() },
                onSkipToPreviousUtterance = { viewModel.skipToPreviousUtterance() }
            )
            // ActionModeLayout
            if (isDrawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            viewModel.showReaderDrawer(false)
                            viewModel.showColorSelectionPanel(false)
                        }
                )
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                AnimatedVisibility(
                    visible = !showReaderUISettings && !showReaderSettings,
                ) {
                    BottomToolbar(
                        textPageFactory = viewModel.pageController.pageFactory,
                        showToolbar = areToolbarsVisible,
                        viewModel = viewModel,
                        onToggleAppearanceSettings = { viewModel.readerUISettingsOpen() },
                        onToggleReaderSettings = { viewModel.readerSettingsOpen() },
                        textToSpeech = {
                            // Android 13+ 需要通知权限
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                // 检查权限状态
                                when (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )) {
                                    PackageManager.PERMISSION_GRANTED -> {
                                        // 已授予，直接启动 TTS
                                        viewModel.toggleTts()
                                    }

                                    else -> {
                                        // 未授予，先显示说明对话框
                                        showPermissionExplanationDialog.value = true
                                    }
                                }
                            } else {
                                // Android 12 及以下，不需要该权限，直接启动
                                viewModel.toggleTts()
                            }
                        }
                    )
                }
            }

            ReaderDrawer(
                viewModel = viewModel,
                isOpen = isDrawerOpen,

                onClose = { viewModel.showReaderDrawer(false) },
                onNoteClick = { note ->
                    // Handle note click, e.g., navigate to the note's location in the book
                    viewModel.showReaderDrawer(false)
                    viewModel.viewModelScope.launchIO {
                        viewModel.navigateTo(note.locatorInfo)
                    }
                },
                onUpdateNote = { updatedNote ->
                    viewModel.updateNote(updatedNote)
                },
                onRemoveNote = { note ->
                    viewModel.deleteNote(note)
                },
                onChapterSelect = { selectedChapter ->
                    viewModel.showReaderDrawer(false)
                    viewModel.viewModelScope.launchIO {
                        viewModel.onChapterClick(selectedChapter)
                    }
                },
                onBookmarkClick = { bookmark ->
                    viewModel.showReaderDrawer(false)
                    viewModel.viewModelScope.launchIO {
                        viewModel.navigateTo(bookmark.locatorInfo)
                    }
                },
                onRemoveBookmark = { bookmark ->
                    viewModel.deleteBookmark(bookmark)
                },
                onRemoveAnnotation = viewModel::deleteAnnotation,
                onUpdateAnnotation = viewModel::updateAnnotation,
                onClickAnnotation = { annotation ->
                    viewModel.showReaderDrawer(false)
                    viewModel.viewModelScope.launchIO {
                        viewModel.navigateTo(annotation.locatorInfo)
                    }
                },
            )
            if (showNoteDialog) {
                NoteDialog(
                    appPreferences = appPreferences!!,
                    selectedText = noteDialogSelectedText,
                    onSave = { noteText, selectedColor ->
                        viewModel.viewModelScope.launch {
                            viewModel.addNote(noteText, selectedColor)
                        }
                        viewModel.noteDialogOpen(false)
                    },
                    onDismiss = {
                        viewModel.noteDialogOpen(false)
                        viewModel.cancelTextSelected()
                    },
                    showPremiumModal = {
                        viewModel.noteDialogOpen(false)
                        navController.navigate(Screens.PremiumScreen.route)
                    }
                )
            }

            //选中的笔记
            selectedNote?.let { note ->
                NoteContent(
                    note = note,
                    onDismiss = {
                        viewModel.clearSelectedNote()
                        viewModel.cancelTextSelected()
                    },
                    onEdit = { editedNote ->
                        viewModel.updateNote(editedNote)
                        viewModel.clearSelectedNote()
                        viewModel.cancelTextSelected()
                    },
                    onDelete = { noteToDelete ->
                        viewModel.deleteNote(noteToDelete)
                        viewModel.clearSelectedNote()
                        viewModel.cancelTextSelected()
                    },

                )
            }

            if (showReaderUISettings) {
                ReaderUISettings(
                    appPreferences = appPreferences!!,
                    viewModel = viewModel,
                    readerPreferences = readerPreferences,
                    onDismiss = { viewModel.readerUISettingsOpen(false) }
                )
            }

            //阅读设置
            if (showReaderSettings) {
                ReaderSettings(
                    viewModel = viewModel,
                    readerPreferences = readerPreferences,
                    onDismiss = { viewModel.readerSettingsOpen(false) }
                )
            }

            if (showReadBgList) {
                ReadBgListPage(viewModel) {
                    viewModel.showReadBgList(false)
                }
            }

            var dp16 = remember { 0f }
            with(LocalDensity.current) {
                dp16 = 16.dp.toPx()
            }

            if (clickedLinkContent != null) { //点击的链接内容popup
                Popup(
                    popupPositionProvider = TopPopupPositionProvider(
                        Alignment.TopStart,
                        IntOffset(0, dp16.toInt()),
                        anchor = IntOffset(
                            clickedLinkContent?.clickX?.toInt() ?: 0,
                            clickedLinkContent?.clickY?.toInt() ?: 0
                        )
                    ),
                    onDismissRequest = {
                        viewModel.clearClickedLinkContent()
                    }
                ) {

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 32.dp)
                            .fillMaxWidth()

                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.clearClickedLinkContent()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 4.dp, end = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Popup",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(
                            Modifier
                                .padding(8.dp, 36.dp, 8.dp, 8.dp)
                                .heightIn(60.dp, 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = clickedLinkContent?.content.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (showTextToolbar) {
            TextToolbar(
                navController = navController,
                viewModel = viewModel,
                selectedText = viewModel.selectedTextForSearch, // actionSelectedText,
                rect = textToolbarRect,
                onHighlight = { color ->    //高亮
                    viewModel.handleHighlight(color)
                },
                onUnderline = { color ->    //下划线
                    viewModel.handleUnderline(color)
                },
                onNote = {                  //新增笔记
                    viewModel.handleNote()
                    viewModel.textToolbarOpen(false)
                    //                showTextToolbar = false
                },
                onDismiss = {
                    viewModel.textToolbarOpen(false)
                    viewModel.cancelTextSelected()
                },
                onTranslatePanel = {
                    viewModel.onTranslateClicked()
                },
                onSearch = {
                    val text = viewModel.selectedTextForSearch
                    if (text != null) {
                        viewModel.startSearch(text)
                    }
                },
                onShare = {
                    // 分享本身不需要任何存储权限（FileProvider URI + ACTION_SEND）
                    // 保存到相册的 WRITE_EXTERNAL_STORAGE 权限申请延迟到 Dialog 内"保存到相册"按钮
                    viewModel.snapshotQuoteData()
                    showQuoteDialog = true
                },
                appPreferences = appPreferences!!,
                selectedAnnotation = selectedAnnotation,
                onRemoveAnnotation = {
                    viewModel.deleteAnnotation(it)
                },
                colorHistory = readerPreferences.colorHistory.map { it ->
                    Color(it.toCompatibleArgb())
                },
                onColorHistoryUpdated = { newHistory ->
                    viewModel.updateColorHistory(newHistory.mapNotNull { it -> it.toAndroidColor() }
                    )
                },
                showColorSelectionPanel = showColorSelectionPanel
            )
        }

        // 书摘分享弹窗：Surface 叠加 + 渐显渐隐动画（X5 BackHandler 放外层，覆盖 exit 动画期间）
        var isQuoteDialogAnimatingExit by remember { mutableStateOf(false) }
        BackHandler(enabled = showQuoteDialog || isQuoteDialogAnimatingExit) {
            if (showQuoteDialog) {
                showQuoteDialog = false
                isQuoteDialogAnimatingExit = true
            }
        }
        // 资源清理延迟到 exit 动画结束后（S1：动画期间提前清理会导致 bitmap 变白）。
        // 仅在退出动画期间（isQuoteDialogAnimatingExit=true）执行，避免初始组合/旋转重建时误触发清理。
        LaunchedEffect(showQuoteDialog, isQuoteDialogAnimatingExit) {
            if (!showQuoteDialog && isQuoteDialogAnimatingExit) {
                delay(280)  // ≥ exit 动画 250ms + 余量
                viewModel.textToolbarOpen(false)
                viewModel.clearQuoteCardBitmaps()
                isQuoteDialogAnimatingExit = false
            }
        }
        AnimatedVisibility(
            visible = showQuoteDialog,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250))
        ) {
            MaterialTheme {
                ShareQuoteCardDialog(
                    viewModel = viewModel,
                    onDismiss = { showQuoteDialog = false },
                    fontFamily = readerPreferences.font.let { font ->
                        readerPreferences.fontVariant.let { variant ->
                            com.wxn.reader.util.ShareQuoteCardUtil.resolveTypeface(
                                context, font, variant
                            ).let { tf ->
                                androidx.compose.ui.text.font.FontFamily(tf)
                            }
                        }
                    }
                )
            }
        }

        if (showTranslatePanel) {
            DisposableEffect(Unit) {
                onDispose {
                    // 兜底所有离开组合路径：关闭、翻页、退出阅读器、旋转屏、跨面板互关
                    viewModel.cancelTranslateRequest()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (translatedTextContent != null || translateStatus == MainReadViewModel.TranslateStatus.ERROR) {
                            viewModel.hideTranslatePanel()
                            viewModel.cancelTextSelected()
                        } else {
                            viewModel.hideTranslatePanelAndShowToolbar()
                        }
                    }
            )
            TranslatePanel(
                viewModel = viewModel,
                rect = textToolbarRect,
                selectedText = translateSelectedText,
                targetLang = translateTargetLang,
                translatedText = translatedTextContent,
                supportedLanguages = supportedLanguages,
                onTargetLangChange = { viewModel.updateTargetLang(it) },
                onTranslate = { viewModel.translate() }
            )
        }

        if (showTranslatePicker) {
            TranslatePickerSheet(
                items = translatorItems,
                currentSelectedId = translatorPrefs.lastSelectedTranslator,
                isSetAsDefault = translatorPrefs.lastSelectedTranslator.isNotBlank(),
                onConfirm = { translatorId, setAsDefault ->
                    viewModel.onTranslatorConfirmed(translatorId, setAsDefault)
                },
                onDismiss = {
                    viewModel.hideTranslatePicker()
                }
            )
        }

        if (showDictionaryPanel) {
            DisposableEffect(Unit) {
                onDispose {
                    // 兜底所有离开组合路径：关闭、翻页、退出阅读器、旋转屏、跨面板互关
                    viewModel.cancelDictionaryRequest()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (dictionaryStatus == MainReadViewModel.DictionaryStatus.SUCCESS
                            || dictionaryStatus == MainReadViewModel.DictionaryStatus.NOT_FOUND
                            || dictionaryStatus == MainReadViewModel.DictionaryStatus.ERROR
                        ) {
                            viewModel.hideDictionaryPanel()
                        } else {
                            viewModel.hideDictionaryPanelAndShowToolbar()
                        }
                    }
            )
            DictionaryPanel(
                viewModel = viewModel,
                rect = textToolbarRect,
                onDismiss = { viewModel.hideDictionaryPanel() }
            )
        }

        if (showDictionaryPicker) {
            DictionaryPickerSheet(
                items = dictionaryItems,
                currentSelectedId = dictionaryPrefs.defaultLookupApp,
                isSetAsDefault = dictionaryPrefs.defaultLookupApp.isNotBlank(),
                onConfirm = { dictId, setAsDefault ->
                    viewModel.onDictionaryPickerConfirmed(dictId, setAsDefault)
                },
                onDismiss = {
                    viewModel.hideDictionaryPicker()
                }
            )
        }

        if (searchSheetState == SearchSheetState.EXPANDED) {
            SearchResultsBottomSheet(
                query = searchQuery,
                progress = searchProgress,
                returnLocator = returnLocatorState,
                returnChapterName = returnChapterName,
                onResultClick = { viewModel.navigateToSearchResult(it) },
                onReturnClick = { viewModel.navigateBackToReturnLocator() },
                onClose = { viewModel.closeSearchSheet() },
                onMinimize = { viewModel.minimizeSearchSheet() },
            )
        }

        if (showOutHrefDialog) {
            AlertDialog(
                onDismissRequest = {
                    viewModel.hideOutHrefDialog()

                },
                title = { Text("") },
                text = { Text(stringResource(R.string.dialog_content_to_out_href, outHref)) },
                dismissButton = {
                    Button(
                        onClick = {
                            viewModel.hideOutHrefDialog()
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        onClick = {
                            navigateToHref(outHref)
                            viewModel.hideOutHrefDialog()
                        }
                    ) {
                        Text(stringResource(R.string.navigate_to))
                    }
                },
            )
        }

        // 权限说明对话框
        if (showPermissionExplanationDialog.value) {
            AlertDialog(
                onDismissRequest = {
                    showPermissionExplanationDialog.value = false
                },
                title = {
                    Text(stringResource(R.string.notification_permission_explanation_title))
                },
                text = {
                    Text(stringResource(R.string.notification_permission_explanation_message))
                },
                confirmButton = {
                    Button(onClick = {
                        showPermissionExplanationDialog.value = false
                        // 用户确认后再请求权限
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text(stringResource(R.string.notification_permission_continue))
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        showPermissionExplanationDialog.value = false
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // 电池优化对话框
        if (showBatteryDialog) {
            BatteryOptimizationDialog(
                onDismiss = { viewModel.dismissBatteryOptimizationDialog() },
                onConfirm = { viewModel.onBatteryOptimizationConfirm() },
                onSkip = { viewModel.onBatteryOptimizationSkip() },
                onNeverShowAgain = { viewModel.onBatteryOptimizationNeverShowAgain() }
            )
        }

        if (searchSheetState == SearchSheetState.MINIMIZED) {
            val fabBottomPadding by animateDpAsState(
                targetValue = if (areToolbarsVisible) 138.dp else 16.dp,
                animationSpec = tween(durationMillis = 300),
                label = "fabBottomPadding"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 16.dp, bottom = fabBottomPadding),
                contentAlignment = Alignment.BottomEnd,
            ) {
                SearchFAB(
                    resultCount = searchProgress.results.size,
                    isSearching = !searchProgress.isComplete,
                    onClick = { viewModel.expandSearchSheet() },
                    onLongClick = { viewModel.closeSearchSheet() },
                    showHighlight = showSearchFabGuide,
                )

                if (showSearchFabGuide) {
                    SearchFabGuideTooltip(
                        onDismiss = { viewModel.markSearchFabGuideShown() }
                    )
                }
            }
        }

        // TTS播放计时器完成显示提示窗体
        if (showTimerExpired) {
            TimerExpiredLayer(
                onDismiss = { viewModel.markTimerLayerDismiss() }
            )
        }

        // 权限被拒绝提示对话框
        if (showPermissionDeniedDialog.value) {
            AlertDialog(
                onDismissRequest = {
                    showPermissionDeniedDialog.value = false
                },
                title = {
                    Text(stringResource(R.string.notification_permission_required_title))
                },
                text = {
                    Text(stringResource(R.string.notification_permission_required_message))
                },
                confirmButton = {
                    Button(onClick = {
                        showPermissionDeniedDialog.value = false
                        waitingForPermission.value = true
                        try {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: SecurityException) {
                            ToastUtil.show(R.string.action_launch_failed)
                        }
                    }) {
                        Text(stringResource(R.string.notification_permission_go_settings))
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        showPermissionDeniedDialog.value = false
                    }) {
                        Text(stringResource(R.string.notification_permission_cancel))
                    }
                }
            )
        }

        // 阅读引导页覆盖层
        if (showReaderGuide && uiState is BookReaderUiState.LOAD_SUCCESS) {

            ReaderGuideOverlay(
                onDismiss = { viewModel.markReaderGuideShown() },
                isVScrollMode = readerPreferences.scroll == 6
            )
        }
        //show when toggling
        when (showClickAreaReaderGuide) {
            0 -> ReaderGuideOverlay(
                leftHandMode = leftHandMode,
                isVScrollMode = readerPreferences.scroll == 6,
                onDismiss = { viewModel.showClickAreaMode(-1, leftHandMode) }
            )

            1 -> ReaderGuideOverlay2(
                leftHandMode = leftHandMode,
                isVScrollMode = readerPreferences.scroll == 6,
                onDismiss = {
                    viewModel.showClickAreaMode(-1, leftHandMode)
                }
            )
        }
    }
}

@Composable
private fun SearchFabGuideTooltip(
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, with(LocalDensity.current) { (-100).dp.roundToPx() }),
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .width(260.dp)
                .wrapContentHeight()
                .padding(end = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.search_fab_guide_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.search_fab_guide_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}