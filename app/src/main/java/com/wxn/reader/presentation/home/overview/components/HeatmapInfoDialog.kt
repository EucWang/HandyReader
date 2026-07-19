package com.wxn.reader.presentation.home.overview.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.reader.presentation.statistics.components.ReadingHeatmapLevel
import com.wxn.reader.presentation.statistics.components.color

/**
 * Info dialog for the home heatmap card.
 *
 * Mirrors the existing info-dialog pattern ([AlertDialog] with a single
 * `R.string.confirm` button, see `AppPickerSheet`), and renders the colour
 * legend plus a local-data privacy notice. Legend swatches reuse
 * [ReadingHeatmapLevel.color] so they stay in sync with the heatmap cells.
 *
 * @param onDismissRequest Called when the dialog is dismissed (confirm button,
 *  back press or tapping outside).
 */
@Composable
fun HeatmapInfoDialog(
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.reading_habits_info_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.reading_habits_info_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.heatmap_legend_header),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(8.dp))
                LegendList()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
private fun LegendList() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ReadingHeatmapLevel.entries.forEach { level ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            color = level.color(),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                Text(
                    text = level.label(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * Localized range label for a level.
 *
 * Mirrors the threshold semantics used by [ReadingHeatmapLevel.forReadingTime]:
 * the lightest tier reads as "< 15 min" (its upper bound), bounded tiers read
 * as "min – max min", and the deepest reads as "≥ 120 min".
 */
@Composable
private fun ReadingHeatmapLevel.label(): String = when (this) {
    ReadingHeatmapLevel.NONE -> stringResource(R.string.heatmap_level_none)
    ReadingHeatmapLevel.LIGHT ->
        stringResource(R.string.heatmap_level_under, maxMinutesExclusive!!)
    ReadingHeatmapLevel.DEEP ->
        stringResource(R.string.heatmap_level_over, minMinutes)
    else -> {
        val upperInclusive = maxMinutesExclusive!! - 1
        stringResource(R.string.heatmap_level_range, minMinutes, upperInclusive)
    }
}
