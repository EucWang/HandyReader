package com.wxn.bookread.provider

import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.containsCJK
import com.wxn.bookread.data.model.containsConnectedScript
import com.wxn.bookread.data.model.isTrimableWs

/***
 * Justify 分布决策器
 */
object JustifyChecker {

    /** justify 词距上限系数（em）：
     *  gapWidthT 超过则进入 （词距封顶 + 组内字距摊入）；
     * 仅作用于非连写脚本行（KOReader Word Expansion 同型）
     **/
    const val JUSTIFY_MAX_WORD_GAP_FACTOR = 0.5f

    /**
     *  justify 混合分布的组内字距上限系数（em）：
     *  每字符摊入超过则回退为无上限纯词距拉满
     *  （不再放弃对齐，见 docs/plans/2026-09-01-plan-justify-true-shortline-wordfill.md）
     * */
    const val JUSTIFY_MAX_CHAR_GAP_FACTOR = 0.1f

    /** CJK 逐字 justify 的字距上限系数（em），
     * 维持现状值
     **/
    const val CJK_JUSTIFY_CHAR_GAP_FACTOR = 0.25f


    /****
     *  Justify 分布决策方法
     *
     *  @param chars  行内容
     *  @param effWidth 有效宽度
     *  @param textSize 文字数量
     */
    fun resolveJustifyPlan(
        chars : List<TextChar>,
        effWidth: Float,
        textSize: Float
    ): JustifyPlan {

        if (chars.isEmpty() || effWidth <= 0f) {
            return JustifyPlan.SKIP
        }

        //行首行尾剔除，需要提出
        var firstEligible = 0  //第一个非空白有效字符
        while (firstEligible < chars.size &&
            chars[firstEligible].isTrimableWs()) {
            firstEligible++
        }
        var lastEligible = chars.size - 1 //最后一个非空白有效字符
        while (lastEligible >= firstEligible &&
            chars[lastEligible].isTrimableWs()) {
            lastEligible--
        }

        if (firstEligible > lastEligible) {
            return JustifyPlan.SKIP //// 全空白行
        }

        //行首行尾部分，分配的宽度
        var headWs = 0f
        for (i in 0 until firstEligible) {
            headWs += chars[i].end - chars[i].start
        }
        var tailWs = 0f
        for (i in lastEligible+1 until chars.size) {
            tailWs += chars[i].end - chars[i].start
        }

        //行所占的总宽度
        val contentWidth = chars.maxOf { it.end } - chars.minOf{ it.start }
        //总宽度 中抠除掉行首行尾空白符占据的宽度，得到有效显示宽度
        val contentBase = contentWidth - headWs - tailWs

        val maxWordGap = textSize * JUSTIFY_MAX_WORD_GAP_FACTOR

        val hasImage = chars.any { it.isImage }  //有行内图片

        //为什么含图片行 保持现状？
        // ── 含图片行：图片坐标由 ImageLayoutProvider 绝对定位、charData 为路径。
        //    保持现状守卫口径（raw 判定 + distributeWords 整组平移），零行为变化。
        if (hasImage) {
            //根据renderGroup分组计算有多少中间空格，
            val gapCount = chars.groupBy { it.renderGroup }.size - 1
            if (gapCount <= 0) {
                return JustifyPlan.SKIP
            }

            val gapWidthRaw = (effWidth - contentWidth) / gapCount

            return if (gapWidthRaw > 0f && gapWidthRaw <= maxWordGap) {
                JustifyPlan.wordDistribute(gapWidthRaw)
            } else {
                JustifyPlan.SKIP
            }
        }

        val words = chars.groupBy { it.renderGroup }.values.toList()
        var gapCount = words.size - 1

        //── 单个词(包括中日韩无空白符的情况)一行的情况（无空白；可能带拖尾空格）
        if (gapCount <= 0) {
            // 是连写不拆字
            if (chars.containsConnectedScript()) {
                return JustifyPlan.SKIP
            }
            //是非中日韩情况，单个单词占一行
            if (!chars.containsCJK()) {
                return JustifyPlan.SKIP
            }

            //由于一行按照renderGroup只有1个，即中间没有空白符
            //那么只能按照字级别分配多余空间，撑满一行
            gapCount = lastEligible - firstEligible
            if (gapCount <= 0) {
                return JustifyPlan.SKIP
            }

            val gap = (effWidth - contentBase) / gapCount
            val maxGap = textSize * CJK_JUSTIFY_CHAR_GAP_FACTOR
            return if (gap > 0f && gap <= maxGap) {
                JustifyPlan.charDistribute(gap)
            } else {
                JustifyPlan.SKIP
            }
        }

        //多词一行的情况，常见英/德/法/意/阿拉伯语 等语言以空白符分开单词显示的场景
        val gapWidthT = (effWidth - contentBase) / gapCount
        if (gapWidthT <= 0f) {
            return JustifyPlan.SKIP  // 超宽归 exceed
        }

        //每个词能分配的容量在最大可分配容量限制之内
        if (gapWidthT <= maxWordGap) {
            val gapWidthRaw = (effWidth - contentWidth) / gapCount
            if (gapWidthRaw <= 0f) { //剩余 < 首尾空白宽，禁负压缩
                return JustifyPlan.SKIP
            }
            return JustifyPlan.wordDistribute(gapWidthRaw)
        }

        // 词距超限 → 依脚本类型分流

        //阿拉伯语等不能添加词内空间的情况下，词必须连写的场景
        if (chars.containsConnectedScript()) {
            val gapWidthRaw = (effWidth - contentWidth) / gapCount
            return if (gapWidthRaw > 0f) { //只能按照词间分配多余行宽
                JustifyPlan.wordDistribute(gapWidthRaw)
            } else {
                JustifyPlan.SKIP
            }
        }

        //其他语言不需要词连写的场景，则即按照最大限度先在词间分配，然后在每个字母之间分配
        var within = 0
        var across = 0
        for (i in firstEligible + 1 .. lastEligible) {
            if (chars[i - 1].renderGroup == chars[i].renderGroup) {
                within ++
            } else {
                across++
            }
        }
        if (within <= 0) {
            return JustifyPlan.SKIP
        }
        val perCharWidth = (effWidth - contentWidth - across * maxWordGap) / within
        //分配给每个字母间的额外宽度小于限制
        if (perCharWidth <= textSize * JUSTIFY_MAX_CHAR_GAP_FACTOR) {
            return JustifyPlan.hybrid(maxWordGap, perCharWidth)
        }
        //真·短行（字距摊入超限）→ 无上限纯词距拉满，绝不放弃中间行对齐
        val gapWidthRaw = (effWidth - contentWidth) / gapCount
        return if (gapWidthRaw > 0f) {
            JustifyPlan.wordDistribute(gapWidthRaw)
        } else {
            JustifyPlan.SKIP  // 负压缩防御（理论不可达：perChar>cap ⇒ effWidth>contentWidth）
        }

    }
}