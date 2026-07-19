package com.wxn.bookread.data.model.preference

/**
 * 阅读主题预设。
 *
 * 一个预设捆绑了一套完整的"阅读视觉+排版"配置：背景色、文字色、字体、字号、行距、段距、边距等。
 * 用户在主题选择器点击某个预设即一次性应用全部字段（[com.wxn.bookread.data.source.local.ReaderPreferencesUtil.updatePreferences] 单次批量写入）。
 *
 * 字段映射规则见评审文档 §4.3「ReaderThemePreset → ReaderPreferences 逐字段映射表」：
 * - 预设涵盖：font, fontVariant, fontSize, lineHeight, letterSpacing, paragraphIndent, paragraphSpacing,
 *   pageHorizontalMargins, pageVerticalMargins, backgroundColor, textColor, backgroundImage, titleSize, titleSpacing。
 * - 不涵盖（切主题时保留用户值）：colorHistory, forceAlignOverride, userTextAlign, brightness 等非视觉/非排版字段。
 *
 * 说明：本类位于 bookread 模块，不持有 displayNameRes（字符串资源在 app 模块）。
 * 主题的 i18n 显示名由 app 模块的预设注册表（ReaderThemePresets）映射 themeId → R.string.xxx。
 *
 * @param themeId 稳定唯一标识（持久化用，不可变更）。9 个枚举值见 app 模块 ReaderThemePresets。
 * @param backgroundColor 背景色 ARGB。
 * @param textColor 文字色（墨色）ARGB。
 * @param backgroundImage 背景图路径，所有预设恒为空串（预设不依赖用户自定义背景图）。
 * @param font 字体路径：系统字体（"serif"/"sans_serif"/"monospace"）或用户下载字体目录路径。
 * @param fontVariant 字体变体名（"regular"/"bold"/"italic"/"bolditalic"）。
 * @param fontSize 字号系数（0.5~2.0），最终字号 = fontSize × BASE_FONT_SIZE(16sp)。
 * @param lineHeight 行高系数（1.0~3.0），最终行高 = textHeight × lineHeight。
 * @param letterSpacing 字间距（em 值，0.0~1.0）。
 * @param paragraphIndent 段落首行缩进（字符宽度倍数，0.0~3.0）。
 * @param paragraphSpacing 段落间距（字高倍数，0.0~3.0）。
 * @param pageHorizontalMargins 左右边距系数（0.0~5.0），左右边距和 = margins × 0.1 × 屏宽。
 * @param pageVerticalMargins 上下边距系数（0.0~5.0），上下边距和 = margins × 0.1 × 屏高。
 * @param titleSize 标题字号系数（保留字段，方案A后标题随 fontSize 联动，此值不再被渲染层读取，恒为默认 1.0）。
 * @param titleTopSpacing 标题顶部间距（dp）。
 * @param titleBottomSpacing 标题底部间距（dp）。
 * @param isDark 该预设是否为暗色主题（代码常量，**不入库**：仅作为 5+5 分组与模式联动的依据）。
 * @param sortOrder 同模式内的稳定排序（亮色 0-4，暗色 5-9），用于主题选择器渲染顺序。
 */
data class ReaderThemePreset(
    val themeId: String,
    val backgroundColor: Int,
    val textColor: Int,
    val backgroundImage: String = "",
    val font: String,
    val fontVariant: String = "regular",
    val fontSize: Double,
    val lineHeight: Double,
    val letterSpacing: Double = 0.0,
    val paragraphIndent: Double = 2.0,
    val paragraphSpacing: Double,
    val pageHorizontalMargins: Double,
    val pageVerticalMargins: Double,
    @Suppress("unused") val titleSize: Double = 1.0,
    val titleTopSpacing: Double = 18.0,
    val titleBottomSpacing: Double = 15.0,
    val isDark: Boolean = false,
    val sortOrder: Int = 0,
)
