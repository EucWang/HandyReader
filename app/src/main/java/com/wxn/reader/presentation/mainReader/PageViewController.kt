package com.wxn.reader.presentation.mainReader

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.wxn.base.bean.Book
import com.wxn.base.util.launchIO
import com.wxn.bookread.PageCallback
import com.wxn.bookread.ReadBook
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.ui.PageViewCallback
import com.wxn.bookread.ui.PageViewDataProvider
import com.wxn.bookread.ui.SelectTextCallback
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.use_case.chapters.BookHelper
import com.wxn.reader.domain.use_case.chapters.GetChapterByIdUserCase
import com.wxn.reader.domain.use_case.chapters.GetChapterCountByBookIdUserCase
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

open class PageViewController @Inject constructor(val context: Context,
    val getChapterByIdUserCase: GetChapterByIdUserCase,
    val getChapterCountByBookIdUserCase : GetChapterCountByBookIdUserCase,
    val appPreferencesUtil: AppPreferencesUtil,
): PageViewDataProvider, PageViewCallback, SelectTextCallback  {

    var scope: CoroutineScope? = null
    var titleDate = MutableLiveData<String>()
    var book: Book? = null
    var inBookshelf = false
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

    suspend fun resetBook(book:Book){
        this.book = book
        getChapterCountByBookIdUserCase.invoke(book.id).collect { count->
            this.chapterSize = count
        }
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
        ReadBook.loadContent(durChapterIndex, resetPageOffset = resetPageOffset)
        loadContent(durChapterIndex + 1, resetPageOffset = resetPageOffset)
        loadContent(durChapterIndex - 1, resetPageOffset = resetPageOffset)
    }

    private fun loadContent(index: Int, upContent: Boolean = true, resetPageOffset: Boolean) {
        val curBook = book ?: return
        scope?.launchIO {
            val bookId = curBook.id
            getChapterByIdUserCase(bookId, index).collect { chapter ->
                BookHelper.loadChpaterContent(context, bookId, chapter)?.let { content ->
                    val contents = BookHelper.disposeContent(appPreferencesUtil, chapter, content)
                    when(chapter.chapterIndex) {
                        durChapterIndex -> {  //当前章节
                            ChapterProvider.getTextChapter(context, curBook, chapter, contents, imageStyles = "", chapterSize)
                        }
                        durChapterIndex -1 -> { //上一个章节

                        }
                        durChapterIndex + 1 -> { //下一个章节

                        }
                    }
                }
            }
        }
    }

    /***
     * 当前章节中正在显示的页面的索引
     */
    override fun durChapterPos(): Int {
        ReadBook.curTextChapter?.let {
            if (ReadBook.durPageIndex < it.pageSize) {
                return ReadBook.durPageIndex
            }
            return it.pageSize - 1
        }
        return ReadBook.durPageIndex
    }

    override fun clickCenter() {
//        TODO("Not yet implemented")
    }

    override fun screenOffTimerStart() {
//        TODO("Not yet implemented")
    }

    override fun showTextActionMenu() {
//        TODO("Not yet implemented")
    }

    override fun upSelectedStart(x: Float, y: Float, top: Float) {
//        TODO("Not yet implemented")
    }

    override fun upSelectedEnd(x: Float, y: Float) {
//        TODO("Not yet implemented")
    }

    override fun onCancelSelect() {
//        TODO("Not yet implemented")
    }
}