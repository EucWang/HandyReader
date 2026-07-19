package com.wxn.reader.util.download

import java.io.File

interface IDownloader {

    suspend fun downloadToFile(
        url: String,
        targetFile: File,
        headers: Map<String, String>? = null,
        onProgress: (Float) -> Unit = {}
    ): String

    fun cancelDownload(targetFile: File)
}