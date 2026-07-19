package com.wxn.reader.util

import android.util.Log
import io.sentry.Sentry
import timber.log.Timber

/**
 * 将 Timber 的 ERROR 级别日志（带 Throwable）转发到 Sentry。
 *
 * 仅转发 priority == Log.ERROR 且 throwable != null 的日志，
 * 避免日志噪音和重复上报（Sentry SDK 自身已自动捕获未处理异常）。
 *
 * 注意：BookApplication 中 Sentry 的 beforeSend 已过滤 DEBUG/INFO/WARNING，
 * 但本 Tree 在调用 Sentry.captureException 前先按 priority 过滤，双保险。
 *
 * 设计理由：
 * - `bookread` 和 `base` 模块不直接依赖 Sentry SDK（保持模块边界）
 * - 通过 Timber.plant(SentryTree()) 自动覆盖所有模块的 Timber.e(throwable) 调用
 * - 符合 Android 行业标准实践
 */
class SentryTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // 仅上报 ERROR 级别且带 throwable 的日志
        if (priority >= Log.ERROR && t != null) {
            try {
                Sentry.captureException(t)
            } catch (ex: Throwable) {
                // Sentry 自身故障时降级到 android.util.Log，避免反馈循环
                android.util.Log.e("SentryTree", "Failed to capture exception", ex)
            }
        }
    }
}
