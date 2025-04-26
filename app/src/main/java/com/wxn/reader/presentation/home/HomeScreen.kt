package com.wxn.reader.presentation.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.ModeEdit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.rememberAsyncImagePainter
import com.wxn.reader.R
import com.wxn.reader.data.model.Layout
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.PurchaseHelperController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.bookDetails.components.EditMetadataModal
import com.wxn.reader.presentation.bookShelf.BookShelfScreen
import com.wxn.reader.presentation.home.components.CustomBottomAppBar
import com.wxn.reader.presentation.home.components.CustomSearchBar
import com.wxn.reader.presentation.home.components.CustomSnackbar
import com.wxn.reader.presentation.home.components.CustomTopAppBar
import com.wxn.reader.presentation.home.components.GridLayout
import com.wxn.reader.presentation.home.components.LayoutModal
import com.wxn.reader.presentation.home.components.ListLayout
import com.wxn.reader.presentation.home.components.SortFilterModal
import com.wxn.reader.presentation.sharedComponents.NavigationItem
import com.wxn.reader.presentation.sharedComponents.Shelves
import com.wxn.reader.util.Logger
import com.wxn.reader.util.PurchaseHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val purchaseHelper: PurchaseHelper = PurchaseHelperController.current
    val coroutineScope = rememberCoroutineScope()
    val navController = LocalNavController.current

    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val shelves by viewModel.shelves.collectAsStateWithLifecycle()
    val isAddingBooks by viewModel.isAddingBooks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val booksInShelf by viewModel.booksInShelfSet.collectAsStateWithLifecycle()
    val books = viewModel.books.collectAsLazyPagingItems()

    val importProgress by viewModel.importProgressState.collectAsStateWithLifecycle()
    val snackbarState by viewModel.snackbarState.collectAsStateWithLifecycle()

    val selectedTabRow by viewModel.selectedTabRow.collectAsStateWithLifecycle()
    var selectedTab by viewModel.selectedTab

//    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val allShelves = remember(shelves) { listOf("All Books") + shelves.map { it.name } }
    val pagerState = rememberPagerState { allShelves.size }

    var searchMode by remember { mutableStateOf(false) }
    val selectedBooks by viewModel.selectedBooks.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()

    var showLayoutModal by viewModel.showLayoutModal
    var showSortModal by viewModel.showSortModal
    var showMetadataModal by viewModel.showMetadataModal


//    LaunchedEffect(Unit) {
//        delay(5000)
//        if (!appPreferences.isPremium && Random.nextFloat() <= 0.10f) {
//            navController.navigate(Screens.PremiumScreen.route)
//        }
//    }

    LaunchedEffect(selectedTab) {
        pagerState.animateScrollToPage(selectedTab)
        viewModel.clearBookSelection()
        if (selectedTab == 0) {
            viewModel.updateCurrentShelf(null)
        } else {
            val shelf = shelves.getOrNull(selectedTab - 1)
            viewModel.updateCurrentShelf(shelf)
            shelf?.let { viewModel.getBooksForShelf(it.id) }
        }
    }

    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            selectedTab = pagerState.currentPage
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        if (appPreferences.homeBackgroundImage.isNotEmpty()) { //自定义背景
            Image(
                painter = rememberAsyncImagePainter(appPreferences.homeBackgroundImage),
                contentDescription = "Book cover",
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.7f),
                contentScale = ContentScale.Crop
            )
        }

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

//    CustomNavigationDrawer(
//        drawerState = drawerState,
//    ) {
        Scaffold(
            topBar = {
                AnimatedVisibility(
                    visible = searchMode,
                    enter = slideInHorizontally(initialOffsetX = { it }),
                    exit = slideOutHorizontally(targetOffsetX = { it })
                ) {
                    TopAppBar(
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
                        shelves = shelves,
                        selectedBooks = selectedBooks,
                        selectionMode = selectionMode,
                        clearSelection = {
                            viewModel.clearBookSelection()
                        },
                        selectAll = {
                            viewModel.selectAllBooks(books.itemSnapshotList.items)
                        },
                        appPreferences = appPreferences,
                        toggleLayoutModal = { showLayoutModal = true },
                        toggleSortFilterModal = { showSortModal = true },
                        totalBooks = books.itemCount,
                        currentShelfBookCount = booksInShelf.size,
                        toggleSearchMode = {
                            searchMode = true
                        },
//                        openDrawer = {
//                            coroutineScope.launch {
//                                drawerState.open()
//                            }
//                        },
                    )
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = !selectionMode,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                ) {
                    NavigationBar {
                        NavigationBarItem(
                            icon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Ebooks") },
                            label = { Text(
                                stringResource(R.string.ebooks)
                            ) },
                            selected = selectedTabRow == 0,
                            onClick = { viewModel.updateCurrentTabRow(0) }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Headset, contentDescription = "AudioBooks") },
                            label = { Text(stringResource(R.string.audio_books)) },
                            selected = selectedTabRow == 1,
                            onClick = { viewModel.updateCurrentTabRow(1) }
                        )
                        NavigationBarItem(
                            icon = {
                                Icon(Icons.Default.Person, contentDescription = "Mine")
                            },
                            label = {
                                Text(stringResource(R.string.mine))
                            },
                            selected = selectedTabRow == 2,
                            onClick = { viewModel.updateCurrentTabRow(2) }
                        )
                    }
                }

                AnimatedVisibility(
                    visible = selectionMode,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                ) {
                    CustomBottomAppBar(
                        shelves = shelves,
                        selectedBooks = selectedBooks,
                        viewModel = viewModel,
                        clearSelection = {
                            viewModel.clearBookSelection()
                        },
                        navController = navController
                    )
                }
            },
            floatingActionButton = {
                AnimatedVisibility(
                    visible = (selectionMode && selectedBooks.size == 1),
                    enter = fadeIn() + slideInHorizontally(initialOffsetX = { it }),
                    exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it }),
                ) {
                    FloatingActionButton(
                        shape = CircleShape,
                        onClick = {
                            showMetadataModal = true
//                            val encodedUri = Uri.encode(selectedBooks[0].uri)
//                            navController.navigate(
//                                Screens.BookDetailsScreen.route + "/${selectedBooks[0].id}/${encodedUri}"
//                            )
                        }
                    ) {
                        Icon(Icons.Default.ModeEdit, contentDescription = "Edit Book")
                    }
                }
            },
            snackbarHost = {
                CustomSnackbar(
                    snackbarState = snackbarState,
                    importProgressState = importProgress,
                )
            },
            containerColor = Color.Transparent,
            contentColor = Color.Transparent
        ) { innerPadding ->
            if (selectedTabRow == 0 || selectedTabRow == 1) {
                HomeShelfsPanel(innerPadding, pagerState, viewModel)
            } else if (selectedTabRow == 2) {
                HomeMinePanel(innerPadding, viewModel)
            }
        }
    }
}
