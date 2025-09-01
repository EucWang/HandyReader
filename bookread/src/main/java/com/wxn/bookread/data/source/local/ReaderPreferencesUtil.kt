// In ReaderPreferencesUtil.kt
package com.wxn.bookread.data.source.local

import android.content.Context
import android.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.base.ext.toColor
import com.wxn.base.ext.toCompatibleArgb
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.config.ConfigReadingProgression
import com.wxn.bookread.data.model.config.toTextAlign
import com.wxn.bookread.data.model.preference.ReaderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private val Context.readerPrefsDataStore by preferencesDataStore(name = "reader_prefs")

class ReaderPreferencesUtil @Inject constructor(context: Context) {

    private val dataStore = context.readerPrefsDataStore

    companion object {
        val FONT_SIZE = doublePreferencesKey("font_size")                                   //字体大小
        val LETTER_SPACING = doublePreferencesKey("letter_spacing")                         //字母间距
        val LINE_HEIGHT = doublePreferencesKey("line_height")                               //行高

        val FONT_FAMILY = stringPreferencesKey("font_family")                                      //字体
        val FONT_BOLD = intPreferencesKey("font_bold")                                      //字体是否粗体

        val PAGE_HORIZONTAL_MARGINS = doublePreferencesKey("page_horizontal_margins")      //页面水平间距
        val PAGE_VERTICAL_MARGINS = doublePreferencesKey("page_vertical_margins")                     //页面顶部间距

        val PARAGRAPH_INDENT = doublePreferencesKey("paragraph_indent")                     //段落缩进
        val PARAGRAPH_SPACING = doublePreferencesKey("paragraph_spacing")                   //段落间距
        val WORD_SPACING = doublePreferencesKey("word_spacing")                             //词间距

        val TITLE_FONT_SIZE = doublePreferencesKey("title_font_size")                       //标题文字大小
        val TITLE_TOP_SPACING = doublePreferencesKey("title_top_spacing")                   //标题顶部间距
        val TITLE_BOTTOM_SPACING = doublePreferencesKey("title_bottom_spacing")             //标题底部间距

        val TEXT_ALIGN = stringPreferencesKey("text_align")                                 //文本对齐方式

        val BACKGROUND_COLOR = intPreferencesKey("background_color")                        //背景颜色
        val BACKGROUND_IMAGE = stringPreferencesKey("background_image")                        //背景图片
        val TEXT_COLOR = intPreferencesKey("text_color")                                    //文字颜色
        val COLOR_HISTORY = stringPreferencesKey("color_history")                           //颜色历史

        val READING_PROGRESSION = stringPreferencesKey("reading_progression")               //阅读方向，从左向右 / 从右向左
        val VERTICAL_TEXT = booleanPreferencesKey("vertical_text")                          //垂直文本

        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")                        //保持屏幕常亮
        val TAP_NAVIGATION = booleanPreferencesKey("tap_navigation")                        //点击导航
        val SCROLL = intPreferencesKey("scroll")                                        //滚动
        val PUBLISHER_STYLES = booleanPreferencesKey("publisher_styles")                    //出版商样式
        val TEXT_NORMALIZATION = booleanPreferencesKey("text_normalization")                //文字标准化

        // Default values
//        @OptIn(ExperimentalReadiumApi::class)
        val defaultPreferences = ReaderPreferences(
            fontSize = 1.0,
            font = "serif",
            fontBold = 0,
            titleSize = 1.0,
            titleTopSpacing = 18.0,
            titleBottomSpacing = 15.0,
            letterSpacing = 0.0,
            lineHeight = 1.2,
            pageHorizontalMargins = 1.5,
            pageVerticalMargins = 1.2,
            paragraphIndent = 2.0,
            paragraphSpacing = 0.2,
            wordSpacing = 0.0,
            textAlign = TextAlign.Justify,
            backgroundColor = Color.WHITE,
            backgroundImage = "",
            textColor = Color.BLACK,
            colorHistory = emptyList(),
            keepScreenOn = true,
            tapNavigation = false,
            scroll = 1,
            readingProgression = ConfigReadingProgression.LTR,
            verticalText = false,
            publisherStyles = false,
            textNormalization = false,
        )
    }

    private suspend fun initializeDefaultPreferences() {
        val preferences = dataStore.data.firstOrNull()
        if (preferences == null) {
            dataStore.edit { pref ->
                pref[FONT_SIZE] = defaultPreferences.fontSize.toDouble()
                pref[LINE_HEIGHT] = defaultPreferences.lineHeight.toDouble()
                pref[LETTER_SPACING] = defaultPreferences.letterSpacing.toDouble()
                pref[WORD_SPACING] = defaultPreferences.wordSpacing.toDouble()

                pref[PAGE_HORIZONTAL_MARGINS] = defaultPreferences.pageHorizontalMargins.toDouble()
                pref[PAGE_VERTICAL_MARGINS] = defaultPreferences.pageVerticalMargins.toDouble()
                pref[PARAGRAPH_INDENT] = defaultPreferences.paragraphIndent.toDouble()
                pref[PARAGRAPH_SPACING] = defaultPreferences.paragraphSpacing.toDouble()
                pref[TEXT_ALIGN] = defaultPreferences.textAlign.toString()

                pref[BACKGROUND_COLOR] = defaultPreferences.backgroundColor
                pref[BACKGROUND_IMAGE] = defaultPreferences.backgroundImage
                pref[TEXT_COLOR] = defaultPreferences.textColor

                pref[COLOR_HISTORY] = serializeColorHistory(defaultPreferences.colorHistory)

                pref[KEEP_SCREEN_ON] = defaultPreferences.keepScreenOn
                pref[SCROLL] = defaultPreferences.scroll
                pref[TAP_NAVIGATION] = defaultPreferences.tapNavigation
                pref[READING_PROGRESSION] = defaultPreferences.readingProgression.name
                pref[VERTICAL_TEXT] = defaultPreferences.verticalText
                pref[PUBLISHER_STYLES] = defaultPreferences.publisherStyles
                pref[TEXT_NORMALIZATION] = defaultPreferences.textNormalization

                pref[FONT_FAMILY] = defaultPreferences.font
                pref[FONT_BOLD] = defaultPreferences.fontBold
                pref[TITLE_FONT_SIZE] = defaultPreferences.titleSize.toDouble()
                pref[TITLE_TOP_SPACING] = defaultPreferences.titleTopSpacing.toDouble()
                pref[TITLE_BOTTOM_SPACING] = defaultPreferences.titleBottomSpacing.toDouble()
            }
        }
    }

    init {
        Coroutines.scope().launch {
            initializeDefaultPreferences()
        }
    }

    val readerPrefsFlow: Flow<ReaderPreferences> = dataStore.data.map { preferences ->
        ReaderPreferences(
            fontSize = preferences[FONT_SIZE] ?: defaultPreferences.fontSize,
            letterSpacing = preferences[LETTER_SPACING] ?: defaultPreferences.letterSpacing,
            lineHeight = preferences[LINE_HEIGHT] ?: defaultPreferences.lineHeight,
            pageHorizontalMargins = preferences[PAGE_HORIZONTAL_MARGINS] ?: defaultPreferences.pageHorizontalMargins,
            pageVerticalMargins = preferences[PAGE_VERTICAL_MARGINS] ?: defaultPreferences.pageVerticalMargins,
            paragraphIndent = preferences[PARAGRAPH_INDENT] ?: defaultPreferences.paragraphIndent,
            paragraphSpacing = preferences[PARAGRAPH_SPACING]
                ?: defaultPreferences.paragraphSpacing,
            wordSpacing = preferences[WORD_SPACING] ?: defaultPreferences.wordSpacing,
            textAlign = toTextAlign(preferences[TEXT_ALIGN]) ?: defaultPreferences.textAlign,
            backgroundColor = preferences[BACKGROUND_COLOR] ?: Color.WHITE,
            backgroundImage = preferences[BACKGROUND_IMAGE] ?: "",
            textColor = preferences[TEXT_COLOR] ?: Color.BLACK,
            colorHistory = preferences[COLOR_HISTORY]?.let { parseColorHistory(it) } ?: emptyList(),
            keepScreenOn = preferences[KEEP_SCREEN_ON] ?: defaultPreferences.keepScreenOn,
            tapNavigation = preferences[TAP_NAVIGATION] ?: defaultPreferences.tapNavigation,
            scroll = preferences[SCROLL] ?: defaultPreferences.scroll,
            readingProgression = ConfigReadingProgression.valueOf(
                preferences[READING_PROGRESSION] ?: defaultPreferences.readingProgression.name
            ),
            verticalText = preferences[VERTICAL_TEXT] ?: defaultPreferences.verticalText,
            publisherStyles = preferences[PUBLISHER_STYLES] ?: defaultPreferences.publisherStyles,
            textNormalization = preferences[TEXT_NORMALIZATION] ?: defaultPreferences.textNormalization,

            font = if (preferences[FONT_FAMILY].isNullOrEmpty()) { defaultPreferences.font } else { preferences[FONT_FAMILY].orEmpty() },
            fontBold = preferences[FONT_BOLD] ?: defaultPreferences.fontBold,
            titleSize = preferences[TITLE_FONT_SIZE] ?: defaultPreferences.titleSize,
            titleTopSpacing = preferences[TITLE_TOP_SPACING] ?: defaultPreferences.titleTopSpacing,
            titleBottomSpacing = preferences[TITLE_BOTTOM_SPACING] ?: defaultPreferences.titleBottomSpacing,
        )
    }

    suspend fun updatePreferences(newPreferences: ReaderPreferences) {
        Logger.d("ReaderPreferencesUtil::updatePreferences[$newPreferences]")
        dataStore.edit { preferences ->
            preferences[FONT_SIZE] = newPreferences.fontSize.toDouble()
            preferences[LINE_HEIGHT] = newPreferences.lineHeight.toDouble()
            preferences[LETTER_SPACING] = newPreferences.letterSpacing.toDouble()
            preferences[WORD_SPACING] = newPreferences.wordSpacing.toDouble()

            preferences[PAGE_HORIZONTAL_MARGINS] = newPreferences.pageHorizontalMargins.toDouble()
            preferences[PAGE_VERTICAL_MARGINS] = newPreferences.pageVerticalMargins.toDouble()
            preferences[PARAGRAPH_INDENT] = newPreferences.paragraphIndent.toDouble()
            preferences[PARAGRAPH_SPACING] = newPreferences.paragraphSpacing.toDouble()
            preferences[TEXT_ALIGN] = newPreferences.textAlign.toString()

            preferences[BACKGROUND_COLOR] = newPreferences.backgroundColor
            preferences[BACKGROUND_IMAGE] = newPreferences.backgroundImage
            preferences[TEXT_COLOR] = newPreferences.textColor

            preferences[COLOR_HISTORY] = serializeColorHistory(newPreferences.colorHistory)

            preferences[KEEP_SCREEN_ON] = newPreferences.keepScreenOn
            preferences[SCROLL] = newPreferences.scroll
            preferences[TAP_NAVIGATION] = newPreferences.tapNavigation
            preferences[READING_PROGRESSION] = newPreferences.readingProgression.name
            preferences[VERTICAL_TEXT] = newPreferences.verticalText
            preferences[PUBLISHER_STYLES] = newPreferences.publisherStyles
            preferences[TEXT_NORMALIZATION] = newPreferences.textNormalization

            preferences[FONT_FAMILY] = newPreferences.font
            preferences[FONT_BOLD] = newPreferences.fontBold
            preferences[TITLE_FONT_SIZE] = newPreferences.titleSize.toDouble()
            preferences[TITLE_TOP_SPACING] = newPreferences.titleTopSpacing.toDouble()
            preferences[TITLE_BOTTOM_SPACING] = newPreferences.titleBottomSpacing.toDouble()
//            preferences[lINE_SPACING_EXTRA] = newPreferences.lineSpacingExtra.toDouble()
        }
    }


    suspend fun resetFontPreferences() {
        Logger.d("ReaderPreferencesUtil::resetFontPreferences")
        dataStore.edit { preferences ->
            preferences[FONT_SIZE] = defaultPreferences.fontSize.toDouble()
            preferences[LINE_HEIGHT] = defaultPreferences.lineHeight.toDouble()
            preferences[LETTER_SPACING] = defaultPreferences.letterSpacing.toDouble()
            preferences[WORD_SPACING] = defaultPreferences.wordSpacing.toDouble()
            preferences[TITLE_FONT_SIZE] = defaultPreferences.titleSize.toDouble()

            preferences[FONT_FAMILY] = defaultPreferences.font
            preferences[FONT_BOLD] = defaultPreferences.fontBold
        }
    }

    suspend fun resetPagePreferences() {
        Logger.d("ReaderPreferencesUtil::resetPagePreferences")
        dataStore.edit { preferences ->
            preferences[PAGE_HORIZONTAL_MARGINS] = defaultPreferences.pageHorizontalMargins.toDouble()
            preferences[PAGE_VERTICAL_MARGINS] = defaultPreferences.pageVerticalMargins.toDouble()
            preferences[PARAGRAPH_INDENT] = defaultPreferences.paragraphIndent.toDouble()
            preferences[PARAGRAPH_SPACING] = defaultPreferences.paragraphSpacing.toDouble()
            preferences[TEXT_ALIGN] = defaultPreferences.textAlign.toString()

            preferences[TITLE_TOP_SPACING] = defaultPreferences.titleTopSpacing.toDouble()
            preferences[TITLE_BOTTOM_SPACING] = defaultPreferences.titleBottomSpacing.toDouble()
        }
    }

    suspend fun resetUiPreferences() {
        Logger.d("ReaderPreferencesUtil::resetUiPreferences")
        dataStore.edit { preferences ->
            preferences[BACKGROUND_COLOR] = defaultPreferences.backgroundColor
            preferences[BACKGROUND_IMAGE] = defaultPreferences.backgroundImage
            preferences[TEXT_COLOR] = defaultPreferences.textColor
        }
    }

    suspend fun resetReaderPreferences() {
        Logger.d("ReaderPreferencesUtil::resetReaderPreferences")
        dataStore.edit { preferences ->
            preferences[KEEP_SCREEN_ON] = defaultPreferences.keepScreenOn
            preferences[SCROLL] = defaultPreferences.scroll
            preferences[TAP_NAVIGATION] = defaultPreferences.tapNavigation
            preferences[READING_PROGRESSION] = defaultPreferences.readingProgression.name
            preferences[VERTICAL_TEXT] = defaultPreferences.verticalText
            preferences[PUBLISHER_STYLES] = defaultPreferences.publisherStyles
            preferences[TEXT_NORMALIZATION] = defaultPreferences.textNormalization
        }
    }

    private fun serializeColorHistory(colors: List<Color>): String {
        val ret = colors.joinToString(",") { it.toCompatibleArgb().toString() }
        Logger.d("ReaderPreferencesUtil::serializeColorHistory[${colors}],ret=${ret}")
        return ret
    }

    private fun parseColorHistory(serialized: String): List<Color> {
        if (serialized.isEmpty()) {
            return emptyList()
        }

        return serialized.split(",")
            .filter { it.isNotEmpty() } // Filter out any empty strings
            .mapNotNull {
                try {
                    it.toInt().toColor()
                } catch (e: NumberFormatException) {
                    null // Skip invalid color values
                }
            }
    }
}