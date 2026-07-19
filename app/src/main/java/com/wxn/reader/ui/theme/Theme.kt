package com.wxn.reader.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.reader.BookApplication
import com.wxn.reader.data.model.AppTheme
import androidx.compose.ui.graphics.toArgb


fun stringResource(@StringRes res: Int, vararg args: Any) : String {
    val str = BookApplication.app.applicationContext.getString(res, *args)
    return str
}

@Composable
fun ReadTheme(
    viewModel: AppThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val themePreferences by viewModel.themePreferences.collectAsStateWithLifecycle()
    val view = LocalView.current
    val activity = LocalActivity.current

    if (themePreferences != null) {
        val prefs = themePreferences!!
        val option = prefs.colorScheme

        val darkTheme = when (prefs.appTheme) {
            AppTheme.SYSTEM -> isSystemInDarkTheme()
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
            else -> isSystemInDarkTheme()
        }

        // Target scheme, memoized so Dynamic doesn't re-query the system every frame (N4).
        val context = LocalContext.current
        val targetScheme = remember(option, darkTheme, context) {
            option.resolve(isDark = darkTheme, context = context)
        }

        // P2.3 — animate the scheme transition. ColorScheme can't be animated directly, so drive
        // a Float progress and lerp the consumed roles between the previous and target scheme.
        // `animFrom` is the scheme we lerp away from; it's frozen while animating and updated to
        // the target once an animation completes (or to the current frame if interrupted).
        val progress = remember { Animatable(1f) }
        var animFrom by remember { mutableStateOf(targetScheme) }
        LaunchedEffect(targetScheme) {
            if (animFrom != targetScheme) {
                // If we're already mid-animation (progress < 1), freeze the current interpolated
                // frame as the new source so a rapid A→B→C chain bends smoothly (K7) rather than
                // snapping back to A. If we're at rest (progress == 1), animate from animFrom.
                if (progress.value < 1f) {
                    animFrom = lerpColorScheme(animFrom, targetScheme, progress.value)
                }
                progress.snapTo(0f)
                progress.animateTo(1f, animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
                animFrom = targetScheme
            }
        }
        val animatedScheme = lerpColorScheme(animFrom, targetScheme, progress.value)

        // System bars (P2.8). Judge icon appearance from the surface luminance (N3: memoized, and
        // only applied once the animation settles so we don't repaint system bars every frame).
        // 底色统一用 surfaceContainer，与 TopToolbar / BottomToolbar / 首页 NavigationBar 的容器色对齐，
        // 确保系统栏与各栏背景在所有主题下无缝衔接（N3: memoized，且仅在动画结束后写入，避免逐帧重绘）。
        val barsColor = animatedScheme.surfaceContainer
        val surfaceIsLight = remember(barsColor) { luminance(barsColor) > 0.5f }
        if (!view.isInEditMode) {
            LaunchedEffect(progress.value >= 1f) {
                if (progress.value >= 1f) {
                    activity?.window?.let { window ->
                        window.statusBarColor = barsColor.toArgb()
                        window.navigationBarColor = barsColor.toArgb()
                        WindowCompat.getInsetsController(window, view).apply {
                            isAppearanceLightStatusBars = surfaceIsLight
                            isAppearanceLightNavigationBars = surfaceIsLight
                        }
                    }
                }
            }
        }

        MaterialTheme(
            colorScheme = animatedScheme,
            typography = Typography,
            content = content
        )
    }
}
