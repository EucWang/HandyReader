package com.wxn.reader.data.remote.opds

import android.content.Context
import android.util.Xml
import com.wxn.reader.data.model.opds.OpdsEntry
import com.wxn.reader.data.model.opds.OpdsFacet
import com.wxn.reader.data.model.opds.OpdsFeed
import com.wxn.reader.data.model.opds.OpdsGroup
import com.wxn.reader.data.model.opds.OpdsLink
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.net.URI

import android.util.Base64
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import java.io.File

object OpdsFeedParser {

    private const val NS_ATOM = "http://www.w3.org/2005/Atom"
    private const val NS_OPDS = "http://opds-spec.org/2010/catalog"
    private const val NS_SEARCH = "http://a9.com/-/spec/opensearch/1.1/"
    private const val NS_DC = "http://purl.org/dc/terms/"
    private const val NS_THR = "http://purl.org/syndication/thread/1.0"

    private val RESERVED_PREFIXES = setOf("xml", "xmlns")

    fun parse(
        context: Context,
        xml: String,
        catalogId: Long = 0,
        baseUrl: String? = null
    ): OpdsFeed {
        return try {
            val normalized = normalizeNamespaces(xml)
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(normalized))
            readFeed(context, parser, catalogId, baseUrl)
        } catch (e: XmlPullParserException) {
            throw OpdsParseException("Failed to parse OPDS feed: ${e.message}", e)
        } catch (e: IOException) {
            throw OpdsParseException("IO error during parsing: ${e.message}", e)
        }
    }

    fun parse(
        context: Context,
        inputStream: InputStream,
        catalogId: Long = 0,
        baseUrl: String? = null
    ): OpdsFeed {
        return try {
            val xml = inputStream.bufferedReader().use { it.readText() }
            val normalized = normalizeNamespaces(xml)
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(normalized))
            readFeed(context, parser, catalogId, baseUrl)
        } catch (e: XmlPullParserException) {
            throw OpdsParseException("Failed to parse OPDS feed: ${e.message}", e)
        } catch (e: IOException) {
            throw OpdsParseException("IO error during parsing: ${e.message}", e)
        }
    }

    internal fun normalizeNamespaces(xml: String): String {
        val declared = Regex("""xmlns:(\w+)\s*=""")
            .findAll(xml)
            .mapTo(mutableSetOf()) { it.groupValues[1] }

        val usedInTags = Regex("""<\s*/?\s*([a-zA-Z]\w*):""")
            .findAll(xml)
            .mapTo(mutableSetOf()) { it.groupValues[1] }

        val usedInAttrs = Regex("""\s([a-zA-Z]\w*):\w+\s*=""")
            .findAll(xml)
            .mapTo(mutableSetOf()) { it.groupValues[1] }

        val undeclared = (usedInTags + usedInAttrs) - declared - RESERVED_PREFIXES
        if (undeclared.isEmpty()) return xml

        Logger.d("OpdsFeedParser: Normalizing undeclared namespace prefixes: $undeclared")

        val firstElement = Regex("""<[a-zA-Z]""").find(xml) ?: return xml
        val firstElementStart = firstElement.range.first

        var tagEndIndex = -1
        var inQuote: Char? = null
        for (i in (firstElementStart + 1) until xml.length) {
            val ch = xml[i]
            if (inQuote != null) {
                if (ch == inQuote) inQuote = null
                continue
            }
            when (ch) {
                '"', '\'' -> inQuote = ch
                '>' -> { tagEndIndex = i; break }
            }
        }
        if (tagEndIndex == -1) return xml

        val declarations = undeclared.joinToString(" ") {
            """xmlns:$it="urn:opds:temp:$it""""
        }
        return buildString(xml.length + declarations.length + 1) {
            append(xml, 0, tagEndIndex)
            append(' ')
            append(declarations)
            append(xml, tagEndIndex, xml.length)
        }
    }

    private fun upgradeToHttps(url: String): String {
        if (url.startsWith("http://")) {
            val host = url.substring(7).takeWhile { it != '/' && it != ':' }
            if (isPrivateHost(host)) return url
            return "https://" + url.substring(7)
        }
        return url
    }

    private fun isPrivateHost(host: String): Boolean {
        val ipv4 = host.split(".")
        if (ipv4.size == 4 && ipv4.all { it.toIntOrNull() != null }) {
            val first = ipv4[0].toInt()
            val second = ipv4[1].toInt()
            return first == 127 || first == 10 ||
                    (first == 172 && second in 16..31) ||
                    (first == 192 && second == 168)
        }
        return host.equals("localhost", ignoreCase = true) ||
                host.endsWith(".local", ignoreCase = true)
    }

    fun resolveUrl(context: Context, base: String?, relative: String): String {
        if (relative.startsWith("http://") ||
            relative.startsWith("https://") ||
            relative.startsWith("data:")) return upgradeToHttps(relative)
        if (base.isNullOrBlank()) return relative
        return try {
            val baseUri = URI(upgradeToHttps(base))
            val resolved = baseUri.resolve(URI(relative))
            upgradeToHttps(resolved.toString())
        } catch (_: Exception) {
            relative
        }
    }

    private fun readFeed(
        context: Context,
        parser: XmlPullParser,
        catalogId: Long,
        baseUrl: String?
    ): OpdsFeed {
        var title = ""
        var subtitle: String? = null
        var iconUrl: String? = null
        var selfUrl = ""
        var nextUrl: String? = null
        var prevUrl: String? = null
        var searchUrl: String? = null
        var searchType: String? = null
        var totalResults: Int? = null
        var osStartIndex: Int? = null
        var osItemsPerPage: Int? = null
        val facets = mutableListOf<OpdsFacet>()
        val entries = mutableListOf<OpdsEntry>()
        val links = mutableListOf<OpdsLink>()
        val groups = mutableListOf<OpdsGroup>()
        val effectiveBaseUrl = baseUrl ?: ""

        while (parser.eventType != XmlPullParser.START_TAG) {
            if (parser.next() == XmlPullParser.END_DOCUMENT) {
                throw OpdsParseException("Unexpected end of document, expected <feed>")
            }
        }
        if (parser.name.equals("feed", ignoreCase = true).not()) {
            throw OpdsParseException("Expected <feed> root element, found <${parser.name}>")
        }
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when {
                isTag(parser, "title") -> title = readText(parser)
                isTag(parser, "subtitle") -> subtitle = readText(parser)
                isTag(parser, "icon") -> iconUrl = readText(parser)
                isTag(parser, "link") -> {
                    val link = readLink(context, parser, effectiveBaseUrl)
                    links.add(link)
                }

                isTag(parser, "entry") -> {
                    entries.add(readEntry(context, parser, effectiveBaseUrl))
                }

                isTag(parser, "collection") || isTag(parser, "group") -> {
                    groups.add(readGroup(context, parser, effectiveBaseUrl))
                }

                isTag(parser, "totalResults") -> {
                    totalResults = readText(parser).trim().toIntOrNull()
                }

                isTag(parser, "startIndex") -> {
                    osStartIndex = readText(parser).trim().toIntOrNull()
                }

                isTag(parser, "itemsPerPage") -> {
                    osItemsPerPage = readText(parser).trim().toIntOrNull()
                }

                else -> skip(parser)
            }
        }

        for (link in links) {
            when {
                link.isSelf -> selfUrl = link.href
                link.isNext -> nextUrl = link.href
                link.isPrevious -> prevUrl = link.href
                link.isSearch -> {
                    searchUrl = link.href
                    searchType = link.type
                }

                link.isFacet -> {
                    facets.add(
                        OpdsFacet(
                            title = link.title ?: "",
                            href = link.href,
                            group = link.facetGroup ?: link.title ?: "",
                            isActive = link.isActiveFacet
                        )
                    )
                }
            }
        }

        if (nextUrl == null && totalResults != null && osStartIndex != null && osItemsPerPage != null) {
            synthesizePageUrl(selfUrl.ifBlank { effectiveBaseUrl }, osStartIndex, osItemsPerPage, totalResults)
                ?.let { nextUrl = it }
        }

        if (prevUrl == null && osStartIndex != null && osItemsPerPage != null && osStartIndex > osItemsPerPage) {
            synthesizePrevUrl(selfUrl.ifBlank { effectiveBaseUrl }, osStartIndex, osItemsPerPage)
                ?.let { prevUrl = it }
        }

        val isNavigation = entries.isNotEmpty() && entries.any { it.navigationLinks.isNotEmpty() }

        return OpdsFeed(
            title = title,
            subtitle = subtitle,
            iconUrl = iconUrl?.let {
                if (it.startsWith("data:"))
                    saveDataUriToCache(context, it) ?: it
                else
                    resolveUrl(context, effectiveBaseUrl, it)
           },
            selfUrl = selfUrl,
            nextUrl = nextUrl,
            prevUrl = prevUrl,
            searchUrl = searchUrl,
            searchType = searchType,
            facets = facets,
            entries = entries,
            groups = groups,
            catalogId = catalogId,
            isNavigation = isNavigation
        )
    }

    private fun readGroup(context: Context, parser: XmlPullParser, baseUrl: String): OpdsGroup {
        var title = ""
        var href: String? = null
        val links = mutableListOf<OpdsLink>()
        val entries = mutableListOf<OpdsEntry>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when {
                isTag(parser, "title") -> title = readText(parser)
                isTag(parser, "link") -> {
                    val link = readLink(context, parser, baseUrl)
                    links.add(link)
                    if (href == null && link.isNavigation) href = link.href
                }

                isTag(parser, "entry") -> {
                    entries.add(readEntry(context, parser, baseUrl))
                }

                else -> skip(parser)
            }
        }

        return OpdsGroup(
            title = title,
            href = href ?: links.firstOrNull()?.href ?: "",
            entries = entries
        )
    }

    private fun readEntry(context: Context, parser: XmlPullParser, baseUrl: String): OpdsEntry {
        var id = ""
        var title = ""
        var updated: String? = null
        var published: String? = null
        var summary: String? = null
        var content: String? = null
        var language: String? = null
        var coverUrl: String? = null
        val authors = mutableListOf<String>()
        val categories = mutableListOf<String>()
        val links = mutableListOf<OpdsLink>()

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            when {
                isTag(parser, "id") -> id = readText(parser)
                isTag(parser, "title") -> title = readText(parser)
                isTag(parser, "updated") -> updated = readText(parser)
                isTag(parser, "published") -> published = readText(parser)
                isTag(parser, "summary") -> summary = readText(parser)
                isTag(parser, "content") -> content = readRawContent(parser)
                isTag(parser, "language") -> language = readText(parser)
                isTag(parser, "author") -> {
                    authors.add(readAuthor(parser))
                }

                isTag(parser, "category") -> {
                    val label = parser.getAttributeValue(null, "label")
                        ?: parser.getAttributeValue(null, "term")
                    if (label != null) categories.add(label)
                    skip(parser)
                }

                isTag(parser, "link") -> {
                    val link = readLink(context, parser, baseUrl)
                    links.add(link)
                }

                else -> skip(parser)
            }
        }

        val bestCover = links
            .filter { it.isImage }
            .minByOrNull {
                when {
                    it.relSet.any { r -> r.contains("thumbnail") } -> 0
                    it.relSet.any { r -> r.contains("cover") } -> 1
                    else -> 2
                }
            }
            ?.href ?.let {
                if (it.startsWith("data:")) saveDataUriToCache(context, it) ?: it
                else it
            } ?: coverUrl

        val bestFullCover = links
            .filter { it.isImage && !it.relSet.any { r -> r.contains("thumbnail") } }
            .minByOrNull {
                when {
                    it.relSet.any { r -> r.contains("cover") } -> 0
                    else -> 1
                }
            }
            ?.href?.let {
                if (it.startsWith("data:")) saveDataUriToCache(context, it) ?: it
                else it
            } ?: bestCover

        return OpdsEntry(
            id = id,
            title = title,
            authors = authors,
            summary = summary,
            content = content,
            coverUrl = bestCover,
            fullCoverUrl = bestFullCover,
            published = published,
            updated = updated,
            language = language,
            categories = categories,
            links = links,
            price = links.firstOrNull { it.isBuy }?.title,
            sourceFeedUrl = baseUrl
        )
    }

    private fun readAuthor(parser: XmlPullParser): String {
        var name = ""
        parser.require(XmlPullParser.START_TAG, null, "author")
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            if (isTag(parser, "name")) {
                name = readText(parser)
            } else {
                skip(parser)
            }
        }
        return name
    }

    private fun readLink(context: Context, parser: XmlPullParser, baseUrl: String): OpdsLink {
        val rawHref = parser.getAttributeValue(null, "href") ?: ""
        val href = resolveUrl(context, baseUrl, rawHref)
        val rel = parser.getAttributeValue(null, "rel") ?: ""
        val type = parser.getAttributeValue(null, "type")
        val title = parser.getAttributeValue(null, "title")
        val facetGroup = parser.getAttributeValue(NS_OPDS, "facetGroup")
            ?: parser.getAttributeValue(null, "facetGroup")
        val activeFacet = parser.getAttributeValue(NS_OPDS, "activeFacet")
            ?: parser.getAttributeValue(null, "activeFacet")
        val length = parser.getAttributeValue(null, "length")?.toLongOrNull()

        val effectiveRel = buildString {
            append(rel)
            val opdsPrice = parser.getAttributeValue(NS_OPDS, "price")
                ?: parser.getAttributeValue(null, "price")
            if (!opdsPrice.isNullOrBlank()) {
                if (isNotEmpty()) append(" ")
                append("http://opds-spec.org/acquisition/buy")
            }
        }

        skipToEndTag(parser)

        return OpdsLink(
            href = href,
            relSet = effectiveRel.split("\\s+".toRegex()).filter { it.isNotBlank() }.toSet(),
            type = type,
            title = title,
            length = length,
            facetGroup = facetGroup,
            isActiveFacet = activeFacet == "true"
        )
    }

    private fun readText(parser: XmlPullParser): String {
        val result = StringBuilder()
        while (parser.next() != XmlPullParser.END_TAG) {
            when (parser.eventType) {
                XmlPullParser.TEXT -> result.append(parser.text)
                XmlPullParser.START_TAG -> result.append(readText(parser))
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("p", ignoreCase = true) ||
                        parser.name.equals("br", ignoreCase = true) ||
                        parser.name.equals("hr", ignoreCase = true) ||
                        parser.name.equals("li", ignoreCase = true) ||
                        parser.name.equals("div", ignoreCase = true) ||
                        parser.name.equals("span", ignoreCase = true) ||
                        parser.name.equals("i", ignoreCase = true) ||
                        parser.name.equals("u", ignoreCase = true) ||
                        parser.name.equals("pre", ignoreCase = true)) {
                        result.append("\n")
                    }
                }
            }
        }
        return result.toString().trim()
    }

    private fun isTag(parser: XmlPullParser, tagName: String): Boolean {
        return parser.name.equals(tagName, ignoreCase = true)
    }

    private fun synthesizePageUrl(
        baseUrl: String,
        startIndex: Int,
        itemsPerPage: Int,
        totalResults: Int
    ): String? {
        val nextStart = startIndex + itemsPerPage
        if (nextStart >= totalResults) return null
        return replaceOrAppendQuery(baseUrl, "start", nextStart.toString())
    }

    private fun synthesizePrevUrl(
        baseUrl: String,
        startIndex: Int,
        itemsPerPage: Int
    ): String? {
        val prevStart = startIndex - itemsPerPage
        if (prevStart < 0) return null
        return replaceOrAppendQuery(baseUrl, "start", prevStart.toString())
    }

    private fun replaceOrAppendQuery(url: String, key: String, value: String): String {
        val pattern = Regex("([?&])$key=[^&]*")
        return if (pattern.containsMatchIn(url)) {
            pattern.replace(url, "\$1$key=$value")
        } else {
            val sep = if ("?" in url) "&" else "?"
            "$url$sep$key=$value"
        }
    }

    private fun skip(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    private fun skipToEndTag(parser: XmlPullParser) {
        if (parser.eventType != XmlPullParser.START_TAG) return
        var depth = 1
        while (depth != 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> return
            }
        }
    }

    fun buildSearchUrl(
        searchTemplate: String,
        query: String,
        startIndex: Int = 0,
        count: Int = 50
    ): String {
        val hasPlaceholder = searchTemplate.contains("{searchTerms}")
        var url = searchTemplate
            .replace("{searchTerms}", java.net.URLEncoder.encode(query, "UTF-8"))
            .replace("{startIndex}", startIndex.toString())
            .replace("{startPage}", ((startIndex / count) + 1).toString())
            .replace("{count}", count.toString())
        if (!hasPlaceholder) {
            val separator = if (url.contains("?")) "&" else "?"
            url = "${url}${separator}q=${java.net.URLEncoder.encode(query, "UTF-8")}"
        }
        return url
    }


    private fun saveDataUriToCache(context: Context, dataUri: String): String? {
        if (!dataUri.startsWith("data:image/")) return null
        val regex = Regex("^data:image/(\\w+);base64,(.+)$")
        val match = regex.matchEntire(dataUri) ?: return null
        val ext = match.groupValues[1].let {
            when (it) { "png" -> "png"; "jpeg" -> "jpg"; "gif" -> "gif"
                "webp" -> "webp"; "bmp" -> "bmp"; else -> return null }
        }
        val base64Data = match.groupValues[2]
        val bytes = try { Base64.decode(base64Data, Base64.DEFAULT) } catch (_: Exception) { return null }

        val dir = PathUtil.getCachedOpdsImagesDir(context)
        val file = File(dir, "${dataUri.hashCode().toUInt()}.$ext")
        if (!file.exists()) {
            file.writeBytes(bytes)
            trimCache(dir, 30)
        }
        return file.toURI().toString()
    }

    /***
     * 文件超过 100 时按最后修改时间清理最旧的
     */
    private fun trimCache(dir: File, maxFiles: Int) {
        val files = dir.listFiles() ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }
            .take(files.size - maxFiles)
            .forEach { it.delete() }
    }

    private fun readRawContent(parser: XmlPullParser): String {
        val result = StringBuilder()
        var depth = 0
        while (true) {
            val event = parser.next()
            when (event) {
                XmlPullParser.START_TAG -> {
                    depth++
                    result.append("<").append(parser.name)
                    for (i in 0 until parser.attributeCount) {
                        result.append(" ${parser.getAttributeName(i)}=\"${parser.getAttributeValue(i)}\"")
                    }
                    result.append(">")
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    result.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    if (depth == 0) break
                    depth--
                    result.append("</").append(parser.name).append(">")
                }
            }
        }
        return result.toString().trim()
    }
}
