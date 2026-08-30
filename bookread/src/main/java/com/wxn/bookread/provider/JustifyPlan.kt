package com.wxn.bookread.provider

/***
 * justify 分布决策（纯数据）
 *
 */
class JustifyPlan private constructor(

    /***
     * justify 根部决策 类型
     */
    val mode: Mode,

    /**
     * WORD=现状 gapWidthRaw；
     * CHAR=CJK 逐字间距；
     * HYBRID= 封顶词距 0.5em
     */
    val wordGap: Float,

    /**
     * 仅 HYBRID：组内每字符间距
     */
    val perChar: Float
) {

    enum class Mode {
        SKIP,  /* 忽略， */
        WORD_DISTRIBUTE,   /* 词间距 */
        CHAR_DISTRIBUTE,   /* 字间距 */
        HYBRID    /* 混合间距 */
    }

    companion object {

        val SKIP = JustifyPlan(Mode.SKIP, 0f, 0f)

        fun wordDistribute(gapWidthRaw: Float) = JustifyPlan(Mode.WORD_DISTRIBUTE, gapWidthRaw, 0f)

        fun charDistribute(gap: Float) = JustifyPlan(Mode.CHAR_DISTRIBUTE, gap, 0f)

        fun hybrid(wordGap: Float, perChar: Float) = JustifyPlan(Mode.HYBRID, wordGap, perChar)
    }

}