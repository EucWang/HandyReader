package com.wxn.reader.presentation.mainReader

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxn.base.bean.Book
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.bookparser.TextParser
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.singleOrNull
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

open class PageViewController @Inject constructor(
    val context: Context,
    val getChapterByIdUserCase: GetChapterByIdUserCase,
    val getChapterCountByBookIdUserCase: GetChapterCountByBookIdUserCase,
    val updateBookUseCase: UpdateBookUseCase,
    val appPreferencesUtil: AppPreferencesUtil,
    val textParser: TextParser
) : PageViewDataProvider, PageViewCallback, SelectTextCallback {

    var scope: CoroutineScope? = null
//    var titleDate = MutableLiveData<String>()

    override var book: Book? = null

    var inBookshelf = false
    var durPageIndex = 0

    //    var isLocalBook = true
    var callBack: PageCallback? = null
    var prevTextChapter: TextChapter? = null
    var curTextChapter: TextChapter? = null
    var nextTextChapter: TextChapter? = null
    override var msg: String? = null            //对应章节名？

    /***
     * 当前显示的章节索引
     */
    override var durChapterIndex: Int = 0
    override var headerHeight: Int = 0

    /***
     * 章节数
     */
    override var chapterSize: Int = 0

    @Volatile
    override var isInitFinish: Boolean = false

    override var isAutoPage: Boolean = false

    override var autoPageProgress: Int = 0

    override var pageFactory: TextPageFactory? = null

    override var isScroll: Boolean = false

    private var screenTimeOut: Long = 0

    init {
        ChapterProvider.tryCreatePreference(context)
    }

    /***
     * 初始章节加载成功/失败回调
     */
    private var onInitChapterLoadListener: ((Boolean)->Unit)? = null

    suspend fun resetBook(book: Book, initChapterLoadListener: ((Boolean)->Unit)) {
        Logger.i("PageViewController::resetBook:book=$book")
        this.prevTextChapter = null
        this.curTextChapter = null
        this.nextTextChapter = null
        chapterSize = 0
        durChapterIndex = 0
        isScroll = false

        this.book = book
        val count = try {
            getChapterCountByBookIdUserCase(book.id).first()
        } catch (ex: NoSuchElementException) {
            Logger.e("PageViewController::resetBook:${ex.message}, failed")
            return
        }
        this.chapterSize = count
        durChapterIndex = book.scrollIndex
        Logger.d("PageViewController::resetBook:chapterSize=$chapterSize, durChapterIndex=$durChapterIndex")
        isInitFinish = true
        onInitChapterLoadListener = initChapterLoadListener
        Logger.d("PageViewController::resetBook:isInitFinish=$isInitFinish")
    }

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
        Logger.i("PageViewController::loadContent:resetPageOffset=$resetPageOffset,durChapterIndex=$durChapterIndex, isInitFinish=$isInitFinish")
        if (isInitFinish) {
            scope?.launchIO {
                loadContent(durChapterIndex, resetPageOffset = resetPageOffset)
                loadContent(durChapterIndex + 1, resetPageOffset = resetPageOffset)
                loadContent(durChapterIndex - 1, resetPageOffset = resetPageOffset)
            }
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
//        Logger.i("PageViewController::loadContent:index=$index,upContent=$upContent,resetPageOffset=$resetPageOffset,bookid=${book?.id},bookname=${book?.title}")
        if (index < 0) return
        val curBook = book ?: return
        val bookId = curBook.id
        Logger.i("PageViewController::loadContent:index=$index,bookId=$bookId")

        val chapter = try {
            getChapterByIdUserCase(bookId, index).first()
        } catch (ex: NoSuchElementException) {
            Logger.e("PageViewController::${ex.message}, failed")
            if (isInitFinish) {
                onInitChapterLoadListener?.invoke(false)
                onInitChapterLoadListener = null
            }
            return
        }
        BookHelper.loadChapterContent(context, curBook, chapter, textParser).let { contents ->
            val contents = BookHelper.disposeContent(appPreferencesUtil, chapter, contents)
            Logger.i("PageViewController::loadContent:index=$index, contents.size=${contents.size}")
            val textChapter = ChapterProvider.getTextChapter(context, curBook, chapter, contents, imageStyles = "", chapterSize)
            when (chapter.chapterIndex) {
                durChapterIndex -> {    //加载的是当前章节
                    curTextChapter = textChapter
                    if (upContent) {
                        callBack?.upContent(resetPageOffset = resetPageOffset)
                    }
                    callBack?.upView()
                    if (isInitFinish && onInitChapterLoadListener != null) {
                        Logger.e("PageViewController::loadChapterContent first success")
                        onInitChapterLoadListener?.invoke(true)
                        onInitChapterLoadListener = null
                    }
                }

                durChapterIndex - 1 -> { //加载的是上一章节
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

    override fun moveToNextChapter(upContent: Boolean): Boolean {
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
                Logger.d("PageViewController::moveToNextChapter:when curTextChapter is null, durChapterIndex=$durChapterIndex")
                loadContent(durChapterIndex, upContent, false)
            }
        } else {
            callBack?.upContent()
        }
        Coroutines.mainScope().launchIO {
            Logger.d("PageViewController::moveToNextChapter:, durChapterIndex=${durChapterIndex + 1}")
            loadContent(durChapterIndex.plus(1), upContent, false)
        }
        saveRead()
        callBack?.upView()
//        curPageChanged()
        return true
    }

    override fun moveToPrevChapter(upContent: Boolean, toLast: Boolean): Boolean {
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
                Logger.d("PageViewController::moveToPrevChapter when curTextChapter is null, durChapterIndex=${durChapterIndex}")
                loadContent(durChapterIndex, upContent, false)
            }
        } else if (upContent) {
            callBack?.upContent()
        }

        Coroutines.mainScope().launchIO {
            Logger.d("PageViewController::moveToPrevChapter, durChapterIndex=${durChapterIndex-1}")
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
//        Logger.i("PageViewController::screenOffTimerStart")
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

    fun clear() {
        book = null
        callBack = null
        prevTextChapter = null
        curTextChapter = null
        nextTextChapter = null
        durChapterIndex = 0
        durPageIndex = 0
        msg = null
        headerHeight = 0
        chapterSize = 0
        isInitFinish = false
        isAutoPage = false
        autoPageProgress = 0
        pageFactory = null
        isScroll = false
        Logger.i("PageViewController:clear()")
    }
}