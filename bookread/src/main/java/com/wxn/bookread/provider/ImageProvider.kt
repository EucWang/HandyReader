package com.wxn.bookread.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wxn.base.bean.Book
import java.io.File
import java.io.FileInputStream

object ImageProvider {

    /***
     * 从缓存目录中，加载图片的bitmap
     */
    fun getImage(context: Context, book:Book, imgSrc: String, onUi:Boolean = false): Bitmap? {
        return try {
            BitmapFactory.decodeStream(FileInputStream(File(imgSrc)))
        }catch (ex : Exception) {
            null
        }
    }
}