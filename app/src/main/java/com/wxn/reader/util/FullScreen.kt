package com.wxn.reader.util

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.wxn.reader.MainActivity

object FullScreenManager {
    private var activeCount = 0
    private var activeReadPageCount = 0
    // 最近一次 SetFullScreen 请求的 showSystemBars 值。
    // cleanup 时仅在最后一次是 show 时恢复系统栏，避免 config change（旋转）等场景无条件 show 导致闪烁。
    private var lastShowSystemBars = false

    @Synchronized
    fun registerFullScreen() {
        activeCount++
    }

    @Synchronized
    fun updateShowSystemBars(show: Boolean) {
        lastShowSystemBars = show
    }

    @Synchronized
    fun unregisterFullScreen(windowInsetsController: WindowInsetsControllerCompat) {
        activeCount--
        if (activeCount <= 0) {
            activeCount = 0
            // 仅在最后请求为 show 时才恢复，避免隐藏态下 cleanup 误 show 造成闪烁。
            if (lastShowSystemBars) {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    @Synchronized
    fun registerReadPage() {
        activeReadPageCount++
        MainActivity.inReadPage = true
    }

    @Synchronized
    fun unregisterReadPage() {
        activeReadPageCount--
        if (activeReadPageCount <= 0) {
            activeReadPageCount = 0
            MainActivity.inReadPage = false
        }
    }
}

@Composable
fun SetFullScreen(context: Context, showSystemBars: Boolean) {
    val window = (context as? Activity)?.window ?: return
    val windowInsetsController = remember(window) {
        WindowCompat.getInsetsController(window, window.decorView)
    }

    DisposableEffect(Unit) {
        FullScreenManager.registerFullScreen()
        onDispose {
            FullScreenManager.unregisterFullScreen(windowInsetsController)
        }
    }

    LaunchedEffect(showSystemBars, windowInsetsController) {
        // 记录最新请求值，供 unregisterFullScreen 判断是否需要恢复系统栏。
        FullScreenManager.updateShowSystemBars(showSystemBars)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (showSystemBars) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
