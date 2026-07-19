package com.wxn.reader.presentation.settings.backup

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.base.util.withIO
import com.wxn.reader.data.model.backup.UserDecision
import com.wxn.reader.data.source.local.SyncPreferencesUtil
import com.wxn.reader.util.sync.BackupProgressEmitter
import com.wxn.reader.util.sync.BackupRestoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ★ v1.3 一般-2:VM 注入 BackupRestoreManager(Singleton 持 scope + Mutex),VM 不持 scope。
 * VM 仅做:take 权限 / 触发 Manager / 转发用户决策给 emitter。
 *
 * ★ 同步方案 §7.2.1。
 */
@HiltViewModel
class BackupSettingsViewModel @Inject constructor(
    private val backupRestoreManager: BackupRestoreManager,
    val progressEmitter: BackupProgressEmitter,
    private val syncPrefs: SyncPreferencesUtil,
    application: Application,
) : AndroidViewModel(application) {

    val lastBackupTimeFlow = syncPrefs.lastBackupTimeFlow

    /** ★ P1-1:还原进行中标记(进程死亡后重进设置页据此提示)。 */
    val restoreInProgressFlow = syncPrefs.restoreInProgressFlow

    /** ★ 一般-9:take 权限必须在 startBackup 之前,用 viewModelScope.launchIO(IO 协程内同步调)。 */
    fun persistAndStartBackup(uri: Uri) {
        viewModelScope.launchIO {
            withIO {
                runCatching {
                    getApplication<Application>().contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            backupRestoreManager.startBackup(uri)
        }
    }

    /**
     * ★ P2-2:MIME 放宽为任意类型,此处做 `.zip` 后缀校验。
     *   非 .zip → 不启动还原(Stage 0 顶层 catch 已兜底非 zip 内容,
     *   但后缀校验能避免用户误选其他文件后走完整 zip 解析流程)。
     *
     *   ⚠ SAF content:// URI 的 `uri.path` 通常不含扩展名(如 Downloads provider 返回
     *     `/document/123`),必须经 ContentResolver 查 DISPLAY_NAME 才能拿到真实文件名。
     */
    fun startRestore(uri: Uri) {
        viewModelScope.launchIO {
            val displayName = withIO {
                runCatching {
                    getApplication<Application>().contentResolver.query(
                        uri, null, null, null, null,
                    )?.use { c ->
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                    }
                }.getOrNull()
            }
            if (!displayName.orEmpty().endsWith(".zip", ignoreCase = true)) {
                Logger.w("BackupSettingsViewModel: not a .zip file, ignore: $displayName")
                return@launchIO
            }
            backupRestoreManager.startRestore(uri)
        }
    }

    /** ★ P1-1:清除还原中断标记(用户看到提示后调)。 */
    fun dismissRestoreIncomplete() {
        viewModelScope.launchIO {
            syncPrefs.clearRestoreInProgress()
        }
    }

    /** 进程死亡恢复:进页面时清理上次中断的 .tmp 残留(§9.3)。 */
    fun cleanupResidue() {
        viewModelScope.launchIO {
            backupRestoreManager.cleanupResidue(getApplication())
        }
    }

    /**
     * ★ v1.4 一般-F9:取消上次未完成的交互态。
     * 场景:用户在 ConfirmRestore/HashPartial 态切背景,系统未杀进程 → Mutex 持有 → 重开 App 无响应。
     */
    fun cancelPendingOperation() {
        backupRestoreManager.cancelPending()
    }

    // ===== 交互态用户决策转发(HashPartial / ConfirmRestore)=====
    fun onHashPartialContinue() {
        progressEmitter.resume(UserDecision.Continue)
    }

    fun onHashPartialCancel() {
        progressEmitter.resume(UserDecision.Cancel)
    }

    fun onRestoreConfirm() {
        progressEmitter.resume(UserDecision.Continue)
    }

    fun onRestoreCancel() {
        progressEmitter.resume(UserDecision.Cancel)
    }

    fun onComplete() {
        progressEmitter.reset()
    }
}
