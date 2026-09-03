package com.wxn.bookparser.domain.file


import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.MimeType
import com.anggrayudi.storage.file.getAbsolutePath
import com.wxn.base.util.Logger
import java.io.File
import java.io.InputStream
import java.util.UUID

private const val TAG = "CachedFile"

/**
 * Cached File.
 * Faster than [androidx.documentfile.provider.DocumentFile].
 * Saves all it's variables after initialized.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
@Immutable
class CachedFile(
    private val context: Context,
    val uri: Uri,
    private val builder: CachedFileBuilder? = null
) {
    @Immutable
    private data class QueryParams(
        val name: String,
        val size: Long,
        val lastModified: Long,
        val isDirectory: Boolean
    )

    private val queryParams by lazy {
        getFileQueryParams()
    }
    val path: String by lazy { builder?.path ?: getFilePath() }
    val rawFile: File? by lazy { storeInCache() }

    val name: String get() = builder?.name ?: queryParams.name
    val size: Long get() = builder?.size ?: queryParams.size
    val lastModified: Long get() = builder?.lastModified ?: queryParams.lastModified
    val isDirectory: Boolean get() = builder?.isDirectory ?: queryParams.isDirectory

    fun canAccess(): Boolean {
        return if (uri.scheme == "file") {
            val path = uri.path
            if (!path.isNullOrEmpty()) {
                val file = File(path)
                file.exists() && file.canRead()
            } else {
                false
            }
        } else {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.let {
                    it.close()
                    return true
                }
                throw Exception("Could not access URI: $uri")
            } catch (e: Exception) {
                false
            }
        }
    }

    val extension: String
        get() {
            return if (uri.scheme == "file") {
                uri.path?.substringAfterLast(".")?.lowercase()?.trim().orEmpty()
            } else {
                name.substringAfterLast(".").lowercase().trim()
            }
        }

    fun openInputStream(): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
                ?: throw Exception("Failed to open InputStream for URI: $uri")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listFiles(forEach: ((CachedFile) -> Unit)? = null): List<CachedFile> {
        if (!isDirectory || !canAccess()) return emptyList()

        val cachedFiles = mutableListOf<CachedFile>()

        val nameColumn = DocumentsContract.Document.COLUMN_DISPLAY_NAME
        val uriColumn = DocumentsContract.Document.COLUMN_DOCUMENT_ID
        val sizeColumn = DocumentsContract.Document.COLUMN_SIZE
        val lastModifiedColumn = DocumentsContract.Document.COLUMN_LAST_MODIFIED
        val isDirectoryColumn = DocumentsContract.Document.COLUMN_MIME_TYPE

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getDocumentId(uri)
        )

        // 三级降级阶梯（同 getFileQueryParams，方案 2026-09-03-plan-import-metadata-query-fallback.md §2.2）：
        // L1 全列 → L2 SAFE 最小列（display_name + document_id）→ 仍失败返回已收集结果，遍历降级不中断。
        // L2 缺 size/lastModified/mime_type → 子项对应字段传 null（见下），由子项自身惰性兜底，
        // 不得传 0/猜值——builder 非 null 字段会短路子项查询，把占位值永久固化。
        queryWithFallback(
            uri = childrenUri,
            projection = listOf(
                nameColumn,
                uriColumn,
                sizeColumn,
                lastModifiedColumn,
                isDirectoryColumn
            ),
            minimalProjection = listOf(nameColumn, uriColumn)
        )?.use { cursor ->
            if (cursor.count == 0) {
                return emptyList()
            }

            try {
                val nameIndex = cursor.getColumnIndex(nameColumn)
                val uriIndex = cursor.getColumnIndex(uriColumn)
                val sizeIndex = cursor.getColumnIndex(sizeColumn)
                val lastModifiedIndex = cursor.getColumnIndex(lastModifiedColumn)
                val isDirectoryIndex = cursor.getColumnIndex(isDirectoryColumn)

                if (uriIndex < 0) return@use cachedFiles

                while (cursor.moveToNext()) {
                    val nameQuery = if (nameIndex >= 0) cursor.getString(nameIndex) else ""
                    val pathQuery = if (nameQuery.isNotEmpty()) "$path/$nameQuery" else path
                    val uriQuery = DocumentsContract.buildDocumentUriUsingTree(
                        uri,
                        cursor.getString(uriIndex)
                    )
                    val sizeQuery: Long? = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else null
                    val lastModifiedQuery: Long? =
                        if (lastModifiedIndex >= 0) cursor.getLong(lastModifiedIndex) else null
                    val isDirectoryQuery: Boolean? = if (isDirectoryIndex >= 0) {
                        cursor.getString(isDirectoryIndex) == DocumentsContract.Document.MIME_TYPE_DIR
                    } else null

                    val queryFile = CachedFileCompat.fromUri(
                        context = context,
                        uri = uriQuery,
                        builder = CachedFileCompat.build(
                            name = nameQuery,
                            path = pathQuery,
                            size = sizeQuery,
                            lastModified = lastModifiedQuery,
                            isDirectory = isDirectoryQuery
                        )
                    )

                    forEach?.invoke(queryFile)
                    cachedFiles.add(queryFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return cachedFiles
    }

    fun walk(
        includeDirectories: Boolean = false,
        forEach: ((CachedFile) -> Unit)? = null
    ): List<CachedFile> {
        val cachedFiles = mutableListOf<CachedFile>()

        listFiles {
            when (it.isDirectory) {
                false -> {
                    forEach?.invoke(it)
                    cachedFiles.add(it)
                }

                true -> {
                    if (includeDirectories) cachedFiles.add(it)
                    cachedFiles.addAll(it.walk(includeDirectories, forEach))
                }
            }
        }

        return cachedFiles
    }

    /**
     * Copies the file to [BookCacheManager] cache directory and provides [java.io.File].
     *
     * @return null if the file is directory or failed
     */
    private fun storeInCache(): File? {
        if (isDirectory) return null
        val manager = BookCacheManager.getInstance() ?: return null
        val fileName = generateCacheFileName()
        return manager.writeCacheFile(fileName) {
            context.contentResolver.openInputStream(uri)
        }
    }

    private fun generateCacheFileName(): String {
        var ext = MimeType.getExtensionFromFileName(path).orEmpty()
        if (ext.isNotEmpty()) ext = ".$ext"
        val lastDotIndex = path.lastIndexOf(".")
        val lastSlashIndex = path.lastIndexOf("/")
        val pureName = if (lastDotIndex > 0 && lastDotIndex > lastSlashIndex) {
            path.substring(lastSlashIndex + 1, lastDotIndex)
        } else {
            path
        }
        val uriHash = Integer.toHexString(uri.toString().hashCode())
        return "${pureName.replace("_", "-").replace("/", "_").takeLast(55)}_${path.length}_${uriHash}_${size}${ext}"
    }

    private fun getFileQueryParams(): QueryParams {
        val nameColumn = DocumentsContract.Document.COLUMN_DISPLAY_NAME
        val sizeColumn = DocumentsContract.Document.COLUMN_SIZE
        val lastModifiedColumn = DocumentsContract.Document.COLUMN_LAST_MODIFIED
        val isDirectoryColumn = DocumentsContract.Document.COLUMN_MIME_TYPE

        val projection = mutableListOf<String>().apply {
            if (builder?.name == null) add(nameColumn)
            if (builder?.size == null) add(sizeColumn)
            if (builder?.lastModified == null) add(lastModifiedColumn)
            if (builder?.isDirectory == null) add(isDirectoryColumn)
        }

        if (projection.isEmpty() && builder != null) {
            return QueryParams(
                name = builder.name!!,
                size = builder.size!!,
                lastModified = builder.lastModified!!,
                isDirectory = builder.isDirectory!!
            )
        }

        // L2 SAFE 最小投影 = needed ∩ {display_name, document_id}；document_id 对字段解析无贡献，
        // 故交集实际只可能含 display_name。交集为空（如导入路径 builder 已含 name+isDirectory，
        // 仅缺 size/lastModified，而这两列不在 SAFE 集合）时跳过 L2，直接走字段级兜底，
        // 省一次注定无收益的跨进程查询（审查 F-R1-3）。
        val minimalProjection = listOfNotNull(
            nameColumn.takeIf { builder?.name == null }
        )

        queryWithFallback(uri, projection, minimalProjection)?.use { cursor ->
            try {
                if (cursor.moveToFirst()) {
                    var nameQuery: String? = if (builder?.name == null) {
                        val index = cursor.getColumnIndex(nameColumn)
                        if (index >= 0) cursor.getString(index) else null
                    } else null

                    var sizeQuery: Long? = if (builder?.size == null) {
                        val index = cursor.getColumnIndex(sizeColumn)
                        if (index >= 0) cursor.getLong(index) else null
                    } else null

                    var lastModifiedQuery: Long? = if (builder?.lastModified == null) {
                        val index = cursor.getColumnIndex(lastModifiedColumn)
                        if (index >= 0) cursor.getLong(index) else null
                    } else null

                    var isDirectoryQuery: String? = if (builder?.isDirectory == null) {
                        val index = cursor.getColumnIndex(isDirectoryColumn)
                        if (index >= 0) cursor.getString(index) else null
                    } else null

                    return QueryParams(
                        name = nameQuery ?: builder?.name ?: uri.lastPathSegment ?: "unknown_${UUID.randomUUID()}",
                        size = sizeQuery ?: builder?.size ?: statSizeOrZero(),
                        lastModified = lastModifiedQuery ?: builder?.lastModified ?: 0L,
                        isDirectory = when (isDirectoryQuery) {
                            DocumentsContract.Document.MIME_TYPE_DIR -> true
                            null -> builder?.isDirectory ?: false
                            else -> false
                        }
                    )
                }
            } catch (e: Exception) {
                Logger.w("$TAG: cursor resolution failed, uri=$uri, ${e.message}")
            }
        }

        return QueryParams(
            name = builder?.name ?: uri.lastPathSegment ?: "unknown_${UUID.randomUUID()}",
            size = builder?.size ?: statSizeOrZero(),
            lastModified = builder?.lastModified ?: 0L,
            isDirectory = builder?.isDirectory ?: false
        )
    }

    /**
     * 元数据查询三级降级阶梯（方案 2026-09-03-plan-import-metadata-query-fallback.md §2.1）：
     * L1 按需投影正常查询（健康设备到此为止，行为与历史一致）→ L1 抛异常（catch Exception，
     * ROM/第三方 provider 的 IAE/NPE/IllegalState/SecurityException 等全量兜住，门禁批复①）→
     * L2 SAFE 最小投影重试（空交集跳过）→ L2 失败返回 null，由调用方做字段级兜底。
     */
    private fun queryWithFallback(uri: Uri, projection: List<String>, minimalProjection: List<String>): Cursor? {
        try {
            return context.contentResolver.query(uri, projection.toTypedArray(), null, null, null)
        } catch (e: Exception) {
            Logger.w("$TAG: metadata query L1 failed, degrade, uri=$uri, projection=$projection, ${e.message}")
        }
        if (minimalProjection.isEmpty()) {
            Logger.w("$TAG: metadata query L1 failed and L2 skipped (empty safe intersection), uri=$uri")
            return null
        }
        return try {
            context.contentResolver.query(uri, minimalProjection.toTypedArray(), null, null, null)
        } catch (e: Exception) {
            Logger.w("$TAG: metadata query L2 failed, uri=$uri, projection=$minimalProjection, ${e.message}")
            null
        }
    }

    /**
     * 降级时的文件大小兜底：content URI 走 openFileDescriptor.statSize（openFile 通道，
     * 不经投影校验，与内容读取同通道，statSize 真实性是 TXT 章节偏移正确性的前提）；
     * file:// URI 无 provider 派发，openFileDescriptor 恒空，直接取 File.length()
     * （审查 F-R1-2，修复 file:// 书 size 恒 0 的存量隐患）。两通道均失败返回 0，
     * 等价历史默认值。不得读 rawFile/size，避免与 queryParams 懒加载互相递归。
     */
    private fun statSizeOrZero(): Long = try {
        when (uri.scheme) {
            "file" -> uri.path?.let { path -> File(path).takeIf { it.exists() }?.length() } ?: 0L
            else -> context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        }
    } catch (e: Exception) {
        Logger.w("$TAG: statSize fallback failed, uri=$uri, ${e.message}")
        0L
    }

    private fun getFilePath(): String {
        val tempFile = DocumentFileCompat.fromUri(context, uri)
        return tempFile?.getAbsolutePath(context)?.trimEnd('/') ?: ""
    }
}