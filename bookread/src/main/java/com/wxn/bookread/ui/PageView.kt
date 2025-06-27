package com.wxn.bookread.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.ui.delegate.PageDelegate
import android.graphics.Paint
import android.view.MotionEvent
import androidx.core.graphics.toColorInt
import com.wxn.base.bean.Book
import com.wxn.base.ext.screenshot
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.ui.delegate.CoverPageDelegate
import com.wxn.bookread.ui.delegate.NoAnimPageDelegate
import com.wxn.bookread.ui.delegate.ScrollPageDelegate
import com.wxn.bookread.ui.delegate.SimulationPageDelegate
import com.wxn.bookread.ui.delegate.SlidePageDelegate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val defaultAnimationSpeed = 300

    /***
     * 是否按下
     */
    private var pressDown = false

    /***
     * 是否移动
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

    //是否按下文本选中
    private var pressOnTextSelected = false

    //
    private var firstRelativePage = 0

    private var firstLineIndex: Int = 0

    private var firstCharIndex: Int = 0

    val slopSquare by lazy { ViewConfiguration.get(context).scaledTouchSlop }                       //用户手势滑动的最小距离

    private val centerRectF = RectF(width * 0.33f, height * 0.33f, width * 0.66f, height * 0.66f)   //中间矩形区域

    private val autoPageRect by lazy { Rect() }

    private val autoPagePint by lazy {
        Paint().apply {
//            color = context.accentColor
            color = "#FFAD1457".toColorInt()
        }
    }

    private var clickTurnPage: Boolean = true //从配置里得到的控制变量
    private var clickAllNext: Boolean = false //从配置里得到的控制变量

    init {
        Logger.d("PageView::init")
        ChapterProvider.init(context)
        addView(nextPage)               //添加三个界面
        addView(curPage)
        addView(prevPage)
        upBg()                          //更新背景
        setWillNotDraw(false)           //init时不绘制自身
        upPageAnim()

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Logger.d("PageView::onSizeChanged:w=$w,h=$h,oldw=$oldw,oldh=$oldh")
        centerRectF.set(width * 0.33f, height * 0.33f, width * 0.66f, height * 0.66f)
        prevPage.x = -w.toFloat()
        pageDelegate?.setViewSize(w, h)
        if (oldw != 0 && oldh != 0) {
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
                if (isTextSelected) {
                    curPage.cancelSelect()
                    isTextSelected = false
                    pressOnTextSelected = true
                } else {
                    pressOnTextSelected = false
                }
                longPressed = false
                postDelayed(longPressRunnable, longPressTimeout)
                pressDown = true
                isMove = false
                pageDelegate?.onTouch(event)
                pageDelegate?.onDown()
                setStartPoint(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                pressDown = true
                if (!isMove) {
                    isMove =
                        abs(startX - event.x) > slopSquare || abs(startY - event.y) > slopSquare
                }
                if (isMove) {
                    longPressed = false
                    removeCallbacks(longPressRunnable)
                    if (isTextSelected) {
                        selectText(event.x, event.y)
                    } else {
                        pageDelegate?.onTouch(event)
                    }
                }
            }

            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (!pressDown) return true
                if (!isMove) {
                    if (!longPressed && !pressOnTextSelected) {
                        onSingleTapUp()
                        return true
                    }
                }
                if (isTextSelected) {
                    dataProvider?.showTextActionMenu()
                } else if (isMove) {
                    pageDelegate?.onTouch(event)
                }
                pressOnTextSelected = false
            }
        }
        return true
    }

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
        curPage.selectText(startX, startY) { relativePage, lineIndex, charIndex ->
            isTextSelected = true
            firstRelativePage = relativePage
            firstLineIndex = lineIndex
            firstCharIndex = charIndex
            curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
            curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
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

        //判断是否点击在了链接上
        val curPage = dataProvider?.pageFactory?.currentPage
        val textLines = curPage?.textLines.orEmpty()
        var clickLine: TextLine? = null
        for (line in textLines) {
            val clickY = startY - ChapterProvider.paddingTop.toFloat()
            val lineStartX = line.textChars.getOrNull(0)?.start ?: -1f
            val lineEndX = line.textChars.lastOrNull()?.end ?: -1f
            if (line.lineTop <= clickY && clickY <= line.lineBottom && lineStartX <= startX && startX <= lineEndX ) {
                clickLine = line
                break
            }
        }
        if (curPage != null && clickLine != null) {
            val chapterIndex = curPage.chapterIndex
            val paragraphIndex = clickLine.paragraphIndex
            dataProvider?.pageFactory?.let { factory ->
                val (tags, textCssInfo) = factory.getPagesAnnotation(chapterIndex, paragraphIndex,
                    clickLine.charStartOffset + if (clickLine.isTableCell) clickLine.rowLineOffset else 0,
                    clickLine.charEndOffset + if(clickLine.isTableCell) clickLine.rowLineOffset else 0 )
                val tag = tags.firstOrNull { item ->
                    item.name == "a" && item.params.isNotEmpty() && item.params.contains("href")
                }
                if (tag != null) {
                    val tagStart = tag.start
                    val tagEnd = tag.end
                    var clickChar: TextChar? = null
                    for((index, textChar) in clickLine.textChars.withIndex()) {
                        val charIndex = clickLine.charStartOffset + index
                        if (tagStart <= charIndex && charIndex < tagEnd &&
                            textChar.start <= startX && startX <= textChar.end) { //is a link character
                            clickChar = textChar
                            break
                        }
                    }
                    if (clickChar != null) {
                        Logger.d("PageView::onSingleTapUp::clickChar=${clickChar},event=(${startX}, ${startY})")
                        dataProvider?.clickLink(tag, startX, startY)
                        return true
                    }
                }
            }
        }

        if (centerRectF.contains(startX, startY)) {
            if (!isAbortAnim) {
                dataProvider?.clickCenter()
            }
        } else if (clickTurnPage) {
            if (startX > width / 2 || clickAllNext) {
                pageDelegate?.nextPageByAnim(defaultAnimationSpeed)
            } else {
                pageDelegate?.prevPageByAnim(defaultAnimationSpeed)
            }
        }
        return true
    }


    /**
     * 选择文本
     */
    private fun selectText(x: Float, y: Float) {
        curPage.selectText(x, y) { relativePage, lineIndex, charIndex ->
            when {
                relativePage > firstRelativePage -> {
                    curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectEndMoveIndex(relativePage, lineIndex, charIndex)
                }

                relativePage < firstRelativePage -> {
                    curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectStartMoveIndex(relativePage, lineIndex, charIndex)
                }

                lineIndex > firstLineIndex -> {
                    curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectEndMoveIndex(relativePage, lineIndex, charIndex)
                }

                lineIndex < firstLineIndex -> {
                    curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectStartMoveIndex(relativePage, lineIndex, charIndex)
                }

                charIndex > firstCharIndex -> {
                    curPage.selectStartMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectEndMoveIndex(relativePage, lineIndex, charIndex)
                }

                else -> {
                    curPage.selectEndMoveIndex(firstRelativePage, firstLineIndex, firstCharIndex)
                    curPage.selectStartMoveIndex(relativePage, lineIndex, charIndex)
                }
            }
        }
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
     * 跳转到对应界面
     */
    override fun pageChanged() {
//        TODO("Not yet implemented")
    }

    /****
     * 处理tts  TODO
     */
    override fun contentLoadFinish() {
//        if (intent.getBooleanExtra("readAloud", false)) {
//            intent.removeExtra("readAloud")
//            ReadBook.readAloud()
//        }
//        loadStates = true
    }

    /***
     * 更新页面切换动画类型
     */
    override fun upPageAnim() {
        Coroutines.mainScope().launch {
            ChapterProvider.tryCreatePreference(context)
            ChapterProvider.readTipPreferencesUtil?.readTIpPreferencesFlow?.firstOrNull()?.let { preference ->
                val pageAnim = preference.pageAnim
                isScroll = pageAnim == 3
                when (pageAnim) {
                    0 -> if (pageDelegate !is CoverPageDelegate) {
                        pageDelegate = CoverPageDelegate(this@PageView)
                    }

                    1 -> if (pageDelegate !is SlidePageDelegate) {
                        pageDelegate = SlidePageDelegate(this@PageView)
                    }

                    2 -> if (pageDelegate !is SimulationPageDelegate) {
                        pageDelegate = SimulationPageDelegate(this@PageView)
                    }

                    3 -> if (pageDelegate !is ScrollPageDelegate) {
                        pageDelegate = ScrollPageDelegate(this@PageView)
                    }

                    else -> if (pageDelegate !is NoAnimPageDelegate) {
                        pageDelegate = NoAnimPageDelegate(this@PageView)
                    }
                }
                Logger.d("PageView::upPageAnim:pageAnim=$pageAnim,isScroll=$isScroll,pageDelegate=${pageDelegate}")
            }
        }
    }

    override fun getSelectedText(): String {
        return prevPage.selectedText +
                curPage.selectedText +
                nextPage.selectedText
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

    /***
     * 更新提示样式
     */
    fun upTipStyle() {
        curPage.upTipStyle()
        prevPage.upTipStyle()
        nextPage.upTipStyle()
    }

    /****
     * 更新显示样式
     */
    fun upStyle() {
        ChapterProvider.upStyle(context)
        curPage.upStyle()
        prevPage.upStyle()
        nextPage.upStyle()
    }

    /***
     * 更新背景
     */
    fun upBg() {
        Coroutines.mainScope().launch {
            ChapterProvider.tryCreatePreference(context)
            ChapterProvider.readerPreferencesUtil?.readerPreferencesFlow?.firstOrNull()?.let { preference ->
                val bgColor = preference.backgroundColor
                curPage.setBg(bgColor)
                prevPage.setBg(bgColor)
                nextPage.setBg(bgColor)
            }
        }

//        ReadBookConfig.bg ?: let {
//            ReadBookConfig.upBg()
//        }
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

}