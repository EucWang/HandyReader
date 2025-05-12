package com.wxn.bookread.data.model.config

import kotlinx.serialization.Serializable


/**
 * Typeface for a publication's text.
 *
 * For a list of vetted font families, see https://readium.org/readium-css/docs/CSS10-libre_fonts.
 */
@JvmInline
@Serializable
public value class ConfigFontFamily(public val name: String) {

    public companion object {
        // Generic font families
        // See https://www.w3.org/TR/css-fonts-4/#generic-font-families
        public val SERIF: ConfigFontFamily = ConfigFontFamily("serif")
        public val SANS_SERIF: ConfigFontFamily = ConfigFontFamily("sans-serif")
        public val CURSIVE: ConfigFontFamily = ConfigFontFamily("cursive")
        public val FANTASY: ConfigFontFamily = ConfigFontFamily("fantasy")
        public val MONOSPACE: ConfigFontFamily = ConfigFontFamily("monospace")

        // Accessibility fonts embedded with Readium
        public val ACCESSIBLE_DFA: ConfigFontFamily = ConfigFontFamily("AccessibleDfA")
        public val IA_WRITER_DUOSPACE: ConfigFontFamily = ConfigFontFamily("IA Writer Duospace")
        public val OPEN_DYSLEXIC: ConfigFontFamily = ConfigFontFamily("OpenDyslexic")

        fun from(font: String?) : ConfigFontFamily? {
            return when(font) {
                "serif" -> SERIF
                "sans-serif" -> SANS_SERIF
                "cursive" -> CURSIVE
                "fantasy" -> FANTASY
                "monospace" -> MONOSPACE
                "accessible-dfa" -> ACCESSIBLE_DFA
                "ia_writer_duospace" -> IA_WRITER_DUOSPACE
                "open_dyslexic" -> OPEN_DYSLEXIC
                else -> null
            }
        }
    }
}
