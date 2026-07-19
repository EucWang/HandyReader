package com.wxn.reader.presentation.opds

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wxn.reader.util.NetImage
import com.wxn.reader.R
import com.wxn.reader.data.model.opds.OpdsEntry
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.opds.components.FacetFilterBar
import com.wxn.reader.presentation.opds.viewmodels.OpdsBrowseViewModel
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar
import com.wxn.reader.util.LanguageUtil
import com.wxn.reader.util.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsBrowseScreen(
    viewModel: OpdsBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    val navController = LocalNavController.current
    val context = LocalContext.current

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            val isBuyHint = message == context.getString(R.string.opds_buy_hint)
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (isBuyHint) context.getString(R.string.opds_retry) else null
            )
            if (result == SnackbarResult.ActionPerformed && isBuyHint) {
                viewModel.retry()
            }
            viewModel.clearUserMessage()
        }
    }

    BackHandler(enabled = uiState.selectedEntry != null) {
        if (uiState.showExternalLinkDialog) {
            viewModel.dismissExternalLinkDialog()
        } else {
            viewModel.dismissBookDetail()
        }
    }

    BackHandler(enabled = uiState.browseStack.isNotEmpty()) {
        viewModel.navigateBack()
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        text = uiState.catalog?.name ?: stringResource(R.string.opds_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!viewModel.navigateBack()) {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.opds_browse_back))
                    }
                },
                actions = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val feed = uiState.feed
            if (feed != null && feed.supportsSearch) {
                val isGutenberg = uiState.catalog?.predefinedId == "gutenberg"
                var showLangMenu by remember { mutableStateOf(false) }
                val gutenbergLanguages = remember {
                    LanguageUtil.languageMaps.values
                        .distinctBy { it.lang }
                        .map { it.lang to it.displayName }
                }
                val selectedLang = uiState.selectedSearchLanguage

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.opds_search_books)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isGutenberg) {
                                Box {
                                    Box(modifier = Modifier.clickable{ showLangMenu = true }
                                        .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
                                        .padding(vertical = 4.dp, horizontal = 6.dp),
                                        contentAlignment = Alignment.Center) {
                                        if (selectedLang != null) {
                                            Text(
                                                text = gutenbergLanguages
                                                    .find { it.first == selectedLang }
                                                    ?.second ?: selectedLang,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Language,
                                                contentDescription = stringResource(R.string.all_languages),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = showLangMenu,
                                        onDismissRequest = { showLangMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.all_languages)) },
                                            onClick = {
                                                viewModel.selectSearchLanguage(null)
                                                showLangMenu = false
                                            }
                                        )
                                        gutenbergLanguages.forEach { (code, name) ->
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    viewModel.selectSearchLanguage(code)
                                                    showLangMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.clearSearch()
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        if (searchQuery.isNotBlank()) viewModel.search(searchQuery)
                    })
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            val facets = uiState.facets
            if (facets.isNotEmpty()) {
                FacetFilterBar(
                    facets = facets,
                    onFacetClick = { facet ->
                        viewModel.navigateToFacet(facet.href)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    val error = uiState.error
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = when (error) {
                                    is OpdsBrowseError.NetworkError -> stringResource(R.string.opds_error_network)
                                    is OpdsBrowseError.AuthRequired -> stringResource(R.string.opds_error_auth)
                                    is OpdsBrowseError.ParseError -> stringResource(R.string.opds_error_parse)
                                    is OpdsBrowseError.EmptySearch -> stringResource(R.string.opds_no_results)
                                    is OpdsBrowseError.RateLimited -> stringResource(R.string.opds_error_rate_limited)
                                    is OpdsBrowseError.ContentTypeError -> stringResource(R.string.opds_error_content_type)
                                    null -> ""
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (error !is OpdsBrowseError.EmptySearch) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(onClick = { viewModel.retry() }) {
                                        Text(stringResource(R.string.opds_retry))
                                    }
                                    if (error is OpdsBrowseError.ContentTypeError) {
                                        OutlinedButton(onClick = {
                                            viewModel.openCurrentUrlInBrowser()
                                        }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.opds_open_in_browser))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    val entries = uiState.entries

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            EntryItem(
                                entry = entry,
                                onEntryClick = {
                                    viewModel.navigateToEntry(entry)
                                },
                                onBookClick = {
                                    viewModel.showBookDetail(entry)
                                },
                                onOpenInBrowser = {
                                    viewModel.openExternalLink(entry)
                                },
                                onOpenExternalNavigation = {
                                    viewModel.openExternalNavigation(entry)
                                }
                            )
                        }

                        if (uiState.hasLoadMoreError) {
                            item(key = "load_more_error") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.opds_load_more_error),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    TextButton(onClick = { viewModel.loadMore() }) {
                                        Text(stringResource(R.string.opds_retry))
                                    }
                                }
                            }
                        }

                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }

                    LaunchedEffect(listState) {
                        snapshotFlow {
                            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            val totalItems = listState.layoutInfo.totalItemsCount
                            lastVisible >= totalItems - 3 && totalItems > 0
                        }.collect { nearEnd ->
                            if (nearEnd && uiState.feed?.hasMore == true && !uiState.isLoadingMore && !uiState.hasLoadMoreError) {
                                viewModel.loadMore()
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.showExternalLinkDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissExternalLinkDialog()
            },
            title = { Text("") },
            text = {
                Text(
                    stringResource(
                        R.string.dialog_content_to_out_href,
                        uiState.externalLinkUrl
                    )
                )
            },
            dismissButton = {
                Button(onClick = {
                    viewModel.dismissExternalLinkDialog()
                }) {
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
                        viewModel.confirmExternalLink()
                    }
                ) {
                    Text(stringResource(R.string.navigate_to))
                }
            },
        )
    }

    uiState.selectedEntry?.let { entry ->
        OpdsBookDetailSheet(
            entry = entry,
            browseViewModel = viewModel,
            onDismiss = { viewModel.dismissBookDetail() }
        )
    }
}

@Composable
private fun EntryItem(
    entry: OpdsEntry,
    onEntryClick: () -> Unit,
    onBookClick: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onOpenExternalNavigation: () -> Unit
) {
    val isExternalNavigation = entry.externalNavigationLinks.isNotEmpty()
        && entry.acquisitionLinks.isEmpty()
        && entry.acquisitionHtmlLinks.isEmpty()
        && entry.externalNavigationLinks.size == entry.navigationLinks.size
    val isNavigation = entry.navigationLinks.isNotEmpty()
        && entry.acquisitionLinks.isEmpty()
        && entry.acquisitionHtmlLinks.isEmpty()
        && !isExternalNavigation
    val isExternalHtml = entry.htmlLinks.isNotEmpty()
        && entry.acquisitionLinks.isEmpty()
        && entry.acquisitionHtmlLinks.isEmpty()
        && entry.navigationLinks.isEmpty()
    val isCompact = isNavigation || isExternalHtml || isExternalNavigation

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                when {
                    isExternalNavigation -> onOpenExternalNavigation()
                    isNavigation -> onEntryClick()
                    isExternalHtml -> onOpenInBrowser()
                    else -> onBookClick()
                }
            }),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompact) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val imageSize = if (isCompact) 42.dp else 120.dp
            if (entry.coverUrl != null) {
                NetImage(
                    modifier = Modifier.size(imageSize),
                    url = entry.coverUrl,
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.authors.isNotEmpty()) {
                    Text(
                        text = entry.authors.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val description = entry.summary
                if (description != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isExternalNavigation  || isNavigation || isExternalHtml) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
