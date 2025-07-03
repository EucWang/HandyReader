package com.wxn.reader.presentation.mainReader

import android.content.Context
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.Locator
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.bean.TextTag
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.bookparser.TextParser
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.ui.PageCallback
import com.wxn.bookread.ui.PageViewCallback
import com.wxn.bookread.ui.PageViewDataProvider
import com.wxn.bookread.ui.SelectTextCallback
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.model.toTextTags
import com.wxn.reader.domain.use_case.annotations.GetAnnotationsUseCase
import com.wxn.reader.domain.use_case.books.UpdateBookUseCase
import com.wxn.reader.domain.use_case.chapters.BookHelper
import com.wxn.reader.domain.use_case.chapters.GetChapterByIdUserCase
import com.wxn.reader.domain.use_case.chapters.GetChapterCountByBookIdUserCase
import com.wxn.reader.domain.use_case.chapters.UpdateChapterWordCountUserCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.iterator
import kotlin.collections.orEmpty
import kotlin.collections.set
import kotlin.math.roundToInt

open class PageViewController @Inject constructor(
    val context: Context,
    val getChapterByIdUserCase: GetChapterByIdUserCase,
    val getChapterCountByBookIdUserCase: GetChapterCountByBookIdUserCase,
    val getAnnotationsUseCase: GetAnnotationsUseCase,

    val updateChapterWordCountUserCase: UpdateChapterWordCountUserCase,
    val updateBookUseCase: UpdateBookUseCase,
    val appPreferencesUtil: AppPreferencesUtil,
    val textParser: TextParser
) : PageViewDataProvider, PageViewCallback, SelectTextCallback {

    var scope: CoroutineScope? = null
//    var titleDate = MutableLiveData<String>()

    override var book: Book? = null
    var userAnnotations : ArrayList<BookAnnotation>? = null

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
        fun onSelectedText(startX: Float, startY : Float, endX : Float, endY : Float)
        fun onSelectedCancel()
        fun onCheckedAnnotation(annotationIds: List<String>, startX: Float, startY: Float, endX: Float, endY: Float)
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

        userAnnotations?.clear()
        userAnnotations = arrayListOf()
        try {
            val annotations : List<BookAnnotation> = getAnnotationsUseCase(book.id).first()
            if (annotations.isNotEmpty()) {
                userAnnotations?.addAll(annotations)
            }
        } catch(ex: NoSuchElementException) {
            Logger.e("PageViewController::resetBook2:${ex.message}, failed")
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

    /****
     * refresh view of chapter
     * @param annotation add to TextChapter
     * @param conflictAnnotations delete from TextChapter
     */
    suspend fun updateChapter(annotation: BookAnnotation?, conflictAnnotations: List<BookAnnotation>) {
        val tags = curTextChapter?.annotations?.toMutableMap() ?: return
        if (conflictAnnotations.isNotEmpty()) {
            for(entry in tags) {
                val lists = entry.value.toMutableList()
                lists.removeIf { item ->
                    conflictAnnotations.firstOrNull {
                        it.id.toString() == item.uuid
                    } != null
                }
                if (lists.size != entry.value.size) {
                    entry.setValue(lists)
                }
            }
        }

        val readerTexts = curTextChapter?.readerTexts ?: return
        val texttags = annotation?.toTextTags(durChapterIndex, readerTexts).orEmpty()
        if (texttags.isNotEmpty()) {
            val keys = tags.keys.plus(texttags.keys)
            for(key in keys) {
                tags[key] = (tags[key].orEmpty()).toMutableList().plus(texttags[key].orEmpty())
            }
        }
        curTextChapter?.annotations = tags
        callBack?.upContent(resetPageOffset = false)
    }

    private suspend fun loadContent(chapterIndex: Int, upContent: Boolean = true, resetPageOffset: Boolean) {
//        Logger.i("PageViewController::loadContent:index=$index,upContent=$upContent,resetPageOffset=$resetPageOffset,bookid=${book?.id},bookname=${book?.title}")
        if (chapterIndex < 0) return
        val curBook = book ?: return
        val bookId = curBook.id
        Logger.i("PageViewController::loadContent:index=$chapterIndex,bookId=$bookId")

        val chapter = try {
            getChapterByIdUserCase(bookId, chapterIndex).first()
        } catch (ex: NoSuchElementException) {
            Logger.e("PageViewController::${ex.message}, failed")
            if (isInitFinish) {
                onInitChapterLoadListener?.invoke(false)
                onInitChapterLoadListener = null
            }
            return
        }
        BookHelper.loadChapterContent(context, curBook, chapter, textParser).let { contents ->
            Logger.i("PageViewController::loadContent:index=$chapterIndex, contents.size=${contents.size}")

            var tags = hashMapOf<Int, List<TextTag>>()  //章节全部标签信息
            contents.forEachIndexed { index, content ->
                if (content is ReaderText.Text) {
                    if (content.annotations.isNotEmpty()) {
                        tags[index] = content.annotations
                    }
                }
            }
            //将BookAnnotation转换成TextTag,控制界面的显示
            userAnnotations?.forEach { anno ->
                val texttags = anno.toTextTags(chapterIndex, contents)
                if (texttags.isNotEmpty()) {
                    val keys = tags.keys.plus(texttags.keys)
                    for(key in keys) {
                        tags[key] = (tags[key].orEmpty()).toMutableList().plus(texttags[key].orEmpty())
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

    fun getSelectedLocator(): Locator? {
        return if (durChapterIndex >= 0 &&
            startParagraphIndex >= 0 &&
            endParagraphIndex >= 0 &&
            startInnerTextOffset >= 0 &&
            endInnerTextOffset >= 0
        ) {
            Locator(
                "",
                durChapterIndex,
                startParagraphIndex = startParagraphIndex,
                startTextOffset = startInnerTextOffset,
                endParagraphIndex = endParagraphIndex,
                endTextOffset = endInnerTextOffset,
                text = getSelectedText()
            )
        } else {
            null
        }
    }

    /****
     * 设置屏幕常亮
     */
    override fun screenOffTimerStart() {
//        Logger.i("PageViewController::screenOffTimerStart")
    }

    override fun showTextActionMenu() {
        Logger.i("PageViewController::showTextActionMenu")
        clickListener?.onSelectedText(selectedStartX, selectedStartTop + ChapterProvider.paddingTop, selectedEndX, selectedEndY + ChapterProvider.paddingTop)
    }

    //选中的位置
    private var selectedStartX: Float = 0.0f
    private var selectedStartY: Float = 0.0f
    private var selectedStartTop: Float = 0.0f
    private var selectedEndX : Float = 0f
    private var selectedEndY : Float = 0f

    private var startParagraphIndex: Int = -1
    private var startInnerTextOffset : Int =-1
    private var endParagraphIndex : Int = -1
    private var endInnerTextOffset: Int = -1

    override fun upSelectedStart(x: Float, y: Float, top: Float, paragraphIndex: Int, innerTextOffset: Int) {
        selectedStartX = x
        selectedStartY = y
        selectedStartTop = top
        startParagraphIndex = paragraphIndex
        startInnerTextOffset = innerTextOffset
        Logger.i("PageViewController::upSelectedStart:x=$x,y=$y,top=$top")
    }

    override fun upSelectedEnd(x: Float, y: Float, paragraphIndex: Int, innerTextOffset: Int) {
        Logger.i("PageViewController::upSelectedEnd:x=$x,y=$y")
        selectedEndX = x
        selectedEndY = y
        endParagraphIndex = paragraphIndex
        endInnerTextOffset = innerTextOffset
    }

    fun cancelTextSelected() {
        Logger.i("PageViewController::cancelTextSelected")
        callBack?.cancelTextSelected()
    }

    override fun onCancelSelect() {
        Logger.i("PageViewController::onCancelSelect")
        clickListener?.onSelectedCancel()
        selectedStartX = 0f
        selectedStartY = 0f
        selectedStartTop = 0f
        selectedEndX = 0f
        selectedEndY = 0f
        startParagraphIndex = -1
        startInnerTextOffset = -1
        endParagraphIndex = -1
        endInnerTextOffset = -1
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

    override fun clickedAnnotation(annotationIds: List<String>) {
        val curChapter = curTextChapter ?: return
        val curPage = pageFactory?.currentPage ?: return
        val pendingRange = arrayListOf<Triple<Int, Int, Int>>()

        //找到相应的标注所对应的段落和文字开始结束偏移位置，保存成集合
        curChapter.annotations.let { tagMap ->
            for(entity in tagMap) {
                val paragraphIndex = entity.key
                entity.value.filter { annotationIds.contains(it.uuid) }.forEach { annoTag ->
                    val startOffset = annoTag.start
                    val endOffset = annoTag.end
                    pendingRange.add(Triple(paragraphIndex, startOffset, endOffset))
                }
            }
        }

        if(pendingRange.isNotEmpty()) {
            //遍历得到tag对应的选中文本的开始字符屏幕位置和结束位置屏幕位置
            var startX = -1f
            var startY = -1f
            var endX = -1f
            var endY = -1f
            var lastCh : TextChar? = null
            var lastLine : TextLine? = null
            //遍历当前页中的每一行，找到对应标注的开始字符和结束字符
            curPage.textLines.forEach { line ->
                //当前行包含在给定的标注范围内
                val range = pendingRange.firstOrNull {
                    line.paragraphIndex == it.first
                }
                if (range != null) {
                    val startOffset = range.second
                    val endOffset = range.third

                    for ((index, ch) in line.textChars.withIndex()) {
                        if (!ch.isImage && ch.charData.isNotEmpty() && ch.charData.length == 1) {
                            val charIndexInParagraph = line.charStartOffset + index
                            if (charIndexInParagraph >= startOffset && charIndexInParagraph < endOffset) {
                                ch.selected = true
                                if (startX < 0f && startY < 0f) {
                                    startX = ch.start
                                    startY = line.lineTop
                                }
                                lastCh = ch
                                lastLine = line
                            }
                        }
                    }
                }
            }
            if (lastCh != null && lastLine != null) {
                endX = lastCh.end
                endY = lastLine.lineBottom
            }
            if (startX > 0f && startY > 0f && endX > 0f && endY > 0f) {
                Logger.d("PageViewController::clickedAnnotation::startX=$startX,startY=$startX,endX=$startX,endY=$endY")
                callBack?.upSelectedRange(startX, startY, endX, endY)
                callBack?.upContent(resetPageOffset = false)
                clickListener?.onCheckedAnnotation(annotationIds, startX, startY, endX, endY)
            }
        }
    }

    fun clear() {
        scope?.launchIO {
            book?.let {
                BookHelper.closeBook(context, it, textParser)
            }
            book = null
        }
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