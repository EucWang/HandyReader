package com.wxn.reader.data.model

data class GuidePreferences (
    val isHomeFabGuideShown: Boolean = false,
    val isSearchFabGuideShown: Boolean = false,
    val needsLegacyCacheCleanup: Boolean = true,
    val hasOpdsFirstDownloadChoiceMade: Boolean = false,
    val opdsDownloadLocation: String = "app_internal",
    val opdsSafTreeUri: String = ""
)
