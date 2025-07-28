package com.wxn.reader.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.wxn.base.util.Logger
import com.wxn.base.util.supportedExtensions
import com.wxn.bookparser.exts.mimeType

object DocumentUtil {

    suspend fun getFilesFromDirectory(context: Context, uri: Uri): List<DocumentFile> {
        return try {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            if (documentFile == null) {
                emptyList()
            } else {
                scanDirs(documentFile)
            }
        } catch (e: Exception) {
            Logger.e("HomeViewModel::Error scanning directory: $uri, $e")
            emptyList()
        }
    }

    private fun scanDirs(directory: DocumentFile): List<DocumentFile> {
        val rets = arrayListOf<DocumentFile>()
        val exts = supportedExtensions()
        val dirs = arrayListOf(directory)
        do {
            val dir = dirs.removeFirstOrNull() ?: break
            val files = dir.listFiles()
            for(file in files) {
                if(file.isDirectory) {
                    dirs.add(file)
                } else if (file.isFile) {
                    if (file.mimeType in exts) {
                        rets.add(file)
                    }
                }
            }
        }while (dirs.isNotEmpty())
        return rets
    }
}