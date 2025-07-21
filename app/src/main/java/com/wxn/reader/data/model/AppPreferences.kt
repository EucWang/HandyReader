package com.wxn.reader.data.model

import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.ReadingStatus

data class AppPreferences(
    //App Settings
    val isFirstLaunch: Boolean,
    val isAssetsBooksFetched: Boolean,
    val scanDirectories: Set<String>,
    val enablePdfSupport: Boolean,
    val language: String,

    //Ui settings
    val appTheme: AppTheme,
    val colorScheme: String,
    val homeLayout: Layout,
    val homeBackgroundImage: String,
    val gridCount: Int,
    val showEntries: Boolean,
    val showRating: Boolean,
    val showReadingStatus: Boolean,
    val showReadingDates: Boolean,
    val showFileTypeLabel: Boolean,

    val sortBy: SortOption,
    val sortOrder: SortOrder,

    val readingStatus: Set<ReadingStatus> = emptySet(),
    val fileTypes: Set<FileType> = emptySet(),

    // premium unlocked
    val isPremium: Boolean,
    val autoOpenLastRead : Boolean,
    val lastBookId : Long,
)


enum class SortOption {
    TITLE,
    AUTHOR,
    LAST_OPENED,
    LAST_ADDED,
    RATING,
    PROGRESSION,
}


enum class SortOrder {
    ASCENDING,
    DESCENDING
}


enum class Layout {
    Grid,
    CoverOnly,
    List,
}