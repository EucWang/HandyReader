package com.wxn.reader.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.reader.data.model.GuidePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.guidePrefsDataStore by preferencesDataStore(name = "guide_prefs")

class GuidePrefUtil @Inject constructor(context: Context) {
    private val dataStore = context.guidePrefsDataStore


    companion object {
        val IS_HOME_FAB_GUIDE_SHOWN = booleanPreferencesKey("is_home_fab_guide_shown")
        val IS_SEARCH_FAB_GUIDE_SHOWN = booleanPreferencesKey("is_search_fab_guide_shown")
        val IS_PER_BOOK_OVERRIDE_TIP_SHOWN = booleanPreferencesKey("is_per_book_override_tip_shown")
        val NEEDS_LEGACY_CACHE_CLEANUP = booleanPreferencesKey("needs_legacy_cache_cleanup")
        val OPDS_FIRST_CHOICE_MADE = booleanPreferencesKey("opds_first_choice_made")
        val OPDS_DOWNLOAD_LOCATION = stringPreferencesKey("opds_download_location")
        val OPDS_SAF_TREE_URI = stringPreferencesKey("opds_saf_tree_uri")

        val defaultPreferences = GuidePreferences()
    }

    val guidePrefsFlow: Flow<GuidePreferences> = dataStore.data.map { preferences ->
        GuidePreferences(
            isHomeFabGuideShown = preferences[IS_HOME_FAB_GUIDE_SHOWN] ?: false,
            isSearchFabGuideShown = preferences[IS_SEARCH_FAB_GUIDE_SHOWN] ?: false,
            needsLegacyCacheCleanup = preferences[NEEDS_LEGACY_CACHE_CLEANUP] ?: true,
            hasOpdsFirstDownloadChoiceMade = preferences[OPDS_FIRST_CHOICE_MADE] ?: false,
            opdsDownloadLocation = preferences[OPDS_DOWNLOAD_LOCATION] ?: "app_internal",
            opdsSafTreeUri = preferences[OPDS_SAF_TREE_URI] ?: ""
        )
    }

    suspend fun isHomeFabGuideShown(): Boolean {
        return guidePrefsFlow.first().isHomeFabGuideShown
    }

    suspend fun setHomeFabGuideShown() {
        dataStore.edit { preferences ->
            preferences[IS_HOME_FAB_GUIDE_SHOWN] = true
        }
    }

    suspend fun isSearchFabGuideShown(): Boolean {
        return guidePrefsFlow.first().isSearchFabGuideShown
    }

    suspend fun setSearchFabGuideShown() {
        dataStore.edit { preferences ->
            preferences[IS_SEARCH_FAB_GUIDE_SHOWN] = true
        }
    }

    /** ★ v11 per-book：首次开启"仅本书生效"时是否已展示过提示。 */
    suspend fun isPerBookOverrideTipShown(): Boolean {
        return dataStore.data.first()[IS_PER_BOOK_OVERRIDE_TIP_SHOWN] ?: false
    }

    /** ★ v11 per-book：标记首次开启提示已展示。 */
    suspend fun setPerBookOverrideTipShown() {
        dataStore.edit { preferences ->
            preferences[IS_PER_BOOK_OVERRIDE_TIP_SHOWN] = true
        }
    }

    suspend fun needsLegacyCacheCleanup(): Boolean {
        return guidePrefsFlow.first().needsLegacyCacheCleanup
    }

    suspend fun setLegacyCacheCleaned() {
        dataStore.edit { preferences ->
            preferences[NEEDS_LEGACY_CACHE_CLEANUP] = false
        }
    }

    suspend fun hasOpdsFirstDownloadChoiceMade(): Boolean {
        return guidePrefsFlow.first().hasOpdsFirstDownloadChoiceMade
    }

    suspend fun setOpdsFirstDownloadChoiceMade() {
        dataStore.edit { preferences ->
            preferences[OPDS_FIRST_CHOICE_MADE] = true
        }
    }

    suspend fun getOpdsDownloadLocation(): String {
        return guidePrefsFlow.first().opdsDownloadLocation
    }

    suspend fun getOpdsSafTreeUri(): String {
        return guidePrefsFlow.first().opdsSafTreeUri
    }

    suspend fun setOpdsDownloadPrefs(location: String, safTreeUri: String) {
        dataStore.edit { preferences ->
            preferences[OPDS_DOWNLOAD_LOCATION] = location
            preferences[OPDS_SAF_TREE_URI] = safTreeUri
        }
    }
}
