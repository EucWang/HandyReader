package com.wxn.mobi.inative

import android.content.Context
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.base.bean.ReaderText
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

//    external fun convertToEpub(context: Context, path: String): String?

    external fun getChapters(context: Context, book_id: Long, path: String) : Array<BookChapter>?

    external fun getChapter(context: Context, path: String, chapter: BookChapter): Array<ReaderText>

    external fun getCssInfo(context: Context, book_id: Long, cssNames: Array<String>): Array<CssInfo>?
}