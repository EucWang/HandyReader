package com.wxn.bookparser.exts

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.extension
import com.anggrayudi.storage.file.toRawFile
import java.io.File

fun DocumentFile.rawFile(context: Context): File? {
    return this.toRawFile(context)
}

val DocumentFile.mimeType: String
    get() = this.extension