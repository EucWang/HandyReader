package com.wxn.bookread.ext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap

object BitmapExt {

    fun bitmapFromResource(context: Context, @DrawableRes resId: Int) : Bitmap? {
        var ret: Bitmap? = null
        val drawable = AppCompatResources.getDrawable(context, resId)
        ret = if (drawable is BitmapDrawable) {
            drawable.bitmap
        } else {
            val width = drawable?.intrinsicWidth ?: 0
            val height = drawable?.intrinsicHeight ?: 0
            if (drawable != null && width > 0 && height > 0) {
                try {
                    val bitmap = createBitmap(width, height)
                    val canvas = Canvas(bitmap)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                } catch(ex : IllegalArgumentException) {
                    null
                }
            } else {
                null
            }
        }
        return ret
    }
}