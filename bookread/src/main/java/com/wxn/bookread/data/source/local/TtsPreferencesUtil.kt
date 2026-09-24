package com.wxn.bookread.data.source.local

import android.content.Context
import androidx.core.app.LocaleManagerCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        val BUFFERED_PARAGRAPHS = intPreferencesKey("buffered_paragraphs")
        val LANGUAGE = stringPreferencesKey("language")
        val VOICE = stringPreferencesKey("voice")
        val USE_BOOK_LANGUAGE = booleanPreferencesKey("use_book_language")

        val defaultPreferences = TtsPreferences(
            localeCode = "" , //"en",
            speed = 1.0f,
            pitch = 1.0f,
            bufferedParagraphs = 3,
            voiceName = "",
            useBookLanguage = true,
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
            pitch = preferences[PITCH] ?: defaultPreferences.pitch,
            bufferedParagraphs = (preferences[BUFFERED_PARAGRAPHS]
                ?: defaultPreferences.bufferedParagraphs).coerceIn(3, 7),
            voiceName = preferences[VOICE] ?: defaultPreferences.voiceName,
            useBookLanguage = preferences[USE_BOOK_LANGUAGE] ?: defaultPreferences.useBookLanguage,
        )
    }

    suspend fun updatePreferences(newPreferences: TtsPreferences) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE] = newPreferences.localeCode
            preferences[SPEED] = newPreferences.speed
            preferences[PITCH] = newPreferences.pitch
            preferences[BUFFERED_PARAGRAPHS] = newPreferences.bufferedParagraphs.coerceIn(3, 7)
            preferences[VOICE] = newPreferences.voiceName
            preferences[USE_BOOK_LANGUAGE] = newPreferences.useBookLanguage
        }
    }

    suspend fun updateSpeed(speed: Float) {
        dataStore.edit { it[SPEED] = speed }
    }

    suspend fun updatePitch(pitch: Float) {
        dataStore.edit { it[PITCH] = pitch }
    }

    suspend fun updateBufferedParagraphs(bufferedParagraphs: Int) {
        dataStore.edit { it[BUFFERED_PARAGRAPHS] = bufferedParagraphs.coerceIn(3, 7) }
    }

    suspend fun updateLanguage(localeCode: String, useBookLanguage: Boolean) {
        dataStore.edit {
            it[LANGUAGE] = localeCode
            it[USE_BOOK_LANGUAGE] = useBookLanguage
        }
    }

    suspend fun updateVoiceName(voiceName: String) {
        dataStore.edit { it[VOICE] = voiceName }
    }

    suspend fun resetTtsPreferences() {
        dataStore.edit { preferences ->
            preferences[LANGUAGE] = defaultPreferences.localeCode
            preferences[SPEED] = defaultPreferences.speed
            preferences[PITCH] = defaultPreferences.pitch
            preferences[BUFFERED_PARAGRAPHS] = defaultPreferences.bufferedParagraphs
            preferences[VOICE] = defaultPreferences.voiceName
            preferences[USE_BOOK_LANGUAGE] = defaultPreferences.useBookLanguage
        }
    }
}
