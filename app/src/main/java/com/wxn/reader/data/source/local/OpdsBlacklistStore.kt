package com.wxn.reader.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.opdsBlacklistDataStore by preferencesDataStore(name = "opds_blacklist")

class OpdsBlacklistStore @Inject constructor(context: Context) {
    private val dataStore = context.opdsBlacklistDataStore

    companion object {
        val BLACKLISTED_IDS = stringSetPreferencesKey("blacklisted_predefined_ids")
    }

    suspend fun getBlacklistedIds(): Set<String> {
        return dataStore.data.map { prefs ->
            prefs[BLACKLISTED_IDS] ?: emptySet()
        }.first()
    }

    suspend fun addToBlacklist(predefinedId: String) {
        dataStore.edit { prefs ->
            val current = prefs[BLACKLISTED_IDS] ?: emptySet()
            prefs[BLACKLISTED_IDS] = current + predefinedId
        }
    }
}
