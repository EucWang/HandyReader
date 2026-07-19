package com.wxn.reader.presentation.statistics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wxn.reader.domain.model.ReadingActive
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Calendar

/**
 * 滚动 52 周热力图窗口起点:今天零点往前推 364 天(52*7)。
 * 这样热力图始终展示最近一年,跨年时上一年数据不会丢失。
 *
 * @param now 当前时刻,默认取系统时钟;可注入便于单元测试跨年/边界场景。
 */
fun rollingHeatmapStart(now: Calendar = Calendar.getInstance()): Long =
    (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -52 * 7)
    }.timeInMillis

@Composable
fun ReadingHeatmap(
    readingActivities: List<ReadingActive>,
    windowStartMillis: Long,
    modifier: Modifier = Modifier,
) {
    val currentCalendar = Calendar.getInstance()
    // 窗口起点由外部 ViewModel 提供单一数据源,与首页 SQL 的 date >= windowStartMillis
    // 引用同一个值,避免跨午夜/跨年/重组时两边各自算导致的"数据-格子"错位。
    val startCalendar = Calendar.getInstance().apply {
        timeInMillis = windowStartMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val daysInWeek = 7
    val totalDays = ChronoUnit.DAYS.between(
        startCalendar.toInstant(),
        currentCalendar.toInstant()
    ).toInt() + 1

    // 一周从周日开始(第0行=周日)。窗口起点是星期几,第一列就在上方留几个空格,
    // 使之后每列都是完整一周 → 每一行严格对应固定的星期几。
    // 注:Calendar.DAY_OF_WEEK 固定 1=周日…7=周六,与 locale/firstDayOfWeek 无关。
    val leadingBlanks = remember(windowStartMillis) { startCalendar.get(Calendar.DAY_OF_WEEK) - 1 }
    val weeksToShow = (leadingBlanks + totalDays + 6) / 7

    val sortedData = remember(readingActivities) {
        readingActivities.groupBy {
            Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    val calendar = startCalendar.clone() as Calendar
    var cellIndex = 0

    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .padding(top = 8.dp)
                .horizontalScroll(scrollState)
        ) {
            repeat(weeksToShow) {
                Column {
                    repeat(daysInWeek) {
                        if (cellIndex < leadingBlanks) {
                            // 窗口起点之前(更早的日期)的对齐占位,不推进日期,
                            // 仅用于把窗口起点对齐到它真实的星期几那一行。
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(1.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        } else {
                            val currentDate = calendar.timeInMillis
                            if (currentDate <= currentCalendar.timeInMillis) {
                                val currentLocalDate = Instant.ofEpochMilli(currentDate)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                val readingData = sortedData[currentLocalDate] ?: emptyList()
                                val readingTime = readingData.sumOf { it.readingTime  } / 60000 // Convert to minutes

                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(1.dp)
                                        .background(
                                            color = getColorForReadingTime(readingTime),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .padding(1.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f),
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                            calendar.add(Calendar.DAY_OF_YEAR, 1)
                        }
                        cellIndex++
                    }
                }
            }
        }
    }
}

@Composable
fun getColorForReadingTime(readingTimeMinutes: Long): Color =
    ReadingHeatmapLevel.forReadingTime(readingTimeMinutes).color()