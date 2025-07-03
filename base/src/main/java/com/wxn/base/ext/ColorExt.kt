package com.wxn.base.ext

import android.os.Build
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import androidx.compose.ui.graphics.Color as ComposeColor
import android.graphics.Color as AndroidColor


//在 Android 中，颜色值可以以多种格式表示，包括 rgb、argb、rrggbb、aarrggbb 等，Color.parseColor 支持这些格式
fun String.toColor(): Int? {
    return try {
        this.toColorInt()
    } catch (ex: Exception) {
        null
    }
}

/**
 * Universal way to get ARGB Int from any Color object (works on all API levels)
 */
fun AndroidColor.toCompatibleArgb(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Standard method on API ≥26
        this.toArgb()
    } else {
        // For API <26: Use reflection to access internal float array [r,g,b,a]
        try {
            val componentsField = AndroidColor::class.java.getDeclaredField("mComponents")
            componentsField.isAccessible = true
            val components = componentsField.get(this) as FloatArray

            val r = (components[0].coerceIn(0f, 1f) * 255).toInt()
            val g = (components[1].coerceIn(0f, 1f) * 255).toInt()
            val b = (components[2].coerceIn(0f, 1f) * 255).toInt()
            val a = (components[3].coerceIn(0f, 1f) * 255).toInt()

            (a shl 24) or (r shl 16) or (g shl 8) or b
        } catch (e: Exception) {
            // Fallback to black if reflection fails
            0xFF000000.toInt()
        }
    }
}

/**
 * Android <26的valueOf替代方案：
 * @param argb Int格式的颜色值（如0xFFRRGGBB或#AARRGGBB字符串转换后的整型）
 */
fun Int.toColor(): AndroidColor? {
    val argb = this
    val a = ((argb shr 24) and 0xFF) / 255f // Alpha [0..1]
    val r = ((argb shr 16) and 0xFF) / 255f // Red [0..1]
    val g = ((argb shr 8) and 0xFF) / 255f // Green [0..1]
    val b = (argb and 0xFF) / 255f          // Blue [0..1]

    val ret = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // API ≥26直接调用原生方法
        AndroidColor.valueOf(r, g, b, a)
    } else {
        // API <26构造等效的浮点数组
        val components = floatArrayOf(r, g, b, a)
        try {
            val constructor = AndroidColor::class.java.getDeclaredConstructor(FloatArray::class.java)
            constructor.isAccessible = true
            constructor.newInstance(components)
        } catch (e: Exception) {
//            throw RuntimeException("Failed to create legacy Color object", e)
            null
        }
    }
    return ret
}

fun Int.toComposeColor(): ComposeColor {
    return ComposeColor(this)
}

fun ComposeColor.toAndroidColor(): AndroidColor? = this.toArgb().toColor()

fun ComposeColor.toStringColor(): String {
    val argb = this.toArgb()
    val a  = ((argb shr 24) and 0xFF).toInt()
    val r = ((argb shr 16) and 0xFF).toInt()
    val g = ((argb shr 8) and 0xFF).toInt()
    val b = (argb and 0xFF).toInt()

    return String.format("#%02X%02X%02X%02X", a, r, g, b)
}



@Stable
fun ColorA(color: Int, alpha: Float = 1F): ComposeColor {
    if (alpha <= 0F) return ComposeColor.Transparent
    if (alpha >= 1F) {
        if (color == 0) {
            return ComposeColor.Black
        } else if (color == 0xF || color == 0xFF || color == 0xFFF || color == 0xFFFF || color == 0xFFFFF || color == 0xFFFFFF) {
            return ComposeColor.White
        }
    }
    val a = (alpha * 0xFF).toInt()
    return when (color) {
        0 -> {
            ComposeColor(a shl 24)
        }

        in 1..0xF -> {
            ComposeColor((a shl 24) or (color shl 20) or (color shl 16) or (color shl 12) or (color shl 8) or (color shl 4))
        }

        in 1..0xFF -> {
            ComposeColor((a shl 24) or (color shl 16) or (color shl 8) or color)
        }

        in 1..0xFFF -> {
            val r = color shr 8
            val g = (color and 0xF0) shr 4
            val b = color and 0xF
            if (r == g && r == b) {
                val rr = r shl 4 or r
                val gg = g shl 4 or g
                val bb = b shl 4 or b
                ComposeColor((a shl 24) or (rr shl 16) or (gg shl 8) or bb)
            } else {
                ComposeColor((a shl 24) or color)
            }
        }

        else -> {
            ComposeColor(a shl 24 or color)
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