package com.wxn.bookread.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import androidx.core.graphics.toColorInt
import com.wxn.base.bean.CssFontStyle
import com.wxn.base.bean.CssFontWeight
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.ext.DpExt
import com.wxn.base.ext.getCompatColor
import com.wxn.base.ext.toColor
import com.wxn.bookread.R
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.ext.BitmapExt
import com.wxn.bookread.provider.ChapterProvider

/**
 * 阅读器渲染资源单例。
 *
 * 在 `MainActivity.onCreate()` 中紧跟 `ChapterProvider.init()` 之后调用 [init]。
 * init() 之前访问属性不会崩溃（有 fallback 默认值），但颜色/尺寸可能不准确。
 * 不持有任何 Context 引用，无内存泄漏风险。
 */
object RenderResources {

    private var initialized = false

    // ==================== 颜色常量 ====================

    /** 笔记默认颜色（十六进制字符串） */
    const val NOTE_DEFAULT_COLOR_HEX = "#FFFF00"

    /** 笔记背景半透明 alpha 值（≈ 0.4f * 255 = 102 = 0x66） */
    const val NOTE_BG_ALPHA = 0x66

    // ==================== 尺寸（init 中精确解析，fallback 为近似 dp 值） ====================

    var dp4: Float = 4f; private set
    var dp6: Float = 6f; private set
    var dp12: Float = 12f; private set
    var dp21: Float = 21f; private set
    var handleRadiusPx: Float = 10f; private set
    var handleLineHeightPx: Float = 24f; private set

    // ==================== 笔记图标 Bitmap（init 中加载） ====================

    var noteIconBmp: Bitmap? = null; private set

    // ==================== 画笔 — context 依赖颜色（init 中设置） ====================

    val highlightPaint = Paint().apply { style = Paint.Style.FILL }

    val linePaint = Paint().apply { style = Paint.Style.FILL }

    val selectedPaint = Paint().apply { style = Paint.Style.FILL }

    // ==================== 画笔 — 固定颜色（不需要 context） ====================

    val bookmarkPaint = Paint().apply {
        style = Paint.Style.FILL
        color = "#FF575757".toColorInt()
    }

    val noteBgPaint = Paint().apply { style = Paint.Style.FILL }

    val noteCirclePaint = Paint().apply { style = Paint.Style.FILL }

    val readAloudBgPaint = Paint().apply { style = Paint.Style.FILL }

    val searchHighlightPaint = Paint().apply {
        color = 0x4000BFFF
        style = Paint.Style.FILL
    }

    val imagePlaceholderPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.LTGRAY
    }

    val drawingPaint = TextPaint().apply { isAntiAlias = true }

    val listDotPaint = Paint().apply {
        color = "#FF333333".toColorInt()
        strokeWidth = 15f
    }

    val underlinePaint = Paint().apply {
        color = "#FF575757".toColorInt()
        style = Paint.Style.FILL
    }

    // ==================== 选区手柄画笔 ====================

    val handlePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    val handleStrokePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // ==================== 几何暂存对象 ====================

    val bookmarkPath = Path()
    val noteIconRect = RectF()
    val noteBgRect = RectF()
    val readAloudBgRect = RectF()

    // ==================== 初始化 ====================

    fun init(context: Context) {
        if (initialized) return
        val ctx = context.applicationContext

        highlightPaint.color = ctx.getCompatColor(R.color.highlight)
        linePaint.color = ctx.getCompatColor(R.color.divider)
        selectedPaint.color = ctx.getCompatColor(R.color.btn_bg_press_2)

        noteIconBmp = BitmapExt.bitmapFromResource(ctx, R.drawable.ic_note)

        dp4 = DpExt.dp2px(ctx, 4f)
        dp6 = DpExt.dp2px(ctx, 6f)
        dp12 = DpExt.dp2px(ctx, 12f)
        dp21 = DpExt.dp2px(ctx, 21f)
        handleRadiusPx = DpExt.dp2px(ctx, 10f)
        handleLineHeightPx = DpExt.dp2px(ctx, 24f)

        initialized = true
    }

    /***
     * apply one single character paint to drawingPaint
     * @param ch
     * @param isTitle    current Character is Title
     * @param isBold
     * @param isSmall    current Character font is small size
     * @param textCssInfo  paragraph css info
     * @param inlineScale current Character font size scale
     */
    fun applyCharPaint(
        ch: TextChar,
        isTitle: Boolean,
        isBold: Boolean,
        isSmall: Boolean,
        textCssInfo: TextCssInfo?,
        inlineScale: Float
    ) {
        // ① paragraph fontSize + color : pass title
        if (!isTitle && textCssInfo != null) {
            if (textCssInfo.fontSize.isEm()) {
                drawingPaint.textSize *= textCssInfo.fontSize.value
            } else if (textCssInfo.fontSize.isPx()) {
                drawingPaint.textSize = textCssInfo.fontSize.value
            }
            textCssInfo.fontColor.toColor()?.let { color ->
                drawingPaint.color = color
            }
        }

        // ② inline scale
        if (!isTitle && !ch.isImage && inlineScale != 1f) {
            drawingPaint.textSize *= inlineScale
        }

        // ③ fontWeight / fontStyle
        val effectiveFontWeight = when {
            isBold -> CssFontWeight.FontWeightBold
            textCssInfo != null -> textCssInfo.fontWeight
            else -> CssFontWeight.FontWeightNormal
        }
        val effectiveFontStyle = textCssInfo?.fontStyle ?: CssFontStyle.CssFontStyleNormal

        drawingPaint.typeface = ChapterProvider.getTypeface(effectiveFontWeight, effectiveFontStyle)

        // ④ hasGlyph fallback(字形缺失时换 fallback 字体;hasGlyph 不依赖 textSize,时序无关)
        if (ch.charData.isNotEmpty() && !drawingPaint.hasGlyph(ch.charData)) {
            drawingPaint.typeface = ChapterProvider.fallbackTypeface
        }
        // ⑤ <small> 缩小
        if (isSmall) {
            drawingPaint.textSize *= 0.8f
        }
    }
}
