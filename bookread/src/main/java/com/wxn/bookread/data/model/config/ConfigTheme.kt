package com.wxn.bookread.data.model.config

import androidx.annotation.ColorInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// https://github.com/readium/readium-css/blob/master/css/src/modules/ReadiumCSS-day_mode.css
@ColorInt private val dayContentColor: Int = android.graphics.Color.parseColor("#121212")

@ColorInt private val dayBackgroundColor: Int = android.graphics.Color.parseColor("#FFFFFF")

// https://github.com/readium/readium-css/blob/master/css/src/modules/ReadiumCSS-night_mode.css
@ColorInt private val nightContentColor: Int = android.graphics.Color.parseColor("#FEFEFE")

@ColorInt private val nightBackgroundColor: Int = android.graphics.Color.parseColor("#000000")

// https://github.com/readium/readium-css/blob/master/css/src/modules/ReadiumCSS-sepia_mode.css
@ColorInt private val sepiaContentColor: Int = android.graphics.Color.parseColor("#121212")

@ColorInt private val sepiaBackgroundColor: Int = android.graphics.Color.parseColor("#faf4e8")

@Serializable
public enum class ConfigTheme(
    @ColorInt public val contentColor: Int,
    @ColorInt public val backgroundColor: Int
) {
    @SerialName("light")
    LIGHT(contentColor = dayContentColor, backgroundColor = dayBackgroundColor),

    @SerialName("dark")
    DARK(contentColor = nightContentColor, backgroundColor = nightBackgroundColor),

    @SerialName("sepia")
    SEPIA(contentColor = sepiaContentColor, backgroundColor = sepiaBackgroundColor);
}
