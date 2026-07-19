package com.wxn.reader.presentation.statistics.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

/**
 * Reading depth levels rendered by the heatmap.
 *
 * Acts as the single source of truth for both the heatmap cell colors
 * ([getColorForReadingTime]) and the legend swatches in [HeatmapInfoDialog],
 * so the two can never drift out of sync. Thresholds mirror the original
 * `when` branches in [ReadingHeatmap].
 */
@Stable
enum class ReadingHeatmapLevel(
    /** Lower bound (inclusive), in minutes. */
    val minMinutes: Long,
    /** Upper bound (exclusive), in minutes. `null` means open-ended (>= minMinutes). */
    val maxMinutesExclusive: Long?,
    /** Alpha applied to the base color. */
    val alpha: Float,
) {
    NONE(0, 1, 0.1f),
    LIGHT(1, 15, 0.2f),
    MODERATE(15, 30, 0.4f),
    ACTIVE(30, 60, 0.6f),
    INTENSE(60, 120, 0.8f),
    DEEP(120, null, 1.0f);

    companion object {
        /** Returns the level for the given reading time. Behaviour mirrors the original `when`. */
        fun forReadingTime(readingTimeMinutes: Long): ReadingHeatmapLevel = when {
            readingTimeMinutes <= 0L -> NONE
            readingTimeMinutes < 15 -> LIGHT
            readingTimeMinutes < 30 -> MODERATE
            readingTimeMinutes < 60 -> ACTIVE
            readingTimeMinutes < 120 -> INTENSE
            else -> DEEP
        }
    }
}

/**
 * Resolves the level color against the current theme. Kept `@Composable` so that
 * both heatmap cells and legend swatches track the same theme color source,
 * staying consistent across light/dark mode switches.
 */
@Composable
fun ReadingHeatmapLevel.color(): Color {
    val base = if (this == ReadingHeatmapLevel.NONE) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    return base.copy(alpha = alpha)
}
