package com.wxn.bookread.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withClip
import com.wxn.base.bean.CssFontWeight
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.bean.TextTag
import com.wxn.base.ext.DpExt
import com.wxn.base.ext.getCompatColor
import com.wxn.base.ext.toColor
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.R
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.ext.BitmapExt
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.provider.ChapterProvider.contentPaint
import com.wxn.bookread.provider.ChapterProvider.typeface
import com.wxn.bookread.provider.ImageProvider
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.collections.firstOrNull
import kotlin.math.min

/**
 * 阅读内容界面,
 * 文字和图片在界面上的显示
 * 控制文字的选中
 */
class ContentTextView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    /***
     * 是否允许选中文本
     */
    var selectAble = true

    var upView: ((TextPage) -> Unit)? = null

    /***
     * 选中文字的画笔
     */
    private val selectedPaint by lazy {
        Paint().apply {
            color = context.getCompatColor(R.color.btn_bg_press_2)
            style = Paint.Style.FILL
        }
    }

    private val noteIconBmp : Bitmap? by lazy {
        BitmapExt.bitmapFromResource(context, R.drawable.ic_note)
    }

    /***
     * 选中文字的画笔
     */
    private val highlightPaint by lazy {
        Paint().apply {
            color = context.getCompatColor(R.color.highlight)
            style = Paint.Style.FILL
        }
    }

    private val linePaint by lazy {
        Paint().apply {
            color = context.getCompatColor(R.color.divider)
            style = Paint.Style.FILL
        }
    }

    var callback: SelectTextCallback? = null

    /**
     * 可视矩形
     */
    private val visibleRect = RectF()

    /**
     * 选择字符的开始位置
     * 三个值对应： 页面索引，行索引，字符索引
     */
    private val selectStart = arrayOf(0, 0, 0)

    /***
     * 选择字符的结束位置
     * 三个值对应： 页面索引，行索引，字符索引
     */
    private val selectEnd = arrayOf(0, 0, 0)

    /**
     * 当前显示的TextPage
     */
    private var textPage: TextPage = TextPage()

    //滚动参数
    private val pageFactory: TextPageFactory? get() = callback?.pageFactory

    //滚动偏移量
    private var pageOffset = 0f

    private var textPaintColor: Int = Color.BLACK

    init {
        contentDescription = textPage.text
        Coroutines.mainScope().launch {
            textPaintColor = ChapterProvider.readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()?.textColor ?: Color.BLACK
        }
        Logger.i("ContentTextView::init")
    }

    fun setContent(textPage: TextPage) {
        this.textPage = textPage
        upView?.invoke(textPage)
        contentDescription = textPage.text
        Coroutines.mainScope().launch {
            textPaintColor = ChapterProvider.readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()?.textColor ?: Color.BLACK
        }
        invalidate()
    }

    /***
     * 刷新可见矩形区域
     */
    fun refreshVisibleRect() {
        val left = ChapterProvider.paddingLeft.toFloat()
        val top = ChapterProvider.paddingTop.toFloat()
        val right = ChapterProvider.visibleRight.toFloat()
        val bottom = ChapterProvider.visibleBottom.toFloat()
        Logger.d("ContentTextView::refreshVisibleRect::left=$left, top=$top,right=$right,bottom=$bottom")
        visibleRect.set(left, top, right, bottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        Logger.i("ContentTextView:onSizeChanged:w=$w,h=$h,oldw=$oldw,oldh=$oldh")
        super.onSizeChanged(w, h, oldw, oldh)
        ChapterProvider.setViewSize(context, w, h)
        refreshVisibleRect()
        textPage.format()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawPage(canvas)
    }

    @Deprecated("没有起作用，得删掉")
    private fun getLineMargin(index: Int, textLine:TextLine, calcTextPage: TextPage): Pair<Int,Int> {
        val prevline = if (index > 0) {
            calcTextPage.textLines[index - 1]
        } else {
            null
        }
        val nextline = if(index < calcTextPage.textLines.size - 1) {
            calcTextPage.textLines[index + 1]
        } else {
            null
        }
        var marginTop = 0
        var marginBottom = 0
        prevline?.let { prev ->
            marginTop = (prev.lineBottom - textLine.lineTop).toInt().coerceAtLeast(0)
        }
        nextline?.let {next ->
            marginBottom = (textLine.lineBottom - next.lineTop).toInt().coerceAtLeast(0)
        }
        return marginTop to marginBottom
    }

    /***
     * 绘制页，或者滚动中的下一页或者下下一页
     */
    private fun drawPage(canvas: Canvas) {
        var relativeOffset = relativeOffset(0)
        Logger.i("ContentTextView::drawPage:relativeOffset=$relativeOffset, paddingOffset =$pageOffset, textPage.height=${textPage.height}")
        val factory = pageFactory ?: return

        val chapterIndex = textPage.chapterIndex        //章节索引
        val noteIds = mutableSetOf<String>()

        tryDrawBookmark(canvas)

        textPage.textLines.forEachIndexed { index, textLine ->
            val (marginTop, marginBottom) = getLineMargin(index, textLine, textPage)

            val paragraphIndex = textLine.paragraphIndex    //段落索引
            val startOffset = textLine.charStartOffset + if(textLine.isTableCell) textLine.rowLineOffset else 0   //当前行所在段落起始索引
            val endOffset = textLine.charEndOffset + if(textLine.isTableCell) textLine.rowLineOffset else 0         //当前行所在段落结束索引（不包含）
            val (tags, textCssInfo) = factory.getPagesAnnotation(chapterIndex, paragraphIndex, startOffset, endOffset)

            tryDrawReadAloudBg(canvas, textLine, relativeOffset, marginTop, marginBottom)
            tryDrawNote(canvas, textLine, tags, relativeOffset, noteIds, marginTop, marginBottom)
            drawLine(canvas, textLine, tags, textCssInfo, relativeOffset)
        }

        if (true != callback?.isScroll) return          //非滚动翻页，跳过
        if (pageFactory?.hasNext() != true) return      //没有下一页，跳过

        relativePage(1)?.let { nextPage ->
            relativeOffset = relativeOffset(1)

            val chapterIdx = nextPage.chapterIndex        //章节索引

            nextPage.textLines.forEachIndexed { index, textLine ->        //绘制下一页
                val (marginTop, marginBottom) = getLineMargin(index, textLine, nextPage)

                val paragraphIndex = textLine.paragraphIndex    //段落索引
                val startOffset = textLine.charStartOffset  + if(textLine.isTableCell) textLine.rowLineOffset else 0      //当前行所在段落起始索引
                val endOffset = textLine.charEndOffset  + if(textLine.isTableCell) textLine.rowLineOffset else 0          //当前行所在段落结束索引（不包含）
                val (tags, textCssInfo) = factory.getPagesAnnotation(chapterIdx, paragraphIndex, startOffset, endOffset)

                tryDrawReadAloudBg(canvas, textLine, relativeOffset, marginTop, marginBottom)
                tryDrawNote(canvas, textLine, tags, relativeOffset, noteIds, marginTop, marginBottom)
                drawLine(canvas, textLine, tags, textCssInfo, relativeOffset)
            }
        }

        if (pageFactory?.hasNextPlus() != true) return
        relativeOffset = relativeOffset(2)
        if (relativeOffset < ChapterProvider.visibleHeight) {   //绘制下下一页
            relativePage(2)?.let { nextNextPage ->

                val chapterIdx = nextNextPage.chapterIndex        //章节索引

                nextNextPage.textLines.forEachIndexed { index, textLine ->
                    val (marginTop, marginBottom) = getLineMargin(index, textLine, nextNextPage)

                    val paragraphIndex = textLine.paragraphIndex    //段落索引
                    val startOffset = textLine.charStartOffset + if(textLine.isTableCell) textLine.rowLineOffset else 0      //当前行所在段落起始索引
                    val endOffset = textLine.charEndOffset  + if(textLine.isTableCell) textLine.rowLineOffset else 0          //当前行所在段落结束索引（不包含）
                    val (tags, textCssInfo) = factory.getPagesAnnotation(chapterIdx, paragraphIndex, startOffset, endOffset)

                    tryDrawReadAloudBg(canvas, textLine, relativeOffset, marginTop, marginBottom)
                    tryDrawNote(canvas, textLine, tags, relativeOffset, noteIds, marginTop, marginBottom)
                    drawLine(canvas, textLine, tags, textCssInfo, relativeOffset)
                }
            }
        }
    }

    /***
     * try 都让我 bookmark
     */
    private fun tryDrawBookmark(canvas: Canvas) {
        if (textPage.bookmarkId >= 0) {
            val dp21px = DpExt.dp2px(context, 21f)
            val left = ChapterProvider.viewWidth - dp21px
            val top = 0f
            canvas.drawPath(Path().apply {
                moveTo(left, top)
                lineTo(left + dp21px, top + 0f)
                lineTo(left + dp21px, top + dp21px * 2f)
                lineTo(left + dp21px * 0.5f, top + dp21px * 1.5f)
                lineTo(left + 0f, top + dp21px * 2f)
                lineTo(left + 0f, top + 0f)
            },
                Paint().apply{
                    style = Paint.Style.FILL
                    color = "#FF575757".toColorInt()
                })
        }
    }

    private fun tryDrawReadAloudBg(
        canvas: Canvas,
        textLine: TextLine,
        relativeOffset: Float,
        marginTop : Int,
        marginBottom : Int
    ) {

        val lineTop = textLine.lineTop + relativeOffset
        val lineBottom = textLine.lineBottom + relativeOffset

        if (textLine.isReadAloud) {   //绘制笔记的背景色
            var noteColor = "#FFFF00"
            val dp12px = DpExt.dp2px(context, 12f)
            val dp6px = DpExt.dp2px(context, 6f)
            val left = 0f
            val top = lineTop - (marginTop / 2)
            val right = ChapterProvider.viewWidth.toFloat()
            val bottom = lineBottom + (marginBottom / 2)
            canvas.drawRect(
                RectF(left, top, right, bottom),
                Paint().apply {
                    noteColor.toColor()?.let { intColor ->
                        color = intColor
                        alpha = (0.4 * 256).toInt()
                    }
                    style = Paint.Style.FILL
                })
        }
    }

    private fun tryDrawNote(
        canvas: Canvas,
        textLine: TextLine,
        tags: List<TextTag>,
        relativeOffset: Float,
        noteIds:MutableSet<String>,
        marginTop : Int,
        marginBottom : Int
    ) {
        val lineTop = textLine.lineTop + relativeOffset
        val lineBottom = textLine.lineBottom + relativeOffset

        val noteAtLine = tags.firstOrNull {
            it.name == "note"
        }
        if (noteAtLine != null) {   //绘制笔记的背景色
            var noteColor = noteAtLine.paramsPairs().firstOrNull{
                it.first == "color"
            }?.second.orEmpty()
            if (noteColor.isEmpty()) {
                noteColor = "#FFFF00"
            }

            val dp12px = DpExt.dp2px(context, 12f)
            val dp6px = DpExt.dp2px(context, 6f)

            val left = 0f
            val top = lineTop - (marginTop / 2)
            val right = ChapterProvider.viewWidth.toFloat()
            val bottom = lineBottom + (marginBottom / 2)
            canvas.drawRect(
                RectF(left, top, right, bottom),
                Paint().apply {
                    noteColor.toColor()?.let { intColor ->
                        color = intColor
                        alpha = (0.4 * 256).toInt()
                    }
                    style = Paint.Style.FILL
                })

            if (!noteIds.contains(noteAtLine.uuid)) {
                noteIconBmp?.let { noteIcon ->
                    val left = 0f
                    val top = lineTop - dp12px
                    canvas.drawCircle(left + dp12px, top + dp12px, dp12px, Paint().apply {
                        style = Paint.Style.FILL
                        noteColor.toColor()?.let { intColor ->
                            color = intColor
                        }
                    })
                    canvas.drawBitmap(noteIcon, null, RectF(left + dp6px, top + dp6px, left + 3 * dp6px, top + 3 * dp6px), null)
                    noteIds.add(noteAtLine.uuid)
                }
            }
        }
    }

    /***
     * 绘制行
     */
    private fun drawLine(canvas: Canvas,
                         textLine: TextLine,
                         tags: List<TextTag>,
                         textCssInfo: TextCssInfo?,
                         relativeOffset: Float) {

        val lineTop = textLine.lineTop + relativeOffset
        val lineBase = textLine.lineBase + relativeOffset
        val lineBottom = textLine.lineBottom + relativeOffset

        canvas.withClip(visibleRect) {
            if (textLine.isImage) {                              //绘制图片
                Logger.d("ContentTextView::drawLine:drawImage:lineTop=${lineTop}, lineBottom=${lineBottom}")
                drawImage(this, textLine, lineTop, lineBottom)

            } else if (textLine.isLine) {                        //绘制线段
                val startx = textLine.lineStart.first
                val starty = textLine.lineStart.second + relativeOffset
                val endx = textLine.lineEnd.first
                val endy = textLine.lineEnd.second + relativeOffset

                val linePaint = Paint()
                textLine.lineColor.orEmpty().toColor()?.let { color ->
                    linePaint.color = color
                }
                linePaint.strokeWidth = if (textLine.lineBorder > 0) textLine.lineBorder else 1f

                drawLine(startx, starty, endx, endy, linePaint)
            } else {                                            //绘制一行文字
                drawChars(
                    this, textLine, tags, textCssInfo, lineTop, lineBase, lineBottom,
                    isTitle = textLine.isTitle
                )
            }
        }
    }


    private fun drawChars(
        canvas: Canvas,
        textLine: TextLine,
        textTags: List<TextTag>,
        textCssInfo: TextCssInfo?,
        lineTop: Float,
        lineBase: Float,
        lineBottom: Float,
        isTitle: Boolean
    ) {
        var tagPaint: TextPaint? = null
        var lineTextTag: TextTag? = null
        var charTextTag: TextTag? = null

        //标题或者文本内容的textPaint
        var defaultTextPaint: TextPaint? = null
        if (isTitle) {
            defaultTextPaint = ChapterProvider.titlePaint   //标题
        } else {
            if (textTags.isEmpty()) {
                defaultTextPaint = contentPaint                                                         //没有修饰标签， 默认文字
            } else if (textTags.size == 1) {
                val tagStart = textTags[0].start                                                        //修饰标签相对于段落的开始偏移位置
                val tagEnd = textTags[0].end                                                            //修饰标签相对于段落的开始偏移位置
                val lineStartIndex = textLine.charStartOffset //基于段落的行开始偏移位置
                val lineEndIndex = textLine.charEndOffset      //基于段落的行结束偏移位置

                if (tagEnd <= lineStartIndex || tagStart >= lineEndIndex) { //修饰标签位置和行文字没有对上， 默认文字
                    defaultTextPaint = contentPaint
                } else if (lineStartIndex >= tagStart && lineEndIndex <= tagEnd) {  //修饰标签位置和行文字完全吻合， 修饰标签
                    lineTextTag = textTags[0]
                    defaultTextPaint = ChapterProvider.getPaintByTagName(lineTextTag)
                }
            }
        }

//        if (isReadAloud) {
//            defaultTextPaint?.color = "#FFAD1457".toColorInt()
//        }

        if (textLine.withLineDot > 0) { //绘制html列表前面的 圆点/方块
            val lineTop = textLine.lineTop
            val lineBottom = textLine.lineBottom
            val start =  textLine.textChars.firstOrNull()?.start ?: 0f
            val end = start - 60
            val centerX = (start + end) / 2;
            val centerY = (lineBottom + lineTop) / 2;
            val dotPaint = Paint()
            dotPaint.color =  "#FF333333".toColorInt()
            dotPaint.strokeWidth = 15f
            if (textLine.withLineDot % 2 == 1) {
                canvas.drawCircle(centerX, centerY, 8.0f, dotPaint)
            } else {
                canvas.drawRect(centerX - 10f, centerY - 10f, centerX + 10f, centerY + 10f, dotPaint)
            }
        }

        textLine.textChars.forEachIndexed { index, ch ->
            val charIndex = textLine.charStartOffset + index
            var isHighlight = false     //是否高亮
            var hightlightColor : String = "0xFFFFFF00"
            var underlineColor : String = "0xFF575757"
            var isUnderline = false

            val parentPaint = if (defaultTextPaint != null) defaultTextPaint else {
                val texttag = if (textTags.size == 1) {
                    if (textTags[0].start <= charIndex && charIndex < textTags[0].end) textTags[0] else null
                } else {
//                    filterTags(charIndex, textTags)
                    val tags = arrayListOf<TextTag>()
                    for (tag in textTags) {
                        if (tag.start <= charIndex && charIndex <= tag.end) {
                            if (tag.name in arrayOf("h1", "h2", "h3", "h4", "a")) {
                                tags.add(tag)
                            } else if (tag.name == "underline") {
                                tag.paramsPairs().firstOrNull { it.first == "color" }?.second?.let {
                                    underlineColor = it
                                }
                                isUnderline = true
                            } else if (tag.name == "highlight") {
                                tag.paramsPairs().firstOrNull { it.first == "color" }?.second?.let {
                                    hightlightColor = it
                                    highlightPaint.color = hightlightColor.toColorInt()
                                }
                                isHighlight = true
                            }
                        }
                    }
                    tags.firstOrNull()
                }
                if (isHighlight) {
                    ChapterProvider.contentPaint //高亮是文字使用默认的画笔
                } else {
                    ChapterProvider.getPaintByTagName(texttag)
                }
            }
            val paint = TextPaint()
            paint.set(parentPaint)

            if (!isTitle && textCssInfo !=null) {
                if (textCssInfo.fontSize.isEm()) {
                    paint.textSize *= textCssInfo.fontSize.value
                } else if (textCssInfo.fontSize.isPx()) {
                    paint.textSize = textCssInfo.fontSize.value
                }
                paint.typeface = when(textCssInfo.fontWeight) {
                    CssFontWeight.FontWeightNormal -> {
                        Typeface.create(typeface, Typeface.NORMAL)
                    }
                    CssFontWeight.FontWeightBold -> {
                        Typeface.create(typeface, Typeface.BOLD)
                    }
                    CssFontWeight.FontWeightBolder -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            Typeface.create(typeface, 900, false)
                        } else {
                            Typeface.create(typeface, Typeface.BOLD)
                        }
                    }
                    CssFontWeight.FontWeightLighter -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            Typeface.create(typeface, 300, false)
                        } else {
                            Typeface.create(typeface, Typeface.NORMAL)
                        }
                    }
                }
                textCssInfo.fontColor.toColor()?.let { color ->
                    paint.color = color
                }
            }

            if (isHighlight) {
                val verticalpadding = 10f
                val horizontalpadding = 1f
                //绘制高亮文字时的背景
                canvas.drawRoundRect(RectF(ch.start - horizontalpadding, lineTop - verticalpadding, ch.end + horizontalpadding, lineBottom + verticalpadding), 1f, 1f, highlightPaint)
            }
            if (isUnderline) {                                                   //设置画笔绘制下划线
                underlineColor.toColor()?.let { color ->
                    linePaint.color = color
                }
                linePaint.strokeWidth = 3f

                canvas.drawLine(ch.start, textLine.lineBottom, ch.end,  textLine.lineBottom, linePaint)
            }
            if (ch.selected) {
                //绘制选择文字时的背景框
                canvas.drawRect(ch.start , lineTop, ch.end, lineBottom, selectedPaint)
            }

            if (ch.isImage) {
                val lineTop = textLine.lineTop
                val lineBottom = textLine.lineBottom
                Logger.d("ContentTextView::drawLine:drawInnerImage:lineTop=${lineTop}, lineBottom=${lineBottom}")
                drawImage(canvas, ch, lineTop, lineBottom) //绘制图片
            } else {
                canvas.drawText(ch.charData, ch.start, lineBase, paint) //绘制每一个字
            }
        }
    }

    fun filterTags(charIndex: Int, textTags: List<TextTag>): TextTag? {
        val tags = arrayListOf<TextTag>()
        for (tag in textTags) {
            if (tag.start <= charIndex && charIndex < tag.end) {
                if (tag.name in arrayOf("h1", "h2", "h3", "h4", "a", "underline")) {
                    tags.add(tag)
                }
            }
        }
        return tags.getOrNull(0)
    }

    /***
     * 绘制图片
     */
    private fun drawImage(canvas: Canvas, textLine: TextLine, lineTop: Float, lineBottom: Float) {
        textLine.textChars.forEach { textChar ->
            callback?.book?.let { book ->
                val rectF = RectF(textChar.start, lineTop, textChar.end, lineBottom)
//                Logger.d("ContentTextView::drawImage:rect=[${rectF.left},${rectF.top},${rectF.right},${rectF.bottom},height=${rectF.height()},width=${rectF.width()}]")
                ImageProvider.getImage(context, book, textChar.charData, true)?.let { bmp ->
                    canvas.drawBitmap(bmp, null, rectF, null)
                }
            }
        }
    }

    /***
     * 绘制图片, 行内图片
     */
    private fun drawImage(canvas: Canvas, textChar: TextChar, lineTop: Float, lineBottom: Float) {
        callback?.book?.let { book ->
            val rectF = RectF(textChar.start, lineTop, textChar.end, lineBottom)
//                Logger.d("ContentTextView::drawImage:rect=[${rectF.left},${rectF.top},${rectF.right},${rectF.bottom},height=${rectF.height()},width=${rectF.width()}]")
            ImageProvider.getImage(context, book, textChar.charData, true)?.let { bmp ->
                canvas.drawBitmap(bmp, null, rectF, null)
            }
        }
    }

    //------------------------------------------------------------------------------------------------

    /**
     * 滚动事件时，修改偏移量
     */
    fun onScroll(mOffset: Float) {
        if (mOffset == 0f) return
        val pageFactore = this.pageFactory
        if (pageFactore == null) return
        pageOffset += mOffset //累加偏移量

        if (!pageFactore.hasPrev() && pageOffset > 0) { //在没有上一页的情况下，pageOffset不能大于0
            pageOffset = 0f
        } else if (!pageFactore.hasNext()  //在没有下一页的情况下，pageOffset不能小于0
            && pageOffset < 0
            && pageOffset + textPage.height < ChapterProvider.visibleHeight
        ) {
            val offset = ChapterProvider.visibleHeight - textPage.height
            pageOffset = min(0f, offset)
        } else if (pageOffset > 0) {  //当pageOffset > 0时
            pageFactore.moveToPrev(false)       //ReadBook中配置移动到上一页/或者上一个章节
            textPage = pageFactore.currentPage  //修改当前页为变化之后的TextPage
            pageOffset -= textPage.height       //则pageOffset减去一页的高度
            upView?.invoke(textPage)
        } else if (pageOffset < -textPage.height) { //当pageOffset < 一页高度时
            pageFactore.moveToNext(false)           //ReadBook中配置移动到下一页/或者下一个章节
            pageOffset += textPage.height           //则pageOffset 加上一页的高度
            textPage = pageFactore.currentPage      //修改当前页为变化之后的TextPage
            upView?.invoke(textPage)
        }
        invalidate()                                //刷新界面显示，重新绘制内容
    }

    /**
     * 重置偏移量为0f
     */
    fun resetPageOffset() {
        pageOffset = 0f
    }

    /**
     * 选中文字
     */
    fun selectText(x: Float, y: Float, select: (relativePage: Int, lineIndex: Int, charIndex: Int) -> Unit) {
        if (!selectAble) return //如果禁止选择文字
        if (!visibleRect.contains(x, y)) return  //当前位置超过了可视区域

        var relativeOffset: Float
        for (relativePos in 0..2) { //前中后三个TextPage
            relativeOffset = relativeOffset(relativePos) //3个页面的offset
            if (relativePos > 0) {
                if (true != callback?.isScroll) return                                  //非滚动翻页
                if (relativeOffset >= ChapterProvider.visibleHeight) return     //超过了可视高度
            }

            relativePage(relativePos)?.let { page -> //对应的TextPage
                var top = 0f
                var bottom = 0f
                var start = 0f
                var end = 0f
                for ((lineIndex, textLine) in page.textLines.withIndex()) {
                    top = textLine.lineTop
                    bottom = textLine.lineBottom + relativeOffset
                    if (y > top && y < bottom) {
                        for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                            start = textChar.start
                            end = textChar.end
                            if (x > start && x < end) {
                                if (textChar.isImage) { //选中图片时， 显示个弹窗 TODO

                                } else {
                                    textChar.selected = true
                                    invalidate()
                                    select(relativePos, lineIndex, charIndex)
                                }
                                return
                            }
                        }
                        return
                    }
                }
            }
        }
    }

    /***
     * 开始选择符 移动
     */
    fun selectStartMove(x: Float, y: Float) {
        if (!visibleRect.contains(x, y)) return // 不在可视区域内，则跳过
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                if (true != callback?.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }

            relativePage(relativePos)?.let { page ->
                var top = 0f
                var bottom = 0f
                var start = 0f
                var end = 0f
                for ((lineIndex, textLine) in page.textLines.withIndex()) {
                    top = textLine.lineTop + relativeOffset
                    bottom = textLine.lineBottom + relativeOffset
                    if (y > top && y < bottom) {
                        for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                            start = textChar.start
                            end = textChar.end
                            if (x > start && x < end) {
                                if (selectStart[0] != relativePos
                                    || selectStart[1] != lineIndex
                                    || selectStart[2] != charIndex
                                ) {
                                    //如果选择的开始位置超过了结束位置
                                    if (selectToInt(relativePos, lineIndex, charIndex) > selectToInt(selectEnd)) {
                                        return
                                    }
                                    selectStart[0] = relativePos
                                    selectStart[1] = lineIndex
                                    selectStart[2] = charIndex
                                    upSelectedStart(
                                        textChar.start,
                                        textLine.lineBottom + relativeOffset,
                                        textLine.lineTop + relativeOffset,
                                        textLine.paragraphIndex,
                                        textLine.charStartOffset + charIndex
                                    )
                                    upSelectChars()
                                }
                                return
                            }
                        }
                        return
                    }
                }
            }
        }
    }

    /***
     * 结束选择符 移动
     */
    fun selectEndMove(x: Float, y: Float) {
        if (!visibleRect.contains(x, y)) return

        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                if (true != callback?.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }

            Logger.d("ContentTextView:selectEndMove:x=$x,y=$y")
            relativePage(relativePos)?.let { page ->
                var top: Float
                var bottom: Float
                var start: Float
                var end: Float
                for ((lineIndex, textLine) in page.textLines.withIndex()) {
                    top = textLine.lineTop + relativeOffset
                    bottom = textLine.lineBottom + relativeOffset
                    if (y > top && y < bottom) {
                        for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                            start = textChar.start
                            end = textChar.end
                            if (x > start && x < end) {
                                if (selectEnd[0] != relativePos ||
                                    selectEnd[1] != lineIndex ||
                                    selectEnd[2] != charIndex
                                ) { //当前字符不是最后一个选中的字符
                                    //选中结束符的位置跑到选中开始符的前面去了
                                    if (selectToInt(relativePos, lineIndex, charIndex) < selectToInt(selectStart)) {
                                        return
                                    }
                                    selectEnd[0] = relativePos
                                    selectEnd[1] = lineIndex
                                    selectEnd[2] = charIndex
                                    upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset,
                                        textLine.paragraphIndex,
                                        textLine.charStartOffset + charIndex)
                                    upSelectChars()
                                }
                                return
                            }
                        }
                        return
                    }
                }
            }
        }
    }


    /***
     * 设置 选中开始符
     */
    fun selectStartMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        selectStart[0] = relativePage
        selectStart[1] = lineIndex
        selectStart[2] = charIndex

        relativePage(relativePage)?.textLines[lineIndex]?.let { textLine ->
            val textChar = textLine.textChars[charIndex]
            upSelectedStart(
                textChar.start,
                textLine.lineBottom + relativeOffset(relativePage),
                textLine.lineTop + relativeOffset(relativePage),
                textLine.paragraphIndex,
                textLine.charStartOffset + charIndex
            )
        }
    }

    /***
     * 设置 选中结束符
     */
    fun selectEndMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        selectEnd[0] = relativePage
        selectEnd[1] = lineIndex
        selectEnd[2] = charIndex

        relativePage(relativePage)?.textLines[lineIndex]?.let { textLine ->
            val textChar = textLine.textChars[charIndex]
            upSelectedEnd(textChar.end, textLine.lineBottom + relativeOffset(relativePage),
                textLine.paragraphIndex, textLine.charStartOffset + charIndex)
            upSelectChars()
        }
    }

    /****
     * 取消选中
     */
    fun cancelSelect() {
        Logger.i("ContentTextView::cancelSelect::invalidate")
        val last = if (true == callback?.isScroll) 2 else 0
        for (relativePos in 0..last) {
            relativePage(relativePos)?.textLines?.forEach { textLine ->
                textLine.textChars.forEach { ch ->
                    ch.selected = false
                }
            }
        }
        invalidate()
        callback?.onCancelSelect()
    }

    val selectText: String
        get() {
            val stringBuilder = StringBuilder()
            for (relativePos in selectStart[0]..selectEnd[0]) {
                relativePage(relativePos)?.let { textPage ->
                    if (relativePos == selectStart[0] && relativePos == selectEnd[0]) {
                        for (lineIndex in selectStart[1]..selectEnd[1]) {
                            if (lineIndex == selectStart[1] && lineIndex == selectEnd[1]) {
                                stringBuilder.append(
                                    textPage.textLines[lineIndex].text.substring(
                                        selectStart[2],
                                        selectEnd[2] + 1
                                    )
                                )
                            } else if (lineIndex == selectStart[1]) {
                                stringBuilder.append(
                                    textPage.textLines[lineIndex].text.substring(
                                        selectStart[2]
                                    )
                                )
                            } else if (lineIndex == selectEnd[1]) {
                                stringBuilder.append(
                                    textPage.textLines[lineIndex].text.substring(0, selectEnd[2] + 1)
                                )
                            } else {
                                stringBuilder.append(textPage.textLines[lineIndex].text)
                            }
                        }
                    } else if (relativePos == selectStart[0]) {
                        for (lineIndex in selectStart[1] until (relativePage(relativePos)?.textLines?.size ?: 0)) {
                            if (lineIndex == selectStart[1]) {
                                stringBuilder.append(
                                    textPage.textLines[lineIndex].text.substring(
                                        selectStart[2]
                                    )
                                )
                            } else {
                                stringBuilder.append(textPage.textLines[lineIndex].text)
                            }
                        }
                    } else if (relativePos == selectEnd[0]) {
                        for (lineIndex in 0..selectEnd[1]) {
                            if (lineIndex == selectEnd[1]) {
                                stringBuilder.append(
                                    textPage.textLines[lineIndex].text.substring(0, selectEnd[2] + 1)
                                )
                            } else {
                                stringBuilder.append(textPage.textLines[lineIndex].text)
                            }
                        }
                    } else if (relativePos in selectStart[0] + 1 until selectEnd[0]) {
                        for (lineIndex in selectStart[1]..selectEnd[1]) {
                            stringBuilder.append(textPage.textLines[lineIndex].text)
                        }
                    }
                }
            }
            return stringBuilder.toString()
        }


    private fun upSelectedStart(x: Float, y: Float, top: Float, paragraphIndex: Int, textOffset: Int) {
        callback?.apply {
            upSelectedStart(x, y + headerHeight, top + headerHeight, paragraphIndex, textOffset)
        }
    }

    private fun upSelectedEnd(x: Float, y: Float, paragraphIndex: Int, textOffset: Int) {
        callback?.apply {
            upSelectedEnd(x, y + headerHeight, paragraphIndex, textOffset)
        }
    }

    /***
     * 更新字符的选中状态
     */
    private fun upSelectChars() {
        val last = if (true == callback?.isScroll) 2 else 0

        for (relativePos in 0..last) {
            relativePage(relativePos)?.let { page ->
                for ((lineIndex, textLine) in page.textLines.withIndex()) {
                    for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                        textChar.selected = if (relativePos == selectStart[0]
                            && relativePos == selectEnd[0]
                            && lineIndex == selectStart[1]
                            && lineIndex == selectEnd[1]
                        ) {
                            charIndex in selectStart[2]..selectEnd[2]
                        } else if (relativePos == selectStart[0] && lineIndex == selectStart[1]) {
                            charIndex >= selectStart[2]
                        } else if (relativePos == selectEnd[0] && lineIndex == selectEnd[1]) {
                            charIndex <= selectEnd[2]
                        } else if (relativePos == selectStart[0] && relativePos == selectEnd[0]) {
                            lineIndex in (selectStart[1] + 1) until selectEnd[1]
                        } else if (relativePos == selectStart[0]) {
                            lineIndex > selectStart[1]
                        } else if (relativePos == selectEnd[0]) {
                            lineIndex < selectEnd[1]
                        } else {
                            relativePos in selectStart[0] + 1 until selectEnd[0]
                        }
                    }
                }
            }
        }
        invalidate()
    }

    /***
     * 三个索引值 页面索引， 行索引， 字符索引
     * 转换成一个int值
     */
    private fun selectToInt(page: Int, line: Int, char: Int): Int {
        return page * 10_000_000 + line * 100_000 + char
    }

    /***
     *  三个索引值 页面索引， 行索引， 字符索引
     *  转换成一个int值
     */
    private fun selectToInt(select: Array<Int>): Int {
        return select[0] * 10000000 + select[1] * 100000 + select[2]
    }

    /***
     * 相对偏移量
     */
    private fun relativeOffset(relativePos: Int): Float {
        return when (relativePos) {
            0 -> pageOffset
            1 -> pageOffset + textPage.height
            else -> pageOffset + textPage.height + (pageFactory?.nextPage?.height ?: 0f)
        }
    }

    /****
     * 现对的当前页，或者下一页，或者下下一页
     */
    private fun relativePage(relativePos: Int): TextPage? {
        return when (relativePos) {
            0 -> textPage
            1 -> pageFactory?.nextPage
            else -> pageFactory?.nextPagePlus
        }
    }

    override fun onDetachedFromWindow() {
        Logger.i("ContentTextView::onDetachedFromWindow")
        upView = null
        callback = null
        super.onDetachedFromWindow()
    }
}