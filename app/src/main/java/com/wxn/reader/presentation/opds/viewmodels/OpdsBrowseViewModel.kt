package com.wxn.reader.presentation.opds.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.R
import com.wxn.reader.data.model.opds.OpdsEntry
import com.wxn.reader.data.remote.opds.OpdsAuthException
import com.wxn.reader.data.remote.opds.OpdsContentTypeException
import com.wxn.reader.data.remote.opds.OpdsNetworkException
import com.wxn.reader.data.remote.opds.OpdsParseException
import com.wxn.reader.data.model.opds.OpdsEntryCache
import com.wxn.reader.domain.use_case.opds.BrowseOpdsFeedUseCase
import com.wxn.reader.domain.use_case.opds.ManageOpdsCatalogUseCase
import com.wxn.reader.domain.use_case.opds.SearchOpdsUseCase
import com.wxn.reader.presentation.opds.BrowseStackEntry
import com.wxn.reader.presentation.opds.OpdsBrowseError
import com.wxn.reader.presentation.opds.OpdsBrowseUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.wxn.base.util.Logger
import com.wxn.reader.data.model.opds.OpdsFeed

@HiltViewModel
class OpdsBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val browseFeedUseCase: BrowseOpdsFeedUseCase,
    private val searchUseCase: SearchOpdsUseCase,
    private val manageCatalogUseCase: ManageOpdsCatalogUseCase,
    private val entryCache: OpdsEntryCache
) : ViewModel() {

    private val catalogId: Long = savedStateHandle["catalogId"] ?: -1L

    private val _uiState = MutableStateFlow(OpdsBrowseUiState())
    val uiState: StateFlow<OpdsBrowseUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null
    private var fetchGeneration = 0

    init {
        loadCatalogAndFetch()
    }

    private fun loadCatalogAndFetch() {
        viewModelScope.launch {
            val catalog = manageCatalogUseCase.getAllEnabledCatalogs()
                .first()
                .firstOrNull { it.id == catalogId }
            if (catalog != null) {
                _uiState.update { it.copy(catalog = catalog) }
                fetchFeed(catalog.url, catalog.name)
            } else {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = OpdsBrowseError.NetworkError(context.getString(R.string.opds_error_network))
                ) }
            }
        }
    }

    private fun launchFetch(block: suspend (Int) -> Unit) {
        fetchGeneration++
        val myGen = fetchGeneration
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            try {
                block(myGen)
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (myGen == fetchGeneration) {
                    _uiState.update { it.copy(isLoading = false, isSearching = false, isLoadingMore = false) }
                }
                throw e
            }
        }
    }

    fun fetchFeed(url: String, title: String = "") {
        launchFetch { gen ->
            _uiState.update { it.copy(isLoading = true, error = null, hasLoadMoreError = false, showAuthDialog = false, currentUrl = url) }
            browseFeedUseCase(catalogId, url).fold(
                onSuccess = { feed ->
                    if (gen != fetchGeneration) return@fold

                    val effectiveFeed = sanitizeFeed(feed)

                    if (effectiveFeed.searchUrl != null) {
                        val currentSearchUrl = _uiState.value.catalog?.searchUrl
                        val needsUpdate = currentSearchUrl == null
                                || effectiveFeed.searchType?.contains("opensearch+template") == true
                        if (needsUpdate) {
                            updateCatalogSearchUrl(effectiveFeed.searchUrl)
                        }
                    }

                    updateCatalogLastAccessed()

                    val allEntries = effectiveFeed.entries + effectiveFeed.groups.flatMap { it.entries }

                    _uiState.update { state ->
                        state.copy(
                            feed = effectiveFeed,
                            entries = allEntries,
                            facets = effectiveFeed.facets,
                            isLoading = false,
                            isSearching = false,
                            error = if (allEntries.isEmpty() && effectiveFeed.navigationEntries.isEmpty()) {
                                OpdsBrowseError.EmptySearch
                            } else null
                        )
                    }
                },
                onFailure = { error ->
                    if (gen != fetchGeneration) return@fold
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = when (error) {
                                is OpdsAuthException -> OpdsBrowseError.AuthRequired
                                is OpdsContentTypeException -> OpdsBrowseError.ContentTypeError(error.url)
                                is OpdsParseException -> OpdsBrowseError.ParseError(error.message ?: "Parse error")
                                is OpdsNetworkException -> if (error.statusCode == 429) OpdsBrowseError.RateLimited else OpdsBrowseError.NetworkError(error.message ?: "Unknown error")
                                else -> OpdsBrowseError.NetworkError(error.message ?: "Unknown error")
                            },
                            showAuthDialog = error is OpdsAuthException
                        )
                    }
                }
            )
        }
    }

    fun loadMore() {
        val feed = _uiState.value.feed ?: return
        val nextUrl = feed.nextUrl ?: return

        launchFetch { gen ->
            _uiState.update { it.copy(isLoadingMore = true, hasLoadMoreError = false) }
            browseFeedUseCase(catalogId, nextUrl, useCache = false).fold(
                onSuccess = { nextFeed ->
                    if (gen != fetchGeneration) return@fold
                    val sanitizedNextFeed = sanitizeFeed(nextFeed)
                    _uiState.update { state ->
                        val allNewEntries = sanitizedNextFeed.entries + sanitizedNextFeed.groups.flatMap { it.entries }
                        state.copy(
                            feed = sanitizedNextFeed,
                            entries = (state.entries + allNewEntries).distinctBy { it.id },
                            facets = sanitizedNextFeed.facets.ifEmpty { state.facets },
                            isLoadingMore = false,
                            hasLoadMoreError = false
                        )
                    }
                },
                onFailure = { error ->
                    if (gen != fetchGeneration) return@fold
                    _uiState.update { state ->
                        state.copy(
                            isLoadingMore = false,
                            hasLoadMoreError = true
                        )
                    }
                }
            )
        }
    }

    fun showBookDetail(entry: OpdsEntry) {
        Logger.d("OpdsBrowseViewModel::showBookDetail::$entry")
        entryCache.put(catalogId, entry)
        _uiState.update { it.copy(selectedEntry = entry) }
    }

    fun dismissBookDetail() {
        _uiState.update { it.copy(selectedEntry = null) }
    }

    fun navigateToEntry(entry: OpdsEntry) {
        Logger.d("OpdsBrowseViewModel::navigateToEntry:$entry")
        val navLinks = entry.navigationLinks
        if (navLinks.isNotEmpty()) {
            val targetUrl = navLinks.first().href
            val state = _uiState.value
            val parentUrl = state.browseStack.lastOrNull()?.url
            if (parentUrl != null && urlsMatch(targetUrl, parentUrl)) {
                if (state.isLoading) return
                _uiState.update { it.copy(browseStack = it.browseStack.dropLast(1)) }
                fetchFeed(parentUrl, state.browseStack.lastOrNull()?.title ?: "")
            } else {
                pushToStack(entry.title, state.currentUrl)
                fetchFeed(targetUrl, entry.title)
            }
        }
    }

    fun openExternalLink(entry: OpdsEntry) {
        Logger.i("OpedsBrowseViewModel:openExternalLink:$entry")
        val htmlLink = entry.htmlLinks.firstOrNull() ?: return
        _uiState.update {
            it.copy(
                showExternalLinkDialog = true,
                externalLinkUrl = htmlLink.href
            )
        }
    }

    fun openExternalNavigation(entry: OpdsEntry) {
        Logger.i("OpdsBrowseViewModel:openExternalNavigation:$entry")
        val link = entry.externalNavigationLinks.firstOrNull() ?: return
        _uiState.update {
            it.copy(
                showExternalLinkDialog = true,
                externalLinkUrl = link.href
            )
        }
    }

    fun confirmExternalLink() {
        val url = _uiState.value.externalLinkUrl
        val isBuy = _uiState.value.pendingBuyAction
        _uiState.update { it.copy(showExternalLinkDialog = false, externalLinkUrl = "", pendingBuyAction = false) }
        openInBrowser(url)
        if (isBuy) {
            _uiState.update { it.copy(userMessage = context.getString(R.string.opds_buy_hint)) }
        }
    }

    fun dismissExternalLinkDialog() {
        _uiState.update { it.copy(showExternalLinkDialog = false, externalLinkUrl = "", pendingBuyAction = false) }
    }

    fun openHtmlAcquisitionLink(url: String) {
        _uiState.update {
            it.copy(
                showExternalLinkDialog = true,
                externalLinkUrl = url
            )
        }
    }

    fun requestBuyConfirmation(url: String) {
        _uiState.update {
            it.copy(
                showExternalLinkDialog = true,
                externalLinkUrl = url,
                pendingBuyAction = true
            )
        }
    }

    fun navigateToFacet(facetUrl: String) {
        if (_uiState.value.isLoading) return
        pushToStack(_uiState.value.feed?.title ?: "", _uiState.value.currentUrl)
        fetchFeed(facetUrl)
    }

    fun selectSearchLanguage(langCode: String?) {
        _uiState.update { it.copy(selectedSearchLanguage = langCode) }
        val currentQuery = _uiState.value.searchQuery
        if (currentQuery.isNotBlank()) {
            search(currentQuery)
        }
    }

    fun navigateBack(): Boolean {
        val stack = _uiState.value.browseStack
        if (stack.isEmpty()) return false
        val previous = stack.last()
        _uiState.update { it.copy(browseStack = stack.dropLast(1)) }
        fetchFeed(previous.url, previous.title)
        return true
    }

    private fun sanitizeFeed(feed: OpdsFeed): OpdsFeed {
        val catalog = _uiState.value.catalog
        return if (catalog?.isPredefined == true && !catalog.supportsSearch && feed.searchUrl != null) {
            feed.copy(searchUrl = null, searchType = null)
        } else {
            feed
        }
    }

    private fun pushToStack(title: String, url: String) {
        _uiState.update { state ->
            state.copy(browseStack = state.browseStack + BrowseStackEntry(title, url))
        }
    }

    private fun urlsMatch(url1: String, url2: String): Boolean {
        return try {
            val u1 = java.net.URI(url1).normalize()
            val u2 = java.net.URI(url2).normalize()
            val schemeMatch = u1.scheme?.equals(u2.scheme, ignoreCase = true) ?: (u2.scheme == null)
            val hostMatch = u1.host?.equals(u2.host, ignoreCase = true) ?: (u2.host == null)
            val pathMatch = u1.path.trimEnd('/') == u2.path.trimEnd('/')
            val portMatch = u1.port == u2.port
            val queryMatch = u1.query == u2.query
            schemeMatch && hostMatch && pathMatch && portMatch && queryMatch
        } catch (_: Exception) {
            url1.trimEnd('/').equals(url2.trimEnd('/'), ignoreCase = true)
        }
    }

    fun search(query: String) {
        val catalog = _uiState.value.catalog ?: return
        if (catalog.isPredefined && !catalog.supportsSearch) return
        val searchUrl = catalog.searchUrl ?: _uiState.value.feed?.searchUrl ?: return

        val languageParam = _uiState.value.selectedSearchLanguage
        val effectiveSearchUrl = if (languageParam != null && catalog.predefinedId == "gutenberg") {
            val separator = if (searchUrl.contains("?")) "&" else "?"
            "$searchUrl${separator}languages=$languageParam"
        } else {
            searchUrl
        }

        launchFetch { gen ->
            _uiState.update { it.copy(isSearching = true, searchQuery = query) }
            searchUseCase(catalogId, effectiveSearchUrl, query).fold(
                onSuccess = { feed ->
                    if (gen != fetchGeneration) return@fold
                    val allEntries = feed.entries + feed.groups.flatMap { it.entries }
                    _uiState.update { state ->
                        state.copy(
                            feed = feed,
                            entries = allEntries.distinctBy { it.id },
                            facets = feed.facets,
                            isSearching = false,
                            hasLoadMoreError = false,
                            error = if (allEntries.isEmpty()) OpdsBrowseError.EmptySearch else null
                        )
                    }
                },
                onFailure = { error ->
                    if (gen != fetchGeneration) return@fold
                    _uiState.update { state ->
                        state.copy(
                            isSearching = false,
                            error = when (error) {
                                is OpdsAuthException -> OpdsBrowseError.AuthRequired
                                is OpdsContentTypeException -> OpdsBrowseError.ContentTypeError(error.url)
                                is OpdsParseException -> OpdsBrowseError.ParseError(error.message ?: "Parse error")
                                is OpdsNetworkException -> if (error.statusCode == 429) OpdsBrowseError.RateLimited else OpdsBrowseError.NetworkError(error.message ?: "Search failed")
                                else -> OpdsBrowseError.NetworkError(error.message ?: "Search failed")
                            }
                        )
                    }
                }
            )
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", error = null, hasLoadMoreError = false) }
        val catalog = _uiState.value.catalog
        if (catalog != null) {
            fetchFeed(catalog.url, catalog.name)
        }
    }

    private fun openInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: SecurityException) {
            Logger.e("OpdsBrowseViewModel::openInBrowser SecurityException: ${e.message}")
        }
    }

    fun retry() {
        val url = _uiState.value.currentUrl
        if (url.isNotBlank()) {
            fetchFeed(url)
        } else {
            loadCatalogAndFetch()
        }
    }

    fun onAuthDialogDismiss() {
        _uiState.update { it.copy(showAuthDialog = false) }
    }

    private fun updateCatalogSearchUrl(searchUrl: String) {
        viewModelScope.launch {
            val catalog = _uiState.value.catalog ?: return@launch
            if (catalog.isPredefined && !catalog.supportsSearch) return@launch
            manageCatalogUseCase.updateCatalog(
                catalog.copy(searchUrl = searchUrl, supportsSearch = true)
            )
        }
    }

    private fun updateCatalogLastAccessed() {
        viewModelScope.launch {
            val catalog = _uiState.value.catalog ?: return@launch
            manageCatalogUseCase.updateCatalog(
                catalog.copy(lastAccessedAt = System.currentTimeMillis())
            )
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun openCurrentUrlInBrowser() {
        val url = _uiState.value.currentUrl
        if (url.isNotBlank()) {
            openInBrowser(url)
        }
    }
}
