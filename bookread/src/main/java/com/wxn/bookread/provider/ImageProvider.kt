package com.wxn.bookread.provider

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import androidx.annotation.VisibleForTesting
import com.wxn.base.util.Logger
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

object ImageProvider {

    /** OpenGL ES 3.0 主流纹理上限兜底（Android 12 强制 GLES 3.0） */
    private const val FALLBACK_MAX_BITMAP_DIMENSION = 4096

    /**
     * 预加载防雪崩阈值：当 [memoryCache] 已用容量超过该比例时，跳过本张预解码，
     * 交由主线程 [getImage] 兜底。用于避免图片密集章节一次性预解码导致的瞬时 OOM 峰值。
     */
    @VisibleForTesting
    internal val preloadHighWatermarkRatio = 0.7f

    private val memoryCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).toInt()
    ) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.allocationByteCount
    }

    /**
     * 图片原始宽高缓存（imgSrc -> [originW, originH]）。
     * 用于让 [getImage] / [preload] 计算 sampleSize 时避免重复读取 JPEG 头（文件 I/O）。
     * 由 [computeSampleSize] 统一写入。
     */
    private val boundsCache = ConcurrentHashMap<String, IntArray>()

    /** 设备 Canvas 单张 bitmap 安全上限（缓存，避免每帧 new Canvas()）。@Volatile 保证可见性，幂等无需加锁。 */
    @Volatile
    private var cachedMaxBitmapDimension: Int = 0

    /**
     * 后台预解码图片并写入缓存，使后续主线程 [getImage] 调用成为零 I/O 的缓存命中。
     *
     * **调用契约**：应在后台线程（分页/IO）调用，不应在主线程调用。
     * 幂等：同一 [imgSrc]+[targetWidth]+[targetHeight] 多次调用不会重复解码（先查缓存判重）。
     * 防雪崩：当 [memoryCache] 已用容量超过 [preloadHighWatermarkRatio] 时跳过本张，交由 [getImage] 兜底。
     *
     * @param imgSrc 图片本地完整路径
     * @param targetWidth 目标显示宽度（px），0 表示未知，按设备上限缩放
     * @param targetHeight 目标显示高度（px），0 表示未知，按设备上限缩放
     */
    fun preload(imgSrc: String, targetWidth: Int = 0, targetHeight: Int = 0) {
        val sampleSize = computeSampleSize(imgSrc, targetWidth, targetHeight) ?: return
        val cacheKey = key(imgSrc, sampleSize)
        // 1. 幂等：已缓存则直接返回（LruCache 线程安全）
        if (memoryCache.get(cacheKey) != null) return
        // 2. 防雪崩：内存吃紧时跳过，交由 getImage 兜底，避免密集章节瞬时 OOM 峰值
        if (memoryCache.size() > memoryCache.maxSize() * preloadHighWatermarkRatio) {
            Logger.d("ImageProvider::preload skipped (cache high watermark) imgSrc=$imgSrc")
            return
        }
        // 3. 解码并缓存
        decode(imgSrc, sampleSize)?.let { bmp ->
            // put 前再判一次，防止并发预加载同一 key 重复解码
            if (memoryCache.get(cacheKey) == null) {
                memoryCache.put(cacheKey, bmp)
            }
        }
    }

    /**
     * 获取书籍内嵌图片。
     *
     * **调用契约**：主线程同步调用（在 onDraw 中调用）。
     *
     * **快路径（零 I/O）**：若 [preload] 已在后台预解码，则 [boundsCache] 命中 +
     * [memoryCache] 命中，直接返回缓存 Bitmap，无任何文件 I/O。
     *
     * **兜底路径（同步解码）**：若缓存未命中（preload 未覆盖/被跳过/边角场景），
     * 则沿用原有同步解码逻辑，保证行为不回退。
     *
     * @param imgSrc 图片本地完整路径
     * @param targetWidth 目标显示宽度（px），0 表示未知，按设备上限缩放
     * @param targetHeight 目标显示高度（px），0 表示未知，按设备上限缩放
     */
    fun getImage(
        imgSrc: String,
        targetWidth: Int = 0,
        targetHeight: Int = 0
    ): Bitmap? {
        val sampleSize = computeSampleSize(imgSrc, targetWidth, targetHeight) ?: return null
        val cacheKey = key(imgSrc, sampleSize)
        // 快路径：内存缓存命中即返回（零文件 I/O）
        memoryCache.get(cacheKey)?.let { return it }

        // 兜底路径：同步解码（仅在 preload 未覆盖时触发，保证零回归）
        return decode(imgSrc, sampleSize)?.also { bmp ->
            memoryCache.put(cacheKey, bmp)
        }
    }

    /** 计算 sampleSize（power-of-2）。同时填充 [boundsCache]，避免后续重复读 JPEG 头。 */
    private fun computeSampleSize(
        imgSrc: String,
        targetWidth: Int,
        targetHeight: Int
    ): Int? {
        val bounds = boundsCache.getOrPut(imgSrc) {
            // 读原图尺寸（不加载像素）
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imgSrc, boundsOptions)
            intArrayOf(boundsOptions.outWidth, boundsOptions.outHeight)
        }
        val originW = bounds[0]
        val originH = bounds[1]
        if (originW <= 0 || originH <= 0) {
            Logger.e("ImageProvider::decode bounds failed imgSrc=$imgSrc")
            return null
        }
        val maxDim = getDeviceMaxBitmapDimension()
        return calculateInSampleSize(originW, originH, targetWidth, targetHeight, maxDim)
    }

    /** 按采样尺寸解码（不查缓存，仅负责解码）。失败返回 null。 */
    private fun decode(imgSrc: String, sampleSize: Int): Bitmap? {
        return try {
            FileInputStream(File(imgSrc)).use { fis ->
                val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeStream(fis, null, opts)
            }
        } catch (ex: Exception) {
            Logger.e(
                "ImageProvider::decode failed imgSrc=$imgSrc sampleSize=$sampleSize",
                ex
            )
            null
        }
    }

    private fun key(imgSrc: String, sampleSize: Int): String = "$imgSrc|$sampleSize"

    /**
     * 计算降采样率。
     *
     * 策略：
     * - 取 2x 目标尺寸作为采样门槛（保证 Retina 清晰度，与 Glide/Picasso 默认一致）
     * - 同时强制最长边不超过 [maxDim]（设备 Canvas 上限）
     * - targetW/targetH 无效时，按原图宽高比缩放到 maxDim
     *
     * @param maxDim 设备允许的最大 bitmap 维度（最长边像素）。公开此参数是为了单元测试可重复。
     */
    @VisibleForTesting
    internal fun calculateInSampleSize(
        originW: Int, originH: Int,
        targetW: Int, targetH: Int,
        maxDim: Int
    ): Int {
        val effectiveTargetW: Int
        val effectiveTargetH: Int
        if (targetW <= 0 || targetH <= 0) {
            // 无目标尺寸：按原图宽高比缩放到 maxDim
            val ratio = originW.toFloat() / originH.toFloat()
            if (originW >= originH) {
                effectiveTargetW = maxDim
                effectiveTargetH = (maxDim / ratio).toInt().coerceAtLeast(1)
            } else {
                effectiveTargetH = maxDim
                effectiveTargetW = (maxDim * ratio).toInt().coerceAtLeast(1)
            }
        } else {
            effectiveTargetW = targetW
            effectiveTargetH = targetH
        }

        // 2x 目标尺寸 → 保留 Retina 清晰度
        var sampleSize = 1
        val halfW = effectiveTargetW * 2
        val halfH = effectiveTargetH * 2
        while ((originW / sampleSize) > halfW && (originH / sampleSize) > halfH) {
            sampleSize *= 2
        }
        // 同时强制最长边不超过设备 Canvas 上限
        while (maxOf(originW, originH) / sampleSize > maxDim) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    /**
     * 获取设备 Canvas 单张 bitmap 安全上限（最长边像素）。
     * API 21+ 可直接查询 Canvas.maximumBitmapWidth/Height。
     * 结果缓存到 [cachedMaxBitmapDimension]，避免每帧 new Canvas()。
     */
    private fun getDeviceMaxBitmapDimension(): Int {
        cachedMaxBitmapDimension.takeIf { it > 0 }?.let { return it }
        val ret = try {
            val canvas = Canvas()
            val reported =  maxOf(canvas.maximumBitmapWidth, canvas.maximumBitmapHeight)
//            val result = if (maxDim > 0) maxDim else FALLBACK_MAX_BITMAP_DIMENSION
            if (reported > 0) min(reported, FALLBACK_MAX_BITMAP_DIMENSION) else FALLBACK_MAX_BITMAP_DIMENSION
        } catch (e: Throwable) {
            FALLBACK_MAX_BITMAP_DIMENSION
        }
        cachedMaxBitmapDimension = ret
        return ret
    }

    fun clearCache() {
        memoryCache.evictAll()
        boundsCache.clear()
    }
}
