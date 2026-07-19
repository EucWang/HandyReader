package com.wxn.reader.ui.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.wxn.reader.R

/**
 * 阅读取色器的预定义色（单一数据源：从 [ReaderThemePresets.ALL] 派生）。
 *
 * 取代 ReaderUISettings 内手抄的 `predefinedColors: Map<String, Color>`——后者手抄主题色值，
 * 曾因与预设不同步引发 Q-01 色值碰撞 bug（night_brown 人为改色规避）。本模型直接从预设派生，
 * 主题增改时自动同步，根治不同步。
 *
 * @param nameRes 色名资源，在 Composable 内用 `stringResource()` 解析（响应式，切语言刷新）。
 * @param value Compose Color（与持久化层 Int ARGB 经 [com.wxn.base.ext.toComposeColor] 同源，位级相等）。
 * @param role 该色的用途：[ColorRole.TEXT]（文字色墨水）或 [ColorRole.BACKGROUND]（背景色）。
 * @param isDark **该颜色本身的明暗**（非所属主题）：深色→true，浅色→false。
 *   用于 [visibleIn] 四象限过滤——深墨水配亮主题，浅墨水配暗主题；浅底配亮主题，深底配暗主题。
 */
data class PredefinedColor(
    @StringRes val nameRes: Int,
    val value: Color,
    val role: ColorRole,
    val isDark: Boolean,
)

/** 预定义色的用途。 */
enum class ColorRole { TEXT, BACKGROUND }

/**
 * 该预定义色在给定模式下是否可见（四象限过滤）。
 *
 * 过滤矩阵（modeIsDark = 当前阅读模式是否暗色）：
 * - 浅色模式文本：深墨水（TEXT && isDark）
 * - 浅色模式背景：浅底（BACKGROUND && !isDark）
 * - 深色模式文本：浅墨水（TEXT && !isDark）
 * - 深色模式背景：深底（BACKGROUND && isDark）
 *
 * 即：背景色的明暗 == 模式明暗；文本色的明暗 != 模式明暗（深底配浅字、浅底配深字）。
 *
 * @param modeIsDark 当前阅读模式是否为暗色（LIGHT→false，DARK→true，AUTO→系统当前）。
 * @param asBackground 该 ColorSection 是否为背景色区（true=背景，false=文字）。
 */
fun PredefinedColor.visibleIn(modeIsDark: Boolean, asBackground: Boolean): Boolean =
    if (asBackground) (isDark == modeIsDark) else (isDark != modeIsDark)

/**
 * 预定义色注册表：从 [ReaderThemePresets.ALL] 派生的取色器色板。
 *
 * 含 5 亮主题 + 5 暗主题的 textColor（墨水）与 backgroundColor（背景）。
 * UI 层（ColorSection）按 [visibleIn] 过滤后渲染——同一份色板，亮/暗模式各显示对应色项。
 *
 * 注意：多个预设可能共享同一色值（如 cream/classic/sepia 三者的文字色同为 #3E2723 深棕墨）。
 * 故派生后按 (role, value) 去重，避免取色器渲染出多个相同色块。
 */
object PredefinedColors {

    /**
     * 全部预定义色（文字墨水 + 背景），由 [ReaderThemePresets.ALL] 派生。
     *
     * 同一 (role, value) 只保留首次出现项——例如 cream/classic/sepia 共用的 #3E2723 深棕墨
     * 只产生一条 TEXT 条目，防止取色器出现重复色块。
     */
    val ALL: List<PredefinedColor> = buildList {
        ReaderThemePresets.ALL.forEach { entry ->
            val p = entry.preset
            // 文字墨水：textColor，role=TEXT。
            // isDark 语义：亮主题字色是"深墨"(isDark=true)，暗主题字色是"浅墨"(isDark=false)。
            // 故取预设 isDark 取反 —— 不用 luminance 计算（中灰字色 #B8B8B8/#B0B0B0 的 luminance≈0.48 踩 0.5 阈值会被误判）。
            add(PredefinedColor(inkNameRes(entry.themeId), Color(p.textColor), ColorRole.TEXT, !p.isDark))
            // 背景色：backgroundColor，role=BACKGROUND。
            // isDark 语义：亮主题背景是"浅底"(isDark=false)，暗主题背景是"深底"(isDark=true)，直接用预设 isDark。
            add(PredefinedColor(bgNameRes(entry.themeId), Color(p.backgroundColor), ColorRole.BACKGROUND, p.isDark))
        }
    }.distinctBy { it.role to it.value }

    /** themeId → 文字墨水色名资源。 */
    @StringRes
    private fun inkNameRes(themeId: String): Int = when (themeId) {
        ReaderThemePresets.ID_DEFAULT -> R.string.ink_charcoal      // #2C2C2C 炭灰
        ReaderThemePresets.ID_CREAM,
        ReaderThemePresets.ID_CLASSIC,
        ReaderThemePresets.ID_SEPIA -> R.string.ink_brown    // #3E2723 深棕墨
        ReaderThemePresets.ID_GREEN -> R.string.ink_forest          // #2E4A2E 森林墨
        ReaderThemePresets.ID_AMOLED_BLACK -> R.string.ink_light_grey // #B8B8B8 浅灰墨
        ReaderThemePresets.ID_NIGHT -> R.string.ink_warm_beige      // #B8A989 暖米墨
        ReaderThemePresets.ID_DARK_BLUE -> R.string.ink_mist_blue   // #8DA9C4 雾蓝墨
        ReaderThemePresets.ID_DARK_GREY -> R.string.ink_grey        // #B0B0B0 中灰墨
        ReaderThemePresets.ID_DARK_GREEN -> R.string.ink_light_green // #9CB89C 浅绿墨
        else -> R.string.ink_grey  // 兜底防崩溃
    }

    /** themeId → 背景色名资源。 */
    @StringRes
    private fun bgNameRes(themeId: String): Int = when (themeId) {
        ReaderThemePresets.ID_DEFAULT -> R.string.bg_warm_white     // #FAFAF7
        ReaderThemePresets.ID_CREAM -> R.string.bg_cream_white      // #FFFBF0
        ReaderThemePresets.ID_CLASSIC -> R.string.bg_paper_beige    // #F7F1E3
        ReaderThemePresets.ID_SEPIA -> R.string.bg_old_paper        // #F4ECD8
        ReaderThemePresets.ID_GREEN -> R.string.bg_bean_green       // #C7EDCC
        ReaderThemePresets.ID_AMOLED_BLACK -> R.string.bg_amoled_black // #000000
        ReaderThemePresets.ID_NIGHT -> R.string.bg_warm_brown       // #2C2419
        ReaderThemePresets.ID_DARK_BLUE -> R.string.bg_deep_blue    // #0F1A2E
        ReaderThemePresets.ID_DARK_GREY -> R.string.bg_soft_black   // #1C1C1E
        ReaderThemePresets.ID_DARK_GREEN -> R.string.bg_dark_green  // #1B2A1F
        else -> R.string.bg_warm_white  // 兜底防崩溃
    }
}
