package com.wxn.reader.util

import com.wxn.reader.BuildConfig

data class AppVersion(
    val versionName: String,
    val versionNumber: Long,
    val releaseDate: String
)

fun getAppVersion(): AppVersion? {
    return try {
        AppVersion(
            versionName = BuildConfig.VERSION_NAME,
            versionNumber = BuildConfig.VERSION_CODE.toLong(),
            releaseDate = BuildConfig.RELEASE_DATE
        )
    } catch (e: Exception) {
        null
    }
}
