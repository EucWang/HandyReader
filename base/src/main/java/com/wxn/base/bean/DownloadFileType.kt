package com.wxn.base.bean

import com.wxn.base.util.PathUtil

enum class DownloadFileType(val displayName: String, val directory: String) {
    BG_IMAGE("Background Image", PathUtil.PATH_READ_BG),
    TTS_MODEL("TTS Model", PathUtil.PATH_TTS_MODELS),
    TTS_DEPENDENCY("TTS Dependency", PathUtil.PATH_TTS_DEPENDENCIES),

    FONT("Font", PathUtil.PATH_FONTS),
    OPDS_BOOK("OPDS Book", PathUtil.PATH_OPDS_BOOKS);

    companion object {
        fun fromString(value: String): DownloadFileType {
            return values().find { it.name == value } ?: BG_IMAGE
        }
    }
}