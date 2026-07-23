package com.wxn.reader.presentation.mainReader

import android.content.Context
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.wxn.base.bean.Book
import com.wxn.base.bean.Bookmark
import com.wxn.base.bean.Locator
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.bean.TextTag
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.bookparser.TextParser
import com.wxn.bookread.data.model.SpeekBookStatus
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.provider.ImageProvider
import com.wxn.bookread.ui.PageCallback
import com.wxn.bookread.ui.PageViewCallback
import com.wxn.bookread.ui.PageViewDataProvider
import com.wxn.bookread.ui.SelectTextCallback
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.model.BookAnnotation
import com.wxn.reader.domain.model.Note
import com.wxn.reader.domain.model.toTextTags
import com.wxn.reader.domain.use_case.annotations.GetAnnotationsUseCase
import com.wxn.reader.domain.use_case.bookmarks.GetBookmarksForBookUseCase
import com.wxn.reader.domain.use_case.books.UpdateProgressFieldsUseCase
import com.wxn.reader.domain.use_case.books.UpdateWordCountUseCase
import com.wxn.reader.domain.use_case.chapters.BookHelper
import com.wxn.reader.domain.use_case.chapters.GetChapterByIdUserCase
import com.wxn.reader.domain.use_case.chapters.GetChapterCountByBookIdUserCase
import com.wxn.reader.domain.use_case.chapters.UpdateChapterWordCountUserCase
import com.wxn.reader.domain.use_case.notes.GetNotesForBookUseCase
import com.wxn.reader.service.TtsStateHolder
import com.wxn.reader.util.TtsServiceController
import com.wxn.base.bean.TtsPlaybackStatus
import com.wxn.base.ext.statusBarHeight
import com.wxn.bookread.ext.getPageIndexFromLocator
import com.wxn.bookread.ext.orZero
import com.wxn.reader.presentation.mainReader.helpers.JumpHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt


@Singleton
class PageViewController @OptIn(UnstableApi::class)
@Inject constructor(
    override val context: Context,
    val getChapterByIdUserCase: GetChapterByIdUserCase,
    val getChapterCountByBookIdUserCase: GetChapterCountByBookIdUserCase,

    val getAnnotationsUseCase: GetAnnotationsUseCase,
    val getNotesForBookUseCase: GetNotesForBookUseCase,
    val getBookmarksForBookUseCase: GetBookmarksForBookUseCase,

    val updateChapterWordCountUserCase: UpdateChapterWordCountUserCase,
    val updateProgressFieldsUseCase: UpdateProgressFieldsUseCase,
    val updateWordCountUseCase: UpdateWordCountUseCase,

    val appPreferencesUtil: AppPreferencesUtil,
    val textParser: TextParser,
    override var ttsStateHolder: TtsStateHolder,
    override var ttsServiceController: TtsServiceController,
) : TTSController(context, ttsStateHolder, ttsServiceController), PageViewDataProvider,
    PageViewCallback, SelectTextCallback {

    override var book: Book? = null
    var userAnnotations: ArrayList<BookAnnotation>? = null
    var userNotes: ArrayList<Note>? = null
    var userBookmakrs: ArrayList<Bookmark>? = null

    var inBookshelf = false

    /***
     * 通过durChapterPos() 方法获得 页面索引，而不要直接使用这个属性
     */
    var durPageIndex = 0

    @kotlin.concurrent.Volatile
    var targetProgress: Double = -1.0 //临时保存更改的进度，默认0.0, 不作为正常进度使用

    @kotlin.concurrent.Volatile
    var targetLocator: Locator? = null  //临时保存更改的进度，

    @kotlin.concurrent.Volatile
    var pendingAnchorId: String? = null

    var searchedLocators: List<Locator> = emptyList()

    //    var isLocalBook = true
    var callBack: PageCallback? = null
    var prevTextChapter: TextChapter? = null

    /***
     * 通过textChapter() 来获得对应的章节，不用直接使用当前属性
     */
    var curTextChapter: TextChapter? = null
    var nextTextChapter: TextChapter? = null
    override var msg: String? = null            //对应章节名？

    private val loadChapterDispatcher = Dispatchers.IO.limitedParallelism(1)

    interface OnClickListener {
        fun onCenterClick()
        fun hideMenu()

        fun showMenu()
        fun onLinkClick(href: String?, clickX: Float, clickY: Float)
        fun onPageChange()
        fun onSelectedText(startX: Float, startY: Float, endX: Float, endY: Float)
        fun onSelectedCancel()
        fun onCheckedAnnotation(annotationIds: List<String>, rect: RectF)
        fun onCheckedNote(noteId: String, rect: RectF)

        suspend fun updateReadingTime(force: Boolean = false)

        fun onTtsPlayStatus(ttsPlayStatus: TtsPlaybackStatus)

        fun showTimerExpired()

        fun onContinuousScrollLoadingChanged(isLoading: Boolean) {}
    }

    var clickListener: OnClickListener? = null

    interface OnNavigationLoadingListener {
        fun onNavigationLoadingStart(targetChapterIndex: Int, immediate: Boolean = false)
        fun onNavigationLoadingComplete(chapterIndex: Int)
        fun onNavigationLoadingError(chapterIndex: Int)
    }

    var navigationLoadingListener: OnNavigationLoadingListener? = null

    /***
     * 当前显示的章节索引
     */
    @Volatile
    override var durChapterIndex: Int = 0

    override var headerHeight: Int = context.statusBarHeight

    /***
     * 章节数
     */
    override var chapterSize: Int = 0

    @Volatile
    override var isInitFinish: Boolean = false

    /**
     * 版本锁：使用 AtomicInteger 保证原子递增操作
     * 防止多线程竞态条件导致的重复加载问题
     */
    private val contentLoadVersion = AtomicInteger(0)

    private val ownerTokenGenerator = AtomicLong(0L)
    private val currentOwnerToken = AtomicLong(0L)

    fun isOwner(token: Long): Boolean = token > 0 && currentOwnerToken.get() == token

    /**
     * 追踪当前正在执行的内容加载任务
     * 用于在新加载请求到来时取消之前的任务，避免重复工作和内存泄漏
     */
    private var loadContentJob: Job? = null

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
    suspend fun calcChaptersWords() {
        val curBook = book ?: return
        isCalcChapterWords = true
        val start = System.currentTimeMillis()
        val chapterIndexWords: ArrayList<Triple<Int, Int, Int>> = arrayListOf()
        val wordCountTriple = BookHelper.loadWordCount(context, curBook, textParser)
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
            curBook.wordCount = totalWordCount.toLong()
            for (item in chapterIndexWords) {
                val progress = progressWordCount.toFloat() / totalWordCount
                val wordCount = item.second
                val picCount = item.third
                val count = wordCount + picCount
                val chapterIndex = item.first - 1
                updateChapterWordCountUserCase.invoke(
                    curBook.id,
                    chapterIndex,
                    wordCount.toLong(),
                    picCount.toLong(),
                    progress
                )
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
            updateWordCountUseCase(curBook.id, curBook.wordCount)
        }
        isCalcChapterWords = false
        Logger.d("PageViewController::calcChapterWords:totalWordCount=${totalWordCount}, spend=${System.currentTimeMillis() - start}")
    }

    suspend fun resetBook(book: Book, initChapterLoadListener: ((Boolean) -> Unit)): Long {
        val token = ownerTokenGenerator.incrementAndGet()
        currentOwnerToken.set(token)
        Logger.i("PageViewController::resetBook:book=$book")
        contentLoadVersion.set(0)  // 原子操作重置版本锁
        loadContentJob?.cancel()  // 取消之前的加载任务
        loadContentJob = null
        this.prevTextChapter = null
        this.curTextChapter = null
        this.nextTextChapter = null
        chapterSize = 0
        durChapterIndex = 0
        isScroll = false
        isInitFinish = false

        this.book = book
        // F1 修订：字段提前到 try 之前赋值，确保 catch 块能安全触发回调（避免静默失效导致永久 spinner）
        onInitChapterLoadListener = initChapterLoadListener
        val count = try {
            getChapterCountByBookIdUserCase(book.id).first()
        } catch (ex: NoSuchElementException) {
            Logger.e("PageViewController::resetBook:${ex.message}, failed")
            onInitChapterLoadListener?.invoke(false)
            return token
        }

        userAnnotations?.clear()
        userAnnotations = arrayListOf()
        try {
            val annotations: List<BookAnnotation> = getAnnotationsUseCase(book.id).first()
            if (annotations.isNotEmpty()) {
                userAnnotations?.addAll(annotations)
            }
        } catch (ex: NoSuchElementException) {
            Logger.e("PageViewController::resetBook2:${ex.message}, failed")
            onInitChapterLoadListener?.invoke(false)
            return token
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

        searchedLocators = emptyList()
        this.chapterSize = count
        durChapterIndex = book.scrollIndex
        durPageIndex = book.scrollOffset
        Logger.d("PageViewController::resetBook:chapterSize=$chapterSize, durChapterIndex=$durChapterIndex")
        isInitFinish = true
        Logger.d("PageViewController::resetBook:isInitFinish=$isInitFinish")
        loadContent(true)
        return token
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

    /***
     * 跳转到当前章节的起始页（第0页）
     */
    fun gotoChapterStart() {
        Logger.i("PageViewController::gotoChapterStart")
        setPageIndex(0)
        callBack?.upContent()
    }

    override fun changeChapter(newChapterIndex: Int, newProgress: Double): Boolean {
        Logger.i("PageViewController::changeChapter:newChapterIndex=$newChapterIndex, newProgress=$newProgress")
        if (durChapterIndex != newChapterIndex) {
            durChapterIndex = newChapterIndex
            durPageIndex = 0
        }
        if (newProgress >= 0.0) {
            val curChapter = curTextChapter ?: return false
            if (curChapter.totalWordCount == 0L) {
                Logger.e("PageViewController::changeChapter failed, no word count info")
                return false
            }

            Logger.i("PageViewController::changeChapter:set targetProgress:$newProgress")
            targetProgress = newProgress
        }
        navigationLoadingListener?.onNavigationLoadingStart(newChapterIndex, immediate = false)
        loadContent(true)
        return true
    }

    override fun changeChapterAndPage(newChapterIndex: Int, locator: Locator?) {
        Logger.i("PageViewController::changeChapterAndPage:newChapterIndex=$newChapterIndex,locator=$locator")
        if (durChapterIndex != newChapterIndex) {
            Logger.i("PageViewController::changeChapterAndPage:change Chapter to $newChapterIndex, curChapterIndex=$durChapterIndex")
            navigationLoadingListener?.onNavigationLoadingStart(newChapterIndex, immediate = false)
            durChapterIndex = newChapterIndex
            durPageIndex = 0

            targetLocator = locator
            loadContent(true)
        } else {
            if (locator != null) {
                val targetPageIndex = curTextChapter?.getPageIndexFromLocator(locator) ?: -1
                Logger.i("PageViewController::changeChapterAndPage:same chapter then change targetPageIndex=$targetPageIndex")
                if (targetPageIndex >= 0 && targetPageIndex < (curTextChapter?.pageSize?:0)) {
                    setPageIndex(targetPageIndex)
                    callBack?.upContent()
                }
            }
        }
    }

    /***
     * 跳到其他章节-有锚点，
     * 则先跳转到章节，
     * 然后在解析到章节数据之后，再跳转到目标锚点页面位置
     */
    fun changeChapterWithAnchor(newChapterIndex: Int, anchorId: String) {
        Logger.i("PageViewController::changeChapterWithAnchor:newChapterIndex=$newChapterIndex, anchorId=$anchorId")
        if (durChapterIndex != newChapterIndex) {
            durChapterIndex = newChapterIndex
            durPageIndex = 0
        }
        pendingAnchorId = anchorId
        navigationLoadingListener?.onNavigationLoadingStart(newChapterIndex, immediate = false)
        loadContent(true)
    }

    /***
     * 在当前章节中根据定位锚点，找到对应的段落，以及页面索引，跳转到对应的页面
     * @return 成功设置并跳转，返回true，否则返回false
     */
    fun locateAnchorInCurrentChapter(anchorId: String): Boolean {
        Logger.i("PageViewController::locateAnchorInCurrentChapter:anchorId=$anchorId")
        val texts = curTextChapter?.readerTexts
        if (texts.isNullOrEmpty()) {
            Logger.w("PageViewController::locateAnchorInCurrentChapter:curTextChapter or readerTexts is null/empty")
            return false
        }
        val paraIndex = JumpHelper.findAnchorParagraphIndex(texts, anchorId)
        val pageSize = (curTextChapter?.pageSize ?: 0)
        if (paraIndex >= 0) {
            val locator = Locator(
                chapterIndex = durChapterIndex,
                startParagraphIndex = paraIndex,
                startTextOffset = 0,
                progression = 0.0)
            //根据 段落索引，找到目标页面索引
            val targetPageIndex = curTextChapter?.getPageIndexFromLocator(locator) ?: -1
            if (targetPageIndex in 0..<pageSize) {
                //设置页面索引
                setPageIndex(targetPageIndex)
                callBack?.upContent() //更新显示，跳转到目标页面
                Logger.d("PageViewController::locateAnchorInCurrentChapter:jumped to page=$targetPageIndex")
                return true
            }
        } else {
            Logger.w("PageViewController::locateAnchorInCurrentChapter:anchor $anchorId not found in current chapter, to chapter start part##")
            if (pageSize > 0) {
                setPageIndex(0)
                callBack?.upContent()
                return true
            }
        }
        return false
    }


    val isLoadingContent: Boolean
        get() = loadContentJob?.isActive == true

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
        if (anchorId.isEmpty()) {
            return null
        }

        curTextChapter?.readerTexts?.let { texts ->
            var linkIndex = -1
            //对当前章节中的段落进行遍历，找到anchoId对应的段落的索引
            for (index in 0 until texts.size) {
                val paragraph = texts[index]
                if (paragraph is ReaderText.Text) {
                    val tag =
                        paragraph.annotations.firstOrNull { it.anchorId.isNotEmpty() && it.anchorId == anchorId }
                    if (tag != null) {
                        linkIndex = index
                        break
                    }
                }
            }
            // 定位到了锚点所在的章节中的段落
            // 从第一个锚点定位的段落开始往后遍历，找到有此锚点标记的章节内容，碰到非此锚点的情况则退出循环
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
        Logger.i("PageViewController::loadContent:resetPageOffset=$resetPageOffset,durChapterIndex=$durChapterIndex, isInitFinish=$isInitFinish, contentLoadVersion=$contentLoadVersion")
        if (!isInitFinish) {
            Logger.w("PageViewController::loadContent skipped - not initialized")
            return
        }

        // 版本锁机制：先取消旧任务，再原子递增版本号，保证线程安全
        loadContentJob?.cancel()  // 先取消，避免与新任务冲突
        loadContentJob = null

        val currentVersion = contentLoadVersion.incrementAndGet()  // 原子递增，线程安全
        Logger.d("PageViewController::loadContent cancelled previous job, version=$currentVersion")

        isBatchLoading = true
        // 使用 launchIO 而不是 launch(Dispatchers.IO)，保持与现有代码一致
        loadContentJob = scope.launchIO {
            // 双重检查：协程活性 + 版本号验证
            if (!isActive) {
                if (currentVersion == contentLoadVersion.get()) {
                    //当两次 loadContent 调用重叠时，被取消的旧协程会错误地清除 isBatchLoading
                    isBatchLoading = false
                }
                Logger.d("PageViewController::loadContent cancelled - coroutine not active")
                return@launchIO
            }
            if (currentVersion != contentLoadVersion.get()) {
                // 有更新的版本待执行，由新版本管理 isBatchLoading
                Logger.d("PageViewController::loadContent cancelled - newer version pending (current=$currentVersion, latest=${contentLoadVersion.get()})")
                return@launchIO
            }

            Logger.d("PageViewController::loadContent executing - version=$currentVersion")

            try {
                loadChapter(durChapterIndex, resetPageOffset = resetPageOffset)
                if (currentVersion != contentLoadVersion.get()) return@launchIO
                loadChapter(durChapterIndex + 1, resetPageOffset = resetPageOffset)
                if (currentVersion != contentLoadVersion.get()) return@launchIO
                loadChapter(durChapterIndex - 1, resetPageOffset = resetPageOffset)
            } finally {
                if (currentVersion == contentLoadVersion.get()) {
                    isBatchLoading = false
                }
            }
            callBack?.upContent()
        }
    }

    /***
     * 章节内,页面位置跳转
     */
    override fun setPageIndex(index: Int) {
        Logger.i("PageViewController::setPageIndex:index=$index")
        durPageIndex = index
        saveRead()
        clickListener?.onPageChange()
    }

    internal fun saveRead() {
        val curBook = book ?: return
        scope.launchIO {
            val lastTime = System.currentTimeMillis()
            val lastChapterIndex = durChapterIndex
            val lastPageInChapter = durPageIndex
            val lastProgress = (progression * 100.0).toFloat()

            updateProgressFieldsUseCase(
                bookId = curBook.id,
                lastOpened = lastTime,
                scrollIndex = lastChapterIndex,
                scrollOffset = lastPageInChapter,
                progress = lastProgress
            )

            Logger.d("PageViewController::saveRead::lastOpened=${lastTime},lastChapterIndex=${lastChapterIndex},lastPageInChapter=${lastPageInChapter},lastProgress=${lastProgress}")
        }
    }

    override fun upMsg(msg: String?) {
        if (this.msg != msg) {
            this.msg = msg
            callBack?.upContent()
        }
    }

    suspend fun updateChapterByAddBookmark(addedBookmark: Bookmark): Boolean {
        if (userBookmakrs == null) {
            userBookmakrs = arrayListOf()
        }
        userBookmakrs?.add(addedBookmark)
        return innerUpdateChapterHandleBookmark()
    }

    suspend fun updateChapterByDelBookmark(deledBookmark: Bookmark): Boolean {
        if (userBookmakrs == null) {
            userBookmakrs = arrayListOf()
        }
        userBookmakrs?.remove(deledBookmark)
        return innerUpdateChapterHandleBookmark()
    }

    private suspend fun innerUpdateChapterHandleBookmark(): Boolean {
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
     * update bookAnnotation then update chapter
     */
    suspend fun updateChapterByUpdateAnnotation(anno: BookAnnotation) {
        val tags = curTextChapter?.annotations?.toMutableMap() ?: return
        val readerTexts = curTextChapter?.readerTexts ?: return

        for (entry in tags) {
            val lists = entry.value.toMutableList()
            lists.removeIf { item ->
                anno.id.toString() == item.uuid && anno.type.toString() == item.name
            }
            if (lists.size != entry.value.size) {
                entry.setValue(lists)
            }
        }

        val texttags = anno.locatorInfo?.toTextTags(
            anno.id.toString(),
            anno.type.toString(),
            anno.color,
            durChapterIndex,
            readerTexts
        ).orEmpty()
        if (texttags.isNotEmpty()) {
            val keys = tags.keys.plus(texttags.keys)
            for (key in keys) {
                tags[key] = (tags[key].orEmpty()).toMutableList().plus(texttags[key].orEmpty())
            }
        }
        curTextChapter?.annotations = tags

        val index = userAnnotations?.indexOfFirst { it.id == anno.id } ?: -1
        if (index >= 0) {
            userAnnotations?.set(index, anno)
        }

        callBack?.clearBitmapCache()
        callBack?.upContent(resetPageOffset = false)
    }

    suspend fun updateChapterByUpdateNote(note: Note) {
        val tags = curTextChapter?.annotations?.toMutableMap() ?: return
        val readerTexts = curTextChapter?.readerTexts ?: return
        for (entry in tags) {
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
            for (key in keys) {
                tags[key] = (tags[key].orEmpty()).toMutableList().plus(texttags[key].orEmpty())
            }
        }
        curTextChapter?.annotations = tags
        callBack?.clearBitmapCache()
        callBack?.upContent(resetPageOffset = false)
    }

    /****
     * refresh view of chapter
     * @param annotation add to TextChapter
     * @param conflictAnnotations delete from TextChapter
     */
    fun updateChapter(
        annotation: BookAnnotation?,
        addNote: Note?,
        deleteNote: Note?,
        conflictAnnotations: List<BookAnnotation>
    ) {
        val tags = textChapter(0)?.annotations?.toMutableMap()
        if (tags == null) {
            Logger.e("${this.javaClass.name}::updateChapter::tags is null")
        }

        if (conflictAnnotations.isNotEmpty() && !tags.isNullOrEmpty()) {
            for (entry in tags) { //遍历所有的tag中的BookAnnotation, 从其中删除掉
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

            // 同步清理 userAnnotations 缓存，防止 loadContent 时重新加载已删除的注解
            userAnnotations?.removeAll { annotationToRemove ->
                conflictAnnotations.any { it.id == annotationToRemove.id }
            }
        }

        if (deleteNote != null && !tags.isNullOrEmpty()) {
            for (entry in tags) {
                val lists = entry.value.toMutableList()
                lists.removeIf { item ->
                    deleteNote.id.toString() == item.uuid && item.name == "note"
                }
                if (lists.size != entry.value.size) {
                    entry.setValue(lists)
                }
            }

            // 需要添加：同步清理 userNotes 缓存
            userNotes?.removeAll { it.id == deleteNote.id }
        }

        val readerTexts = textChapter(0)?.readerTexts
        if (readerTexts != null) {
            val texttags = annotation?.locatorInfo?.toTextTags(
                annotation.id.toString(),
                annotation.type.toString(),
                annotation.color,
                durChapterIndex, readerTexts
            ).orEmpty()
            if (texttags.isNotEmpty() && !tags.isNullOrEmpty()) {
                val keys = tags.keys.plus(texttags.keys)
                for (key in keys) {
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
                for (key in keys) {
                    tags[key] =
                        (tags[key].orEmpty()).toMutableList().plus(noteTextTags[key].orEmpty())
                }
            }
        }

        curTextChapter?.annotations = tags.orEmpty()
        // 添加新标注到 userAnnotations 缓存
        if (annotation != null) {
            userAnnotations?.add(annotation)
        }
        if (addNote != null) {
            userNotes?.add(addNote)
        }

        callBack?.clearBitmapCache()
        callBack?.upContent(resetPageOffset = false)
    }

    private fun getPageBookmark(
        textPage: TextPage,
        chapter: TextChapter,
        chapterBookmarks: List<Bookmark>?
    ): Bookmark? {
        if (chapterBookmarks.isNullOrEmpty()) {
//            Logger.d("TextPageFactory::getPageBookmark:: current chapter[${chapter.position}] bookmarks is empty.")
            return null
        } else {
//            Logger.d("TextPageFactory::getPageBookmark:: current chapter[${chapter.position}] bookmarks.size=${chapterBookmarks.size}.")
        }
        var pageStartParagraphIndex = 0
        var pageStartParagraphTextOffset = 0
        var pageEndParagraphIndex = 0
        var pageEndParagraphTextOffset = 0

        val firstLine = textPage.textLines.firstOrNull()
        val lastLine = textPage.textLines.lastOrNull()

        pageStartParagraphIndex = firstLine?.paragraphIndex ?: 0
        pageStartParagraphTextOffset = firstLine?.charStartOffset ?: 0
        pageEndParagraphIndex = lastLine?.paragraphIndex ?: 0
        pageEndParagraphTextOffset = lastLine?.charEndOffset ?: 0
        Logger.d(
            "TextPageFactory::getPageBookmark::pageStartParagraphIndex=${pageStartParagraphIndex},pageEndParagraphIndex=${pageEndParagraphIndex}," +
                    "pageStartParagraphTextOffset=$pageStartParagraphTextOffset,pageEndParagraphTextOffset=$pageEndParagraphTextOffset"
        )

        var targetMark: Bookmark? = null
        for (mark in chapterBookmarks) {
            val locator = mark.locatorInfo ?: continue
            val paragraphIndex = locator.startParagraphIndex
            val textOffset = locator.startTextOffset
            Logger.d("TextPageFactory::getPageBookmark::locator=${locator}")

            if (paragraphIndex !in pageStartParagraphIndex..pageEndParagraphIndex) {
                continue
            }

            if (paragraphIndex == pageStartParagraphIndex && textOffset >= pageStartParagraphTextOffset) {
                targetMark = mark
                break
            } else if (paragraphIndex == pageEndParagraphIndex && textOffset < pageEndParagraphTextOffset) {
                targetMark = mark
                break
            } else if (paragraphIndex > pageStartParagraphIndex && paragraphIndex < pageEndParagraphIndex) {
                targetMark = mark
                break
            }
        }
        return targetMark
    }

    @Volatile var isBatchLoading = false

    override suspend fun loadChapter(
        chapterIndex: Int,
        upContent: Boolean,
        resetPageOffset: Boolean,
        assignToSlot: Boolean
    ): TextChapter? {
        return withContext(loadChapterDispatcher) {
//        Logger.i("PageViewController::loadContent:index=$index,upContent=$upContent,resetPageOffset=$resetPageOffset,bookid=${book?.id},bookname=${book?.title}")
            if (chapterIndex !in 0 until chapterSize) {
                if (chapterIndex == durChapterIndex) {
                    navigationLoadingListener?.onNavigationLoadingError(chapterIndex)
                }
                return@withContext null
            }
            val curBook = book ?: run {
                if (chapterIndex == durChapterIndex) {
                    navigationLoadingListener?.onNavigationLoadingError(chapterIndex)
                }
                return@withContext null
            }
            val bookId = curBook.id
            Logger.i("PageViewController::loadContent:index=$chapterIndex,bookId=$bookId")

            val chapter = getChapterByIdUserCase(bookId, chapterIndex).firstOrNull()
            if (chapter == null) {
                Logger.e("PageViewController::chapter not found, index=$chapterIndex, bookId=$bookId")
                if (chapterIndex == durChapterIndex) {
                    navigationLoadingListener?.onNavigationLoadingError(chapterIndex)
                }
                if (isInitFinish) {
                    onInitChapterLoadListener?.invoke(false)
                    onInitChapterLoadListener = null
                }
                return@withContext null
            }
            try {
                val readerTexts: List<ReaderText> =
                    BookHelper.loadChapterContent(context, curBook, chapter, textParser)
            Logger.i("PageViewController::loadContent:index=$chapterIndex,chapter.index=${chapter.chapterIndex} readerTexts.size=${readerTexts.size}")

            var tags = hashMapOf<Int, List<TextTag>>()  //章节全部标签信息
            readerTexts.forEachIndexed { index, content ->
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
                    chapterIndex, readerTexts
                )
                if (!texttags.isNullOrEmpty()) {
                    val keys = tags.keys.plus(texttags.keys)
                    for (key in keys) {
                        tags[key] =
                            (tags[key].orEmpty()).toMutableList().plus(texttags[key].orEmpty())
                    }
                }
            }
            //将Note转换成TextTag，控制界面显示
            userNotes?.forEach { note ->
                val texttags = note.locatorInfo?.toTextTags(
                    note.id.toString(),
                    "note",
                    note.color,
                    chapterIndex, readerTexts
                )
                if (!texttags.isNullOrEmpty()) {
                    val keys = tags.keys.plus(texttags.keys)
                    for (key in keys) {
                        tags[key] =
                            (tags[key].orEmpty()).toMutableList().plus(texttags[key].orEmpty())
                    }
                }
            }

            //遍历当前章节的书签
            val chapterBookmarks = userBookmakrs?.filter {
                it.chapterIndex == chapterIndex
            }
            Logger.d("PageViewController::loadContent[$chapterIndex],chapterBookmarks[${chapterBookmarks?.size}]")

            val supperReaderTexts =
                BookHelper.disposeContent(appPreferencesUtil, chapter, readerTexts)

            val cssInfoMaps = hashMapOf<Int, TextCssInfo>()
            var wordCount = 0L
            var picCount = 0L
            for ((index, content) in supperReaderTexts.withIndex()) {
                if (content is ReaderText.Text) {
                    cssInfoMaps[index] = content.textCssInfo
                    wordCount += content.line.length
                } else if (content is ReaderText.Image) {
                    picCount++
                }
            }

            val textChapter = ChapterProvider.getTextChapter(
                chapter,
                supperReaderTexts,
                imageStyles = "",
                chapterSize
            )
            textChapter?.annotations = tags
            textChapter?.textCssInfos = cssInfoMaps
            textChapter?.readerTexts = supperReaderTexts
            textChapter?.wordCount = wordCount
            textChapter?.totalWordCount = curBook.wordCount
            textChapter?.chapterProgress = chapter.chapterProgress
            Logger.d("PageViewController::loadChapter:ch=$chapterIndex, totalWordCount=${curBook.wordCount}, bookWC=${book?.wordCount}")

            if (chapter.wordCount == 0L && chapter.picCount == 0L && (wordCount > 0 || picCount > 0)) {
                updateChapterWordCountUserCase.invoke(
                    bookId, chapter.chapterIndex, wordCount, picCount, 0f
                )
            }

            textChapter?.pages?.forEach { page ->
                page.bookmarkId = getPageBookmark(page, textChapter, chapterBookmarks)?.id ?: -1
            }

            if (assignToSlot) {
                var needOnPageChange = (targetProgress < 0.0)

                when (chapter.chapterIndex) {
                    durChapterIndex -> {    //加载的是当前章节
                        curTextChapter = textChapter

                        //修改切换之后的显示章节的第几页
                        if (targetProgress >= 0.0 && curBook.wordCount > 0 && targetProgress >= chapter.chapterProgress) {
                            val inChapterProgress = targetProgress - chapter.chapterProgress
                            val inChapterPercent = chapter.count.toDouble() / curBook.wordCount.toDouble()
                            val chapterPageSize = textChapter?.pageSize ?: 0

                            Logger.d("PageViewController::inChapterProgress=${inChapterProgress},inChapterPercent=${inChapterPercent}, pageSize=${chapterPageSize} durPageIndex=$durPageIndex,targetProgress=$targetProgress")
                            val pageIndex =
                                try {
                                    if (inChapterPercent <= 0.0 || chapterPageSize <= 0) {
                                        Logger.w("PageViewController::invalid inChapterPercent=$inChapterPercent, pageSize=$chapterPageSize, fallback to page 0")
                                        0
                                    } else {
                                        ((inChapterProgress / inChapterPercent) * (chapterPageSize.toDouble().orZero())).roundToInt()
                                    }
                                } catch (ex: Exception) {
                                    Logger.w("PageViewController::pageIndex calculation failed: ${ex.message}, fallback to 0")
                                    0
                                }
                            if (pageIndex in 0 until (textChapter?.pageSize ?: 0)) {
                                durPageIndex = pageIndex
                            }
                            Logger.d("PageViewController::pageIndex =${pageIndex}, durPageIndex=$durPageIndex, wordCount=${curTextChapter?.wordCount},totalWordCount=${curTextChapter?.totalWordCount}")
                            targetProgress = -1.0
                            needOnPageChange = true
                        }

                        val pendingAnchor = pendingAnchorId
                        pendingAnchorId = null
                        if (pendingAnchor != null) {
                            val texts = textChapter?.readerTexts
                            if (texts != null) {
                                val paraIndex = JumpHelper.findAnchorParagraphIndex(texts, pendingAnchor)
                                if (paraIndex >= 0) {
                                    targetLocator = Locator(
                                        chapterIndex = durChapterIndex,
                                        startParagraphIndex = paraIndex,
                                        startTextOffset = 0,
                                        progression = 0.0
                                    )
                                    Logger.d("PageViewController::loadChapter:pendingAnchorId '$pendingAnchor' -> paragraph $paraIndex -> targetLocator")
                                } else {
                                    Logger.w("PageViewController::loadChapter:pendingAnchorId '$pendingAnchor' not found in chapter $chapterIndex")
                                }
                            }
                        }

                        val locator = targetLocator
                        if (locator != null) {
                            val targetPageIndex = textChapter?.getPageIndexFromLocator(locator) ?: -1
                            if (targetPageIndex >= 0 && targetPageIndex < (textChapter?.pageSize?: 0)) {
                                durPageIndex = targetPageIndex
                                Logger.d("PageViewController:: to targetPageIndex =${targetPageIndex}, ")
                            }
                            targetLocator = null
                        }

                        if (upContent) {
                            callBack?.upContent(resetPageOffset = resetPageOffset)
                        }
                        callBack?.upView()
                        navigationLoadingListener?.onNavigationLoadingComplete(durChapterIndex)
                        if (isInitFinish && onInitChapterLoadListener != null) {
                            Logger.d("PageViewController::loadChapterContent first success")
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
                    Logger.d("PageViewController::loadContent success onPageChange::${durChapterIndex}")
                    clickListener?.onPageChange()
                }
            }

            return@withContext textChapter
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("PageViewController::loadChapter unexpected error: ${e.message}")
                pendingAnchorId = null
                if (chapterIndex == durChapterIndex) {
                    navigationLoadingListener?.onNavigationLoadingError(chapterIndex)
                }
                return@withContext null
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
        // nextTextChapter may have been paginated before a later font-size (or other style)
        // change; showing it as-is would flash the old layout at the new size. Treat a
        // style-version mismatch as a cache miss and force a fresh pagination instead.
        curTextChapter = nextTextChapter?.takeIf { it.styleVersion == ChapterProvider.styleVersion.get() }
        nextTextChapter = null
        if (curTextChapter == null) {
            navigationLoadingListener?.onNavigationLoadingStart(durChapterIndex, immediate = true)
            scope.launchIO {
                Logger.d("PageViewController::moveToNextChapter:when curTextChapter is null, durChapterIndex=$durChapterIndex")
                loadChapter(durChapterIndex, upContent, false)
            }
        } else {
            callBack?.upContent()
        }
        scope.launchIO {
            Logger.d("PageViewController::moveToNextChapter:, durChapterIndex=${durChapterIndex + 1}")
            loadChapter(durChapterIndex.plus(1), upContent, false)
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
        // See moveToNextChapter: prevTextChapter can be similarly stale.
        curTextChapter = prevTextChapter?.takeIf { it.styleVersion == ChapterProvider.styleVersion.get() }
        prevTextChapter = null

        if (curTextChapter == null) {
            navigationLoadingListener?.onNavigationLoadingStart(durChapterIndex, immediate = true)
            scope.launchIO {
                Logger.d("PageViewController::moveToPrevChapter when curTextChapter is null, durChapterIndex=${durChapterIndex}")
                loadChapter(durChapterIndex, upContent, false)
            }
        } else if (upContent) {
            callBack?.upContent()
        }

        scope.launchIO {
            Logger.d("PageViewController::moveToPrevChapter, durChapterIndex=${durChapterIndex - 1}")
            loadChapter(durChapterIndex.minus(1), upContent, false)
        }
        saveRead()
        callBack?.upView()
        return true
    }

    override fun clickCenter() {
        Logger.i("PageViewController::clickCenter")
        clickListener?.onCenterClick()
    }

    override fun hideMenu() {
        Logger.i("PageViewController::hideMenu")
        clickListener?.hideMenu()
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
        val curProgression = progression
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

    override fun getSelectionLocator(): Locator? {
        if (selectionChapterIndex < 0 ||
            startParagraphIndex < 0 ||
            endParagraphIndex < 0 ||
            startInnerTextOffset < 0 ||
            endInnerTextOffset < 0
        ) {
            return null
        }
        return Locator(
            id = "",
            chapterIndex = selectionChapterIndex,
            startParagraphIndex = startParagraphIndex,
            startTextOffset = startInnerTextOffset,
            endParagraphIndex = endParagraphIndex,
            endTextOffset = endInnerTextOffset,
            text = "",
            progression = progression
        )
    }

    fun getSelectedLocator(): Locator? {
        return getSelectionLocator()?.copy(text = getSelectedText())
    }

    fun isSelectionOnCurrentPage(): Boolean {
        if (selectionChapterIndex < 0 || selectionChapterIndex != durChapterIndex) return false
        val curPage = pageFactory?.currentPage ?: return false
        val startLine = curPage.textLines.firstOrNull { it.paragraphIndex == startParagraphIndex }
        val endLine = curPage.textLines.firstOrNull { it.paragraphIndex == endParagraphIndex }
        return startLine != null || endLine != null
    }

    fun getCurrentLocator(): Locator? {
        val chapter = curTextChapter ?: return null
        val page = chapter.pages.getOrNull(durPageIndex) ?: return null
        val midLineIndex = page.textLines.size / 2
        val midLine = page.textLines.getOrNull(midLineIndex)
            ?: page.textLines.firstOrNull() ?: return null
        return Locator(
            chapterIndex = durChapterIndex,
            startParagraphIndex = midLine.paragraphIndex,
            startTextOffset = midLine.charStartOffset,
            endParagraphIndex = midLine.paragraphIndex,
            endTextOffset = midLine.charEndOffset,
            text = "",
            progression = progression,
        )
    }

    /****
     * 设置屏幕常亮
     */
    override fun screenOffTimerStart() {
//        Logger.i("PageViewController::screenOffTimerStart")
    }

    override fun showTextActionMenu() {
        Logger.i("PageViewController::showTextActionMenu")
//        clickListener?.onSelectedText(
//            selectedStartX,
//            selectedStartTop,
//            selectedEndX,
//            selectedEndY
//        )
        clickListener?.onSelectedText(
            selectedStartX,
            selectedStartTop,
            selectedEndX,
            selectedEndY
        )
    }

    override fun showToolbarMenu() {
        Logger.i("PageViewController::showToolbarMenu")
        clickListener?.showMenu()
    }

    //选中的位置
    private var selectedStartX: Float = 0.0f
    private var selectedStartY: Float = 0.0f
    private var selectedStartTop: Float = 0.0f
    private var selectedEndX: Float = 0f
    private var selectedEndY: Float = 0f

    private var selectionChapterIndex: Int = -1

    fun setSelectionChapterIndex(index: Int) {
        selectionChapterIndex = index
    }

    private var startParagraphIndex: Int = -1
    private var startInnerTextOffset: Int = -1
    private var endParagraphIndex: Int = -1
    private var endInnerTextOffset: Int = -1

    override fun upSelectedStart(
        x: Float,
        y: Float,
        top: Float,
        paragraphIndex: Int,
        innerTextOffset: Int
    ) {
        selectedStartX = x
        selectedStartY = y
        selectedStartTop = top
        startParagraphIndex = paragraphIndex
        startInnerTextOffset = innerTextOffset
        if (selectionChapterIndex < 0) {
            selectionChapterIndex = durChapterIndex
        }
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
        selectionChapterIndex = -1
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
            for (entity in tagMap) {
                val paragraphIndex = entity.key
                entity.value.filter { noteId == it.uuid && it.name == "note" }.forEach { annoTag ->
                    val startOffset = annoTag.start
                    val endOffset = annoTag.end
                    pendingRange.add(Triple(paragraphIndex, startOffset, endOffset))
                }
            }
        }
        innerSelectText(pendingRange, curPage) { rect ->
            if (pendingRange.isNotEmpty()) {
                val minP = pendingRange.minOf { it.first }
                val maxP = pendingRange.maxOf { it.first }
                val first = pendingRange.filter { it.first == minP }.minByOrNull { it.second }
                val last = pendingRange.filter { it.first == maxP }.maxByOrNull { it.third }
                if (first != null && last != null) {
                    selectionChapterIndex = durChapterIndex
                    startParagraphIndex = first.first
                    startInnerTextOffset = first.second
                    endParagraphIndex = last.first
                    endInnerTextOffset = last.third
                }
            }
            clickListener?.onCheckedNote(noteId, rect)
        }
    }

    override fun clickedAnnotation(annotationIds: List<String>) {
        Logger.d("PageViewController::clickedAnnotation::annotationIds=$annotationIds")
        val curChapter = curTextChapter ?: return
        val curPage = pageFactory?.currentPage ?: return
        val pendingRange = arrayListOf<Triple<Int, Int, Int>>()

        curChapter.annotations.let { tagMap ->
            for (entity in tagMap) {
                val paragraphIndex = entity.key
                entity.value.filter {
                    annotationIds.contains(it.uuid) && (it.name == "underline" || it.name == "highlight")
                }.forEach { annoTag ->
                    val startOffset = annoTag.start
                    val endOffset = annoTag.end - 1
                    pendingRange.add(Triple(paragraphIndex, startOffset, endOffset))
                }
            }
        }

        innerSelectText(pendingRange, curPage) { rect ->
            if (pendingRange.isNotEmpty()) {
                val minP = pendingRange.minOf { it.first }
                val maxP = pendingRange.maxOf { it.first }
                val first = pendingRange.filter { it.first == minP }.minByOrNull { it.second }
                val last = pendingRange.filter { it.first == maxP }.maxByOrNull { it.third }
                if (first != null && last != null) {
                    selectionChapterIndex = durChapterIndex
                    startParagraphIndex = first.first
                    startInnerTextOffset = first.second
                    endParagraphIndex = last.first
                    endInnerTextOffset = last.third
                }
            }
            clickListener?.onCheckedAnnotation(annotationIds, rect)
        }
    }

    private fun innerSelectText(
        pendingRange: List<Triple<Int, Int, Int>>,
        curPage: TextPage,
        onFinished: (RectF) -> Unit
    ) {
        if (pendingRange.isNotEmpty()) {
            //遍历得到tag对应的选中文本的开始字符屏幕位置和结束位置屏幕位置
            var startX = -1f
            var startY = -1f
            var endX = -1f
            var endY = -1f
            var lastCh: TextChar? = null
            var lastLine: TextLine? = null
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
                Logger.d("PageViewController::clickedAnnotation::startX=$startX,startY=$startY,endX=$endX,endY=$endY")
//                callBack?.upSelectedRange(startX, startY, endX, endY)
                callBack?.upContent(resetPageOffset = false)
//                clickListener?.onCheckedAnnotation(annotationIds, startX, startY, endX, endY)
                onFinished(RectF(startX, startY, endX, endY))
            }
        }
    }

    override fun currentPage(): TextPage? = textChapter(0)?.page(durChapterPos())

    override fun syncTtsPlayStatus(ttsPlaybackStatus: TtsPlaybackStatus) {
        Logger.i("PageViewController::syncTtsPlayStatus:$ttsPlaybackStatus")
        clickListener?.onTtsPlayStatus(ttsPlaybackStatus)
    }

    override fun onTimerExpired() {
        clickListener?.showTimerExpired()
    }

    /***
     * update view after modify preference
     * @param resetPageOffset true=重置到章节首页；false=保留当前阅读位置（默认）。
     *        F4-01：偏好变更（切主题/调字号/行距/字体）默认保留阅读位置——
     *        collector 无法区分触发源，且 loadChapter 已按章节进度重算 durPageIndex，
     *        分页变化后位置自动适配。顺带修复"用户调字号跳回章节首页"的体验问题。
     *
     * suspend: waits for the on-screen chapter to be repaginated with the new style before
     * triggering a redraw. ChapterProvider.upStyle already applied the new style (e.g. font
     * size) to the paint objects synchronously; firing upContent() immediately, before
     * durChapterIndex is repaginated, would draw the new paint size against the old layout —
     * the mis-sized flash seen while dragging the font-size slider (imperceptible on a fast
     * emulator, visible on a slower device where repagination can't keep up). durChapterIndex
     * ± 1 aren't on screen, so they still refresh in the background via loadContent().
     */
    suspend fun updatePageViews(prefs: ReaderPreferences? = null, resetPageOffset: Boolean = false) {
        ChapterProvider.upStyle(context, prefs)
        loadChapter(durChapterIndex, upContent = false, resetPageOffset = resetPageOffset)
        loadContent(resetPageOffset)
        callBack?.upContent()
        callBack?.upStyle()
        callBack?.upTipStyle()
        callBack?.upBg()
        callBack?.upPageAnim()
        callBack?.upPageControl()
    }

    override fun clear(ownerToken: Long?) {
        super.clear(ownerToken)
        if (ownerToken != null && !isOwner(ownerToken)) {
            Logger.i("PageViewController:clear() data skipped - caller (token=$ownerToken) is not the current owner (token=${currentOwnerToken.get()})")
            return
        }
        // 清理图片内存缓存（纯内存操作，主线程同步执行）
        // 放在 launchIO 之前，确保内存先于文件操作释放
        ImageProvider.clearCache()
        scope.launchIO {
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
        chapterSize = 0
        isInitFinish = false
        onInitChapterLoadListener = null  // F2 修订：清空监听器，消除幽灵回调
        contentLoadVersion.set(0)  // 原子操作重置版本锁
        loadContentJob?.cancel()  // 取消正在进行的加载任务
        loadContentJob = null
        isAutoPage = false
        autoPageProgress = 0
        pageFactory = null
        isScroll = false
        searchedLocators = emptyList()
        Logger.i("PageViewController:clear()")
    }

    override fun getSearchHighlights(): List<Locator> = searchedLocators

    fun addSearchHighlight(locators: List<Locator>) {
        searchedLocators = locators
    }

    fun clearSearchHighlights() {
        if (searchedLocators.isEmpty()) return
        searchedLocators = emptyList()
        callBack?.upContent(resetPageOffset = false)
    }

    fun animToNext() {
        callBack?.moveToNextPage()
    }

    fun animToPrev() {
        callBack?.moveToPrevPage()
    }


    /**
     * 停止朗读（新版本）
     */
    override fun stopReadPageNew() {
        Logger.i("PageViewController::stopReadPageNew")
        super.stopReadPageNew()
        callBack?.upContent()
    }

    override fun refreshView() {
        callBack?.upContent()
    }

    override fun changeChapterAndPage(
        newChapterIndex: Int,
        newPageIndex: Int,
        newProgress: Double
    ): Boolean {
        Logger.i("PageViewController::changeChapterAndPage:newChapterIndex=$newChapterIndex,newPageIndex=$newPageIndex,newProgress=$newProgress")
        if (durChapterIndex != newChapterIndex) {
            durChapterIndex = newChapterIndex
        }

        if (durPageIndex != newPageIndex) {
            durPageIndex = newPageIndex
        }
        if (newProgress >= 0.0) {
            val curChapter = curTextChapter ?: return false
            if (curChapter.totalWordCount == 0L) {
                Logger.e("PageViewController::changeChapter failed, no word count info")
                return false
            }

            targetProgress = newProgress
        }
        loadContent(true)
        saveRead()
        return true
    }

    override suspend fun updateReadingTime() {
        clickListener?.updateReadingTime()
    }

    override fun getSpeakBookStatus(): SpeekBookStatus {
        return this.speakingStatus
    }

    fun updateBg() {
        callBack?.upBg()
    }
}