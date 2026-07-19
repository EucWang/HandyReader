package com.wxn.reader.data.model

import android.content.Intent

data class TranslatorItem(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val packageName: String? = null,
    val activityName: String? = null,
    val isBuiltIn: Boolean = false,
    val intentAction: String = Intent.ACTION_PROCESS_TEXT
)
