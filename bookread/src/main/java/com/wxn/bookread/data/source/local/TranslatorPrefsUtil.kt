package com.wxn.bookread.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.base.util.Coroutines
import com.wxn.bookread.data.model.preference.TranslatorPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject


private val Context.translatorPrefsDataStore by preferencesDataStore(name = "translator_prefs")


class TranslatorPrefsUtil @Inject constructor(context: Context) {

    private val dataStore = context.translatorPrefsDataStore

    companion object {

        val LAST_TARGET_TRANSILATE_LANG = stringPreferencesKey("lastTargetTransilateLang")

        val LAST_SELECTED_TRANSLATOR = stringPreferencesKey("lastSelectedTranslator")

        val defaultPreferences = TranslatorPreferences(
            lastTargetTransilateLang = "",
            lastSelectedTranslator = "",
        )
    }

    private suspend fun initializeDefaultPreferences() {
        val preferences = dataStore.data.firstOrNull()
        if (preferences == null) {
            dataStore.edit { pref ->
                pref[LAST_TARGET_TRANSILATE_LANG] = defaultPreferences.lastTargetTransilateLang
                pref[LAST_SELECTED_TRANSLATOR] = defaultPreferences.lastSelectedTranslator
            }
        }
    }


    init {
        Coroutines.scope().launch {
            initializeDefaultPreferences()
        }
    }

    val transilatorPrefsFlow: Flow<TranslatorPreferences> = dataStore.data.map { prefs ->
        TranslatorPreferences(
            lastTargetTransilateLang = prefs[LAST_TARGET_TRANSILATE_LANG] ?: defaultPreferences.lastTargetTransilateLang,
            lastSelectedTranslator = prefs[LAST_SELECTED_TRANSLATOR] ?: defaultPreferences.lastSelectedTranslator
        )
    }

    /**
     * 更新目标翻译语言
     */
    suspend fun updateTargetLang(targetLang: String) {
        dataStore.edit { pref ->
            pref[LAST_TARGET_TRANSILATE_LANG] = targetLang
        }
    }

    /***
     * 更新翻译应用信息
     */
    suspend fun updateLastTranslator(translator: String) {
        dataStore.edit { pref ->
            pref[LAST_SELECTED_TRANSLATOR] = translator
        }
    }
}