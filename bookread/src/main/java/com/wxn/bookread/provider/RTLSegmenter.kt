package com.wxn.bookread.provider

import com.wxn.base.bean.RunLayout
import com.wxn.base.bean.SegmentResult
import com.wxn.base.bean.TextDirection
import com.wxn.bookread.jni.SheenBidiNative

object RTLSegmenter {

    fun segment(text: String, declaredRtl: Boolean? = null) : SegmentResult {
        if (text.isBlank()) {
            return SegmentResult(
                TextDirection.LTR,
                false,
                emptyList(),
                // 空白段无强字符：基调 = LTR 兜底（与 B4 契约一致）；锚点仍由显式声明决定
                declaredRtl ?: false
            )
        }

        // ★ D 方向解耦：排版基调恒 = SheenBidi 首强（显式 dir 不再强制基级——基级与内容
        //   首强冲突时视觉序 runs 跨行拼装错乱，U5 缺陷根）。declaredRtl 的唯一消费 =
        //   锚点方向 anchorBaseRtl。
        val bidiParagraph = SheenBidiNative.bidiRuns(text, baseRtl = false)

        val runs = bidiParagraph.runs
        if (runs.isEmpty()) {
            return SegmentResult(
                TextDirection.LTR,
                false,
                emptyList(),
                declaredRtl ?: false
            )
        }

        // ★ 基调 = native P2-P3 解析的段落基级（first-strong，无强字符兜底 LTR）。
        //   禁止用 runs[0].isRtl 反推：数字/URL 开头的 RTL 段，首 run 是 EN 的 level 2
        //   偶数级（视觉 LTR）run，反推会把 RTL 段误判为 LTR 基调（effAlign/行方向/章节聚合全错）。
        val baseRtl = (bidiParagraph.baseLevel and 1) == 1

        val hasRtlRun = runs.any { it.isRtl }
        val hasLtrRun = runs.any { it.isLtr }
        // 纯方向 fast path
        if (!hasLtrRun) {
            return SegmentResult(
                TextDirection.RTL,
                baseRtl,
                emptyList(),
                declaredRtl ?: baseRtl
            )
        }
        if (!hasRtlRun) {
            return SegmentResult(
                TextDirection.LTR,
                baseRtl,
                emptyList(),
                declaredRtl ?: baseRtl
            )
        }

        return SegmentResult(
            direction = if (baseRtl) TextDirection.RTL else TextDirection.LTR,  // ★ 不用 MIXED
            baseRtl = baseRtl,
            // 混合段：direction=基调，runs=视觉序
            runs = runs.map {
                RunLayout(it.isRtl, it.offset, it.length)
            },
            declaredRtl ?: baseRtl
        )
    }
}