package com.wxn.reader.util.download

import com.wxn.base.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ZipExtractor @Inject constructor() {

    /**
     * 解压zip文件
     * @param zipPath zip文件路径
     * @param targetDirectory 目标目录
     */
    suspend fun extract(
        zipPath: String,
        targetDirectory: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {

        try {
            Logger.d("ZipExtractor: Starting extraction: $zipPath -> $targetDirectory")

            val zipFile = File(zipPath)
            val destDir = File(targetDirectory)

            if (!zipFile.exists()) {
                return@withContext Result.failure(Exception("Zip file not found: $zipPath"))
            }

            if (!destDir.exists()) {
                destDir.mkdirs()
            }

//            var totalEntries = 0
            var extractedEntries = 0

            // 第一遍：统计总条目数
//            ZipInputStream(FileInputStream(zipFile)).use { zis ->
//                var entry: ZipEntry? = zis.nextEntry
//                while (entry != null) {
//                    if (!entry.isDirectory) {
//                        totalEntries++
//                    }
//                    zis.closeEntry()
//                    entry = zis.nextEntry
//                }
//            }

            // 第二遍：解压
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val destFile = File(destDir, entry.name)

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        // 确保父目录存在
                        destFile.parentFile?.mkdirs()

                        FileOutputStream(destFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        extractedEntries++

//                        if (extractedEntries % 10 == 0) {
//                            Logger.d("ZipExtractor: Extracted $extractedEntries/$totalEntries")
//                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            Logger.d("ZipExtractor: Extraction complete: $extractedEntries files")
            Result.success(Unit)

        } catch (e: Exception) {
            Logger.e("ZipExtractor: Extraction failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 删除文件
     */
    fun deleteFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            Logger.e("ZipExtractor: Failed to delete file: $filePath, ${e.message}")
            false
        }
    }
}