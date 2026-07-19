package com.wxn.reader.data.source.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * 崩溃时间戳持久化。用 SharedPreferences + commit() 适配崩溃边界同步写。
 *
 * **为什么不用 DataStore（C5 机制层论证）**，崩溃边界 DataStore 有 4 个失败模式：
 * - A: `edit` 序列化在独立 actor 协程，崩溃线程所在 CoroutineScope 被取消时 actor 存活无法保证
 * - B: 崩溃边界调 suspend `edit` 必须 runBlocking，actor 线程受影响时死锁 → Sentry 转发永不执行
 * - C: DataStore 首次访问等初始化+迁移检查，启动早期崩溃会与初始化协程纠缠死锁
 * - D（最致命）: 进程崩溃后 framework 发 SIGKILL 不可拦截，AtomicFile 先写 .tmp 再 rename 可能写半丢失/损坏
 *
 * SharedPreferences.commit() 同步 open+write+fsync+close 全部规避，kill 前已完成 fsync。
 *
 * **使用纪律**：[recordCrashNow]（commit 同步写）仅在 UncaughtExceptionHandler 调用；
 * [getLastCrashTimestamp]（读）走 SharedPreferences 内存缓存无 I/O，正常运行读取安全。
 * 正常运行绝不调 commit 避免卡主线程。
 */
class CrashPrefs @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)

    /** 记录崩溃时间（覆盖式，H1：每次崩溃刷新 7 天禁弹窗口，对崩溃频发用户更严格）。 */
    fun recordCrashNow() {
        prefs.edit { putLong(KEY, System.currentTimeMillis()) }
    }

    fun getLastCrashTimestamp(): Long = prefs.getLong(KEY, 0L)

    companion object { private const val KEY = "last_crash_timestamp" }
}
