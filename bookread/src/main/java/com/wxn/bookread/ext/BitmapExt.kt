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

    /** 加载封面 Bitmap，兼容本地文件路径与 content:// URI；降采样避免大图 OOM（X3） */
    fun loadCoverBitmap(context: Context, path: String): android.graphics.Bitmap? {
        return try {
            // 先只读尺寸
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(path))?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                }
            } else if (java.io.File(path).exists()) {
                android.graphics.BitmapFactory.decodeFile(path, bounds)
            } else return null

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // 降采样到目标尺寸（封面区域最长边 ~1080px 即可）
            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1080, 1440)
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sampleSize }

            if (path.startsWith("content://")) {
                context.contentResolver.openInputStream(android.net.Uri.parse(path))?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, opts)
                }
            } else {
                android.graphics.BitmapFactory.decodeFile(path, opts)
            }
        } catch (e: Exception) {
            com.wxn.base.util.Logger.w("loadCoverBitmap failed for $path: ${e.message}")
            null
        }
    }

    /** 计算 inSampleSize，使降采样后尺寸 ≥ reqW/reqH 且尽量小 */
    private fun calculateInSampleSize(outW: Int, outH: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        while (outW / (sample * 2) >= reqW && outH / (sample * 2) >= reqH) {
            sample *= 2
        }
        return sample
    }
}