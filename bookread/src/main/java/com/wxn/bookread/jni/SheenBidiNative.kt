package com.wxn.bookread.jni

import com.wxn.base.util.Logger
import com.wxn.bookread.data.beans.BidiParagraph
import com.wxn.bookread.data.beans.BidiRun

object SheenBidiNative {

    val available: Boolean by lazy {
        try {
            System.loadLibrary("sheenbidi_jni")
            true
        } catch (e: UnsatisfiedLinkError) {
            Logger.e("SheenBidi .so load failed, RTL degrades to LTR legacy paths:$e")
            false
        }
    }

    val version: String by lazy { if (available) nativeVersion() else "unavailable" }

    private external fun bidiRunsNative(text: String, baseRtl: Boolean): IntArray
    private external fun nativeVersion(): String

    private external fun bidiRunsExplicitNative(text: String, rtl: Boolean): IntArray

    fun bidiRuns(text: String, baseRtl: Boolean): BidiParagraph {
        if (!available || text.isEmpty()) return BidiParagraph(0, emptyList())
        val flat = bidiRunsNative(text, baseRtl)
        if (flat.size < 1 || (flat.size - 1) % 3 != 0 || flat[0] !in 0..1) {
            Logger.e("SheenBidiNative: malformed bidi result size=${flat.size} base=${flat.getOrNull(0)}")
            return BidiParagraph(0, emptyList())
        }
        val baseLevel = flat[0]
        val runs = ArrayList<BidiRun>((flat.size - 1) / 3)
        var i = 1
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
        return BidiParagraph(baseLevel, runs)
    }

    /** 显式声明的段落基调（HTML dir / CSS direction）：以具体基级创建段落，
     *  SheenBidi 跳过首强嗅探
     *  */
    fun bidiRunsExplicit(text: String, baseRtl: Boolean) : BidiParagraph {
        if (!available || text.isEmpty()) return BidiParagraph(0, emptyList())
        val flat = bidiRunsExplicitNative(text, baseRtl)
        if (flat.size < 1 || (flat.size - 1) % 3 != 0 || flat[0] !in 0..1) {
            Logger.e("SheenBidiNative: malformed bidi result size=${flat.size} base=${flat.getOrNull(0)}")
            return BidiParagraph(0, emptyList())
        }
        val baseLevel = flat[0]
        val runs = ArrayList<BidiRun>((flat.size - 1) / 3)
        var i = 1
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
        return BidiParagraph(baseLevel, runs)
    }
}