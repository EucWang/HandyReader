package com.wxn.base.util

import java.util.Locale

fun String.toLocale(): Locale? {
    if (this.isEmpty() || this.isBlank()) {
        return null
    }

    val str = this.trim()
    if (str.length < 2) {
        return null
    }

    val language = str.substring(0, 2)

    var country = ""
    var variant = ""
    if (str.length >= 5 && (str[2] == '_' || str[2] == '-')) {

        val splitor = if (str[2] == '_') {
            '_'
        } else {
            '-'
        }

        val lastSplitorIndex = str.lastIndexOf(splitor)
        val next = if (lastSplitorIndex > 2) {
            variant = str.substring(lastSplitorIndex + 1, str.length)

            str.substring(3, lastSplitorIndex)
        } else {
            str.substring(3, str.length)
        }

        if (next.length == 2)  {
            country = next.uppercase()
        } else {
            variant = next
        }
    }
//    Logger.d("LocaleExt:language=$language,country=$country,variant=$variant")

    return Locale(language, country)
}
