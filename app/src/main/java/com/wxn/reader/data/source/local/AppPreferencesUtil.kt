package com.wxn.reader.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.base.util.Coroutines
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.model.AppTheme
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.model.Layout
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.model.SortOption
import com.wxn.reader.data.model.SortOrder
import com.wxn.base.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.appPrefsDataStore by preferencesDataStore(name = "app_prefs")

class AppPreferencesUtil @Inject constructor(context: Context) {
    private val dataStore = context.appPrefsDataStore

    companion object {
        val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        val IS_ASSETS_BOOKS_FETCHED = booleanPreferencesKey("is_assets_books_fetched")
        val SCAN_DIRECTORY = stringSetPreferencesKey("scan_directory")
        val ENABLE_PDF_SUPPORT = booleanPreferencesKey("enable_pdf_support")
        val LANGUAGE = stringPreferencesKey("language")
        val APP_THEME = stringPreferencesKey("app_theme")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
        val HOME_BACKGROUND_IMAGE = stringPreferencesKey("home_background_image")
        val GRID_COUNT = intPreferencesKey("grid_count")
        val SHOW_ENTRIES = booleanPreferencesKey("show_entries")
        val SHOW_RATING = booleanPreferencesKey("show_rating")
        val SHOW_READING_STATUS = booleanPreferencesKey("show_reading_status")
        val SHOW_READING_DATES = booleanPreferencesKey("show_reading_dates")
        val SHOW_PDF_LABEL = booleanPreferencesKey("show_pdf_label")
        val SORT_BY = stringPreferencesKey("sort_by")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val READING_STATUS = stringSetPreferencesKey("reading_status")
        val FILE_TYPE = stringSetPreferencesKey("file_type")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")

        val AUTO_OPEN_LAST_READ = booleanPreferencesKey("auto_open_last_read")

        val LAST_OPEN_BOOK_ID = longPreferencesKey("last_open_book_id")

        // Default values
        val defaultPreferences = AppPreferences(
            isFirstLaunch = true,
            isAssetsBooksFetched = false,
            scanDirectories = emptySet(),
            enablePdfSupport = true,
            language = "",
            appTheme = AppTheme.SYSTEM,
            colorScheme = "Dynamic",
            homeLayout = Layout.Grid,
            homeBackgroundImage = "",
            gridCount = 4,
            showEntries = false,
            showRating = false,
            showReadingStatus = false,
            showReadingDates = false,
            showFileTypeLabel = true,
            sortBy = SortOption.LAST_ADDED,
            sortOrder = SortOrder.ASCENDING,
            readingStatus = emptySet(),
            fileTypes = emptySet(),
            isPremium = true,
            autoOpenLastRead = false,
            lastBookId = 0L,
        )
    }

    init {
        Coroutines.scope().launch {
            initializeDefaultPreferences()
        }
    }

    private suspend fun initializeDefaultPreferences() {
        val preferences = dataStore.data.first()
        if (preferences[IS_FIRST_LAUNCH] == null) {
            dataStore.edit { prefs ->
                prefs[IS_FIRST_LAUNCH] = defaultPreferences.isFirstLaunch
                prefs[IS_ASSETS_BOOKS_FETCHED] = defaultPreferences.isAssetsBooksFetched
                prefs[SCAN_DIRECTORY] = defaultPreferences.scanDirectories
                prefs[ENABLE_PDF_SUPPORT] = defaultPreferences.enablePdfSupport
                prefs[LANGUAGE] = defaultPreferences.language
                prefs[APP_THEME] = defaultPreferences.appTheme.name
                prefs[COLOR_SCHEME] = defaultPreferences.colorScheme
                prefs[HOME_LAYOUT] = defaultPreferences.homeLayout.name
                prefs[HOME_BACKGROUND_IMAGE] = defaultPreferences.homeBackgroundImage
                prefs[GRID_COUNT] = defaultPreferences.gridCount
                prefs[SHOW_ENTRIES] = defaultPreferences.showEntries
                prefs[SHOW_RATING] = defaultPreferences.showRating
                prefs[SHOW_READING_STATUS] = defaultPreferences.showReadingStatus
                prefs[SHOW_READING_DATES] = defaultPreferences.showReadingDates
                prefs[SHOW_PDF_LABEL] = defaultPreferences.showFileTypeLabel
                prefs[SORT_BY] = defaultPreferences.sortBy.name
                prefs[SORT_ORDER] = defaultPreferences.sortOrder.name
                prefs[READING_STATUS] = defaultPreferences.readingStatus.map { it.name }.toSet()
                prefs[FILE_TYPE] = defaultPreferences.fileTypes.map { it.name }.toSet()
                prefs[IS_PREMIUM] = defaultPreferences.isPremium
                prefs[AUTO_OPEN_LAST_READ] = defaultPreferences.autoOpenLastRead
                prefs[LAST_OPEN_BOOK_ID] = defaultPreferences.lastBookId
            }
        }
    }


    val appPrefsFlow: Flow<AppPreferences> = dataStore.data.map { preferences ->
        AppPreferences(
            isFirstLaunch = preferences[IS_FIRST_LAUNCH] ?: defaultPreferences.isFirstLaunch,
            isAssetsBooksFetched = preferences[IS_ASSETS_BOOKS_FETCHED] ?: defaultPreferences.isAssetsBooksFetched,
            scanDirectories = preferences[SCAN_DIRECTORY] ?: defaultPreferences.scanDirectories,
            enablePdfSupport = preferences[ENABLE_PDF_SUPPORT] ?: defaultPreferences.enablePdfSupport,
            language = preferences[LANGUAGE] ?: defaultPreferences.language,
            appTheme = AppTheme.valueOf(preferences[APP_THEME] ?: defaultPreferences.appTheme.name),
            colorScheme = preferences[COLOR_SCHEME] ?: defaultPreferences.colorScheme,
            homeLayout = Layout.valueOf(preferences[HOME_LAYOUT] ?: defaultPreferences.homeLayout.name),
            homeBackgroundImage = preferences[HOME_BACKGROUND_IMAGE] ?: defaultPreferences.homeBackgroundImage,
            gridCount = preferences[GRID_COUNT] ?: defaultPreferences.gridCount,
            showEntries = preferences[SHOW_ENTRIES] ?: defaultPreferences.showEntries,
            showRating = preferences[SHOW_RATING] ?: defaultPreferences.showRating,
            showReadingStatus = preferences[SHOW_READING_STATUS] ?: defaultPreferences.showReadingStatus,
            showReadingDates = preferences[SHOW_READING_DATES] ?: defaultPreferences.showReadingDates,
            showFileTypeLabel = preferences[SHOW_PDF_LABEL] ?: defaultPreferences.showFileTypeLabel,
            sortBy = SortOption.valueOf(preferences[SORT_BY] ?: defaultPreferences.sortBy.name),
            sortOrder = SortOrder.valueOf(preferences[SORT_ORDER] ?: defaultPreferences.sortOrder.name),
            readingStatus = preferences[READING_STATUS]?.map { ReadingStatus.valueOf(it) }?.toSet() ?: defaultPreferences.readingStatus,
            fileTypes = preferences[FILE_TYPE]?.map { FileType.valueOf(it) }?.toSet() ?: defaultPreferences.fileTypes,
            isPremium = preferences[IS_PREMIUM] ?: defaultPreferences.isPremium,
            autoOpenLastRead = preferences[AUTO_OPEN_LAST_READ] ?: defaultPreferences.autoOpenLastRead,
            lastBookId = preferences[LAST_OPEN_BOOK_ID] ?: defaultPreferences.lastBookId
        )
    }

    /***
     * 返回中文转换器类型：
     * 1： 中文jianti
     * 2 ： 中文繁体
     * 0  其他
     */
    suspend fun chineseConverterType(): Int {
        appPrefsFlow.firstOrNull()?.let {
            val code = it.language
            return when {
                code == "zh-CN" || code == "ZH-HANS" -> 1
                code == "zh-TW" || code == "zh-HK" || code == "ZH-HANT" -> 2
                else -> 0
            }
        }
        return 0
    }


    suspend fun updateAppPreferences(newPreferences: AppPreferences) {
        dataStore.edit { preferences ->
            Logger.d("AppPreferencesUtil:Updating preferences. isFirstLaunch: ${newPreferences.isFirstLaunch}," +
                    " Scan directories: ${newPreferences.scanDirectories}," +
                    " Premium status: ${newPreferences.isPremium}," +
                    "language:${newPreferences.language}")
            preferences[IS_FIRST_LAUNCH] = newPreferences.isFirstLaunch
            preferences[IS_ASSETS_BOOKS_FETCHED] = newPreferences.isAssetsBooksFetched
            preferences[SCAN_DIRECTORY] = newPreferences.scanDirectories
            preferences[ENABLE_PDF_SUPPORT] = newPreferences.enablePdfSupport
            preferences[LANGUAGE] = newPreferences.language
            preferences[APP_THEME] = newPreferences.appTheme.name
            preferences[COLOR_SCHEME] = newPreferences.colorScheme
            preferences[HOME_LAYOUT] = newPreferences.homeLayout.name
            preferences[HOME_BACKGROUND_IMAGE] = newPreferences.homeBackgroundImage
            preferences[GRID_COUNT] = newPreferences.gridCount
            preferences[SHOW_ENTRIES] = newPreferences.showEntries
            preferences[SHOW_RATING] = newPreferences.showRating
            preferences[SHOW_READING_STATUS] = newPreferences.showReadingStatus
            preferences[SHOW_READING_DATES] = newPreferences.showReadingDates
            preferences[SHOW_PDF_LABEL] = newPreferences.showFileTypeLabel
            preferences[SORT_BY] = newPreferences.sortBy.name
            preferences[SORT_ORDER] = newPreferences.sortOrder.name
            preferences[READING_STATUS] = newPreferences.readingStatus.map { it.name }.toSet()
            preferences[FILE_TYPE] = newPreferences.fileTypes.map { it.name }.toSet()
            preferences[IS_PREMIUM] = newPreferences.isPremium
            preferences[AUTO_OPEN_LAST_READ] = newPreferences.autoOpenLastRead
            preferences[LAST_OPEN_BOOK_ID] = newPreferences.lastBookId
        }
    }

    suspend fun resetLayoutPreferences() {
        dataStore.edit { preferences ->
            preferences[HOME_LAYOUT] = defaultPreferences.homeLayout.name
            preferences[HOME_BACKGROUND_IMAGE] = defaultPreferences.homeBackgroundImage
            preferences[GRID_COUNT] = defaultPreferences.gridCount
            preferences[SHOW_ENTRIES] = defaultPreferences.showEntries
            preferences[SHOW_RATING] = defaultPreferences.showRating
            preferences[SHOW_READING_STATUS] = defaultPreferences.showReadingStatus
            preferences[SHOW_READING_DATES] = defaultPreferences.showReadingDates
            preferences[SHOW_PDF_LABEL] = defaultPreferences.showFileTypeLabel
        }
    }
}



