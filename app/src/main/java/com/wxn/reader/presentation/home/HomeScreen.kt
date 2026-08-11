package com.wxn.reader.presentation.home

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import com.wxn.base.util.ToastUtil
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.PurchaseHelperController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.navigation.buildReaderRoute
import com.wxn.reader.navigation.navigateToScreen
import com.wxn.reader.presentation.home.components.CustomSearchBar
import com.wxn.reader.presentation.home.components.DeleteConfirmDialog
import com.wxn.reader.presentation.home.components.SelectionFabMenu
import com.wxn.reader.presentation.home.components.ShelfPickerDialog
import com.wxn.reader.presentation.home.components.CustomTopAppBar
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar
import com.wxn.reader.presentation.home.components.HomeFloatingActionButton
import com.wxn.reader.presentation.home.components.ImportOverlay
import com.wxn.reader.presentation.home.overview.HomeOverviewPanel
import com.wxn.reader.presentation.home.states.ImportProgressState
import kotlinx.coroutines.delay
import com.wxn.reader.util.PurchaseHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val purchaseHelper: PurchaseHelper = PurchaseHelperController.current
    val navController = LocalNavController.current

    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val booksInShelf by viewModel.booksInShelfSet.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()

    val importProgress by viewModel.importProgressState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarEvent by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    // D8：ON_RESUME 接入前台检测（仅重置 reviewInFlight/committedThisShow）
    // K4/K5：触发2 已改为 onCleared→SharedFlow，ON_RESUME 不再评估条件2
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.onAppForeground()
    }
    val showReviewPrompt by viewModel.showReviewPrompt.collectAsStateWithLifecycle()

    LaunchedEffect(snackbarEvent) {
        snackbarEvent?.let { event ->
            snackbarHostState.showSnackbar(
                message = event.message,
                duration = event.duration
            )
            viewModel.clearSnackbarMessage()
        }
    }
    val showFabGuide by viewModel.showFabGuide.collectAsStateWithLifecycle()

    val reselectBookId by viewModel.reselectBookId.collectAsStateWithLifecycle()
    val reselectBookFileType by viewModel.reselectBookFileType.collectAsStateWithLifecycle()
    val reselectInProgress by viewModel.reselectInProgress.collectAsStateWithLifecycle()
    // ★ P1-1:orphan 文件不匹配弹窗
    val orphanMismatch by viewModel.orphanMismatch.collectAsStateWithLifecycle()
    // ★ 文件不可访问弹窗
    val fileMissingBookId by viewModel.fileMissingBookId.collectAsStateWithLifecycle()
    val fileMissingDeleteInProgress by viewModel.fileMissingDeleteInProgress.collectAsStateWithLifecycle()
    // ★ 文件重定位(Relocate)状态
    val relocateInProgress by viewModel.relocateInProgress.collectAsStateWithLifecycle()
    val relocateMismatch by viewModel.relocateMismatch.collectAsStateWithLifecycle()

    val reselectLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && reselectBookId != null) {
            // 不立即 clearReselectBookId;由 reimportBookWithNewUri 完成后(success/fail)驱动
            viewModel.reimportBookWithNewUri(reselectBookId!!, uri)
        } else {
            // 用户在 picker 里没选文件直接返回 → 清状态
            viewModel.clearReselectBookId()
        }
    }

    // ★ 文件重定位 launcher:用户为改名/移动后的书重新指定文件
    val relocateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && fileMissingBookId != null) {
            viewModel.relocateBook(fileMissingBookId!!, uri)
        }
        // uri == null:用户在 picker 里直接返回 → 保持 fileMissing 弹窗,不做任何操作
    }

    if (reselectBookId != null) {
        // 用 books 列表的 source 判定是否 orphan(无需新增 StateFlow)
        val reselectedBook = remember(reselectBookId) {
            books.find { it.id == reselectBookId }
        }
        val isOrphan = reselectedBook?.source == HomeViewModel.SOURCE_SYNC_ORPHAN
        val bookTitle = reselectedBook?.title ?: ""

        AlertDialog(
            onDismissRequest = {
                if (!reselectInProgress) viewModel.clearReselectBookId()
            },
            title = {
                Text(
                    stringResource(
                        if (isOrphan) R.string.orphan_book_title
                        else R.string.book_file_not_found
                    )
                )
            },
            text = {
                if (reselectInProgress) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.orphan_importing))
                    }
                } else {
                    Text(
                        stringResource(
                            if (isOrphan) R.string.orphan_book_message
                            else R.string.book_file_not_accessible_message,
                            bookTitle
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
//                        val mimeTypes = when (reselectBookFileType) {
//                            "pdf" -> arrayOf("application/pdf")
//                            "epub" -> arrayOf("application/epub+zip")
//                            "mobi" -> arrayOf("application/x-mobipocket-ebook")
//                            "azw3" -> arrayOf("application/vnd.amazon.mobi8-ebook")
//                            "fb2" -> arrayOf("application/x-fictionbook+xml")
//                            else -> arrayOf("*/*")
//                        }
                        val mimeTypes =  arrayOf("*/*")
                        reselectLauncher.launch(mimeTypes)
                    },
                    enabled = !reselectInProgress
                ) {
                    Text(stringResource(R.string.reselect_file))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.clearReselectBookId() },
                    enabled = !reselectInProgress
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ★ 文件不可访问弹窗（scan/import/opds/external_import 来源，文件被外部删除/移动）
    // 三按钮布局(参考 ReviewPromptDialog 模式):
    //   confirmButton = 重新定位(primary)
    //   dismissButton = Column { 忽略 ; 从书库移除(error) }
    if (fileMissingBookId != null && relocateMismatch == null) {
        val missingBook = remember(fileMissingBookId) {
            books.find { it.id == fileMissingBookId }
        }
        val bookTitle = missingBook?.title ?: ""
        // 按钮禁用条件:正在删除 或 正在重定位
        val anyActionInProgress = fileMissingDeleteInProgress || relocateInProgress

        AlertDialog(
            onDismissRequest = {
                if (!anyActionInProgress) viewModel.clearFileMissingState()
            },
            icon = {
                Icon(
                    Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.file_not_accessible)) },
            text = {
                when {
                    fileMissingDeleteInProgress -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.deleting))
                        }
                    }
                    relocateInProgress -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.relocate_in_progress))
                        }
                    }
                    else -> {
                        Text(stringResource(R.string.file_deleted_externally_message, bookTitle))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        relocateLauncher.launch(arrayOf("*/*"))
                    },
                    enabled = !anyActionInProgress
                ) {
                    Text(stringResource(R.string.relocate_file))
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { viewModel.clearFileMissingState() },
                        enabled = !anyActionInProgress
                    ) {
                        Text(stringResource(R.string.ignore))
                    }
                    TextButton(
                        onClick = { viewModel.removeFileMissingBook(fileMissingBookId!!) },
                        enabled = !anyActionInProgress,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(stringResource(R.string.remove_from_library))
                    }
                }
            }
        )
    }

    // ★ 重定位文件不匹配弹窗(contentHash 或 fileType 不一致时弹出)
    relocateMismatch?.let { mismatch ->
        AlertDialog(
            onDismissRequest = {
                // 关闭不匹配弹窗 → 回到文件不可访问弹窗(fileMissingBookId 仍非空)
                viewModel.dismissRelocateMismatch()
            },
            title = { Text(stringResource(R.string.orphan_file_mismatch_title)) },
            text = {
                Text(stringResource(R.string.relocate_hash_mismatch_message, mismatch.bookTitle))
            },
            confirmButton = {
                TextButton(onClick = {
                    // 重新选择:关闭 mismatch 弹窗,重新拉起 picker
                    viewModel.dismissRelocateMismatch()
                    val missingBook = books.find { it.id == mismatch.bookId }
                    relocateLauncher.launch(arrayOf("*/*"))
                }) {
                    Text(stringResource(R.string.reselect_file))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissRelocateMismatch() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ★ P1-1:orphan 文件不匹配弹窗(contentHash 或 fileType 不一致时弹出)
    orphanMismatch?.let { mismatch ->
        AlertDialog(
            onDismissRequest = {
                // 关闭不匹配弹窗 → 回到 reselect 弹窗(用户可重新选择文件)
                viewModel.dismissOrphanMismatch()
            },
            title = {
                Text(stringResource(R.string.orphan_file_mismatch_title))
            },
            text = {
                Text(stringResource(R.string.orphan_file_mismatch_message, mismatch.orphanTitle))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmImportAsNewBook() }) {
                    Text(stringResource(R.string.orphan_import_as_new))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissOrphanMismatch() }) {
                    Text(stringResource(R.string.reselect_file))
                }
            }
        )
    }

    val selectedTabRow by viewModel.selectedTabRow.collectAsStateWithLifecycle()
    var selectedTab by viewModel.selectedTab

//    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val context = LocalContext.current

    val allShelves = remember(shelves) { listOf(
        context.getString(R.string.all_books)) + shelves.map { it.name } }
    val pagerState = rememberPagerState { allShelves.size }

    var searchMode by remember { mutableStateOf(false) }
    val selectedBooks by viewModel.selectedBooks.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()

    var showLayoutModal by viewModel.showLayoutModal
    var showSortModal by viewModel.showSortModal
    var showMetadataModal by viewModel.showMetadataModal

    var showSelectDirectoryDialog by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var selectionFabExpanded by remember { mutableStateOf(false) }
    var showShelfPickerDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }

    val shouldShowGuide = showFabGuide == true && !fabExpanded
    var guideFading by remember { mutableStateOf(false) }

    val guideAlpha by animateFloatAsState(
        targetValue = if (shouldShowGuide && !guideFading) 1f else 0f,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        finishedListener = {
            if (guideFading) {
                viewModel.markFabGuideShown()
            }
        },
        label = "guideAlpha"
    )

    if (showFabGuide != true) {
        guideFading = false
    }

    val guideActive = shouldShowGuide && !guideFading

    BackHandler(enabled = importProgress !is ImportProgressState.Idle) {
        when (importProgress) {
            is ImportProgressState.Completed,
            is ImportProgressState.Error -> viewModel.dismissImportDialog()
            is ImportProgressState.InProgress -> { }
            is ImportProgressState.Idle -> { }
        }
    }

    BackHandler(enabled = fabExpanded) {
        fabExpanded = false
    }

    LaunchedEffect(selectionMode, searchMode, selectedTabRow, selectedTab) {
        fabExpanded = false
        selectionFabExpanded = selectionMode
    }

    val lastOpenBookRoute by viewModel.openLastBookRoute.collectAsStateWithLifecycle()

    val getDirectoryPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let {
//                context.contentResolver.takePersistableUriPermission(
//                    it,
//                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
//                )
//                viewModel.addScanDirectory(it.toString())
                viewModel.addScanDirectory(it)
            }
        }

    val importFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                viewModel.importSingleFile(uri)
            }
        }

    LaunchedEffect(selectedTab) {
        pagerState.animateScrollToPage(selectedTab)
        viewModel.clearBookSelection()
        if (selectedTab == 0) {
            viewModel.updateCurrentShelf(null)
        } else {
            val shelf = shelves.getOrNull(selectedTab - 1)
            viewModel.updateCurrentShelf(shelf, selectedTab)
            shelf?.let { viewModel.getBooksForShelf(it.id) }
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            selectedTab = pagerState.currentPage
        }
    }


    if (appPreferences != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            PageBackground(viewModel)

            Scaffold(
                topBar = {
                    if (selectedTabRow == 0) {
                        AppTopAppBar(
                            modifier = Modifier.fillMaxWidth(),
                            title = { Text(stringResource(R.string.home)) }
                        )
                    } else {
                        AnimatedVisibility(
                            visible = searchMode,
                            enter = slideInHorizontally(initialOffsetX = { it }),
                            exit = slideOutHorizontally(targetOffsetX = { it })
                        ) {
                            AppTopAppBar(
                                modifier = Modifier.fillMaxWidth(),
                                title = {
                                    CustomSearchBar(
                                        query = searchQuery,
                                        onQueryChange = { viewModel.updateSearchQuery(it) },
                                        onClose = {
                                            searchMode = false
                                            viewModel.updateSearchQuery("")
                                        }
                                    )
                                }
                            )
                        }
                        AnimatedVisibility(
                            visible = !searchMode,
                            enter = slideInHorizontally(initialOffsetX = { -it }),
                            exit = slideOutHorizontally(targetOffsetX = { -it })
                        ) {
                            CustomTopAppBar(
                                viewModel = viewModel,
                                selectedTab = selectedTab,
                                selectedBooks = selectedBooks,
                                selectionMode = selectionMode,
                                selectAll = {
                                    viewModel.selectAllBooks(books)
                                },
                                appPreferences = appPreferences!!,
                                toggleLayoutModal = { showLayoutModal = true },
                                toggleSortFilterModal = { showSortModal = true },
                                totalBooks = books.size,
                                currentShelfBookCount = booksInShelf.size,
                                toggleSearchMode = {
                                    searchMode = true
                                },
                            )
                        }
                    }
                },
                bottomBar = {
                    AnimatedVisibility(
                        visible = !selectionMode,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                    ) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text(
                                    stringResource(R.string.home),
                                    textAlign = TextAlign.Center
                                ) },
                                selected = selectedTabRow == 0,
                                onClick = { viewModel.updateCurrentTabRow(0) }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null) },
                                label = { Text(stringResource(R.string.library),
                                    textAlign = TextAlign.Center)
                                        },
                                selected = selectedTabRow == 1,
                                onClick = { viewModel.updateCurrentTabRow(1) }
                            )
                            NavigationBarItem(
                                icon = {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                },
                                label = { Text(stringResource(R.string.mine),
                                    textAlign = TextAlign.Center)
                                        },
                                selected = selectedTabRow == 2,
                                onClick = { viewModel.updateCurrentTabRow(2) }
                            )
                        }
                    }

                },
                floatingActionButton = {
                    if (selectedTabRow == 1) {
                        val isSnackbarVisible by remember {
                            derivedStateOf { snackbarHostState.currentSnackbarData != null }
                        }
                        val fabOffset by animateDpAsState(
                            //60dp的snackbar的高度 + 8dp + 8dp 的间隔
                            targetValue = if (isSnackbarVisible) 76.dp else 0.dp,
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            label = "fabSnackbarOffset"
                        )

                        val density = LocalDensity.current
                        Box(modifier = Modifier.offset {
                            IntOffset(0, with(density) { -fabOffset.roundToPx() })
                        }) {
                            if (selectionMode) {
                                SelectionFabMenu(
                                    isExpanded = selectionFabExpanded,
                                    onExpandChange = { selectionFabExpanded = it },
                                    allFavorited = selectedBooks.isNotEmpty() &&
                                        selectedBooks.all { it.isFavorite },
                                    canEdit = selectedBooks.size == 1,
                                    onEditBook = {
                                        selectionFabExpanded = false
                                        showMetadataModal = true
                                    },
                                    onToggleFavorite = {
                                        selectionFabExpanded = false
                                        viewModel.toggleFavorite(selectedBooks)
                                        viewModel.clearBookSelection()
                                    },
                                    onMoveToShelf = {
                                        selectionFabExpanded = false
                                        showShelfPickerDialog = true
                                    },
                                    onDelete = {
                                        selectionFabExpanded = false
                                        showDeleteConfirmDialog = true
                                    },
                                    onClose = {
                                        selectionFabExpanded = false
                                        viewModel.clearBookSelection()
                                    }
                                )
                            } else {
                            Box {
                                HomeFloatingActionButton(
                                    isExpanded = fabExpanded,
                                    onExpandChange = { fabExpanded = it },
                                    onImportFileClick = {
                                        try {
                                            importFileLauncher.launch(
                                                arrayOf(
                                                    "application/epub+zip",
                                                    "application/pdf",
                                                    "application/x-mobipocket-ebook",
                                                    "application/vnd.amazon.mobi8-ebook",
                                                    "text/plain",
                                                    "text/html",
                                                    "application/xhtml+xml",
                                                    "audio/mpeg",
                                                    "audio/mp4",
                                                    "audio/aac",
                                                    "*/*"
                                                )
                                            )
                                        } catch (_: ActivityNotFoundException) {
                                            ToastUtil.show(R.string.no_file_manager_found)
                                        }
                                    },
                                    onAddScanDirClick = { showSelectDirectoryDialog = true },
                                    onOpdsClick = {
                                        navController.navigateToScreen(
                                            Screens.OpdsCatalogListScreen.route
                                        )
                                    },
                                    showHighlight = guideActive,
                                )

                                if (shouldShowGuide || guideFading) {
                                    FabGuideTooltip(
                                        alpha = guideAlpha,
                                        interactive = guideActive,
                                        onDismiss = { guideFading = true },
                                    )
                                }
                            }
                        }
                        }
                    }
                },
                snackbarHost = { },
                containerColor = Color.Transparent,
                contentColor = Color.Transparent
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    if (selectedTabRow < 0) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (selectedTabRow == 0) {
                        var isBookOpen by rememberSaveable { mutableStateOf(false) }
                        LaunchedEffect(isBookOpen) {
                            if (isBookOpen) {
                                delay(500)
                                isBookOpen = false
                            }
                        }
                        HomeOverviewPanel(
                            contentPadding = innerPadding,
                            onOpenBook = { book ->
                                if (!isBookOpen && viewModel.openBookWithAccessCheck(book)) {
                                    isBookOpen = true
                                    val route = buildReaderRoute(book.id,
                                        book.fileType,
                                        book.filePath,
                                        book.coverImage,
                                        title = book.title,
                                        author = book.author,
                                    )
                                    navController.navigate(route)
                                }
                            },
                            onImportClick = {
                                viewModel.updateCurrentTabRow(1)
                            },
                            onNavigateToStatistics = {
                                navController.navigate(Screens.StatisticsScreen.route)
                            },
                        )
                    } else if (selectedTabRow == 1) {
                        HomeShelfsPanel(innerPadding, pagerState, viewModel)
                    } else if (selectedTabRow == 2) {
                        HomeMinePanel(innerPadding, viewModel)
                    }
                    val scrimAlpha by animateFloatAsState(
                        targetValue = if (fabExpanded) 0.4f else 0f,
                        animationSpec = tween(durationMillis = 300),
                        label = "scrimAlpha"
                    )
                    if (scrimAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = scrimAlpha))
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        if (!selectionMode) {
                                            fabExpanded = false
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            //单本书籍导入时，snackbar显示加载状态
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 88.dp)  // Bottom navigation的高度 + 8dp的间隔
            ) { data ->
                Snackbar(
                    action = {
                        TextButton(onClick = { data.dismiss() }) {
                            Text(stringResource(R.string.dismiss))
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(data.visuals.message, maxLines = 2)
                }
            }

            //目录导入书籍时，全屏遮罩显示加载进度
            ImportOverlay(
                importState = importProgress,
                onDismiss = { viewModel.dismissImportDialog() }
            )

            if (showShelfPickerDialog) {
                ShelfPickerDialog(
                    selectedBooks = selectedBooks,
                    shelves = shelves,
                    viewModel = viewModel,
                    onCancel = {
                        showShelfPickerDialog = false
                    },
                    onConfirm = {
                        showShelfPickerDialog = false
                        viewModel.clearBookSelection()
                    },
                    onNavigateToShelves = {
                        showShelfPickerDialog = false
                        navController.navigateToScreen(Screens.ShelvesScreen.route)
                    },
                )
            }

            if (showDeleteConfirmDialog) {
                DeleteConfirmDialog(
                    onCancel = {
                        showDeleteConfirmDialog = false
                    },
                    onDismiss = {
                        showDeleteConfirmDialog = false
                        viewModel.clearBookSelection()
                    },
                    onConfirm = {
                        viewModel.removeBooks(selectedBooks)
                        showDeleteConfirmDialog = false
                        viewModel.clearBookSelection()
                    },
                )
            }

            // 好评引导弹窗：D4 仅在首页/书库 tab 显示（防止读完书跳详情页时弹窗错位）
            if (showReviewPrompt && selectedTabRow in 0..1) {
                val context = androidx.compose.ui.platform.LocalContext.current
                com.wxn.reader.presentation.home.components.ReviewPromptDialog(
                    onRate = {
                        viewModel.onRateClick(context as androidx.activity.ComponentActivity)
                    },
                    onFeedback = {
                        viewModel.onFeedbackClick()
                        navController.navigate(Screens.FeedbackScreen.route)
                    },
                    onLater = { viewModel.onLaterClick() },
                    onDismiss = { viewModel.onDismissClick() },
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }

    if (showSelectDirectoryDialog) {
        AlertDialog(
            onDismissRequest = { showSelectDirectoryDialog = false },
            title = { Text(stringResource(R.string.select_directory)) },
            text = { Text(stringResource(R.string.choose_a_directory_to_add_to_the_scan_list)) },
            confirmButton = {
                Button(onClick = {
                    showSelectDirectoryDialog = false
                    try {
                        getDirectoryPermissionLauncher.launch(null)
                    } catch (_: ActivityNotFoundException) {
                        ToastUtil.show(R.string.no_file_manager_found)
                    }
                }) {
                    Text(stringResource(R.string.select))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSelectDirectoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (lastOpenBookRoute.isNotEmpty()) {
        navController.navigate(lastOpenBookRoute)
        viewModel.resetLastBookOpenRoute()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearBookSelection()
        }
    }
}

@Composable
private fun FabGuideTooltip(
    alpha: Float,
    interactive: Boolean,
    onDismiss: () -> Unit,
) {
    Popup(
        alignment = Alignment.BottomEnd,
        offset = IntOffset(0, with(LocalDensity.current) { (-100).dp.roundToPx() }),
        properties = PopupProperties(
            focusable = interactive,
            dismissOnBackPress = interactive,
            dismissOnClickOutside = interactive,
        ),
        onDismissRequest = { if (interactive) onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .width(260.dp)
                .wrapContentHeight()
                .padding(end = 16.dp)
                .graphicsLayer { this.alpha = alpha },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 8.dp,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.fab_guide_tap_to_add_books),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.fab_guide_add_books_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable fun PageBackground(viewModel: HomeViewModel) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()

    if (!themePreferences?.homeBackgroundImage.isNullOrEmpty()) { //自定义背景
        Image(
            painter = rememberAsyncImagePainter(themePreferences?.homeBackgroundImage),
            contentDescription = "Book cover",
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.7f),
            contentScale = ContentScale.Crop
        )
        // Gradient overlay
        Box(                    //默认背景
            modifier = Modifier.fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background
                        ),
                        startY = 0f,
                        endY = 2000f
                    )
                )
        )
    } else {
        //默认纯色背景
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    }
}