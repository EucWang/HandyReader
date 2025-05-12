package com.wxn.base.util

import android.os.Build
import android.os.StrictMode
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

fun Throwable.toast(prefix: String = "") {
    if (this is CancellationException) return
//    val errorMessage = getErrorMessage()
//    if (errorMessage.isEmpty() && prefix.isEmpty()) return
    toast("$prefix${this.message}")
}

object Logger {

    fun init(isDebug:Boolean) {
        if (isDebug) {
//            enableStrictMode()
            Timber.plant(Timber.DebugTree())
        }
    }

    /**
     * Strict mode will log violation of VM and threading policy.
     * Use it to make sure the app doesn't do too much work on the main thread.
     */
    private fun enableStrictMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return
        }

        val executor = Executors.newSingleThreadExecutor()
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .permitDiskWrites()
                .permitDiskReads()
                .penaltyListener(executor) { violation ->
                    Timber.e(violation, "Thread policy violation")
                }
//                .penaltyDeath()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyListener(executor) { violation ->
                    Timber.e(violation, "VM policy violation")
                }
//                .penaltyDeath()
                .build()
        )
    }


    fun i(t: Throwable) {
        Timber.i(t)
    }

    fun i(message: String) {
        Timber.i(message)
    }

    fun d(t: Throwable) {
        Timber.d(t)
    }

    fun d(message: String) {
        Timber.d(message)
    }

    fun w(t: Throwable) {
        Timber.w(t)
    }

    fun w(message: String) {
        Timber.w(message)
    }

    fun e(t: Throwable) {
        Timber.e(t)
    }

    fun e(message: String) {
        Timber.e(message)
    }

    fun v(t: Throwable) {
        Timber.v(t)
    }

    fun v(message: String) {
        Timber.v(message)
    }

    fun wtf(t: Throwable) {
        Timber.wtf(t)
    }

    fun wtf(message: String) {
        Timber.wtf(message)
    }

    fun log(priority: Int, t: Throwable) {
        Timber.log(priority, t)
    }

    fun log(priority: Int, message: String) {
        Timber.log(priority, message)
    }
}