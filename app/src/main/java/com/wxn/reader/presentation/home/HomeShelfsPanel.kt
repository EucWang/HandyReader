package com.wxn.reader.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wxn.base.bean.Book
import com.wxn.base.util.Logger
import com.wxn.reader.data.model.Layout
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.buildReaderRoute
import com.wxn.reader.presentation.bookDetails.components.EditMetadataModal
import com.wxn.reader.presentation.home.components.GridLayout
import com.wxn.reader.presentation.home.components.LayoutModal
import com.wxn.reader.presentation.home.components.ListLayout
import com.wxn.reader.presentation.home.components.SortFilterModal
import com.wxn.reader.presentation.sharedComponents.Shelves
import com.wxn.reader.presentation.sharedComponents.dialogs.AddShelfDialog


@Composable
fun HomeShelfsPanel(
    innerPadding: PaddingValues,
    pagerState: PagerState,
    viewModel: HomeViewModel
) {
    var selectedTab by viewModel.selectedTab
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()

    val selectedBooks by viewModel.selectedBooks.collectAsStateWithLifecycle()
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()

    var showLayoutModal by viewModel.showLayoutModal
    var showSortModal by viewModel.showSortModal
    var showMetadataModal by viewModel.showMetadataModal

    var showAddShelfDialog by remember { mutableStateOf(false) }
    var newShelfName by remember { mutableStateOf("") }

    if (appPreferences != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Shelves(
                appPreferences = appPreferences!!,
                shelves = shelves,
                selectedTab = selectedTab,
                onTabSelected = { index ->
                    selectedTab = index
                },
                onShowAddShelfDialog = {
                    viewModel.clearBookSelection()
                    showAddShelfDialog = true
                }
            )
            val isAddingBook by viewModel.isAddingBooks.collectAsState()
            HorizontalPager(
                userScrollEnabled = !isAddingBook,
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { index ->
                Logger.d("HomeScreen:index=$index")
                Box(modifier = Modifier.fillMaxSize()) {
                    Column {
                        when (index) {
                            else -> {
                                HomeMainPanel(viewModel)
                            }
                        }
                    }
                }
            }
        }

        if (showLayoutModal) {
            LayoutModal(
                appPreferences = appPreferences!!,
                viewModel = viewModel,
                onDismiss = { showLayoutModal = false },
            )
        }
        if (showSortModal) {
            SortFilterModal(
                appPreferences = appPreferences!!,
                viewModel = viewModel,
                onDismiss = { showSortModal = false },
            )
        }
        var fullBookForEdit by remember { mutableStateOf<Book?>(null) }
        LaunchedEffect(showMetadataModal, selectedBooks) {
            val books = selectedBooks
            if (showMetadataModal && books.isNotEmpty()) {
                fullBookForEdit = viewModel.getFullBookById(books[0].id)
            } else {
                fullBookForEdit = null
            }
        }
        if (showMetadataModal && fullBookForEdit != null) {
            EditMetadataModal(
                book = fullBookForEdit,
                onDismiss = {
                    showMetadataModal = false
                    viewModel.clearBookSelection()
                }
            )
        }
    }

    if (showAddShelfDialog) {
        AddShelfDialog(
            newShelfName = newShelfName,
            onShelfNameChange = { newShelfName = it },
            shelves = listOf("All Books") + shelves.map { it.name },
            onAddShelf = {
                viewModel.addShelf(newShelfName)
            },
            onDismiss = {
                showAddShelfDialog = false
            }
        )
    }
}

@Composable
fun HomeMainPanel(viewModel: HomeViewModel) {
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val isAddingBooks by viewModel.isAddingBooks.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val slideInAnimationSpec = tween<IntOffset>(durationMillis = 300)
    val tweenInAnimationSpec = tween<Float>(durationMillis = 300)

    var isBookOpen by remember { mutableStateOf(false) }
    val navController: NavHostController = LocalNavController.current

    fun openBook(openedBook: Book) {
        if (!isBookOpen) {
            if (!viewModel.openBookWithAccessCheck(openedBook)) {
                return
            }
            val navigateToBook = {
                isBookOpen = true
                val route = buildReaderRoute(
                    openedBook.id,
                    openedBook.fileType,
                    openedBook.filePath,
                    openedBook.coverImage,
                    title = openedBook.title,
                    author = openedBook.author
                )
                Logger.d("OpenBook::isBookOpen=$isBookOpen,book.fileType=${openedBook.fileType},id=${openedBook.id},route=$route")
                if (route.isNotEmpty()) {
                    navController.navigate(route = route)
                }
            }
            navigateToBook()
        }
    }

    if (appPreferences != null) {
        if (appPreferences!!.homeLayout == Layout.Grid || appPreferences!!.homeLayout == Layout.CoverOnly) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tweenInAnimationSpec) + slideInVertically(
                    animationSpec = slideInAnimationSpec,
                    initialOffsetY = { it })
            ) {
                GridLayout(
                    clearSearch = { viewModel.updateSearchQuery("") },
                    selectionMode = selectionMode,
                    toggleSelection = {
                        viewModel.toggleBookSelection(it)
                    },
                    viewModel = viewModel,
                    isLoading = isAddingBooks,
                    appPreferences = appPreferences!!,
                    openBook = ::openBook,
                )
            }
        } else {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tweenInAnimationSpec) + slideInVertically(
                    animationSpec = slideInAnimationSpec,
                    initialOffsetY = { it })
            ) {
                ListLayout(
                    clearSearch = { viewModel.updateSearchQuery("") },
                    selectionMode = selectionMode,
                    toggleSelection = {
                        viewModel.toggleBookSelection(it)
                    },
                    viewModel = viewModel,
                    isLoading = isAddingBooks,
                    appPreferences = appPreferences!!,
                    openBook = ::openBook
                )
            }
        }
    }
}