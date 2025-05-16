package com.wxn.bookread

import androidx.lifecycle.MutableLiveData
import com.wxn.base.bean.Book
import com.wxn.bookread.data.model.TextChapter

interface PageCallback {
    fun loadChapterList(book: Book)
    fun upContent(relativePosition: Int = 0, resetPageOffset: Boolean = true)
    fun upView()
    fun pageChanged()
    fun contentLoadFinish()
    fun upPageAnim()
}

object ReadBook {

    var titleDate = MutableLiveData<String>()
    var book: Book? = null
    var inBookshelf = false
    var chapterSize = 0
    var durChapterIndex = 0
    var durPageIndex = 0
    var isLocalBook = true
    var callBack: PageCallback? = null
    var prevTextChapter: TextChapter? = null
    var curTextChapter: TextChapter? = null
    var nextTextChapter: TextChapter? = null
    var msg: String? = null

    /***
     * 正在加载中的章节的索引列表
     * 防止重复添加
     */
    private val loadingChapters = arrayListOf<Int>()
//    private val readRecord = ReadRecord() //TODO
    var readStartTime: Long = System.currentTimeMillis()

    fun resetData(book: Book) {
        this.book = book
        //设置阅读记录的书名
//        readRecord.bookName = book.title
        //根据书名查找阅读记录
//        readRecord.readTime = App.db.getReadRecordDao().getReadTime(book.bookName) ?: 0
        //设置当前章节索引
//        durChapterIndex = book.durChapterIndex //TODO
        //设置当前阅读的进度(首行字符的索引位置)
//        durPageIndex = book.durChapterPos   //TODO
        //是否是本地数据
        isLocalBook = true // book.origin == BookType.local
        //清空章节数量大小, 前一个章节，当前章节，下一个章节
        chapterSize = 0
        prevTextChapter = null
        curTextChapter = null
        nextTextChapter = null
        titleDate.postValue(book.title) //更新书名，并触发界面书名显示更新
        //清空图片缓存
//        ImageProvider.clearAllCache() //TODO
        synchronized(this) {
            loadingChapters.clear() //清理已加载了的章节
        }
    }

//    fun upReadStartTime() {
//        Coroutine.async {
//            readRecord.readTime = readRecord.readTime + System.currentTimeMillis() - readStartTime
//            readStartTime = System.currentTimeMillis()
//            App.db.getReadRecordDao().insert(readRecord)
//        }
//    }

    fun moveToNextChapter(upContent: Boolean): Boolean { //TODO
        return false
    }

    fun setPageIndex(pageIndex: Int) { //TODO
        durPageIndex = pageIndex
//        saveRead()
//        curPageChanged()
    }


    fun moveToPrevChapter(upContent: Boolean, toLast: Boolean = true): Boolean { //TODO
        return false
    }


    /**
     * chapterOnDur: 0为当前页,1为下一页,-1为上一页
     */
    fun textChapter(chapterOnDur: Int = 0): TextChapter? {
        return when (chapterOnDur) {
            0 -> curTextChapter
            1 -> nextTextChapter
            -1 -> prevTextChapter
            else -> null
        }
    }

    /***
     * 当前章节中正在显示的页面的索引
     */
    fun durChapterPos(): Int {
        curTextChapter?.let {
            if (durPageIndex < it.pageSize) {
                return durPageIndex
            }
            return it.pageSize - 1
        }
        return durPageIndex
    }

    /**
     * 加载章节内容
     */
    fun loadContent(resetPageOffset: Boolean) {
        loadContent(durChapterIndex, resetPageOffset = resetPageOffset)
        loadContent(durChapterIndex + 1, resetPageOffset = resetPageOffset)
        loadContent(durChapterIndex - 1, resetPageOffset = resetPageOffset)
    }

    fun loadContent(index: Int, upContent: Boolean = true, resetPageOffset: Boolean) {
        //TODO
    }
}