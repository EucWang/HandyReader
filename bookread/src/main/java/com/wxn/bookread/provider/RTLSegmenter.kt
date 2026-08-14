package com.wxn.bookread.provider

import com.wxn.base.bean.RunLayout
import com.wxn.base.bean.SegmentResult
import com.wxn.base.bean.TextDirection
import com.wxn.bookread.jni.SheenBidiNative

object RTLSegmenter {

    private val WHITESPACE_REGEX = Regex("\\s+")

    fun segment(text: String) : SegmentResult {
        if (text.isBlank()) {
            return SegmentResult(TextDirection.LTR,
                false,
                emptyList())
        }

        val runs = SheenBidiNative.bidiRuns(text, baseRtl = false)
        if (runs.isEmpty()) {
            val dir = TextDirection.LTR
            return SegmentResult(
                dir,
                false,
                emptyList()
            )
        }

        val hasRtlRun = runs.any { it.isRtl }
        val hasLtrRun = runs.any { it.isLtr }
        // 纯方向 fast path
        if (!hasLtrRun) {
            return SegmentResult(
                TextDirection.RTL,
                true,
                emptyList()
            )
        }
        if (!hasRtlRun) {
            return SegmentResult(
                TextDirection.LTR,
                false,
                emptyList()
            )
        }

        val baseRtl = runs[0].isRtl
        // 混合段：direction=基调（首词），runs=视觉序
        val runLayouts = runs.map {
            RunLayout(it.isRtl, it.offset, it.length)
        }
        return SegmentResult(
            direction = if (baseRtl) TextDirection.RTL else TextDirection.LTR,  // ★ 不用 MIXED
            baseRtl = baseRtl,
            runs = runLayouts
        )
    }
}