package com.wxn.reader.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.reviewPromptDataStore by preferencesDataStore(name = "review_prompt_prefs")

/** 评审弹窗状态快照。 */
data class ReviewPromptState(
    val lastShownTimestamp: Long = 0L,
    val showCount: Int = 0,
    val hasClickedManualReview: Boolean = false,
    val lastSystemReviewShownDate: Long = 0L,
    /** I1：点弹窗外部/返回键关闭的时间戳（14 天短期冷却，不消耗 showCount、不进 90 天冷却）。 */
    val lastExternalDismissTimestamp: Long = 0L,
    /** I1/K3：点弹窗外部关闭的累计次数（≥2 次触发软退订 90 天冷却）。 */
    val externalDismissCount: Int = 0,
    /** 条件2：上一次弹窗（条件1/条件2 任一）的自然日午夜（startOfDay）。
     *  S2/S4：仅在 tryShow() 弹窗成功后写入，用于每自然日最多弹 1 次的去重。 */
    val lastDailyCheckDate: Long = 0L,
)

/**
 * 评审弹窗状态持久化。遵循 [AnalysisPrefUtil] 的独立 edit 块约束（不引入全量 update 方法）。
 */
class ReviewPrefsUtil @Inject constructor(private val context: Context) {
    private val dataStore = context.reviewPromptDataStore

    companion object {
        private val LAST_SHOWN = longPreferencesKey("last_shown_timestamp")
        private val SHOW_COUNT = intPreferencesKey("show_count")
        private val HAS_CLICKED_MANUAL = booleanPreferencesKey("has_clicked_manual_review")
        private val LAST_SYSTEM_REVIEW_DATE = longPreferencesKey("last_system_review_shown_date")
        private val LAST_EXTERNAL_DISMISS = longPreferencesKey("last_external_dismiss_timestamp")
        private val EXTERNAL_DISMISS_COUNT = intPreferencesKey("external_dismiss_count")
        private val LAST_DAILY_CHECK_DATE = longPreferencesKey("last_daily_check_date")
    }

    val reviewPrefsFlow: Flow<ReviewPromptState> = dataStore.data.map { p ->
        ReviewPromptState(
            lastShownTimestamp = p[LAST_SHOWN] ?: 0L,
            showCount = p[SHOW_COUNT] ?: 0,
            hasClickedManualReview = p[HAS_CLICKED_MANUAL] ?: false,
            lastSystemReviewShownDate = p[LAST_SYSTEM_REVIEW_DATE] ?: 0L,
            lastExternalDismissTimestamp = p[LAST_EXTERNAL_DISMISS] ?: 0L,
            externalDismissCount = p[EXTERNAL_DISMISS_COUNT] ?: 0,
            lastDailyCheckDate = p[LAST_DAILY_CHECK_DATE] ?: 0L,
        )
    }

    /** 直接读取当前状态快照（suspend 便利方法）。 */
    suspend fun getState(): ReviewPromptState = reviewPrefsFlow.first()

    /** 弹窗已显示：累加 showCount + 记录时间。
     *  E3：立即写（不延后到渲染确认），防用户秒关弹窗导致计数丢失绕过上限。 */
    suspend fun recordShown() = dataStore.edit { p ->
        p[LAST_SHOWN] = System.currentTimeMillis()
        p[SHOW_COUNT] = (p[SHOW_COUNT] ?: 0) + 1
    }

    /** 用户手动点击过评价入口 → 永久禁用自动弹窗。 */
    suspend fun setManualReviewClicked() = dataStore.edit { it[HAS_CLICKED_MANUAL] = true }

    /** 记录系统评分流程已展示（用于本地配额预判，365 天内不再调系统 API）。 */
    suspend fun recordSystemReviewShown() = dataStore.edit {
        it[LAST_SYSTEM_REVIEW_DATE] = System.currentTimeMillis()
    }

    /** K3/J7：dumb setter，业务判定（到期重置等）在 ReviewPromptManager 层。 */
    suspend fun recordExternalDismiss(now: Long, newCount: Int) = dataStore.edit {
        it[LAST_EXTERNAL_DISMISS] = now
        it[EXTERNAL_DISMISS_COUNT] = newCount
    }

    /** S2/S4：记录弹窗成功的自然日午夜（startOfDay）。
     *  仅由 ReviewPromptManager.markDailyChecked() 转发调用，确保每自然日最多弹 1 次。 */
    suspend fun markDailyChecked(date: Long) = dataStore.edit {
        it[LAST_DAILY_CHECK_DATE] = date
    }
}
