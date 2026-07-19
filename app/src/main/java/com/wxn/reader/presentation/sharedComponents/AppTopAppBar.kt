package com.wxn.reader.presentation.sharedComponents

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 应用统一标题栏。
 *
 * 容器色固定为 [MaterialTheme.colorScheme.surfaceContainer]，与系统状态栏
 * （Theme.kt 中 `window.statusBarColor`）、阅读器 TopToolbar / BottomToolbar、
 * 首页 NavigationBar 保持一致，使标题栏与状态栏在所有主题下连成一片。
 *
 * 1:1 镜像 M3 [TopAppBar] 的 API：除 `containerColor` 默认值不同外，其余参数完全一致，
 * 迁移时调用点只需把 `TopAppBar(` 改为 `AppTopAppBar(`，参数无需调整。
 *
 * 仅当需要特殊视觉效果（如 bookDetails 的透明覆盖图、Speaker 的彩色栏）时才覆盖
 * [colors] 参数，这种场景应继续直接使用 [TopAppBar]。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior
    )
}
