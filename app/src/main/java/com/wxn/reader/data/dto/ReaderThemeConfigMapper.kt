package com.wxn.reader.data.dto

import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.model.preference.ReaderThemePreset
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil

/**
 * [ReaderThemeConfigEntity]（Room 存档）与 [ReaderPreferences]（DataStore 工作态）之间的转换。
 *
 * 存档只覆盖"视觉+排版"字段（与 ReaderThemePreset 同集），其余字段在 loadTarget 时从当前 prefs 保留。
 */

/**
 * saveCurrent：将当前阅读偏好存为主题存档。
 *
 * @param themeId 存档归属的主题 id；默认取 readerThemeId，若为 null 则必须显式传入。
 */
fun ReaderPreferences.toReaderThemeConfigEntity(themeId: String? = readerThemeId): ReaderThemeConfigEntity {
    val resolvedThemeId = themeId ?: throw IllegalArgumentException("themeId must not be null when saving reader theme config")
    return ReaderThemeConfigEntity(
        themeId = resolvedThemeId,
        backgroundColor = backgroundColor,
        textColor = textColor,
        backgroundImage = backgroundImage,
        font = font,
        fontVariant = fontVariant,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        paragraphIndent = paragraphIndent,
        paragraphSpacing = paragraphSpacing,
        pageHorizontalMargins = pageHorizontalMargins,
        pageVerticalMargins = pageVerticalMargins,
        titleSize = titleSize,
        titleTopSpacing = titleTopSpacing,
        titleBottomSpacing = titleBottomSpacing,
        // v11：对齐字段纳入归档管控（随 per-book 特性同步，见设计方案 §二.0）
        userTextAlign = userTextAlign,
        forceAlignOverride = if (forceAlignOverride) 1 else 0,
        updatedAt = System.currentTimeMillis(),
    )
}

/**
 * loadTarget：将主题存档应用到当前偏好（保留非主题字段），返回可写入 DataStore 的完整偏好。
 *
 * v11 起：对齐字段（userTextAlign/forceAlignOverride）已纳入归档，**从 entity 读取**而非从 current 透传，
 * 使切主题能还原用户此前保存的对齐设置（与 per-book delta 覆盖范围对齐）。
 *
 * @param current 提供非主题字段（colorHistory/brightness 等）的保留值
 */
fun ReaderThemeConfigEntity.toReaderPreferences(current: ReaderPreferences): ReaderPreferences {
    return current.copy(
        readerThemeId = themeId,
        backgroundColor = backgroundColor,
        textColor = textColor,
        backgroundImage = backgroundImage,
        font = font,
        fontVariant = fontVariant,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        paragraphIndent = paragraphIndent,
        paragraphSpacing = paragraphSpacing,
        pageHorizontalMargins = pageHorizontalMargins,
        pageVerticalMargins = pageVerticalMargins,
        titleSize = titleSize,
        titleTopSpacing = titleTopSpacing,
        titleBottomSpacing = titleBottomSpacing,
        // v11：对齐字段从存档读取（不再透传 current，见设计方案 §二.0 第 3(b) 条）
        userTextAlign = userTextAlign,
        forceAlignOverride = forceAlignOverride != 0,
    )
}

/**
 * 从 [ReaderThemePreset] 构造存档实体（首次将预设固化为存档时用，或 resetTheme 后由 switchTheme 触发）。
 *
 * 预设不含对齐字段（对齐是强用户偏好、不随主题预设变化）。v11 起对齐纳入归档后，此处构造的存档实体
 * 两列填 [ReaderPreferencesUtil.defaultPreferences] 的对齐值（userTextAlign=4/Justify, forceAlignOverride=0），
 * 仅用于保证 archive 行 schema 完整性——**运行时切主题还原对齐不依赖 preset→archive 这条路径**
 * （switchTheme 的 loadTarget 优先读 archive 行，而 preset 转的 archive 行对齐=DEFAULT 4；
 * 用户真实的对齐偏好走 ReaderPreferences→archive 这条 mapper 3(a) 路径持久化）。
 */
fun ReaderThemePreset.toReaderThemeConfigEntity(): ReaderThemeConfigEntity {
    return ReaderThemeConfigEntity(
        themeId = themeId,
        backgroundColor = backgroundColor,
        textColor = textColor,
        backgroundImage = backgroundImage,
        font = font,
        fontVariant = fontVariant,
        fontSize = fontSize,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        paragraphIndent = paragraphIndent,
        paragraphSpacing = paragraphSpacing,
        pageHorizontalMargins = pageHorizontalMargins,
        pageVerticalMargins = pageVerticalMargins,
        titleSize = titleSize,
        titleTopSpacing = titleTopSpacing,
        titleBottomSpacing = titleBottomSpacing,
        // v11：预设不含对齐，填 defaultPreferences 值保 schema 完整（见设计方案 §二.0 第 3(c) 条）
        userTextAlign = ReaderPreferencesUtil.defaultPreferences.userTextAlign,
        forceAlignOverride = if (ReaderPreferencesUtil.defaultPreferences.forceAlignOverride) 1 else 0,
        updatedAt = System.currentTimeMillis(),
    )
}

/**
 * * 号标识用的浮点比较容差。
 *
 * 阅读器的字号/行距/边距由 Compose Slider（Float）调整，经 [Float.toDouble] 入库后，
 * 例如 `0.6f.toDouble() = 0.6000000238418579`，与预设 `0.6`（纯 Double）用 `==` 永不相等。
 * 若不设容差，"拖离再拖回原位"或"仅碰一下 Slider"都会被误判为已修改，* 号不消失。
 * `1e-6` 远小于 Slider 最小步长（0.1 量级），既消除 Float→Double 精度噪声，又不漏判真实微调（≥0.1）。
 */
private const val THEME_DIFF_EPSILON = 1e-6

private fun Double.differsFrom(other: Double): Boolean =
    kotlin.math.abs(this - other) > THEME_DIFF_EPSILON

/**
 * 逐字段判定存档是否偏离了预设（用于 * 号"已微调"标识）。
 *
 * 比较范围 = 15 个"视觉/排版"字段（与 [ReaderThemePreset] 实际定义的字段一一对应）：
 * - Int/String（背景/文字色、背景图、字体）精确比较；
 * - Double（字号/行距/边距等）用 [THEME_DIFF_EPSILON] 容差比较，规避 Float→Double 精度噪声。
 *
 * **v11 显式排除对齐两列**（设计方案 §二.0 第 4 条）：[ReaderThemeConfigEntity] v11 起新增的
 * `userTextAlign`/`forceAlignOverride` 虽存入存档，但**不参与**本比对——对齐是强用户偏好、不随主题预设变化，
 * 若纳入则 preset 路径构造的 archive 行（对齐=DEFAULT 4）与用户 prefs 路径构造的 archive 行（对齐=用户值）
 * 恒判"已修改"导致 * 号恒亮。preset 本身不含对齐字段，无法作为对齐的比对基准。
 */
fun ReaderThemeConfigEntity.differsFrom(preset: ReaderThemePreset): Boolean =
    backgroundColor != preset.backgroundColor ||
        textColor != preset.textColor ||
        backgroundImage != preset.backgroundImage ||
        font != preset.font ||
        fontVariant != preset.fontVariant ||
        fontSize.differsFrom(preset.fontSize) ||
        lineHeight.differsFrom(preset.lineHeight) ||
        letterSpacing.differsFrom(preset.letterSpacing) ||
        paragraphIndent.differsFrom(preset.paragraphIndent) ||
        paragraphSpacing.differsFrom(preset.paragraphSpacing) ||
        pageHorizontalMargins.differsFrom(preset.pageHorizontalMargins) ||
        pageVerticalMargins.differsFrom(preset.pageVerticalMargins) ||
        titleSize.differsFrom(preset.titleSize) ||
        titleTopSpacing.differsFrom(preset.titleTopSpacing) ||
        titleBottomSpacing.differsFrom(preset.titleBottomSpacing)
