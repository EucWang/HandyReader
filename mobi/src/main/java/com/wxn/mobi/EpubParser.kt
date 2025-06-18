package com.wxn.mobi

import android.content.Context
import android.util.Log
import com.wxn.mobi.data.model.MetaInfo
import com.wxn.mobi.inative.NativeLib

object EpubParser {

    fun getEpubInfo(context: Context, path: String): MetaInfo? {
        Log.d("MobiParser", "getMobiInfo:path=$path")
        val metaInfo: MetaInfo? = NativeLib.loadEpub(context.applicationContext, path)
        Log.d("MobiParser", "mobiInfo = $metaInfo")
        return metaInfo
    }

}