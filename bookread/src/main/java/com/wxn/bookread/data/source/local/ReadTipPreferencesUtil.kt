package com.wxn.bookread.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.bookread.data.model.preference.ReadTipPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.readTipPrefsDataStore by preferencesDataStore(name = "reader_tips_prefs")

class ReadTipPreferencesUtil @Inject constructor(context: Context) {

    private val dataStore = context.readTipPrefsDataStore

    companion object {
        const val ReadTip_none = 0
        const val ReadTip_chapterTitle = 1
        const val ReadTip_time = 2
        const val ReadTip_battery = 3
        const val ReadTip_page = 4
        const val ReadTip_totalProgress = 5
        const val ReadTip_pageAndTotal = 6
        const val ReadTip_bookName = 7

        val HeaderPaddingBottom = intPreferencesKey("headerPaddingBottom")
        val HeaderPaddingLeft = intPreferencesKey("headerPaddingLeft")
        val HeaderPaddingRight = intPreferencesKey("headerPaddingRight")
        val HeaderPaddingTop = intPreferencesKey("headerPaddingTop")

        val FooterPaddingBottom = intPreferencesKey("footerPaddingBottom")
        val FooterPaddingLeft = intPreferencesKey("footerPaddingLeft")
        val FooterPaddingRight = intPreferencesKey("footerPaddingRight")
        val FooterPaddingTop = intPreferencesKey("footerPaddingTop")

        val TipHeaderLeft = intPreferencesKey("tip_header_left")
        val TipHeaderMiddle = intPreferencesKey("tip_header_middle")
        val TipHeaderRight = intPreferencesKey("tip_header_right")

        val TipFooterLeft = intPreferencesKey("tip_footer_left")
        val TipFooterMiddle = intPreferencesKey("tip_footer_middle")
        val TipFooterRight = intPreferencesKey("tip_footer_right")

        val HideHeader = booleanPreferencesKey("hide_header")
        val HideFooter = booleanPreferencesKey("hide_footer")

//        val HideStatusBar = booleanPreferencesKey("hide_status_bar")
//        val HideNavigationBar = booleanPreferencesKey("hide_navigation_bar")

        val ClickTurnPage = booleanPreferencesKey("click_turn_page")
        val ClickAllNext = booleanPreferencesKey("click_all_next")
        val TextFullJustify = booleanPreferencesKey("text_full_justify")
        val TextBottomJustify = booleanPreferencesKey("text_bottom_justify")

        val defaultReadTipPreference = ReadTipPreferences(
            headerPaddingBottom = 0,
            headerPaddingLeft = 16,
            headerPaddingRight = 16,
            headerPaddingTop = 0,

            footerPaddingBottom = 60,
            footerPaddingLeft = 16,
            footerPaddingRight = 16,
            footerPaddingTop = 6,

            tipHeaderLeft = 2,
            tipHeaderMiddle = 0,
            tipHeaderRight = 3,

            tipFooterLeft = 1,
            tipFooterMiddle = 0,
            tipFooterRight = 6,

            hideHeader = true,
            hideFooter = true,

//            hideStatusBar = true,
//            hideNavigationBar = true,

            clickTurnPage = true,
            clickAllNext = false,
            textFullJustify = true,
            textBottomJustify = true
        )
    }

    val readTIpPreferencesFlow: Flow<ReadTipPreferences> = dataStore.data.map { preference ->
        ReadTipPreferences(
            headerPaddingBottom = preference[HeaderPaddingBottom]
                ?: defaultReadTipPreference.headerPaddingBottom,
            headerPaddingLeft = preference[HeaderPaddingLeft]
                ?: defaultReadTipPreference.headerPaddingLeft,
            headerPaddingRight = preference[HeaderPaddingRight]
                ?: defaultReadTipPreference.headerPaddingRight,
            headerPaddingTop = preference[HeaderPaddingTop]
                ?: defaultReadTipPreference.headerPaddingTop,

            footerPaddingBottom = preference[FooterPaddingBottom]
                ?: defaultReadTipPreference.footerPaddingBottom,
            footerPaddingLeft = preference[FooterPaddingLeft]
                ?: defaultReadTipPreference.footerPaddingLeft,
            footerPaddingRight = preference[FooterPaddingRight]
                ?: defaultReadTipPreference.footerPaddingRight,
            footerPaddingTop = preference[FooterPaddingTop]
                ?: defaultReadTipPreference.footerPaddingTop,

            tipHeaderLeft = preference[TipHeaderLeft] ?: defaultReadTipPreference.tipHeaderLeft,
            tipHeaderMiddle = preference[TipHeaderMiddle]
                ?: defaultReadTipPreference.tipHeaderMiddle,
            tipHeaderRight = preference[TipHeaderRight] ?: defaultReadTipPreference.tipHeaderRight,

            tipFooterLeft = preference[TipFooterLeft] ?: defaultReadTipPreference.tipFooterLeft,
            tipFooterMiddle = preference[TipFooterMiddle]
                ?: defaultReadTipPreference.tipFooterMiddle,
            tipFooterRight = preference[TipFooterRight] ?: defaultReadTipPreference.tipFooterRight,

            hideHeader = preference[HideHeader] ?: defaultReadTipPreference.hideHeader,
            hideFooter = preference[HideFooter] ?: defaultReadTipPreference.hideFooter,

//            hideStatusBar = preference[HideStatusBar] ?: defaultReadTipPreference.hideStatusBar,
//            hideNavigationBar = preference[HideNavigationBar] ?: defaultReadTipPreference.hideNavigationBar,

            clickTurnPage = preference[ClickTurnPage] ?: defaultReadTipPreference.clickTurnPage,
            clickAllNext = preference[ClickAllNext] ?: defaultReadTipPreference.clickAllNext,
            textFullJustify = preference[TextFullJustify]
                ?: defaultReadTipPreference.textFullJustify,
            textBottomJustify = preference[TextBottomJustify]
                ?: defaultReadTipPreference.textBottomJustify,
        )
    }

    suspend fun updatePreferences(newPreferences: ReadTipPreferences) {
        dataStore.edit { preference ->
            preference[HeaderPaddingBottom] = newPreferences.headerPaddingBottom
            preference[HeaderPaddingLeft] = newPreferences.headerPaddingLeft
            preference[HeaderPaddingRight] = newPreferences.headerPaddingRight
            preference[HeaderPaddingTop] = newPreferences.headerPaddingTop

            preference[FooterPaddingBottom] = newPreferences.footerPaddingBottom
            preference[FooterPaddingLeft] = newPreferences.footerPaddingLeft
            preference[FooterPaddingRight] = newPreferences.footerPaddingRight
            preference[FooterPaddingTop] = newPreferences.footerPaddingTop

            preference[TipHeaderLeft] = newPreferences.tipHeaderLeft
            preference[TipHeaderMiddle] = newPreferences.tipHeaderMiddle
            preference[TipHeaderRight] = newPreferences.tipHeaderRight

            preference[TipFooterLeft] = newPreferences.tipFooterLeft
            preference[TipFooterMiddle] = newPreferences.tipFooterMiddle
            preference[TipFooterRight] = newPreferences.tipFooterRight

            preference[HideHeader] = newPreferences.hideHeader
            preference[HideFooter] = newPreferences.hideFooter

//            preference[HideStatusBar] = newPreferences.hideStatusBar
//            preference[HideNavigationBar] = newPreferences.hideNavigationBar

            preference[ClickTurnPage] = newPreferences.clickTurnPage
            preference[ClickAllNext] = newPreferences.clickAllNext
            preference[TextFullJustify] = newPreferences.textFullJustify
            preference[TextBottomJustify] = newPreferences.textBottomJustify
        }
    }


    suspend fun reset() {
        dataStore.edit { preference ->
            preference[HeaderPaddingBottom] = defaultReadTipPreference.headerPaddingBottom
            preference[HeaderPaddingLeft] = defaultReadTipPreference.headerPaddingLeft
            preference[HeaderPaddingRight] = defaultReadTipPreference.headerPaddingRight
            preference[HeaderPaddingTop] = defaultReadTipPreference.headerPaddingTop

            preference[FooterPaddingBottom] = defaultReadTipPreference.footerPaddingBottom
            preference[FooterPaddingLeft] = defaultReadTipPreference.footerPaddingLeft
            preference[FooterPaddingRight] = defaultReadTipPreference.footerPaddingRight
            preference[FooterPaddingTop] = defaultReadTipPreference.footerPaddingTop

            preference[TipHeaderLeft] = defaultReadTipPreference.tipHeaderLeft
            preference[TipHeaderMiddle] = defaultReadTipPreference.tipHeaderMiddle
            preference[TipHeaderRight] = defaultReadTipPreference.tipHeaderRight

            preference[TipFooterLeft] = defaultReadTipPreference.tipFooterLeft
            preference[TipFooterMiddle] = defaultReadTipPreference.tipFooterMiddle
            preference[TipFooterRight] = defaultReadTipPreference.tipFooterRight

            preference[HideHeader] = defaultReadTipPreference.hideHeader
            preference[HideFooter] = defaultReadTipPreference.hideFooter

//            preference[HideStatusBar] = defaultReadTipPreference.hideStatusBar
//            preference[HideNavigationBar] = defaultReadTipPreference.hideNavigationBar

            preference[ClickTurnPage] = defaultReadTipPreference.clickTurnPage
            preference[ClickAllNext] = defaultReadTipPreference.clickAllNext
            preference[TextFullJustify] = defaultReadTipPreference.textFullJustify
            preference[TextBottomJustify] = defaultReadTipPreference.textBottomJustify
        }
    }

}