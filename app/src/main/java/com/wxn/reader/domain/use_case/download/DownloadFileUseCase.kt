package com.wxn.reader.domain.use_case.download

import com.wxn.base.bean.DownloadFileType
import com.wxn.reader.util.download.FileDownloadManager
import javax.inject.Inject


class DownloadFileUseCase @Inject constructor(
    private val fileDownloadManager: FileDownloadManager
) {

    operator fun invoke(
        fileId: String,
        url: String,
        fileType: DownloadFileType = DownloadFileType.BG_IMAGE,
        fileName: String? = null,
        extraData: Any? = null
    ): String {
        return fileDownloadManager.enqueueDownload(fileId, url, fileType, fileName, extraData)
    }
}