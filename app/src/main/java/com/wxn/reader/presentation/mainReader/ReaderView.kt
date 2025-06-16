package com.wxn.reader.presentation.mainReader

import android.graphics.Rect
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wxn.base.ext.toAndroidColor
import com.wxn.base.ext.toCompatibleArgb
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.ui.PageView
import com.wxn.bookread.ui.TextPageFactory
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
import io.github.jan.supabase.realtime.Column
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
    val isTtsOn by viewModel.isTtsOn.collectAsStateWithLifecycle()
    val showTextToolbar by viewModel.showTextToolbar.collectAsStateWithLifecycle()
    val showColorSelectionPanel by viewModel.showColorSelectionPanel.collectAsStateWithLifecycle()

    val showUISettings by viewModel.showUISettings.collectAsStateWithLifecycle()
    val showFontSettings by viewModel.showFontSettings.collectAsStateWithLifecycle()
    val showPageSettings by viewModel.showPageSettings.collectAsStateWithLifecycle()
    val showReaderSettings by viewModel.showReaderSettings.collectAsStateWithLifecycle()
    val showNoteDialog by viewModel.showNoteDialog.collectAsStateWithLifecycle()
    val selectedNote by viewModel.selectedNote.collectAsStateWithLifecycle()

    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val annotations by viewModel.annotations.collectAsStateWithLifecycle()
    val selectedAnnotation by viewModel.selectedAnnotation.collectAsStateWithLifecycle()

    val clickedLinkContent by viewModel.clickedLinkContent.collectAsStateWithLifecycle()

    fun onPageChange(newPage: Double) {
        Logger.d("ReaderView::onPageChange:$newPage")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color(readerPreferences.backgroundColor))
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
                view.dataProvider?.book = book
                view.upStyle()
                view.upTipStyle()
                view.upBg()
                view.upStatusBar()
                view.dataProvider?.loadContent(true)
            }
        )

        AnimatedVisibility(
            visible = areToolbarsVisible,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it })
        ) {
            TopToolbar(
                isBookmarked = false, // isBookmarked,
                navController = navController,
                book = book,
                bookTitle = book?.title,
                currentChapter = viewModel.pageController.curTextChapter?.title.orEmpty(), //currentChapter,
                onChaptersClick = { viewModel.chaptersDrawerOpen() },
                onNotesDrawerToggle = { viewModel.notesDrawerOpen() },
                onBookmarkDrawerToggle = { viewModel.bookmarksDrawerOpen() },
                onHighlightsDrawerToggle = { viewModel.highlightsDrawerOpen() },
                bookmark = {
//                    currentLocator?.let { locator ->
//                        val existingBookmark =
//                            bookmarks.find { it.locator == locator.toJSON().toString() }
//                        if (existingBookmark != null) {
//                            viewModel.deleteBookmark(existingBookmark)
//                        } else {
//                            viewModel.addBookmark(locator)
//                        }
//                    }
                },
                textToSpeech = {
//                    viewModel.toggleTts(navigatorFragment, context)
                },
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
//                ttsNavigator?.play()
//                viewModel.setTtsPlaying(true)
//            },
//            onPause = {
//                ttsNavigator?.pause()
//                viewModel.setTtsPlaying(false)
//            },
//            onEnd = {
//                viewModel.toggleTts(navigatorFragment, context)
//            },
//            onSpeedChange = { viewModel.setTtsSpeed(it.toDouble()) },
//            onPitchChange = { viewModel.setTtsPitch(it.toDouble()) },
//            onLanguageChange = { viewModel.setTtsLanguage(it) },
//            onSkipToNextUtterance = { viewModel.skipToNextUtterance() },
//            onSkipToPreviousUtterance = { viewModel.skipToPreviousUtterance() }
//        )
        // ActionModeLayout
        if (showTextToolbar || isHighlightsDrawerOpen || isChaptersDrawerOpen) {
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
                        viewModel.textToolbarOpen(false)
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
                    progression = viewModel.pageController.progression,
                    onPageChange = ::onPageChange,
                    onToggleFontSettings = { viewModel.fontSettingsOpen() },
                    onTogglePageSettings = { viewModel.pageSettingsOpen() },
                    onToggleReaderSettings = { viewModel.readerSettingsOpen() },
                    onToggleUISettings = { viewModel.uiSettingsOpen() }
                )
            }
        }

        ChaptersDrawer2(
            isOpen = isChaptersDrawerOpen,
            currentChapter = viewModel.pageController.curTextChapter?.title.orEmpty(),
            tableOfContents = viewModel.showOutChapters,
            onChapterSelect = { selectedChapter ->
                viewModel.onLinkClick(selectedChapter.srcName, -1f, -1f)
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
            appPreferences = appPreferences,
            isOpen = isNotesDrawerOpen,
            onClose = { viewModel.notesDrawerOpen(false) },
            notes = notes,
            onNoteClick = { note ->
                // Handle note click, e.g., navigate to the note's location in the book
                viewModel.viewModelScope.launch {
//                    Locator.fromJSON(JSONObject(note.locator))?.let { navigatorFragment?.go(it) }
                    //TODO 跳转到对应位置
                    viewModel.notesDrawerOpen(false)
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
            appPreferences = appPreferences,
            isOpen = isBookmarksDrawerOpen,
            onClose = { viewModel.bookmarksDrawerOpen(false) },
            bookmarks = bookmarks,
            onBookmarkClick = { bookmark ->
                viewModel.viewModelScope.launch {
                    //TODO
//                    Locator.fromJSON(JSONObject(bookmark.locator))
//                        ?.let { navigatorFragment?.go(it) }
                    viewModel.bookmarksDrawerOpen(false)
                }
            },
            onRemoveBookmark = { bookmark ->
                viewModel.deleteBookmark(bookmark)
            }
        )

        AnnotationsDrawer(
            navController = navController,
            appPreferences = appPreferences,
//            navigator = navigatorFragment,
            annotations = annotations,
            onRemoveAnnotation = viewModel::deleteAnnotation,
            onUpdateAnnotation = viewModel::updateAnnotation,
            onClickAnnotation = {
                //TODO 跳转到注释位置
            },
            isOpen = isHighlightsDrawerOpen,
            onClose = { viewModel.highlightsDrawerOpen(false) }
        )

        if (showNoteDialog) {
            NoteDialog(
                appPreferences = appPreferences,
                selectedText = viewModel.pageController.getSelectedText(),  //noteDialogSelectedText,
                onSave = { noteText, selectedColor -> // Capture the selected color
                    viewModel.viewModelScope.launch { //TODO
//                        val selection = navigatorFragment?.currentSelection()
//                        if (selection != null) {
//                            val locator = selection.locator
//                            val bookId = viewModel.currentBookId.value ?: return@launch
//
//                            val newNote = Note(
//                                locator = locator.toJSON().toString(),
//                                selectedText = noteDialogSelectedText,
//                                note = noteText,
//                                color = selectedColor.toArgb().toString(), // Use selected color
//                                bookId = bookId
//                            )
//                            viewModel.addNote(newNote)
//                        }
                    }
                    viewModel.noteDialogOpen(false)
                },
                onDismiss = { viewModel.noteDialogOpen(false) },
                showPremiumModal = {
                    viewModel.noteDialogOpen(false)
                    navController.navigate(Screens.PremiumScreen.route)
//                    viewModel.purchasePremium(purchaseHelper)
//                    showPremiumModal = true
                }
            )
        }

        selectedNote?.let { note ->
            NoteContent(
                appPreferences = appPreferences,
                note = note,
                onDismiss = { viewModel.clearSelectedNote() },
                onEdit = { editedNote ->
                    viewModel.updateNote(editedNote)
                    viewModel.clearSelectedNote()
                },
                onDelete = { noteToDelete ->
                    viewModel.deleteNote(noteToDelete)
                    viewModel.clearSelectedNote()
                },
                showPremiumModal = {
                    viewModel.clearSelectedNote()
//                    viewModel.purchasePremium(purchaseHelper)
                    navController.navigate(Screens.PremiumScreen.route)
                }
            )
        }

        if (showFontSettings) {
            FontSettings(
                viewModel = viewModel,
                readerPreferences = readerPreferences,
                onDismiss = { viewModel.fontSettingsOpen(false) },
            )
        }

        if (showPageSettings) {
            PageSettings(
                viewModel = viewModel,
                readerPreferences = readerPreferences,
                onDismiss = { viewModel.pageSettingsOpen(false) },
            )
        }

        if (showUISettings) {
            UiSettings(
                navController = navController,
//                purchaseHelper = purchaseHelper,
                appPreferences = appPreferences,
                viewModel = viewModel,
                readerPreferences = readerPreferences,
                onDismiss = { viewModel.uiSettingsOpen(false) }
            )
        }

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

    var textToolbarRect by remember { mutableStateOf<Rect?>(null) }

    if (showTextToolbar) {
        TextToolbar(
            navController = navController,
            viewModel = viewModel,
            selectedText = "", // actionSelectedText,
            rect = textToolbarRect!!,
            onHighlight = { color ->
//                handleHighlight(color)
            },
            onUnderline = { color ->
//                handleUnderline(color)
            },
            onNote = {
//                handleNote()
                viewModel.textToolbarOpen(false)
//                showTextToolbar = false
            },
            onDismiss = { viewModel.textToolbarOpen(false) },
            appPreferences = appPreferences,
            selectedAnnotation = selectedAnnotation,
            onRemoveAnnotation = {
                viewModel.deleteAnnotation(it)
            },
            colorHistory = readerPreferences.colorHistory.map { it ->
                Color(it.toCompatibleArgb())
            },
            onColorHistoryUpdated = { newHistory ->
                viewModel.updateReaderPreferences(readerPreferences.copy(colorHistory = newHistory.mapNotNull { it ->
                    it.toAndroidColor()
                }))
            },
            showColorSelectionPanel = showColorSelectionPanel
        )
    }


}