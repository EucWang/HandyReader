package com.wxn.reader.ui.theme

import android.graphics.Color as AndroidColor
import androidx.annotation.StringRes
import com.wxn.reader.R
import com.wxn.bookread.data.model.preference.ReaderThemePreset

/**
 * 阅读主题预设注册表（app 模块单例）。
 *
 * 10 个预设（5 亮 + 5 暗）的完整定义：颜色（§3.2）+ 排版（§3.5.2）+ 明暗分组（isDark）+ 排序（sortOrder）。
 * - [ReaderThemePreset] 数据类在 bookread 模块（不持 i18n 资源）；
 * - 本类补充 [displayNameRes]（app 模块的字符串资源）并通过 [ReaderThemeEntry] 捆绑两者。
 *
 * 色值来源：评审文档 §3.2 完整色值表 + §3.5.2 排版参数表（4 轮评审定稿）。
 * 字体决策：Default=sans_serif（现代化默认，§3.5.4）；暖色书卷→serif，冷色现代→sans_serif。
 *
 * 5+5 配对表见 [PAIRING]（亮↔暗双向），用于 AUTO 模式系统暗色切换。
 *
 * themeId 与 Room 表 [com.wxn.reader.data.dto.ReaderThemeConfigEntity] 主键、DataStore readerThemeId 字段一致。
 */
data class ReaderThemeEntry(
    val preset: ReaderThemePreset,
    @StringRes val displayNameRes: Int,
) {
    val themeId: String get() = preset.themeId
}

object ReaderThemePresets {

    // 10 预设 themeId 常量（与 Room/DataStore 持久化值一致，不可变更）
    // ===== 亮色（sortOrder 0-4）=====
    const val ID_DEFAULT = "default"
    const val ID_CREAM = "cream"
    const val ID_CLASSIC = "classic"
    const val ID_SEPIA = "sepia"
    const val ID_GREEN = "green"
    // ===== 暗色（sortOrder 5-9）=====
    const val ID_AMOLED_BLACK = "amoled_black"
    const val ID_NIGHT = "night"
    const val ID_DARK_BLUE = "dark_blue"
    const val ID_DARK_GREY = "dark_grey"
    const val ID_DARK_GREEN = "dark_green"

    /**
     * 将 #RRGGBB 色值串解析为 Android ARGB Int（与 ReaderPreferences.backgroundColor/textColor 类型一致）。
     * 复用 android.graphics.Color.parseColor，确保色值在 sRGB 与渲染层（Paint.color）完全一致。
     */
    private fun argb(hex: String): Int = AndroidColor.parseColor(hex)

    /** 全部 10 个预设，按"亮色（0-4）→ 暗色（5-9）"顺序排列（主题选择器按模式过滤后渲染）。 */
    val ALL: List<ReaderThemeEntry> = listOf(
        // ===== 亮色主题（sortOrder 0-4）=====
        ReaderThemeEntry(
            preset = ReaderThemePreset(
                themeId = ID_DEFAULT,
                // §3.2: #FAFAF7 微暖白 / #2C2C2C 炭灰
                backgroundColor = argb("#FAFAF7"),
                textColor = argb("#2C2C2C"),
                // §3.5.2: Default 通用默认，老用户平滑升级
                font = "sans_serif",
                fontSize = 1.0,
                lineHeight = 1.5,
                paragraphSpacing = 0.6,
                pageHorizontalMargins = 1.5,
                pageVerticalMargins = 1.2,
                isDark = false,
                sortOrder = 0,
            ),
            displayNameRes = R.string.reader_theme_default,
        ),
        ReaderThemeEntry(
            preset = ReaderThemePreset(
                themeId = ID_CREAM,
                // §3.2: #FFFBF0 奶油白 / #3E2723 深棕墨
                backgroundColor = argb("#FFFBF0"),
                textColor = argb("#3E2723"),
                // §3.5.2: 日常长阅读，衬线增强书卷沉浸
                font = "serif",
                fontSize = 1.0,
                lineHeight = 1.5,
                paragraphSpacing = 0.7,
                pageHorizontalMargins = 1.6,
                pageVerticalMargins = 1.3,
                isDark = false,
                sortOrder = 1,
            ),
            displayNameRes = R.string.reader_theme_cream,
        ),
        ReaderThemeEntry(
            preset = ReaderThemePreset(
                themeId = ID_CLASSIC,
                // §3.2: #F7F1E3 仿纸米 / #3E2723 深棕墨
                backgroundColor = argb("#F7F1E3"),
                textColor = argb("#3E2723"),
                // §3.5.2: 实体书质感还原，段距略大分章感
                font = "serif",
                fontSize = 1.0,
                lineHeight = 1.5,
                paragraphSpacing = 0.8,
                pageHorizontalMargins = 1.6,
                pageVerticalMargins = 1.3,
                isDark = false,
                sortOrder = 2,
            ),
            displayNameRes = R.string.reader_theme_classic,
        ),
        ReaderThemeEntry(
            preset = ReaderThemePreset(
                themeId = ID_SEPIA,
                // §3.2: #F4ECD8 旧纸黄 / #3E2723 深棕墨
                backgroundColor = argb("#F4ECD8"),
                textColor = argb("#3E2723"),
                // §3.5.2: 复古/睡前，旧纸黄降蓝光，行高放宽降疲劳
                font = "serif",
                fontSize = 1.05,
                lineHeight = 1.6,
                paragraphSpacing = 0.8,
                pageHorizontalMargins = 1.6,
                pageVerticalMargins = 1.3,
                isDark = false,
                sortOrder = 3,
            ),
            displayNameRes = R.string.reader_theme_sepia,
        ),
        ReaderThemeEntry(
            preset = ReaderThemePreset(
                themeId = ID_GREEN,
                // §3.2: #C7EDCC 豆绿 / #2E4A2E 深森林墨
                backgroundColor = argb("#C7EDCC"),
                textColor = argb("#2E4A2E"),
                // §3.5.2: 经典护眼绿，网文/长篇连载
                font = "sans_serif",
                fontSize = 1.0,
                lineHeight = 1.5,
                paragraphSpacing = 0.7,
                pageHorizontalMargins = 1.5,
                pageVerticalMargins = 1.2,
                isDark = false,
                sortOrder = 4,
            ),
            displayNameRes = R.string.reader_theme_green,
        ),
        // ===== 暗色主题（sortOrder 5-9）=====
        ReaderThemeEntry(
            preset = ReaderThemePreset(
                themeId = ID_AMOLED_BLACK,
                // §3.2: #000000 极致黑 / #B8B8B8 浅灰墨（非纯白防刺眼）
                backgroundColor = argb("#000000"),
                textColor = argb("#B8B8B8"),
                // §3.5.2: AMOLED 省电纯黑，无衬线均衡可读，对标 dark_grey
                font = "sans_serif",
                fontSize = 1.05,
                lineHeight = 1.6,
                paragraphSpacing = 0.7,
                pageHorizontalMargins = 1.5,
                pageVerticalMargins = 1.2,
                isDark = true,
                sortOrder = 5,
            ),
            displayNameRes = R.string.reader_theme_amoled_black,
        ),
        ReaderThemeEntry(
            preset = ReaderThemePreset(
                themeId = ID_NIGHT,
                // §3.2: #2C2419 暖深棕 / #B8A989 暖米墨
                backgroundColor = argb("#2C2419"),
                textColor = argb("#B8A989"),
                // §3.5.2: 睡前暗环境，字号+10%、行高最大1.7、暖棕减蓝光
                font = "serif",
                fontSize = 1.1,
                lineHeight = 1.7,
                paragraphSpacing = 0.8,
                pageHorizontalMargins = 1.6,
                pageVerticalMargins = 1.3,
                isDark = true,
                sortOrder = 6,
            ),
            displayNameRes = R.string.reader_theme_night,
        ),
        ReaderThemeEntry(
            preset = ReaderThemePreset(
                themeId = ID_DARK_BLUE,
                // §3.2: #0F1A2E 深海蓝 / #8DA9C4 雾蓝墨
                backgroundColor = argb("#0F1A2E"),
                textColor = argb("#8DA9C4"),
                // §3.5.2: 深蓝沉浸，科幻/技术类夜间阅读
                font = "sans_serif",
                fontSize = 1.05,
                lineHeight = 1.6,
                paragraphSpacing = 0.7,
                pageHorizontalMargins = 1.5,
                pageVerticalMargins = 1.2,
                isDark = true,
                sortOrder = 7,
            ),
            displayNameRes = R.string.reader_theme_dark_blue,
        ),
        ReaderThemeEntry(
            preset = ReaderThemePreset(
                themeId = ID_DARK_GREY,
                // §3.2: #1C1C1E 柔黑 / #B0B0B0 中灰墨
                backgroundColor = argb("#1C1C1E"),
                textColor = argb("#B0B0B0"),
                // §3.5.2: 通用柔黑（标准夜读），无衬线均衡可读
                font = "sans_serif",
                fontSize = 1.05,
                lineHeight = 1.6,
                paragraphSpacing = 0.7,
                pageHorizontalMargins = 1.5,
                pageVerticalMargins = 1.2,
                isDark = true,
                sortOrder = 8,
            ),
            displayNameRes = R.string.reader_theme_dark_grey,
        ),
        ReaderThemeEntry(
            preset = ReaderThemePreset(
                themeId = ID_DARK_GREEN,
                // §3.2: #1B2A1F 墨绿暗 / #9CB89C 浅绿墨
                backgroundColor = argb("#1B2A1F"),
                textColor = argb("#9CB89C"),
                // §3.5.2: 绿色护眼暗版，网文/长篇连载夜间，对标 green 暗色
                font = "sans_serif",
                fontSize = 1.05,
                lineHeight = 1.6,
                paragraphSpacing = 0.7,
                pageHorizontalMargins = 1.5,
                pageVerticalMargins = 1.2,
                isDark = true,
                sortOrder = 9,
            ),
            displayNameRes = R.string.reader_theme_dark_green,
        ),
    )

    /** 亮色主题（isDark=false），用于 LIGHT 模式 / 浅色模式过滤。 */
    val LIGHT_THEMES: List<ReaderThemeEntry> get() = ALL.filter { !it.preset.isDark }

    /** 暗色主题（isDark=true），用于 DARK 模式 / 深色模式过滤。 */
    val DARK_THEMES: List<ReaderThemeEntry> get() = ALL.filter { it.preset.isDark }

    /**
     * 亮↔暗配对表（双向）。用于 AUTO 模式系统暗色切换：亮主题切到对应暗主题，反之亦然。
     *
     * 配对逻辑（按色温/护眼场景对应）：
     * - default ↔ amoled_black（极致白↔极致黑，中性默认）
     * - cream ↔ night（暖色护眼，奶油白↔暖深棕）
     * - classic ↔ dark_blue（经典↔沉稳）
     * - sepia ↔ dark_grey（柔和↔中性）
     * - green ↔ dark_green（绿色护眼，亮↔暗）
     */
    private val PAIRING: Map<String, String> = mapOf(
        ID_DEFAULT to ID_AMOLED_BLACK,
        ID_CREAM to ID_NIGHT,
        ID_CLASSIC to ID_DARK_BLUE,
        ID_SEPIA to ID_DARK_GREY,
        ID_GREEN to ID_DARK_GREEN,
    )

    /** 查找配对主题 id（双向：亮→暗 或 暗→亮）。无配对返回 null。 */
    fun getPairedThemeId(themeId: String): String? =
        PAIRING[themeId] ?: PAIRING.entries.firstOrNull { it.value == themeId }?.key

    /** 按 themeId 查找预设条目。不存在返回 null。 */
    fun getById(themeId: String?): ReaderThemeEntry? =
        ALL.firstOrNull { it.themeId == themeId }

    /** 按 themeId 查找预设（仅数据，无显示名）。不存在返回 null。 */
    fun getPresetById(themeId: String?): ReaderThemePreset? = getById(themeId)?.preset

    /** 判断 themeId 是否为合法的预设 id。 */
    fun isValidId(themeId: String?): Boolean =
        themeId != null && ALL.any { it.themeId == themeId }
}
