package com.wxn.reader.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

object M3Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    object Duration {
        const val SHORT = 150
        const val MEDIUM = 300
        const val LONG = 500
        const val EXTRA_LONG = 700
    }
}
