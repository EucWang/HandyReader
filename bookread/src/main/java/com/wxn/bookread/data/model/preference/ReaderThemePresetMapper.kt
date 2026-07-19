package com.wxn.bookread.data.model.preference

/**
 * 主题预设与 [ReaderPreferences] 之间的转换。
 *
 * 预设只覆盖"视觉+排版"字段（见 ReaderThemePreset 文档），其余字段（colorHistory/forceAlignOverride/userTextAlign/brightness 等）
 * 切主题时保留用户当前值。
 *
 * 用本预设覆盖 [current] 的预设字段，保留非预设字段不变，返回新的 [ReaderPreferences]。
 *
 * @param current 用户当前阅读偏好（提供非预设字段的保留值）
 * @return 应用本预设后的完整阅读偏好，readerThemeId 设为本预设的 themeId
 */
fun ReaderThemePreset.applyTo(current: ReaderPreferences): ReaderPreferences {
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
        // 不覆盖（保留用户值）：colorHistory, forceAlignOverride, userTextAlign, brightness, brightnessSet,
        //   keepScreenOn, scroll, animationSpeed, readingProgression, verticalText 等非视觉/非排版字段
    )
}
