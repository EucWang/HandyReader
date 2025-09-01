package com.wxn.bookread.ui.delegate

import android.graphics.Canvas
import android.graphics.Matrix
import com.wxn.base.util.Logger
import com.wxn.bookread.ui.PageView

class SlideVerticalPageDelegate(pageView: PageView) : VerticalPageDelegate(pageView) {
//
//      Scroll
//    // 滑动追踪的时间
//    private val velocityDuration = 1000
//    //速度追踪器
//    private val mVelocity: VelocityTracker = VelocityTracker.obtain()
//
//    override fun onAnimStart(animationSpeed: Int) {
//        //惯性滚动
//        fling(
//            0, touchY.toInt(), 0, mVelocity.yVelocity.toInt(),
//            0, 0, -10 * viewHeight, 10 * viewHeight
//        )
//    }
//
//    override fun onAnimStop() {
//        // nothing
//    }
//
//    override fun onTouch(event: MotionEvent) {
//        when (event.action) {
//            MotionEvent.ACTION_DOWN -> {
//                abortAnim()
//                mVelocity.clear()
//            }
//            MotionEvent.ACTION_MOVE -> {
//                onScroll(event)
//            }
//            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
//                onAnimStart(pageView.defaultAnimationSpeed)
//            }
//        }
//    }
//
//    override fun onScroll() {
//        curPage.onScroll(touchY - lastY)
//    }
//
//    override fun onDraw(canvas: Canvas) {
//        // nothing
//    }
//
//    private fun onScroll(event: MotionEvent) {
//        mVelocity.addMovement(event)
//        mVelocity.computeCurrentVelocity(velocityDuration)
//        val action: Int = event.action
//        val pointerUp =
//            action and MotionEvent.ACTION_MASK == MotionEvent.ACTION_POINTER_UP
//        val skipIndex = if (pointerUp) event.actionIndex else -1
//        // Determine focal point
//        var sumX = 0f
//        var sumY = 0f
//        val count: Int = event.pointerCount
//        for (i in 0 until count) {
//            if (skipIndex == i) continue
//            sumX += event.getX(i)
//            sumY += event.getY(i)
//        }
//        val div = if (pointerUp) count - 1 else count
//        val focusX = sumX / div
//        val focusY = sumY / div
//        pageView.setTouchPoint(sumX, sumY)
//        if (!isMoved) {
//            val deltaX = (focusX - startX)
//            val deltaY = (focusY - startY)
//            val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
//            isMoved = distance > pageView.slopSquare
//        }
//        if (isMoved) {
//            isRunning = true
//        }
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        mVelocity.recycle()
//    }
//
//    override fun abortAnim() {
//        isStarted = false
//        isMoved = false
//        isRunning = false
//        if (!scroller.isFinished) {
//            pageView.isAbortAnim = true
//            scroller.abortAnimation()
//        } else {
//            pageView.isAbortAnim = false
//        }
//    }
//
//    override fun nextPageByAnim(animationSpeed: Int) {
//        if (pageView.isAbortAnim) {
//            return
//        }
//        pageView.setStartPoint(0f, 0f, false)
//        startScroll(0, 0, 0, -ChapterProvider.visibleHeight, animationSpeed)
//    }
//
//    override fun prevPageByAnim(animationSpeed: Int) {
//        if (pageView.isAbortAnim) {
//            return
//        }
//        pageView.setStartPoint(0f, 0f, false)
//        startScroll(0, 0, 0, ChapterProvider.visibleHeight, animationSpeed)
//    }

    private val bitmapMatrix = Matrix()

    override fun onAnimStart(animationSpeed: Int) {
        Logger.d("${this.javaClass.name}::onAnimStart()")
        val distanceY: Float
        when (mDirection) {
            Direction.NEXT -> distanceY =
                if (isCancel) {
                    var dis = viewHeight - startY + touchY
                    if (dis > viewHeight) {
                        dis = viewHeight.toFloat()
                    }
                    viewHeight - dis
                } else {
                    -(touchY + (viewHeight - startY))
                }
            else -> distanceY =
                if (isCancel) {
                    -(touchY - startY)
                } else {
                    viewHeight - (touchY - startY)
                }
        }
        startScroll(0, touchY.toInt(), 0, distanceY.toInt(), animationSpeed)
    }

    override fun onAnimStop() {
        Logger.d("${this.javaClass.name}::onAnimStop() then fillPage,isCancel[$isCancel],mDirection[$mDirection]")
        if (!isCancel) {
            pageView.fillPage(mDirection)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val offsetY = touchY - startY

        if ((mDirection == Direction.NEXT && offsetY > 0) ||
            (mDirection == Direction.PREV && offsetY < 0)) {
            return
        }

        val distanceY = if (offsetY > 0) offsetY - viewHeight else offsetY + viewHeight
        if (!isRunning) return

        if (mDirection == Direction.PREV) {
            bitmapMatrix.setTranslate(0.toFloat(), distanceY + viewHeight)
            curBitmap?.let { canvas.drawBitmap(it, bitmapMatrix, null) }

            bitmapMatrix.setTranslate(0.toFloat(), distanceY)
            prevBitmap?.let { canvas.drawBitmap(it, bitmapMatrix, null) }
        } else if (mDirection == Direction.NEXT) {

            bitmapMatrix.setTranslate(0.toFloat(), distanceY)
            nextBitmap?.let { canvas.drawBitmap(it, bitmapMatrix, null) }

            bitmapMatrix.setTranslate(0.toFloat(), distanceY - viewHeight)
            curBitmap?.let { canvas.drawBitmap(it, bitmapMatrix, null) }
        }
    }
}