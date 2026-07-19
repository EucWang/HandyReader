package com.wxn.bookparser.domain.file

import androidx.compose.runtime.Immutable

@Immutable
data class CachedFileBuilder(
    val name: String?,
    val path: String?,
    val size: Long? = null,
    val lastModified: Long? = null,
    val isDirectory: Boolean? = null
)