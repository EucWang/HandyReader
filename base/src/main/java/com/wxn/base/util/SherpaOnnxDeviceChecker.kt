package com.wxn.base.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object SherpaOnnxDeviceChecker {
    private const val MIN_RAM_BYTES_32BIT = 2L * 1024 * 1024 * 1024

    fun isDeviceSupported(context: Context): Boolean {
        if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) return true
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return memInfo.totalMem >= MIN_RAM_BYTES_32BIT
    }

    fun isLowEndDevice(): Boolean = Build.SUPPORTED_64_BIT_ABIS.isEmpty()
}
