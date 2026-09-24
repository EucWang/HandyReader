package com.wxn.bookread.data.model.preference

data class TtsPreferences constructor(
    var localeCode: String,
    var speed: Float,
    var pitch: Float,
    var bufferedParagraphs: Int,
    var voiceName: String,
    var useBookLanguage: Boolean,
) {

}
