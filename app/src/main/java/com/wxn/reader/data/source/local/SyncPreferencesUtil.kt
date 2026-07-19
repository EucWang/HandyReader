package com.wxn.reader.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.syncDataStore by preferencesDataStore(name = "sync_preferences")

/**
 * 同步功能开关与元信息(备份时间/还原中断标记等)。
 *
 * ★ 一期:`isSyncEnabled()` 恒为 false(sync_queue 不写入,装饰器短路)。
 *   二期激活后置 true。该开关由 v2.6 §7.3.1.1 B4 定义。
 *
 * ★ P1-1:`restoreInProgress` 标记还原进行中,进程死亡后重进设置页时提示用户。
 *   仅写 2 次(还原开始 + 还原结束),不记录进度,避免性能问题。
 */
@Singleton
class SyncPreferencesUtil @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keySyncEnabled = booleanPreferencesKey("sync_enabled")
    private val keyRestoreInProgress = booleanPreferencesKey("restore_in_progress")
    private val keyLastBackupTime = stringPreferencesKey("last_backup_time")
    // ★ M3-A:移除 keyLastBackupUri —— lastBackupUri 写入但全代码库无读取方(死字段,过度设计)

    /** 是否激活二期 WebDAV 同步(一期恒 false)。 */
    val syncEnabledFlow: Flow<Boolean> = context.syncDataStore.data.map { it[keySyncEnabled] ?: false }

    suspend fun isSyncEnabled(): Boolean = syncEnabledFlow.first()

    /**
     * ★ P1-1:还原进行中标记。还原开始时 setRestoreInProgress(),
     *   还原结束(成功/失败/取消)时 clearRestoreInProgress()。
     *   仅布尔标志,不记录进度。进程死亡后保留,重进设置页据此提示。
     */
    val restoreInProgressFlow: Flow<Boolean> =
        context.syncDataStore.data.map { it[keyRestoreInProgress] ?: false }

    suspend fun setRestoreInProgress() {
        context.syncDataStore.edit { it[keyRestoreInProgress] = true }
    }

    suspend fun clearRestoreInProgress() {
        context.syncDataStore.edit { it[keyRestoreInProgress] = false }
    }

    val lastBackupTimeFlow: Flow<String?> =
        context.syncDataStore.data.map { it[keyLastBackupTime] }

    /**
     * 持久化上次备份完成时间。
     *
     * ★ M1:参数为预格式化的 locale 显示串(格式 `yyyy-MM-dd HH:mm`),
     *   由 [BackupRestoreManager.startBackup] 在备份成功后传入,
     *   [BackupSettingsScreen] 直接 `Text(it)` 渲染,无需二次格式化。
     *   **非 ISO 8601**(旧参数名 timeIso 已废弃,名不副实)。
     */
    suspend fun setLastBackup(formattedTime: String) {
        context.syncDataStore.edit { it[keyLastBackupTime] = formattedTime }
    }
}
