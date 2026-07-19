package com.wxn.reader.presentation.settings.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wxn.reader.R
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar
import com.wxn.reader.util.sync.BackupProgressState

/**
 * ★ 同步方案 §7.2:备份与还原设置页。
 *
 * - 列表项:备份 / 还原 / 上次备份时间。
 * - 进度对话框:progress != Idle 时显示(阻塞,完成前不可关闭)。
 *
 * ★ v1.3 一般-1:用 M3 ListItem + AlertDialog,对齐项目既有模式。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsScreen(
    viewModel: BackupSettingsViewModel = hiltViewModel(),
) {
    val navController: NavHostController = LocalNavController.current
    val progress by viewModel.progressEmitter.state.collectAsStateWithLifecycle()
    val lastBackupTime by viewModel.lastBackupTimeFlow.collectAsState(initial = null as String?)
    val restoreInProgress by viewModel.restoreInProgressFlow.collectAsState(initial = false)
    // ★ 一般-1:Active 态禁用按钮,防重复触发
    val canTrigger = progress is BackupProgressState.Idle

    // M1 修复:进程死亡恢复——进页面时清理上次中断的 .tmp 残留(原 cleanupResidue 死代码接线)
    LaunchedEffect(Unit) {
        viewModel.cleanupResidue()
    }

    val pickDir = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        // ★ 一般-9:uri 为 null(用户取消)直接 return;非 null 走 persistAndStartBackup
        uri?.let { viewModel.persistAndStartBackup(it) }
    }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.startRestore(it) }
    }

    Scaffold(
        topBar = {
            AppTopAppBar(title = { Text(stringResource(R.string.backup_restore_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )

        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.backup_action)) },
                    supportingContent = { Text(stringResource(R.string.backup_action_desc)) },
                    modifier = Modifier.clickable(enabled = canTrigger) { pickDir.launch(null) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.restore_action)) },
                    supportingContent = { Text(stringResource(R.string.restore_action_desc)) },
                    modifier = Modifier.clickable(enabled = canTrigger) {
                        pickFile.launch(arrayOf("*/*"))
                    },
                )
            }
            lastBackupTime?.let {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.last_backup)) },
                        supportingContent = { Text(it) },
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("") },
                    supportingContent = {
                        Text(
                            stringResource(R.string.backup_scope_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
        }

        // 进度对话框(阻塞,所有交互态在此对话框内渲染)
        if (progress !is BackupProgressState.Idle) {
            BackupRestoreProgressDialog(
                state = progress,
                onComplete = viewModel::onComplete,
                onHashPartialContinue = viewModel::onHashPartialContinue,
                onHashPartialCancel = viewModel::onHashPartialCancel,
                onRestoreConfirm = viewModel::onRestoreConfirm,
                onRestoreCancel = viewModel::onRestoreCancel,
                onCancelActive = viewModel::cancelPendingOperation,
            )
        }

        // ★ P1-1:进程死亡后还原中断标记检测。仅当当前无活跃操作(Idle)且标记为 true 时提示。
        if (canTrigger && restoreInProgress) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.restore_incomplete_title)) },
                text = { Text(stringResource(R.string.restore_incomplete_msg)) },
                confirmButton = {
                    Button(onClick = viewModel::dismissRestoreIncomplete) {
                        Text(stringResource(R.string.done))
                    }
                },
            )
        }
    }
}
