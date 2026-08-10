package com.wxn.bookread.jni

import com.wxn.base.util.Logger
import com.wxn.bookread.data.beans.BidiRun

object SheenBidiNative {

    val available: Boolean by lazy {
        try {
            System.loadLibrary("sheenbidi_jni")
            true
        } catch (e: UnsatisfiedLinkError) {
            Logger.e("SheenBidi .so load failed, MIXED fallback to handleRtlLine:$e")
            false
        }
    }

    val version: String by lazy { if (available) nativeVersion() else "unavailable" }

    private external fun bidiRunsNative(text: String, baseRtl: Boolean): IntArray
    private external fun nativeVersion(): String

    fun bidiRuns(text: String, baseRtl: Boolean): List<BidiRun> {
        if (!available || text.isEmpty()) return emptyList()
        val flat = bidiRunsNative(text, baseRtl)
        if (flat.isEmpty()) return emptyList()
        val runs = ArrayList<BidiRun>(flat.size / 3)
        var i = 0
        while (i + 2 < flat.size) {
            val offset = flat[i]
            val length = flat[i + 1]
            val level = flat[i + 2]
            if (offset < 0 || length < 0 || offset + length > text.length) {
                Logger.e("SheenBidiNative: invalid run offset=$offset len=$length textLen=${text.length}")
                break
            }
            runs.add(BidiRun(offset, length, level))
            i += 3
        }
        return runs
    }
}