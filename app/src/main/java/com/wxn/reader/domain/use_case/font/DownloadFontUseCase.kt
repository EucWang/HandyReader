package com.wxn.reader.domain.use_case.font

import android.content.Context
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.reader.domain.repository.FontRepository
import com.wxn.reader.util.download.FileDownloadManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class DownloadFontUseCase @Inject constructor(
    private val fontRepository: FontRepository,
    private val fileDownloadManager: FileDownloadManager,
    @ApplicationContext private val context: Context
) {

    suspend operator fun invoke(fontId: String): Result<Unit> {
        return try {
            val catalog = fontRepository.getCatalog()
            val catalogItem = catalog.find { it.id == fontId }
                ?: return Result.failure(IllegalArgumentException("Font not found: $fontId"))

            val existing = fontRepository.getFontById(fontId)
            if (existing != null && existing.downloadedAt != null) {
                return Result.success(Unit)
            }

            val fontDir = File(PathUtil.getDownloadDir(context, DownloadFileType.FONT), fontId)
            if (!fontDir.exists() && !fontDir.mkdirs()) {
                return Result.failure(IllegalStateException("Failed to create directory: ${fontDir.absolutePath}"))
            }

            for (variantItem in catalogItem.variants) {
                val fileId = "${fontId}_${variantItem.variant}"
                fileDownloadManager.enqueueDownload(
                    fileId = fileId,
                    url = variantItem.url,
                    fileType = DownloadFileType.FONT,
                    fileName = "${fontId}/${variantItem.localFileName}",
                    extraData = fontId
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("DownloadFontUseCase::invoke error: ${e.message}")
            Result.failure(e)
        }
    }
}
