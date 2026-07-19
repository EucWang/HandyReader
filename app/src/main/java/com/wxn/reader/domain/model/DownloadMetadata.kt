package com.wxn.reader.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException


@Serializable
data class DownloadMetadata(
    // 标识信息
    val fileId: String,
    val url: String,
    val targetPath: String,
    val tempPath: String,

    // 进度信息
    val totalBytes: Long? = null,
    val downloadedBytes: Long = 0,
    val supportsRange: Boolean = false,

    // 验证信息
    val etag: String? = null,
    val lastModified: String? = null,

    // 时间信息
    val createdAt: Long,
    val lastUpdated: Long
) {
    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromFile(file: File): DownloadMetadata? {
            return try {
                json.decodeFromString(file.readText())
            } catch (e: Exception) {
                null  // 仅在读取时验证，失败返回null
            }
        }

        fun saveToFile(metadata: DownloadMetadata, file: File) {
            val tempFile = File(file.parent, "${file.name}.tmp")
            tempFile.writeText(json.encodeToString(metadata))
            if (!tempFile.renameTo(file)) {
                throw IOException("Failed to save metadata atomically")
            }
        }


    }

    fun isValid(): Boolean {
        return fileId.isNotBlank() && url.isNotBlank() && targetPath.isNotBlank()
    }




}