package com.wxn.reader.util.download

/****
 * 下载状态
 */
data class DownloadState(
    val id: String,
    val progress: Float = 0f,
    val isPendingDownload: Boolean = false,
    val isDownloading: Boolean = false,
    val isCompleted: Boolean = false,
    val isExtracting: Boolean = false,
    val error: String? = null
)