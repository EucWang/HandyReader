package com.wxn.reader.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.reader.data.model.BatteryOptimazePrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private val Context.batteryOptimazeDataStore by preferencesDataStore(name = "battery_optimaze_prefs")

class BatteryOptimazePrefsUtil @Inject constructor(context: Context) {

    private val dataStore = context.batteryOptimazeDataStore

    companion object {
        val ENABLE = booleanPreferencesKey("enable")
        val SHOW_DAY = stringPreferencesKey("show_day")
    }

    val defaultPrefs = BatteryOptimazePrefs(
        enable = true,
        showDay = ""
    )

    val batteryOptimazePrefsFlow: Flow<BatteryOptimazePrefs> = dataStore.data.map { prefs ->
        BatteryOptimazePrefs(
            enable = prefs[ENABLE] ?: defaultPrefs.enable,
            showDay = prefs[SHOW_DAY] ?: defaultPrefs.showDay
        )
    }

    suspend fun disableBatteryOptimaze() {
        dataStore.edit { prefs ->
            prefs[ENABLE] = false
        }
    }

    suspend fun updateShowDay() {
        val prefs = batteryOptimazePrefsFlow.firstOrNull()
        if (prefs?.enable == true) {
            val today = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(System.currentTimeMillis()))
            } catch (ex: Exception) {
                ""
            }

            dataStore.edit { prefs ->
                prefs[SHOW_DAY] = today
            }
        }
    }

    /***
     * 在允许显示的情况下
     * 一天只能显示一次
     */
    suspend fun enableShowToday() :Boolean {
        val prefs = batteryOptimazePrefsFlow.firstOrNull()
        return if (prefs?.enable == true) {
            val today = try {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(System.currentTimeMillis()))
            } catch (ex: Exception) {
                ""
            }
            !(prefs.showDay == today && today.isNotEmpty())
        } else {
            false
        }
    }

}