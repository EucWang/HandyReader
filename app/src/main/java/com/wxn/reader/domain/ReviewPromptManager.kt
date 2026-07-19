package com.wxn.reader.domain

import com.wxn.base.util.DateUtil
import com.wxn.base.util.Logger
import com.wxn.reader.data.source.local.AnalysisPrefUtil
import com.wxn.reader.data.source.local.CrashPrefs
import com.wxn.reader.data.source.local.ReviewPrefsUtil
import com.wxn.reader.domain.repository.BooksRepository
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 好评引导弹窗核心决策器（@Singleton）。
 *
 * 职责：
 * 1. 维护跨 VM 事件总线（触发1 [bookFinishedEvents] + 触发2 [readingSessionEnded]）
 * 2. 校验触发条件2（连续 5 天 × 每天 ≥15min）
 * 3. 核心闸门 [shouldShow]（冷却/上限/崩溃/熔断/外部分级冷却）
 *
 * 状态持久化委托 [ReviewPrefsUtil]（DataStore）与 [CrashPrefs]（SharedPreferences）。
 */
@Singleton
class ReviewPromptManager @Inject constructor(
    private val reviewPrefsUtil: ReviewPrefsUtil,
    private val crashPrefs: CrashPrefs,
    private val booksRepository: BooksRepository,
    private val analysisPrefUtil: AnalysisPrefUtil,
) {
    /**
     * 触发1 事件总线：仅承载"读完书的 bookId"。
     * MainReadViewModel 发，HomeViewModel 收。
     * DROP_OLDEST 保证背压，丢事件无害（用户只弹一次）。
     */
    private val _bookFinishedEvents = MutableSharedFlow<Long>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val bookFinishedEvents: SharedFlow<Long> = _bookFinishedEvents.asSharedFlow()

    /**
     * 触发2 事件总线：退出阅读页信号（K4/K5：替代 ON_RESUME + tab 评估）。
     * 由 MainReadViewModel.onCleared() 发，HomeViewModel 收。
     * onCleared 只在导航离开阅读页时触发（配置变更不误触发）。
     */
    private val _readingSessionEnded = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val readingSessionEnded: SharedFlow<Unit> = _readingSessionEnded.asSharedFlow()

    /** K3：Mutex 保护 recordExternalDismiss 的 read-modify-write。 */
    private val dismissMutex = Mutex()

    /**
     * 触发1：读完一本书触发。wordCount 门槛前置过滤（F1/F3：PDF 不调用此方法，其 wordCount 不可靠）。
     * best-effort：tryEmit 失败静默。
     */
    fun notifyBookFinished(bookId: Long, wordCount: Long) {
        Logger.i("ReviewPromptManager::notifyBookFinished:bookId=$bookId,wordCount=$wordCount")
        if (wordCount <= WORD_COUNT_THRESHOLD) return
        _bookFinishedEvents.tryEmit(bookId)
    }

    /** 触发2：退出阅读页（由 MainReadViewModel.onCleared 调用）。 */
    fun notifyReadingSessionEnded() {
        _readingSessionEnded.tryEmit(Unit)
    }

    /**
     * 触发2：连续天数评估（K6：3 天去重）。
     *
     * 只读判断，不写标志；标志写入由 HomeViewModel.tryShow() 成功后统一调用 [markDailyChecked]。
     */
    suspend fun checkConsecutiveDaysTrigger(): Boolean {
        val today = DateUtil.startOfDay(System.currentTimeMillis())
        val state = reviewPrefsUtil.getState()
        if (today - state.lastDailyCheckDate < CONSECUTIVE_CHECK_INTERVAL_MS) return false
        val days = booksRepository.getConsecutiveReadingDays(MIN_MILLIS_PER_DAY)
        val ret = days >= CONSECUTIVE_DAYS_THRESHOLD
        Logger.d("ReviewPromptManager:checkConsecutiveDaysTrigger:days=$days,ret=$ret")
        return ret
    }

    /**
     * 标记当天评估日期（K6：3 天去重用，触发1/触发2 共享）。
     * 确保每 3 天最多评估 1 次，且触发1/触发2 共享配额。
     */
    suspend fun markDailyChecked() =
        reviewPrefsUtil.markDailyChecked(DateUtil.startOfDay(System.currentTimeMillis()))

    /**
     * 核心闸门：判断是否真的该弹窗。
     *
     * F4：读取多个 prefs 是非原子快照，但闸门判定为"尽力而为"，偶尔误差可接受
     * （有 90 天冷却 + showCount 上限兜底）。
     */
    suspend fun shouldShow(): Boolean {
        val state = reviewPrefsUtil.getState()
        val now = System.currentTimeMillis()
        val firstLaunch = analysisPrefUtil.getFirstLaunchTimestamp()

        // P2-2：firstLaunch=0 表示 DataStore 异常，恢复为当前时间并本次不弹
        if (firstLaunch == 0L) {
            Logger.w("ReviewPromptManager::shouldShow: firstLaunch=0, recovering")
            analysisPrefUtil.ensureFirstLaunchTimestamp()
            return false
        }

        val lastCrash = crashPrefs.getLastCrashTimestamp()

        // H1/K8：lastCrash==0L 表示无崩溃，跳过判断；否则覆盖式刷新的 14 天禁弹窗口
        val withinCrashCooldown = lastCrash != 0L &&
            (now - lastCrash) <= CRASH_COOLDOWN_MS

        // K3：外部分级冷却（count<2 → 14天短期；count≥2 → 90天软退订）
        val externalBlocked = when {
            state.externalDismissCount >= SOFT_OPT_OUT_COUNT ->
                (now - state.lastExternalDismissTimestamp) < SOFT_OPT_OUT_COOLDOWN_MS
            state.lastExternalDismissTimestamp != 0L ->
                (now - state.lastExternalDismissTimestamp) < EXTERNAL_DISMISS_COOLDOWN_MS
            else -> false
        }

        Logger.d("ReviewPromptManager::shouldShow:now=$now,firstLaunch=$firstLaunch")

        val ret = state.showCount < MAX_SHOW_COUNT                  // 终身上限 2
            && !state.hasClickedManualReview                      // 手动评价熔断
            && (now - firstLaunch) >= INSTALL_MIN_MS              // 安装 ≥ 7 天
            && (now - state.lastShownTimestamp) >= COOLDOWN_MS    // 90 天冷却（明确交互后）
            && !withinCrashCooldown                               // 崩溃 14 天禁弹
            && !externalBlocked                                    // 外部分级冷却
        Logger.d("ReviewPromptManager::shouldShow:ret=$ret")
        return ret
    }

    /** 弹窗已显示：累加 showCount + 记录时间（E3：立即写）。仅"稍后"时调用。 */
    suspend fun onShown() = reviewPrefsUtil.recordShown()

    /**
     * 点外部关闭：分级冷却（K3 Mutex 保护 read-modify-write）。
     *
     * - count < 2 → 14 天短期冷却
     * - count ≥ 2 → 90 天软退订，到期后 count 重置为 1（重启 14 天配额）
     */
    suspend fun recordExternalDismiss() = dismissMutex.withLock {
        val state = reviewPrefsUtil.getState()
        val now = System.currentTimeMillis()
        val reset = state.externalDismissCount >= SOFT_OPT_OUT_COUNT &&
            (now - state.lastExternalDismissTimestamp) >= SOFT_OPT_OUT_COOLDOWN_MS
        val newCount = if (reset) 1 else state.externalDismissCount + 1
        reviewPrefsUtil.recordExternalDismiss(now, newCount)
    }

    /** 点"去评价/去反馈"或手动入口时调用：永久禁弹。 */
    suspend fun onManualReviewClicked() = reviewPrefsUtil.setManualReviewClicked()

    /** 记录系统 Review API 已展示（365 天配额）。 */
    suspend fun recordSystemReviewShown() = reviewPrefsUtil.recordSystemReviewShown()

    companion object {
        /** 读完触发的字数门槛：确保是正式书而非小文档。 */
        const val WORD_COUNT_THRESHOLD = 20_000L

        /** 触发2：连续阅读天数门槛。 */
        const val CONSECUTIVE_DAYS_THRESHOLD = 5

        /** 触发2：每天最少阅读时长（毫秒，15 分钟）。 */
        const val MIN_MILLIS_PER_DAY = 15L * 60 * 1000

        /** 终身弹窗次数上限。 */
        const val MAX_SHOW_COUNT = 2

        /** 软退订：外部关闭次数达到此值后升级 90 天冷却。 */
        const val SOFT_OPT_OUT_COUNT = 2

        private val INSTALL_MIN_MS = 7L * DateUtil.DAY_MS
        private val COOLDOWN_MS = 90L * DateUtil.DAY_MS

        /** K8：崩溃禁弹窗口（原 7 天→14 天）。 */
        private val CRASH_COOLDOWN_MS = 14L * DateUtil.DAY_MS

        /** 外部关闭短期冷却（原 5 天→14 天）。 */
        private val EXTERNAL_DISMISS_COOLDOWN_MS = 14L * DateUtil.DAY_MS

        /** 软退订冷却（外部关闭 ≥2 次后升级）。 */
        private val SOFT_OPT_OUT_COOLDOWN_MS = 90L * DateUtil.DAY_MS

        /** 触发2 评估去重间隔（3 天内不重复评估）。 */
        private val CONSECUTIVE_CHECK_INTERVAL_MS = 3L * DateUtil.DAY_MS
    }
}
