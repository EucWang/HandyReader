package com.wxn.reader.util

import android.content.Context
import android.net.Uri
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil.PATH_IMPORTED_BOOKS
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class UriResolutionResult(
    val uri: String,
    val source: String,
    val permissionPersisted: Boolean = false,
)

class ExternalFileResolver @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun resolve(uri: Uri): UriResolutionResult {
        val persisted = tryPersistPermission(uri)
        if (persisted) {
            Logger.d("ExternalFileResolver: Persisted permission for $uri")
        } else {
            Logger.d("ExternalFileResolver: No persistable permission for $uri, will import to storage")
        }
        return UriResolutionResult(
            uri = uri.toString(),
            source = "external",
            permissionPersisted = persisted,
        )
    }

    private fun tryPersistPermission(uri: Uri): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            true
        } catch (e: Exception) {
            Logger.d("ExternalFileResolver: Persist permission failed: ${e.message}")
            false
        }
    }

    fun importToStorage(uri: Uri, destFileName: String): String? {
        return try {
            val dir = File(context.filesDir, PATH_IMPORTED_BOOKS)
            if (!dir.exists() && !dir.mkdirs()) return null
            val destFile = File(dir, destFileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(destFile)).use { output ->
                    input.copyTo(output, 64 * 1024)
                }
            } ?: return null
            "file://${destFile.absolutePath}"
        } catch (e: Exception) {
            Logger.d("ExternalFileResolver: importToStorage failed: ${e.message}")
            null
        }
    }

    fun extractFileName(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }
}
