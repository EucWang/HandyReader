package com.wxn.base.util

object ColorUtil {
    fun toColorInt(fontColor: String): Int? {
        var color = fontColor
        if (color.isNullOrEmpty()) return null
        if (color.startsWith("0x") || color.startsWith("0X")) {
            color = color.substring(2)
        } else if (color.startsWith("#")) {
            color = color.substring(1)
        }
        if (color.length != 6) {
            return null
        }
        val result = try {
            color.toInt(16)
        } catch (_: Exception) {
            null
        }
        return result
    }

}