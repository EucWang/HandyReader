package com.wxn.reader.presentation.statistics.components

data class ReadingTimeComponents(
    val hours: Long,
    val minutes: Long,
    val seconds: Long
)

fun parseReadingTime(timeInMillis: Long): ReadingTimeComponents {
    return ReadingTimeComponents(
        hours = timeInMillis / (1000 * 60 * 60),
        minutes = (timeInMillis % (1000 * 60 * 60)) / (1000 * 60),
        seconds = (timeInMillis % (1000 * 60)) / 1000
    )
}

fun formatReadingTimeShort(timeInMillis: Long, pattern: String = "%1\$d h %2\$d min"): String {
    val (h, m, _) = parseReadingTime(timeInMillis)
    return String.format(pattern, h, m)
}

fun formatReadingTimeFull(timeInMillis: Long, pattern: String = "%1\$d h %2\$d min %3\$d s"): String {
    val (h, m, s) = parseReadingTime(timeInMillis)
    return String.format(pattern, h, m, s)
}