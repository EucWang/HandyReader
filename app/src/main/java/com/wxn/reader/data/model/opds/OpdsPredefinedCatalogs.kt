package com.wxn.reader.data.model.opds

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class OpdsPredefinedCatalogs(
    val version: Int = 1,
    val updated: String = "",
    val catalogs: List<PredefinedCatalogItem> = emptyList()
)

@Serializable
data class PredefinedCatalogItem(
    val id: String,
    val name: String,
    val url: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val category: String = "public",
    val supportsSearch: Boolean = false,
    val language: String? = null,
)

object OpdsCatalogConfigParser {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun parse(jsonString: String): OpdsPredefinedCatalogs {
        return json.decodeFromString<OpdsPredefinedCatalogs>(jsonString)
    }
    val FALLBACK_CATALOGS = OpdsPredefinedCatalogs(
        version = 5,
        updated = "2026-05-29 12:22:00",
        catalogs = listOf(
            PredefinedCatalogItem(
                id = "gutenberg",
                name = "Project Gutenberg",
                url = "https://m.gutenberg.org/ebooks.opds/?format=opds",
                description = "Over 70,000 free eBooks in the public domain",
                category = "public",
                supportsSearch = true,
                language = "en"
            ),
            PredefinedCatalogItem(
                id = "manybooks",
                name = "manybooks",
                url = "https://manybooks.net/opds",
                description = "50000+ Free eBooks in the Genres you Love",
                category = "public",
                supportsSearch = false,
                language = "en"
            ),
            PredefinedCatalogItem(
                id = "unglue",
                name = "Unglue",
                url = "https://www.unglue.it/api/opds/",
                description = "Unglue.it is a place for individuals and institutions to join together in support of free ebooks.",
                category = "public",
                supportsSearch = true,
                language = "en"
            ),
            PredefinedCatalogItem(
                id = "gallica",
                name = "Gallica (BnF)",
                url = "https://gallica.bnf.fr/opds",
                description = "French National Library — millions of public domain eBooks",
                category = "public",
                supportsSearch = false,
                language = "fr"
            ),
            PredefinedCatalogItem(
                id = "lzzy",
                "Lzzy's eBooks Library",
                "https://ebooks.qumran.org/opds/",
                description = "Sicher bin ich nicht der einzige, der ständig gutes Futter für seinen eBook-Reader sucht. Legal natürlich, und möglichst günstig.",
                category = "public",
                supportsSearch = true,
                language = "de"
            ),
            PredefinedCatalogItem(
                id = "Wenyuange",
                name = "WenYuanGe",
                url = "https://wenyuange.org/opds/",
                description = "WenYuanGe is built by Calibre，thanks for the open source power.",
                category = "public",
                supportsSearch = false,
                language = "zh"
            )
        )
    )
}
