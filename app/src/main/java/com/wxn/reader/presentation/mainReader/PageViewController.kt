package com.wxn.reader.presentation.mainReader

import android.content.Context
import android.graphics.RectF
import com.wxn.base.bean.Book
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
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.ui.PageCallback
import com.wxn.bookread.ui.PageViewCallback
import com.wxn.bookread.ui.PageViewDataProvider
import com.wxn.bookread.ui.SelectTextCallback
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.base.bean.Bookmark
import com.wxn.bookread.ext.calcProgress
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.model.toTextTags
import com.wxn.reader.domain.use_case.annotations.GetAnnotationsUseCase
import com.wxn.reader.domain.use_case.bookmarks.GetBookmarksForBookUseCase
import com.wxn.reader.domain.use_case.books.UpdateBookUseCase
import com.wxn.reader.domain.use_case.chapters.BookHelper
import com.wxn.reader.domain.use_case.chapters.GetChapterByIdUserCase
import com.wxn.reader.domain.use_case.chapters.GetChapterCountByBookIdUserCase
import com.wxn.reader.domain.use_case.chapters.UpdateChapterWordCountUserCase
import com.wxn.reader.domain.use_case.notes.GetNotesForBookUseCase
import com.wxn.reader.util.tts.TtsNavigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.io.Reader
import javax.inject.Inject
import kotlin.collections.firstOrNull
import kotlin.collections.iterator
import kotlin.collections.orEmpty
import kotlin.collections.set
import kotlin.math.roundToInt

open class PageViewController @Inject constructor(
    val context: Context,
    val getChapterByIdUserCase: GetChapterByIdUserCase,
    val getChapterCountByBookIdUserCase: GetChapterCountByBookIdUserCase,

    val getAnnotationsUseCase: GetAnnotationsUseCase,
    val getNotesForBookUseCase: GetNotesForBookUseCase,
    val getBookmarksForBookUseCase: GetBookmarksForBookUseCase,

    val updateChapterWordCountUserCase: UpdateChapterWordCountUserCase,
    val updateBookUseCase: UpdateBookUseCase,
    val appPreferencesUtil: AppPreferencesUtil,
    val textParser: TextParser
) : PageViewDataProvider, PageViewCallback, SelectTextCallback {

    var scope: CoroutineScope? = null
//    var titleDate = MutableLiveData<String>()

    override var book: Book? = null
    var userAnnotations : ArrayList<BookAnnotation>? = null
    var userNotes : ArrayList<Note>? = null
    var userBookmakrs: ArrayList<Bookmark>? = null

    var inBookshelf = false

    /***
     * 通过durChapterPos() 方法获得 页面索引，而不要直接使用这个属性
     */
    var durPageIndex = 0
    var targetProgress: Double = -1.0 //临时保存更改的进度，默认0.0, 不作为正常进度使用

    //    var isLocalBook = true
    var callBack: PageCallback? = null
    var prevTextChapter: TextChapter? = null

    /***
     * 通过textChapter() 来获得对应的章节，不用直接使用当前属性
     */
    var curTextChapter: TextChapter? = null
    var nextTextChapter: TextChapter? = null
    override var msg: String? = null            //对应章节名？

    interface OnClickListener {
        fun onCenterClick()
        fun onLinkClick(href: String?, clickX: Float, clickY: Float)
        fun onPageChange()
        fun onSelectedText(startX: Float, startY : Float, endX : Float, endY : Float)
        fun onSelectedCancel()
        fun onCheckedAnnotation(annotationIds: List<String>, rect: RectF)
        fun onCheckedNote(noteId: String, rect: RectF)
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

        userNotes?.clear()
        userNotes = arrayListOf()
        val notes = getNotesForBookUseCase(book.id).firstOrNull()
        if (!notes.isNullOrEmpty()) {
            userNotes?.addAll(notes)
        }

        userBookmakrs?.clear()
        userBookmakrs = arrayListOf()
        val bookmarks = getBookmarksForBookUseCase(book.id).firstOrNull()
        if (!bookmarks.isNullOrEmpty()) {
            userBookmakrs?.addAll(bookmarks)
        }
        Logger.d("PageViewController::resetBook:[${book.id}],userBokmarks[${userBookmakrs?.size}]")

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

    suspend fun updateChapterByAddBookmark(addedBookmark: Bookmark):Boolean {
        if (userBookmakrs == null) {
            userBookmakrs = arrayListOf()
        }
        userBookmakrs?.add(addedBookmark)
        return innerUpdateChapterHandleBookmark()
    }

    suspend fun updateChapterByDelBookmark(deledBookmark: Bookmark) : Boolean {
        if (userBookmakrs == null) {
            userBookmakrs = arrayListOf()
        }
        userBookmakrs?.remove(deledBookmark)
        return innerUpdateChapterHandleBookmark()
    }

    private suspend fun innerUpdateChapterHandleBookmark() : Boolean {
        val textChapter = curTextChapter ?: return false
        val chapterIndex = textChapter.position
        //遍历当前章节的书签
        val chapterBookmarks = userBookmakrs?.filter {
            it.chapterIndex == chapterIndex
        }
        Logger.d("PageViewController::loadContent[$chapterIndex],chapterBookmarks[${chapterBookmarks?.size}]")

        textChapter.pages.forEach { page ->
            page.bookmarkId = getPageBookmark(page, textChapter, chapterBookmarks)?.id ?: -1
        }
        callBack?.upContent(resetPageOffset = false)
        return true
    }

    /***
     * update note then update chapter
     */
    suspend fun updateChapterByUpdateNote(note: Note) {
        val tags = curTextChapter?.annotations?.toMutableMap() ?: return
        val readerTexts = curTextChapter?.readerTexts ?: return
        for(entry in tags) {
            val lists = entry.value.toMutableList()
            lists.removeIf { item ->
                note.id.toString() == item.uuid && item.name == "note"
            }
            if (lists.size != entry.value.size) {
                entry.setValue(lists)
            }
        }

        val texttags = note.locatorInfo?.toTextTags(
            note.id.toString(),
            "note",
            note.color,
            durChapterIndex,
            readerTexts
        ).orEmpty()
        if (texttags.isNotEmpty()) {
            val keys = tags.keys.plus(texttags.keys)
            for(key in keys) {
                tags[key] = (tags[key].orEmpty()).toMutableList().plus(texttags[key].orEmpty())
            }
        }
        curTextChapter?.annotations = tags
        callBack?.upContent(resetPageOffset = false)
    }

    /****
     * refresh view of chapter
     * @param annotation add to TextChapter
     * @param conflictAnnotations delete from TextChapter
     */
    suspend fun updateChapter(annotation: BookAnnotation?, addNote: Note?, deleteNote: Note?, conflictAnnotations: List<BookAnnotation>) {
        val tags = textChapter(0)?.annotations?.toMutableMap()
        if (tags == null) {
            Logger.e("${this.javaClass.name}::updateChapter::tags is null")
        }

        if (conflictAnnotations.isNotEmpty() && !tags.isNullOrEmpty()) {
            for(entry in tags) {
                val lists = entry.value.toMutableList()
                lists.removeIf { item ->
                    conflictAnnotations.firstOrNull {
                        it.id.toString() == item.uuid && it.type.toString() == item.name
                    } != null
                }
                if (lists.size != entry.value.size) {
                    entry.setValue(lists)
                }
            }
        }

        if (deleteNote != null && !tags.isNullOrEmpty()) {
            for(entry in tags) {
                val lists = entry.value.toMutableList()
                lists.removeIf { item ->
                    deleteNote.id.toString() == item.uuid && item.name == "note"
                }
                if (lists.size != entry.value.size) {
                    entry.setValue(lists)
                }
            }
        }

        val readerTexts = textChapter(0)?.readerTexts
        if (readerTexts != null) {
            val texttags = annotation?.locatorInfo?.toTextTags(
                annotation.id.toString(),
                annotation.type.toString(),
                annotation.color,
                durChapterIndex, readerTexts).orEmpty()
            if (texttags.isNotEmpty() && !tags.isNullOrEmpty()) {
                val keys = tags.keys.plus(texttags.keys)
                for(key in keys) {
                    tags[key] = (tags[key].orEmpty()).toMutableList().plus(texttags[key].orEmpty())
                }
            }

            val noteTextTags = addNote?.locatorInfo?.toTextTags(
                addNote.id.toString(),
                "note",
                addNote.color,
                durChapterIndex,
                readerTexts
            ).orEmpty()
            if (noteTextTags.isNotEmpty() && !tags.isNullOrEmpty()) {
                val keys = tags.keys.plus(noteTextTags.keys)
                for(key in keys) {
                    tags[key] = (tags[key].orEmpty()).toMutableList().plus(noteTextTags[key].orEmpty())
                }
            }
        }

        curTextChapter?.annotations = tags.orEmpty()
        callBack?.upContent(resetPageOffset = false)
    }

    private fun getPageBookmark(textPage: TextPage, chapter: TextChapter, chapterBookmarks: List<Bookmark>?):Bookmark? {
        if (chapterBookmarks.isNullOrEmpty()) {
//            Logger.d("TextPageFactory::getPageBookmark:: current chapter[${chapter.position}] bookmarks is empty.")
            return null
        } else {
//            Logger.d("TextPageFactory::getPageBookmark:: current chapter[${chapter.position}] bookmarks.size=${chapterBookmarks.size}.")
        }
        var pageStartParagraphIndex = 0
        var pageStartParagraphTextOffset = 0
        var pageEndParagraphIndex  = 0
        var pageEndParagraphTextOffset = 0

        val firstLine = textPage.textLines.firstOrNull()
        val lastLine = textPage.textLines.lastOrNull()

        pageStartParagraphIndex = firstLine?.paragraphIndex ?: 0
        pageStartParagraphTextOffset = firstLine?.charStartOffset ?: 0
        pageEndParagraphIndex = lastLine?.paragraphIndex ?: 0
        pageEndParagraphTextOffset = lastLine?.charEndOffset ?: 0
        Logger.d("TextPageFactory::getPageBookmark::pageStartParagraphIndex=${pageStartParagraphIndex},pageEndParagraphIndex=${pageEndParagraphIndex}," +
                "pageStartParagraphTextOffset=$pageStartParagraphTextOffset,pageEndParagraphTextOffset=$pageEndParagraphTextOffset")

        var targetMark : Bookmark? = null
        for(mark in chapterBookmarks) {
            val locator = mark.locatorInfo ?: continue
            val paragraphIndex = locator.startParagraphIndex
            val textOffset = locator.startTextOffset
            Logger.d("TextPageFactory::getPageBookmark::locator=${locator}")

            if (paragraphIndex < pageStartParagraphIndex || paragraphIndex > pageEndParagraphIndex) {
                continue
            }

            if (paragraphIndex == pageStartParagraphIndex && textOffset >= pageStartParagraphTextOffset) {
                targetMark = mark
                break
            } else if (paragraphIndex == pageEndParagraphIndex && textOffset <= pageEndParagraphTextOffset) {
                targetMark = mark
                break
            } else if (paragraphIndex > pageStartParagraphIndex && paragraphIndex < pageEndParagraphIndex) {
                targetMark = mark
                break
            }
        }
        return targetMark
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
            Logger.i("PageViewController::loadContent:index=$chapterIndex,chapter.index=${chapter.chapterIndex} contents.size=${contents.size}")

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
                val texttags = anno.locatorInfo?.toTextTags(
                    anno.id.toString(),
                    anno.type.toString(),
                    anno.color,
                    chapterIndex, contents)
                if (!texttags.isNullOrEmpty()) {
                    val keys = tags.keys.plus(texttags.keys)
                    for(key in keys) {
                        tags[key] = (tags[key].orEmpty()).toMutableList().plus(texttags[key].orEmpty())
                    }
                }
            }
            //将Note转换成TextTag，控制界面显示
            userNotes?.forEach { note ->
                val texttags = note.locatorInfo?.toTextTags(
                    note.id.toString(),
                    "note",
                    note.color,
                    chapterIndex, contents)
                if (!texttags.isNullOrEmpty()) {
                    val keys = tags.keys.plus(texttags.keys)
                    for(key in keys) {
                        tags[key] = (tags[key].orEmpty()).toMutableList().plus(texttags[key].orEmpty())
                    }
                }
            }

            //遍历当前章节的书签
            val chapterBookmarks = userBookmakrs?.filter {
                it.chapterIndex == chapterIndex
            }
            Logger.d("PageViewController::loadContent[$chapterIndex],chapterBookmarks[${chapterBookmarks?.size}]")

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

            textChapter?.pages?.forEach { page ->
                page.bookmarkId = getPageBookmark(page, textChapter, chapterBookmarks)?.id ?: -1
            }

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
//        Logger.i("PageViewController::durChapterPos")
        curTextChapter?.let {
            if (durPageIndex < it.pageSize) {
                return durPageIndex
            }
            return it.pageSize - 1
        }
//        Logger.i("PageViewController::durChapterPos::durPageIndex=$durPageIndex")
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
     * 获取当前界面上，第一行的文字的Locator
     * 如果是图片，一样处理
     */
    fun getCurrentPageLocator(): Locator? {
        val chapterIndex = durChapterIndex
        val curChapter = textChapter(0) ?: return null
        val pageIndex = durPageIndex
        val curPage = curChapter.pages.getOrNull(pageIndex) ?: return null
        val curLine = curPage.textLines.firstOrNull()
        val curProgression  = progression
        return Locator(
            id = "",
            chapterIndex = chapterIndex,
            startParagraphIndex = curLine?.paragraphIndex ?: 0,
            startTextOffset = curLine?.charStartOffset ?: 0,
            endParagraphIndex = curLine?.paragraphIndex ?: 0,
            endTextOffset = curLine?.charEndOffset ?: 0,
            text = curLine?.text ?: "",
            progression = curProgression
        )
    }

    fun getSelectedLocator(): Locator? {
        return if (durChapterIndex >= 0 &&
            startParagraphIndex >= 0 &&
            endParagraphIndex >= 0 &&
            startInnerTextOffset >= 0 &&
            endInnerTextOffset >= 0
        ) {
            val progress = curTextChapter.calcProgress(startParagraphIndex, startInnerTextOffset)
            Locator(
                "",
                durChapterIndex,
                startParagraphIndex = startParagraphIndex,
                startTextOffset = startInnerTextOffset,
                endParagraphIndex = endParagraphIndex,
                endTextOffset = endInnerTextOffset,
                text = getSelectedText(),
                progression = progress
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
    }

    override fun upSelectedEnd(x: Float, y: Float, paragraphIndex: Int, innerTextOffset: Int) {
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

    override fun clickedNote(noteId: String) {
        Logger.i("PageViewController::clickNote::noteId=$noteId")
        val curChapter = curTextChapter ?: return
        val curPage = pageFactory?.currentPage ?: return
        val pendingRange = arrayListOf<Triple<Int, Int, Int>>()

        curChapter.annotations.let { tagMap ->
            for(entity in tagMap) {
                val paragraphIndex = entity.key
                entity.value.filter { noteId == it.uuid && it.name == "note" }.forEach { annoTag ->
                    val startOffset = annoTag.start
                    val endOffset = annoTag.end
                    pendingRange.add(Triple(paragraphIndex, startOffset, endOffset))
                }
            }
        }
        innerSelectText(pendingRange, curPage) { rect ->
            clickListener?.onCheckedNote(noteId, rect)
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
                entity.value.filter {
                    annotationIds.contains(it.uuid) && (it.name == "underline" || it.name == "highlight")
                }.forEach { annoTag ->
                    val startOffset = annoTag.start
                    val endOffset = annoTag.end
                    pendingRange.add(Triple(paragraphIndex, startOffset, endOffset))
                }
            }
        }

        innerSelectText(pendingRange, curPage) { rect ->
            clickListener?.onCheckedAnnotation(annotationIds, rect)
        }
    }

    private fun innerSelectText(pendingRange: List<Triple<Int, Int, Int>>, curPage: TextPage, onFinished: (RectF)->Unit) {
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
                            if (charIndexInParagraph >= startOffset && charIndexInParagraph <= endOffset) {
                                ch.selected = true
                                if (startX < 0f && startY < 0f) {
                                    startX = ch.start
                                    startY = line.lineTop + ChapterProvider.paddingTop
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
                endY = lastLine.lineBottom + ChapterProvider.paddingTop
            }
            if (startX > 0f && startY > 0f && endX > 0f && endY > 0f) {
                Logger.d("PageViewController::clickedAnnotation::startX=$startX,startY=$startY,endX=$endX,endY=$endY")
//                callBack?.upSelectedRange(startX, startY, endX, endY)
                callBack?.upContent(resetPageOffset = false)
//                clickListener?.onCheckedAnnotation(annotationIds, startX, startY, endX, endY)
                onFinished(RectF(startX, startY, endX, endY))
            }
        }
    }

    fun currentPage() : TextPage? = textChapter(0)?.page(durChapterPos())

    fun stopReadPage() {
        scope?.launchIO {
            val chapter = textChapter(0) ?: return@launchIO
            for(page in chapter.pages) {
                val currentTextLines = page.textLines
                if (currentTextLines.isNotEmpty()) {
                    for (textLine in currentTextLines) {
                        textLine.isReadAloud = false
                    }
                }
            }
            with(Dispatchers.Main) {
                callBack?.upContent()
            }
        }
    }

    fun readPage(ttsNavigator: TtsNavigator, onFinish:()->Unit) {
        Logger.i("PageViewController::readPage::durChapterIndex=${durChapterIndex},durPageIndex=$durPageIndex")
        var status = 1
        scope?.launchIO {
            do {
                var textLines : List<TextLine>? = currentPage()?.textLines
                status = ttsNavigator.play(textLines) { targetLines, status ->
                    val currentTextLines = currentPage()?.textLines ?: return@play
                    if (currentTextLines.isNotEmpty()) {
                        for (textLine in currentTextLines) {
                            textLine.isReadAloud = false
                            if (targetLines.contains(textLine)) {
                                textLine.isReadAloud = status
                                Logger.d("PageViewController::readPage::line[${textLine.text}]::set readAloud::status[$status]")
                            }
                        }
                    }
                    callBack?.upContent()
                }
                if (status == 1) {
                    Logger.d("MainReadViewModel::ttsPlay::then moveToNextPage or moveToNextChapter")
                    with(Dispatchers.Main) {
                        moveToNextPage()
                        val curChapter = textChapter(0)
                        if (curChapter != null) {
                            if (durPageIndex >= curChapter.pageSize) {
                                moveToNextChapter(true)
                            }
                        } else {
                            status = 0
                        }
                        delay(200)
                    }
//                } else if (status < 0) {
//                    ToastUtil.show("Language not suppport.")
                } else {
                    Logger.d("MainReadViewModel::ttsPlay::status=$status")

                }
            } while(status == 1)
            onFinish()
        }
    }

    /***
     * update view after modify preference
     */
    fun updatePageViews() {
        ChapterProvider.upStyle(context) {
            loadContent(true)
            callBack?.upContent()
            callBack?.upStyle()
            callBack?.upTipStyle()
            callBack?.upBg()
            callBack?.upPageAnim()
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