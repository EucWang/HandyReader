package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wxn.reader.domain.model.DownloadStatus


@Entity(
    tableName = "download_history",
    indices = [Index("downloadedAt")]
)
data class DownloadHistoryEntity(
    @PrimaryKey
    val fileId: String,

    val url: String,
    val fileType: String,           // FileType.name
    val fileName: String?,

    val localPath: String,
    val fileSize: Long,

    val status: DownloadStatus,
    val errorMessage: String? = null,

    val startedAt: Long,
    val completedAt: Long? = null,
    val downloadedAt: Long          // 用于排序的时间戳
)
