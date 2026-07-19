package com.wxn.bookparser.domain.file


import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.Immutable
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.file.MimeType
import com.anggrayudi.storage.file.getAbsolutePath
import java.io.File
import java.io.InputStream
import java.util.UUID

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

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                nameColumn,
                uriColumn,
                sizeColumn,
                lastModifiedColumn,
                isDirectoryColumn
            ),
            null, null, null
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
                    val sizeQuery = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
                    val lastModifiedQuery = if (lastModifiedIndex >= 0) cursor.getLong(lastModifiedIndex) else 0L
                    val isDirectoryQuery = if (isDirectoryIndex >= 0) {
                        cursor.getString(isDirectoryIndex) == DocumentsContract.Document.MIME_TYPE_DIR
                    } else false

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

        context.contentResolver.query(
            uri,
            projection.toTypedArray(),
            null, null, null
        )?.use { cursor ->
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
                        size = sizeQuery ?: builder?.size ?: 0L,
                        lastModified = lastModifiedQuery ?: builder?.lastModified ?: 0L,
                        isDirectory = when (isDirectoryQuery) {
                            DocumentsContract.Document.MIME_TYPE_DIR -> true
                            null -> builder?.isDirectory ?: false
                            else -> false
                        }
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return QueryParams(
            name = "unknown_${UUID.randomUUID()}",
            size = 0,
            lastModified = 0,
            isDirectory = false
        )
    }

    private fun getFilePath(): String {
        val tempFile = DocumentFileCompat.fromUri(context, uri)
        return tempFile?.getAbsolutePath(context)?.trimEnd('/') ?: ""
    }
}