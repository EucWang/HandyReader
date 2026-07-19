package com.wxn.reader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.materialkolor.dynamicColorScheme

/**
 * App color schemes.
 *
 * Design notes (see theme refactor, 2026-06):
 * - 7 themes (Violet / Teal / Pink / Yellow / Blue / Red / Green) are generated at runtime from
 *   a single seed color via MaterialKolor, which derives a full M3 `ColorScheme` (incl.
 *   surfaceContainer*, inverseSurface, surfaceDim, surfaceBright, …) that automatically
 *   satisfies M3 tonal relationships and WCAG contrast.
 * - 3 warm themes (Sepia / Parchment / Grey) keep a hand-tuned warm neutral background to
 *   preserve the "paper feel"; their M3 roles that aren't currently consumed are filled in and
 *   the 7 surface elevation tiers are hand-picked warm neutrals so card layering stays warm.
 * - The Default (monochrome) scheme keeps pure black/white primary but fills the M3 roles that
 *   are *not* consumed by existing UI (surfaceTint, inverse*, scrim, outlineVariant). The
 *   surfaceContainer family is intentionally left at the framework default to avoid changing
 *   the look of the 30+ places that already read it with the current neutral grey.
 * - The 12 persisted theme keys are unchanged so existing users are migrated transparently;
 *   only the underlying ColorScheme values change.
 * - Seed-based schemes use `by lazy` so the (relatively expensive) HCT computation runs only
 *   for the theme that's actually active, never all of them at startup.
 */

/**
 * Single source of truth for the color schemes is now [ColorSchemeOption]; the legacy
 * `VALID_COLOR_SCHEME_KEYS` set has been removed (Phase 2). Persisted keys are the neutral ids in
 * [ColorSchemeOption.persistedKey].
 */

// region — Seed-based scheme generation (MaterialKolor)

private fun seedLight(seed: Color): ColorScheme =
    dynamicColorScheme(seedColor = seed, isDark = false, isAmoled = false)

private fun seedDark(seed: Color): ColorScheme =
    dynamicColorScheme(seedColor = seed, isDark = true, isAmoled = false)

// endregion

// region — Default (monochrome) scheme
// Pure black/white kept on purpose (user-facing identity "Monochrome"); only the M3 roles that
// are NOT consumed by existing UI are filled in so card/dialog/bottom-sheet layering works,
// while surfaceContainer* stays at the framework default (protects 30+ existing call sites).

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBDBDBD),
    onPrimaryContainer = Color(0xFF212121),
    secondary = Color(0xFF616161),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E0E0),
    onSecondaryContainer = Color(0xFF424242),
    tertiary = Color(0xFF757575),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEEEEEE),
    onTertiaryContainer = Color(0xFF616161),
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF424242),
    surfaceTint = Color(0xFF000000),
    inverseSurface = Color(0xFF121212),
    inversePrimary = Color(0xFFFFFFFF),
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFCDAD7),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
    scrim = Color(0xFF000000),
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF424242),
    onPrimaryContainer = Color(0xFFE0E0E0),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF616161),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF9E9E9E),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF424242),
    onTertiaryContainer = Color(0xFFE0E0E0),
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF424242),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceTint = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFFEEEEEE),
    inversePrimary = Color(0xFF000000),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF616161),
    outlineVariant = Color(0xFF424242),
    scrim = Color(0xFF000000),
)
// endregion

// region — Violet (Purple) — seed #7B1FA2 (Purple800)
private val PurpleSeed = Color(0xFF7B1FA2)

val LightPurpleScheme by lazy { seedLight(PurpleSeed) }
val DarkPurpleScheme by lazy { seedDark(PurpleSeed) }
// endregion

// region — Teal — seed #00695C (Teal800, deepened from #009688 to meet contrast)
private val TealSeed = Color(0xFF00695C)

val LightTealScheme by lazy { seedLight(TealSeed) }
val DarkTealScheme by lazy { seedDark(TealSeed) }
// endregion

// region — Pink — seed #AD1457 (Pink800; original #FFC1CC gave only 1.5:1 contrast on white text)
private val PinkSeed = Color(0xFFAD1457)

val LightPinkScheme by lazy { seedLight(PinkSeed) }
val DarkPinkScheme by lazy { seedDark(PinkSeed) }
// endregion

// region — Yellow — seed #F9A825 (Yellow800; original #FFF9C4 was ~T95, essentially no chroma)
private val YellowSeed = Color(0xFFF9A825)

val LightYellowScheme by lazy { seedLight(YellowSeed) }
val DarkYellowScheme by lazy { seedDark(YellowSeed) }
// endregion

// region — Blue — seed #3F51B5 (Indigo800; original #E6E6FA was ~T90, essentially no chroma)
private val BlueSeed = Color(0xFF3F51B5)

val LightBlueScheme by lazy { seedLight(BlueSeed) }
val DarkBlueScheme by lazy { seedDark(BlueSeed) }
// endregion

// region — Red — seed #AD1457 rose (hue ≈ 345)
// Deliberately offset toward rose/magenta so the primary is distinguishable from the M3 error
// red (hue 0–10), resolving the action-vs-error colour-semantic clash.
private val RedSeed = Color(0xFFAD1457)

val LightRedScheme by lazy { seedLight(RedSeed) }
val DarkRedScheme by lazy { seedDark(RedSeed) }
// endregion

// region — Green — seed #2E7D32 (Green800; original #50C878 gave only 2.2:1 on white text)
private val GreenSeed = Color(0xFF2E7D32)

val LightGreenScheme by lazy { seedLight(GreenSeed) }
val DarkGreenScheme by lazy { seedDark(GreenSeed) }
// endregion

// region — Sepia (warm, paper-like). Hand-tuned to keep the warm background.
val LightSepiaScheme = lightColorScheme(
    primary = Color(0xFF8B4513),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEB887),
    onPrimaryContainer = Color(0xFF3E2723),
    secondary = Color(0xFFD2691E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4B5),
    onSecondaryContainer = Color(0xFF3E2723),
    tertiary = Color(0xFFCD853F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFAF0E6),
    onTertiaryContainer = Color(0xFF3E2723),
    background = Color(0xFFFDF5E6),
    onBackground = Color(0xFF3E2723),
    surface = Color(0xFFFDF5E6),
    onSurface = Color(0xFF3E2723),
    surfaceVariant = Color(0xFFE6D8CC),
    onSurfaceVariant = Color(0xFF4E342E),
    surfaceTint = Color(0xFF8B4513),
    inverseSurface = Color(0xFF3E2723),
    inversePrimary = Color(0xFFDEB887),
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFCDAD7),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8D6E63),
    outlineVariant = Color(0xFFE6D8CC),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFF3EAD6),
    surfaceBright = Color(0xFFFFFBF0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF3E1),
    surfaceContainer = Color(0xFFF8F0DE),
    surfaceContainerHigh = Color(0xFFF3EBD9),
    surfaceContainerHighest = Color(0xFFEEE4D0),
)

val DarkSepiaScheme = darkColorScheme(
    primary = Color(0xFFDEB887),
    onPrimary = Color(0xFF3E2723),
    primaryContainer = Color(0xFF8B4513),
    onPrimaryContainer = Color(0xFFFFF8DC),
    secondary = Color(0xFFFFE4B5),
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFFD2691E),
    onSecondaryContainer = Color(0xFFFFF8DC),
    tertiary = Color(0xFFFAF0E6),
    onTertiary = Color(0xFF3E2723),
    tertiaryContainer = Color(0xFFCD853F),
    onTertiaryContainer = Color(0xFFFFF8DC),
    background = Color(0xFF3E2723),
    onBackground = Color(0xFFFDF5E6),
    surface = Color(0xFF3E2723),
    onSurface = Color(0xFFFDF5E6),
    surfaceVariant = Color(0xFF4E342E),
    onSurfaceVariant = Color(0xFFE6D8CC),
    surfaceTint = Color(0xFFDEB887),
    inverseSurface = Color(0xFFFDF5E6),
    inversePrimary = Color(0xFF8B4513),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA1887F),
    outlineVariant = Color(0xFF4E342E),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF2C1B14),
    surfaceBright = Color(0xFF52402F),
    surfaceContainerLowest = Color(0xFF23150F),
    surfaceContainerLow = Color(0xFF372318),
    surfaceContainer = Color(0xFF3E2723),
    surfaceContainerHigh = Color(0xFF4A3128),
    surfaceContainerHighest = Color(0xFF56392F),
)
// endregion

// region — Grey / "Twilight" (cool neutral, comfortable low-glare Chrome)
val LightGreyScheme = lightColorScheme(
    primary = Color(0xFF263238),
    onPrimary = Color(0xFFECEFF1),
    primaryContainer = Color(0xFF455A64),
    onPrimaryContainer = Color(0xFFECEFF1),
    secondary = Color(0xFF37474F),
    onSecondary = Color(0xFFECEFF1),
    secondaryContainer = Color(0xFF546E7A),
    onSecondaryContainer = Color(0xFFECEFF1),
    tertiary = Color(0xFF78909C),
    onTertiary = Color(0xFF102027),
    tertiaryContainer = Color(0xFFCFD8DC),
    onTertiaryContainer = Color(0xFF263238),
    background = Color(0xFFECEFF1),
    onBackground = Color(0xFF263238),
    surface = Color(0xFFECEFF1),
    onSurface = Color(0xFF263238),
    surfaceVariant = Color(0xFFB0BEC5),
    onSurfaceVariant = Color(0xFF37474F),
    surfaceTint = Color(0xFF263238),
    inverseSurface = Color(0xFF263238),
    inversePrimary = Color(0xFFB0BEC5),
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFCDAD7),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF78909C),
    outlineVariant = Color(0xFFB0BEC5),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFDDE2E5),
    surfaceBright = Color(0xFFF9FBFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F5F7),
    surfaceContainer = Color(0xFFEDF0F2),
    surfaceContainerHigh = Color(0xFFE7EAEC),
    surfaceContainerHighest = Color(0xFFE1E5E7),
)

val DarkGreyScheme = darkColorScheme(
    primary = Color(0xFF90A4AE),
    onPrimary = Color(0xFF102027),
    primaryContainer = Color(0xFF455A64),
    onPrimaryContainer = Color(0xFFCFD8DC),
    secondary = Color(0xFF90A4AE),
    onSecondary = Color(0xFF102027),
    secondaryContainer = Color(0xFF546E7A),
    onSecondaryContainer = Color(0xFFECEFF1),
    tertiary = Color(0xFFB0BEC5),
    onTertiary = Color(0xFF263238),
    tertiaryContainer = Color(0xFF37474F),
    onTertiaryContainer = Color(0xFFECEFF1),
    background = Color(0xFF102027),
    onBackground = Color(0xFFCFD8DC),
    surface = Color(0xFF102027),
    onSurface = Color(0xFFCFD8DC),
    surfaceVariant = Color(0xFF37474F),
    onSurfaceVariant = Color(0xFFB0BEC5),
    surfaceTint = Color(0xFF90A4AE),
    inverseSurface = Color(0xFFECEFF1),
    inversePrimary = Color(0xFF263238),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF78909C),
    outlineVariant = Color(0xFF37474F),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF0B151B),
    surfaceBright = Color(0xFF2E3D46),
    surfaceContainerLowest = Color(0xFF060F13),
    surfaceContainerLow = Color(0xFF0E1C23),
    surfaceContainer = Color(0xFF102027),
    surfaceContainerHigh = Color(0xFF1B2A31),
    surfaceContainerHighest = Color(0xFF23333B),
)
// endregion

// region — Parchment (antique book feel). Hand-tuned warm background preserved.
val LightParchmentScheme = lightColorScheme(
    primary = Color(0xFF6D4C41),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7CCC8),
    onPrimaryContainer = Color(0xFF3E2723),
    secondary = Color(0xFF8D6E63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFEBE9),
    onSecondaryContainer = Color(0xFF3E2723),
    tertiary = Color(0xFFBCAAA4),
    onTertiary = Color(0xFF3E2723),
    tertiaryContainer = Color(0xFFF5F5F5),
    onTertiaryContainer = Color(0xFF3E2723),
    background = Color(0xFFFFFBE6),
    onBackground = Color(0xFF3E2723),
    surface = Color(0xFFFFFBE6),
    onSurface = Color(0xFF3E2723),
    surfaceVariant = Color(0xFFF0E8D9),
    onSurfaceVariant = Color(0xFF4E342E),
    surfaceTint = Color(0xFF6D4C41),
    inverseSurface = Color(0xFF3E2723),
    inversePrimary = Color(0xFFD7CCC8),
    error = Color(0xFFB00020),
    onError = Color.White,
    errorContainer = Color(0xFFFCDAD7),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8D6E63),
    outlineVariant = Color(0xFFF0E8D9),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFFF5EFCE),
    surfaceBright = Color(0xFFFFFFF6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF8E1),
    surfaceContainer = Color(0xFFFAF5D8),
    surfaceContainerHigh = Color(0xFFF4EFCB),
    surfaceContainerHighest = Color(0xFFEEE8BD),
)

val DarkParchmentScheme = darkColorScheme(
    primary = Color(0xFFD7CCC8),
    onPrimary = Color(0xFF3E2723),
    primaryContainer = Color(0xFF6D4C41),
    onPrimaryContainer = Color(0xFFFFFBE6),
    secondary = Color(0xFFEFEBE9),
    onSecondary = Color(0xFF3E2723),
    secondaryContainer = Color(0xFF8D6E63),
    onSecondaryContainer = Color(0xFFFFFBE6),
    tertiary = Color(0xFFF5F5F5),
    onTertiary = Color(0xFF3E2723),
    tertiaryContainer = Color(0xFFBCAAA4),
    onTertiaryContainer = Color(0xFFFFFBE6),
    background = Color(0xFF362F2D),
    onBackground = Color(0xFFFFFBE6),
    surface = Color(0xFF362F2D),
    onSurface = Color(0xFFFFFBE6),
    surfaceVariant = Color(0xFF4E342E),
    onSurfaceVariant = Color(0xFFF0E8D9),
    surfaceTint = Color(0xFFD7CCC8),
    inverseSurface = Color(0xFFFFFBE6),
    inversePrimary = Color(0xFF6D4C41),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFFA1887F),
    outlineVariant = Color(0xFF4E342E),
    scrim = Color(0xFF000000),
    surfaceDim = Color(0xFF262120),
    surfaceBright = Color(0xFF47403B),
    surfaceContainerLowest = Color(0xFF1F1A18),
    surfaceContainerLow = Color(0xFF322B29),
    surfaceContainer = Color(0xFF362F2D),
    surfaceContainerHigh = Color(0xFF423A35),
    surfaceContainerHighest = Color(0xFF4D453E),
)
// endregion

/**
 * Linearly interpolate between two [ColorScheme]s by [fraction] (0 → [start], 1 → [stop]).
 *
 * Only the roles actually consumed by the app are interpolated (K11 — avoids the cost of
 * recomputing all 48 roles per animation frame). Roles not listed here keep [start]'s value,
 * which is fine because no UI reads them during a transition.
 *
 * Driven by an `Animatable<Float>` in `ReadTheme` to animate theme switches (P2.3).
 */
fun lerpColorScheme(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme {
    val t = fraction.coerceIn(0f, 1f)
    return start.copy(
        primary = lerp(start.primary, stop.primary, t),
        onPrimary = lerp(start.onPrimary, stop.onPrimary, t),
        primaryContainer = lerp(start.primaryContainer, stop.primaryContainer, t),
        onPrimaryContainer = lerp(start.onPrimaryContainer, stop.onPrimaryContainer, t),
        secondary = lerp(start.secondary, stop.secondary, t),
        onSecondary = lerp(start.onSecondary, stop.onSecondary, t),
        secondaryContainer = lerp(start.secondaryContainer, stop.secondaryContainer, t),
        onSecondaryContainer = lerp(start.onSecondaryContainer, stop.onSecondaryContainer, t),
        tertiary = lerp(start.tertiary, stop.tertiary, t),
        onTertiary = lerp(start.onTertiary, stop.onTertiary, t),
        background = lerp(start.background, stop.background, t),
        onBackground = lerp(start.onBackground, stop.onBackground, t),
        surface = lerp(start.surface, stop.surface, t),
        onSurface = lerp(start.onSurface, stop.onSurface, t),
        surfaceVariant = lerp(start.surfaceVariant, stop.surfaceVariant, t),
        onSurfaceVariant = lerp(start.onSurfaceVariant, stop.onSurfaceVariant, t),
        surfaceTint = lerp(start.surfaceTint, stop.surfaceTint, t),
        surfaceContainerLowest = lerp(start.surfaceContainerLowest, stop.surfaceContainerLowest, t),
        surfaceContainerLow = lerp(start.surfaceContainerLow, stop.surfaceContainerLow, t),
        surfaceContainer = lerp(start.surfaceContainer, stop.surfaceContainer, t),
        surfaceContainerHigh = lerp(start.surfaceContainerHigh, stop.surfaceContainerHigh, t),
        surfaceContainerHighest = lerp(start.surfaceContainerHighest, stop.surfaceContainerHighest, t),
        error = lerp(start.error, stop.error, t),
        onError = lerp(start.onError, stop.onError, t),
        errorContainer = lerp(start.errorContainer, stop.errorContainer, t),
        onErrorContainer = lerp(start.onErrorContainer, stop.onErrorContainer, t),
        outline = lerp(start.outline, stop.outline, t),
        outlineVariant = lerp(start.outlineVariant, stop.outlineVariant, t),
    )
}

/** WCAG-style relative luminance of a color, in 0..1. Used to pick system-bar icon appearance. */
fun luminance(color: Color): Float {
    val r = channelLinear(color.red)
    val g = channelLinear(color.green)
    val b = channelLinear(color.blue)
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

private fun channelLinear(c: Float): Float {
    val d = c.toDouble()
    val linear = if (d <= 0.03928) d / 12.92 else Math.pow((d + 0.055) / 1.055, 2.4)
    return linear.toFloat()
}
