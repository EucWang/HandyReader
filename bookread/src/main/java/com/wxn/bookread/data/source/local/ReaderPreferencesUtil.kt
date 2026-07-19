package com.wxn.bookread.data.source.local

import android.content.Context
import android.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.base.ext.toColor
import com.wxn.base.ext.toCompatibleArgb
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.config.ConfigReadingProgression
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.model.preference.ReaderThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
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
        val FONT_VARIANT = stringPreferencesKey("font_variant")                              //字体变体名称

        val PAGE_HORIZONTAL_MARGINS = doublePreferencesKey("page_horizontal_margins")      //页面水平间距
        val PAGE_VERTICAL_MARGINS = doublePreferencesKey("page_vertical_margins")                     //页面顶部间距

        val PARAGRAPH_INDENT = doublePreferencesKey("paragraph_indent")                     //段落缩进
        val PARAGRAPH_SPACING = doublePreferencesKey("paragraph_spacing")                   //段落间距
        val WORD_SPACING = doublePreferencesKey("word_spacing")                             //词间距

        val TITLE_FONT_SIZE = doublePreferencesKey("title_font_size")                       //标题文字大小
        val TITLE_TOP_SPACING = doublePreferencesKey("title_top_spacing")                   //标题顶部间距
        val TITLE_BOTTOM_SPACING = doublePreferencesKey("title_bottom_spacing")             //标题底部间距

        val FORCE_ALIGN_OVERRIDE = booleanPreferencesKey("force_align_override")             //强制覆盖对齐
        val USER_TEXT_ALIGN = intPreferencesKey("user_text_align")                           //用户对齐偏好: 1=Left, 2=Right, 3=Center, 4=Justify

        val BACKGROUND_COLOR = intPreferencesKey("background_color")                        //背景颜色
        val BACKGROUND_IMAGE = stringPreferencesKey("background_image")                        //背景图片
        val TEXT_COLOR = intPreferencesKey("text_color")                                    //文字颜色
        val COLOR_HISTORY = stringPreferencesKey("color_history")                           //颜色历史
        val READER_THEME_ID = stringPreferencesKey("reader_theme_id")                       //阅读主题id
        val READER_THEME_MODE = stringPreferencesKey("reader_theme_mode")                     //阅读主题模式(LIGHT/DARK/AUTO)

        val READING_PROGRESSION = stringPreferencesKey("reading_progression")               //阅读方向，从左向右 / 从右向左
        val VERTICAL_TEXT = booleanPreferencesKey("vertical_text")                          //垂直文本

        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")                        //保持屏幕常亮
        val TAP_NAVIGATION = booleanPreferencesKey("tap_navigation")                        //点击导航
        val SCROLL = intPreferencesKey("scroll")                                        //滚动
        val ANIMATION_SPEED = intPreferencesKey("animation_speed")                      //翻页动画速度
        val PUBLISHER_STYLES = booleanPreferencesKey("publisher_styles")                    //出版商样式
        val TEXT_NORMALIZATION = booleanPreferencesKey("text_normalization")                //文字标准化
        val VOLUME_KEY_PAGE_TURNING = booleanPreferencesKey("volume_key_page_turning")      //音量键翻页
        val CLICK_AREA_MODE = intPreferencesKey("click_area_mode")                           //点击区域模式
        val LEFT_HANDED_MODE = booleanPreferencesKey("left_handed_mode")                     //左手操作模式
        val BRIGHTNESS = floatPreferencesKey("brightness")                                   //亮度值
        val BRIGHTNESS_SET = booleanPreferencesKey("brightness_set")                         //是否手动设置过亮度
        val COLUMNS = intPreferencesKey("columns")                               //双列显示开关, ==2 即开始

        // Default values
//        @OptIn(ExperimentalReadiumApi::class)
        val defaultPreferences = ReaderPreferences(
            fontSize = 1.0,
            font = "sans_serif",
            fontBold = 0,
            fontVariant = "regular",
            titleSize = 1.0,
            titleTopSpacing = 18.0,
            titleBottomSpacing = 15.0,
            letterSpacing = 0.0,
            lineHeight = 1.5,
            pageHorizontalMargins = 1.5,
            pageVerticalMargins = 1.2,
            paragraphIndent = 2.0,
            paragraphSpacing = 0.6,
            wordSpacing = 0.0,
            forceAlignOverride = false,
            userTextAlign = 4,
            backgroundColor = Color.WHITE,
            backgroundImage = "",
            textColor = Color.BLACK,
            colorHistory = emptyList(),
            readerThemeId = null,
            readerThemeMode = ReaderThemeMode.AUTO,
            keepScreenOn = true,
            tapNavigation = false,
            scroll = 1,
            animationSpeed = 210,
            readingProgression = ConfigReadingProgression.AUTO,
            verticalText = false,
            publisherStyles = false,
            textNormalization = false,
            volumeKeyPageTurning = false,
            clickAreaMode = 0,
            leftHandedMode = false,
            brightness = 0.0f,
            brightnessSet = false,
            columns = 1)
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
                pref[FORCE_ALIGN_OVERRIDE] = defaultPreferences.forceAlignOverride
                pref[USER_TEXT_ALIGN] = defaultPreferences.userTextAlign

                pref[BACKGROUND_COLOR] = defaultPreferences.backgroundColor
                pref[BACKGROUND_IMAGE] = defaultPreferences.backgroundImage
                pref[TEXT_COLOR] = defaultPreferences.textColor

                pref[COLOR_HISTORY] = serializeColorHistory(defaultPreferences.colorHistory)
                // readerThemeId 默认 null（Q-02-B：老用户/新用户首次都不预设主题，主题卡无选中）

                pref[KEEP_SCREEN_ON] = defaultPreferences.keepScreenOn
                pref[SCROLL] = defaultPreferences.scroll
                pref[ANIMATION_SPEED] = defaultPreferences.animationSpeed
                pref[TAP_NAVIGATION] = defaultPreferences.tapNavigation
                pref[READING_PROGRESSION] = defaultPreferences.readingProgression.name
                pref[VERTICAL_TEXT] = defaultPreferences.verticalText
                pref[PUBLISHER_STYLES] = defaultPreferences.publisherStyles
                pref[TEXT_NORMALIZATION] = defaultPreferences.textNormalization
                pref[VOLUME_KEY_PAGE_TURNING] = defaultPreferences.volumeKeyPageTurning
                pref[CLICK_AREA_MODE] = defaultPreferences.clickAreaMode
                pref[LEFT_HANDED_MODE] = defaultPreferences.leftHandedMode
                pref[BRIGHTNESS] = defaultPreferences.brightness
                pref[BRIGHTNESS_SET] = defaultPreferences.brightnessSet

                pref[FONT_FAMILY] = defaultPreferences.font
                pref[FONT_BOLD] = defaultPreferences.fontBold
                pref[FONT_VARIANT] = defaultPreferences.fontVariant
                pref[TITLE_FONT_SIZE] = defaultPreferences.titleSize.toDouble()
                pref[TITLE_TOP_SPACING] = defaultPreferences.titleTopSpacing.toDouble()
                pref[TITLE_BOTTOM_SPACING] = defaultPreferences.titleBottomSpacing.toDouble()

                pref[COLUMNS] = defaultPreferences.columns
            }
        }
    }

    init {
        Coroutines.scope().launch {
            initializeDefaultPreferences()
        }
    }

    /**
     * v11 per-book：effective 偏好 override 层。
     *
     * per-book 开启时由 MainReadViewModel 推入 effective 值（全局基线 ∪ delta）；
     * null 时回退纯 DataStore（全局模式 / 非 per-book 书）。
     * 渲染层（ChapterProvider 等）读 [readerPrefsFlow] 自动拿到 effective 值，无需感知 per-book 逻辑。
     */
    private val _effectiveOverride = MutableStateFlow<ReaderPreferences?>(null)

    /** 由 ViewModel 在 effective 流算出结果后调用，推入当前生效偏好。null = 清除 override（回退全局）。 */
    fun setEffectiveOverride(prefs: ReaderPreferences?) {
        _effectiveOverride.value = prefs
    }

    /** 纯 DataStore 原始值（不含 per-book override）。effective 流作基线计算用，避免与 [readerPrefsFlow] 递归。 */
    val rawReaderPrefsFlow: Flow<ReaderPreferences> = dataStore.data.map { preferences ->
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
            forceAlignOverride = preferences[FORCE_ALIGN_OVERRIDE] ?: defaultPreferences.forceAlignOverride,
            userTextAlign = preferences[USER_TEXT_ALIGN] ?: defaultPreferences.userTextAlign,
            backgroundColor = preferences[BACKGROUND_COLOR] ?: Color.WHITE,
            backgroundImage = preferences[BACKGROUND_IMAGE] ?: "",
            textColor = preferences[TEXT_COLOR] ?: Color.BLACK,
            colorHistory = preferences[COLOR_HISTORY]?.let { parseColorHistory(it) } ?: emptyList(),
            readerThemeId = preferences[READER_THEME_ID],
            readerThemeMode = preferences[READER_THEME_MODE]
                ?.let { runCatching { ReaderThemeMode.valueOf(it) }.getOrNull() }
                ?: defaultPreferences.readerThemeMode,
            keepScreenOn = preferences[KEEP_SCREEN_ON] ?: defaultPreferences.keepScreenOn,
            tapNavigation = preferences[TAP_NAVIGATION] ?: defaultPreferences.tapNavigation,
            scroll = preferences[SCROLL] ?: defaultPreferences.scroll,
            animationSpeed = preferences[ANIMATION_SPEED] ?: defaultPreferences.animationSpeed,
            readingProgression = ConfigReadingProgression.valueOf(
                preferences[READING_PROGRESSION] ?: defaultPreferences.readingProgression.name
            ),
            verticalText = preferences[VERTICAL_TEXT] ?: defaultPreferences.verticalText,
            publisherStyles = preferences[PUBLISHER_STYLES] ?: defaultPreferences.publisherStyles,
            textNormalization = preferences[TEXT_NORMALIZATION] ?: defaultPreferences.textNormalization,
            volumeKeyPageTurning = preferences[VOLUME_KEY_PAGE_TURNING] ?: defaultPreferences.volumeKeyPageTurning,
            clickAreaMode = preferences[CLICK_AREA_MODE] ?: defaultPreferences.clickAreaMode,
            leftHandedMode = preferences[LEFT_HANDED_MODE] ?: defaultPreferences.leftHandedMode,
            brightness = preferences[BRIGHTNESS] ?: defaultPreferences.brightness,
            brightnessSet = preferences[BRIGHTNESS_SET] ?: defaultPreferences.brightnessSet,

            font = if (preferences[FONT_FAMILY].isNullOrEmpty()) { defaultPreferences.font } else { preferences[FONT_FAMILY].orEmpty() },
            fontBold = preferences[FONT_BOLD] ?: defaultPreferences.fontBold,
            fontVariant = preferences[FONT_VARIANT] ?: defaultPreferences.fontVariant,
            titleSize = preferences[TITLE_FONT_SIZE] ?: defaultPreferences.titleSize,
            titleTopSpacing = preferences[TITLE_TOP_SPACING] ?: defaultPreferences.titleTopSpacing,
            titleBottomSpacing = preferences[TITLE_BOTTOM_SPACING] ?: defaultPreferences.titleBottomSpacing,

            columns = preferences[COLUMNS] ?: defaultPreferences.columns,
        )
    }

    /**
     * 渲染层单一数据源：override 非 null 时返回 effective 值（per-book），否则返回纯 DataStore 值（全局）。
     *
     * 渲染层（ChapterProvider / PageView 等）只需读本 Flow，自动拿到 per-book 生效值，无需感知 per-book 逻辑。
     */
    val readerPrefsFlow: Flow<ReaderPreferences> = combine(rawReaderPrefsFlow, _effectiveOverride) { raw, override ->
        override ?: raw
    }


    suspend fun updateBgColor(color:Int) {
        Logger.i("ReaderPreferencesUtil::updateBgColorWithNonImage[$color]")
        dataStore.edit { prefs ->
            prefs[BACKGROUND_COLOR] = color
        }
    }

    suspend fun updateBgColorWithNonImage(color:Int) {
        Logger.i("ReaderPreferencesUtil::updateBgColorWithNonImage[$color]")
        dataStore.edit { prefs ->
            prefs[BACKGROUND_COLOR] = color
            prefs[BACKGROUND_IMAGE] = ""
        }
    }

    suspend fun updateParagraphSpacing(spacing: Double) {
        Logger.i("ReaderPreferencesUtil::updateparagraphSpacing[$spacing]")
        dataStore.edit { prefs ->
            prefs[PARAGRAPH_SPACING] = spacing
        }
    }

    suspend fun updateParagraphIndent(indent:Double) {
        Logger.i("ReaderPreferencesUtil::updateParagraphIndent[$indent]")
        dataStore.edit { prefs ->
            prefs[PARAGRAPH_INDENT] = indent
        }
    }

    suspend fun updatePageVerticalMargins(margin:Double) {
        Logger.i("ReaderPreferencesUtil::updatePageVerticalMargins[$margin]")
        dataStore.edit { prefs ->
            prefs[PAGE_VERTICAL_MARGINS] = margin
        }
    }

    suspend fun updatePageHorizontalMargins(margin:Double) {
        Logger.i("ReaderPreferencesUtil::updatePageHorizontalMargins[$margin]")
        dataStore.edit { prefs ->
            prefs[PAGE_HORIZONTAL_MARGINS] = margin
        }
    }

    suspend fun updateTextColor(color: Int) {
        Logger.d("ReaderPreferencesUtil::updateTextColor[$color]")
        dataStore.edit { prefs ->
            prefs[TEXT_COLOR] = color
        }
    }

    suspend fun updateLetterSpacing(letterSpacing: Double) {
        Logger.d("ReaderPreferencesUtil::updateLetterSpacing[$letterSpacing]")
        dataStore.edit { prefs ->
            prefs[LETTER_SPACING] = letterSpacing
        }
    }

    suspend fun updateLineHeight(lineHeight: Double) {
        Logger.d("ReaderPreferencesUtil::updateLineHeight[$lineHeight]")
        dataStore.edit { prefs ->
            prefs[LINE_HEIGHT] = lineHeight
        }
    }

    suspend fun updateFontSize(fontSize: Double) {
        Logger.d("ReaderPreferencesUtil::updateFontSize[$fontSize]")
        dataStore.edit { prefs ->
            prefs[FONT_SIZE] = fontSize
        }
    }

    suspend fun updateReaderBgImage(path: String) {
        Logger.d("ReaderPreferencesUtil::updateReaderBgImage[$path]")
        dataStore.edit { prefs ->
            prefs[BACKGROUND_IMAGE] = path
        }
    }

    suspend fun updateLeftHandMode(isLeftHandedMode: Boolean) {
        Logger.d("ReaderPreferencesUtil::updateLeftHandMode[$isLeftHandedMode]")
        dataStore.edit { prefs ->
            prefs[LEFT_HANDED_MODE] = isLeftHandedMode
        }
    }

    suspend fun updateClickAreaMode(clickAreaMode: Int) {
        Logger.d("ReaderPreferencesUtil::updateClickAreaMode[$clickAreaMode]")
        dataStore.edit { prefs ->
            prefs[CLICK_AREA_MODE] = clickAreaMode
        }
    }

    suspend fun updateAnimSpeed(animSpeed: Int) {
        dataStore.edit { prefs ->
            prefs[ANIMATION_SPEED] = animSpeed
        }
    }

    suspend fun updateScrollType(scrollType: Int) {
        Logger.d("ReaderPreferencesUtil::updateScrollType[$scrollType]")
        dataStore.edit { prefs ->
            prefs[SCROLL] = scrollType
        }
    }

    /**
     * 双列显示开关（全局阅读设置，不进 per-book override）。
     * 与 [updateScrollType] 同为全局模式：直接写 DataStore，不经过 perBookConfigRepo.saveSnapshot。
     */
    suspend fun updateDualColumn(enabled: Boolean) {
        Logger.d("ReaderPreferencesUtil::updateDualColumn[$enabled]")
        dataStore.edit { prefs ->

            prefs[COLUMNS] = if (enabled) 2 else 1
        }
    }

    suspend fun updateKeepScreenOn(isKeepScreenOn:Boolean) {
        Logger.d("ReaderPreferencesUtil::updateKeepScreenOn[$isKeepScreenOn]")
        dataStore.edit { prefs ->
            prefs[KEEP_SCREEN_ON] = isKeepScreenOn
        }
    }

    suspend fun updateVolumeKeyPageTurning(isVolumeKeyPageTurning:Boolean) {
        Logger.d("ReaderPreferencesUtil::updateVolumeKeyPageTurning[$isVolumeKeyPageTurning]")
        dataStore.edit { prefs ->
            prefs[VOLUME_KEY_PAGE_TURNING] = isVolumeKeyPageTurning
        }
    }

    suspend fun updateColorHistory(colorHistory: List<Color>) {
        Logger.d("ReaderPreferencesUtil::updateColorHistory[$colorHistory]")
        dataStore.edit { prefs ->
            prefs[COLOR_HISTORY] = serializeColorHistory(colorHistory)
        }
    }

    suspend fun updateFontPrefs(font :String , fontVariant : String) {
        Logger.d("ReaderPreferencesUtil::updateFontPrefs[$font, $fontVariant]")
        dataStore.edit { prefs ->
            prefs[FONT_FAMILY] = font
            prefs[FONT_VARIANT] = fontVariant
        }
    }

    /**
     * 批量更新阅读偏好（C-01 修复 + Q-10 关键）。
     *
     * 单次 [dataStore.edit] 原子写入全部字段 → 单次 Flow emit → 单次重排版（loadContent 版本锁收敛）。
     * 切主题（switchTheme）必须用本方法，禁止逐字段调用 updateXxx setter（否则 N 次磁盘写 + N 次 upStyle 主线程卡顿）。
     *
     * 字段清单与 [ReaderPreferences] data class 完全对齐（C-01），含 readerThemeId，无 lineSpacingExtra 残留。
     */
    suspend fun updatePreferences(newPreferences: ReaderPreferences) {
        Logger.d("ReaderPreferencesUtil::updatePreferences[$newPreferences]")
        dataStore.edit { preferences ->
            // Font Settings
            preferences[FONT_SIZE] = newPreferences.fontSize
            preferences[FONT_FAMILY] = newPreferences.font
            preferences[FONT_BOLD] = newPreferences.fontBold
            preferences[FONT_VARIANT] = newPreferences.fontVariant
            preferences[TITLE_FONT_SIZE] = newPreferences.titleSize
            preferences[TITLE_TOP_SPACING] = newPreferences.titleTopSpacing
            preferences[TITLE_BOTTOM_SPACING] = newPreferences.titleBottomSpacing
            preferences[LETTER_SPACING] = newPreferences.letterSpacing
            preferences[LINE_HEIGHT] = newPreferences.lineHeight
            preferences[PAGE_HORIZONTAL_MARGINS] = newPreferences.pageHorizontalMargins
            preferences[PAGE_VERTICAL_MARGINS] = newPreferences.pageVerticalMargins
            preferences[PARAGRAPH_INDENT] = newPreferences.paragraphIndent
            preferences[PARAGRAPH_SPACING] = newPreferences.paragraphSpacing
            preferences[WORD_SPACING] = newPreferences.wordSpacing
            preferences[FORCE_ALIGN_OVERRIDE] = newPreferences.forceAlignOverride
            preferences[USER_TEXT_ALIGN] = newPreferences.userTextAlign

            // ui Settings
            preferences[BACKGROUND_COLOR] = newPreferences.backgroundColor
            preferences[BACKGROUND_IMAGE] = newPreferences.backgroundImage
            preferences[TEXT_COLOR] = newPreferences.textColor
            preferences[COLOR_HISTORY] = serializeColorHistory(newPreferences.colorHistory)
            // readerThemeId 可空：null 表示"未选主题"（Q-02-B）。DataStore stringPreferencesKey 不支持 null，
            // 故用 remove 表示 null（读取时 preferences[READER_THEME_ID] 对不存在的 key 返回 null）。
            val themeId = newPreferences.readerThemeId
            if (themeId == null) {
                preferences.remove(READER_THEME_ID)
            } else {
                preferences[READER_THEME_ID] = themeId
            }
            preferences[READER_THEME_MODE] = newPreferences.readerThemeMode.name

            // Reader Settings
            preferences[KEEP_SCREEN_ON] = newPreferences.keepScreenOn
            preferences[TAP_NAVIGATION] = newPreferences.tapNavigation
            preferences[SCROLL] = newPreferences.scroll
            preferences[ANIMATION_SPEED] = newPreferences.animationSpeed
            preferences[READING_PROGRESSION] = newPreferences.readingProgression.name
            preferences[VERTICAL_TEXT] = newPreferences.verticalText
            preferences[PUBLISHER_STYLES] = newPreferences.publisherStyles
            preferences[TEXT_NORMALIZATION] = newPreferences.textNormalization
            preferences[VOLUME_KEY_PAGE_TURNING] = newPreferences.volumeKeyPageTurning
            preferences[CLICK_AREA_MODE] = newPreferences.clickAreaMode
            preferences[LEFT_HANDED_MODE] = newPreferences.leftHandedMode
            preferences[BRIGHTNESS] = newPreferences.brightness
            preferences[BRIGHTNESS_SET] = newPreferences.brightnessSet
            preferences[COLUMNS] = newPreferences.columns
        }
    }

    /** 持久化阅读主题模式（LIGHT/DARK/AUTO）。主题切换/联动由 ViewModel 层处理。 */
    suspend fun updateReaderThemeMode(mode: ReaderThemeMode) {
        Logger.d("ReaderPreferencesUtil::updateReaderThemeMode[$mode]")
        dataStore.edit { preferences ->
            preferences[READER_THEME_MODE] = mode.name
        }
    }


    suspend fun resetReadUiPreferences() {
        dataStore.edit { preferences ->
            preferences[FONT_SIZE] = defaultPreferences.fontSize.toDouble()
            preferences[LINE_HEIGHT] = defaultPreferences.lineHeight.toDouble()
            preferences[LETTER_SPACING] = defaultPreferences.letterSpacing.toDouble()
            preferences[WORD_SPACING] = defaultPreferences.wordSpacing.toDouble()
            preferences[TITLE_FONT_SIZE] = defaultPreferences.titleSize.toDouble()

            preferences[FONT_FAMILY] = defaultPreferences.font
            preferences[FONT_BOLD] = defaultPreferences.fontBold
            preferences[FONT_VARIANT] = defaultPreferences.fontVariant

            preferences[PAGE_HORIZONTAL_MARGINS] = defaultPreferences.pageHorizontalMargins.toDouble()
            preferences[PAGE_VERTICAL_MARGINS] = defaultPreferences.pageVerticalMargins.toDouble()
            preferences[PARAGRAPH_INDENT] = defaultPreferences.paragraphIndent.toDouble()
            preferences[PARAGRAPH_SPACING] = defaultPreferences.paragraphSpacing.toDouble()
            preferences[FORCE_ALIGN_OVERRIDE] = defaultPreferences.forceAlignOverride
            preferences[USER_TEXT_ALIGN] = defaultPreferences.userTextAlign

            preferences[TITLE_TOP_SPACING] = defaultPreferences.titleTopSpacing.toDouble()
            preferences[TITLE_BOTTOM_SPACING] = defaultPreferences.titleBottomSpacing.toDouble()

            preferences[BACKGROUND_COLOR] = defaultPreferences.backgroundColor
            preferences[BACKGROUND_IMAGE] = defaultPreferences.backgroundImage
            preferences[TEXT_COLOR] = defaultPreferences.textColor
            // Q-02-B：重置 UI 偏好时同时清除主题选中态（readerThemeId=null → 主题卡无选中）
            preferences.remove(READER_THEME_ID)
            preferences[READER_THEME_MODE] = defaultPreferences.readerThemeMode.name
        }
    }

    suspend fun resetReaderPreferences() {
        Logger.d("ReaderPreferencesUtil::resetReaderPreferences")
        dataStore.edit { preferences ->
            preferences[KEEP_SCREEN_ON] = defaultPreferences.keepScreenOn
            preferences[SCROLL] = defaultPreferences.scroll
            preferences[ANIMATION_SPEED] = defaultPreferences.animationSpeed
            preferences[TAP_NAVIGATION] = defaultPreferences.tapNavigation
            preferences[READING_PROGRESSION] = defaultPreferences.readingProgression.name
            preferences[VERTICAL_TEXT] = defaultPreferences.verticalText
            preferences[PUBLISHER_STYLES] = defaultPreferences.publisherStyles
            preferences[TEXT_NORMALIZATION] = defaultPreferences.textNormalization
            preferences[VOLUME_KEY_PAGE_TURNING] = defaultPreferences.volumeKeyPageTurning
            preferences[CLICK_AREA_MODE] = defaultPreferences.clickAreaMode
            preferences[LEFT_HANDED_MODE] = defaultPreferences.leftHandedMode
            preferences[BRIGHTNESS] = defaultPreferences.brightness
            preferences[BRIGHTNESS_SET] = defaultPreferences.brightnessSet
            // dualColumn 与 scroll 同属 reader 行为组（resetReadUiPreferences 不处理：双列是阅读行为，不是 UI 外观）
            preferences[COLUMNS] = defaultPreferences.columns
        }
    }

    suspend fun updateBrightness(brightness: Float, brightnessSet: Boolean) {
        Logger.d("ReaderPreferencesUtil::updateBrightness[$brightness, $brightnessSet]")
        dataStore.edit { prefs ->
            prefs[BRIGHTNESS] = brightness
            prefs[BRIGHTNESS_SET] = brightnessSet
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