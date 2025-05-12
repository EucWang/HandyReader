package com.wxn.base.ext

import android.graphics.Color
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.compose.ui.graphics.Color as ComposeColor
import android.graphics.Color as AndroidColor

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
            val componentsField = Color::class.java.getDeclaredField("mComponents")
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

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // API ≥26直接调用原生方法
        Color.valueOf(r, g, b, a)
    } else {
        // API <26构造等效的浮点数组
        val components = floatArrayOf(r, g, b, a)
        try {
            val constructor = Color::class.java.getDeclaredConstructor(FloatArray::class.java)
            constructor.isAccessible = true
            constructor.newInstance(components)
        } catch (e: Exception) {
//            throw RuntimeException("Failed to create legacy Color object", e)
            null
        }
    }
}

fun ComposeColor.toAndroidColor(): AndroidColor? = this.toArgb().toColor()
