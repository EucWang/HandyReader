package com.wxn.reader.data.model.opds

data class OpdsLink(
    val href: String,
    val relSet: Set<String>,
    val type: String? = null,
    val title: String? = null,
    val length: Long? = null,
    val facetGroup: String? = null,
    val isActiveFacet: Boolean = false
) {
    companion object {
        val DOWNLOADABLE_MIME_TYPES = setOf(
            "application/epub+zip", "application/epub",
            "application/pdf",
            "application/x-mobipocket-ebook",
            "application/x-mobipocket-ebook-azw3", "application/vnd.amazon.mobi8-ebook",
            "application/x-fictionbook+xml",
            "text/plain", "text/html", "text/markdown",
            "audio/mpeg", "audio/mp4"
        )
    }

    constructor(href: String, rel: String, type: String? = null, title: String? = null)
            : this(href, rel.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet(), type, title)

    private fun hasRel(rel: String): Boolean = relSet.contains(rel)

    private fun hasRelContaining(substring: String): Boolean = relSet.any { it.contains(substring) }

    val isAcquisition: Boolean
        get() = hasRelContaining("http://opds-spec.org/acquisition")

    val isBuy: Boolean
        get() = hasRelContaining("http://opds-spec.org/acquisition/buy")

    val isSample: Boolean
        get() = hasRelContaining("http://opds-spec.org/acquisition/sample")

    val isBorrow: Boolean
        get() = hasRelContaining("http://opds-spec.org/acquisition/borrow")

    val isFreeAcquisition: Boolean
        get() = isAcquisition && !isBuy && !isSample && !isBorrow

    val isNavigation: Boolean
        get() = (hasRel("subsection") || hasRel("collection")
            || (type?.contains("atom") == true && !isAcquisition))
            && !isHtmlNavigation

    val isHtmlNavigation: Boolean
        get() = (hasRel("subsection") || hasRel("collection"))
            && type?.startsWith("text/html") == true

    val isSearch: Boolean
        get() = hasRel("search") || type?.contains("opensearch") == true

    val isFacet: Boolean
        get() = hasRelContaining("http://opds-spec.org/facet")

    val isNext: Boolean
        get() = hasRel("next")

    val isPrevious: Boolean
        get() = hasRel("prev") || hasRel("previous")

    val isSelf: Boolean
        get() = hasRel("self")

    val isImage: Boolean
        get() = type?.startsWith("image/") == true
            && (hasRelContaining("thumbnail") || hasRelContaining("image") || hasRelContaining("cover"))

    val isEnclosure: Boolean
        get() = hasRel("enclosure")

    val isHtmlContentLink: Boolean
        get() = type?.startsWith("text/html") == true && !isEnclosure

    val isDownloadableFormat: Boolean
        get() = type?.lowercase()?.substringBefore(";")?.trim() in DOWNLOADABLE_MIME_TYPES

    val isAtomAcquisition: Boolean
        get() = isDownloadableFormat
            && (isEnclosure || hasRel("related") || hasRel("alternate") || relSet.isEmpty())
            && !isHtmlContentLink
}
