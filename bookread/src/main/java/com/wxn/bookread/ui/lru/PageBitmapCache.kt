package com.wxn.bookread.ui.lru

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.wxn.base.ext.screenshot
import com.wxn.base.util.Logger
import com.wxn.bookread.data.beans.CacheStats
import com.wxn.bookread.data.beans.PageKey
import com.wxn.bookread.ui.ContentView

class PageBitmapCache(
    private val context: Context
) {
    // 使用LruCache自动管理内存
    private val memoryCache = object : LruCache<String, Bitmap>(getCacheSize()) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (evicted) {
                oldValue.recycle() // 自动回收被移除的bitmap
            }
        }
    }

    // 缓存命中统计
    private var hitCount = 0
    private var missCount = 0

    /**
     * 获取缓存的bitmap
     */
    fun getBitmap(pageKey: PageKey): Bitmap? {
        val cacheKey = pageKey.toCacheKey()
        return memoryCache.get(cacheKey)?.also {
            hitCount++
            Logger.d("PageBitmapCache: Cache HIT for $cacheKey")
        } ?: run {
            missCount++
            Logger.d("PageBitmapCache: Cache MISS for $cacheKey")
            null
        }
    }

    /**
     * 缓存bitmap
     */
    fun putBitmap(pageKey: PageKey, bitmap: Bitmap) {
        val cacheKey = pageKey.toCacheKey()
        memoryCache.put(cacheKey, bitmap)
        Logger.d("PageBitmapCache: Cached $cacheKey")
    }

    /**
     * 预热缓存 - 提前创建可能需要的bitmap
     */
    fun preloadBitmap(pageKey: PageKey, contentView: ContentView): Bitmap? {
        return getBitmap(pageKey) ?: run {
            val bitmap = contentView.screenshot()
            if (bitmap != null) {
                putBitmap(pageKey, bitmap)
            }
            bitmap
        }
    }

    /**
     * 清空缓存
     */
    fun clearCache() {
        memoryCache.evictAll()
        hitCount = 0
        missCount = 0
    }

    /**
     * 获取缓存统计
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            hitCount = hitCount,
            missCount = missCount,
            hitRate = if (hitCount + missCount > 0) {
                hitCount.toFloat() / (hitCount + missCount)
            } else 0f,
            memoryUsage = memoryCache.size(),
            maxSize = memoryCache.maxSize()
        )
    }

    private fun getCacheSize(): Int {
        // 使用应用可用内存的1/8作为缓存大小
        val memInfo = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memInfo)
        return (memInfo.availMem / 8).toInt()
    }

    private fun PageKey.toCacheKey(): String {
        return "${chapterIndex}_${pageIndex}_${contentHash}_${viewWidth}x${viewHeight}"
    }
}