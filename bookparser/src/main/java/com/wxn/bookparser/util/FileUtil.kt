package com.wxn.bookparser.util

import java.io.*;

object FileUtil {

    /**
     * 将 InputStream 内容写入目标文件
     * @param inputStream 输入流（调用方需确保可读且非空）
     * @param destFilePath 目标文件的绝对路径（如 "/data/data/包名/files/output.txt"）
     * @throws IOException 当发生IO错误时抛出（如磁盘空间不足、权限问题等）
     */
    fun writeStreamToFile(inputStream: InputStream, destFilePath: String) {
        val destFile = File(destFilePath)

        // 自动创建父目录（如果不存在）
        destFile.parentFile?.takeIf { !it.exists() }?.mkdirs()

        // Kotlin的use扩展函数自动关闭资源
        BufferedOutputStream(FileOutputStream(destFile)).use { output ->
            inputStream.use { input ->
                input.copyTo(output) // Kotlin内置的复制扩展函数
            }
        }
    }
}