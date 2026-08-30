package com.wxn.base.bean

data class SegmentResult(
    val direction: TextDirection,   // LTR 或 RTL（基调，首词方向）
    val baseRtl: Boolean,            // 等价 direction==RTL，保留为 layout 选基调方便
    val runs: List<RunLayout>,       // 空=纯方向段 fast path；size>=2=混合段（视觉序）

    // ★ D 方向解耦：列表锚点方向 = 显式 dir 声明优先、无声明回退 SheenBidi 首强（==baseRtl）。
    //   仅供锚点消费点使用（postProcessRtlLine 锚点块、ChapterProvider 列表序号格式/缩进侧），
    //   排版链路禁用本字段（排版基调恒用 baseRtl）。
    val anchorBaseRtl: Boolean = false
)