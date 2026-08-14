package com.wxn.bookread.provider

import android.graphics.BitmapFactory
import com.wxn.base.util.Logger

object ImageLayoutProvider {

    var imgScale = 1.0f

    /****
     * 约束图片的宽高,
     * 和系统的displayMetrics.density 系数得到一个合适的缩放大小.
     * 然后约束到最大宽高范围之内, 即界面可视宽高之内,
     * 如果不超过,则显示默认宽高,
     * @param imgWidth : 图片的宽度
     * @param imgHeight : 图片的高度
     * @param imgSrc : 图片的路径
     * @param maxWidth: 最大图片的宽度
     * @param maxHeight : 最大图片的高度
     * @param useScale : 是否应用系统的density系数, 如果传入的是图片原始的宽高,则需要; 如果传入的是重新计算过的宽高,则不需要
     * @return 约束在最大宽高之内, 重新计算之后的图片的宽高
     */
    internal fun constraintImageSize(
        imgWidth: Int,
        imgHeight: Int,
        imgSrc: String,
        maxWidth: Int,
        maxHeight: Int,
        useScale: Boolean = true
    ): Pair<Int, Int> {
        var originWidth = imgWidth
        var originHeight = imgHeight
        if (originWidth <= 0 || originHeight <= 0) {
            val options: BitmapFactory.Options = BitmapFactory.Options()
            options.inJustDecodeBounds = true // 不加载图片像素，只获取宽高
            options.inSampleSize = 2
            BitmapFactory.decodeFile(imgSrc, options)
            originWidth = options.outWidth
            originHeight = options.outHeight
            if (originWidth <= 0 || originHeight <= 0) {
                Logger.e("ChapterProvider::constraintImageSize::decode image[$imgSrc][$imgWidth,$imgHeight] size failed")
                return Pair(0, 0)
            }
        }
        val scale = if (useScale) imgScale else 1.0f
        originWidth = (imgWidth * scale).toInt()  //图片的实际宽高
        originHeight = (imgHeight * scale).toInt()
        if (originWidth <= 0 || originHeight <= 0) {
            return Pair(0, 0)
        }
        val radio: Float = (originWidth.toFloat() / originHeight)  //宽高比

        //约束到最大范围之内的宽高,不要超过一个屏幕
        if (originWidth > maxWidth) {
            originWidth = maxWidth
            originHeight = (originWidth / radio).toInt()
        }
        if (originHeight > maxHeight) {
            originHeight = maxHeight
            originWidth = (originHeight * radio).toInt()
        }

        return Pair(originWidth, originHeight)
    }


    /****
     * 将图片的宽高缩放到最大宽高匹配的大小,让其填满宽度或者填满高度
     */
    internal fun fillImageSize(
        imgWidth: Int,
        imgHeight: Int,
        imgSrc: String,
        maxWidth: Int,
        maxHeight: Int,
    ): Pair<Int, Int> {
        var originWidth = imgWidth
        var originHeight = imgHeight
        if (originWidth <= 0 || originHeight <= 0) {
            val options: BitmapFactory.Options = BitmapFactory.Options()
            options.inJustDecodeBounds = true // 不加载图片像素，只获取宽高
            options.inSampleSize = 2
            BitmapFactory.decodeFile(imgSrc, options)
            originWidth = options.outWidth
            originHeight = options.outHeight
            if (originWidth <= 0 || originHeight <= 0) {
                Logger.e("ChapterProvider::constraintImageSize::decode image[$imgSrc][$imgWidth,$imgHeight] size failed")
                return Pair(0, 0)
            }
        }
        val radio: Float = (originWidth.toFloat() / originHeight)  //宽高比

        var targetWidth = maxWidth
        var targetHeight = (targetWidth / radio).toInt()
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight
            targetWidth = (targetHeight * radio).toInt()
        }
        return Pair(targetWidth, targetHeight)
    }
}