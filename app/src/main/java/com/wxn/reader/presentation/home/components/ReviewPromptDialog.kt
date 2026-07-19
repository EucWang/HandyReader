package com.wxn.reader.presentation.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarRate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.wxn.reader.R

/**
 * 好评引导弹窗（方案 B-合规）。
 *
 * **合规设计**：给好评⭐️ 与提建议💬 两个按钮平等并列，不判定用户情绪、不拦截差评用户
 * → 规避 Google Play "选择性诱导"红线（用户点好评时调系统 Review API，用户仍可自主给 1 星）。
 *
 * 纯 UI 组件，业务逻辑（系统评分调用、配额预判、goShop 兜底）在 HomeViewModel，避免泄漏 Activity。
 *
 * @param onRate 点"给好评" → 调用方触发系统 Review 流程
 * @param onFeedback 点"提建议" → 跳转 FeedbackScreen
 * @param onLater 点"稍后" → 进入 90 天冷却
 * @param onDismiss 点外部/返回键 → I1：仅 5 天短期冷却，不消耗配额
 */
@Composable
fun ReviewPromptDialog(
    onRate: () -> Unit,
    onFeedback: () -> Unit,
    onLater: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.StarRate, contentDescription = null) },
        title = {
            Text(
                text = stringResource(R.string.review_prompt_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.review_prompt_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            // C6：按钮防抖，防止快速连点重复触发系统评分流程
            var requesting by remember { mutableStateOf(false) }
            Button(
                onClick = { if (!requesting) { requesting = true; onRate() } },
                enabled = !requesting,
            ) {
                Text(stringResource(R.string.review_prompt_rate))
            }
        },
        dismissButton = {
            // H3：三按钮垂直排列（水平排列在 AlertDialog 内会拥挤），对标主流评分引导弹窗
            Column {
                TextButton(onClick = onFeedback) {
                    Text(stringResource(R.string.review_prompt_feedback))
                }
                TextButton(onClick = onLater) {
                    Text(stringResource(R.string.review_prompt_later))
                }
            }
        },
    )
}
