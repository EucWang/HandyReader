package com.wxn.bookread.data.model


/***
 * 序列行的 锚点标记
 */
data class LineDot(
    var enable: Boolean = false,
    var level: Int = 0,    //行是否前面显示列表符号， 圆点/方块/空心圆.. 大于0即有符号，1 标识1级列表； 2表现2级列表...
    var anchorX: Float = Float.NaN,
    var order:Int = 0,
    /**
     * 标记绘制方向 = 列表锚点方向（segDirect.anchorBaseRtl：显式 dir 声明优先、
     * 无声明回退 SheenBidi 首强；D 方向解耦——与排版基调 baseRtl 分离），
     * 由排版引擎落锚时与 anchorX 同点写入。
     * legacy 纯 LTR 路径不写入 → 默认 false。
     */
    var markerRtl: Boolean = false,
    var shape: ListDotShape? = null
)