package com.wxn.bookread.data.model.preference

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.firstHintPrefsDataStore by preferencesDataStore(name = "first_hint_prefs")

class FirstHintPrefsUtil @Inject constructor(
    val context: Context
) {

    private val dataStore = context.firstHintPrefsDataStore

    companion object {
        val HAS_SHOWN_SWIPE_TO_DELETE_HINT = booleanPreferencesKey("has_shown_swipe_to_delete_hint")

        val defaultFirstHintPref = FirstHintPreferences(
            hasShownSwipeToDeleteHint = false
        )
    }

    val firstHintFlow: Flow<FirstHintPreferences> = dataStore.data.map { pref ->
        FirstHintPreferences(
            hasShownSwipeToDeleteHint = pref[HAS_SHOWN_SWIPE_TO_DELETE_HINT]
                ?: defaultFirstHintPref.hasShownSwipeToDeleteHint
        )
    }

    suspend fun clearSwipeToDelHint() {
        dataStore.edit { prefs ->
            prefs[HAS_SHOWN_SWIPE_TO_DELETE_HINT] = true
        }
    }
}