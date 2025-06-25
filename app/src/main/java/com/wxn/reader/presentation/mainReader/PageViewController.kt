package com.wxn.reader.presentation.mainReader

import android.content.Context
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.bean.TextTag
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
import com.wxn.reader.domain.use_case.chapters.UpdateChapterWordCountUserCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.math.roundToInt

open class PageViewController @Inject constructor(
    val context: Context,
    val getChapterByIdUserCase: GetChapterByIdUserCase,
    val getChapterCountByBookIdUserCase: GetChapterCountByBookIdUserCase,
    val updateChapterWordCountUserCase: UpdateChapterWordCountUserCase,
    val updateBookUseCase: UpdateBookUseCase,
    val appPreferencesUtil: AppPreferencesUtil,
    val textParser: TextParser
) : PageViewDataProvider, PageViewCallback, SelectTextCallback {

    var scope: CoroutineScope? = null
//    var titleDate = MutableLiveData<String>()

    override var book: Book? = null

    var inBookshelf = false
    var durPageIndex = 0
    var targetProgress: Double = -1.0 //临时保存更改的进度，默认0.0, 不作为正常进度使用

    //    var isLocalBook = true
    var callBack: PageCallback? = null
    var prevTextChapter: TextChapter? = null
    var curTextChapter: TextChapter? = null
    var nextTextChapter: TextChapter? = null
    override var msg: String? = null            //对应章节名？

    interface OnClickListener {
        fun onCenterClick()
        fun onLinkClick(href: String?, clickX: Float, clickY: Float)
        fun onPageChange()
    }

    var clickListener: OnClickListener? = null


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

    val progression: Double
        get() {
            var retVal = curTextChapter?.chapterProgress?.toDouble() ?: 0.0
            curTextChapter?.let { textChapter ->
                val chapterPercent = if (textChapter.totalWordCount > 0) {
                    textChapter.wordCount.toDouble() / textChapter.totalWordCount.toDouble()
                } else {
                    0.0
                }
                val pageSize = textChapter.pageSize
                if (pageSize > 0) {
                    retVal += chapterPercent * (durPageIndex.toDouble() / pageSize.toDouble())
                }
                Logger.d(
                    "PageViewController::progression::totalWordCount=${textChapter.totalWordCount},wordCount=${textChapter.wordCount}," +
                            "pageSize=${pageSize},durPageIndex=${durPageIndex}, retVal=${retVal}"
                )
            }
            return retVal
        }

    init {
        ChapterProvider.tryCreatePreference(context)
    }


    /***
     * 初始章节加载成功/失败回调
     */
    private var onInitChapterLoadListener: ((Boolean) -> Unit)? = null

    @Volatile
    var isCalcChapterWords: Boolean = false

    /****
     * 计算每一章节的字数，已经进度，便于计算用户阅读进度
     */
    suspend fun calcChaptersWords(book: Book) {
        isCalcChapterWords = true
        val start = System.currentTimeMillis()
        val chapterIndexWords: ArrayList<Triple<Int, Int, Int>> = arrayListOf()
        val wordCountTriple = BookHelper.loadWordCount(context, book, textParser)
        var totalWordCount = 0
        val lastOne = wordCountTriple.lastOrNull()
        if (lastOne != null && lastOne.first == -1) {
            totalWordCount = lastOne.second
        }
        Logger.d("PageViewController::calcChaptersWords:totalWordCount=$totalWordCount")
        var progressWordCount = 0L
        if (totalWordCount > 0) {
            chapterIndexWords.addAll(wordCountTriple)
            chapterIndexWords.removeLastOrNull()    //移除最后一条记录总数的条目
            book.wordCount = totalWordCount.toLong()
            for (item in chapterIndexWords) {
                val progress = progressWordCount.toFloat() / totalWordCount
                val wordCount = item.second
                val picCount = item.third
                val count = wordCount + picCount
                val chapterIndex = item.first - 1
                updateChapterWordCountUserCase.invoke(book.id, chapterIndex, wordCount.toLong(), picCount.toLong(), progress)
                progressWordCount += count

                //更新当前加载了的章节的信息
                if (curTextChapter?.position == chapterIndex) {
                    curTextChapter?.wordCount = wordCount.toLong()
                    curTextChapter?.picCount = picCount.toLong()
                    curTextChapter?.chapterProgress = progress
                    curTextChapter?.totalWordCount = totalWordCount.toLong()
                } else if (prevTextChapter?.position == chapterIndex) {
                    prevTextChapter?.wordCount = wordCount.toLong()
                    prevTextChapter?.picCount = picCount.toLong()
                    prevTextChapter?.chapterProgress = progress
                    prevTextChapter?.totalWordCount = totalWordCount.toLong()
                } else if (nextTextChapter?.position == chapterIndex) {
                    nextTextChapter?.wordCount = wordCount.toLong()
                    nextTextChapter?.picCount = picCount.toLong()
                    nextTextChapter?.chapterProgress = progress
                    nextTextChapter?.totalWordCount = totalWordCount.toLong()
                }
            }
            updateBookUseCase.invoke(book)
        }
        isCalcChapterWords = false
        Logger.d("PageViewController::calcChapterWords:totalWordCount=${totalWordCount}, spend=${System.currentTimeMillis() - start}")
    }

    suspend fun resetBook(book: Book, initChapterLoadListener: ((Boolean) -> Unit)) {
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
        durPageIndex = book.scrollOffset
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

    override fun changeChapter(newChapterIndex: Int, newProgress: Double): Boolean {
        if (durChapterIndex != newChapterIndex) {
            durChapterIndex = newChapterIndex
            durPageIndex = 0
        }
        if (newProgress >= 0.0) {
            val curChapter = curTextChapter ?: return false
            if (curChapter.totalWordCount == 0L || curChapter.wordCount == 0L) {
                Logger.e("PageViewController::changeChapter failed, no word count info")
                return false
            }

            targetProgress = newProgress
        }
        loadContent(true)
        return true
    }

    override fun findLinkContent(href: String): String? {
        var anchorId = ""
        if (href.contains("#")) {
            val hrefParts = href.split("#")
            if (hrefParts.size == 2) {
                anchorId = hrefParts[1]
            }
        } else {
            anchorId = href
        }

        curTextChapter?.readerTexts?.let { texts ->
            var linkIndex = -1
            for (index in 0 until texts.size) {
                val paragraph = texts[index]
                if (paragraph is ReaderText.Text) {
                    val tag = paragraph.annotations.firstOrNull { it.anchorId.isNotEmpty() && it.anchorId == anchorId }
                    if (tag != null) {
                        linkIndex = index
                        break
                    }
                }
            }
            if (linkIndex >= 0 && linkIndex < texts.size) {
                var content = StringBuilder()
                for (index in linkIndex until texts.size) {
                    var paragraph = texts[index]
                    if (paragraph is ReaderText.Text) {
                        val tag = paragraph.annotations.firstOrNull { tag ->
                            tag.anchorId.isNotEmpty()
                        }
                        if ((tag == null || tag.anchorId == anchorId)) {
                            if (paragraph.line.isNotEmpty()) {
                                content.append(paragraph.line)
                            }
                        } else {
                            break
                        }
                        if (content.length > 5) {
                            break
                        } else {
                            content.append("\n")
                        }
                    }
                }
                return content.toString()
            }
        }
        return null
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
        clickListener?.onPageChange()
    }

    private fun saveRead() {
        val curBook = book ?: return
        scope?.launchIO {
            curBook.lastOpened = System.currentTimeMillis()
            curBook.scrollIndex = durChapterIndex
            curBook.scrollOffset = durPageIndex
            curBook.progress = (progression * 100.0).toFloat()
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
            Logger.i("PageViewController::loadContent:index=$index, contents.size=${contents.size}")

            val tags = hashMapOf<Int, List<TextTag>>()  //章节全部标签信息
            contents.forEachIndexed { index, content ->
                if (content is ReaderText.Text) {
                    if (content.annotations.isNotEmpty()) {
                        tags[index] = content.annotations
                    }
                }
            }
            val cssInfos = BookHelper.loadChpaterCsses(context, curBook, tags, textParser)      //章节全部的css信息

            val contents = BookHelper.disposeContent(appPreferencesUtil, chapter, contents, cssInfos)

            val cssInfoMaps = hashMapOf<Int, TextCssInfo>()
            var wordCount = 0L
            for ((index, content) in contents.withIndex()) {
                if (content is ReaderText.Text) {
                    cssInfoMaps[index] = content.textCssInfo
                    wordCount += content.line.length
                }
            }

            val textChapter = ChapterProvider.getTextChapter(chapter, contents, imageStyles = "", chapterSize)
            textChapter?.annotations = tags
            textChapter?.textCssInfos = cssInfoMaps
            textChapter?.readerTexts = contents
            textChapter?.wordCount = wordCount
            textChapter?.totalWordCount = curBook.wordCount
            textChapter?.chapterProgress = chapter.chapterProgress

            var needOnPageChange = (targetProgress < 0.0)

            when (chapter.chapterIndex) {
                durChapterIndex -> {    //加载的是当前章节
                    curTextChapter = textChapter

                    if (targetProgress >= 0.0 && curBook.wordCount > 0 && targetProgress >= chapter.chapterProgress) { //修改切换之后的显示章节的第几页
                        val inChapterProgress = targetProgress - chapter.chapterProgress
                        val inChapterPercent = chapter.wordCount.toDouble() / curBook.wordCount.toDouble()
                        val chapterPageSize = textChapter?.pageSize ?: 0

                        Logger.d("PageViewController::inChapterProgress=${inChapterProgress},inChapterPercent=${inChapterPercent}, pageSize=${chapterPageSize} durPageIndex=$durPageIndex,targetProgress=$targetProgress")
                        val pageIndex = ((inChapterProgress / inChapterPercent) * (chapterPageSize.toDouble() ?: 0.0)).roundToInt()
                        if (pageIndex in 0 until (textChapter?.pageSize ?: 0)) {
                            durPageIndex = pageIndex
                        }
                        Logger.d("PageViewController::pageIndex =${pageIndex}, durPageIndex=$durPageIndex, wordCount=${curTextChapter?.wordCount},totalWordCount=${curTextChapter?.totalWordCount}")
                        targetProgress = -1.0
                        needOnPageChange = true
                    }

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

            if (needOnPageChange) {
                Logger.e("PageViewController::loadContent success onPageChange::${durChapterIndex}")
                clickListener?.onPageChange()
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
            Logger.d("PageViewController::moveToPrevChapter, durChapterIndex=${durChapterIndex - 1}")
            loadContent(durChapterIndex.minus(1), upContent, false)
        }
        saveRead()
        callBack?.upView()
        return true
    }

    override fun clickCenter() {
        Logger.i("PageViewController::clickCenter")
        clickListener?.onCenterClick()
    }

    fun getSelectedText(): String {
        return callBack?.getSelectedText().orEmpty()
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

    override fun clickLink(tag: TextTag, clickX: Float, clickY: Float) {
        val params = tag.paramsPairs()
        val href = params.find { pair ->
            pair.first == "href"
        }?.second.orEmpty()
        Logger.d("PageViewController::clickLink::${tag}, href=${href}")
        if (href.isNotEmpty()) {
            clickListener?.onLinkClick(href, clickX, clickY)
        }
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