package com.wxn.mobi

import android.content.Context
import android.util.Log
import com.wxn.mobi.data.model.MobiInfo
import com.wxn.mobi.inative.NativeLib

object MobiParser {

    fun getMobiInfo(context: Context, path: String): MobiInfo? {
        Log.d("MobiParser", "getMobiInfo:path=$path")
        val strings = NativeLib.loadMobi(context, path)

        if (strings.isNullOrEmpty()) {
            return null
        }

        return MobiInfo(
            title = strings.getOrNull(1).orEmpty(),
            author = strings.getOrNull(2).orEmpty(),
            contributor = strings.getOrNull(3).orEmpty(),

            subject = strings.getOrNull(4).orEmpty(),
            publisher = strings.getOrNull(5).orEmpty(),
            date = strings.getOrNull(6).orEmpty(),

            description = strings.getOrNull(7).orEmpty(),
            review = strings.getOrNull(8).orEmpty(),
            imprint = strings.getOrNull(9).orEmpty(),

            copyright = strings.getOrNull(10).orEmpty(),
            isbn = strings.getOrNull(11).orEmpty(),
            asin = strings.getOrNull(12).orEmpty(),

            language = strings.getOrNull(13).orEmpty(),
            isEncrypted = strings.getOrNull(14).orEmpty() == "true",

            coverPath = strings.getOrNull(0).orEmpty(),
        )
    }
}