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
 *  - renderGroup != 0 且与当前缓冲组相同且 typeface 一致 → 累积
 *  - renderGroup 变 / renderGroup==0 / typeface 变（hasGlyph fallback）/ 图片 / 行末 → flush
 *  - renderGroup == 0（setNormalText 纯 LTR 路径，未分组）→ 逐字 drawText（= 现状，零回归）
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
    private var y = 0f
    private var paint = TextPaint()    // 组首字快照

    private var group = NONE

    fun clear() {
        sb.clear()
        minStart = 0f
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
        val sameTypeface = livePaint.typeface === paint.typeface

        // ① 累积 / 开新 run / rg==0 逐字画
        if (rg != 0 && group != NONE && rg == group && sameTypeface) {
            sb.append(ch.charData)
            if (ch.start < minStart) {
                minStart = ch.start
            }
        } else {
            flush(canvas)

            if (rg != 0) {
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

        // ② 贪心末字探测：本 group 到此结束（下一字换组 / 换图 / 行末）→ 立即整形绘制整词
        if (rg != 0 && (next == null || next.isImage || next.renderGroup != rg)) {
            flush(canvas)
        }
    }

    private fun flush(canvas: Canvas) {
        if (sb.isNotEmpty()) canvas.drawText(sb, 0, sb.length, minStart, y, paint)
        sb.clear()
        group = NONE
    }
}