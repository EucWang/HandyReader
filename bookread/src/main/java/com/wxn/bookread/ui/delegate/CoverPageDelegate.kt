package com.wxn.bookread.ui.delegate

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import com.wxn.base.util.Logger
import com.wxn.bookread.ui.PageView

class CoverPageDelegate(pageView: PageView) : HorizontalPageDelegate(pageView) {
    private val bitmapMatrix = Matrix()
    private val shadowDrawableR: GradientDrawable

    private val shadowDrawableL: GradientDrawable

    init {
        val shadowColors = intArrayOf(0x66111111, 0x00000000)
        shadowDrawableR = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT, shadowColors
        )
        shadowDrawableR.gradientType = GradientDrawable.LINEAR_GRADIENT

        shadowDrawableL = GradientDrawable(
            GradientDrawable.Orientation.RIGHT_LEFT, shadowColors
        )
        shadowDrawableL.gradientType = GradientDrawable.LINEAR_GRADIENT
    }

    override fun onDraw(canvas: Canvas) {
        if (!isRunning) return
        if (mDirection == Direction.NONE) return
        val offsetX = touchX - startX

        // invertPageTurn 下 NEXT/PREV 的正向滑动方向互换，故判定条件也随之镜像
        val invert = pageView.invertPageTurn
        val effectiveNext = (mDirection == Direction.NEXT) xor invert
        if ((effectiveNext && offsetX > 0) || (!effectiveNext && offsetX < 0)) {
            return
        }

        val distanceX = if (offsetX > 0) offsetX - viewWidth else offsetX + viewWidth

        // stationary(底层固定)： NEXT=nextBitmap, PREV=curBitmap
        // sliding (滑动页)： NEXT=curBitmap, PREV=prevBitmap
        val stationary = if (mDirection == Direction.NEXT) nextBitmap else curBitmap
        val sliding = if (mDirection == Direction.NEXT) curBitmap else prevBitmap

        stationary?.let {
            if (!it.isRecycled) {
                canvas.drawBitmap(it, 0f, 0f, null)
            }
        }
        val slidingOffset = if (mDirection == Direction.NEXT) {
            distanceX + if (invert) viewWidth else -viewWidth
        } else {
            distanceX
        }

        bitmapMatrix.setTranslate(slidingOffset, 0.toFloat())
        sliding?.let {
            if (!it.isRecycled) {
                canvas.drawBitmap(it,  bitmapMatrix, null)
            }
        }
        addShadow(distanceX.toInt(), canvas, invert)
    }

    private fun addShadow(distanceX: Int, canvas: Canvas, invert: Boolean) {
        if (distanceX == 0) return
        val edge = if (distanceX < 0) {
            distanceX + viewWidth
        } else {
            distanceX
        }

        if (invert) {
            shadowDrawableL.setBounds(edge - 30, 0, edge, viewHeight)
            shadowDrawableL.draw(canvas)
        } else {
            shadowDrawableR.setBounds(edge, 0, edge + 30, viewHeight)
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
        Logger.d("${this.javaClass.name}::onAnimStart():then startScroll")
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
                        -(touchX - startX)
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

}
