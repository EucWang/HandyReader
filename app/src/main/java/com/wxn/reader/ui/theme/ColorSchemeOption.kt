package com.wxn.reader.ui.theme

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.wxn.reader.R

/**
 * The single source of truth for the app's color schemes.
 *
 * Each entry is identified by a neutral [persistedKey] (e.g. "pink", "sepia", "dynamic") that is
 * what actually gets stored in DataStore. This is deliberately *not* the legacy "[Light|Dark] Xxx"
 * form: switching the system dark mode must NOT rewrite the user's stored choice (that was the
 * P0 side-effect fixed by this refactor). Light/Dark is now resolved at runtime from [appTheme]
 * + system state, never persisted as part of the scheme key.
 *
 * Migrating existing users: [fromPersistedKey] maps the old "[Light|Dark] Xxx" keys to the new
 * neutral ids on read (zero migration mechanism — DataStore has no schema version).
 *
 * The enum never holds a [Context]; [resolve] takes it as a parameter so Dynamic can query the
 * system, without leaking the Activity into a singleton (the enum instance outlives any Activity).
 */
enum class ColorSchemeOption(
    val persistedKey: String,
    @param:StringRes val displayNameRes: Int,
    internal val lightScheme: ColorScheme?,
    internal val darkScheme: ColorScheme?,
) {
    DYNAMIC("dynamic", R.string.theme_dynamic, null, null),
    DEFAULT("default", R.string.theme_monochrome, LightColorScheme, DarkColorScheme),
    GREY("grey", R.string.theme_twilight, LightGreyScheme, DarkGreyScheme),
    SEPIA("sepia", R.string.theme_sepia, LightSepiaScheme, DarkSepiaScheme),
    PARCHMENT("parchment", R.string.theme_parchment, LightParchmentScheme, DarkParchmentScheme),
    YELLOW("yellow", R.string.theme_pastel_yellow, LightYellowScheme, DarkYellowScheme),
    TEAL("teal", R.string.theme_teal, LightTealScheme, DarkTealScheme),
    BLUE("blue", R.string.theme_lavender_blue, LightBlueScheme, DarkBlueScheme),
    PINK("pink", R.string.theme_pastel_pink, LightPinkScheme, DarkPinkScheme),
    PURPLE("purple", R.string.theme_violet, LightPurpleScheme, DarkPurpleScheme),
    RED("red", R.string.theme_crimson_red, LightRedScheme, DarkRedScheme),
    GREEN("green", R.string.theme_emerald_green, LightGreenScheme, DarkGreenScheme),
    ;

    /**
     * Resolve to a concrete [ColorScheme] for the current mode. Callers SHOULD memoize the result
     * (e.g. `remember(option, isDark) { option.resolve(...) }`) so Dynamic doesn't re-query the
     * system on every recomposition/animation frame.
     */
    fun resolve(isDark: Boolean, context: Context): ColorScheme = when (this) {
        DYNAMIC -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (isDark) DarkColorScheme else LightColorScheme
        }
        else -> (if (isDark) darkScheme else lightScheme)!!
    }

    companion object {
        /**
         * Deserialize a persisted key. Never throws — unknown/legacy values fall back to [DYNAMIC]
         * (the safest default: "follow the system"). Legacy "Light Xxx"/"Dark Xxx" keys (Phase 1
         * format) and bare "Light"/"Dark" are normalised to their neutral id; this branch can be
         * removed a couple of releases after all users have migrated.
         */
        fun fromPersistedKey(raw: String?): ColorSchemeOption {
            if (raw == null) return DYNAMIC
            entries.firstOrNull { it.persistedKey == raw }?.let { return it }
            val neutral = when {
                raw == "Dynamic" -> "dynamic"
                raw.startsWith("Light ") || raw.startsWith("Dark ") ->
                    raw.substringAfter(" ").lowercase()
                raw == "Light" || raw == "Dark" -> "default"
                else -> return DYNAMIC
            }
            return entries.firstOrNull { it.persistedKey == neutral } ?: DYNAMIC
        }
    }
}
