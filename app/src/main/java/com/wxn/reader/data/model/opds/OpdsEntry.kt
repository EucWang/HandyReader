package com.wxn.reader.data.model.opds

import java.net.URI

data class OpdsEntry(
    val id: String,
    val title: String,
    val authors: List<String> = emptyList(),
    val summary: String? = null,
    val content: String? = null,
    val coverUrl: String? = null,
    val fullCoverUrl: String? = coverUrl,
    val published: String? = null,
    val updated: String? = null,
    val language: String? = null,
    val categories: List<String> = emptyList(),
    val links: List<OpdsLink> = emptyList(),
    val price: String? = null,
    val sourceFeedUrl: String = ""
) {
    val acquisitionLinks: List<OpdsLink>
        get() {
            val opdsAcquisition = links.filter { it.isAcquisition }
                .filterNot { it.isHtmlContentLink }
            if (opdsAcquisition.isNotEmpty()) return opdsAcquisition
            return links.filter { it.isAtomAcquisition }
                .distinctBy { it.href to it.type }
                .sortedBy { link ->
                    when {
                        link.type?.contains("pdf") == true -> 0
                        link.type?.contains("epub") == true -> 1
                        link.type?.startsWith("audio/") == true -> 2
                        link.type?.startsWith("text/") == true -> 4
                        else -> 3
                    }
                }
        }

    val acquisitionHtmlLinks: List<OpdsLink>
        get() = links.filter { it.isAcquisition && it.isHtmlContentLink }

    val freeAcquisitionLinks: List<OpdsLink>
        get() = links.filter { it.isFreeAcquisition }
            .filterNot { it.isHtmlContentLink }

    val buyLink: OpdsLink?
        get() = links.firstOrNull { it.isBuy }

    val sampleLink: OpdsLink?
        get() = links.firstOrNull { it.isSample }

    val borrowLink: OpdsLink?
        get() = links.firstOrNull { it.isBorrow }

    val navigationLinks: List<OpdsLink>
        get() = links.filter { it.isNavigation }

    val htmlLinks: List<OpdsLink>
        get() = links.filter { it.isHtmlNavigation }

    val externalNavigationLinks: List<OpdsLink>
        get() = links.filter { it.isNavigation && isExternalHost(it.href) }

    private fun isExternalHost(href: String): Boolean {
        val feedHost = try { URI(sourceFeedUrl).host } catch (_: Exception) { null } ?: return false
        val linkHost = try { URI(href).host } catch (_: Exception) { null } ?: return false
        if (feedHost == linkHost) return false
        return feedHost.removePrefix("www.") != linkHost.removePrefix("www.")
    }

    val primaryAction: EntryAction
        get() {
            val free = freeAcquisitionLinks.firstOrNull()
            val buy = buyLink
            val sample = sampleLink
            return when {
                free != null -> EntryAction.Download(free)
                buy != null -> EntryAction.Buy(buy)
                sample != null -> EntryAction.DownloadSample(sample)
                acquisitionLinks.isNotEmpty() -> EntryAction.Download(acquisitionLinks.first())
                acquisitionHtmlLinks.isNotEmpty() -> EntryAction.OpenInBrowser(acquisitionHtmlLinks.first())
                else -> EntryAction.Unavailable
            }
        }
}

sealed class EntryAction {
    data class Download(val link: OpdsLink) : EntryAction()
    data class Buy(val link: OpdsLink) : EntryAction()
    data class DownloadSample(val link: OpdsLink) : EntryAction()
    data class OpenInBrowser(val link: OpdsLink) : EntryAction()
    data object Unavailable : EntryAction()
}
