package com.wxn.reader.presentation.home

import android.net.Uri
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.paging.compose.collectAsLazyPagingItems
import com.wxn.base.bean.Book
import com.wxn.reader.R
import com.wxn.reader.data.model.Layout
import com.wxn.reader.presentation.bookDetails.components.EditMetadataModal
import com.wxn.reader.presentation.bookShelf.BookShelfScreen
import com.wxn.reader.presentation.home.components.GridLayout
import com.wxn.reader.presentation.home.components.LayoutModal
import com.wxn.reader.presentation.home.components.ListLayout
import com.wxn.reader.presentation.home.components.SortFilterModal
import com.wxn.reader.presentation.sharedComponents.Shelves
import com.wxn.base.util.Logger
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.FileType.Companion.stringToFileType
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens


@Composable fun HomeShelfsPanel(innerPadding: PaddingValues, pagerState: PagerState, viewModel: HomeViewModel) {
    var selectedTab by viewModel.selectedTab
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val books = viewModel.books.collectAsLazyPagingItems()
    val selectedBooks by viewModel.selectedBooks.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val isAddingBooks by viewModel.isAddingBooks.collectAsStateWithLifecycle()
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()

    var showLayoutModal by viewModel.showLayoutModal
    var showSortModal by viewModel.showSortModal
    var showMetadataModal by viewModel.showMetadataModal

    if (appPreferences != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Shelves(
                viewModel = viewModel,
                appPreferences = appPreferences!!,
                shelves = shelves,
                selectedTab = selectedTab,
                onTabSelected = { index ->
                    selectedTab = index
                },
                onAddShelf = { newShelfName ->
                    viewModel.addShelf(newShelfName)
                },
            )
            val isAddingBook by viewModel.isAddingBooks.collectAsState()
            HorizontalPager(
                userScrollEnabled = !isAddingBook,
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
    //                .background(color = MaterialTheme.colorScheme.background)
            ) { index ->
                Logger.d("HomeScreen:index=$index")
                Box(modifier = Modifier.fillMaxSize()) {
    //                if (appPreferences.homeBackgroundImage.isNotEmpty()) { //自定义背景
    //                    Image(
    //                        painter = rememberAsyncImagePainter(appPreferences.homeBackgroundImage),
    //                        contentDescription = "Book cover",
    //                        modifier = Modifier
    //                            .fillMaxSize()
    //                            .alpha(0.7f),
    //                        contentScale = ContentScale.Crop
    //                    )
    //                }
    //
    //                // Gradient overlay
    //                Box(                    //默认背景
    //                    modifier = Modifier.fillMaxSize()
    //                        .background(
    //                            brush = Brush.verticalGradient(
    //                                colors = listOf(
    //                                    Color.Transparent,
    //                                    MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
    //                                    MaterialTheme.colorScheme.background
    //                                ),
    //                                startY = 0f,
    //                                endY = 2000f
    //                            )
    //                        )
    //                )

                    Column {
                        when (index) {
                            0 -> {
                                HomeMainPanel(viewModel)
                            }

                            1 -> {
                                HomeVoiceBookPanel(1, viewModel)
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
        if (showMetadataModal) {
            EditMetadataModal(
                book = selectedBooks[0],
                onDismiss = {
                    viewModel.toggleBookSelection(selectedBooks[0])
                    showMetadataModal = false
                }
            )
        }
    }
}

@Composable
fun HomeVoiceBookPanel(index: Int, viewModel: HomeViewModel) {
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val books = viewModel.books.collectAsLazyPagingItems()
    val selectedBooks by viewModel.selectedBooks.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val isAddingBooks by viewModel.isAddingBooks.collectAsStateWithLifecycle()
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()

    val shelf = shelves.getOrNull(index - 1)
    if (shelf != null && appPreferences != null) {
        BookShelfScreen(
            clearSearch = { viewModel.updateSearchQuery("") },
            shelf = shelf,
            books = books,
            homeViewModel = viewModel,
            selectedBooks = selectedBooks,
            selectionMode = selectionMode,
            toggleSelection = { book -> viewModel.toggleBookSelection(book) },
            isLoading = isAddingBooks,
            appPreferences = appPreferences!!,
        )
    } else {
        Text(stringResource(R.string.shelf_not_found))
    }
}

@Composable
fun HomeMainPanel(viewModel: HomeViewModel) {
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val books = viewModel.books.collectAsLazyPagingItems()
    val selectedBooks by viewModel.selectedBooks.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val isAddingBooks by viewModel.isAddingBooks.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    val slideInAnimationSpec = tween<IntOffset>(durationMillis = 300)
    val tweenInAnimationSpec = tween<Float>(durationMillis = 300)

//                            if (books.itemCount == 0) {
//                                EmptyShelfContent("Library")
//                            }

    var isBookOpen by remember { mutableStateOf(false) }
    val navController: NavHostController = LocalNavController.current

    fun openBook(openedBook: Book) {
        if (!isBookOpen) {  // Only open a book if no book is currently open
            val navigateToBook = {
                val encodedUri = Uri.encode(openedBook.filePath)
                isBookOpen = true  // Set the state to indicate a book is open
                val route = when (stringToFileType(openedBook.fileType)) {
//                    FileType.EPUB -> Screens.BookReaderScreen.route + "/${openedBook.id}/${encodedUri}"
                    FileType.PDF -> Screens.PdfReaderScreen.route + "/${openedBook.id}/${encodedUri}"
                    FileType.AUDIOBOOK -> Screens.AudiobookReaderScreen.route + "/${openedBook.id}/${encodedUri}"
                    else -> {
                        Screens.MainReaderScreen.route + "/${openedBook.id}/${encodedUri}"
                    }
                }
                Logger.d("OpenBook::isBookOpen=$isBookOpen,book.fileType=${openedBook.fileType},encodedUri=${encodedUri},id=${openedBook.id},route=$route")
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
                    books = books,
                    selectedBooks = selectedBooks,
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
                    books = books,
                    selectedBooks = selectedBooks,
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