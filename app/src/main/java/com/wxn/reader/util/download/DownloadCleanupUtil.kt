package com.wxn.reader.util.download


import android.content.Context
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import java.io.File
import java.util.concurrent.TimeUnit


object DownloadCleanupUtil {

    const val MAX_TEMP_FILE_AGE_DAYS = 7L

    /***
     * 清理几个下载目录中的临时文件, 防止下载失败,导致大量的临时文件占用空间
     */
    fun cleanupOldTempFiles(context: Context) {
        val bgDir = File(context.filesDir, PathUtil.PATH_READ_BG)
        cleanupInDirectory(bgDir)

        // 其他目录（TTS模型、字体等）
        val ttsDir = File(context.filesDir, PathUtil.PATH_TTS_MODELS)
        cleanupInDirectory(ttsDir)

        val fontDir = File(context.filesDir, PathUtil.PATH_FONTS)
        cleanupInDirectory(fontDir)
    }

    /***
     * 清理某个目录下超过7天的临时文件
     */
    private fun cleanupInDirectory(dir: File) {
        if (!dir.exists()) return

        val cutoffTime = System.currentTimeMillis() -
                TimeUnit.DAYS.toMillis(MAX_TEMP_FILE_AGE_DAYS)

        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp")&& file.lastModified() < cutoffTime) {
                try {
                    file.delete()
                    Logger.d("DownloadCleanupUtil: cleaned old temp file: ${file.absolutePath}")

                    //检测临时文件对应的meta文件, 如果存在则删除
                    val filename = file.name.substringBeforeLast(".tmp")
                    val metaFile = File(file.parentFile, filename + ".meta")
                    if (metaFile.exists() && metaFile.canExecute()) {
                        metaFile.delete()
                    }

                } catch (e: Exception) {
                    Logger.w("DownloadCleanupUtil: failed to delete ${file.absolutePath}: ${e.message}")
                }
            }
        }
    }

}