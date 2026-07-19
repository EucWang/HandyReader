package com.wxn.reader.presentation.lookuphistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookupHistoryScreen(
    viewModel: LookupHistoryViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    var showSortMenu by remember { mutableStateOf(false) }
    var showBookFilter by remember { mutableStateOf(false) }
    var showLangFilter by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(R.string.lookup_history)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = {
                            showSortMenu = true
                        }) {
                            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.cd_sort))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = {
                                showSortMenu = false
                            }
                        ) {
                            SortBy.entries.forEach { sortBy ->
                                DropdownMenuItem(
                                    text = { Text(sortByLabel(sortBy)) },
                                    onClick = {
                                        viewModel.setSortBy(sortBy)
                                        showSortMenu =false
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            FilterBar(
                uiState = uiState,
                onBookSelected = viewModel::selectBook,
                onLangSelected = viewModel::selectLang,
                showBookFilter = showBookFilter,
                onShowBookFilterChange = { showBookFilter = it },
                showLangFilter = showLangFilter,
                onShowLangFilterChange = { showLangFilter = it }
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = uiState.error ?: "Unknown error",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                uiState.cards.isEmpty() -> {
                    EmptyState(modifier = Modifier.padding(innerPadding))
                }

                else -> {
                    LookupHistoryPager(
                        cards = uiState.cards,
                        currentPage = currentPage,
                        onPageChanged = viewModel::setCurrentPage,
                        totalCount = uiState.totalCount,
                        onRetryDefinition = viewModel::retryFetchDefinition
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.lookup_history_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lookup_history_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FilterBar(
    uiState: LookupHistoryUiState,
    onBookSelected: (Long?) -> Unit,
    onLangSelected: (Pair<String, String>?) -> Unit,
    showBookFilter: Boolean,
    onShowBookFilterChange: (Boolean) -> Unit,
    showLangFilter: Boolean,
    onShowLangFilterChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center) {
            FilterChip(
                selected = uiState.selectedBookId != null,
                onClick = { onShowBookFilterChange(true) },
                label = {
                    Text(
                        uiState.selectedBookId?.let { uiState.availableBooks[it] }
                            ?: stringResource(R.string.all_books),
                        maxLines = 1
                    )
                }
            )
            DropdownMenu(
                expanded = showBookFilter,
                onDismissRequest = { onShowBookFilterChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.all_books)) },
                    onClick = { onBookSelected(null); onShowBookFilterChange(false) }
                )
                uiState.availableBooks.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = { Text(title, maxLines = 1) },
                        onClick = { onBookSelected(id); onShowBookFilterChange(false) }
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center) {
            FilterChip(
                selected = uiState.selectedLang != null,
                onClick = { onShowLangFilterChange(true) },
                label = {
                    Text(
                        uiState.selectedLang?.second ?: stringResource(R.string.all_languages),
                        maxLines = 1
                    )
                }
            )
            DropdownMenu(
                expanded = showLangFilter,
                onDismissRequest = { onShowLangFilterChange(false) }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.all_languages)) },
                    onClick = {
                        onLangSelected(null)
                        onShowLangFilterChange(false)
                    }
                )
                uiState.availableLangs.forEach { lang ->
                    DropdownMenuItem(
                        text = { Text(lang.second) },
                        onClick = {
                            onLangSelected(lang)
                            onShowLangFilterChange(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun sortByLabel(sortBy: SortBy): String {
    return when (sortBy) {
        SortBy.TIME_DESC -> stringResource(R.string.sort_by_time_desc)
        SortBy.TIME_ASC -> stringResource(R.string.sort_by_time_asc)
        SortBy.WORD_ASC -> stringResource(R.string.sort_by_word_asc)
        SortBy.WORD_DESC -> stringResource(R.string.sort_by_word_desc)
    }
}

@Composable
private fun LookupHistoryPager(
    cards: List<LookupHistoryCard>,
    currentPage: Int,
    onPageChanged: (Int) -> Unit,
    totalCount: Int,
    onRetryDefinition: (LookupHistoryCard) -> Unit = {}
) {
    val pagerState = rememberPagerState(
        initialPage = currentPage.coerceIn(0, (cards.size - 1).coerceAtLeast(0)),
        pageCount = { cards.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            if (page < cards.size) {
                LookupHistoryCardComposable(
                    card = cards[page],
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    onRetryDefinition = onRetryDefinition
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / $totalCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
