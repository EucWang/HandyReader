package com.wxn.mobi.inative

import android.content.Context
import com.wxn.mobi.data.model.FileCrc
import com.wxn.mobi.data.model.MobiInfo

object NativeLib {

//    companion object {
        // Used to load the 'mobi' library on application startup.
        init {
            System.loadLibrary("appmobi")
        }
//    }

    external fun nativeFilesCrc(paths: Array<String>) : Array<FileCrc>?

    external fun loadMobi(context: Context, path:String) : MobiInfo? // Array<String>?

    external fun convertToEpub(context: Context, path: String): String?
}