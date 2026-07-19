package com.wxn.reader.data.model

import com.wxn.reader.ui.theme.ColorSchemeOption

data class ThemePreferences(
    val appTheme: AppTheme,
    val colorScheme: ColorSchemeOption,
    val homeBackgroundImage: String
)
