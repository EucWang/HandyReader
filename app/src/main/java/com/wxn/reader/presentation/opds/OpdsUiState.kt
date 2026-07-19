package com.wxn.reader.presentation.opds

import com.wxn.reader.data.dto.OpdsCatalogEntity
import com.wxn.reader.data.model.opds.OpdsEntry
import com.wxn.reader.data.model.opds.OpdsFacet
import com.wxn.reader.data.model.opds.OpdsFeed

data class OpdsCatalogListUiState(
    val predefinedCatalogs: List<OpdsCatalogEntity> = emptyList(),
    val customCatalogs: List<OpdsCatalogEntity> = emptyList(),
    val isLoading: Boolean = false,
    val syncError: Boolean = false,
    val userMessage: String? = null
) {
    val catalogs: List<OpdsCatalogEntity>
        get() = predefinedCatalogs + customCatalogs
}

data class OpdsBrowseUiState(
    val feed: OpdsFeed? = null,
    val entries: List<OpdsEntry> = emptyList(),
    val facets: List<OpdsFacet> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val browseStack: List<BrowseStackEntry> = emptyList(),
    val catalog: OpdsCatalogEntity? = null,
    val error: OpdsBrowseError? = null,
    val userMessage: String? = null,
    val showAuthDialog: Boolean = false,
    val currentUrl: String = "",
    val showExternalLinkDialog: Boolean = false,
    val externalLinkUrl: String = "",
    val selectedEntry: OpdsEntry? = null,
    val pendingBuyAction: Boolean = false,
    val selectedSearchLanguage: String? = null,
    val hasLoadMoreError: Boolean = false
)

data class BrowseStackEntry(
    val title: String,
    val url: String
)

sealed class OpdsBrowseError {
    data class NetworkError(val message: String) : OpdsBrowseError()
    data object AuthRequired : OpdsBrowseError()
    data class ParseError(val message: String) : OpdsBrowseError()
    data object EmptySearch : OpdsBrowseError()
    data object RateLimited : OpdsBrowseError()
    data class ContentTypeError(val url: String) : OpdsBrowseError()
}

data class OpdsBookUiState(
    val entry: OpdsEntry? = null,
    val catalogId: Long = 0L,
    val isLoading: Boolean = true,
    val error: String? = null,
    val downloadState: com.wxn.reader.util.download.DownloadState? = null,
    val isImporting: Boolean = false,
    val importedBookId: Long = 0L,
    val importedFilePath: String? = null,
    val importedFileType: String? = null,
    val selectedFormatIndex: Int = 0,
    val showCorruptedDialog: Boolean = false,
    val showFirstDownloadDialog: Boolean = false,
    val importedBookTitle: String? = null,
    val importedBookAuthor: String? = null,
)

data class AddCatalogDialogState(
    val isVisible: Boolean = false,
    val isEditing: Boolean = false,
    val editCatalogId: Long = 0,
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val isValidating: Boolean = false,
    val validationResult: String? = null,
    val isError: Boolean = false
)
