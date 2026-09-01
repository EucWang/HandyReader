package com.wxn.bookread.provider

import android.graphics.Canvas
import android.text.TextPaint
import com.wxn.bookread.data.model.TextChar

/**
 * 按 renderGroup 聚合连续字为"shaping run"，整 run 一次 drawText。
 *
 * 修复阿拉伯语等连写文字的逐字绘制断裂（isolated form）：把同词（同 renderGroup）的字
 * 累积成串，flush 时一次性 drawText，让 HarfBuzz 产 initial/medial/final 连写形。
 *
 * 规则：
 *  - needsRunShaping==true 且 renderGroup!=0 且与当前缓冲组相同且 typeface 一致 → 累积
 *  - 组变 / 退出整组整形 / typeface 变（hasGlyph fallback）/ 图片 / 行末 → flush
 *  - needsRunShaping==false → 逐字 drawText（E8：逐字安全字符，justify 组内分布可见）
 *
 * E8 概念分离：renderGroup = 词/分布单元（justify 分组依据，语义不变）；
 * needsRunShaping = 整形单元（是否必须整组 drawText 保 HarfBuzz 连写形），二者独立赋值。
 *
 * 落点 = 组内 min(ch.start)（左边缘，方向无关）。
 * paint = 组首字快照（组内 uniform，由 renderGroup 构造保证 + typeface 引用校验）。
 *
 * 不读不改 TextChar 的 start/end（C1 不变量不受影响）。
 * UI 线程顺序用 / 首 line clear
 */
class ShapedRunBuffer {

    private companion object {
        const val NONE = Int.MIN_VALUE
    }

    private val sb = StringBuilder()
    private var minStart = 0f
    private var maxEnd = 0f        // E8 新增：组内最大 end（flush 宽度校验用）

    private var y = 0f
    private var paint = TextPaint()    // 组首字快照

    private var group = NONE

    fun clear() {
        sb.clear()
        minStart = 0f
        maxEnd = 0f
        y = 0f
        paint.reset()
        group = NONE
    }

    fun draw(canvas: Canvas,
                      ch: TextChar,
                      baselineY: Float,
                      livePaint: TextPaint,
                      next: TextChar?) {
        val rg = ch.renderGroup
        // E8：仅整组整形字符（连写/组合脚本）累积成 shaping run；逐字安全字符直接单字绘制，
        // ch.start 为准 → justify 组内分布（CHAR_DISTRIBUTE/HYBRID perChar）可见。
        val runShaped = ch.needsRunShaping && rg != 0

        val sameTypeface = livePaint.typeface === paint.typeface

        // ① 累积 / 开新 run / rg==0 逐字画
        if (runShaped && group != NONE && rg == group && sameTypeface) {
            sb.append(ch.charData)
            if (ch.start < minStart) {
                minStart = ch.start
            }
            if (ch.end > maxEnd) {
                maxEnd = ch.end
            }
        } else {
            flush(canvas)

            if (runShaped) {
                sb.append(ch.charData)
                minStart = ch.start
                y = baselineY
                group = rg
                paint.set(livePaint)
            } else {
                canvas.drawText(ch.charData, ch.start, baselineY, livePaint)
                group = NONE
            }
        }

        // ② 贪心末字探测：本组到此结束（下一字换组 / 换图 / 退出整组整形 / 行末）→ 立即整形绘制整词
        if (runShaped && (next == null || next.isImage || !next.needsRunShaping || next.renderGroup != rg)) {
            flush(canvas)
        }
    }

    private fun flush(canvas: Canvas) {
        if (sb.isNotEmpty()) {
            // E8 不变量 tripwire：整组整形字符的组内坐标必须自然衔接（决策层保证连写 run 无组内分布）。
            // 方向无关校验：坐标跨度 ≈ 自然整形宽度，背离即说明有特性改写了组内坐标
            //（否则会被整组 drawText 静默吞掉——E7 类缺陷复发时此处第一时间可见）。仅日志，无行为变化。
            val natural = paint.measureText(sb, 0, sb.length)
            if (kotlin.math.abs(natural - (maxEnd - minStart)) > 1f) {
                android.util.Log.w(
                    "ShapedRunBuffer",
                    "组内坐标与整形宽度背离：span=${maxEnd - minStart} natural=$natural group=$group"
                )
            }
            canvas.drawText(sb, 0, sb.length, minStart, y, paint)
        }
        sb.clear()
        group = NONE
        maxEnd = 0f
    }
}