package com.wxn.bookread.ui.delegate

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import com.wxn.base.util.Logger
import com.wxn.bookread.ui.PageView

class CoverVerticalPageDelegate(pageView: PageView) : VerticalPageDelegate(pageView) {

    private val bitmapMatrix = Matrix()
    private val shadowDrawableR: GradientDrawable

    init {
        val shadowColors = intArrayOf(0x66111111, 0x00000000)
        shadowDrawableR = GradientDrawable(
//            GradientDrawable.Orientation.LEFT_RIGHT, shadowColors
            GradientDrawable.Orientation.TOP_BOTTOM, shadowColors
        )
        shadowDrawableR.gradientType = GradientDrawable.LINEAR_GRADIENT
    }

    override fun onDraw(canvas: Canvas) {
        if (!isRunning) return
        val offsetY = touchY - startY

        if ((mDirection == Direction.NEXT && offsetY > 0)
            || (mDirection == Direction.PREV && offsetY < 0)
        ) {
            return
        }

        val distanceY = if (offsetY > 0) offsetY - viewHeight else offsetY + viewHeight
        if (mDirection == Direction.PREV) {
            bitmapMatrix.setTranslate(0.toFloat(), distanceY)
            curBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            prevBitmap?.let { canvas.drawBitmap(it, bitmapMatrix, null) }

            addShadow(distanceY.toInt(), canvas)
        } else if (mDirection == Direction.NEXT) {
            bitmapMatrix.setTranslate(0.toFloat(), distanceY - viewHeight)
            nextBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
            curBitmap?.let { canvas.drawBitmap(it, bitmapMatrix, null) }
            addShadow(distanceY.toInt(), canvas)
        }
    }


    private fun addShadow(top: Int, canvas: Canvas) {
        if (top < 0) {
            shadowDrawableR.setBounds(0, top + viewHeight, viewWidth, top + viewHeight + 30)
            shadowDrawableR.draw(canvas)
        } else if (top > 0) {
            shadowDrawableR.setBounds(0, top, viewWidth, top + 30)
            shadowDrawableR.draw(canvas)
        }
    }

    override fun onAnimStop() {
        Logger.d("${this.javaClass.name}::onAnimStop() then fillPage,isCancel[$isCancel],mDirection[$mDirection]")
        if (!isCancel) {
            pageView.fillPage(mDirection)
        }
    }

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

}