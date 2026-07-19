package com.wxn.reader.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.reader.data.model.DictionaryPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

private val Context.dictionaryPrefsDataStore by preferencesDataStore(name = "dictionary_prefs")

class DictionaryPrefsUtil @Inject constructor(context: Context) {

    private val dataStore = context.dictionaryPrefsDataStore

    companion object {
        val LAST_DICT_LANG = stringPreferencesKey("last_dict_lang")
        val DEFAULT_LOOKUP_APP = stringPreferencesKey("default_lookup_app")
        val LOOKUP_DATE = stringPreferencesKey("lookup_date")
        val LOOKUP_COUNT = intPreferencesKey("lookup_count")
        private const val DAILY_LIMIT = 120
    }

    val defaultPreferences = DictionaryPreferences(
        lastDictLang = "",
        defaultLookupApp = "",
        lookupDate = "",
        lookupCount = 0
    )

    val dictionaryPrefsFlow: Flow<DictionaryPreferences> = dataStore.data.map { prefs ->
        DictionaryPreferences(
            lastDictLang = prefs[LAST_DICT_LANG] ?: defaultPreferences.lastDictLang,
            defaultLookupApp = prefs[DEFAULT_LOOKUP_APP] ?: defaultPreferences.defaultLookupApp,
            lookupDate = prefs[LOOKUP_DATE] ?: defaultPreferences.lookupDate,
            lookupCount = prefs[LOOKUP_COUNT] ?: defaultPreferences.lookupCount
        )
    }

    suspend fun updateLastDictLang(lang: String) {
        if (lang.isBlank()) return
        dataStore.edit { prefs ->
            prefs[LAST_DICT_LANG] = lang
        }
    }

    suspend fun updateDefaultLookupApp(appId: String) {
        dataStore.edit { prefs ->
            prefs[DEFAULT_LOOKUP_APP] = appId
        }
    }

    suspend fun canLookup(): Boolean {
        val prefs = dataStore.data.first()
        val today = LocalDate.now().toString()
        val currentDate = prefs[LOOKUP_DATE] ?: ""
        val currentCount = prefs[LOOKUP_COUNT] ?: 0
        return currentDate != today || currentCount < DAILY_LIMIT
    }

    suspend fun incrementLookupCount() {
        val today = LocalDate.now().toString()
        dataStore.edit { prefs ->
            val currentDate = prefs[LOOKUP_DATE] ?: ""
            if (currentDate != today) {
                prefs[LOOKUP_DATE] = today
                prefs[LOOKUP_COUNT] = 1
            } else {
                prefs[LOOKUP_COUNT] = (prefs[LOOKUP_COUNT] ?: 0) + 1
            }
        }
    }
}
