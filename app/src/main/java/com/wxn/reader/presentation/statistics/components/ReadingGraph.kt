package com.wxn.reader.presentation.statistics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.wxn.reader.R
import com.wxn.reader.domain.model.ReadingActive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ReadingGraph(
    readingActivities: List<ReadingActive>
) {
    val dayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val fullDateFormat = remember { SimpleDateFormat("EEE. d MMMM", Locale.getDefault()) }

    var selectedDayIndex by remember { mutableIntStateOf(6) }

    // Calculate the start day (6 days ago)
    val startCalendar = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -6)
    }

    // Create a list of 7 days ending with the current day
    val daysOfWeek = (0..6).map { dayOffset ->
        val dayCalendar = (startCalendar.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        val readingActivityForDay = readingActivities.find {
            val activityCalendar = Calendar.getInstance().apply {
                timeInMillis = it.date
            }
            activityCalendar.get(Calendar.YEAR) == dayCalendar.get(Calendar.YEAR) &&
                    activityCalendar.get(Calendar.DAY_OF_YEAR) == dayCalendar.get(Calendar.DAY_OF_YEAR)
                    && it.readingTime >= 60000L
        }
        Pair(dayCalendar, readingActivityForDay?.readingTime ?: 0L)
    }

    val maxReadingTime = daysOfWeek.maxOfOrNull { it.second } ?: 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = fullDateFormat.format(daysOfWeek[selectedDayIndex].first.time), style = MaterialTheme.typography.bodyLarge)
        Text(text = formatReadingTimeShort(daysOfWeek[selectedDayIndex].second, stringResource(R.string.reading_time_short)))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            daysOfWeek.forEachIndexed { index, (dayCalendar, readingTime) ->
                val heightFactor =
                    if (maxReadingTime > 0) readingTime.toFloat() / maxReadingTime else 0f
                val dayName = dayFormat.format(dayCalendar.time)
                val isSelected = index == selectedDayIndex
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                        .weight(1f)

                ) {
                    Box(
                        modifier = Modifier
                            .height((heightFactor * 100).dp)
                            .width(24.dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.tertiary
                            )
                            .clickable {
                                selectedDayIndex = index
                            }
                    )
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = dayName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}