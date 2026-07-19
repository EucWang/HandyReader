package com.wxn.reader.util.download

import com.wxn.base.bean.DownloadFileType

data class ExtractionResult(
    val fileId: String,
    val url: String,
    val zipPath: String,
    val targetDir: String,
    val fileType: DownloadFileType,
    val success: Boolean,
    val error: String? = null,
    val extraData: Any? = null
)