package com.wxn.reader.domain.use_case.font

import android.content.Context
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.reader.domain.repository.FontRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class DeleteFontUseCase @Inject constructor(
    private val fontRepository: FontRepository,
    @ApplicationContext private val context: Context
) {

    suspend operator fun invoke(fontId: String): Result<Unit> {
        return try {
            val fontEntity = fontRepository.getFontById(fontId) ?: return Result.success(Unit)
            val fontDir = fontEntity.localDir

            if (fontDir != null) {
                val dir = File(fontDir)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }

            fontRepository.deleteFont(fontId)

            val fontBaseDir = File(PathUtil.getDownloadDir(context, DownloadFileType.FONT), fontId)
            if (fontBaseDir.exists()) {
                fontBaseDir.deleteRecursively()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("DeleteFontUseCase::invoke error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun isCurrentFont(fontId: String, currentFontPath: String): Boolean {
        if (currentFontPath.isEmpty()) return false
        val fontEntity = fontRepository.getFontById(fontId) ?: return false
        return fontEntity.localDir == currentFontPath
    }
}
