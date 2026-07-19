package com.wxn.reader.presentation.settings.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.wxn.reader.R
import com.wxn.reader.data.model.backup.BackupResult
import com.wxn.reader.util.sync.BackupProgressState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ★ v1.3 一般-1:阻塞型对话框,用 M3 AlertDialog(对齐 GeneralSettings.kt:483 模式)。
 * 完成前不可关闭;HashPartial/ConfirmRestore 交互态走 confirmButton/dismissButton slot。
 *
 * ★ 同步方案 §7.1.3。
 */
@Composable
fun BackupRestoreProgressDialog(
    state: BackupProgressState,
    onComplete: () -> Unit,
    onHashPartialContinue: () -> Unit,
    onHashPartialCancel: () -> Unit,
    onRestoreConfirm: () -> Unit,
    onRestoreCancel: () -> Unit,
    onCancelActive: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = {
            // 强制不可关闭:完成/交互态由对应回调触发(对齐 GeneralSettings.kt:486)
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        title = {
            Text(
                when (state) {
                    is BackupProgressState.Active -> stringResource(state.detailResId, *state.detailArgs.toTypedArray())
                    is BackupProgressState.HashPartial -> stringResource(R.string.hash_partial_title)
                    is BackupProgressState.ConfirmRestore -> stringResource(R.string.restore_confirm_title)
                    is BackupProgressState.Done -> stringResource(R.string.backup_done_title)
                    BackupProgressState.Idle -> ""
                }
            )
        },        text = {
            Column(
                modifier = Modifier.fillMaxWidth(0.9f)) {
                when (state) {
                    is BackupProgressState.Active -> {
                        if (state.progress != null) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(state.detailResId, *state.detailArgs.toTypedArray()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is BackupProgressState.HashPartial -> {
                        if (state.inaccessibleBooks.isNotEmpty()) {
                            Text(stringResource(R.string.hash_partial_inaccessible, state.inaccessibleBooks.size))
                        }
                        if (state.hashFailedBooks.isNotEmpty()) {
                            Text(stringResource(R.string.hash_partial_failed, state.hashFailedBooks.size))
                        }
                        val allBooks = state.inaccessibleBooks + state.hashFailedBooks
                        if (allBooks.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                allBooks.forEach { name ->
                                    Text(
                                        "• $name",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    is BackupProgressState.ConfirmRestore -> {
                        RestoreConfirmText(diff = state.diff)
                    }
                    is BackupProgressState.Done -> {
                        // C2 修复：PartialFail 渲染失败列表（entryName + message），不再只显示数字
                        when (val r = state.result) {
                            is BackupResult.Success -> {
                                Text(
                                    stringResource(R.string.backup_success, r.manifest.counts.books),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                if (r.skippedBooks.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.backup_skipped_books, r.skippedBooks.size),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        r.skippedBooks.forEach { name ->
                                            Text(
                                                "• $name",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                            is BackupResult.PartialFail -> {
                                // ★ P1-6:根据 isRestore 选对应文案,避免备份场景显示 "Restored"
                                val partialFailRes = if (state.isRestore) {
                                    R.string.restore_partial_fail
                                } else {
                                    R.string.backup_partial_fail
                                }
                                Text(
                                    stringResource(partialFailRes, r.successCount, r.failures.size),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(Modifier.height(8.dp))
                                if (r.failures.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .verticalScroll(rememberScrollState()),
                                    ) {
                                        r.failures.forEach { f ->
                                            // ★ P1-4:title=entryName, subtitle=i18n errorCode 文案
                                            Column {
                                                Text(
                                                    stringResource(R.string.backup_failure_item, f.entryName),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                                Text(
                                                    stringResource(f.errorCode.resId),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                Text(
                                    displayMessage(r, state.isRestore),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                    BackupProgressState.Idle -> { /* 不应进入 */ }
                }
            }
        },
        confirmButton = {
            when (state) {
                is BackupProgressState.Active -> { /* 阻塞期无按钮 */ }
                is BackupProgressState.HashPartial -> {
                    TextButton(onClick = onHashPartialContinue) {
                        Text(stringResource(R.string.continue_anyway))
                    }
                }
                is BackupProgressState.ConfirmRestore -> {
                    TextButton(onClick = onRestoreConfirm) {
                        Text(stringResource(R.string.confirm_restore))
                    }
                }
                is BackupProgressState.Done -> {
                    Button(onClick = onComplete) {
                        Text(stringResource(R.string.done))
                    }
                }
                BackupProgressState.Idle -> {}
            }
        },
        dismissButton = {
            when (state) {
                is BackupProgressState.Active -> {
                    // C3 修复：EXPORTING/MERGING 阻塞期提供取消入口（长任务必须可中断）
                    TextButton(onClick = onCancelActive) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                is BackupProgressState.HashPartial -> {
                    TextButton(onClick = onHashPartialCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                is BackupProgressState.ConfirmRestore -> {
                    TextButton(onClick = onRestoreCancel) {
                        Text(stringResource(R.string.cancel))
                    }
                }
                else -> {}
            }
        },
        icon = if (state is BackupProgressState.Done) {
            {
                val icon = when (val r = state.result) {
                    is BackupResult.Success -> Icons.Outlined.CheckCircle
                    is BackupResult.PartialFail -> Icons.Outlined.Warning
                    is BackupResult.Failed -> Icons.Outlined.Error
                    BackupResult.Cancelled -> Icons.Outlined.Cancel
                }
                Icon(
                    icon,
                    contentDescription = null,
                    tint = when (state.result) {
                        is BackupResult.Success -> MaterialTheme.colorScheme.primary
                        is BackupResult.PartialFail,
                        is BackupResult.Failed -> MaterialTheme.colorScheme.error
                        BackupResult.Cancelled -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        } else null,
    )
}

/** ★ displayMessage 仅被 Done 态的 `else ->` 调用(PartialFail/Success 已在主 when 显式处理),
 *  故只需覆盖 Failed / Cancelled。 */
@Composable
private fun displayMessage(result: BackupResult, isRestore: Boolean): String = when (result) {
    is BackupResult.Failed -> stringResource(result.errorCode.resId)
    BackupResult.Cancelled -> stringResource(R.string.backup_cancelled)
    else -> ""
}

@Composable
private fun ColumnScope.RestoreConfirmText(diff: com.wxn.reader.data.model.backup.RestoreDiff) {
    val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    Text(stringResource(R.string.restore_from, diff.deviceName, timeFormatter.format(Date(diff.createdAt))))
    Text(
        stringResource(
            R.string.restore_backup_count,
            diff.backupBooks,
            diff.backupNotes,
            diff.backupHighlights,
            diff.backupBookmarks,
        ),
    )
    Text(stringResource(R.string.restore_local_count, diff.localBooks))
    Text(stringResource(R.string.restore_matched, diff.matched))
    Text(stringResource(R.string.restore_new_orphan, diff.newOrphan))
    if (diff.deletedTombstones > 0) {
        Text(
            stringResource(R.string.restore_deleted, diff.deletedTombstones),
            color = MaterialTheme.colorScheme.error,
        )
    }
}
