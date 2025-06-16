package com.wxn.reader.util

import java.text.NumberFormat

fun Number.format(maximumFractionDigits: Int, percent: Boolean = false): String {
    val format = if (percent) NumberFormat.getPercentInstance() else NumberFormat.getNumberInstance()
    format.maximumFractionDigits = maximumFractionDigits
    return format.format(this)
}
