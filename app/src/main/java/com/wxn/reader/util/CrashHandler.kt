package com.wxn.reader.util

import android.os.Process
import com.wxn.base.util.Logger
import com.wxn.reader.data.source.local.CrashPrefs
import kotlin.system.exitProcess

/**
 * 崩溃处理器：记录崩溃时间戳后转发给 Sentry default handler。
 *
 * **顺序依赖（G4）**：[install] 必须在 [com.wxn.reader.BookApplication.initComponent] 中
 * `SentryAndroid.init()` 之后调用，否则 [defaultHandler] 会捕获 framework handler
 * 而非 Sentry handler，导致 Sentry 上报静默失效。
 *
 * **NDK 局限（G3）**：纯 native 崩溃（mobi/jp2forandroid 等 C/C++ SIGSEGV）走 sigaction
 * handler，不经过本 Java UncaughtExceptionHandler，`lastCrashTimestamp` 不记录。可接受
 * （进程立即死亡，重启时离崩溃已过较久，7 天禁弹判定意义有限）。
 *
 * **转发语义（G5）**：转发给 default handler 不包裹 try-catch，保持原生
 * UncaughtExceptionHandler 语义，便于 Sentry 自身问题定位。
 */
class CrashHandler private constructor(
    private val crashPrefs: CrashPrefs,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        // 1. 记录崩溃时间戳（失败不阻断，崩溃边界已尽最大努力）
        try {
            crashPrefs.recordCrashNow()
        } catch (writeEx: Exception) {
            Logger.e("CrashHandler: record failed", writeEx)
        }
        // 2. 转发给 Sentry default handler（G5：保持原生语义，不包裹 try-catch）
        val handler = defaultHandler
        if (handler != null) {
            handler.uncaughtException(t, e)
        } else {
            // G1：无默认 handler（ROM 定制/测试框架/Sentry 配置异常），手动终止进程。
            // 否则崩溃被吞掉，app 进入未定义状态。
            Logger.e("CrashHandler: no default handler, kill process", e)
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    companion object {
        /**
         * 安装崩溃处理器。必须在 SentryAndroid.init() 之后调用（G4）。
         *
         * G2：检测当前 default handler 是否已是 CrashHandler，避免重复 install 形成嵌套链
         * （热重启/多进程/测试场景）。
         */
        fun install(crashPrefs: CrashPrefs) {
            val currentDefault = Thread.getDefaultUncaughtExceptionHandler()
            if (currentDefault is CrashHandler) return
            CrashHandler(crashPrefs, currentDefault).install()
        }
    }
}
