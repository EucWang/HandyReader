package com.wxn.reader.util

import android.content.Context
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.util.PathUtil
import com.wxn.reader.domain.model.TTSModelData
import java.io.File

fun TTSModelData.engineModelDir(context: Context): String {
    val modelDirName = this.type + "-" + locale + "-" + name
    var targetDir = ""
    var engineModelDir =
        File(PathUtil.getDownloadDir(context, DownloadFileType.TTS_MODEL), modelDirName)
    if (!engineModelDir.exists()) {
        val parent = PathUtil.getDownloadDir(context, DownloadFileType.TTS_MODEL)
        val fileList = parent.listFiles()
        if (!fileList.isNullOrEmpty()) {
            for (file in fileList) {
                if (file.name.contains(name)
                    && file.isDirectory
                    && file.name.startsWith(type)
                ) {
                    targetDir = file.absolutePath
                    break
                }
            }
        }
    } else {
        targetDir = engineModelDir.absolutePath
    }
    return targetDir
}