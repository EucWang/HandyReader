package com.wxn.reader.data.dto

import com.wxn.bookread.data.model.preference.ReaderPreferences

/**
 * per-book 全量快照与 [ReaderPreferences] 之间的转换（v12 起，替代原 delta 合并逻辑）。
 *
 * v12 重构：原 [overrideWith] delta 合并（nullable 字段 Elvis 回退全局）已删除，
 * 改为全量快照的 saveCurrent / loadTarget 双向转换，对齐 [ReaderThemeConfigMapper] 的语义。
 *
 * 放在 **app 模块**（非 bookread）：形参类型 [PerBookThemeOverrideEntity] 在 app 模块，
 * 而 [ReaderPreferences] 在 bookread 模块；依赖方向是 `app → bookread`，bookread 不能反向引用 app 类。
 *
 * 类型转换列：[PerBookThemeOverrideEntity.forceAlignOverride] 是 `Int`（0/1），
 * 对应模型 `Boolean`；[userTextAlign]（模型 Int）同类型直接透传。
 */

/**
 * saveSnapshot：将当前阅读偏好存为 per-book × per-theme 的全量快照（对齐 `toReaderThemeConfigEntity`）。
 *
 * @param bookId 书籍 id
 * @param themeId 主题 id
 */
fun ReaderPreferences.toPerBookSnapshot(bookId: Long, themeId: String): PerBookThemeOverrideEntity =
    PerBookThemeOverrideEntity(
        bookId = bookId,
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
        userTextAlign = userTextAlign,
        forceAlignOverride = if (forceAlignOverride) 1 else 0,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

/**
 * loadSnapshot：将 per-book 快照应用到当前偏好（保留非主题字段），返回完整的 effective 偏好（对齐 `toReaderPreferences`）。
 *
 * 17 个主题字段从快照读取；其余字段（brightness/colorHistory/keepScreenOn 等非主题字段）从 [current] 保留。
 *
 * @param current 提供非主题字段的保留值（通常为全局 rawReaderPrefsFlow 当前值）
 */
fun PerBookThemeOverrideEntity.toReaderPreferences(current: ReaderPreferences): ReaderPreferences =
    current.copy(
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
        userTextAlign = userTextAlign,
        forceAlignOverride = forceAlignOverride != 0,
    )
