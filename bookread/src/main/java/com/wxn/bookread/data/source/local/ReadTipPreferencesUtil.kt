package com.wxn.bookread.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.bookread.data.model.preference.ReadTipPreferences
import javax.inject.Inject

private val Context.readTipPreferenceDataStore by preferencesDataStore(name = "reader_tips_preferences")

class ReadTipPreferencesUtil @Inject constructor(
    val context : Context
) {

    private val dataStore = context.readTipPreferenceDataStore

    companion object {


        val TipHeaderLeft = intPreferencesKey("tip_header_left")
        val TipHeaderMiddle = intPreferencesKey("tip_header_middle")
        val TipHeaderRight = intPreferencesKey("tip_header_right")

        val TipFooterLeft = intPreferencesKey("tip_footer_left")
        val TipFooterMiddle = intPreferencesKey("tip_footer_middle")
        val TipFooterRight = intPreferencesKey("tip_footer_right")

        val HideHeader = booleanPreferencesKey("hide_header")
        val HideFooter = booleanPreferencesKey("hide_footer")

        //TODO
//        val defaultReadTipPreference = ReadTipPreferences(
//             headerPaddingLeft = 16,
//             headerPaddingRight: Int = 16,
//             headerPaddingTop: Int = 0,
//             headerPaddingBottom: Int = 0,

//             footerPaddingBottom: Int = 60,
//             footerPaddingLeft: Int = 16,
//             footerPaddingRight: Int = 16,
//             footerPaddingTop: Int = 6,

//             showHeaderLine: Boolean = false
//             showFooterLine: Boolean = false
//        )
    }

}