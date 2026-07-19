package com.wxn.reader.util

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp


/***
 * 代替ScrollableTabRow 的安全版本
 */
@Composable
fun SafeScrollableTabRow(
    selectedTabIndex: Int,
    totalTabCount: Int,  // 核心修复：由外部提供当前帧的真实 Tab 数量
    modifier: Modifier = Modifier,
    containerColor: Color = TabRowDefaults.primaryContainerColor,
    contentColor: Color = TabRowDefaults.primaryContentColor,
    edgePadding: Dp = TabRowDefaults.ScrollableTabRowEdgeStartPadding,
    divider: @Composable () -> Unit = { HorizontalDivider() },
    tabs: @Composable () -> Unit
) {
    // 基于外部传入的真实数量进行约束，保证同帧安全
    val safeSelectedTabIndex = selectedTabIndex.coerceIn(0, (totalTabCount - 1).coerceAtLeast(0))

    ScrollableTabRow(
        selectedTabIndex = safeSelectedTabIndex,
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        edgePadding = edgePadding,
        indicator = { tabPositions ->
            // 双重保险：以防极端情况，依然基于实际的 tabPositions 做一次防御
            if (tabPositions.isNotEmpty()) {
                val safeIndex = safeSelectedTabIndex.coerceIn(0, tabPositions.lastIndex)
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[safeIndex])
                )
            }
        },
        divider = divider,
        tabs = tabs
    )
}