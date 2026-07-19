package com.wxn.reader.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import com.wxn.base.util.Logger
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * 将 Compose [ImageBitmap] 转换为 [android.graphics.Bitmap]。
 */
fun ImageBitmap.toAndroidBitmap(): Bitmap = asAndroidBitmap()

/**
 * 书摘卡片分享工具（无状态，所有方法以 context 作参数）。
 *
 * 包含：
 * - 字体解析（复制自 ChapterProvider.kt:321-361，Android-specific，KMP 时改 expect/actual）
 * - FileProvider URI 生成（.tmp + rename 防残留）
 * - 图文混排 Intent 分享（try-catch 降级）
 * - MediaStore 保存到相册（含失败回滚）
 * - 缓存清理
 */
object ShareQuoteCardUtil {

    private const val CACHE_DIR = "shared_cards"
    private const val GALLERY_DIR = "HandyReader"

    // ==================== 字体解析（复制自 ChapterProvider，不重构核心阅读引擎） ====================

    /**
     * 解析字体路径为 [Typeface]（复制自 ChapterProvider.kt:321-361）。
     *
     * @param fontPath 系统字体名（"serif"/"sans_serif"/"monospace"）、content:// URI、或字体目录路径
     * @param fontVariant 变体名（"regular"/"bold"/"italic"/"bolditalic"）
     * @return 解析后的 Typeface，失败兜底 [Typeface.SANS_SERIF]
     */
    fun resolveTypeface(context: Context, fontPath: String, fontVariant: String): Typeface {
        if (fontPath.isBlank()) return Typeface.SANS_SERIF
        return try {
            val isSystemFont = fontPath in listOf("serif", "sans_serif", "monospace")
            when {
                fontPath == "serif" -> Typeface.SERIF
                fontPath == "sans_serif" -> Typeface.SANS_SERIF
                fontPath == "monospace" -> Typeface.MONOSPACE
                fontPath.isNotEmpty() && !isSystemFont -> {
                    val fontDir = File(fontPath)
                    if (fontDir.isDirectory) {
                        // 找匹配 variant 的 .ttf/.otf
                        val variantFile = fontDir.listFiles()?.firstOrNull {
                            it.nameWithoutExtension.equals(fontVariant, ignoreCase = true) &&
                                (it.extension.equals("ttf", ignoreCase = true) ||
                                    it.extension.equals("otf", ignoreCase = true))
                        }
                        if (variantFile != null && variantFile.exists()) {
                            Typeface.createFromFile(variantFile)
                        } else {
                            val anyFont = fontDir.listFiles()?.firstOrNull {
                                it.extension.equals("ttf", ignoreCase = true) ||
                                    it.extension.equals("otf", ignoreCase = true)
                            }
                            if (anyFont != null) Typeface.createFromFile(anyFont)
                            else Typeface.SANS_SERIF
                        }
                    } else if (fontDir.isFile) {
                        Typeface.createFromFile(fontDir)
                    } else {
                        Typeface.SANS_SERIF
                    }
                }
                else -> Typeface.SANS_SERIF
            }
        } catch (e: Exception) {
            Logger.w("ShareQuoteCardUtil::resolveTypeface failed: ${e.message}")
            Typeface.SANS_SERIF
        }
    }

    // ==================== Bitmap → 缓存文件 → FileProvider URI ====================

    /**
     * 将 Bitmap 保存到 cacheDir/shared_cards/ 并返回 FileProvider content:// URI。
     * 使用 .tmp + rename 防渲染取消时残留半成品。
     */
    suspend fun saveBitmapToCacheUri(context: Context, bitmap: Bitmap): Uri? {
        return withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val randomSuffix = (0..9999).random()
            val targetFile = File(cacheDir, "quote_${timestamp}_$randomSuffix.png")
            // 注意：不能用 File(cacheDir, "$targetFile.tmp")——$targetFile 会调用 toString() 得到绝对路径，
            // 被当作子路径拼接到 cacheDir 后面，导致路径重复（ENOENT）。
            val tmpFile = File(cacheDir, "${targetFile.name}.tmp")
            try {
                tmpFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                // rename 到目标名（原子操作）
                if (!tmpFile.renameTo(targetFile)) {
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }
                val authority = "${context.packageName}.fileprovider"
                FileProvider.getUriForFile(context, authority, targetFile)
            } catch (e: Throwable) {
                // catch Throwable：捕获 OutOfMemoryError（Bitmap.compress 在内存吃紧时抛 Error，非 Exception）
                Logger.w("ShareQuoteCardUtil::saveBitmapToCacheUri failed: ${e.message}")
                tmpFile.delete()
                targetFile.delete()
                null
            }
        }
    }

    // ==================== 图文混排分享 ====================

    /**
     * 构造分享 caption 文本（书名 + 作者 + 引文 + 水印）。
     */
    fun buildShareCaption(data: QuoteCardData, editableText: String): String {
        return buildString {
            append(data.bookTitle)
            data.bookAuthor?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
            append("\n\n").append(editableText)
            append("\n\n— HandyReader")
        }
    }

    /**
     * 通过系统 chooser 分享图片 + 文字（图文混排）。
     * 无可接收 App 时抛 [android.content.ActivityNotFoundException]，调用方应 try-catch 降级。
     */
    fun shareImage(context: Context, imageUri: Uri, caption: String, chooserTitle: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // ViewModel 传入的是 Application context，非 Activity context 启动 Activity 必须加
        // FLAG_ACTIVITY_NEW_TASK，否则抛 AndroidRuntimeException，导致 chooser 不弹出。
        val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Logger.w("ShareQuoteCardUtil::shareImage chooser failed: ${e.message}")
            throw e
        }
    }

    // ==================== 保存到相册（MediaStore） ====================

    /**
     * 保存 Bitmap 到系统相册 Pictures/HandyReader/。
     * API 29+ 用 MediaStore RELATIVE_PATH；API 23-28 需 WRITE_EXTERNAL_STORAGE 权限。
     * 写入失败自动回滚（删除 0 字节记录）。
     *
     * @return 成功返回 true
     */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
        return withContext(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val randomSuffix = (0..9999).random()
            val displayName = "quote_${timestamp}_$randomSuffix.png"
            val resolver = context.contentResolver

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToGalleryApi29(resolver, bitmap, displayName)
            } else {
                saveToGalleryLegacy(context, bitmap, displayName)
            }
        }
    }

    private fun saveToGalleryApi29(
        resolver: android.content.ContentResolver,
        bitmap: Bitmap,
        displayName: String
    ): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$GALLERY_DIR")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                true
            } ?: run {
                resolver.delete(uri, null, null) // 回滚
                false
            }
        } catch (e: Throwable) {
            // catch Throwable：compress 在内存吃紧时抛 OutOfMemoryError（非 Exception），需回滚避免 0 字节残留
            Logger.w("ShareQuoteCardUtil::saveToGalleryApi29 failed: ${e.message}")
            resolver.delete(uri, null, null) // 关键回滚，避免 0 字节记录
            false
        }
    }

    private fun saveToGalleryLegacy(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Boolean {
        return try {
            val picturesDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                GALLERY_DIR
            ).apply { mkdirs() }
            val file = File(picturesDir, displayName)
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            // 通知媒体扫描器刷新
            MediaScannerConnection.scanFile(
                context, arrayOf(file.absolutePath), arrayOf("image/png"), null
            )
            true
        } catch (e: Throwable) {
            // catch Throwable：compress 在内存吃紧时抛 OutOfMemoryError（非 Exception）
            Logger.w("ShareQuoteCardUtil::saveToGalleryLegacy failed: ${e.message}")
            false
        }
    }

    // ==================== 缓存清理 ====================

    /**
     * 清理 shared_cards/ 目录下超过 [olderThanMs] 毫秒的缓存文件。
     * 应在 App 启动时调用。
     */
    fun cleanupOldCards(context: Context, olderThanMs: Long = 24 * 3600 * 1000L) {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) return
        val cutoff = System.currentTimeMillis() - olderThanMs
        cacheDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    /**
     * 清理相册中 HandyReader 目录下的 0 字节文件（渲染中断残留）。
     * 应在 App 启动时调用。
     *
     * 版本分支：API 29+ 用 MediaStore 查询（RELATIVE_PATH 列在 API 29 新增）；
     * API < 29 走 File API 直扫 Pictures/HandyReader/ 目录（legacy 保存路径）。
     */
    fun cleanupZeroByteGalleryFiles(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cleanupZeroByteGalleryFilesApi29(context)
            } else {
                cleanupZeroByteGalleryFilesLegacy()
            }
        } catch (e: Throwable) {
            Logger.w("ShareQuoteCardUtil::cleanupZeroByteGalleryFiles failed: ${e.message}")
        }
    }

    /** API 29+：通过 MediaStore 查询 0 字节记录并删除 */
    private fun cleanupZeroByteGalleryFilesApi29(context: Context) {
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.SIZE)
        val selection = "${MediaStore.Images.Media.SIZE} = 0 AND " +
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE '%$GALLERY_DIR%'"
        resolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = android.content.ContentUris.withAppendedId(collection, id)
                resolver.delete(uri, null, null)
            }
        }
    }

    /** API < 29：直扫 Pictures/HandyReader/ 目录，删除 0 字节文件 */
    private fun cleanupZeroByteGalleryFilesLegacy() {
        val picturesDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            GALLERY_DIR
        )
        if (!picturesDir.exists()) return
        picturesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.length() == 0L) {
                file.delete()
            }
        }
    }
}
