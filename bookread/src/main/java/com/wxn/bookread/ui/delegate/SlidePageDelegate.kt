package com.wxn.bookread.ui.delegate

import android.graphics.Canvas
import android.graphics.Matrix
import com.wxn.base.util.Logger
import com.wxn.bookread.ui.PageView

class SlidePageDelegate(pageView: PageView) : HorizontalPageDelegate(pageView) {

    private val bitmapMatrix = Matrix()

    override fun onAnimStart(animationSpeed: Int) {
        Logger.d("${this.javaClass.name}::onAnimStart()")
        val distanceX: Float
        if (pageView.invertPageTurn) {
            when (mDirection) {
                Direction.NEXT -> {
                    distanceX = if (isCancel) {
                        -(touchX - startX)
                    } else {
                        viewWidth - (touchX - startX)
                    }
                }
                else -> {
                    distanceX = if (isCancel) {
                        -(touchX -startX)
                    } else {
                        -(touchX + (viewWidth - startX))
                    }
                }
            }
        } else {
            when (mDirection) {
                Direction.NEXT -> distanceX =
                    if (isCancel) {
                        var dis = viewWidth - startX + touchX
                        if (dis > viewWidth) {
                            dis = viewWidth.toFloat()
                        }
                        viewWidth - dis
                    } else {
                        -(touchX + (viewWidth - startX))
                    }
                else -> distanceX =
                    if (isCancel) {
                        -(touchX - startX)
                    } else {
                        viewWidth - (touchX - startX)
                    }
            }
        }
        startScroll(touchX.toInt(), 0, distanceX.toInt(), 0, animationSpeed)
    }

    override fun onDraw(canvas: Canvas) {
        if (mDirection == Direction.NONE) return
        val offsetX = touchX - startX

        // invertPageTurn 下 NEXT 的正向由左滑变右滑，故 effectiveNext 与 offsetX 的判定互换
        val invert = pageView.invertPageTurn
        val effectiveNext = (mDirection == Direction.NEXT) xor invert
        if ((effectiveNext && offsetX > 0) || (!effectiveNext && offsetX < 0)) {
            return
        }

        val distanceX = if (offsetX > 0) offsetX - viewWidth else offsetX + viewWidth
        if (!isRunning) return

        val incoming = if (mDirection == Direction.NEXT) nextBitmap else prevBitmap
        val drawPrev = (mDirection == Direction.PREV) xor invert

        if (drawPrev) {
            bitmapMatrix.setTranslate(distanceX + viewWidth, 0.toFloat())
            curBitmap?.let {
                if (!it.isRecycled) canvas.drawBitmap(it, bitmapMatrix, null)
            }
            bitmapMatrix.setTranslate(distanceX, 0.toFloat())
            incoming?.let {
                if (!it.isRecycled) canvas.drawBitmap(it, bitmapMatrix, null)
            }
        } else {
            bitmapMatrix.setTranslate(distanceX, 0.toFloat())
            incoming?.let {
                if (!it.isRecycled) canvas.drawBitmap(it, bitmapMatrix, null)
            }
            bitmapMatrix.setTranslate(distanceX - viewWidth, 0.toFloat())
            curBitmap?.let {
                if (!it.isRecycled) canvas.drawBitmap(it, bitmapMatrix, null)
            }
        }
    }

    override fun onAnimStop() {
        Logger.d("${this.javaClass.name}::onAnimStop() then fillPage,isCancel[$isCancel],mDirection[$mDirection]")
        if (!isCancel) {
            pageView.fillPage(mDirection)
        }
    }
}