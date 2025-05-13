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
import com.wxn.bookread.data.model.config.ConfigReadingProgression
import com.wxn.bookread.data.model.config.toTextAlign
import com.wxn.bookread.data.model.preference.ReaderPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.readerPreferencesDataStore by preferencesDataStore(name = "reader_preferences")


class ReaderPreferencesUtil @Inject constructor(
    val context: Context
) {
    private val dataStore = context.readerPreferencesDataStore

    companion object {
        val FONT_SIZE = doublePreferencesKey("font_size")                                   //字体大小
        val LETTER_SPACING = doublePreferencesKey("letter_spacing")                         //字母间距
        val LINE_HEIGHT = doublePreferencesKey("line_height")                               //行高

        val FONT_FAMILY = stringPreferencesKey("font_family")                                      //字体
        val FONT_BOLD = intPreferencesKey("font_bold")                                      //字体是否粗体

        val PAGE_HORIZONTAL_MARGINS = doublePreferencesKey("page_horizontal_margins")      //页面水平间距
        val PAGE_TOP_MARGINS = doublePreferencesKey("page_top_margins")                     //页面顶部间距

        val PARAGRAPH_INDENT = doublePreferencesKey("paragraph_indent")                     //段落缩进
        val PARAGRAPH_SPACING = doublePreferencesKey("paragraph_spacing")                   //段落间距
        val WORD_SPACING = doublePreferencesKey("word_spacing")                             //词间距

        val TITLE_FONT_SIZE = doublePreferencesKey("title_font_size")                       //标题文字大小
        val TITLE_TOP_SPACING = doublePreferencesKey("title_top_spacing")                   //标题顶部间距
        val TITLE_BOTTOM_SPACING = doublePreferencesKey("title_bottom_spacing")             //标题底部间距

        val TEXT_ALIGN = stringPreferencesKey("text_align")                                 //文本对齐方式

        val BACKGROUND_COLOR = intPreferencesKey("background_color")                        //背景颜色
        val TEXT_COLOR = intPreferencesKey("text_color")                                    //文字颜色
        val COLOR_HISTORY = stringPreferencesKey("color_history")                           //颜色历史

        val READING_PROGRESSION = stringPreferencesKey("reading_progression")               //阅读方向，从左向右 / 从右向左
        val VERTICAL_TEXT = booleanPreferencesKey("vertical_text")                          //垂直文本

        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")                        //保持屏幕常亮
        val TAP_NAVIGATION = booleanPreferencesKey("tap_navigation")                        //点击导航
        val SCROLL = booleanPreferencesKey("scroll")                                        //滚动
        val PUBLISHER_STYLES = booleanPreferencesKey("publisher_styles")                    //出版商样式
        val TEXT_NORMALIZATION = booleanPreferencesKey("text_normalization")                //文字标准化


        // Default values
//        @OptIn(ExperimentalReadiumApi::class)
        val defaultPreferences = ReaderPreferences(
            fontSize = 1.0,
            font = "serif",
            fontBold = 0,
            titleSize = 1.0,
            titleTopSpacing = 5.0,
            titleBottomSpacing = 0.0,
            letterSpacing = 0.0,
            lineHeight = 1.5,
            pageHorizontalMargins = 1.0,
            pageTopMargins = 1.0,
            paragraphIndent = 0.0,
            paragraphSpacing = 0.0,
            wordSpacing = 0.0,
            textAlign = TextAlign.Justify,
            backgroundColor = Color.WHITE,
            textColor = Color.BLACK,
            colorHistory = emptyList(),
            keepScreenOn = true,
            tapNavigation = false,
            scroll = false,
            readingProgression = ConfigReadingProgression.LTR,
            verticalText = false,
            publisherStyles = false,
            textNormalization = false,
        )
    }


    val readerPreferencesFlow: Flow<ReaderPreferences> = dataStore.data.map { preferences ->
        ReaderPreferences(
            fontSize = preferences[FONT_SIZE] ?: defaultPreferences.fontSize,
            letterSpacing = preferences[LETTER_SPACING] ?: defaultPreferences.letterSpacing,
            lineHeight = preferences[LINE_HEIGHT] ?: defaultPreferences.lineHeight,
            pageHorizontalMargins = preferences[PAGE_HORIZONTAL_MARGINS] ?: defaultPreferences.pageHorizontalMargins,
            pageTopMargins = preferences[PAGE_TOP_MARGINS] ?: defaultPreferences.pageTopMargins,
            paragraphIndent = preferences[PARAGRAPH_INDENT] ?: defaultPreferences.paragraphIndent,
            paragraphSpacing = preferences[PARAGRAPH_SPACING]
                ?: defaultPreferences.paragraphSpacing,
            wordSpacing = preferences[WORD_SPACING] ?: defaultPreferences.wordSpacing,
//            textAlign = TextAlign.from(
//                preferences[TEXT_ALIGN] ?: defaultPreferences.textAlign
//            ),
            textAlign = toTextAlign(preferences[TEXT_ALIGN]) ?: defaultPreferences.textAlign,
            backgroundColor = preferences[BACKGROUND_COLOR] ?: Color.WHITE,
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
            textNormalization = preferences[TEXT_NORMALIZATION]
                ?: defaultPreferences.textNormalization,

            font = preferences[FONT_FAMILY].orEmpty(),
            fontBold = preferences[FONT_BOLD] ?: defaultPreferences.fontBold,
            titleSize = preferences[TITLE_FONT_SIZE] ?: defaultPreferences.titleSize,
            titleTopSpacing = preferences[TITLE_TOP_SPACING] ?: defaultPreferences.titleTopSpacing,
            titleBottomSpacing = preferences[TITLE_BOTTOM_SPACING] ?: defaultPreferences.titleBottomSpacing
        )
    }

    suspend fun updatePreferences(newPreferences: ReaderPreferences) {
        dataStore.edit { preferences ->
            preferences[FONT_SIZE] = newPreferences.fontSize?.toDouble() ?: 0.0
            preferences[LINE_HEIGHT] = newPreferences.lineHeight?.toDouble() ?: 0.0
            preferences[LETTER_SPACING] = newPreferences.letterSpacing?.toDouble() ?: 0.0
            preferences[WORD_SPACING] = newPreferences.wordSpacing?.toDouble() ?: 0.0

            preferences[PAGE_HORIZONTAL_MARGINS] = newPreferences.pageHorizontalMargins?.toDouble() ?: 0.0
            preferences[PAGE_TOP_MARGINS] = newPreferences.pageTopMargins?.toDouble() ?: 0.0
            preferences[PARAGRAPH_INDENT] = newPreferences.paragraphIndent?.toDouble() ?: 0.0
            preferences[PARAGRAPH_SPACING] = newPreferences.paragraphSpacing?.toDouble() ?: 0.0
            preferences[TEXT_ALIGN] = newPreferences.textAlign.toString()

            preferences[BACKGROUND_COLOR] = newPreferences.backgroundColor
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
            preferences[TITLE_FONT_SIZE] = newPreferences.titleSize?.toDouble() ?: 0.0
            preferences[TITLE_TOP_SPACING] = newPreferences.titleTopSpacing?.toDouble() ?: 0.0
            preferences[TITLE_BOTTOM_SPACING] = newPreferences.titleBottomSpacing?.toDouble() ?: 0.0
        }
    }


    suspend fun resetFontPreferences() {
        dataStore.edit { preferences ->
            preferences[FONT_SIZE] = defaultPreferences.fontSize?.toDouble() ?: 0.0
            preferences[LINE_HEIGHT] = defaultPreferences.lineHeight?.toDouble() ?: 0.0
            preferences[LETTER_SPACING] = defaultPreferences.letterSpacing?.toDouble() ?: 0.0
            preferences[WORD_SPACING] = defaultPreferences.wordSpacing?.toDouble() ?: 0.0
            preferences[TITLE_FONT_SIZE] = defaultPreferences.titleSize?.toDouble() ?: 0.0

            preferences[FONT_FAMILY] = defaultPreferences.font
            preferences[FONT_BOLD] = defaultPreferences.fontBold
        }
    }

    suspend fun resetPagePreferences() {
        dataStore.edit { preferences ->
            preferences[PAGE_HORIZONTAL_MARGINS] = defaultPreferences.pageHorizontalMargins?.toDouble() ?: 0.0
            preferences[PAGE_TOP_MARGINS] = defaultPreferences.pageTopMargins?.toDouble() ?: 0.0
            preferences[PARAGRAPH_INDENT] = defaultPreferences.paragraphIndent?.toDouble() ?: 0.0
            preferences[PARAGRAPH_SPACING] = defaultPreferences.paragraphSpacing?.toDouble() ?: 0.0
            preferences[TEXT_ALIGN] = defaultPreferences.textAlign.toString()

            preferences[TITLE_TOP_SPACING] = defaultPreferences.titleTopSpacing?.toDouble() ?: 0.0
            preferences[TITLE_BOTTOM_SPACING] = defaultPreferences.titleBottomSpacing?.toDouble() ?: 0.0
        }
    }

    suspend fun resetUiPreferences() {
        dataStore.edit { preferences ->
            preferences[BACKGROUND_COLOR] = defaultPreferences.backgroundColor
            preferences[TEXT_COLOR] = defaultPreferences.textColor
        }
    }

    suspend fun resetReaderPreferences() {
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
        return colors.joinToString(",") { it.toCompatibleArgb().toString() }
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
