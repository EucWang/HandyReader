package com.wxn.reader.presentation.bookReader.components.modals.readbglist



/****
 * 下载状态
 */
data class DownloadState(
    val id: String,
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null
)