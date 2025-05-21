package com.wxn.reader.presentation.mainReader

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.wxn.base.bean.Book
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.ui.PageCallback
import com.wxn.bookread.ui.PageViewCallback
import com.wxn.bookread.ui.PageViewDataProvider
import com.wxn.bookread.ui.SelectTextCallback
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.use_case.books.UpdateBookUseCase
import com.wxn.reader.domain.use_case.chapters.BookHelper
import com.wxn.reader.domain.use_case.chapters.GetChapterByIdUserCase
import com.wxn.reader.domain.use_case.chapters.GetChapterCountByBookIdUserCase
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

open class PageViewController @Inject constructor(val context: Context,
    val getChapterByIdUserCase: GetChapterByIdUserCase,
    val getChapterCountByBookIdUserCase : GetChapterCountByBookIdUserCase,
      val updateBookUseCase: UpdateBookUseCase,
    val appPreferencesUtil: AppPreferencesUtil,
): PageViewDataProvider, PageViewCallback, SelectTextCallback  {

    var scope: CoroutineScope? = null
    var titleDate = MutableLiveData<String>()

    override var book: Book? = null

    var inBookshelf = false
    var durPageIndex = 0
    var isLocalBook = true
    var callBack: PageCallback? = null
    var prevTextChapter: TextChapter? = null
    var curTextChapter: TextChapter? = null
    var nextTextChapter: TextChapter? = null
    override var msg: String? = null            //对应章节名？
    /***
     * 正在加载中的章节的索引列表
     * 防止重复添加
     */
    private val loadingChapters = arrayListOf<Int>()

    /***
     * 当前显示的章节索引
     */
    override var durChapterIndex: Int = 0
    override var headerHeight: Int = 0

    /***
     * 章节数
     */
    override var chapterSize: Int= 0

    override var isInitFinish: Boolean= false

    override var isAutoPage: Boolean= false

    override var autoPageProgress: Int= 0

    override var pageFactory: TextPageFactory? = null

    override var isScroll: Boolean= false

    private var screenTimeOut : Long = 0

    init {
        ChapterProvider.tryCreatePreference(context)
    }

    suspend fun resetBook(book:Book){
        Logger.i("PageViewController::resetBook:book=$book")
        this.prevTextChapter = null
        this.curTextChapter = null
        this.nextTextChapter = null
        chapterSize = 0
        durChapterIndex = 0
        isScroll = false

        this.book = book
        getChapterCountByBookIdUserCase.invoke(book.id).collect { count->
            this.chapterSize = count
            durChapterIndex = book.scrollIndex
            Logger.d("PageViewController::resetBook:chapterSize=$chapterSize, durChapterIndex=$durChapterIndex")
            isInitFinish = true
            Logger.d("PageViewController::resetBook:isInitFinish=$isInitFinish")
        }
    }

    /***
     * 是否正确的将章节索引加入到loadingChapters集合中，
     * 已经有了，则返回false
     */
//    private fun addLoading(index: Int): Boolean {
//        synchronized(this) {
//            if (loadingChapters.contains(index)) return false
//            loadingChapters.add(index)
//            return true
//        }
//    }

//    fun removeLoading(index: Int) {
//        synchronized(this) {
//            loadingChapters.remove(index)
//        }
//    }

    /**
     * chapterOnDur: 0为当前页,1为下一页,-1为上一页
     */
    override fun textChapter(chapterOnDur: Int): TextChapter? {
        return when (chapterOnDur) {
            0 -> curTextChapter
            1 -> nextTextChapter
            -1 -> prevTextChapter
            else -> null
        }
    }

    override fun loadContent(resetPageOffset: Boolean) {
        Logger.i("PageViewController::loadContent:resetPageOffset=$resetPageOffset,durChapterIndex=$durChapterIndex")
        scope?.launchIO {
            loadContent(durChapterIndex, resetPageOffset = resetPageOffset)
            loadContent(durChapterIndex + 1, resetPageOffset = resetPageOffset)
            loadContent(durChapterIndex - 1, resetPageOffset = resetPageOffset)
        }
    }

    override fun setPageIndex(index: Int) {
        durPageIndex = index
        saveRead()
        callBack?.pageChanged() // 通知界面刷新进度
    }

    private fun saveRead() {
        val curBook = book ?: return
        scope?.launchIO {
            curBook.lastOpened = System.currentTimeMillis()
            curBook.scrollIndex = durChapterIndex
            curBook.scrollOffset = durPageIndex
            updateBookUseCase(curBook)
        }
    }

    override fun upMsg(msg: String?) {
        if (this.msg != msg) {
            this.msg = msg
            callBack?.upContent()
        }
    }

    private suspend fun loadContent(index: Int, upContent: Boolean = true, resetPageOffset: Boolean) {
        Logger.i("PageViewController::loadContent:index=$index,upContent=$upContent,resetPageOffset=$resetPageOffset")
        if (index < 0) return
        val curBook = book ?: return
        val bookId = curBook.id
        Logger.i("PageViewController::loadContent:index=$index,bookId=$bookId")
//        if (!addLoading(index)) return
        getChapterByIdUserCase(bookId, index).collect { chapter ->
            Logger.i("PageViewController::loadContent:index=$index, chapter=$chapter")
            BookHelper.loadChpaterContent(context, bookId, chapter)?.let { content ->
                val contents = BookHelper.disposeContent(appPreferencesUtil, chapter, content)
                Logger.i("PageViewController::loadContent:index=$index, contents.size=${contents.size}")
                val textChapter = ChapterProvider.getTextChapter(context, curBook, chapter, contents, imageStyles = "", chapterSize)
                when(chapter.chapterIndex) {
                    durChapterIndex -> {    //加载的是当前章节
                        curTextChapter = textChapter
                        if (upContent) {

                            callBack?.upContent(resetPageOffset = resetPageOffset)
                        }
                        callBack?.upView()
                    }
                    durChapterIndex -1 -> { //加载的是上一章节
                        prevTextChapter = textChapter
                        if (upContent) {
                            callBack?.upContent(-1, resetPageOffset)
                        }
                    }
                    durChapterIndex + 1 -> {    //加载的是下一章节
                        nextTextChapter = textChapter
                        if (upContent) {
                            callBack?.upContent(1, resetPageOffset)
                        }
                    }
                }
            }
//            removeLoading(index)
        }
    }

    /***
     * 当前章节中正在显示的页面的索引
     */
    override fun durChapterPos(): Int {
        Logger.i("PageViewController::durChapterPos")
        curTextChapter?.let {
            if (durPageIndex < it.pageSize) {
                return durPageIndex
            }
            return it.pageSize - 1
        }
        Logger.i("PageViewController::durChapterPos::durPageIndex=$durPageIndex")
        return durPageIndex
    }


    fun moveToNextPage() {
        durPageIndex++
        callBack?.upContent()
        saveRead()
    }

    override fun moveToNextChapter(upContent: Boolean) :Boolean {
        if (durChapterIndex >= chapterSize - 1) {
            return false
        }

        val curBook = book ?: return false
        durPageIndex = 0
        durChapterIndex++
        prevTextChapter = curTextChapter
        curTextChapter = nextTextChapter
        nextTextChapter = null
        if (curTextChapter == null) {
            Coroutines.mainScope().launchIO {
                loadContent(durChapterIndex, upContent, false)
            }
        } else {
            callBack?.upContent()
        }
        Coroutines.mainScope().launchIO {
            loadContent(durChapterIndex.plus(1), upContent, false)
        }
        saveRead()
        callBack?.upView()
//        curPageChanged()
        return true
    }

    override fun moveToPrevChapter(upContent: Boolean, toLast: Boolean) : Boolean {
        if (durChapterIndex <= 0) {
            return false
        }
        val curBook = book ?: return false

        durPageIndex = if (toLast) {
            prevTextChapter?.lastIndex ?: 0
        } else {
            0
        }
        durChapterIndex--

        nextTextChapter = curTextChapter
        curTextChapter = prevTextChapter
        prevTextChapter = null

        if (curTextChapter == null) {
            Coroutines.mainScope().launchIO {
                loadContent(durChapterIndex, upContent, false)
            }
        } else if (upContent) {
            callBack?.upContent()
        }

        Coroutines.mainScope().launchIO {
            loadContent(durChapterIndex.minus(1), upContent, false)
        }
        saveRead()
        callBack?.upView()
        return true
    }

    override fun clickCenter() {
        Logger.i("PageViewController::clickCenter")
//        TODO("Not yet implemented")
    }

    /****
     * 设置屏幕常亮
     */
    override fun screenOffTimerStart() {
        Logger.i("PageViewController::screenOffTimerStart")
    }

    override fun showTextActionMenu() {
        Logger.i("PageViewController::showTextActionMenu")
//        TODO("Not yet implemented")
    }

    override fun upSelectedStart(x: Float, y: Float, top: Float) {
        Logger.i("PageViewController::upSelectedStart")
//        TODO("Not yet implemented")
    }

    override fun upSelectedEnd(x: Float, y: Float) {
        Logger.i("PageViewController::upSelectedEnd")
//        TODO("Not yet implemented")
    }

    override fun onCancelSelect() {
        Logger.i("PageViewController::onCancelSelect")
//        TODO("Not yet implemented")
    }
}