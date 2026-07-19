package com.wxn.base.util

import android.app.Activity
import android.content.ContentResolver
import android.provider.Settings
import android.view.WindowManager
import kotlin.math.ln
import kotlin.math.sqrt


object BrightnessHelper {

    private const val HLG_R = 0.5f
    private const val HLG_A = 0.17883277f
    private const val HLG_B = 0.28466892f
    private const val HLG_C = 0.55991073f

    /**
     * 设置当前窗口亮度
     * @param activity  目标 Activity
     * @param brightness 亮度值 0.0f ~ 1.0f
     */
    fun setWindowBrightness(activity: Activity, brightness: Float) {
        val lp = activity.window.attributes
        lp.screenBrightness = brightness
        activity.window.setAttributes(lp)
    }

    /**
     * 获取当前窗口亮度（若未设置过则返回 -1，表示跟随系统）
     */
    fun getWindowBrightness(activity: Activity): Float {
        val lp = activity.window.attributes
        return lp.screenBrightness
    }

    /**
     * 恢复当前窗口为跟随系统亮度
     */
    fun restoreSystemBrightness(activity: Activity) {
        val lp = activity.window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.setAttributes(lp)
    }

    /**
     * 获取系统亮度对应的滑块值 (0.0f ~ 1.0f)。
     *
     * 优先读取 "screen_brightness_float"（线性浮点值，API 26+ 可用），
     * 回退到 SCREEN_BRIGHTNESS 整数 (0-255)。
     * 两条路径均通过 AOSP Hybrid Log Gamma 转换为感知空间滑块值。
     *
     * @param contentResolver ContentResolver
     * @return 滑块值 0.0f ~ 1.0f，读取失败返回 fallback
     */
    fun getSystemBrightnessSliderValue(
        contentResolver: ContentResolver,
        fallback: Float = 0.5f
    ): Float {
        return try {
            val linear = Settings.System.getFloat(
                contentResolver, "screen_brightness_float"
            )
            linearToGamma(linear.coerceIn(0f, 1f))
        } catch (_: Settings.SettingNotFoundException) {
            try {
                val intVal = Settings.System.getInt(
                    contentResolver, Settings.System.SCREEN_BRIGHTNESS
                )
                linearToGamma(intVal / 255f)
            } catch (_: Settings.SettingNotFoundException) {
                fallback
            }
        }
    }

    /**
     * AOSP Hybrid Log Gamma electro-optical transfer function (inverse).
     *
     * Converts a linear brightness fraction [0,1] to the gamma (perceptual) space [0,1]
     * that the system brightness slider uses.
     *
     * Source: com.android.settingslib.display.BrightnessUtils
     */
    private fun linearToGamma(linear: Float): Float {
        val normalizedVal = linear.coerceIn(0f, 1f) * 12f
        val ret = if (normalizedVal <= 1f) {
            sqrt(normalizedVal) * HLG_R
        } else {
            HLG_A * ln(normalizedVal - HLG_B) + HLG_C
        }
        return ret.coerceIn(0f, 1f)
    }
}