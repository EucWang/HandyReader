package com.wxn.mobi.inative

import android.content.Context
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.CssInfo
import com.wxn.mobi.data.model.CountPair
import com.wxn.mobi.data.model.FileCrc
import com.wxn.mobi.data.model.MetaInfo
import com.wxn.mobi.data.model.ParagraphData

object NativeLib {

    init {
        System.loadLibrary("appmobi")
    }

    external fun searchFiles(root: String, patterns: Array<String>): Array<String>

    external fun nativeFilesCrc(paths: Array<String>): Array<FileCrc>?

    external fun loadMobi(context: Context, path: String): MetaInfo? // Array<String>?

    external fun loadEpub(context: Context, path: String): MetaInfo? // Array<String>?

    external fun loadFb2(context: Context, path: String): MetaInfo? // Array<String>?

    /***
     * 获得书籍章节信息
     * @param context
     * @param book_id
     * @param path
     * @param type 1: mobi/azw3; 2: epub; 3: fb2
     * @return
     */
    external fun getChapters(context: Context, book_id: Long, path: String, type:Int): Array<BookChapter>?

    /***
     * 获得书籍章节内数据
     * @param context
     * @param path
     * @param chapter
     * @param type 1: mobi/azw3; 2: epub; 3: fb2
     * @return
     */
    external fun getChapter(context: Context, path: String, chapter: BookChapter, type: Int): Array<ParagraphData>

    external fun getCssInfo(context: Context, book_id: Long, cssNames: Array<String>, tagNames: Array<String>,ids: Array<String>, type: Int): Array<CssInfo>?

    external fun getWordCount(bookId: Long, path: String, type: Int): List<CountPair>

    external fun closeBook(bookId: Long, path: String, type: Int)

}