package com.wxn.reader.util

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color

@Stable
fun ColorA(color: Int, alpha: Float = 1F): Color {
    if (alpha <= 0F) return Color.Transparent
    if (alpha >= 1F) {
        if (color == 0) {
            return Color.Black
        } else if (color == 0xF || color == 0xFF || color == 0xFFF || color == 0xFFFF || color == 0xFFFFF || color == 0xFFFFFF) {
            return Color.White
        }
    }
    val a = (alpha * 0xFF).toInt()
    return when (color) {
        0 -> {
            Color(a shl 24)
        }

        in 1..0xF -> {
            Color((a shl 24) or (color shl 20) or (color shl 16) or (color shl 12) or (color shl 8) or (color shl 4))
        }

        in 1..0xFF -> {
            Color((a shl 24) or (color shl 16) or (color shl 8) or color)
        }

        in 1..0xFFF -> {
            val r = color shr 8
            val g = (color and 0xF0) shr 4
            val b = color and 0xF
            if (r == g && r == b) {
                val rr = r shl 4 or r
                val gg = g shl 4 or g
                val bb = b shl 4 or b
                Color((a shl 24) or (rr shl 16) or (gg shl 8) or bb)
            } else {
                Color((a shl 24) or color)
            }
        }

        else -> {
            Color(a shl 24 or color)
        }
    }
}

@Stable
fun ColorA(vararg colors: Int) = colors.map { ColorA(it) }

@Stable
val ColorBg = ColorA(0xF3)

@Stable
val Color333 = ColorA(0x3)

@Stable
val Color666 = ColorA(0x6)

@Stable
val Color999 = ColorA(0x9)

@Stable
val ColorFB9C21 = ColorA(0xFB9C21)

@Stable
val ColorFFABB2_FF6083 = ColorA(0xFFABB2, 0xFF6083)

@Stable
val ColorMessageSend = ColorA(0x95EC69)