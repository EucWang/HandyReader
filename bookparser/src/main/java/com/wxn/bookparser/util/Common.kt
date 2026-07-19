package com.wxn.bookparser.util

import android.content.Context
import java.io.File
import java.util.UUID

fun getCoverPath(context: Context, coverImageName: String) =
    context.filesDir.absolutePath + File.separator + "covers" + File.separator + UUID.randomUUID().toString() + "_" + coverImageName.replace(File.separator, "_")
