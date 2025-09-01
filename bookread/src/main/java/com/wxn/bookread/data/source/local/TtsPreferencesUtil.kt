package com.wxn.bookread.data.source.local

import android.content.Context
import androidx.core.app.LocaleManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.bookread.data.model.preference.TtsPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.ttsPreferencesDataStore by preferencesDataStore(name = "tts_preferences")

class TtsPreferencesUtil @Inject constructor(
    val context: Context
) {
    private val dataStore = context.ttsPreferencesDataStore

    companion object {
        val SPEED = floatPreferencesKey("speed")
        val PITCH = floatPreferencesKey("spitch")
        val LANGUAGE = stringPreferencesKey("language")

        val defaultPreferences = TtsPreferences(
            localeCode = "" , //"en",
            speed = 1.0f,
            pitch = 1.0f
        )
    }

    val ttsPreferencesFlow: Flow<TtsPreferences> = dataStore.data.map { preferences ->
        if (defaultPreferences.localeCode.isEmpty()) {
            val systemLocale = LocaleManagerCompat.getSystemLocales(context).get(0)
            if (systemLocale != null) {
                defaultPreferences.localeCode = systemLocale.toLanguageTag()
            }
        }

        TtsPreferences(
            localeCode = preferences[LANGUAGE] ?: defaultPreferences.localeCode,
            speed = preferences[SPEED] ?: defaultPreferences.speed,
            pitch = preferences[PITCH] ?: defaultPreferences.pitch
        )
    }

    suspend fun updatePreferences(newPreferences: TtsPreferences) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE] = newPreferences.localeCode
            preferences[SPEED] = newPreferences.speed
            preferences[PITCH] = newPreferences.pitch
        }
    }

    suspend fun resetTtsPreferences() {
        dataStore.edit { preferences ->
            preferences[LANGUAGE] = defaultPreferences.localeCode
            preferences[SPEED] = defaultPreferences.speed
            preferences[PITCH] = defaultPreferences.pitch
        }
    }
}