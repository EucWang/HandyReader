package com.wxn.base.util

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

// 使用Handler扩展替代CountDownLatch
fun <T> Handler?.runAndWait(timeoutMs: Long = 2000, block: () -> T): T? {
    if (this != null) {
        val result = AtomicReference<T?>()
        val latch = CountDownLatch(1)
        post {
            result.set(block());
            latch.countDown()
        }
        return if (latch.await(timeoutMs, TimeUnit.MILLISECONDS)) result.get() else null
    }
    return null
}

// 确保 AudioTrack 操作在同一线程
fun Handler?.runToThread(block: () -> Unit) {
    if (this != null) {
        if (Looper.myLooper() == this.looper) {
            block()
        } else {
            post(block)
        }
    }
}

fun Handler?.postDelayedSafe(
    delayMillis: Long,
    token: Any? = null,
    block: () -> Unit
): Runnable? {
    if (this == null) return null

    val runnable = Runnable { block() }
    if (token != null) {
        postAtTime(runnable, token, SystemClock.uptimeMillis() + delayMillis)
    } else {
        postDelayed(runnable, delayMillis)
    }
    return runnable
}

// 安全的移除回调
fun Handler?.removeCallbacksSafe(runnable: Runnable?) {
    if (this != null && runnable != null) {
        removeCallbacks(runnable)
    }
}