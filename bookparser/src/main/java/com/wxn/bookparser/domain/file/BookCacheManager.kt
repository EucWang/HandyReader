package com.wxn.bookparser.domain.file

import android.content.Context
import com.wxn.base.util.Logger
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong

class BookCacheManager private constructor(val cacheDir: File) {

    companion object {
        const val MAX_CACHE_SIZE = 300L * 1024 * 1024
        const val EVICTION_RATIO = 0.8
        const val BOOK_CACHE_DIR = "book_cache"
        private const val IO_BUFFER_SIZE = 64 * 1024
        private const val MIN_FREE_SPACE = 10L * 1024 * 1024
        private val BOOK_EXTENSIONS = setOf(
            "epub", "mobi", "azw", "azw3", "fb2", "txt", "pdf",
            "md", "html", "htm", "mp3", "m4a", "m4b", "aac"
        )

        @Volatile
        private var instance: BookCacheManager? = null

        fun init(context: Context): Boolean {
            if (instance != null) return true
            val dir = File(context.cacheDir, BOOK_CACHE_DIR)
            if (!dir.exists() && !dir.mkdirs()) return false
            val manager = BookCacheManager(dir)
            manager.cleanTempFiles()
            manager.calibrateTotalSize()
            instance = manager
            return true
        }
        fun getInstance(): BookCacheManager? = instance

        fun isLegacyCacheFile(file: File): Boolean {
            if (file.isDirectory) return false
            if (file.name.endsWith(".tmp")) return false
            val ext = file.name.substringAfterLast(".", "")
            return ext.lowercase() in BOOK_EXTENSIONS
        }
    }

    private val totalSize = AtomicLong(0)

    fun getCacheFile(fileName: String): File? {
        val file = File(cacheDir, fileName)
        if (file.exists() && file.length() > 0) {
            // v2 修复（review §O3）：不再调用 setLastModified(now)。
            // 原实现每次命中都重置 mtime，破坏了文件的真实写入时间语义——上层
            // TxtTextParser.scanWithMemo 用 cachedFile.lastModified 做 memo key，
            // mtime 频繁变化会让 memo 永远失效（每次都重扫）。
            // evictIfNeeded 仍按 lastModified 排序（旧文件的写入时间是真实 LRU 顺序，合理）。
            return file
        }
        return null
    }

    fun writeCacheFile(fileName: String, openStream: () -> InputStream?): File? {
        synchronized(this) {
            getCacheFile(fileName)?.let { return it }
        }
        val sourceSize = parseSizeFromFileName(fileName)
        if (sourceSize != null && sourceSize > 0) {
            if (cacheDir.usableSpace < sourceSize + MIN_FREE_SPACE) {
                return null
            }
        }
        val tempFile = File(cacheDir, "${fileName}_${Thread.currentThread().id}.writing")
        try {
            openStream()?.use { input ->
                BufferedOutputStream(FileOutputStream(tempFile), IO_BUFFER_SIZE).use { output ->
                    input.copyTo(output, IO_BUFFER_SIZE)
                }
            } ?: return null
        } catch (e: Exception) {
            tempFile.delete()
            return null
        }
        synchronized(this) {
            getCacheFile(fileName)?.let {
                tempFile.delete()
                return it
            }
            val targetFile = File(cacheDir, fileName)
            if (!tempFile.renameTo(targetFile)) {
                tempFile.delete()
                return null
            }
            totalSize.addAndGet(parseSizeFromFileName(fileName) ?: targetFile.length())
            evictIfNeeded()
            return targetFile
        }
    }

    fun cleanLegacyCache(rootCacheDir: File) {
        rootCacheDir.listFiles()?.filter { isLegacyCacheFile(it) }?.forEach { file ->
            try {
                file.delete()
            } catch (e: Exception) {
                Logger.w("BookCacheManager: failed to delete legacy cache: ${file.name}")
            }
        }
    }

    fun hasAvailableSpace(minBytes: Long = MIN_FREE_SPACE): Boolean {
        return cacheDir.usableSpace >= minBytes
    }

    private fun evictIfNeeded() {
        val current = totalSize.get()
        if (current <= MAX_CACHE_SIZE) return
        val targetSize = (MAX_CACHE_SIZE * EVICTION_RATIO).toLong()
        val files = cacheDir.listFiles()
            ?.filter { isCacheDataFile(it) }
            ?.sortedBy { it.lastModified() }
            ?: return
        var freed = 0L
        for (file in files) {
            if (current - freed <= targetSize) break
            val size = parseSizeFromFileName(file.name) ?: file.length()
            if (file.delete()) {
                freed += size
            }
        }
        if (freed > 0) {
            totalSize.addAndGet(-freed)
        }
        if (totalSize.get() < 0) {
            calibrateTotalSize()
        }
    }

    fun cleanTempFiles() {
        cacheDir.listFiles()?.filter {
            it.name.endsWith(".tmp") || it.name.endsWith(".writing")
        }?.forEach { it.delete() }
    }

    private fun isCacheDataFile(file: File): Boolean {
        val name = file.name
        return !name.endsWith(".tmp") && !name.endsWith(".writing")
    }

    private fun calibrateTotalSize() {
        val files = cacheDir.listFiles()?.filter { isCacheDataFile(it) } ?: return
        val size = files.sumOf { parseSizeFromFileName(it.name) ?: it.length() }
        totalSize.set(size)
    }

    private fun parseSizeFromFileName(name: String): Long? {
        val lastUnderscore = name.lastIndexOf('_')
        if (lastUnderscore < 0) return null
        val dotIndex = name.lastIndexOf('.')
        val sizeStr = if (dotIndex > lastUnderscore) {
            name.substring(lastUnderscore + 1, dotIndex)
        } else {
            name.substring(lastUnderscore + 1)
        }
        return sizeStr.toLongOrNull()
    }
}
