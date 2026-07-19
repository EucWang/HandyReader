package com.wxn.reader.util.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.wxn.base.util.Logger
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import com.wxn.reader.data.backup.ContentHashCalculator
import com.wxn.reader.data.source.local.dao.BookDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * ★ 一般-A:一次性后台静默补算 contentHash(老用户升级路径)。
 *
 * 约束:充电 + 不低电量(不强制 WiFi,本地文件 IO);低优先级,不打扰用户。
 * 触发:App 升级到含本期的版本后,Application 调度一次(见 BookApplication)。
 *
 * ★ 建议-F1:WorkManager 基础设施已就绪(BookApplication 已实现 WorkConfiguration.Provider + HiltWorkerFactory),
 *   本 Worker 直接 @HiltWorker 照抄 DownloadWorker 模式。
 *
 * ★ 同步方案 §3.4 时机 (2b)。
 *
 * ★ A+++ 修复:
 *   (1) 严重-6: 用 [BookDao.getActiveBookIds] 取活行,跳过已 deduped / 软删行,避免反复 sha256;
 *   (2) 一般-4: try-catch 包裹单本书,失败不阻断整体;
 *   (3) 严重-1: 新增 deduped 统计,区分"算入 hash"和"被去重"。
 */
@HiltWorker
class EnsureContentHashWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val contentHashCalculator: ContentHashCalculator,
    private val bookDao: BookDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // ★ 严重-6:只处理活行(deleted=0 AND importStatus=0),跳过已 deduped / 软删行
        val bookIds = bookDao.getActiveBookIds()
        Logger.d("EnsureContentHashWorker: start, ${bookIds.size} active books")
        var ok = 0
        var deduped = 0      // ★ 严重-1:独立统计被去重的行
        var inaccessible = 0
        var failed = 0
        var skipped = 0
        bookIds.forEach { bookId ->
            // ★ 一般-4:单本 try-catch,失败不阻断整体
            try {
                val proj = bookDao.getContentHashAndFileType(bookId)
                if (proj == null || proj.contentHash != null) {
                    skipped++
                    return@forEach
                }
                when (contentHashCalculator.ensureContentHash(bookId)) {
                    is ContentHashCalculator.EnsureHashResult.Ok -> ok++
                    ContentHashCalculator.EnsureHashResult.Deduped -> deduped++
                    ContentHashCalculator.EnsureHashResult.Inaccessible -> inaccessible++
                    ContentHashCalculator.EnsureHashResult.HashFailed -> failed++
                }
            } catch (e: CancellationException) {
                throw e  // 红线 #6:取消向上传播,不吞
            } catch (e: Exception) {
                failed++
                Logger.w("EnsureContentHashWorker: bookId=$bookId failed: ${e.message}")
            }
        }
        Logger.d(
            "EnsureContentHashWorker: done, ok=$ok, deduped=$deduped, " +
                "inaccessible=$inaccessible, failed=$failed, skipped=$skipped"
        )
        return Result.success()
    }

    companion object {
        fun buildRequest() = OneTimeWorkRequestBuilder<EnsureContentHashWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresCharging(false)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInitialDelay(60, TimeUnit.SECONDS)
            .build()
    }
}
