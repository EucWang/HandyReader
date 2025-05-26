package com.wxn.mobi

import android.content.Context
import android.util.Log
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.mobi.data.model.FileCrc
import com.wxn.mobi.data.model.MobiInfo
import com.wxn.mobi.inative.NativeLib

object MobiParser {

    fun getFileCrc(path: String): FileCrc? {
        val fileCrc = NativeLib.nativeFilesCrc(arrayOf(path))?.firstOrNull()
        Log.d("MobiParser", "getFileCrc:path=$path,result crc is ${fileCrc?.crc}")
        return fileCrc
    }

    fun getMobiInfo(context: Context, path: String): MobiInfo? {
        Log.d("MobiParser", "getMobiInfo:path=$path")
        val mobiInfo: MobiInfo? = NativeLib.loadMobi(context, path)
        Log.d("MobiParser", "mobiInfo = $mobiInfo")
        return mobiInfo
    }

    fun getMobiChapter(context: Context, bookId: Long, path: String): Array<BookChapter>? {
        Log.d("MobiParser", "getMobiChapter:path=$path")
        val chapters : Array<BookChapter>? = NativeLib.getChapters(context, bookId, path)
        Log.d("MobiParser", "getMobiChapter: = $chapters")
        return chapters
    }

    fun getMobiChapterData(context: Context, path:String, chapter: BookChapter) : Array<ReaderText>? {
        Log.d("MobiParser", "getMobiChapterData:path=$path,chapter=$chapter")
        val texts : Array<ReaderText>? = NativeLib.getChapter(context, path, chapter)
        Log.d("MobiParser", "getMobiChapterData: = $texts")
        return texts
    }

//    fun toEpub(context: Context, path: String): String? {
//        Log.d("MobiParser", "toEpub:path=$path")
//        val epubPath: String? = NativeLib.convertToEpub(context, path)
//        Log.d("MobiParser", "toEpub = $epubPath")
//        return epubPath
//    }
}