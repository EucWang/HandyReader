package com.wxn.reader.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.base.util.Coroutines
import com.wxn.reader.data.model.AppTheme
import com.wxn.reader.data.model.ThemePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.themePrefsDataStore by preferencesDataStore(name = "theme_prefs")

class ThemePreferencesUtil @Inject constructor(context: Context) {
    private val dataStore = context.themePrefsDataStore

    companion object {
        val APP_THEME = stringPreferencesKey("app_theme")

        val COLOR_SCHEME = stringPreferencesKey("color_scheme")

        val HOME_BACKGROUND_IMAGE = stringPreferencesKey("home_background_image")
    }

    val defaultPreferences = ThemePreferences(
        appTheme = AppTheme.SYSTEM,
        colorScheme = "Dynamic",
        homeBackgroundImage = "",
    )

    init {
        Coroutines.scope().launch {
            initializeDefaultPreferences()
        }
    }

    private suspend fun initializeDefaultPreferences() {
        val preferences = dataStore.data.firstOrNull()
        if (preferences == null) {
            dataStore.edit { prefs ->
                prefs[APP_THEME] = defaultPreferences.appTheme.name
                prefs[COLOR_SCHEME] = defaultPreferences.colorScheme
                prefs[HOME_BACKGROUND_IMAGE] = defaultPreferences.homeBackgroundImage

            }
        }
    }

    val themePrefsFlow: Flow<ThemePreferences> = dataStore.data.map { preferences ->
        ThemePreferences(
            appTheme = AppTheme.valueOf(preferences[APP_THEME] ?: defaultPreferences.appTheme.name),
            colorScheme = preferences[COLOR_SCHEME] ?: defaultPreferences.colorScheme,
            homeBackgroundImage = preferences[HOME_BACKGROUND_IMAGE]
                ?: defaultPreferences.homeBackgroundImage,
        )
    }

    suspend fun updateAppPreferences(newPreferences: ThemePreferences) {
        dataStore.edit { preferences ->
            preferences[APP_THEME] = newPreferences.appTheme.name
            preferences[COLOR_SCHEME] = newPreferences.colorScheme
            preferences[HOME_BACKGROUND_IMAGE] = newPreferences.homeBackgroundImage
        }
    }

    suspend fun updateTheme(newAppTheme: AppTheme, newColorScheme: String) {
        dataStore.edit { preferences ->
            preferences[APP_THEME] = newAppTheme.name
            preferences[COLOR_SCHEME] = newColorScheme
        }
    }

    suspend fun updateColorTheme(newColorScheme: String) {
        dataStore.edit { preferences ->
            preferences[COLOR_SCHEME] = newColorScheme
        }
    }

    suspend fun updateAppTheme(newAppTheme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[APP_THEME] = newAppTheme.name
        }
    }

    suspend fun updateBgImage(bgColorImg: String) {
        dataStore.edit { preferences ->
            preferences[HOME_BACKGROUND_IMAGE] = bgColorImg
        }
    }
}