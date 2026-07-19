package com.wxn.reader.util.download

import com.wxn.base.bean.DownloadFileType
import java.io.File

data class ExtractionRequest(
    val fileId: String,           // 文件唯一标识
    val url: String,
    val zipPath: String,          // ZIP文件路径
    val fileType: DownloadFileType, // 文件类型（TTS_MODEL/TTS_DEPENDENCY）
    val targetDir: String = File(zipPath).parent, // 解压目标目录
    val extraData: Any? = null
)