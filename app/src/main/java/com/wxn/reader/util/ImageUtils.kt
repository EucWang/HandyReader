package com.wxn.reader.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

private const val HOME_BACKGROUND_PREFIX = "home_bg_"
private const val COVER_PREFIX = "cover_"

private const val READING_BG_PREFIX = "reading_bg_"

object ImageUtils {

    fun saveHomeBackgroundImage(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val imageBytes = stream.readBytes()
                val imageHash = imageBytes.md5Hash()
                val fileName = "$HOME_BACKGROUND_PREFIX$imageHash.jpg"

                context.filesDir.findFile(fileName)?.absolutePath
                    ?: createImageFile(context, fileName, imageBytes)
            }
        }.getOrNull()
    }

    fun listSavedBookCovers(context: Context): List<File> {
        // ★ 封面同步 D5:扫 covers/ 子目录(新规)+ filesDir 根(旧文件兼容,D7 懒迁移)
        val result = mutableListOf<File>()
        val coversDir = File(context.filesDir, "covers")
        if (coversDir.exists()) {
            result += coversDir.listFiles { file ->
                file.isImageFile() && !file.name.startsWith(HOME_BACKGROUND_PREFIX)
            }.orEmpty().toList()
        }
        result += context.filesDir.listFiles { file ->
            file.isFile && file.isImageFile() &&
                file.name.startsWith(COVER_PREFIX) &&
                !file.name.startsWith(HOME_BACKGROUND_PREFIX)
        }.orEmpty().toList()
        return result
    }

    fun saveCoverImage(bitmap: Bitmap, uri: String, context: Context): String? {
        return runCatching {
            val uriHash = uri.md5Hash()
            val imageBytes = bitmap.toByteArray()
            val imageHash = imageBytes.md5Hash()
            val fileName = "$COVER_PREFIX${uriHash}_$imageHash.jpg"
            // ★ 封面同步 D5:归一到 filesDir/covers/ 子目录(与内置封面统一),
            //   备份只需扫一个目录;旧根目录文件由 D7 懒迁移,不强制搬移。
            val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
            val file = File(coversDir, fileName)

            // 旧文件清理:在 covers/ 子目录扫描同 uriHash 前缀
            coversDir.listFiles { _, name ->
                name.startsWith("$COVER_PREFIX$uriHash") && name != fileName
            }?.forEach { it.delete() }

            file.writeBytes(imageBytes)
            file.absolutePath
        }.getOrNull()
    }

    fun saveReadingBgImage(bitmap: Bitmap, uri: String, context: Context): String? {
        return runCatching {
            val uriHash = uri.md5Hash()
            val imageBytes = bitmap.toByteArray()
            val imageHash = imageBytes.md5Hash()
            val fileName = "$READING_BG_PREFIX${uriHash}_$imageHash.jpg"
            val file = File(context.filesDir, fileName)

            context.filesDir.listFiles { _, name ->
                name.startsWith("$READING_BG_PREFIX$uriHash") && name != fileName
            }?.forEach { it.delete() }

            file.writeBytes(imageBytes)
            file.absolutePath
        }.getOrNull()
    }

    private fun createImageFile(context: Context, fileName: String, bytes: ByteArray): String? {
        return File(context.filesDir, fileName).apply {
            writeBytes(bytes)
        }.takeIf { it.exists() }?.absolutePath
    }

    private fun ByteArray.md5Hash(): String = MessageDigest
        .getInstance("MD5")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

    private fun String.md5Hash(): String = MessageDigest
        .getInstance("MD5")
        .digest(toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun Bitmap.toByteArray(): ByteArray {
        return ByteArrayOutputStream().use { stream ->
            compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }
    }

    private fun File.isImageFile(): Boolean = extension.equals("jpg", ignoreCase = true)

    private fun File.findFile(fileName: String): File? = listFiles { _, name ->
        name == fileName
    }?.firstOrNull()
}