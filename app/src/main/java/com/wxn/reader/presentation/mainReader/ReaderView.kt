package com.wxn.reader.presentation.mainReader

import android.content.Intent
import android.net.Uri
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wxn.base.ext.toAndroidColor
import com.wxn.base.ext.toCompatibleArgb
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.ui.PageView
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.bookReader.components.TextToolbar
import com.wxn.reader.presentation.bookReader.components.dialogs.NoteContent
import com.wxn.reader.presentation.bookReader.components.dialogs.NoteDialog
import com.wxn.reader.presentation.bookReader.components.drawers.AnnotationsDrawer
import com.wxn.reader.presentation.bookReader.components.drawers.BookmarksDrawer
import com.wxn.reader.presentation.bookReader.components.drawers.ChaptersDrawer2
import com.wxn.reader.presentation.bookReader.components.drawers.NotesDrawer
import com.wxn.reader.presentation.bookReader.components.modals.FontSettings
import com.wxn.reader.presentation.bookReader.components.modals.PageSettings
import com.wxn.reader.presentation.bookReader.components.modals.ReaderSettings
import com.wxn.reader.presentation.bookReader.components.modals.UiSettings
import com.wxn.reader.presentation.bookReader.components.toolbars.BottomToolbar
import com.wxn.reader.presentation.bookReader.components.toolbars.TopToolbar
import com.wxn.reader.util.LogCompositions
import com.wxn.reader.util.TopPopupPositionProvider
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderView(
    readerPreferences: ReaderPreferences,
    viewModel: MainReadViewModel,
) {
    LogCompositions("Composition:ReaderView")
    val navController = LocalNavController.current
    val book by viewModel.book.collectAsStateWithLifecycle()
    val areToolbarsVisible by viewModel.showMenu.collectAsStateWithLifecycle()
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()

    val isChaptersDrawerOpen by viewModel.isChaptersDrawerOpen.collectAsStateWithLifecycle()
    val isNotesDrawerOpen by viewModel.isNotesDrawerOpen.collectAsStateWithLifecycle()
    val isBookmarksDrawerOpen by viewModel.isBookmarksDrawerOpen.collectAsStateWithLifecycle()
    val isHighlightsDrawerOpen by viewModel.isHighlightsDrawerOpen.collectAsStateWithLifecycle()

    val showTextToolbar by viewModel.showTextToolbar.collectAsStateWithLifecycle()
    val textToolbarRect by viewModel.textToolbarRect.collectAsStateWithLifecycle()

    val showColorSelectionPanel by viewModel.showColorSelectionPanel.collectAsStateWithLifecycle()

    val showUISettings by viewModel.showUISettings.collectAsStateWithLifecycle()
    val showFontSettings by viewModel.showFontSettings.collectAsStateWithLifecycle()
    val showPageSettings by viewModel.showPageSettings.collectAsStateWithLifecycle()
    val showReaderSettings by viewModel.showReaderSettings.collectAsStateWithLifecycle()
    val showNoteDialog by viewModel.showNoteDialog.collectAsStateWithLifecycle()
    val noteDialogSelectedText by viewModel.noteDialogSelectedText.collectAsStateWithLifecycle()

    val selectedNote by viewModel.selectedNote.collectAsStateWithLifecycle()

    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val selectedAnnotation by viewModel.selectedAnnotation.collectAsStateWithLifecycle()

    val clickedLinkContent by viewModel.clickedLinkContent.collectAsStateWithLifecycle()

    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()

    val isTtsOn by viewModel.isTtsOn.collectAsStateWithLifecycle()
    val enableTts by viewModel.enableTts.collectAsStateWithLifecycle()
//    val isTtsPlaying by viewModel.isTtsPlaying.collectAsStateWithLifecycle()
//    val ttsSpeed by viewModel.ttsSpeed.collectAsStateWithLifecycle()
//    val ttsPitch by viewModel.ttsPitch.collectAsStateWithLifecycle()
//    val ttsLanguage by viewModel.ttsLanguage.collectAsStateWithLifecycle()

    val outHref by viewModel.outHref.collectAsStateWithLifecycle()
    val showOutHrefDialog by viewModel.showOutHrefDialog.collectAsStateWithLifecycle()

    val context = LocalContext.current

    fun navigateToHref(href: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(href))
        context.startActivity(Intent.createChooser(intent, "Search with"))
    }

    if (appPreferences != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AndroidView(
                factory = { context ->
                    PageView(context).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                        viewModel.pageController.pageFactory = TextPageFactory(this, viewModel.pageController)
                        this.dataProvider = viewModel.pageController
                        viewModel.pageController.callBack = this
                        viewModel.pageController.clickListener = viewModel
                        setSelectTextCallback(viewModel.pageController)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    Logger.d("ReaderView::update by AndroidView")
                    view.dataProvider?.book = book
                    view.upStyle()
                    view.upTipStyle()
                    view.upBg()
                    view.upStatusBar()
                    view.dataProvider?.loadContent(true)
                }
            )

            val curChapterName by viewModel.curChapterName.collectAsStateWithLifecycle()

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
                    onChaptersClick = { viewModel.chaptersDrawerOpen() },
                    onNotesDrawerToggle = { viewModel.notesDrawerOpen() },
                    onBookmarkDrawerToggle = { viewModel.bookmarksDrawerOpen() },
                    onHighlightsDrawerToggle = { viewModel.highlightsDrawerOpen() },
                    bookmark = {
                        if (isBookmarked) {
                            viewModel.deleteBookmark()
                        } else {
                            viewModel.addBookmark()
                        }
                    },
                    textToSpeech = {
                        viewModel.toggleTts()
                    },
                    enableTts = enableTts,
                    isTtsOn = isTtsOn,
                )
            }

            LaunchedEffect(isTtsOn) {
                if (isTtsOn) {
                    viewModel.onToolbarsVisibilityChanged()
                }
            }

    //        TtsPlayer(
    //            areToolbarsVisible = areToolbarsVisible,
    //            isTtsOn = isTtsOn,
    //            isTtsPlaying = isTtsPlaying,
    //            speed = ttsSpeed,
    //            pitch = ttsPitch,
    //            language = ttsLanguage,
    //            onPlay = {
    //                viewModel.setTtsPlaying(true)
    //            },
    //            onPause = {
    //                viewModel.setTtsPlaying(false)
    //            },
    //            onEnd = {
    //                viewModel.toggleTts()
    //            },
    //            onSpeedChange = { viewModel.setTtsSpeed(it.toDouble()) },
    //            onPitchChange = { viewModel.setTtsPitch(it.toDouble()) },
    //            onLanguageChange = { viewModel.setTtsLanguage(it) },
    //            onSkipToNextUtterance = { viewModel.skipToNextUtterance() },
    //            onSkipToPreviousUtterance = { viewModel.skipToPreviousUtterance() }
    //        )
            // ActionModeLayout
            if (showTextToolbar || isHighlightsDrawerOpen || isChaptersDrawerOpen || isNotesDrawerOpen || isBookmarksDrawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {

                            viewModel.notesDrawerOpen(false)
                            viewModel.bookmarksDrawerOpen(false)
                            viewModel.highlightsDrawerOpen(false)
                            viewModel.chaptersDrawerOpen(false)

                            if (showTextToolbar) {
                                viewModel.textToolbarOpen(false)
                                viewModel.cancelTextSelected()
                            }
                            viewModel.showColorSelectionPanel(false)
                        }
                )
            }

            Column(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                AnimatedVisibility(
                    visible = !showUISettings && !showFontSettings && !showPageSettings && !showReaderSettings,
                ) {
                    BottomToolbar(
                        textPageFactory = viewModel.pageController.pageFactory,
                        showToolbar = areToolbarsVisible,
                        viewModel = viewModel,
                        onToggleFontSettings = { viewModel.fontSettingsOpen() },
                        onTogglePageSettings = { viewModel.pageSettingsOpen() },
                        onToggleReaderSettings = { viewModel.readerSettingsOpen() },
                        onToggleUISettings = { viewModel.uiSettingsOpen() }
                    )
                }
            }

            ChaptersDrawer2(
                isOpen = isChaptersDrawerOpen,
                viewModel = viewModel,
                tableOfContents = viewModel.showOutChapters,
                onChapterSelect = { selectedChapter ->
                    viewModel.onChapterClick(selectedChapter)
                    viewModel.chaptersDrawerOpen(false)
    //                val locator = publication.locatorFromLink(selectedChapter)
    //                locator?.let {
    //                    onChapterChange(it)
    //                    isChaptersDrawerOpen = false
    //                }
                },
                onClose = { viewModel.chaptersDrawerOpen(false) }
            )

            NotesDrawer(
                navController = navController,
                appPreferences = appPreferences!!,
                isOpen = isNotesDrawerOpen,
                onClose = { viewModel.notesDrawerOpen(false) },
                notes = notes,
                onNoteClick = { note ->
                    // Handle note click, e.g., navigate to the note's location in the book
                    viewModel.viewModelScope.launch {
                        viewModel.notesDrawerOpen(false)
                        viewModel.navigateTo(note.locatorInfo)
                    }
                },
                onUpdateNote = { updatedNote ->
                    viewModel.updateNote(updatedNote)
                },
                onRemoveNote = { note ->
                    viewModel.deleteNote(note)
                }
            )

            BookmarksDrawer(
                navController = navController,
                appPreferences = appPreferences!!,
                isOpen = isBookmarksDrawerOpen,
                onClose = { viewModel.bookmarksDrawerOpen(false) },
                bookmarks = bookmarks,
                onBookmarkClick = { bookmark ->
                    viewModel.viewModelScope.launch {
                        viewModel.bookmarksDrawerOpen(false)
                        viewModel.navigateTo(bookmark.locatorInfo)
                    }
                },
                onRemoveBookmark = { bookmark ->
                    viewModel.deleteBookmark(bookmark)
                }
            )

            AnnotationsDrawer(
                navController = navController,
                appPreferences = appPreferences!!,
                annotations = annotations,
                onRemoveAnnotation = viewModel::deleteAnnotation,
                onUpdateAnnotation = viewModel::updateAnnotation,
                onClickAnnotation = { annotation ->
                    viewModel.navigateTo(annotation.locatorInfo)
                },
                isOpen = isHighlightsDrawerOpen,
                onClose = { viewModel.highlightsDrawerOpen(false) }
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
    //                    viewModel.purchasePremium(purchaseHelper)
    //                    showPremiumModal = true
                    }
                )
            }

            //选中的笔记
            selectedNote?.let { note ->
                NoteContent(
                    appPreferences = appPreferences!!,
                    note = note,
                    onDismiss = {
                        viewModel.clearSelectedNote()
                        viewModel.cancelTextSelected()
                    },
                    onEdit = { editedNote ->
                        viewModel.updateNote(editedNote)
                        viewModel.clearSelectedNote()
                    },
                    onDelete = { noteToDelete ->
                        viewModel.deleteNote(noteToDelete)
                        viewModel.clearSelectedNote()
                    },
                    showPremiumModal = {
    //                    viewModel.clearSelectedNote()
    //                    viewModel.purchasePremium(purchaseHelper)
    //                    navController.navigate(Screens.PremiumScreen.route)
                    }
                )
            }

            //字体设置
            if (showFontSettings) {
                FontSettings(
                    viewModel = viewModel,
                    readerPreferences = readerPreferences,
                    onDismiss = { viewModel.fontSettingsOpen(false) },
                )
            }

            //页面设置
            if (showPageSettings) {
                PageSettings(
                    viewModel = viewModel,
                    readerPreferences = readerPreferences,
                    onDismiss = { viewModel.pageSettingsOpen(false) },
                )
            }

            //UI 设置
            if (showUISettings) {
                UiSettings(
                    navController = navController,
    //                purchaseHelper = purchaseHelper,
                    appPreferences = appPreferences!!,
                    viewModel = viewModel,
                    readerPreferences = readerPreferences,
                    onDismiss = { viewModel.uiSettingsOpen(false) }
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

            var dp16 = remember { 0f }
            with(LocalDensity.current) {
                dp16 = 16.dp.toPx()
            }

            if (clickedLinkContent != null) { //点击的链接内容popup
                Popup(
                    popupPositionProvider = TopPopupPositionProvider(
                        Alignment.TopStart,
                        IntOffset(0, dp16.toInt()),
                        anchor = IntOffset(clickedLinkContent?.clickX?.toInt() ?: 0, clickedLinkContent?.clickY?.toInt() ?: 0)
                    ),
                    onDismissRequest = {
                        viewModel.clearClickedLinkContent()
                    }
                ) {

                    Box(
                        modifier = Modifier.padding(horizontal = 32.dp).fillMaxWidth().background(Color.Yellow)
                    ) {
                        IconButton(
                            onClick = {
                                viewModel.clearClickedLinkContent()
                            },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close Popup"
                            )
                        }
                        Column(Modifier.padding(8.dp, 36.dp, 8.dp, 8.dp)) {
                            Text(text = clickedLinkContent?.content.orEmpty(), fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        if (showTextToolbar) {
            TextToolbar(
                navController = navController,
                viewModel = viewModel,
                selectedText = "", // actionSelectedText,
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
                appPreferences = appPreferences!!,
                selectedAnnotation = selectedAnnotation,
                onRemoveAnnotation = {
                    viewModel.deleteAnnotation(it)
                },
                colorHistory = readerPreferences.colorHistory.map { it ->
                    Color(it.toCompatibleArgb())
                },
                onColorHistoryUpdated = { newHistory ->
    //                Logger.d("TextToolbar::onColorHistoryUpdated")
                    viewModel.updateReaderPreferences(readerPreferences.copy(colorHistory = newHistory.mapNotNull { it ->
                        it.toAndroidColor()
                    }), false)
                },
                showColorSelectionPanel = showColorSelectionPanel
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
    }
}