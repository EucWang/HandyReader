package com.wxn.reader.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FontCatalog(
    val version: Int = 1,
    val cdnBaseUrl: String = "",
    val fonts: List<FontCatalogItem> = emptyList()
)

@Serializable
data class FontCatalogItem(
    val id: String,
    val displayName: String,
    val category: String,
    val language: String,
    val dirName: String,
    val variants: List<FontVariantItem> = emptyList()
)

@Serializable
data class FontVariantItem(
    val variant: String,
    val name: String,
    val url: String,
    val fileName: String,
    val localFileName: String
)

object FontCatalogParser {

    const val FONTS_CATALOG_URL =
        "https://cdn.jsdelivr.net/gh/EucWang/fonts@main/fonts_catalog.json"
    const val FONTS_CATALOG_CACHE_FILE = "fonts_catalog.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun parseFromJson(jsonString: String): FontCatalog {
        return json.decodeFromString<FontCatalog>(jsonString)
    }
}
