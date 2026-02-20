package com.wxn.bookread.ui.delegate

import android.graphics.Bitmap
import android.view.MotionEvent
import com.wxn.base.ext.screenshot
import com.wxn.base.util.Logger
import com.wxn.bookread.data.beans.PageKey
import com.wxn.bookread.ui.ContentView
import com.wxn.bookread.ui.PageView
import com.wxn.bookread.ui.lru.PageBitmapCache
import kotlin.math.sqrt

abstract class VerticalPageDelegate(pageView: PageView) : PageDelegate(pageView) {

    private val bitmapCache = PageBitmapCache(pageView.context)
    //page cache keys
    private var currentPrevKey: PageKey? = null
    private var currentCurKey: PageKey? = null
    private var currentNextKey: PageKey? = null


    protected var curBitmap: Bitmap? = null
    protected var prevBitmap: Bitmap? = null
    protected var nextBitmap: Bitmap? = null

    override fun setDirection(direction: Direction) {
        super.setDirection(direction)
        setBitmapWithCache()
    }

    private fun setBitmapWithCache() {
        when(mDirection) {
            Direction.PREV -> {
                currentPrevKey = createPageKey(prevPage)
                currentCurKey = createPageKey(curPage)

                prevBitmap = getBitmapFromCache(currentPrevKey, prevPage)
                curBitmap = getBitmapFromCache(currentCurKey, curPage)
            }
            Direction.NEXT -> {
                currentNextKey = createPageKey(nextPage)
                currentCurKey = createPageKey(curPage)

                nextBitmap = getBitmapFromCache(currentNextKey, nextPage)
                curBitmap = getBitmapFromCache(currentCurKey, curPage)
            }
            else -> Unit
        }
    }
    private fun getBitmapFromCache(pageKey: PageKey?, contentView: ContentView): Bitmap? {
        pageKey ?: return null
        return bitmapCache.getBitmap(pageKey) ?: run {
            val bitmap = contentView.screenshot()
            if (bitmap != null) {
                bitmapCache.putBitmap(pageKey, bitmap)
            }
            bitmap
        }
    }

    /**
     * 创建页面缓存键
     */
    private fun createPageKey(contentView: ContentView): PageKey? {
        val textPage = pageView.dataProvider?.pageFactory?.currentPage ?: return null
        return PageKey(
            chapterIndex = textPage.chapterIndex,
            pageIndex = textPage.index,
            contentHash = textPage.text.hashCode(),
            viewWidth = viewWidth,
            viewHeight = viewHeight
        )
    }

//    private fun setBitmap() {
//        when (mDirection) {
//            Direction.PREV -> {
//                prevBitmap?.recycle()
//                prevBitmap = prevPage.screenshot()
//                curBitmap?.recycle()
//                curBitmap = curPage.screenshot()
//            }
//            Direction.NEXT -> {
//                nextBitmap?.recycle()
//                nextBitmap = nextPage.screenshot()
//                curBitmap?.recycle()
//                curBitmap = curPage.screenshot()
//            }
//            else -> Unit
//        }
//    }

    override fun onTouch(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Logger.d("${this.javaClass.name}::onTouch():ACTION_DOWN:isStarted($isStarted),isMoved($isMoved),isRunning($isRunning),isDeprecatedAction($isDeprecatedAction)")
                val curTimestamp = System.currentTimeMillis()
                if (curTimestamp - lastActionDown <= pageView.slopTapDuration) {
                    isDeprecatedAction = true
//                    abortAnim()
                }
                if (isRunning || isMoved || isRunning) {
                    isDeprecatedAction = true
                }
                lastActionDown = curTimestamp
                if (!isDeprecatedAction) {
                    onDown()
                }
            }
            MotionEvent.ACTION_MOVE -> {
                Logger.d("${this.javaClass.name}::onTouch():ACTION_MOVE:isStarted($isStarted),isMoved($isMoved),isRunning($isRunning),isDeprecatedAction($isDeprecatedAction)")
                if (!isDeprecatedAction) {
                    onScroll(event)
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                Logger.d("${this.javaClass.name}::onTouch():ACTION_UP:isStarted($isStarted),isMoved($isMoved),isRunning($isRunning),isDeprecatedAction($isDeprecatedAction)")
                if (!isDeprecatedAction) {
                    onAnimStart(pageView.defaultAnimationSpeed)
                }
                isDeprecatedAction = false
            }
        }
    }

    private fun onScroll(event: MotionEvent) {
        Logger.d("${this.javaClass.name}::onScroll()")
        val action: Int = event.action
        val pointerUp =
            action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_POINTER_UP
        val skipIndex = if (pointerUp) event.actionIndex else -1
        // Determine focal point
        var sumX = 0f
        var sumY = 0f
        val count: Int = event.pointerCount
        for (i in 0 until count) {
            if (skipIndex == i) continue
            sumX += event.getX(i)
            sumY += event.getY(i)
        }
        val div = if (pointerUp) count - 1 else count
        val focusX = sumX / div
        val focusY = sumY / div
        //判断是否移动了
        if (!isMoved) {
            val deltaX = (focusX - startX)
            val deltaY = (focusY - startY)
            val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
            isMoved = distance >= pageView.slopSquare
            if (isMoved) {
                if (sumY - startY > 0) {
                    //如果上一页不存在
                    if (!hasPrev()) {
                        noNext = true
                        return
                    }
                    setDirection(Direction.PREV)
                } else {
                    //如果不存在表示没有下一页了
                    if (!hasNext()) {
                        noNext = true
                        return
                    }
                    setDirection(Direction.NEXT)
                }
            }
        }
        if (isMoved) {
            isCancel = if (mDirection == Direction.NEXT) sumY > lastY else sumY < lastY
            isRunning = true
            //设置触摸点
            pageView.setTouchPoint(sumX, sumY)
        }
    }


    override fun abortAnim() {
        Logger.d("${this.javaClass.name}::abortAnim()")
        isStarted = false
        isMoved = false
        isRunning = false
        if (!scroller.isFinished) {
            pageView.isAbortAnim = true
            scroller.abortAnimation()
            if (!isCancel) {
                pageView.fillPage(mDirection)
                pageView.invalidate()
            }
        } else {
            pageView.isAbortAnim = false
        }
    }

    override fun nextPageByAnim(animationSpeed: Int) {
        Logger.d("${this.javaClass.name}::nextPageByAnim():isRunning($isRunning),isMoved($isMoved),isStarted($isStarted)")
        if (isRunning || isMoved || isStarted) {
            Logger.d("HorizontalPageDelegate::nextPageByAnim():passed")
            return
        }
        abortAnim()
        if (!hasNext()) return
        setDirection(Direction.NEXT)
        pageView.setTouchPoint(0f, viewHeight.toFloat(), false)
        onAnimStart(animationSpeed)
    }

    override fun prevPageByAnim(animationSpeed: Int) {
        Logger.d("${this.javaClass.name}::prevPageByAnim():isRunning($isRunning),isMoved($isMoved),isStarted($isStarted)")
        if (isRunning || isMoved || isStarted) {
            Logger.d("${this.javaClass.name}::prevPageByAnim():passed")
            return
        }
        abortAnim()
        if (!hasPrev()) return
        setDirection(Direction.PREV)
        pageView.setTouchPoint(0f, 0f)
        onAnimStart(animationSpeed)
    }

    override fun onDestroy() {
        super.onDestroy()
        prevBitmap = null
        curBitmap = null
        nextBitmap = null
        bitmapCache.clearCache()
        Logger.d("VerticalPageDelegate::onDestroy()")
    }
}