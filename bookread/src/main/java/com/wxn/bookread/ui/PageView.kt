package com.wxn.bookread.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.ui.delegate.PageDelegate
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import androidx.compose.ui.util.fastJoinToString
import androidx.core.graphics.toColorInt
import com.wxn.base.bean.Book
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.ext.screenshot
import com.wxn.base.ext.statusBarHeight
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.arrayIndexAt
import com.wxn.bookread.data.model.visualSpan
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.ui.delegate.CoverPageDelegate
import com.wxn.bookread.ui.delegate.CoverVerticalPageDelegate
import com.wxn.bookread.ui.delegate.NoAnimPageDelegate
import com.wxn.bookread.ui.delegate.SimulationPageDelegate
import com.wxn.bookread.ui.delegate.SlidePageDelegate
import com.wxn.bookread.ui.delegate.SlideVerticalPageDelegate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/****
 * 包含三个ContentView， 对应前页，当前页，下一页 三个页面
 * 控制界面切换， 长按，点击等事件处理
 */
class PageView : FrameLayout, IDataSource, PageCallback {

    constructor(context: Context) : super(context) {
        Logger.i("PageView::constructor1")
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        Logger.i("PageView::constructor2")
    }

    var dataProvider: PageViewDataProvider? = null
    private var downInTopRegion = false
    private var downInBottomRegion = false
    private var isVerticalEdgeSwipe = false
    private val edgeRegionThreshold = 0.08f // 顶部/底部区域阈值，占高度的比例

    /** upBg 协程句柄：快速切背景/换书时取消上一次解码，避免多张大图瞬时共存抬高 OOM 峰值 */
    private var upBgJob: Job? = null

    /***
     * 当前章节中正在显示的页面的索引
     */
    override var pageIndex: Int = 0
        get() {
            return dataProvider?.durChapterPos() ?: 0
        }

    override val currentChapter: TextChapter?
        get() {
            return if (dataProvider?.isInitFinish == true) {
                dataProvider?.textChapter(0)
            } else {
                null
            }
        }

    override val nextChapter: TextChapter?
        get() {
            return if (dataProvider?.isInitFinish == true) {
                dataProvider?.textChapter(1)
            } else {
                null
            }
        }

    override val prevChapter: TextChapter?
        get() {
            return if (dataProvider?.isInitFinish == true) {
                dataProvider?.textChapter(-1)
            } else {
                null
            }
        }

    override fun hasNextChapter(): Boolean {
        val retVal = (dataProvider?.durChapterIndex ?: 0) < (dataProvider?.chapterSize ?: 0) - 1
        Logger.d("PageView::HasNextChapter::retVal=$retVal")
        return retVal
    }

    override fun hasPrevChapter(): Boolean {
        val retVal = (dataProvider?.durChapterIndex ?: 0) > 0
        Logger.d("PageView::hasPrevChapter::retVal=$retVal")
        return retVal
    }

    override fun moveToNextPage() {
        pageDelegate?.nextPageByAnim(animationSpeed)
    }

    override fun moveToPrevPage() {
        pageDelegate?.prevPageByAnim(animationSpeed)
    }

    var pageDelegate: PageDelegate? = null
        private set(value) {
            field?.onDestroy()
            field = null
            field = value
            upContent()
        }

    var isScroll = false

    /***
     * 前一页
     */
    var prevPage: ContentView = ContentView(context)

    /***
     * 当前页
     */
    var curPage: ContentView = ContentView(context)

    /***
     * 下一页
     */
    var nextPage: ContentView = ContentView(context)

    /***
     * 默认的动画播放速度
     */
    var animationSpeed = 320  //页面切换动画速度(ms), 从配置中读取

    /***
     * 是否按下
     */
    private var pressDown = false

    /***
     * touch with moving some distance
     */
    private var isMove = false

    //起始点
    var startX: Float = 0f
    var startY: Float = 0f

    //上一个触碰点
    var lastX: Float = 0f
    var lastY: Float = 0f

    //触碰点
    var touchX: Float = 0f
    var touchY: Float = 0f

    //是否停止动画动作
    var isAbortAnim = false

    //长按
    private var longPressed = false

    // 长按超时时间
    private val longPressTimeout = 600L

    //超时时，触发长按事件
    private val longPressRunnable = Runnable {
        longPressed = true
        onLongPress()
    }

    //是否文本选中
    var isTextSelected = false

    //文字选中时,界面上拖拽操作, NONE 无; START: 拖拽开始滑块; END: 拖拽结束滑块
    private enum class HandleDragMode { NONE, START, END }
    private var handleDragMode = HandleDragMode.NONE
    //拖拽的速度
    private val handleTouchSlopPx by lazy { ViewConfiguration.get(context).scaledTouchSlop * 4 }

    // 手柄拖动时 y 轴偏移补偿量（开始手柄在文本行上方，结束手柄在文本行下方）
    // 数值 = handleLineHeight(24dp) - handleRadius(10dp) = 14dp
    // 需与 ContentTextView 中 drawHandle 的尺寸保持同步
    private var handleDragYOffset = 0f
    private val handleYCompensationPx by lazy {
        RenderResources.handleLineHeightPx - RenderResources.handleRadiusPx
    }

    //是否按下文本选中
    private var pressOnTextSelected = false

    val slopSquare by lazy { ViewConfiguration.get(context).scaledTouchSlop }                       //用户手势滑动的最小距离

    val slopTapDuration by lazy { ViewConfiguration.getTapTimeout() }

    private val centerRectF = RectF(width * 0.33f, height * 0.33f, width * 0.66f, height * 0.66f)   //中间矩形区域
    private val topRectF = RectF(0f, 0f, width.toFloat(), height * 0.4f)   //顶部矩形区域

    private val autoPageRect by lazy { Rect() }

    private val autoPagePint by lazy {
        Paint().apply {
//            color = context.accentColor
            color = "#FFAD1457".toColorInt()
        }
    }

    private var clickTurnPage: Boolean = true //从配置里得到的控制变量
    private var clickAllNext: Boolean = false //从配置里得到的控制变量
    private var clickAreaMode: Int = 0 //0=中间区域模式, 1=顶部区域模式
    private var leftHandedMode: Boolean = false //左手操作模式


    // 翻页方向反转（upPageControl 加载；delegate 与点击翻页读此字段）。
    internal var invertPageTurn: Boolean = false

    init {
        Logger.d("PageView::init")
        addView(nextPage)               //添加三个界面
        addView(curPage)
        addView(prevPage)
        // v6：连接三页 ContentTextView 的尺寸变化重排请求（非连续翻页首帧裁剪修复）。
        // init 期间 dataProvider 仍为 null（由 ReaderView.kt 在 factory 块内 init 之后赋值），
        // 此时若触发 onRequestRepaginate，requestRepaginate 的 `dataProvider ?: return` 会安全跳过。
        nextPage.onRequestRepaginate = ::requestRepaginate
        curPage.onRequestRepaginate = ::requestRepaginate
        prevPage.onRequestRepaginate = ::requestRepaginate
        upBg()                          //更新背景
        setWillNotDraw(false)           //init时不绘制自身
        upPageAnim()
        upPageControl()

        Coroutines.mainScope().launch {
            ChapterProvider.readTipPreferencesUtil?.readTIpPreferencesFlow?.firstOrNull()?.let { preference ->
                clickTurnPage = preference.clickTurnPage
                clickAllNext = preference.clickAllNext
            }
        }
    }

    fun setSelectTextCallback(callback: SelectTextCallback) {
        nextPage.setSelectTextCallback(callback)
        curPage.setSelectTextCallback(callback)
        prevPage.setSelectTextCallback(callback)
    }

    /**
     * v6：ContentTextView 尺寸变化时请求重排的统一入口（非连续翻页首帧裁剪修复）。
     *
     * - 防御 dataProvider 空指针（首帧 pageDelegate 由 upPageAnim 异步构造，可能未就绪）；
     * - 复用 isInitFinish 语义（clear 窗口内的孤儿回调被拦截）；
     * - 版本锁（contentLoadVersion）天然去重三页 ContentView 的并发上报。
     *
     * 首帧 pageDelegate 未就绪时，loadContent → upContent 的 `pageFactory ?: return` 会静默 return，
     * 最终由 resetBook 末尾的 loadContent(true)（版本号更大）兜底胜出。
     */
    fun requestRepaginate() {
        val dp = dataProvider
        if (dp == null) {
            Logger.w("PageView::requestRepaginate skipped - dataProvider null")
            return
        }
        if (!dp.isInitFinish) {
            Logger.d("PageView::requestRepaginate skipped - not initialized")
            return
        }
        Logger.d("PageView::requestRepaginate triggering loadContent")
        dp.loadContent(resetPageOffset = false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Logger.d("PageView::onSizeChanged:w=$w,h=$h,oldw=$oldw,oldh=$oldh")
        centerRectF.set(width * 0.33f, height * 0.33f, width * 0.66f, height * 0.66f)
        topRectF.set(0f, 0f, width.toFloat(), height * 0.4f)
        prevPage.x = -w.toFloat()
        pageDelegate?.setViewSize(w, h)

        // v5：同步更新排版尺寸，消除旋转时 loadContent 读到旧竖屏尺寸的竞争条件。
        // ContentTextView.onSizeChanged → setViewSize 随后会被调用，但因尺寸相同而 skip，
        // 所以这里必须在 loadContent 前同步完成尺寸更新。
        ChapterProvider.synchronouslyUpdateLayout(w, h, oldw, oldh)

        // v6 修复（评审 R2）：仅在真实尺寸变化（旋转）时由 PageView 侧触发 loadContent。
        // 首帧 oldw==0 时跳过 —— 首帧重排改由 ContentTextView.onSizeChanged 的 onRequestRepaginate
        // 回调接管（ContentTextView 用 H−statusBar 的正确尺寸分页）。
        // 旋转 oldw>0 时保留 v5 路径：ContentTextView 旋转后 setViewSize 返回 false（尺寸已由
        // synchronouslyUpdateLayout 同步），不会重复触发重排，旋转必须由本处兜底。
        if (oldw > 0 && oldh > 0 && dataProvider?.isInitFinish == true) {
            dataProvider?.loadContent(resetPageOffset = false)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        pageDelegate?.onDraw(canvas)
        if (!isInEditMode && dataProvider?.isAutoPage == true && !isScroll) {            //非编辑模式，非滚动中， 自动阅读中
            nextPage.screenshot()?.let {                                    //将下一页转换成bitmap，然后绘制到canvas上
                val bottom = dataProvider?.autoPageProgress ?: return
                autoPageRect.set(0, 0, width, bottom)
                canvas.drawBitmap(it, autoPageRect, autoPageRect, null)     //将下一页绘制到canvas上
                canvas.drawRect(                                            //沿着底部绘制一条分割线
                    0f,
                    bottom.toFloat() - 1,
                    width.toFloat(),
                    bottom.toFloat(),
                    autoPagePint
                )
            }
        }
    }

    /***
     * Called by a parent to request that a child update its values for mScrollX and mScrollY if necessary.
     * This will typically be done if the child is animating a scroll using a Scroller object.
     */
    override fun computeScroll() {
        pageDelegate?.scroll()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    /**
     * 触摸事件
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        dataProvider?.screenOffTimerStart()
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleDragMode = HandleDragMode.NONE
                Logger.d("PageView::onTouchEvent::handleDragMode=$handleDragMode,isTextSelected=$isTextSelected")

                if (isTextSelected) { //已经处于文本选中状态，然后再次按下，则判断是否按住了开始位置的拖拽滑块/结束位置的拖拽滑块
                    val handlePosns = curPage.getSelectionHandlePositions() //开始滑块/结束滑块对应的尾
                    if (handlePosns != null) {
                        val (startPos, endPos) = handlePosns
                        val dx1 = event.x - startPos.first; val dy1 = event.y - startPos.second
                        val dx2 = event.x - endPos.first;   val dy2 = event.y - endPos.second
                        val startDistSq = dx1 * dx1 + dy1 * dy1
                        val endDistSq = dx2 * dx2 + dy2 * dy2
                        val slopSq = handleTouchSlopPx * handleTouchSlopPx

                        //根据按下位置,确定是开始拖拽开始滑块, 还是拖拽结束滑块
                        if (startDistSq < slopSq || endDistSq < slopSq) {
                            handleDragMode = if (startDistSq < endDistSq) HandleDragMode.START else HandleDragMode.END
                            //根据是触发了开始滑块还是触发了结束滑块，得到一个和滑块的偏移量，以便用户拖动滑块时，跟随滑块的移动，修正文本的选中位置
                            // ★ Unified-Bottom：start/end 手柄圆心统一在行底（lineBottom + h - r），
                            //   手指 y 需减去补偿量归一到文本行，故 start/end 补偿符号统一为 -c。
                            //   与 ContentTextView.drawSelectionHandles / getSelectionHandlePositions 同步。
                            handleDragYOffset = when (handleDragMode) {
                                HandleDragMode.START -> -handleYCompensationPx
                                HandleDragMode.END -> -handleYCompensationPx
                                HandleDragMode.NONE -> 0f
                            }
                        }
                    }

                    //没有选中任何滑块,则退出选择文字模式
                    if (handleDragMode == HandleDragMode.NONE) {
                        curPage.cancelSelect()
                        isTextSelected = false
                        pressOnTextSelected = true
                    } else {
                        pressOnTextSelected = false
                    }
                } else {
                    pressOnTextSelected = false
                }

                longPressed = false
                if (handleDragMode == HandleDragMode.NONE) {
                    postDelayed(longPressRunnable, longPressTimeout)
                }
                pressDown = true
                isMove = false
                pageDelegate?.onTouch(event)
                if (pageDelegate?.isRunning != true && pageDelegate?.isStarted != true) {
                    setStartPoint(event.x, event.y)
                }
                downInTopRegion = event.y < height * edgeRegionThreshold
                downInBottomRegion = event.y > height * (1 - edgeRegionThreshold)
                isVerticalEdgeSwipe = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (handleDragMode != HandleDragMode.NONE) {
                    when (handleDragMode) {
                        HandleDragMode.START -> curPage.selectStartMove(event.x, event.y + handleDragYOffset)
                        HandleDragMode.END -> curPage.selectEndMove(event.x, event.y + handleDragYOffset)
                        HandleDragMode.NONE -> {}
                    }
                    return true
                }

                pressDown = true
                var isStartMove = false
                if (!isMove) {
                    isStartMove = true
                    isMove = abs(startX - event.x) > slopSquare || abs(startY - event.y) > slopSquare
                }

                // 检测垂直边缘滑动
                if (isMove && !isVerticalEdgeSwipe && (downInTopRegion || downInBottomRegion)) {
                    val dx = abs(event.x - startX)
                    val dy = abs(event.y - startY)
                    if (dy > dx && dy > slopSquare) { // 垂直滑动为主且超过阈值
                        if (downInTopRegion && event.y > startY) { // 从顶部向下
                            isVerticalEdgeSwipe = true
                            Logger.d("PageView::onTouchEvent::downInTopRegion#isVerticalEdgeSwipe#")
                        } else if (downInBottomRegion && event.y < startY) { // 从底部向上
                            isVerticalEdgeSwipe = true
                            Logger.d("PageView::onTouchEvent::downInBottomRegion#isVerticalEdgeSwipe#")
                        }
                        if (isVerticalEdgeSwipe) {
                            removeCallbacks(longPressRunnable) // 取消长按
                            longPressed = false
                        }
                    }
                }

                if (isStartMove && !isVerticalEdgeSwipe) {
                    pageDelegate?.startMove()
                }

                if (isMove && !isVerticalEdgeSwipe) {
                    longPressed = false
                    removeCallbacks(longPressRunnable)
                    if (isTextSelected) {
                        // 长按选中句子后，手指移动不破坏选区
                    } else {
                        pageDelegate?.onTouch(event)
                    }
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)

                if (handleDragMode != HandleDragMode.NONE) {
                    handleDragMode = HandleDragMode.NONE
                    handleDragYOffset = 0f
                    if (isTextSelected) {
                        dataProvider?.showTextActionMenu()
                    }
                    pressOnTextSelected = false
                    isVerticalEdgeSwipe = false
                    downInTopRegion = false
                    downInBottomRegion = false
                    return true
                }

                if (!pressDown) return true
                if (!isMove) {
                    if (!longPressed && !pressOnTextSelected) {
                        val curTimestamp = System.currentTimeMillis()
                        if (curTimestamp - lastActionDown > slopTapDuration) {
                            onSingleTapUp()
                            lastActionDown = curTimestamp
                            return true
                        }
                    }
                }
                if (isTextSelected) {
                    dataProvider?.showTextActionMenu()
                } else if (isMove) {
                    pageDelegate?.onTouch(event)
                }
                pressOnTextSelected = false
                // 重置边缘滑动标志
                isVerticalEdgeSwipe = false
                downInTopRegion = false
                downInBottomRegion = false
            }
        }
        return true
    }

    private var lastActionDown : Long = 0L
    override fun detachAllViewsFromParent() {
        super.detachAllViewsFromParent()
        Logger.d("PageView::detachAllViewsFromParent")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Logger.d("PageView::onAttachedToWindow")
    }

    override fun onDetachedFromWindow() {
        Logger.d("PageView::onDetachedFromWindow")
        removeView(prevPage)
        removeView(curPage)
        removeView(nextPage)
        dataProvider = null
        pageDelegate = null
        isScroll = false
        super.onDetachedFromWindow()
    }

    /****
     * 更新系统状态栏
     */
    fun upStatusBar() {
        curPage.upStatusBar()
        prevPage.upStatusBar()
        nextPage.upStatusBar()
    }

    /**
     * 保存开始位置， 刷新显示
     */
    fun setStartPoint(x: Float, y: Float, invalidate: Boolean = true) {
        startX = x
        startY = y
        lastX = x
        lastY = y
        touchX = x
        touchY = y

        if (invalidate) {
            invalidate()
        }
    }

    /**
     * 保存当前位置,开始滚动
     */
    fun setTouchPoint(x: Float, y: Float, invalidate: Boolean = true) {
        lastX = touchX
        lastY = touchY
        touchX = x
        touchY = y
        if (invalidate) {
            invalidate()
        }
        pageDelegate?.onScroll()
    }

    /**
     * 长按选择
     */
    private fun onLongPress() {
        Logger.i("PageView::onLongPress:startX=$startX, startY=$startY")
        curPage.selectText(startX, startY) { relativePage, lineIndex, charIndex ->
            curPage.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            isTextSelected = true
            curPage.selectSentenceAtChar(relativePage, lineIndex, charIndex)
            dataProvider?.showTextActionMenu()
        }
    }

    /**
     * 单击
     */
    private fun onSingleTapUp(): Boolean {
        if (isTextSelected) {
            isTextSelected = false
            return true
        }
        val padding  = 10f
        //判断是否点击在了链接上
        val curPage = dataProvider?.pageFactory?.currentPage
        val textLines = curPage?.textLines.orEmpty()
        var clickLine: TextLine? = null
        val clickY = startY - context.statusBarHeight
        val clickX = startX
        for (line in textLines) {
            val (lineStartX, lineEndX) = line.textChars.visualSpan() ?: continue
            val lineRect = RectF(lineStartX - padding,
                line.lineTop - padding,
                lineEndX + padding,
                line.lineBottom + padding)
            if (lineRect.contains(clickX, clickY)) {
                clickLine = line
                break
            }
        }
        if (curPage != null && clickLine != null) {
            Logger.d("PageView::onSingleTapUp::curPage.index=[${curPage.index}],clickLine=[${clickLine.text}]")
            val chapterIndex = curPage.chapterIndex
            val paragraphIndex = clickLine.paragraphIndex
            dataProvider?.pageFactory?.let { factory ->
                val (tags, textCssInfo) = factory.getPagesAnnotation(chapterIndex, paragraphIndex,
                    clickLine.charStartOffset,
                    clickLine.charEndOffset)
                val filterTags = tags.filter { item ->
                    (item.name == "a" && item.params.isNotEmpty() && item.params.contains("href")) ||
                        ((item.name == "underline" || item.name == "highlight") && item.params.isNotEmpty() && item.params.contains("color")) ||
                            (item.name == "note" && item.params.isNotEmpty() && item.params.contains("color"))
                }
                if (filterTags.isNotEmpty() && clickLine.textChars.isNotEmpty()) {
                    Logger.d("clickLine#tags:${filterTags.map { it.name }.fastJoinToString(", ")}")
                    val annoIds = arrayListOf<String>()

                    val noteTag = filterTags.firstOrNull {
                        it.name == "note"
                    }
                    if (noteTag != null) {
                        dataProvider?.clickedNote(noteTag.uuid)
                        isTextSelected = true
                        return true
                    }

                    for(itemTag in filterTags) {
                        var startTagCharInLineIndex = -1
                        var endTagCharInLineIndex = -1
                        // 文本口径 → 数组口径（图片占数组位不占文本位，M2-③）
                        if (itemTag.start >= clickLine.charStartOffset && itemTag.start <= clickLine.charEndOffset) {
                            startTagCharInLineIndex = clickLine.arrayIndexAt(itemTag.start - clickLine.charStartOffset)
                        } else if (itemTag.start < clickLine.charStartOffset) {
                            startTagCharInLineIndex = 0
                        }

                        if (itemTag.end >= clickLine.charStartOffset && itemTag.end <= clickLine.charEndOffset) {
                            endTagCharInLineIndex = clickLine.arrayIndexAt(itemTag.end - clickLine.charStartOffset)
                        } else if (itemTag.end > clickLine.charEndOffset) {
                            endTagCharInLineIndex = clickLine.textChars.size - 1
                        }

                        val tagSpan = if (startTagCharInLineIndex in 0 until clickLine.textChars.size &&
                            endTagCharInLineIndex in 0..clickLine.textChars.size
                        ) {
                            val from = startTagCharInLineIndex
                            val to = minOf(endTagCharInLineIndex, clickLine.textChars.size - 1)   // 对应原 endChar = lastOrNull() 分支
                            clickLine.textChars.visualSpan { it in from..to }
                        } else {
                            null
                        }

                        //itemTag在这一行的可点击区域
                        if(tagSpan != null) {
                            val tagInLineRect = RectF(
                                tagSpan.first - padding, clickLine.lineTop - padding,
                                tagSpan.second + padding, clickLine.lineBottom + padding
                            )

                            if(tagInLineRect.contains(clickX, clickY)) {
                                Logger.d("PageView::onSingleTapUp::clickRect=${tagInLineRect},event=(${clickX}, ${clickY})")
                                if (itemTag.name == "a") {
                                    dataProvider?.clickLink(itemTag, clickX, clickY)
                                    return true
                                } else if (itemTag.name == "underline" || itemTag.name == "highlight") {
                                    annoIds.add(itemTag.uuid)
                                }
                            }
                        }
                    }
                    if (annoIds.isNotEmpty()) {
                        dataProvider?.clickedAnnotation(annoIds)
                        isTextSelected = true
                        return true
                    }
                }
            }
        }

        // 根据点击区域模式决定使用哪个区域
        val isClickMenuArea = when (clickAreaMode) {
            0 -> centerRectF.contains(clickX, clickY)  // 中间区域模式
            1 -> topRectF.contains(clickX, clickY)      // 顶部区域模式
            else -> centerRectF.contains(clickX, clickY) // 默认中间区域模式
        }
        
        Logger.d("${this.javaClass.name}:onSingleTapUp::isClickMenuArea=${isClickMenuArea},clickAreaMode=${clickAreaMode},leftHandedMode=${leftHandedMode},clickTurnPage=${clickTurnPage},clickX>width/2=${clickX>width/2}, clickAllNext=${clickAllNext}")
        
        if (isClickMenuArea) {
            if (!isAbortAnim) {
                dataProvider?.clickCenter()
            }
        } else if (clickTurnPage) {

            val isNext = if (leftHandedMode) {
                !(clickX > width / 2 || clickAllNext)
            } else {
                (clickX > width / 2 || clickAllNext)
            }
            if (isNext) {
                pageDelegate?.nextPageByAnim(animationSpeed)
            } else {
                pageDelegate?.prevPageByAnim(animationSpeed)
            }
            dataProvider?.hideMenu()
        }
        return true
    }

    /****
     * 根据方向，切换到上一页或者下一页
     */
    fun fillPage(direction: PageDelegate.Direction) {
        val pageFactory = dataProvider?.pageFactory ?: return
        when (direction) {
            PageDelegate.Direction.PREV -> {
                pageFactory.moveToPrev(true)
            }

            PageDelegate.Direction.NEXT -> {
                pageFactory.moveToNext(true)
            }

            else -> Unit
        }
    }

    override fun loadChapterList(book: Book) {
        dataProvider?.upMsg(context.getString(com.wxn.bookread.R.string.toc_updating))
    }

    /***
     * 更新菜单的显示
     */
    override fun upView() {
//        TODO("Not yet implemented")
    }

    /***
     * 阅读位置发生变化之后, 更新显示
     */
//    override fun pageChanged() {
//        upContent()
//    }

    /****
     * 处理tts  TODO
     */
    override fun contentLoadFinish() {
        Logger.i("PageView::contentLoadFinish()")
//        if (intent.getBooleanExtra("readAloud", false)) {
//            intent.removeExtra("readAloud")
//            ReadBook.readAloud()
//        }
//        loadStates = true
//        invalidate()
    }

    override fun upPageControl() {
        Coroutines.mainScope().launch {
            ChapterProvider.readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()?.let { preference ->
                clickAreaMode = preference.clickAreaMode
                leftHandedMode = preference.leftHandedMode
                invertPageTurn = preference.invertPageTurn
            }
        }
    }

    /***
     * 更新页面切换动画类型
     */
    override fun upPageAnim() {
        Coroutines.mainScope().launch {
            ChapterProvider.readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()?.let { preference ->
                animationSpeed = preference.animationSpeed  //从配置中读取动画速度
//                isScroll = (pageAnim == 4)
                val pageAnim = preference.scroll
//                isScroll = (pageAnim == 4)
                when (pageAnim) {
                    1 -> if (pageDelegate !is CoverPageDelegate) {
                        pageDelegate?.onDestroy()
                        pageDelegate = CoverPageDelegate(this@PageView)
                    }

                    2 -> if (pageDelegate !is SlidePageDelegate) {
                        pageDelegate?.onDestroy()
                        pageDelegate = SlidePageDelegate(this@PageView)
                    }

                    3 -> if (pageDelegate !is SimulationPageDelegate) {
                        pageDelegate?.onDestroy()
                        pageDelegate = SimulationPageDelegate(this@PageView)
                    }

                    4 -> if (pageDelegate !is CoverVerticalPageDelegate) {
                        pageDelegate?.onDestroy()
                        pageDelegate = CoverVerticalPageDelegate(this@PageView)
                    }

                    5 -> if (pageDelegate !is SlideVerticalPageDelegate) {
                        pageDelegate?.onDestroy()
                        pageDelegate = SlideVerticalPageDelegate(this@PageView)
                    }

                    else -> if (pageDelegate !is NoAnimPageDelegate) {
                        pageDelegate?.onDestroy()
                        pageDelegate = NoAnimPageDelegate(this@PageView)
                    }
                }
                Logger.d("PageView::upPageAnim:pageAnim=$pageAnim,isScroll=$isScroll,pageDelegate=${pageDelegate}")
            }
        }
    }

    override fun getSelectedText(): String {
        return curPage.selectedText
    }

    /***
     * 更新界面内容
     */
    override fun upContent(relativePosition: Int, resetPageOffset: Boolean) {
        Logger.i("PageView:upContent:relativePosition=$relativePosition, resetPageOffset=$resetPageOffset")
        val pageFactory = dataProvider?.pageFactory ?: return
        if (isScroll && dataProvider?.isAutoPage != true) {
            curPage.setContent(pageFactory.currentPage, resetPageOffset)
        } else {
            curPage.resetPageOffset()
            when (relativePosition) {
                -1 -> prevPage.setContent(pageFactory.prevPage)
                1 -> nextPage.setContent(pageFactory.nextPage)
                else -> {
                    curPage.setContent(pageFactory.currentPage)
                    nextPage.setContent(pageFactory.nextPage)
                    prevPage.setContent(pageFactory.prevPage)
                }
            }
        }
        dataProvider?.screenOffTimerStart()
    }

    override fun cancelTextSelected() {
        Logger.i("PageView::cancelTextSelected::isTextSelected=$isTextSelected")
        if (isTextSelected) {
            curPage.cancelSelect()
            isTextSelected = false
            handleDragMode = HandleDragMode.NONE
            handleDragYOffset = 0f
        }
    }

//    /***
//     * 如果是点选标注区域，需要设置一些内部参数，所有还是得传递进来
//     */
//    override fun upSelectedRange(startCharX: Float, startCharY: Float, endCharX: Float, endCharY: Float) {
//        startX  = startCharX
//        startY = startCharY
//        curPage.selectText(startX, startY) { relativePage, lineIndex, charIndex ->
//            isTextSelected = true
//            firstRelativePage = relativePage
//            firstLineIndex = lineIndex
//            firstCharIndex = charIndex
//            curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
//            curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
//
//            Coroutines.mainScope().launch {
//                delay(50)
//                selectText(endCharX, endCharY)
//            }
//        }
//    }

    /***
     * 更新提示样式
     */
    override fun upTipStyle() {
        curPage.upTipStyle()
        prevPage.upTipStyle()
        nextPage.upTipStyle()
    }

    /****
     * 更新显示样式
     */
    override fun upStyle() {
//        ChapterProvider.upStyle(context)
        curPage.upStyle()
        prevPage.upStyle()
        nextPage.upStyle()
    }

    /***
     * 更新背景
     */
    override fun upBg() {
        // 快速切背景/换书时取消上一次解码，避免多张大图瞬时共存抬高 OOM 峰值
        upBgJob?.cancel()
        upBgJob = Coroutines.mainScope().launch {
            if (!isAttachedToWindow) return@launch

            ChapterProvider.readerPreferencesUtil?.readerPrefsFlow?.firstOrNull()?.let { preference ->
                val bgDrawable = if (preference.backgroundImage.isNotEmpty()) {
                        val imgPath = preference.backgroundImage
                        // Q-09 OOM 修复：用降采样解码替代 BitmapDrawable.createFromPath。
                        // 原实现全分辨率解码高清照片(单张可达数十MB)，反复切主题/背景时多张大 bitmap 共存直至 GC，易 OOM。
                        // 降采样后仅占用屏幕尺寸级别的内存（通常 <1MB），且 decodeDrawable 内部按需缩放。
                        val absPath = if (imgPath.startsWith("/")) {
                            imgPath
                        } else {
                            val file = File(PathUtil.getDownloadFilePath(context, DownloadFileType.BG_IMAGE, imgPath, imgPath))
                            if (file.exists() && file.canRead()) file.absolutePath else null
                        }
                        absPath?.let { decodeSampledBitmapDrawable(it) }
                    } else {
                        null
                    }
                if (bgDrawable != null) {
                    curPage.setBg(bgDrawable)
                    prevPage.setBg(bgDrawable)
                    nextPage.setBg(bgDrawable)
                } else {
                    val bgColor = preference.backgroundColor
                    val cleanBg = preference.backgroundImage.isEmpty()
                    curPage.setBg(bgColor, cleanBg)
                    prevPage.setBg(bgColor, cleanBg)
                    nextPage.setBg(bgColor, cleanBg)
                }

                val foldColor = if (bgDrawable != null) {
                    val bitmap = (bgDrawable as? BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        withContext(Dispatchers.Default) { computeFoldColor(bitmap) }
                    } else null
                } else null

                pageDelegate?.onBgChanged(foldColor ?: preference.backgroundColor)
                clearBitmapCache()
            }
        }

//        ReadBookConfig.bg ?: let {
//            ReadBookConfig.upBg()
//        }
    }

    /**
     * Q-09 OOM 修复：按视图尺寸降采样解码背景图，避免全分辨率解码大图导致 OOM。
     * 要求 2× 视图尺寸作为目标，保证背景图清晰度（CENTER_CROP 会放大裁剪）。
     * 两遍 decodeFile 移至 IO 线程，避免主线程解码卡顿。
     */
    private suspend fun decodeSampledBitmapDrawable(path: String): BitmapDrawable? {
        return try {
            val maxDim = getDeviceMaxBitmapDimension()
            val reqWidth = (width.coerceAtLeast(1)) * 2
            val reqHeight = (height.coerceAtLeast(1)) * 2
            withContext(Dispatchers.IO) {
                // 第一遍：仅读取尺寸信息（不解码像素）
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                // 第二遍：按 inSampleSize 降采样解码
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight, maxDim)
                }
                BitmapFactory.decodeFile(path, opts)?.let { BitmapDrawable(context.resources, it) }
            }
        } catch (ce: CancellationException) {
            // 绝不可吞没取消异常：否则 upBgJob?.cancel() 失效，被取消的旧协程会继续执行
            // setBg/onBgChanged 等收尾逻辑，导致快速切背景时残留旧背景闪烁，破坏结构化并发。
            throw ce
        } catch (ex: Exception) {
            Logger.w("PageView::decodeSampledBitmapDrawable failed: ${ex.message}")
            null
        }
    }

    private fun calcInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int, maxDim: Int): Int {
        if (srcWidth <= 0 || srcHeight <= 0) return 1
        var inSampleSize = 1
        // 任一维度仍超过目标尺寸就继续降采样（修复 && 对近似方形大图降采样不足的缺陷）
        while (srcWidth / inSampleSize > reqWidth || srcHeight / inSampleSize > reqHeight) {
            inSampleSize *= 2
        }
        // 硬上限：最长边绝不超过设备 Canvas 纹理上限，防止 "trying to draw too large bitmap" 崩溃
        while (maxOf(srcWidth, srcHeight) / inSampleSize > maxDim) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun computeFoldColor(bitmap: Bitmap): Int? {
        return runCatching {
            val scaled = Bitmap.createScaledBitmap(bitmap, 1, 1, true)
            val meanColor = scaled.getPixel(0, 0)
            if (scaled != bitmap) scaled.recycle()

            val factor = 0.85f
            Color.rgb(
                (Color.red(meanColor) * factor).toInt().coerceIn(0, 255),
                (Color.green(meanColor) * factor).toInt().coerceIn(0, 255),
                (Color.blue(meanColor) * factor).toInt().coerceIn(0, 255)
            )
        }.getOrNull()
    }

    /***
     * 更新时间显示
     */
//    fun upTime() {
//        curPage.upTime()
//        prevPage.upTime()
//        nextPage.upTime()
//    }

    /***
     * 更新电池显示
     */
//    fun upBattery(battery: Int) {
//        curPage.upBattery(battery)
//        prevPage.upBattery(battery)
//        nextPage.upBattery(battery)
//    }

    override fun clearBitmapCache() {
        pageDelegate?.clearBitmapCache()
    }

    companion object {
        /** OpenGL ES 3.0 主流纹理上限兜底（Android 12 强制 GLES 3.0），与 ImageProvider 保持一致 */
        private const val FALLBACK_MAX_BITMAP_DIMENSION = 4096

        /** 设备 Canvas 单张 bitmap 安全上限（跨实例缓存，避免每次 new Canvas()）。@Volatile 保证可见性，幂等无需加锁。 */
        @Volatile
        private var cachedMaxBitmapDimension: Int = 0

        /**
         * 获取设备 Canvas 单张 bitmap 安全上限（最长边像素）。
         * API 21+ 可直接查询 Canvas.maximumBitmapWidth/Height。
         * 结果缓存到 [cachedMaxBitmapDimension]，避免每次解码 new Canvas()。
         */
        private fun getDeviceMaxBitmapDimension(): Int {
            cachedMaxBitmapDimension.takeIf { it > 0 }?.let { return it }
            val ret = try {
                val canvas = Canvas()
                val reported = maxOf(canvas.maximumBitmapWidth, canvas.maximumBitmapHeight)
                if (reported > 0) min(reported, FALLBACK_MAX_BITMAP_DIMENSION) else FALLBACK_MAX_BITMAP_DIMENSION
            } catch (e: Throwable) {
                FALLBACK_MAX_BITMAP_DIMENSION
            }
            cachedMaxBitmapDimension = ret
            return ret
        }
    }

}