package com.wxn.reader.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 文件可访问性校验工具。
 *
 * 用于在打开书籍前检查文件是否存在/可读。
 * 不持有 Context 引用，作为方法参数传入，避免单例内存泄漏。
 */
object FileAccessValidator {

    enum class Result { ACCESSIBLE, FILE_NOT_FOUND, URI_INVALID }

    private const val SOURCE_SYNC_ORPHAN = "sync_orphan"

    /**
     * 检查书籍文件是否可访问。
     *
     * @param context Application context（用于 ContentResolver）
     * @param uri 书籍文件路径（可能是 content:// / file:// / 裸路径）
     * @param source 书籍来源（scan / import / opds / external / external_import / sync_orphan）
     * @return 校验结果
     */
    fun check(context: Context, uri: String?, source: String): Result {
        if (source == SOURCE_SYNC_ORPHAN) return Result.FILE_NOT_FOUND
        if (uri.isNullOrBlank()) return Result.URI_INVALID

        val parsed = Uri.parse(uri)
        return when (parsed.scheme) {
            "content" -> checkContentUri(context, parsed)
            "file", null -> checkFilePath(parsed.path ?: uri)
            else -> Result.ACCESSIBLE
        }
    }

    private fun checkContentUri(context: Context, uri: Uri): Result = try {
        context.contentResolver.openInputStream(uri)?.close()
        Result.ACCESSIBLE
    } catch (e: Exception) {
        Result.FILE_NOT_FOUND
    }

    private fun checkFilePath(path: String): Result {
        val file = File(path)
        return if (file.exists() && file.canRead()) Result.ACCESSIBLE
        else Result.FILE_NOT_FOUND
    }
}
