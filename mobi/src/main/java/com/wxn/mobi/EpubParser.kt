package com.wxn.mobi

import android.content.Context
import android.util.Log
import com.wxn.mobi.data.model.MobiInfo
import com.wxn.mobi.inative.NativeLib

object EpubParser {

    fun getEpubInfo(context: Context, path: String): MobiInfo? {
        Log.d("MobiParser", "getMobiInfo:path=$path")
        val mobiInfo: MobiInfo? = NativeLib.loadEpub(context.applicationContext, path)
        Log.d("MobiParser", "mobiInfo = $mobiInfo")
        return mobiInfo
    }

}