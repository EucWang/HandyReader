package com.wxn.bookread.data.model.preference

import androidx.compose.ui.text.intl.Locale

data class TtsPreferences constructor(
    var localeCode: String,
    var speed: Float,
    var pitch: Float
) {

}