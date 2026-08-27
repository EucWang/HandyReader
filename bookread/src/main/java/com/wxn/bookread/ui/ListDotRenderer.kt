package com.wxn.bookread.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.LineDot
import com.wxn.bookread.data.model.ListDotShape
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.provider.ChapterProvider
import kotlin.math.max

/**
 * html 列表标记（实心圆/空心圆/方块 + 有序序号）的统一绘制入口（翻页 ContentTextView 与连续滚动
 * ContinuousScrollReaderView 共用，收敛两处历史重复实现）。
 *
 * 垂直定位 = AOSP BulletSpan 语义（android.text.style.BulletSpan#drawLeadingMargin：
 * 剔除行距、取字符带中心）：圆点中心 = lineBase + (ascent + descent)/2，
 * 与行距系数（lineSpacingExtra × lineHeightParam）无关。
 */
object ListDotRenderer {

    const val LIST_INDENT_MIN_PX = 36f

    const val DISC_INK_EM = 0.32f      // 枚举墨迹的锚定基准

    const val DOT_OFFSET_EM = 0.45f    // 锚点偏移（em），dotOffsetPx 接线
    const val UNIT_INDENT_EM = 0.75f   // 每级基础缩进（em），calcListIndent 接线
    const val INK_PAD_EM = 0.05f       // 自洽性校验假设的页缘余量

    /** 字符带中心 = 基线 + (ascent + descent)/2（ascent 为负）。纯函数，JVM 可测。 */
    fun centerY(lineBase: Float, ascent: Float, descent: Float): Float =
        lineBase + (ascent + descent) / 2f

    /** [centerY] 的 TextLine + Paint 重载（band 取 [paint] 字体度量；测试可注入 paint）。 */
    fun centerY(textLine: TextLine, paint: Paint): Float {
        val fm = paint.fontMetrics
        return centerY(textLine.lineBase, fm.ascent, fm.descent)
    }

    /**
     * 绘制一行的列表标记，优先级链：
     * order > 0 → 有序序号（"N." / RTL ".N"，右对齐槽位）；
     * 其次注入的 [LineDot.shape]（调用方覆盖）；
     * 否则 [ListDotShape.shapeForLevel] 规范深度映射
     * （1=实心圆，2=空心圆，≥3=方块，封顶不循环）。
     *
     * @param lineBaseY 该行**实际绘制**的基线 y。翻页模式传入 drawChars 的
     *   `lineBase` 参数（已含 relativeOffset，与字形同帧）；连续滚动模式
     *   走默认值 `textLine.lineBase`（字形即按原始字段绘制）。
     *
     * band 固定取 [ChapterProvider.contentPaint]（正文基准字体，不随行内标签变化，
     * 同 CSS ::marker 不继承行内样式语义）；形状、水平偏移与既有实现逐像素一致。
     */
    fun draw(canvas: Canvas, textLine: TextLine, lineBaseY: Float = textLine.lineBase) {
        val dot = textLine.lineDot ?: return
        if (!dot.enable || dot.level <= 0) return

        if (dot.order > 0) {
            Logger.d("ListDotRender:order=${dot.order} lineIsRtl=${textLine.isRtl} " +
                        "markerRtl=${dot.markerRtl} " +
                        "anchorNaN=${dot.anchorX.isNaN()} anchorX=${dot.anchorX} " +
                        "firstStart=${textLine.textChars.firstOrNull()?.start} " +
                        "maxEnd=${textLine.textChars.maxOfOrNull { it.end }}")
        }

        val textSize = ChapterProvider.contentPaint.textSize
        val markerRtl = dot.markerRtl
        val rawAnchorX = dot.anchorX
        val anchorX = if (!rawAnchorX.isNaN()) {
            rawAnchorX
        } else if (markerRtl) {
            // 理论不可达：RTL 引擎落锚时 anchorX 与 markerRtl 同点写入（C-4）。
            // 无条件告警（零放量），不引入 object 级一次性标志（避免可变状态污染测试，方案 R-9）。
            Logger.w("ListDotRender: RTL 行锚点未落锚（应不可达），回退行尾推导 order=${dot.order}")
            textLine.textChars.maxOfOrNull { it.end } ?: 0f
        } else {
            textLine.textChars.firstOrNull()?.start ?: 0f
        }

        if (dot.order > 0) {
            drawOrderedLabel(canvas, dot, anchorX, markerRtl, lineBaseY)
            return
        }

        val shape = dot.shape ?: ListDotShape.Companion.shapeForLevel(dot.level)

        val offset = dotOffsetPx(textSize) //offset
        val radius = dotRadiusPx(textSize)  //radius

        val centerX = if (markerRtl) anchorX + offset else anchorX - offset
        val fm = ChapterProvider.contentPaint.fontMetrics
        val dotY = centerY(lineBaseY, fm.ascent, fm.descent)

        if (shape.hollow) {
            val stroke = strokePx(textSize)
            RenderResources.listDotStrokePaint.strokeWidth = stroke
            canvas.drawCircle(centerX, dotY, radius - stroke / 2f, RenderResources.listDotStrokePaint)
        }  else if (shape == ListDotShape.DISC) {
            canvas.drawCircle(centerX, dotY, radius, RenderResources.listDotPaint)
        } else {
            val halfW = shapeHalfWidthPx(shape, textSize)
            val halfH = shapeHalfHeightPx(shape, textSize)
            canvas.drawRect(centerX - halfW, dotY - halfH, centerX + halfW, dotY + halfH,
                RenderResources.listDotPaint)
        }
    }

    /****
     * 序号绘制
     */
    private fun drawOrderedLabel(
        canvas: Canvas,
        dot: LineDot,
        anchorX: Float,
        markerRtl: Boolean,
        lineBaseY: Float
    ) {
        val paint = RenderResources.listMarkerPaint
        val label = orderedLabel(dot.order, markerRtl)
        val textWidth = paint.measureText(label)
        val offset = dotOffsetPx(paint.textSize)
        // 水平：点号列共线（外伸放置，css-lists outside 语义）
        //  LTR 右缘 = anchorX − offset（"." 是末字符 ⇒ 点列即右缘，位数向左外伸）；
        //  RTL 左缘 = anchorX + offset（"." 是首字符 ⇒ 点列即左缘，位数向右外伸）。
        val drawX = orderedDrawX(anchorX, offset, textWidth, markerRtl)
        // 垂直：与圆点同 band 中心，反推 baseline
        val fm = paint.fontMetrics
        val centerY = centerY(lineBaseY, fm.ascent, fm.descent)
        canvas.drawText(label, drawX, orderedBaseline(centerY, fm.ascent, fm.descent), paint)
    }

    fun orderedLabel(order: Int, markerRtl:Boolean): String = if (markerRtl) ".$order" else "$order."

    /**
     *  水平：点号列共线（外伸放置，css-lists outside 语义）
    *  LTR 右缘 = anchorX − offset（"." 是末字符 ⇒ 点列即右缘，位数向左外伸）；
    *  RTL 左缘 = anchorX + offset（"." 是首字符 ⇒ 点列即左缘，位数向右外伸）。
     *   两侧与正文间隙 ≥ offset（边缘对齐行恰 = offset，构造性无压字）；
     *   预留不等式 calcListIndent ≥ w + offset − inkPad（LO-7/R-O3，未封顶域）保证外伸不越内容盒。
    */
    internal fun orderedDrawX(anchorX: Float,
                              offset: Float,
                              textWidth: Float,
                              markerRtl: Boolean): Float =
        if (markerRtl) {
            anchorX + offset
        }else {
            anchorX - offset - textWidth
        }

    private fun orderedBaseline(bandCenterY: Float, ascent: Float, descent: Float): Float =
        bandCenterY - (ascent + descent) / 2f

    /****
     * 计算列表段落的缩进量
     *
     * 有序段落缩进 = 基础层级缩进 + 序号列宽。
     *
     * @param level  列表缩进级别
     * @param textSize  列表段落文字大小
     * @param containerWidth 文本容量宽度（双栏时为栏宽；界定基础缩进 1/5 与序号列 15% 两级上限）
     * @param orderLabelWidth 有序列宽：本列表最大序号标签的实测宽（listMarkerPaint.measureText，
     *  *   传入未钳制原始值——15% 封顶防极端长编号，钳制内聚于本函数的单一事实来源）
     */
    fun calcListIndent(level: Int,
                       textSize: Float,
                       containerWidth: Float,
                       orderLabelWidth: Float = 0f): Float {
        if (level <= 0) return 0f

        val base = (level * maxOf(textSize * UNIT_INDENT_EM, LIST_INDENT_MIN_PX))
            .coerceAtMost(containerWidth / 5f)
        val column = orderLabelWidth.coerceIn(0f, containerWidth * 0.15f)
        return base + column
    }

    /***
     * 列表段落锚点的偏移
     */
    fun dotOffsetPx(textSize: Float): Float = (textSize * DOT_OFFSET_EM).coerceIn(14f, 26f)

    /***
     * 列表段落锚点的半径：= DISC 墨迹宽的一半（0.32/2 = 0.16em）。
     * 该半径是所有形状半宽/半高/描边的派生基准（shapeHalfWidthPx 等），勿漏 /2。
     */
    fun dotRadiusPx(textSize: Float): Float = (textSize * DISC_INK_EM / 2f).coerceIn(5f, 10f)


    fun shapeHalfWidthPx(shape: ListDotShape,
                         textSize: Float): Float =
        dotRadiusPx(textSize) * (shape.inkWidthEm / DISC_INK_EM)

    fun shapeHalfHeightPx(shape: ListDotShape,
                          textSize: Float): Float =
        dotRadiusPx(textSize) * (shape.inkHeightEm / DISC_INK_EM)

    /** 空心描边宽：带内 0.04em，下限 1.5px 保证最小档可见 */
    fun strokePx(textSize: Float): Float =
        max(dotRadiusPx(textSize) * 0.25f, 1.5f)
}
