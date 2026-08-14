package com.wxn.base.bean

data class SegmentResult(
    val direction: TextDirection,   // LTR 或 RTL（基调，首词方向）
    val baseRtl: Boolean,            // 等价 direction==RTL，保留为 layout 选基调方便
    val runs: List<RunLayout>        // 空=纯方向段 fast path；size>=2=混合段
)