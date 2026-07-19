package com.wxn.reader.data.source.local

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.base.util.Logger
import com.wxn.reader.data.model.AnalysisPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.analysisPrefsDataStore by preferencesDataStore(name = "analysis_prefs")

/**
 * 用户行为分析偏好持久化工具。
 *
 * **设计约束**（后续扩展必须遵守）：
 * - [firstLaunchTimestamp] 是一次性写入字段，只由 [recordFirstLaunchIfNeeded] 写入
 * - 其他字段的更新方法（如未来的 `updateLastReviewPrompt`）必须使用独立的 edit 块，
 *   不得引入全量 update 方法（参考 [AppPreferencesUtil.updateAppPreferences] 的反模式）
 * - 字段永不持久化为 0L，获取失败时用当前时间兜底
 */
class AnalysisPrefUtil @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.analysisPrefsDataStore

    companion object {
        val FIRST_LAUNCH_TIMESTAMP = longPreferencesKey("first_launch_timestamp")
    }

    val analysisPrefsFlow: Flow<AnalysisPreferences> = dataStore.data.map { prefs ->
        AnalysisPreferences(
            // 未初始化或异常值 0 时用当前时间兜底（仅 Flow 发射，不写入 DataStore）
            firstLaunchTimestamp = prefs[FIRST_LAUNCH_TIMESTAMP]?.takeIf { it != 0L }
                ?: System.currentTimeMillis()
        )
    }

    /**
     * 记录首次启动时间戳（幂等，仅写入一次）。
     *
     * **调用约束**：仅由 [com.wxn.reader.MainActivity.onCreate] 调用一次。
     * 业务层如需读取，请使用 [getFirstLaunchTimestamp] 或 [analysisPrefsFlow]。
     *
     * 并发安全：DataStore.edit 原子事务 + double-check 防重复写入。
     *
     * @param isFirstLaunch 是否为新用户首次启动：
     *        - true  → 写入 System.currentTimeMillis()（精确的首次启动时刻）
     *        - false → 写入 PackageInfo.firstInstallTime（系统兜底，老用户升级场景）
     */
    suspend fun recordFirstLaunchIfNeeded(isFirstLaunch: Boolean) {
        // Fast path：key 已存在 → 已初始化，直接返回
        if (dataStore.data.first()[FIRST_LAUNCH_TIMESTAMP] != null) return

        val timestamp = if (isFirstLaunch) {
            System.currentTimeMillis()
        } else {
            getPackageFirstInstallTime()
        }

        dataStore.edit { prefs ->
            // Double-check：防止并发场景下重复写入
            if (prefs[FIRST_LAUNCH_TIMESTAMP] == null) {
                prefs[FIRST_LAUNCH_TIMESTAMP] = timestamp
                Logger.d("AnalysisPrefUtil: Recorded firstLaunchTimestamp=$timestamp, isFirstLaunch=$isFirstLaunch")
            }
        }
    }

    /** 直接读取首次启动时间戳（suspend 便利方法）。 */
    suspend fun getFirstLaunchTimestamp(): Long {
        return analysisPrefsFlow.first().firstLaunchTimestamp
    }

    /**
     * P2-2：防御性修复——若 firstLaunchTimestamp 为 0 或 null，写入当前时间戳。
     *
     * 正常路径下由 [recordFirstLaunchIfNeeded] 在 MainActivity.onCreate 中一次性写入，
     * 本方法仅作为 DataStore 异常/损坏时的兜底。调用后 return false（等 7 天后再弹）。
     */
    suspend fun ensureFirstLaunchTimestamp() {
        val current = dataStore.data.first()[FIRST_LAUNCH_TIMESTAMP]
        if (current == null || current == 0L) {
            dataStore.edit { prefs ->
                if (prefs[FIRST_LAUNCH_TIMESTAMP] == null || prefs[FIRST_LAUNCH_TIMESTAMP] == 0L) {
                    prefs[FIRST_LAUNCH_TIMESTAMP] = System.currentTimeMillis()
                    Logger.w("AnalysisPrefUtil: firstLaunchTimestamp was 0/null, recovered to now")
                }
            }
        }
    }

    /**
     * 获取系统记录的应用首次安装时间。
     * 处理 Android 13+ 的 getPackageInfo deprecation。
     * 异常时返回当前时间作为兜底（保证永不返回 0L）。
     */
    private fun getPackageFirstInstallTime(): Long = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            ).firstInstallTime
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        }
    } catch (e: Exception) {
        Logger.w("AnalysisPrefUtil: getPackageInfo failed, fallback to currentTime:$e")
        System.currentTimeMillis()
    }
}
