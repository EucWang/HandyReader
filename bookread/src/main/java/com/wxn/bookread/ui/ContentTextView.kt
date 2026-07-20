package com.wxn.bookread.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withClip
import com.wxn.base.bean.CssFontStyle
import com.wxn.base.bean.CssFontWeight
import com.wxn.base.bean.Locator
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.bean.TextTag
import com.wxn.base.bean.TtsPlaybackStatus
import com.wxn.base.ext.isCJKChar
import com.wxn.base.ext.isPunctuation
import com.wxn.base.ext.toColor
import com.wxn.base.util.BreakParagraphUtil
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.provider.ImageProvider
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

    /**
     * v6：尺寸变化时请求重排的回调（非连续翻页首帧裁剪修复）。
     *
     * 当 [onSizeChanged] 检测到尺寸真的变化（[ChapterProvider.setViewSize] 返回 true）时触发，
     * 由 [com.wxn.bookread.ui.PageView.requestRepaginate] 统一调度 loadContent。
     * 是否真正执行由 PageView 决定（防御 pageFactory 空指针 + 复用 isInitFinish 语义）。
     */
    var onRequestRepaginate: (() -> Unit)? = null

    var callback: SelectTextCallback? = null

    /**
     * 可视矩形
     */
    private val visibleRect = RectF()

    private data class VisualPos(
        val relativePage: Int,
        val lineIndex: Int,
        val charIndex: Int
    )

    private data class SelectionVisualRange(
        val sP: Int, val sL: Int, val sC: Int,
        val eP: Int, val eL: Int, val eC: Int
    )

    private var drawSelectionRange: SelectionVisualRange? = null

    private fun resolveVisualPos(paragraphIndex: Int, textOffset: Int): VisualPos? {
        for (rp in 0..2) {
            relativePage(rp)?.let { page ->
                for ((li, line) in page.textLines.withIndex()) {
                    if (line.paragraphIndex == paragraphIndex) {
                        val ci = textOffset - line.charStartOffset
                        if (ci >= 0 && ci < line.textChars.size) {
                            return VisualPos(rp, li, ci)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun Locator.isValidSelection(): Boolean {
        if (startParagraphIndex < endParagraphIndex) return true
        if (startParagraphIndex > endParagraphIndex) return false
        return startTextOffset <= endTextOffset
    }

    private fun isCharInSelection(
        relativePos: Int, lineIndex: Int, charIndex: Int,
        sP: Int, sL: Int, sC: Int,
        eP: Int, eL: Int, eC: Int
    ): Boolean {
        return if (relativePos == sP && relativePos == eP && lineIndex == sL && lineIndex == eL) {
            charIndex in sC..eC
        } else if (relativePos == sP && lineIndex == sL) {
            charIndex >= sC
        } else if (relativePos == eP && lineIndex == eL) {
            charIndex <= eC
        } else if (relativePos == sP && relativePos == eP) {
            lineIndex in (sL + 1) until eL
        } else if (relativePos == sP) {
            lineIndex > sL
        } else if (relativePos == eP) {
            lineIndex < eL
        } else {
            relativePos in sP + 1 until eP
        }
    }

    private fun Locator.startVisualPos(): VisualPos? =
        resolveVisualPos(startParagraphIndex, startTextOffset)

    private fun Locator.endVisualPos(): VisualPos? =
        resolveVisualPos(endParagraphIndex, endTextOffset)

    private fun readSelectionLocator(): Locator? = callback?.getSelectionLocator()

    /**
     * 当前显示的TextPage
     */
    var textPage: TextPage = TextPage()

    //滚动参数
    private val pageFactory: TextPageFactory? get() = callback?.pageFactory

    //滚动偏移量
    private var pageOffset = 0f

    init {
        contentDescription = textPage.text
        Logger.i("ContentTextView::init")
    }

    fun setContent(textPage: TextPage) {
        this.textPage = textPage
        upView?.invoke(textPage)
        contentDescription = textPage.text
        invalidate()
    }

    /***
     * 刷新可见矩形区域
     */
    fun refreshVisibleRect() {
        val left = ChapterProvider.paddingHorizontal.toFloat()
        val top = ChapterProvider.paddingVertical.toFloat()
        val right = ChapterProvider.visibleRight.toFloat()
        val bottom = ChapterProvider.visibleBottom.toFloat()
        Logger.d("ContentTextView::refreshVisibleRect::left=$left, top=$top,right=$right,bottom=$bottom")
        visibleRect.set(left, top, right, bottom)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        Logger.i("ContentTextView:onSizeChanged:w=$w,h=$h,oldw=$oldw,oldh=$oldh")
        super.onSizeChanged(w, h, oldw, oldh)
        // v6：同步更新 ChapterProvider 尺寸（内部同步 recomputeDerivedSizes），并取尺寸是否变化
        val changed = ChapterProvider.setViewSize(context, w, h)
        refreshVisibleRect()
        textPage.format()
        // 尺寸真的变化时上报请求重排；是否真正执行由 PageView.requestRepaginate 决定（防御 + 去重）。
        // 不在此处判断 textLines.isNotEmpty() —— AndroidView factory 的 upContent 与本回调相对顺序
        // 不稳定，若 onSizeChanged 先于 setContent，textLines 为空会误过滤合法重排请求（评审 R3）。
        if (changed) {
            onRequestRepaginate?.invoke()
        }
    }


    private var firstFrameDrawn = false
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!firstFrameDrawn) {
            firstFrameDrawn = true
            Logger.d("ReaderView:bookload: FIRST FRAME DRAWN @ ${System.currentTimeMillis()}")
        }
        drawPage(canvas)
    }

    @Deprecated("没有起作用，得删掉")
    private fun getLineMargin(
        index: Int,
        textLine: TextLine,
        calcTextPage: TextPage
    ): Pair<Int, Int> {
        val prevline = if (index > 0) {
            calcTextPage.textLines[index - 1]
        } else {
            null
        }
        val nextline = if (index < calcTextPage.textLines.size - 1) {
            calcTextPage.textLines[index + 1]
        } else {
            null
        }
        var marginTop = 0
        var marginBottom = 0
        prevline?.let { prev ->
            marginTop = (prev.lineBottom - textLine.lineTop).toInt().coerceAtLeast(0)
        }
        nextline?.let { next ->
            marginBottom = (textLine.lineBottom - next.lineTop).toInt().coerceAtLeast(0)
        }
        return marginTop to marginBottom
    }

    /***
     * 绘制页，或者滚动中的下一页或者下下一页
     */
    private fun drawPage(canvas: Canvas) {
        var relativeOffset = relativeOffset(0)
        val startTime = System.currentTimeMillis()
        Logger.i("ContentTextView::drawPage:relativeOffset=$relativeOffset, paddingOffset =$pageOffset, textPage.height=${textPage.height}")
        val factory = pageFactory ?: return

        drawSelectionRange = computeSelectionVisualRange()

        val chapterIndex = textPage.chapterIndex        //章节索引
        val noteIds = mutableSetOf<String>()

        tryDrawBookmark(canvas)

        textPage.textLines.forEachIndexed { index, textLine ->
            val (marginTop, marginBottom) = getLineMargin(index, textLine, textPage)

            val paragraphIndex = textLine.paragraphIndex    //段落索引
            val startOffset =
                textLine.charStartOffset + if (textLine.isTableCell) textLine.rowLineOffset else 0   //当前行所在段落起始索引
            val endOffset =
                textLine.charEndOffset + if (textLine.isTableCell) textLine.rowLineOffset else 0         //当前行所在段落结束索引（不包含）
            val (tags, textCssInfo) = factory.getPagesAnnotation(
                chapterIndex,
                paragraphIndex,
                startOffset,
                endOffset
            )

            drawRelativePage = 0
            drawLineIndex = index
            tryDrawReadAloudBg(canvas, textLine, relativeOffset, marginTop, marginBottom)
            tryDrawSearchResultsBg(canvas, textPage, textLine, relativeOffset)
            tryDrawNote(canvas, textLine, tags, relativeOffset, noteIds, marginTop, marginBottom)
            drawLine(canvas, textLine, tags, textCssInfo, relativeOffset)
        }

        Logger.i("ContentTextView::drawPage:when(no scroll and no next): spend:${System.currentTimeMillis() - startTime}")
        if (true != callback?.isScroll) {
            drawSelectionHandles(canvas)
            return
        }
        if (pageFactory?.hasNext() != true) {
            drawSelectionHandles(canvas)
            return
        }

        relativePage(1)?.let { nextPage ->
            relativeOffset = relativeOffset(1)

            val chapterIdx = nextPage.chapterIndex        //章节索引

            nextPage.textLines.forEachIndexed { index, textLine ->        //绘制下一页
                val (marginTop, marginBottom) = getLineMargin(index, textLine, nextPage)

                val paragraphIndex = textLine.paragraphIndex    //段落索引
                val startOffset =
                    textLine.charStartOffset + if (textLine.isTableCell) textLine.rowLineOffset else 0      //当前行所在段落起始索引
                val endOffset =
                    textLine.charEndOffset + if (textLine.isTableCell) textLine.rowLineOffset else 0          //当前行所在段落结束索引（不包含）
                val (tags, textCssInfo) = factory.getPagesAnnotation(
                    chapterIdx,
                    paragraphIndex,
                    startOffset,
                    endOffset
                )

                drawRelativePage = 1
                drawLineIndex = index
                tryDrawReadAloudBg(canvas, textLine, relativeOffset, marginTop, marginBottom)
                tryDrawSearchResultsBg(canvas, nextPage, textLine, relativeOffset)
                tryDrawNote(
                    canvas,
                    textLine,
                    tags,
                    relativeOffset,
                    noteIds,
                    marginTop,
                    marginBottom
                )
                drawLine(canvas, textLine, tags, textCssInfo, relativeOffset)
            }
        }

        Logger.i("ContentTextView::drawPage: when(no next plus): spend:${System.currentTimeMillis() - startTime}")
        if (pageFactory?.hasNextPlus() != true) {
            drawSelectionHandles(canvas)
            return
        }
        relativeOffset = relativeOffset(2)
        if (relativeOffset < ChapterProvider.visibleHeight) {   //绘制下下一页
            relativePage(2)?.let { nextNextPage ->

                val chapterIdx = nextNextPage.chapterIndex        //章节索引

                nextNextPage.textLines.forEachIndexed { index, textLine ->
                    val (marginTop, marginBottom) = getLineMargin(index, textLine, nextNextPage)

                    val paragraphIndex = textLine.paragraphIndex    //段落索引
                    val startOffset =
                        textLine.charStartOffset + if (textLine.isTableCell) textLine.rowLineOffset else 0      //当前行所在段落起始索引
                    val endOffset =
                        textLine.charEndOffset + if (textLine.isTableCell) textLine.rowLineOffset else 0          //当前行所在段落结束索引（不包含）
                    val (tags, textCssInfo) = factory.getPagesAnnotation(
                        chapterIdx,
                        paragraphIndex,
                        startOffset,
                        endOffset
                    )

                    drawRelativePage = 2
                    drawLineIndex = index
                    tryDrawReadAloudBg(canvas, textLine, relativeOffset, marginTop, marginBottom)
                    tryDrawSearchResultsBg(canvas, nextNextPage, textLine, relativeOffset)
                    tryDrawNote(
                        canvas,
                        textLine,
                        tags,
                        relativeOffset,
                        noteIds,
                        marginTop,
                        marginBottom
                    )
                    drawLine(canvas, textLine, tags, textCssInfo, relativeOffset)
                }
            }
        }
        drawSelectionHandles(canvas)
        Logger.i("ContentTextView::drawPage: full :${System.currentTimeMillis() - startTime}")
    }

    /***
     * try 都让我 bookmark
     */
    private fun tryDrawBookmark(canvas: Canvas) {
        if (textPage.bookmarkId >= 0) {
            val left = ChapterProvider.viewWidth - RenderResources.dp21
            RenderResources.bookmarkPath.reset()
            RenderResources.bookmarkPath.moveTo(left, 0f)
            RenderResources.bookmarkPath.lineTo(left + RenderResources.dp21, 0f)
            RenderResources.bookmarkPath.lineTo(left + RenderResources.dp21, RenderResources.dp21 * 2f)
            RenderResources.bookmarkPath.lineTo(left + RenderResources.dp21 * 0.5f, RenderResources.dp21 * 1.5f)
            RenderResources.bookmarkPath.lineTo(left, RenderResources.dp21 * 2f)
            RenderResources.bookmarkPath.lineTo(left, 0f)
            canvas.drawPath(RenderResources.bookmarkPath, RenderResources.bookmarkPaint)
        }
    }

    private fun tryDrawReadAloudBg(
        canvas: Canvas,
        textLine: TextLine,
        relativeOffset: Float,
        marginTop: Int,
        marginBottom: Int
    ) {
        val speakBookStatus = pageFactory?.getSpeekBookStatus()
        if (speakBookStatus == null ||
            speakBookStatus.speakingStatus != TtsPlaybackStatus.PLAYING ||
            speakBookStatus.readBookLocator == null
        ) {
//            Logger.d("ContentTextView:tryDrawReadAloudBg:speakBookStatus=${speakBookStatus}")
            return
        }
//        Logger.i("ContentTextView:tryDrawReadAloudBg2:${speakBookStatus}")
        val readBookLocator = speakBookStatus.readBookLocator
        val curChapterIndex = this.textPage.chapterIndex
        val curLineParagraphIndex = textLine.paragraphIndex
        if (readBookLocator.chapterIndex != curChapterIndex ||
            readBookLocator.startParagraphIndex != curLineParagraphIndex
        ) {
//            Logger.d("ContentTextView:tryDrawReadAloudBg:curChapterIndex=${curChapterIndex},curLineParagraphIndex=$curLineParagraphIndex, pass")
            return
        }
        val start = readBookLocator.startTextOffset
        val end = readBookLocator.endTextOffset

        val lineStartOffset = textLine.charStartOffset
        val lineEndOffset = textLine.charEndOffset

        if (lineStartOffset > end || lineEndOffset < start) {
//            Logger.d("ContentTextView:tryDrawReadAloudBg:lineStartOffset=$lineStartOffset,lineEndOffset=$lineEndOffset, pass")
            return
        }

        var startCharOffset = Int.MAX_VALUE
        var endCharOffset = Int.MIN_VALUE

        for (i in 0 until textLine.textChars.size) {
            val offset = i + lineStartOffset
            if (offset in start..<end) {
                if (startCharOffset > offset) {
                    startCharOffset = offset
                }
                if (endCharOffset < offset) {
                    endCharOffset = offset
                }
            }
        }
//        Logger.d("ContentTextView:tryDrawReadAloudBg3:startCharOffset=$startCharOffset,endCharOffset=$endCharOffset, textChars.size=${textLine.textChars.size}, lineStartOffset=$lineStartOffset")
        if (startCharOffset == Int.MAX_VALUE || endCharOffset == Int.MIN_VALUE) {
            return
        }
        val startCharIndex = startCharOffset - lineStartOffset
        val endCharIndex = endCharOffset - lineStartOffset
        if (startCharIndex < 0 || startCharIndex >= textLine.textChars.size ||
            endCharIndex < 0 || endCharIndex >= textLine.textChars.size
        ) {
            return
        }
//        Logger.d("ContentTextView::tryDrawReadAloudBg6::speakBookStatus=${speakBookStatus}," +
//                " startCharIndex=$startCharIndex, endCharIndex=$endCharIndex")

        val lineTop = textLine.lineTop + relativeOffset
        val lineBottom = textLine.lineBottom + relativeOffset

        var noteColor = "#FFFF00"
        val left = textLine.textChars[startCharIndex].start
        val top = lineTop - (marginTop / 2)
        val right = textLine.textChars[endCharIndex].end
        val bottom = lineBottom + (marginBottom / 2)
        RenderResources.readAloudBgPaint.color = noteColor.toColor() ?: Color.YELLOW
        RenderResources.readAloudBgPaint.alpha = (0.4f * 255).toInt()
        RenderResources.readAloudBgRect.set(left, top, right, bottom)
        canvas.drawRect(RenderResources.readAloudBgRect, RenderResources.readAloudBgPaint)
    }

    private fun tryDrawSearchResultsBg(
        canvas: Canvas,
        textPageForChapter: TextPage,
        textLine: TextLine,
        relativeOffset: Float,
    ) {
        val highlights = callback?.getSearchHighlights() ?: return
        if (highlights.isEmpty()) return

        val curChapterIndex = textPageForChapter.chapterIndex
        val curLineParagraphIndex = textLine.paragraphIndex

        for (locator in highlights) {
            if (locator.chapterIndex != curChapterIndex) continue
            if (locator.startParagraphIndex != curLineParagraphIndex) continue

            val lineStartOffset = textLine.charStartOffset
            val lineEndOffset = textLine.charEndOffset
            val matchStart = locator.startTextOffset
            val matchEnd = locator.endTextOffset

            if (lineStartOffset >= matchEnd || lineEndOffset <= matchStart) continue

            var startCharIdx = Int.MAX_VALUE
            var endCharIdx = Int.MIN_VALUE
            for (i in textLine.textChars.indices) {
                val offset = i + lineStartOffset
                if (offset in matchStart until matchEnd) {
                    if (startCharIdx > i) startCharIdx = i
                    if (endCharIdx < i) endCharIdx = i
                }
            }
            if (startCharIdx == Int.MAX_VALUE || endCharIdx == Int.MIN_VALUE) continue

            val lineTop = textLine.lineTop + relativeOffset
            val lineBottom = textLine.lineBottom + relativeOffset
            val left = textLine.textChars[startCharIdx].start
            val right = textLine.textChars[endCharIdx].end
            val top = lineTop - RenderResources.dp4
            val bottom = lineBottom + RenderResources.dp4

            canvas.drawRoundRect(RectF(left, top, right, bottom), 4f, 4f, RenderResources.searchHighlightPaint)
        }
    }

    private fun tryDrawNote(
        canvas: Canvas,
        textLine: TextLine,
        tags: List<TextTag>,
        relativeOffset: Float,
        noteIds: MutableSet<String>,
        marginTop: Int,
        marginBottom: Int
    ) {
        val lineTop = textLine.lineTop + relativeOffset
        val lineBottom = textLine.lineBottom + relativeOffset

        val noteAtLine = tags.firstOrNull {
            it.name == "note"
        }
        if (noteAtLine != null) {
            var noteColor = noteAtLine.paramsPairs().firstOrNull {
                it.first == "color"
            }?.second.orEmpty()
            if (noteColor.isEmpty()) {
                noteColor = RenderResources.NOTE_DEFAULT_COLOR_HEX
            }

            // v5 S4：笔记背景按 textChars 首尾坐标绘制，替代原全页宽（left=0, right=viewWidth）。
            // 原写法在双列下会遮挡另一列文字（背景矩形横跨整页宽度，覆盖左/右列内容）。
            // 参照 tryDrawReadAloudBg / tryDrawSearchResultsBg 已有写法（用 textChars 坐标）。
            // 空行（textChars 为空）回退到原全页宽，保持旧行为。
            val (left, right) = if (textLine.textChars.isNotEmpty()) {
                val firstStart = textLine.textChars.first().start
                val lastEnd = textLine.textChars.last().end
                Pair(firstStart, lastEnd)
            } else {
                Pair(0f, ChapterProvider.viewWidth.toFloat())
            }
            val top = lineTop - (marginTop / 2)
            val bottom = lineBottom + (marginBottom / 2)
            RenderResources.noteBgPaint.color = noteColor.toColor() ?: Color.YELLOW
            RenderResources.noteBgPaint.alpha = RenderResources.NOTE_BG_ALPHA
            RenderResources.noteBgRect.set(left, top, right, bottom)
            canvas.drawRect(RenderResources.noteBgRect, RenderResources.noteBgPaint)

            if (!noteIds.contains(noteAtLine.uuid)) {
                RenderResources.noteIconBmp?.let { noteIcon ->
                    // v5 S4：图标也按 textChars 首字符起点定位（原固定 0f 在双列下会落到左列起点）
                    val iconLeft = if (textLine.textChars.isNotEmpty()) {
                        textLine.textChars.first().start
                    } else {
                        0f
                    }
                    val iconTop = lineTop - RenderResources.dp12
                    RenderResources.noteCirclePaint.color = noteColor.toColor() ?: Color.YELLOW
                    canvas.drawCircle(iconLeft + RenderResources.dp12, iconTop + RenderResources.dp12, RenderResources.dp12, RenderResources.noteCirclePaint)
                    RenderResources.noteIconRect.set(iconLeft + RenderResources.dp6, iconTop + RenderResources.dp6, iconLeft + 3 * RenderResources.dp6, iconTop + 3 * RenderResources.dp6)
                    canvas.drawBitmap(noteIcon, null, RenderResources.noteIconRect, null)
                    noteIds.add(noteAtLine.uuid)
                }
            }
        }
    }

    /***
     * 绘制行
     */
    private fun drawLine(
        canvas: Canvas,
        textLine: TextLine,
        tags: List<TextTag>,
        textCssInfo: TextCssInfo?,
        relativeOffset: Float
    ) {

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

                RenderResources.linePaint.color = textLine.lineColor.orEmpty().toColor() ?: Color.BLACK
                RenderResources.linePaint.strokeWidth = if (textLine.lineBorder > 0) textLine.lineBorder else 1f

                drawLine(startx, starty, endx, endy, RenderResources.linePaint)
            } else {                                            //绘制一行文字
                drawChars(
                    this, textLine, tags, textCssInfo, lineTop, lineBase, lineBottom,
                    isTitle = textLine.isTitle
                )
            }
        }
    }


    private var drawRelativePage = 0
    private var drawLineIndex = 0


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
//        var tagPaint: TextPaint? = null
        var lineTextTag: TextTag? = null
//        var charTextTag: TextTag? = null

        //标题或者文本内容的textPaint
        var defaultTextPaint: TextPaint? = null
        if (isTitle) {
            defaultTextPaint = ChapterProvider.titlePaint   //标题
        } else {
            if (textTags.isEmpty()) {
                defaultTextPaint =
                    ChapterProvider.contentPaint                                                         //没有修饰标签， 默认文字
            } else if (textTags.size == 1) {
                val tagStart =
                    textTags[0].start                                                        //修饰标签相对于段落的开始偏移位置
                val tagEnd =
                    textTags[0].end                                                            //修饰标签相对于段落的开始偏移位置
                val lineStartIndex = textLine.charStartOffset //基于段落的行开始偏移位置
                val lineEndIndex = textLine.charEndOffset      //基于段落的行结束偏移位置

                if (tagEnd <= lineStartIndex || tagStart >= lineEndIndex) { //修饰标签位置和行文字没有对上， 默认文字
                    defaultTextPaint = ChapterProvider.contentPaint
                } else if (lineStartIndex >= tagStart && lineEndIndex <= tagEnd) {  //修饰标签位置和行文字完全吻合， 修饰标签
                    lineTextTag = textTags[0]
                    val tagName = textTags[0].name
                    if (tagName != "highlight" && tagName != "underline") {
                        defaultTextPaint = ChapterProvider.getPaintByTagName(lineTextTag)
                    }
                }
            }
        }

        if (textLine.withLineDot > 0) { //绘制html列表前面的 圆点/方块
            val lTop = textLine.lineTop
            val lBottom = textLine.lineBottom
            val start = textLine.textChars.firstOrNull()?.start ?: 0f
            val end = start - 60
            val centerX = (start + end) / 2;
            val centerY = (lBottom + lTop) / 2;
            if (textLine.withLineDot % 2 == 1) {
                canvas.drawCircle(centerX, centerY, 8.0f, RenderResources.listDotPaint)
            } else {
                canvas.drawRect(
                    centerX - 10f,
                    centerY - 10f,
                    centerX + 10f,
                    centerY + 10f,
                    RenderResources.listDotPaint
                )
            }
        }

        var hightlightColor: String = "#FFFFFF00"
        var underlineColor: String = "#FF575757"

        textLine.textChars.forEachIndexed { index, ch ->
            var isHighlight = false     //是否高亮
            var isUnderline = false
            var isBold = false
            var isSmall = false
            val charIndex = textLine.charStartOffset + index
            val parentPaint = if (defaultTextPaint != null) defaultTextPaint else {
                val texttag = if (textTags.size == 1) {
                    val tag = textTags[0]
                    if (tag.start <= charIndex && charIndex < tag.end) {
                        when (tag.name) {
                            "highlight" -> {
                                tag.paramsPairs().firstOrNull { it.first == "color" }?.second?.let {
                                    hightlightColor = it
                                }
                                RenderResources.highlightPaint.color = hightlightColor.toColor() ?: Color.YELLOW
                                isHighlight = true
                            }

                            "underline" -> {
                                tag.paramsPairs().firstOrNull { it.first == "color" }?.second?.let {
                                    underlineColor = it
                                }
                                isUnderline = true
                            }
                            in arrayOf("h1", "h2", "h3", "h4", "a") -> {

                            }
                            in arrayOf("strong", "b", "big") -> {
                                isBold = true
                            }
                            "small" -> {
                                isSmall = true
                            }
                        }
                        tag
                    } else null
                } else {
                    val tags = arrayListOf<TextTag>()
                    for (tag in textTags) {
                        if (tag.start <= charIndex && charIndex < tag.end) {
                            if (tag.name in arrayOf("h1", "h2", "h3", "h4", "a")) {
                                tags.add(tag)
                            } else if (tag.name == "underline") {
                                tag.paramsPairs().firstOrNull { it.first == "color" }?.second?.let {
                                    underlineColor = it
                                    Logger.d("ContentTextView::underlineColor=$underlineColor")
                                }
                                isUnderline = true
                            } else if (tag.name == "highlight") {
                                tag.paramsPairs().firstOrNull { it.first == "color" }?.second?.let {
                                    hightlightColor = it
                                }
                                RenderResources.highlightPaint.color = hightlightColor.toColor() ?: Color.YELLOW
                                isHighlight = true
                            } else if (tag.name == "strong" || tag.name == "b" || tag.name == "big") {
                                isBold = true
                            } else if (tag.name == "small") {
                                isSmall = true
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

            RenderResources.drawingPaint.set(parentPaint)

            if (!isTitle && textCssInfo != null) {
                if (textCssInfo.fontSize.isEm()) {
                    RenderResources.drawingPaint.textSize *= textCssInfo.fontSize.value
                } else if (textCssInfo.fontSize.isPx()) {
                    RenderResources.drawingPaint.textSize = textCssInfo.fontSize.value
                }
                textCssInfo.fontColor.toColor()?.let { color ->
                    RenderResources.drawingPaint.color = color
                }
            }

            val effectiveFontWeight = when {
                isBold -> CssFontWeight.FontWeightBold
                textCssInfo != null -> textCssInfo.fontWeight
                else -> CssFontWeight.FontWeightNormal
            }
            val effectiveFontStyle = textCssInfo?.fontStyle
                ?: CssFontStyle.CssFontStyleNormal
            RenderResources.drawingPaint.typeface = ChapterProvider.getTypeface(effectiveFontWeight, effectiveFontStyle)

            if (ch.charData.isNotEmpty() && !RenderResources.drawingPaint.hasGlyph(ch.charData)) {
                RenderResources.drawingPaint.typeface = ChapterProvider.fallbackTypeface
            }

            if (isSmall) {
                RenderResources.drawingPaint.textSize *= 0.8f
            }

            if (isHighlight) {                //绘制高亮文字时的背景
                val verticalpadding = 10f
                val horizontalpadding = 1f

                canvas.drawRoundRect(
                    RectF(
                        ch.start - horizontalpadding,
                        lineTop - verticalpadding,
                        ch.end + horizontalpadding,
                        lineBottom + verticalpadding
                    ), 1f, 1f, RenderResources.highlightPaint
                )
            }
            if (isUnderline) {                //设置画笔绘制下划线
                RenderResources.linePaint.color = underlineColor.toColor() ?: Color.GRAY
                RenderResources.linePaint.strokeWidth = 3f

                canvas.drawLine(
                    ch.start,
                    textLine.lineBottom,
                    ch.end,
                    textLine.lineBottom,
                    RenderResources.linePaint
                )
            }
            val selRange = drawSelectionRange
            if (selRange != null && isCharInSelection(
                    drawRelativePage, drawLineIndex, index,
                    selRange.sP, selRange.sL, selRange.sC,
                    selRange.eP, selRange.eL, selRange.eC
                )) {
                canvas.drawRect(ch.start, lineTop, ch.end, lineBottom, RenderResources.selectedPaint)
            }

            if (ch.isImage) {                       //绘制图片
                val lTop = textLine.lineTop
                val lBottom = textLine.lineBottom
                Logger.d("ContentTextView::drawLine:drawInnerImage:lineTop=${lTop}, lineBottom=${lBottom}")
                drawImage(canvas, ch, lTop, lBottom)
            } else {
                canvas.drawText(ch.charData, ch.start, lineBase, RenderResources.drawingPaint) //绘制每一个字
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
        // 早退：View 未关联到 reader 时不绘制
        if (callback?.book == null) return
        textLine.textChars.forEach { textChar ->
            val rectF = RectF(textChar.start, lineTop, textChar.end, lineBottom)
            val bmp = ImageProvider.getImage(
                imgSrc = textChar.charData,
                targetWidth = rectF.width().toInt().coerceAtLeast(1),
                targetHeight = rectF.height().toInt().coerceAtLeast(1)
            )
            if (bmp != null && !bmp.isRecycled) {
                try {
                    canvas.drawBitmap(bmp, null, rectF, null)
                } catch (e: RuntimeException) {
                    Logger.e(
                        "ContentTextView::drawImage(line) failed bmpSize=${bmp.width}x${bmp.height} imgSrc=${textChar.charData}",
                        e
                    )
                }
            }
        }
    }

    /***
     * 绘制图片, 行内图片
     */
    private fun drawImage(canvas: Canvas, textChar: TextChar, lineTop: Float, lineBottom: Float) {
        // 早退：View 未关联到 reader 时不绘制
        if (callback?.book == null) return
        val rectF = RectF(textChar.start, lineTop, textChar.end, lineBottom)
        val bmp = ImageProvider.getImage(
            imgSrc = textChar.charData,
            targetWidth = rectF.width().toInt().coerceAtLeast(1),
            targetHeight = rectF.height().toInt().coerceAtLeast(1)
        )
        if (bmp != null && !bmp.isRecycled) {
            try {
                canvas.drawBitmap(bmp, null, rectF, null)
            } catch (e: RuntimeException) {
                Logger.e(
                    "ContentTextView::drawImage(char) failed bmpSize=${bmp.width}x${bmp.height} imgSrc=${textChar.charData}",
                    e
                )
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
    fun selectText(
        x: Float,
        y: Float,
        select: (relativePage: Int, lineIndex: Int, charIndex: Int) -> Unit
    ) {
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
                    top = textLine.lineTop + relativeOffset
                    bottom = textLine.lineBottom + relativeOffset
                    if (y > top && y < bottom) {
                        for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                            start = textChar.start
                            end = textChar.end
                            if (x > start && x < end) {
                                if (textChar.isImage) { //选中图片时， 显示个弹窗 TODO

                                } else {
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
        if (!visibleRect.contains(x, y)) return
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
                                val paragraphIndex = textLine.paragraphIndex
                                val textOffset = textLine.charStartOffset + charIndex
                                val locator = readSelectionLocator()
                                if (locator != null) {
                                    if (locator.startParagraphIndex == paragraphIndex &&
                                        locator.startTextOffset == textOffset
                                    ) {
                                        return
                                    }
                                    if (paragraphIndex > locator.endParagraphIndex ||
                                        (paragraphIndex == locator.endParagraphIndex && textOffset >= locator.endTextOffset)
                                    ) {
                                        return
                                    }
                                }
                                upSelectedStart(
                                    textChar.start,
                                    textLine.lineBottom + relativeOffset,
                                    textLine.lineTop + relativeOffset,
                                    paragraphIndex,
                                    textOffset
                                )
                                upSelectChars()
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
                                val paragraphIndex = textLine.paragraphIndex
                                val textOffset = textLine.charStartOffset + charIndex
                                val locator = readSelectionLocator()
                                if (locator != null) {
                                    if (locator.endParagraphIndex == paragraphIndex &&
                                        locator.endTextOffset == textOffset
                                    ) {
                                        return
                                    }
                                    if (paragraphIndex < locator.startParagraphIndex ||
                                        (paragraphIndex == locator.startParagraphIndex && textOffset <= locator.startTextOffset)
                                    ) {
                                        return
                                    }
                                }
                                upSelectedEnd(
                                    textChar.end, textLine.lineBottom + relativeOffset,
                                    paragraphIndex,
                                    textOffset
                                )
                                upSelectChars()
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
        val page = relativePage(relativePage) ?: return
        val textLine = page.textLines.getOrNull(lineIndex) ?: return
        val textChar = textLine.textChars.getOrNull(charIndex) ?: return

        upSelectedStart(
            textChar.start,
            textLine.lineBottom + relativeOffset(relativePage),
            textLine.lineTop + relativeOffset(relativePage),
            textLine.paragraphIndex,
            textLine.charStartOffset + charIndex
        )
    }

    /***
     * 设置 选中结束符
     */
    fun selectEndMoveIndex(relativePage: Int, lineIndex: Int, charIndex: Int) {
        val page = relativePage(relativePage) ?: return
        val textLine = page.textLines.getOrNull(lineIndex) ?: return
        val textChar = textLine.textChars.getOrNull(charIndex) ?: return

        upSelectedEnd(
            textChar.end, textLine.lineBottom + relativeOffset(relativePage),
            textLine.paragraphIndex, textLine.charStartOffset + charIndex
        )
        upSelectChars()
    }

    /****
     * 取消选中
     */
    fun cancelSelect() {
        Logger.i("ContentTextView::cancelSelect::invalidate")
        invalidate()
        callback?.onCancelSelect()
    }

    val selectText: String
        get() {
            val locator = readSelectionLocator() ?: return ""
            if (!locator.isValidSelection()) return ""
            val startPos = locator.startVisualPos() ?: return ""
            val endPos = locator.endVisualPos() ?: return ""

            val sP = startPos.relativePage
            val sL = startPos.lineIndex
            val sC = startPos.charIndex
            val eP = endPos.relativePage
            val eL = endPos.lineIndex
            val eC = endPos.charIndex

            val stringBuilder = StringBuilder()
            for (relativePos in sP..eP) {
                relativePage(relativePos)?.let { textPage ->
                    if (relativePos == sP && relativePos == eP) {
                        for (lineIndex in sL..eL) {
                            val lineText = textPage.textLines[lineIndex].text
                            if (lineIndex == sL && lineIndex == eL) {
                                stringBuilder.append(
                                    lineText.substring(
                                        sC,
                                        (eC + 1).coerceAtMost(lineText.length)
                                    )
                                )
                            } else if (lineIndex == sL) {
                                stringBuilder.append(
                                    lineText.substring(sC)
                                )
                            } else if (lineIndex == eL) {
                                stringBuilder.append(
                                    lineText.substring(
                                        0,
                                        (eC + 1).coerceAtMost(lineText.length)
                                    )
                                )
                            } else {
                                stringBuilder.append(lineText)
                            }
                        }
                    } else if (relativePos == sP) {
                        for (lineIndex in sL until textPage.textLines.size) {
                            if (lineIndex == sL) {
                                stringBuilder.append(
                                    textPage.textLines[lineIndex].text.substring(
                                        sC
                                    )
                                )
                            } else {
                                stringBuilder.append(textPage.textLines[lineIndex].text)
                            }
                        }
                    } else if (relativePos == eP) {
                        for (lineIndex in 0..eL) {
                            val lineText = textPage.textLines[lineIndex].text
                            if (lineIndex == eL) {
                                stringBuilder.append(
                                    lineText.substring(
                                        0,
                                        (eC + 1).coerceAtMost(lineText.length)
                                    )
                                )
                            } else {
                                stringBuilder.append(lineText)
                            }
                        }
                    } else {
                        for (lineIndex in textPage.textLines.indices) {
                            stringBuilder.append(textPage.textLines[lineIndex].text)
                        }
                    }
                }
            }
            return stringBuilder.toString()
        }


    private fun upSelectedStart(
        x: Float,
        y: Float,
        top: Float,
        paragraphIndex: Int,
        textOffset: Int
    ) {
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
     * 触发重绘以更新选区视觉状态。
     * 分页模式渲染已是无状态的（computeSelectionVisualRange 从 Locator 每帧计算），
     * 此处仅需 invalidate 触发 onDraw。
     */
    private fun upSelectChars() {
        invalidate()
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

    private fun drawHandle(canvas: Canvas, x: Float, lineTop: Float, lineBottom: Float, isStart: Boolean) {
        val r = RenderResources.handleRadiusPx
        val h = RenderResources.handleLineHeightPx
        if (isStart) {
            val cy = lineTop - h + r
            canvas.drawLine(x, lineTop, x, cy, RenderResources.handlePaint)
            canvas.drawCircle(x, cy, r, RenderResources.handlePaint)
            canvas.drawCircle(x, cy, r, RenderResources.handleStrokePaint)
        } else {
            val cy = lineBottom + h - r
            canvas.drawLine(x, lineBottom, x, cy, RenderResources.handlePaint)
            canvas.drawCircle(x, cy, r, RenderResources.handlePaint)
            canvas.drawCircle(x, cy, r, RenderResources.handleStrokePaint)
        }
    }

    private fun computeSelectionVisualRange(): SelectionVisualRange? {
        val locator = readSelectionLocator() ?: return null
        if (!locator.isValidSelection()) return null
        val startPos = locator.startVisualPos() ?: return null
        val endPos = locator.endVisualPos() ?: return null
        return SelectionVisualRange(
            startPos.relativePage, startPos.lineIndex, startPos.charIndex,
            endPos.relativePage, endPos.lineIndex, endPos.charIndex
        )
    }

    private fun drawSelectionHandles(canvas: Canvas) {
        val locator = readSelectionLocator() ?: return
        if (!locator.isValidSelection()) return
        val startPos = locator.startVisualPos() ?: return
        val endPos = locator.endVisualPos() ?: return

        val sP = startPos.relativePage
        val sL = startPos.lineIndex
        val sC = startPos.charIndex
        val eP = endPos.relativePage
        val eL = endPos.lineIndex
        val eC = endPos.charIndex

        val startLine = relativePage(sP)?.textLines?.getOrNull(sL)
        val endLine = relativePage(eP)?.textLines?.getOrNull(eL)
        if (startLine == null || endLine == null) return
        if (sC !in startLine.textChars.indices || eC !in endLine.textChars.indices) return

        val sChar = startLine.textChars[sC]
        val eChar = endLine.textChars[eC]
        val sOff = relativeOffset(sP)
        val eOff = relativeOffset(eP)

        drawHandle(canvas, sChar.start, startLine.lineTop + sOff, startLine.lineBottom + sOff, true)
        drawHandle(canvas, eChar.end, endLine.lineTop + eOff, endLine.lineBottom + eOff, false)
    }

    /**
     * 获取选择文本的开始/结束的位置
     * 这个还不是滑块的位置，开始滑块相对于开始位置还有一个向上的偏移，而结束滑块相对于结束位置有一个向下的偏移
     */
    fun getSelectionHandlePositions(): Pair<Pair<Float, Float>, Pair<Float, Float>>? {
        val locator = readSelectionLocator() ?: return null
        if (!locator.isValidSelection()) return null
        val startPos = locator.startVisualPos() ?: return null
        val endPos = locator.endVisualPos() ?: return null

        val sP = startPos.relativePage
        val sL = startPos.lineIndex
        val sC = startPos.charIndex
        val eP = endPos.relativePage
        val eL = endPos.lineIndex
        val eC = endPos.charIndex

        val sLine = relativePage(sP)?.textLines?.getOrNull(sL) ?: return null
        val sChar = sLine.textChars.getOrNull(sC) ?: return null
        val eLine = relativePage(eP)?.textLines?.getOrNull(eL) ?: return null
        val eChar = eLine.textChars.getOrNull(eC) ?: return null

        return Pair(
            Pair(sChar.start, sLine.lineTop + relativeOffset(sP) - RenderResources.handleLineHeightPx + RenderResources.handleRadiusPx),
            Pair(eChar.end, eLine.lineBottom + relativeOffset(eP) + RenderResources.handleLineHeightPx - RenderResources.handleRadiusPx)
        )
    }

    data class CharInfo(val data: String, val lineIdx: Int, val charIdx: Int)

    fun selectWordAtChar(relativePos: Int, lineIndex: Int, charIndex: Int) {
        val page = relativePage(relativePos) ?: return
        if (lineIndex !in page.textLines.indices) return

        val targetParagraphIndex = page.textLines[lineIndex].paragraphIndex

        val allChars = mutableListOf<CharInfo>()
        var pressedGlobalIndex = -1

        for ((lIdx, line) in page.textLines.withIndex()) {
            if (line.paragraphIndex != targetParagraphIndex) continue
            for ((cIdx, ch) in line.textChars.withIndex()) {
                allChars.add(CharInfo(ch.charData, lIdx, cIdx))
                if (lIdx == lineIndex && cIdx == charIndex) {
                    pressedGlobalIndex = allChars.size - 1
                }
            }
        }
        if (pressedGlobalIndex < 0) return

        val selectSentence = false
        var sentenceStartGlobal: Int = -1
        var sentenceEndGlobal: Int = -1

        //选择整句的逻辑
        if (selectSentence) {
            sentenceStartGlobal = 0
            val charDataList = allChars.map { it.data }
            for (i in (pressedGlobalIndex - 1) downTo 0) {
                val c = allChars[i].data.firstOrNull() ?: continue
                if (c.isPunctuation() && c.category != CharCategory.DASH_PUNCTUATION
                    && !BreakParagraphUtil.isAbbreviationApostrophe(charDataList, i)
                ) {
                    sentenceStartGlobal = i + 1
                    break
                }
            }

            sentenceEndGlobal = allChars.size - 1
            for (i in (pressedGlobalIndex + 1) until allChars.size) {
                val c = allChars[i].data.firstOrNull() ?: continue
                if (c.isPunctuation() && c.category != CharCategory.DASH_PUNCTUATION
                    && !BreakParagraphUtil.isAbbreviationApostrophe(charDataList, i)
                ) {
                    sentenceEndGlobal = i
                    break
                }
            }

            if (sentenceStartGlobal > sentenceEndGlobal) {
                val tmp = sentenceStartGlobal
                sentenceStartGlobal = sentenceEndGlobal
                sentenceEndGlobal = tmp
            }
        } else { //选择单个词的逻辑
            val pressedChar = allChars[pressedGlobalIndex].data.firstOrNull() ?: return
            val isCJK = pressedChar.isCJKChar()
            if (isCJK) {
                // CJK 语言：直接选中按下的那一个字符
                sentenceStartGlobal = pressedGlobalIndex
                sentenceEndGlobal = pressedGlobalIndex
            } else {
                // 拉丁字母等以空格分词的语言：向前向后找到空格边界，选中一个单词
                sentenceStartGlobal = pressedGlobalIndex
                for (i in (pressedGlobalIndex - 1) downTo 0) {
                    val c = allChars[i].data.firstOrNull() ?: continue
                    if (c.isWhitespace() || c.isPunctuation()) {
                        sentenceStartGlobal = i + 1
                        break
                    }
                    if (i == 0) sentenceStartGlobal = 0
                }
                sentenceEndGlobal = pressedGlobalIndex
                for (i in (pressedGlobalIndex + 1) until allChars.size) {
                    val c = allChars[i].data.firstOrNull() ?: continue
                    if (c.isWhitespace() || c.isPunctuation()) {
                        sentenceEndGlobal = i - 1
                        break
                    }
                    if (i == allChars.size - 1) sentenceEndGlobal = allChars.size - 1
                }
            }
        }

        if (sentenceStartGlobal !in allChars.indices || sentenceEndGlobal !in allChars.indices) return

        val startInfo = allChars[sentenceStartGlobal]
        val endInfo = allChars[sentenceEndGlobal]

        val startLine = page.textLines.getOrNull(startInfo.lineIdx) ?: return
        if (startInfo.charIdx !in startLine.textChars.indices) return
        val startChar = startLine.textChars[startInfo.charIdx]
        val offset = relativeOffset(relativePos)
        upSelectedStart(
            startChar.start,
            startLine.lineBottom + offset,
            startLine.lineTop + offset,
            targetParagraphIndex,
            startLine.charStartOffset + startInfo.charIdx
        )

        val endLine = page.textLines.getOrNull(endInfo.lineIdx) ?: return
        if (endInfo.charIdx !in endLine.textChars.indices) return
        val endChar = endLine.textChars[endInfo.charIdx]
        upSelectedEnd(
            endChar.end,
            endLine.lineBottom + offset,
            targetParagraphIndex,
            endLine.charStartOffset + endInfo.charIdx
        )

        upSelectChars()
    }

    override fun onDetachedFromWindow() {
        Logger.i("ContentTextView::onDetachedFromWindow")
        upView = null
        callback = null
        super.onDetachedFromWindow()
    }
}