package com.wxn.base.ext

import android.os.Build
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color as ComposeColor

//在 Android 中，颜色值可以以多种格式表示，包括 rgb、argb、rrggbb、aarrggbb 等，Color.parseColor 支持这些格式
fun String.toColor(): Int? {
    if (this.isEmpty()) return null
    return try {
        val ret = parseCssColor() ?: this.toColorInt()
        ret
    } catch (ex: Exception) {
        null
    }
}

/**
 * 解析 CSS 颜色字符串为 ARGB Int（0xAARRGGBB）。
 *
 * 纯 Kotlin 实现（不依赖 Android 类），覆盖 CSS 函数格式：
 * - `rgb(r, g, b)` / `rgba(r, g, b, a)` —— a 为 0..1 浮点
 * - `hsl(h, s%, l%)` / `hsla(h, s%, l%, a)` —— h 为 0..360 角度
 *
 * hex（#RGB / #ARGB / #RRGGBB / #AARRGGBB）与命名色（red/transparent/…）
 * 留给 [androidx.core.graphics.toColorInt] 处理（见 [toColor]），
 * 本函数遇到这两种格式返回 null，由调用方回退到 toColorInt。
 *
 * 返回 null 表示“本函数无法识别”，不代表颜色非法——调用方应继续尝试其他解析器。
 */
fun String.parseCssColor(): Int? {
    val s = this.trim()
    if (s.isEmpty()) return null

    val lower = s.lowercase()
    val open = lower.indexOf('(')
    val close = lower.indexOf(')')
    // 仅处理 func(...) 形式；hex / named 走 toColorInt
    if (open <= 0 || close <= open) return null

    val name = lower.substring(0, open).trim()
    val args = s.substring(open + 1, close)
        .split(',')
        // 去空白 + 去掉百分号 + 容忍空段
        .map { it.trim().removeSuffix("%").trim() }

    return when (name) {
        "rgb", "rgba" -> parseRgbArgs(args)
        "hsl", "hsla" -> parseHslArgs(args)
        else -> null
    }
}

private fun parseRgbArgs(args: List<String>): Int? {
    if (args.size !in 3..4) return null
    val r = args[0].toFloatOrNull()?.toInt() ?: return null
    val g = args[1].toFloatOrNull()?.toInt() ?: return null
    val b = args[2].toFloatOrNull()?.toInt() ?: return null
    val a = if (args.size == 4) args[3].toFloatOrNull() ?: return null else 1f
    if (r !in 0..255 || g !in 0..255 || b !in 0..255) return null
    val alpha = (a.coerceIn(0f, 1f) * 255 + 0.5f).toInt()
    return (alpha shl 24) or (r shl 16) or (g shl 8) or b
}

private fun parseHslArgs(args: List<String>): Int? {
    if (args.size !in 3..4) return null
    val h = args[0].toFloatOrNull() ?: return null
    val s = args[1].toFloatOrNull()?.div(100f) ?: return null
    val l = args[2].toFloatOrNull()?.div(100f) ?: return null
    val a = if (args.size == 4) args[3].toFloatOrNull() ?: return null else 1f
    if (s !in 0f..1f || l !in 0f..1f) return null

    val (r, g, b) = hslToRgb(h, s, l)
    val alpha = (a.coerceIn(0f, 1f) * 255 + 0.5f).toInt()
    return (alpha shl 24) or (r shl 16) or (g shl 8) or b
}

/** HSL → RGB，返回 0..255 三通道。h 任意值（自动 mod 360），s/l 已归一化到 0..1。 */
private fun hslToRgb(h: Float, s: Float, l: Float): Triple<Int, Int, Int> {
    val hh = ((h % 360f) + 360f) % 360f / 60f
    val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
    val x = c * (1f - kotlin.math.abs(hh % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        hh < 1f -> Triple(c, x, 0f)
        hh < 2f -> Triple(x, c, 0f)
        hh < 3f -> Triple(0f, c, x)
        hh < 4f -> Triple(0f, x, c)
        hh < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun to255(v: Float) = ((v + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
    return Triple(to255(r1), to255(g1), to255(b1))
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
            val constructor =
                AndroidColor::class.java.getDeclaredConstructor(FloatArray::class.java)
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
    val a = ((argb shr 24) and 0xFF).toInt()
    val r = ((argb shr 16) and 0xFF).toInt()
    val g = ((argb shr 8) and 0xFF).toInt()
    val b = (argb and 0xFF).toInt()

    return String.format("#%02X%02X%02X%02X", a, r, g, b)
}

fun String.toComposeColor(): ComposeColor {
    if (this.isEmpty() || !this.startsWith("#") || this.length != 9) {
        return ComposeColor.White
    }
    val a = this.substring(1, 3).toIntOrNull(16) ?: return ComposeColor.White
    val r = this.substring(3, 5).toIntOrNull(16) ?: return ComposeColor.White
    val g = this.substring(5, 7).toIntOrNull(16) ?: return ComposeColor.White
    val b = this.substring(7, 9).toIntOrNull(16) ?: return ComposeColor.White

    return ComposeColor(r, g, b, a)
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