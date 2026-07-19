package com.wxn.reader.data.model

import androidx.annotation.StringRes
import com.wxn.reader.R

enum class AppTheme(@param:StringRes val displayNameRes: Int) {
    SYSTEM(R.string.system_default),
    LIGHT(R.string.theme_light_mode),
    DARK(R.string.theme_dark_mode),
}
