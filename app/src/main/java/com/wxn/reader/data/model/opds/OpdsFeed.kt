package com.wxn.reader.data.model.opds

data class OpdsFeed(
    val title: String,
    val subtitle: String? = null,
    val iconUrl: String? = null,
    val selfUrl: String = "",
    val nextUrl: String? = null,
    val prevUrl: String? = null,
    val searchUrl: String? = null,
    val searchType: String? = null,
    val facets: List<OpdsFacet> = emptyList(),
    val entries: List<OpdsEntry> = emptyList(),
    val groups: List<OpdsGroup> = emptyList(),
    val catalogId: Long = 0,
    val isNavigation: Boolean = false
) {
    val hasMore: Boolean
        get() = nextUrl != null

    val supportsSearch: Boolean
        get() = searchUrl != null

    val isOpenSearchDescription: Boolean
        get() = searchType?.contains("opensearchdescription") == true

    val hasFacets: Boolean
        get() = facets.isNotEmpty()

    val hasGroups: Boolean
        get() = groups.isNotEmpty()

    val isAcquisitionFeed: Boolean
        get() = entries.any { it.acquisitionLinks.isNotEmpty() }

    val navigationEntries: List<OpdsEntry>
        get() = entries.filter { it.navigationLinks.isNotEmpty() && it.acquisitionLinks.isEmpty() }
}
