package com.wxn.reader.presentation.bookReader.components.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.reader.ui.theme.ReaderThemeEntry
import com.wxn.reader.ui.theme.ReaderThemePresets

/**
 * 阅读主题选择器（5+5 重构版）。
 *
 * 按当前模式（[isDarkMode]）过滤显示：亮色模式渲染 [ReaderThemePresets.LIGHT_THEMES]（5 亮），
 * 深色模式渲染 [ReaderThemePresets.DARK_THEMES]（5 暗）。
 *
 * 卡片右上角已微调标识：themeId 在 [modifiedThemeIds] 中表示该主题被用户微调过
 * （字段级判定：值偏离预设才算微调，非"Room 有存档"）。
 * UI 在面板打开、切换、重置后主动刷新 modifiedThemeIds。
 *
 * @param selectedThemeId 当前主题 id（始终有选中，需求 6）
 * @param onSelectTheme 点击主题卡片回调（触发 switchTheme）
 * @param isDarkMode 当前阅读模式是否暗色（决定渲染亮/暗主题组）
 * @param modifiedThemeIds 被微调过的主题 id 集合（显示 * 号）
 */
@Composable
fun ReaderThemeSelector(
    selectedThemeId: String?,
    onSelectTheme: (String) -> Unit,
    isDarkMode: Boolean,
    modifiedThemeIds: Set<String>,
) {
    val themes = if (isDarkMode) ReaderThemePresets.DARK_THEMES else ReaderThemePresets.LIGHT_THEMES
    Column(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(themes) { entry ->
                ReaderThemePreviewCard(
                    entry = entry,
                    isSelected = entry.themeId == selectedThemeId,
                    isModified = entry.themeId in modifiedThemeIds,
                    onSelect = { onSelectTheme(entry.themeId) }
                )
            }
        }
    }
}

/**
 * 单个阅读主题预览卡片。
 *
 * 预览内容：用主题的 bg 作为卡片背景色，text 色渲染主题名，直观展示该主题的阅读效果。
 * 选中态：2dp primary 边框（M3 规范）。
 * 已微调标识：右上角半透明圆背景 + 编辑图标（Icons.Outlined.Edit），颜色取主题 textColor，
 * 浅/深主题自动适配，符合 M3 图标密度规范（18dp 圆 + 12dp 图标）。
 */
@Composable
private fun ReaderThemePreviewCard(
    entry: ReaderThemeEntry,
    isSelected: Boolean,
    isModified: Boolean,
    onSelect: () -> Unit,
) {
    val bgColor = Color(entry.preset.backgroundColor)
    val textColor = Color(entry.preset.textColor)

    Card(
        modifier = Modifier
            .width(120.dp)
            .height(96.dp)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(
            // 卡片背景用主题实际 bg 色，直观预览
            containerColor = bgColor
        ),
        onClick = onSelect,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // 主题名（用 text 色渲染在 bg 上，预览对比度）
            Text(
                text = stringResource(entry.displayNameRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            // 已微调标识：右上角半透明圆 + 编辑图标（M3 规范，textColor 自动适配浅/深主题）
            // AnimatedVisibility 过渡：缓解 slider 跨越预设值时的单帧闪烁（fade 200ms）
            androidx.compose.animation.AnimatedVisibility(
                visible = isModified,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp, top = 4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(textColor.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.reader_theme_modified),
                        tint = textColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
