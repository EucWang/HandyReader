package com.wxn.reader.util.download

import com.wxn.base.bean.DownloadFileType


data class DownloadRequest(
    val fileId: String,
    val url: String,
    val fileType: DownloadFileType,
    val fileName: String? = null,
    val extraData: Any? = null,
    val authHeader: String? = null,
)