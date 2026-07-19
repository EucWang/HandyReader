package com.wxn.bookread.data.model.preference

/**
 * 阅读主题模式。
 *
 * 阅读模式独立于 App UI 主题模式（[com.wxn.reader.data.model.AppTheme]）：
 * - [LIGHT]：仅显示 5 个亮色主题。
 * - [DARK]：仅显示 5 个暗色主题。
 * - [AUTO]：跟随系统暗色信号（Compose `isSystemInDarkTheme()`），系统切深色时自动切到配对暗主题。
 *
 * 持久化为枚举 [name]（stringPreferencesKey），反序列化用 `runCatching { valueOf(it) }.getOrDefault(AUTO)` 容错。
 */
enum class ReaderThemeMode { LIGHT, DARK, AUTO }
