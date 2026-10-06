package com.wxn.reader.util

import android.os.Process
import com.wxn.base.util.Logger
import com.wxn.reader.data.source.local.CrashPrefs
import kotlin.system.exitProcess

/**
 * 崩溃处理器：记录崩溃时间戳后转发给先前的 default handler。
 *
 * **转发目标**：安装时捕获的 default handler——通常为 Firebase Crashlytics 在
 * ContentProvider 阶段注册的 handler（Java 崩溃仍进入 Crashlytics），最终到 framework
 * handler 终止进程。
 *
 * **NDK 局限（G3）**：纯 native 崩溃（mobi/jp2forandroid 等 C/C++ SIGSEGV）走 sigaction
 * handler，不经过本 Java UncaughtExceptionHandler，`lastCrashTimestamp` 不记录。可接受
 * （进程立即死亡，重启时离崩溃已过较久，7 天禁弹判定意义有限）。
 *
 * **转发语义（G5）**：转发给 default handler 不包裹 try-catch，保持原生
 * UncaughtExceptionHandler 语义。
 *
 * **安装条件（G6）**：仅当先前的 default handler 非空时安装。Android framework 在
 * Application 创建前已安装 KillApplicationHandler，正常设备上恒非空；为 null 只出现在
 * 裸 JVM 环境（如 JVM 单元测试的 executor）——此时不安装，避免把任意线程的未捕获
 * 异常放大成宿主 JVM 退出（G1）。
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
        // 2. 转发给先前的 default handler（G5：保持原生语义，不包裹 try-catch）
        val handler = defaultHandler
        if (handler != null) {
            handler.uncaughtException(t, e)
        } else {
            // G1：无默认 handler（ROM 定制/测试框架等异常场景），手动终止进程。
            // 否则崩溃被吞掉，app 进入未定义状态。
            Logger.e("CrashHandler: no default handler, kill process", e)
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    companion object {
        /**
         * 安装崩溃处理器。在 Application 初始化时调用一次。
         *
         * G2：检测当前 default handler 是否已是 CrashHandler，避免重复 install 形成嵌套链
         * （热重启/多进程/测试场景）。
         *
         * G6：当前 default handler 为 null 时拒绝安装——Android framework 在 Application
         * 创建前已安装 KillApplicationHandler，正常设备上恒非空；为 null 只出现在裸 JVM
         * 环境（如 JVM 单元测试的 executor）。此时劫持 default handler 会把任意线程的
         * 未捕获异常放大成宿主 JVM 退出（G1），故直接跳过。
         */
        fun install(crashPrefs: CrashPrefs) {
            val currentDefault = Thread.getDefaultUncaughtExceptionHandler()
            if (currentDefault == null) return
            if (currentDefault is CrashHandler) return
            CrashHandler(crashPrefs, currentDefault).install()
        }
    }
}
