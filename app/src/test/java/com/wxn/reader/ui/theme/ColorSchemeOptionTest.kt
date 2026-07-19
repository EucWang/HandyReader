package com.wxn.reader.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests [ColorSchemeOption.fromPersistedKey] (migration safety) and [lerpColorScheme] endpoints.
 *
 * Robolectric is required because Compose [Color] / [lightColorScheme] rely on the Android
 * runtime (same reason as [ColorSchemeContrastTest]).
 */
@RunWith(RobolectricTestRunner::class)
class ColorSchemeOptionTest {

    @Test
    fun `fromPersistedKey null returns DYNAMIC`() {
        assertEquals(ColorSchemeOption.DYNAMIC, ColorSchemeOption.fromPersistedKey(null))
    }

    @Test
    fun `fromPersistedKey new neutral id maps directly`() {
        assertEquals(ColorSchemeOption.PINK, ColorSchemeOption.fromPersistedKey("pink"))
        assertEquals(ColorSchemeOption.SEPIA, ColorSchemeOption.fromPersistedKey("sepia"))
        assertEquals(ColorSchemeOption.DYNAMIC, ColorSchemeOption.fromPersistedKey("dynamic"))
        assertEquals(ColorSchemeOption.DEFAULT, ColorSchemeOption.fromPersistedKey("default"))
    }

    @Test
    fun `fromPersistedKey legacy 'Dynamic' maps to DYNAMIC`() {
        assertEquals(ColorSchemeOption.DYNAMIC, ColorSchemeOption.fromPersistedKey("Dynamic"))
    }

    @Test
    fun `fromPersistedKey legacy 'Light Xxx' normalises to neutral`() {
        assertEquals(ColorSchemeOption.PINK, ColorSchemeOption.fromPersistedKey("Light Pink"))
        assertEquals(ColorSchemeOption.SEPIA, ColorSchemeOption.fromPersistedKey("Light Sepia"))
        assertEquals(ColorSchemeOption.DEFAULT, ColorSchemeOption.fromPersistedKey("Light Default"))
    }

    @Test
    fun `fromPersistedKey legacy 'Dark Xxx' normalises to neutral`() {
        assertEquals(ColorSchemeOption.PINK, ColorSchemeOption.fromPersistedKey("Dark Pink"))
        assertEquals(ColorSchemeOption.TEAL, ColorSchemeOption.fromPersistedKey("Dark Teal"))
        assertEquals(ColorSchemeOption.GREEN, ColorSchemeOption.fromPersistedKey("Dark Green"))
    }

    @Test
    fun `fromPersistedKey bare Light Dark maps to DEFAULT`() {
        assertEquals(ColorSchemeOption.DEFAULT, ColorSchemeOption.fromPersistedKey("Light"))
        assertEquals(ColorSchemeOption.DEFAULT, ColorSchemeOption.fromPersistedKey("Dark"))
    }

    @Test
    fun `fromPersistedKey unknown value falls back to DYNAMIC`() {
        assertEquals(ColorSchemeOption.DYNAMIC, ColorSchemeOption.fromPersistedKey("bogus"))
        assertEquals(ColorSchemeOption.DYNAMIC, ColorSchemeOption.fromPersistedKey(""))
    }

    @Test
    fun `fromPersistedKey is symmetric with persistedKey for all entries`() {
        ColorSchemeOption.entries.forEach { option ->
            assertEquals(option, ColorSchemeOption.fromPersistedKey(option.persistedKey))
        }
    }

    @Test
    fun `lerpColorScheme at 0 equals start, at 1 equals stop`() {
        val start = lightColorScheme(primary = Color.Black, surface = Color.White)
        val stop = lightColorScheme(primary = Color.White, surface = Color.Black)
        assertEquals(start.primary, lerpColorScheme(start, stop, 0f).primary)
        assertEquals(stop.primary, lerpColorScheme(start, stop, 1f).primary)
    }

    @Test
    fun `lerpColorScheme clamps fraction`() {
        val start = lightColorScheme(primary = Color.Black)
        val stop = lightColorScheme(primary = Color.White)
        assertEquals(start.primary, lerpColorScheme(start, stop, -1f).primary)
        assertEquals(stop.primary, lerpColorScheme(start, stop, 2f).primary)
    }

    @Test
    fun `lerpColorScheme midpoint is strictly between start and stop`() {
        val start = lightColorScheme(primary = Color.Black)
        val stop = lightColorScheme(primary = Color.White)
        val mid = lerpColorScheme(start, stop, 0.5f).primary
        // Compose lerps in linear space, so the sRGB midpoint of black→white is ~0.388, not 0.5.
        // The meaningful invariant is that mid strictly lies between start (0) and stop (1).
        assertTrue("midpoint should be > start (0), was ${mid.red}", mid.red > 0f)
        assertTrue("midpoint should be < stop (1), was ${mid.red}", mid.red < 1f)
    }

    /**
     * Regression guard for a copy-paste typo where an interpolated role's *stop* source pointed
     * at the wrong role (e.g. `onSecondary = lerp(start.onSecondary, stop.secondary, t)`).
     * At t=1 every interpolated role must equal its counterpart in `stop`, otherwise the role is
     * silently overwritten at the end of a theme-switch animation — which manifested as the
     * FileTypeLabel text color collapsing into the box background color (secondary == onSecondary).
     */
    @Test
    fun `lerpColorScheme at 1 reaches every stop role`() {
        // Give every role a distinct, recognizable value so a wrong source is detectable.
        val start = lightColorScheme()
        val stop = lightColorScheme(
            primary = Color(0xFF111111), onPrimary = Color(0xFF222222),
            primaryContainer = Color(0xFF333333), onPrimaryContainer = Color(0xFF444444),
            secondary = Color(0xFF555555), onSecondary = Color(0xFF666666),
            secondaryContainer = Color(0xFF777777), onSecondaryContainer = Color(0xFF888888),
            tertiary = Color(0xFF999999), onTertiary = Color(0xFFAAAAAA),
            background = Color(0xFFBBBBBB), onBackground = Color(0xFFCCCCCC),
            surface = Color(0xFFDDDDDD), onSurface = Color(0xFFEEEEEE),
            surfaceVariant = Color(0xFFF0F0F0), onSurfaceVariant = Color(0xFFF1F1F1),
            surfaceTint = Color(0xFFF2F2F2),
            surfaceContainerLowest = Color(0xFFF3F3F3), surfaceContainerLow = Color(0xFFF4F4F4),
            surfaceContainer = Color(0xFFF5F5F5), surfaceContainerHigh = Color(0xFFF6F6F6),
            surfaceContainerHighest = Color(0xFFF7F7F7),
            error = Color(0xFFF8F8F8), onError = Color(0xF9F9F9F9),
            errorContainer = Color(0xFFAFAFAF), onErrorContainer = Color(0xFFBFBFBF),
            outline = Color(0xFFCFCFCF), outlineVariant = Color(0xFFDFDFDF),
        )
        val result = lerpColorScheme(start, stop, 1f)
        assertEquals(stop.primary, result.primary)
        assertEquals(stop.onPrimary, result.onPrimary)
        assertEquals(stop.primaryContainer, result.primaryContainer)
        assertEquals(stop.onPrimaryContainer, result.onPrimaryContainer)
        assertEquals(stop.secondary, result.secondary)
        assertEquals(stop.onSecondary, result.onSecondary)
        assertEquals(stop.secondaryContainer, result.secondaryContainer)
        assertEquals(stop.onSecondaryContainer, result.onSecondaryContainer)
        assertEquals(stop.tertiary, result.tertiary)
        assertEquals(stop.onTertiary, result.onTertiary)
        assertEquals(stop.background, result.background)
        assertEquals(stop.onBackground, result.onBackground)
        assertEquals(stop.surface, result.surface)
        assertEquals(stop.onSurface, result.onSurface)
        assertEquals(stop.surfaceVariant, result.surfaceVariant)
        assertEquals(stop.onSurfaceVariant, result.onSurfaceVariant)
        assertEquals(stop.surfaceTint, result.surfaceTint)
        assertEquals(stop.surfaceContainerLowest, result.surfaceContainerLowest)
        assertEquals(stop.surfaceContainerLow, result.surfaceContainerLow)
        assertEquals(stop.surfaceContainer, result.surfaceContainer)
        assertEquals(stop.surfaceContainerHigh, result.surfaceContainerHigh)
        assertEquals(stop.surfaceContainerHighest, result.surfaceContainerHighest)
        assertEquals(stop.error, result.error)
        assertEquals(stop.onError, result.onError)
        assertEquals(stop.errorContainer, result.errorContainer)
        assertEquals(stop.onErrorContainer, result.onErrorContainer)
        assertEquals(stop.outline, result.outline)
        assertEquals(stop.outlineVariant, result.outlineVariant)
    }
}
