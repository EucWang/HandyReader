package com.wxn.reader.util.sync

import android.content.Context
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.reader.data.backup.BackupExporter
import com.wxn.reader.data.backup.BackupImporter
import com.wxn.reader.data.model.backup.BackupResult
import com.wxn.reader.data.model.backup.UserDecision
import com.wxn.reader.data.source.local.SyncPreferencesUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ★ v1.3 一般-2 + 一般-4:备份/还原 Singleton Manager。
 *
 * - 持 [Coroutines.scope](进程级,跨 Activity 重建)+ [Mutex](互斥备份/还原)。
 * - VM 仅调 [startBackup]/[startRestore],协程生命周期脱离 VM。
 * - ★ v1.4 一般-F8:finally 块调 [HybridLogicalClock.persistIfDirty],防进程被杀丢 HLC 内存值。
 * - ★ v1.4 一般-F9:[cancelPending] 解决交互态切后台 Mutex 持有致重开无响应。
 *
 * ★ 同步方案 §7.1.1。
 */
@Singleton
class BackupRestoreManager @Inject constructor(
    private val exporter: BackupExporter,
    private val importer: BackupImporter,
    private val hlc: HybridLogicalClock,
    val emitter: BackupProgressEmitter,
    private val syncPrefs: SyncPreferencesUtil,
) {
    private val scope = Coroutines.scope()
    private val mutex = Mutex()
    /** ★ C3 修复:持有当前任务 Job,供 [cancelPending] 取消长任务(EXPORTING/MERGING 无 await 挂起点)。 */
    @Volatile
    private var activeJob: Job? = null

    /**
     * 启动备份,返回 Job 供调用方按需 join(通常 VM 不 join,fire-and-forget)。
     *
     * ★ K-1 修复:备份成功(Success / PartialFail)后持久化 lastBackupTime,
     *   供 BackupSettingsScreen 的「Last backup」项显示。
     *   - Success 用 [BackupManifest.createdAt](精确,= sourceDeviceHlc.l)
     *   - PartialFail 用 [System.currentTimeMillis](近似,PartialFail 无 manifest)
     *   - Failed / Cancelled 不更新
     * ★ S2:DataStore 写入用 runCatching 吞异常,避免「对话框已显示成功,后台却崩溃」。
     */
    fun startBackup(uri: android.net.Uri): Job = scope.launchIO {
        if (!mutex.tryLock()) {
            Logger.w("BackupRestoreManager:startBackup: another task in progress, ignore")
            return@launchIO
        }
        activeJob = this.coroutineContext[Job]
        try {
            val r = exporter.export(uri, emitter)
            // ★ M2:文件已落地的两种成功态都更新 lastBackupTime
            //   ts == null 表示不更新(Failed / Cancelled)
            val ts: Long? = when (r) {
                is BackupResult.Success -> r.manifest.createdAt
                is BackupResult.PartialFail -> System.currentTimeMillis()
                is BackupResult.Failed, BackupResult.Cancelled -> null
            }
            if (ts != null) {
                runCatching {
                    val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(ts))
                    syncPrefs.setLastBackup(formatted)
                }.onFailure { Logger.w("setLastBackup failed: ${it.message}") }
            }
        } finally {
            // ★ v1.4 一般-F8:备份结束(成功/失败/取消)都落盘 HLC
            hlc.persistIfDirty()
            activeJob = null
            mutex.unlock()
        }
    }

    fun startRestore(uri: android.net.Uri): Job = scope.launchIO {
        if (!mutex.tryLock()) {
            Logger.w("BackupRestoreManager:startRestore: another task in progress, ignore")
            return@launchIO
        }
        activeJob = this.coroutineContext[Job]
        // ★ P1-1:还原开始时设标志(此处 mutex 已锁定,确保仅在真正开始还原时写入)
        runCatching { syncPrefs.setRestoreInProgress() }
        try {
            importer.import(uri, emitter)
        } finally {
            // ★ P1-1:还原结束(成功/失败/取消)清除标志
            runCatching { syncPrefs.clearRestoreInProgress() }
            // ★ v1.4 一般-F8:还原结束(receive 后 HLC 已推进)落盘
            hlc.persistIfDirty()
            activeJob = null
            mutex.unlock()
        }
    }

    /**
     * ★ v1.4 一般-F9 + C3 修复:取消挂起的交互态或长任务。
     *
     * - 交互态(HashPartial/ConfirmRestore):resume(Cancel) 唤醒挂起的 await。
     * - 长任务(EXPORTING/MERGING,无 await 挂起点):[Job.cancel] 抛 CancellationException,
     *   经 `withContext(Dispatchers.IO)` / `appDb.withTransaction` / `CoverSyncIO.copyToCancellable`
     *   的 suspend 点向上传播(红线 #9),实现可中断。
     *
     * 场景:用户在 ConfirmRestore 态切背景 / EXPORTING 阶段误触发 5000 本备份需中止。
     */
    fun cancelPending() {
        emitter.resume(UserDecision.Cancel)
        emitter.reset()
        activeJob?.cancel()
    }

    /** 进程死亡恢复:进 BackupSettingsScreen 时调,清理上次中断的 .tmp 残留(§9.3)。
     *  ★ 封面同步:同时清理 covers/.<sid>.tmp(写入中崩溃残留)。 */
    suspend fun cleanupResidue(ctx: Context) {
        ctx.cacheDir.listFiles { it.name.endsWith(".zip.tmp") }?.forEach { it.delete() }
        val coversDir = File(ctx.filesDir, "covers")
        if (coversDir.exists()) {
            coversDir.listFiles { it.name.endsWith(".tmp") }?.forEach { it.delete() }
        }
    }
}