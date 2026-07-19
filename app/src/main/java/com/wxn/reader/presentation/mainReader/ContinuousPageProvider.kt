package com.wxn.reader.presentation.mainReader

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.wxn.base.bean.Book
import com.wxn.base.bean.Locator
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.ui.IDataSource
import com.wxn.bookread.ui.PageCallback
import com.wxn.reader.presentation.mainReader.models.PreloadState
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 连续滚动页面提供者
 * 
 * 职责：
 * - 合并多个章节的页面为单一列表
 * - 提供页面查询接口
 * - 处理样式变更和内容更新
 * - 管理章节加载调度和预加载逻辑
 */
class ContinuousPageProvider(
    val pageController: PageViewController
) : IDataSource, PageCallback {

    companion object {
        const val REBUILD_COALESCE_DELAY_MS = 50L

        /**
         * 判断 (paragraphIndex, offset) 是否在文本选区范围内。
         *
         * 仅适用于文本选区 Locator（来自 getSelectionLocator()），其 endTextOffset 是包含性的。
         * TTS/搜索的 Locator 使用排他性 endTextOffset，不应调用此函数。
         *
         * 参照 ContentTextView.isCharInSelection() 的纯范围比较逻辑。
         */
        internal fun isOffsetInTextSelection(
            locator: Locator,
            paragraphIndex: Int,
            offset: Int
        ): Boolean {
            return when {
                paragraphIndex < locator.startParagraphIndex ||
                        paragraphIndex > locator.endParagraphIndex -> false

                locator.startParagraphIndex == locator.endParagraphIndex ->
                    offset in locator.startTextOffset..locator.endTextOffset

                paragraphIndex == locator.startParagraphIndex ->
                    offset >= locator.startTextOffset

                paragraphIndex == locator.endParagraphIndex ->
                    offset <= locator.endTextOffset

                else -> true
            }
        }
    }

    // ========== 外部章节跳转滚动请求 ==========
    // 在 upContent() 中捕获目标 (chapterIndex, pageIndex)
    // 在 rebuildMergedPages() 完成后发出滚动请求
    // @Volatile: 跨线程访问（IO 线程写入，主线程 hasPendingJump() 读取）
    private val pendingJumpTarget =
        java.util.concurrent.atomic.AtomicReference<Pair<Int, Int>?>(null)

    // 当前 LazyColumn 可见位置（由滚动观察者更新）
    // 用于区分导航触发的 upContent（需要滚动）和普通刷新触发的 upContent（不需要滚动）
    @Volatile
    internal var currentScrollGlobalIndex: Int = -1

    // 序列号避免 StateFlow 去重（同一位置连续点击两次需要两次滚动）
    data class ScrollTarget(val seq: Long, val globalIndex: Int)

    private val _scrollTarget = MutableStateFlow<ScrollTarget?>(null)
    val scrollTarget: StateFlow<ScrollTarget?> = _scrollTarget.asStateFlow()

    // AtomicLong: 保证 ++ 操作的原子性
    private val scrollSeq = java.util.concurrent.atomic.AtomicLong(0L)
    override var pageIndex: Int = 0
        get() {
            return pageController.durChapterPos()
        }

    override val currentChapter: TextChapter?
        get() {
            return if (pageController.isInitFinish) {
                pageController.textChapter(0)
            } else {
                null
            }
        }

    override val nextChapter: TextChapter?
        get() {
            return if (pageController.isInitFinish) {
                pageController.textChapter(1)
            } else {
                null
            }
        }

    override val prevChapter: TextChapter?
        get() {
            return if (pageController.isInitFinish) {
                pageController.textChapter(-1)
            } else {
                null
            }
        }

    override fun hasNextChapter(): Boolean {
        val retVal = pageController.durChapterIndex < pageController.chapterSize - 1
        Logger.d("PageView::HasNextChapter::retVal=$retVal")
        return retVal
    }

    override fun hasPrevChapter(): Boolean {
        val retVal = pageController.durChapterIndex > 0
        Logger.d("PageView::hasPrevChapter::retVal=$retVal")
        return retVal
    }

    override fun findChapterByPosition(position: Int): TextChapter? {
        return pageController.getCachedChapter(position) ?: preloadedChapters[position]
    }

    // 预加载 Job
    private var preloadNextState: PreloadState? = null
    private var preloadPrevState: PreloadState? = null

    // 已加载章节范围管理（替代调度器的 loadedRange）
    private val _loadedRange = MutableStateFlow(Pair(0, 0))
    val loadedChapterRange: StateFlow<Pair<Int, Int>> = _loadedRange.asStateFlow()

    // 合并后的页面列表 (包含多个章节的页面)
    private val _mergedPages = MutableStateFlow<List<MergedPageItem>>(emptyList())
    val mergedPages: StateFlow<List<MergedPageItem>> = _mergedPages.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _activeLoadCount = java.util.concurrent.atomic.AtomicInteger(0)

    // 预加载的章节缓存（超出3槽位范围的章节）
    private val preloadedChapters = ConcurrentHashMap<Int, TextChapter>()

    // --- 文本选区状态 ---
    /**
     * 每页选区修订号。Key = globalPageIndex，Value = 递增计数器。
     *
     * 在 drawBehind 中读取 pageSelectionRevisions[pageIndex] 会注册 per-key snapshot 依赖。
     * 当 bumpPageRevision(pageIndex) 改变特定 key 时，仅该页面的 Layer 1/3 drawBehind 被失效。
     *
     * 注意：写入方仅限 ContinuousPageProvider 内部的 bumpPageRevision / bumpPageRevisionRange。
     */
    private val _pageSelectionRevisions = mutableStateMapOf<Int, Long>()
    val pageSelectionRevisions: SnapshotStateMap<Int, Long> get() = _pageSelectionRevisions

    /**
     * 递增指定页面的选区修订号，触发该页面的 Layer 1/3 重绘。
     *
     * 注意：read-modify-write 非原子操作，必须在主线程调用。
     * 当前所有调用方（updateSelectionState、cancelTextSelected）均在主线程执行。
     */
    private fun bumpPageRevision(pageIndex: Int) {
        _pageSelectionRevisions[pageIndex] = (_pageSelectionRevisions[pageIndex] ?: 0L) + 1L
    }

    fun refreshPageRevision(pageIndex: Int) {
        bumpPageRevision(pageIndex)
    }

    // --- 文本内容渲染修订号 ---
    /**
     * 全局文本内容渲染修订号（递增计数器）。
     *
     * 在 Layer 2（文本内容）的 drawBehind 中读取 contentRenderRevision 注册 snapshot 依赖。
     * bumpContentRenderRevision() 改变值时，所有可见页面的 Layer 2 drawBehind 被失效重绘。
     *
     * 主要用途：pageFactory 就绪后触发文本层重绘（修复导航返回后链接样式丢失的时序竞态）。
     * bump 是低频操作，仅在 pageFactory 重建时调用，不影响选区/注释层的性能优化。
     */
    private val _contentRenderRevision = mutableIntStateOf(0)
    val contentRenderRevision: Int get() = _contentRenderRevision.intValue

    fun bumpContentRenderRevision() {
        _contentRenderRevision.intValue++
    }

    /**
     * 递增页面范围内所有页面的修订号。
     * 用于选区跨多页变更时，批量触发受影响页面的重绘。
     */
    private fun bumpPageRevisionRange(fromPage: Int, toPage: Int) {
        val start = minOf(fromPage, toPage).coerceAtLeast(0)
        val end = maxOf(fromPage, toPage)
        for (i in start..end) {
            bumpPageRevision(i)
        }
    }

    private val _isSelectionGestureActive = java.util.concurrent.atomic.AtomicBoolean(false)
    val isSelectionGestureActive: Boolean get() = _isSelectionGestureActive.get()
    fun setSelectionGestureActive(active: Boolean) {
        _isSelectionGestureActive.set(active)
    }

    data class CachedSelectionPos(val globalIndex: Int, val lineIndex: Int, val charIndex: Int)

    private var cachedSelectionStart: CachedSelectionPos? = null
    private var cachedSelectionEnd: CachedSelectionPos? = null

    fun updateSelectionCache(
        start: CachedSelectionPos? = null,
        end: CachedSelectionPos? = null
    ) {
        cachedSelectionStart = start
        cachedSelectionEnd = end
    }

    fun clearSelectionCache() {
        _hasActiveSelection = false
        cachedSelectionStart = null
        cachedSelectionEnd = null
    }

    fun getCachedSelectionStart(): CachedSelectionPos? = cachedSelectionStart
    fun getCachedSelectionEnd(): CachedSelectionPos? = cachedSelectionEnd

    /** 是否有正在进行的章节跳转（用于滚动观察者竞态守卫） */
    fun hasPendingJump(): Boolean = pendingJumpTarget.get() != null
    fun hasSelection(): Boolean = cachedSelectionStart != null

    /** 是否有正在执行的 rebuildMergedPages（选区恢复的竞态守卫） */
    val isRebuilding: Boolean get() = refreshJob?.isActive == true

    //是否激活了选择模式
    @Volatile
    private var _hasActiveSelection = false
    val hasActiveSelection: Boolean get() = _hasActiveSelection

    data class SelectionEndpoint(
        val char: TextChar,
        val line: TextLine,
        val itemOffset: Int,
        val charIdx: Int,
        val globalIndex: Int,
        val lineIndex: Int
    )

    // --- 文本选区状态结束 ---

    // --- 注释修订号 ---
    /**
     * 全局注释修订号（递增计数器）。
     *
     * 在 Layer 1.5 的 drawBehind 中读取 annotationRevision 注册 snapshot 依赖。
     * bumpAnnotationRevision() 改变值时，所有可见页面的 Layer 1.5 drawBehind 被失效重绘。
     *
     * 使用全局计数器而非 per-page map，因为 bump 始终针对所有页面，
     * 而 LazyColumn 仅组合可见页面（3-5 页），非可见页面的 drawBehind 不会执行。
     */
    private val _annotationRevision = mutableIntStateOf(0)
    val annotationRevision: Int get() = _annotationRevision.intValue

    private fun bumpAnnotationRevision() {
        _annotationRevision.intValue++
    }


    // 刷新去抖：避免多次重复刷新
    private var refreshJob: kotlinx.coroutines.Job? = null

    // 连续滚动章节加载 Job，用于取消过时的加载
    private var scrollLoadJob: kotlinx.coroutines.Job? = null

    private val loadingLock = Any()

    private fun markLoadingStart() {
        val becameActive = synchronized(loadingLock) {
            _activeLoadCount.getAndIncrement() == 0
        }
        if (becameActive) {
            _isLoading.value = true
            pageController.clickListener?.onContinuousScrollLoadingChanged(true)
        }
    }

    private fun markLoadingEnd() {
        val becameIdle = synchronized(loadingLock) {
            _activeLoadCount.decrementAndGet() == 0
        }
        if (becameIdle) {
            _isLoading.value = false
            pageController.clickListener?.onContinuousScrollLoadingChanged(false)
        }
    }

    /**
     * 合并后的页面项
     * @param globalPageIndex 全局页面索引 (跨章节)
     * @param chapterIndex 所属章节索引
     * @param pageIndex 章节内页面索引
     * @param page 页面数据
     * @param chapterTitle 章节标题
     */
    data class MergedPageItem(
        val globalPageIndex: Int,
        val chapterIndex: Int,
        val pageIndex: Int,
        val page: TextPage,
        val chapterTitle: String,
        val isChapterStart: Boolean = false,  // 是否是章节的第一页
        val isChapterEnd: Boolean = false     // 是否是章节的最后一页
    )

    /**
     * 根据全局页面索引获取页面项
     */
    fun getPageItem(globalIndex: Int): MergedPageItem? {
        return _mergedPages.value.getOrNull(globalIndex)
    }

    /**
     * 根据全局页面索引获取 TextPage
     */
    fun getPage(globalIndex: Int): TextPage? {
        return getPageItem(globalIndex)?.page
    }

    /**
     * 获取当前全局页面索引总数
     */
    fun getPageCount(): Int {
        return _mergedPages.value.size
    }


    /**
     * 根据章节索引和页面索引查找对应的全局页面索引
     * 
     * 此方法用于在模式切换或首次进入时，定位到用户上次的阅读位置。
     * 
     * @param chapterIndex 章节索引
     * @param pageIndex 章节内页面索引
     * @return 全局页面索引（在 mergedPages 中的位置），未找到返回 -1
     */
    fun findGlobalPageIndex(chapterIndex: Int, pageIndex: Int): Int {
        val mergedPages = _mergedPages.value
        val index = mergedPages.indexOfFirst {
            it.chapterIndex == chapterIndex && it.pageIndex == pageIndex
        }
        Logger.d("ContinuousPageProvider:findGlobalPageIndex: chapter=$chapterIndex, page=$pageIndex, globalIndex=$index, totalPages=${mergedPages.size}")
        return index
    }

    /**
     * 清理资源
     */
    suspend fun clear() {
        preloadedChapters.clear()
        preloadNextState?.job?.cancel()
        preloadNextState = null
        preloadPrevState?.job?.cancel()
        preloadPrevState = null
        _mergedPages.value = emptyList()
        _loadedRange.value = Pair(0, 0)
        _isLoading.value = false
        _activeLoadCount.set(0)
        scrollLoadJob?.cancel()
        scrollLoadJob = null
        refreshJob?.cancel()
        refreshJob = null
        _pageSelectionRevisions.clear()  //清理修订号
        pageController.clickListener?.onContinuousScrollLoadingChanged(false)
        pendingJumpTarget.set(null)
        _scrollTarget.value = null
        currentScrollGlobalIndex = -1
        Logger.d("ContinuousPageProvider: 清理完成")
    }

    /**
     * 条件取消预加载任务：只取消不再需要的，保留仍在有效方向的预加载
     *
     * - preloadNext: targetIndex <= theChapterIndex → 已落后，取消
     * - preloadPrev: targetIndex >= theChapterIndex → 已超前，取消
     */
    private fun cancelOutdatedPreloads(theChapterIndex: Int) {
        preloadNextState?.let { state ->
            if (state.targetIndex <= theChapterIndex) {
                state.job.cancel()
                preloadNextState = null
            }
        }
        preloadPrevState?.let { state ->
            if (state.targetIndex >= theChapterIndex) {
                state.job.cancel()
                preloadPrevState = null
            }
        }
    }

    /**
     * 确认章节切换（由 observer 在 chapterIndexChanged 时调用）
     *
     * 快速路径：章节已在缓存中（由 preload 提前加载）→ 同步更新状态，无竞态
     * 慢速路径：章节不在缓存中（preload 失败/未执行）→ 异步加载，取消正在进行的 preload
     *
     * 不调用 onPageChange() — 由 observer 统一处理
     */
    fun loadChaptersForScroll(theChapterIndex: Int) {
        Logger.i("ContinuousPageProvider::loadChaptersForScroll:theChapterIndex=$theChapterIndex")
        val chapterSize = pageController.chapterSize
        if (theChapterIndex !in 0..<chapterSize) {
            Logger.w("ContinuousPageProvider::loadChaptersForScroll:theChapterIndex=$theChapterIndex is invalid")
            return
        }

        // 快速路径：章节已在 3 槽位缓存或预加载缓存中
        val cached = pageController.getCachedChapter(theChapterIndex)
            ?: preloadedChapters[theChapterIndex]
        if (cached != null) {
            Logger.d("ContinuousPageProvider::loadChaptersForScroll: fast path (cached)")
            cancelOutdatedPreloads(theChapterIndex)
            scrollLoadJob?.cancel()
            scrollLoadJob = null
            applyChapterTransition(theChapterIndex, cached)
            return
        }

        // 慢速路径：章节不在缓存中，异步加载
        cancelOutdatedPreloads(theChapterIndex)

        scrollLoadJob?.cancel()
        scrollLoadJob = pageController.scope.launchIO {
            markLoadingStart()
            //先从缓存中拿，没有就去加载
            try {
                //从书籍中解析出来对应的章节
                pageController.loadChapter(
                    chapterIndex = theChapterIndex,
                    upContent = false,
                    resetPageOffset = false,
                    assignToSlot = false
                )?.let { targetChapter ->
                    ensureActive()  // ← 如果 Job 已取消，抛出 CancellationException
                    applyChapterTransition(theChapterIndex, targetChapter)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e("ContinuousPageProvider: loadChaptersForScroll failed for $theChapterIndex: ${e.message}")
            } finally {
                markLoadingEnd()
            }
        }
    }

    /**
     * 应用章节切换：更新阅读位置、迁移槽位、刷新页面列表
     * 由 loadChaptersForScroll 在加载成功后调用（快速路径同步调用，慢速路径在 IO 线程调用）
     */
    private fun applyChapterTransition(theChapterIndex: Int, chapter: TextChapter) {
        val lastCurrentChapter = pageController.curTextChapter
        val currentIdx = pageController.durChapterIndex
        val compare = theChapterIndex - currentIdx
        if (compare != 0) {
            pageController.durChapterIndex = theChapterIndex
            pageController.curTextChapter = chapter
            if (compare == 1) {
                pageController.prevTextChapter = lastCurrentChapter
            } else if (compare == -1) {
                pageController.nextTextChapter = lastCurrentChapter
            } else {
                lastCurrentChapter?.let { preloadedChapters[it.position] = it }
                pageController.prevTextChapter?.let { preloadedChapters[it.position] = it }
                pageController.nextTextChapter?.let { preloadedChapters[it.position] = it }
            }
            refresh()
            pageController.clickListener?.onPageChange()
        } else {
            Logger.d("ContinuousPageProvider::applyChapterTransition: same chapter, skip")
        }
    }

    /**
     * 预加载下一章（用户接近底部时触发）
     * 将目标章节数据缓存到 preloadedChapters，扩展 loadedRange，重建合并页面
     * 不修改 durChapterIndex（不改当前阅读位置）
     */
    fun preloadNextChapter() {
        Logger.i("ContinuousPageProvider:preloadNextChapter->invoke()")
        val lastChapterInPages = _mergedPages.value.lastOrNull()?.chapterIndex ?: return
        val nextChapterIndex = lastChapterInPages + 1
        val chapterSize = pageController.chapterSize
        if (nextChapterIndex >= chapterSize) return
        if (pageController.getCachedChapter(nextChapterIndex) != null) return
        if (preloadedChapters.containsKey(nextChapterIndex)) return

        if (scrollLoadJob?.isActive == true) return  // 章节切换中，不预加载
        Logger.d("ContinuousPageProvider:preloadNextChapter: nextChapterIndex=$nextChapterIndex")

        val state = preloadNextState
        if (state != null && state.job.isActive && state.targetIndex == nextChapterIndex) {
            Logger.d("ContinuousPageProvider:preloadNextChapter: same chapter($nextChapterIndex) already loading, skip")
            return
        }
        state?.job?.cancel()
        preloadNextState = PreloadState(
            job = pageController.scope.launchIO {
                markLoadingStart()
                try {
                    val chapter = pageController.loadChapter(
                        chapterIndex = nextChapterIndex,
                        upContent = false,
                        resetPageOffset = false,
                        assignToSlot = false
                    )
                    if (chapter != null) {
                        ensureActive()  // ← 取消后不再写入 slot 或缓存
                        // 保护 nextTextChapter 槽位：将现有槽位数据迁移到 preloadedChapters
                        val existingNext = pageController.nextTextChapter
                        if (existingNext != null && existingNext.position != nextChapterIndex) {
                            preloadedChapters[existingNext.position] = existingNext
                        }
                        pageController.nextTextChapter = chapter
                        preloadedChapters[nextChapterIndex] = chapter

                        val currentRange = _loadedRange.value
                        val newEnd =
                            minOf(chapterSize - 1, maxOf(currentRange.second, nextChapterIndex))
                        _loadedRange.value = Pair(currentRange.first, newEnd)
                        Logger.d("ContinuousPageProvider:preloadNextChapter: expanded range to [${currentRange.first}, $newEnd]")
                        scheduleRebuild(0)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e("ContinuousPageProvider: preloadNextChapter failed: ${e.message}")
                    try {
                        delay(1000)
                        ensureActive()
                        Logger.d("ContinuousPageProvider: preloadNextChapter retrying chapter $nextChapterIndex")
                        val retryChapter = pageController.loadChapter(
                            chapterIndex = nextChapterIndex,
                            upContent = false,
                            resetPageOffset = false,
                            assignToSlot = false
                        )
                        if (retryChapter != null) {
                            val existingNext = pageController.nextTextChapter
                            if (existingNext != null && existingNext.position != nextChapterIndex) {
                                preloadedChapters[existingNext.position] = existingNext
                            }
                            pageController.nextTextChapter = retryChapter
                            preloadedChapters[nextChapterIndex] = retryChapter
                            val currentRange = _loadedRange.value
                            val newEnd =
                                minOf(chapterSize - 1, maxOf(currentRange.second, nextChapterIndex))
                            _loadedRange.value = Pair(currentRange.first, newEnd)
                            Logger.d("ContinuousPageProvider:preloadNextChapter retry: expanded range to [${currentRange.first}, $newEnd]")
                            scheduleRebuild(0)
                        }
                    } catch (retryEx: kotlinx.coroutines.CancellationException) {
                        throw retryEx
                    } catch (retryEx: Exception) {
                        Logger.e("ContinuousPageProvider: preloadNextChapter retry also failed: ${retryEx.message}")
                    }
                } finally {
                    markLoadingEnd()
                }
            }, targetIndex = nextChapterIndex
        )
    }

    /**
     * 预加载上一章（用户接近顶部时触发）
     */
    fun preloadPrevChapter() {
        Logger.i("ContinuousPageProvider:preloadPrevChapter->invoke()")

        val firstChapterInPages = _mergedPages.value.firstOrNull()?.chapterIndex ?: return
        val prevChapterIndex = firstChapterInPages - 1
        if (prevChapterIndex < 0) return
        if (pageController.getCachedChapter(prevChapterIndex) != null) return
        if (preloadedChapters.containsKey(prevChapterIndex)) return
        if (scrollLoadJob?.isActive == true) return  // 章节切换中，不预加载
        Logger.d("ContinuousPageProvider:preloadPrevChapter: prevChapterIndex=$prevChapterIndex")
        val state = preloadPrevState
        if (state != null && state.job.isActive && state.targetIndex == prevChapterIndex) {
            Logger.d("ContinuousPageProvider:preloadPrevChapter: same chapter($prevChapterIndex) already loading, skip")
            return
        }
        state?.job?.cancel()
        preloadPrevState = PreloadState(
            job = pageController.scope.launchIO {
                markLoadingStart()
                try {
                    val chapter = pageController.loadChapter(
                        chapterIndex = prevChapterIndex,
                        upContent = false,
                        resetPageOffset = false,
                        assignToSlot = false
                    )
                    if (chapter != null) {
                        ensureActive()
                        val existingPrev = pageController.prevTextChapter
                        if (existingPrev != null && existingPrev.position != prevChapterIndex) {
                            preloadedChapters[existingPrev.position] = existingPrev
                        }
                        pageController.prevTextChapter = chapter
                        preloadedChapters[prevChapterIndex] = chapter

                        val currentRange = _loadedRange.value
                        val newStart = maxOf(0, minOf(currentRange.first, prevChapterIndex))
                        _loadedRange.value = Pair(newStart, currentRange.second)
                        Logger.d("ContinuousPageProvider:preloadPrevChapter: expanded range to [$newStart, ${currentRange.second}]")
                        scheduleRebuild(0)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e("ContinuousPageProvider: preloadPrevChapter failed: ${e.message}")
                    try {
                        delay(1000)
                        ensureActive()
                        Logger.d("ContinuousPageProvider: preloadPrevChapter retrying chapter $prevChapterIndex")
                        val retryChapter = pageController.loadChapter(
                            chapterIndex = prevChapterIndex,
                            upContent = false,
                            resetPageOffset = false,
                            assignToSlot = false
                        )
                        if (retryChapter != null) {
                            val existingPrev = pageController.prevTextChapter
                            if (existingPrev != null && existingPrev.position != prevChapterIndex) {
                                preloadedChapters[existingPrev.position] = existingPrev
                            }
                            pageController.prevTextChapter = retryChapter
                            preloadedChapters[prevChapterIndex] = retryChapter
                            val currentRange = _loadedRange.value
                            val newStart = maxOf(0, minOf(currentRange.first, prevChapterIndex))
                            _loadedRange.value = Pair(newStart, currentRange.second)
                            Logger.d("ContinuousPageProvider:preloadPrevChapter retry: expanded range to [$newStart, ${currentRange.second}]")
                            scheduleRebuild(0)
                        }
                    } catch (retryEx: kotlinx.coroutines.CancellationException) {
                        throw retryEx
                    } catch (retryEx: Exception) {
                        Logger.e("ContinuousPageProvider: preloadPrevChapter retry also failed: ${retryEx.message}")
                    }
                } finally {
                    markLoadingEnd()
                }
            }, targetIndex = prevChapterIndex
        )
    }

    /**
     * 统一预加载评估：根据滚动位置和滚动能力决定是否需要预加载
     *
     * 由 ContinuousScrollContent 的统一滚动监听在每次 collect 时调用，
     * 不论章节是否发生变化都执行。
     *
     * 内部调用 preloadNextChapter() / preloadPrevChapter()，
     * 这些方法具有幂等保护（缓存命中/正在加载/已在 preloadedChapters → 直接返回）。
     */
    fun evaluatePreload(
        firstVisibleIndex: Int,
        canScrollForward: Boolean,
        canScrollBackward: Boolean
    ) {
        if (isSelectionGestureActive) return
        val pages = _mergedPages.value
        if (pages.isEmpty()) return
        if (firstVisibleIndex < 0 || firstVisibleIndex >= pages.size) return
        val thresholds = 5
        val totalPages = pages.size

        var nextTriggered = false
        var prevTriggered = false

        // 主动预加载：用户接近边界时提前启动 IO
        if (totalPages >= thresholds) {
            if (firstVisibleIndex >= totalPages - thresholds) {
                preloadNextChapter()
                nextTriggered = true
            }
            if (firstVisibleIndex < thresholds) {
                preloadPrevChapter()
                prevTriggered = true
            }
        }

        // 被动兜底：用户到达绝对边界时触发（仅在阈值预加载未触发时生效）
        if (!nextTriggered && !canScrollForward) {
            preloadNextChapter()
        }
        if (!prevTriggered && !canScrollBackward) {
            preloadPrevChapter()
        }
    }

    /**
     * 刷新当前数据
     * 重建合并页面列表，但不改变已加载的章节范围
     */
    fun refresh() {
        Logger.i("ContinuousPageProvider: refresh")
        val currentChapter = pageController.durChapterIndex
        val desiredStart = maxOf(0, currentChapter - 1)
        val desiredEnd = minOf(pageController.chapterSize - 1, currentChapter + 1)

        // 与现有范围取并集，保留 preload 扩展的范围
        val existingRange = _loadedRange.value
        val newStart = minOf(desiredStart, existingRange.first)
        val newEnd = maxOf(desiredEnd, existingRange.second)

        loadChapterRange(start = newStart, end = newEnd, toLast = false)

        // 清理远离当前位置的预加载缓存（保留 ±3 范围）
        preloadedChapters.keys.removeAll { it < currentChapter - 3 || it > currentChapter + 3 }

        scheduleRebuild()
    }

    /**
     * 调度延迟的 rebuildMergedPages
     * 取消之前未执行的 rebuild，50ms 后执行最新一次,
     * 从而防止重复执行
     */
    private fun scheduleRebuild(delayMs: Long = REBUILD_COALESCE_DELAY_MS) {
        refreshJob?.cancel()
        refreshJob = pageController.scope.launchIO {
            delay(delayMs)
            val currentRange = _loadedRange.value
            rebuildMergedPages(currentRange.first, currentRange.second)
        }
    }

    /**
     * 加载章节范围
     */
    private fun loadChapterRange(start: Int, end: Int, toLast: Boolean) {
        if (start > end) return

        val chapterCount = pageController.chapterSize
        val actualStart = maxOf(0, start)
        val actualEnd = minOf(chapterCount - 1, end)

        Logger.d("ContinuousPageProvider: 加载章节范围 $actualStart - $actualEnd")
        _loadedRange.value = Pair(actualStart, actualEnd)
    }


    /**
     * 重建合并页面列表（不触发异步加载）
     */
    private fun rebuildMergedPages(start: Int, end: Int) {
        if (isSelectionGestureActive) return
        val validStart = start.coerceAtLeast(0)
        val validEnd = end.coerceAtMost(pageController.chapterSize - 1)

        Logger.d("ContinuousPageProvider: rebuilding pages from [$validStart, $validEnd]")

        val mergedList = mutableListOf<MergedPageItem>()
        var globalIndex = 0

        val hadSelection = _hasActiveSelection
        // 重建前记录当前选择的章节/页面
        val oldSelectionRefs = if (hadSelection) {
            val startPos = cachedSelectionStart
            val endPos = cachedSelectionEnd
            val startItem = _mergedPages.value.getOrNull(startPos?.globalIndex ?: -1)
            val endItem = _mergedPages.value.getOrNull(endPos?.globalIndex ?: -1)
            Pair(
                startItem?.let { Pair(it.chapterIndex, it.pageIndex) },
                endItem?.let { Pair(it.chapterIndex, it.pageIndex) }
            )
        } else null

        for (chapterIdx in validStart..validEnd) {
            val chapter = pageController.getCachedChapter(chapterIdx)
                ?: preloadedChapters[chapterIdx]
            if (chapter != null && chapter.pages.isNotEmpty()) {
                chapter.pages.forEachIndexed { pageIdx, page ->
                    mergedList.add(
                        MergedPageItem(
                            globalPageIndex = globalIndex,
                            chapterIndex = chapterIdx,
                            pageIndex = pageIdx,
                            page = page,
                            chapterTitle = chapter.title,
                            isChapterStart = pageIdx == 0,
                            isChapterEnd = pageIdx == chapter.pages.size - 1
                        )
                    )
                    globalIndex++
                }
            } else {
                Logger.d("ContinuousPageProvider:rebuildMergedPages($start,$end), chapterIdx=$chapterIdx, chapter is null")
            }
        }

        _mergedPages.value = mergedList

        // 若本次重建由外部章节跳转触发，在 mergedPages 中查找目标位置并发出滚动请求
        pendingJumpTarget.get()?.let { (chapterIndex, pageIndex) ->
            val globalIndex = mergedList.indexOfFirst {
                it.chapterIndex == chapterIndex && it.pageIndex == pageIndex
            }
            if (globalIndex >= 0) {
                _scrollTarget.value = ScrollTarget(scrollSeq.incrementAndGet(), globalIndex)
                Logger.i("ContinuousPageProvider: 章节跳转滚动请求 rebuildMergedPages: scroll to $globalIndex")
            } else {
                Logger.w("ContinuousPageProvider: 章节跳转滚动请求(chapterIndex=$chapterIndex, pageIndex=$pageIndex) rebuildMergedPages: target not found")
                // 目标不在当前列表中，清除以避免阻塞
                pendingJumpTarget.set(null)
            }
        }

        Logger.d("ContinuousPageProvider: rebuilt ${mergedList.size} pages from [$validStart, $validEnd]")

        //若之前有选择， 更新缓存索引以匹配新列表
        if (hadSelection && oldSelectionRefs != null) {
            updateSelectionIndicesForNewList(
                mergedList,
                oldSelectionRefs.first,
                oldSelectionRefs.second
            )
        }
    }

    /**
     * 清除 pendingJumpTarget。
     * 由 ContinuousScrollContent 在 scrollToItem 完成后调用，
     * 以便滚动观察者恢复正常工作。
     */
    fun clearPendingJump() {
        pendingJumpTarget.set(null)
    }

    /**
     * 主动设置待跳转目标。用于旋转/尺寸变化重排后，在 rebuildMergedPages 完成时
     * 自动滚动到指定 (chapterIndex, pageIndex)，保持阅读位置。
     * 复用既有 pendingJumpTarget → scrollTarget 通道（见 rebuildMergedPages 末尾的读取逻辑）。
     */
    fun setPendingJump(chapterIndex: Int, pageIndex: Int) {
        pendingJumpTarget.set(Pair(chapterIndex, pageIndex))
    }

    /***
     * 更新文字选区的开始/结束索引，
     * 当 _mergedPages 重建时，可能会发生其对应的 globalPageIndex 发生了改变，
     * 如果发生了改变，则需要重新更新缓存的cachedSelectionStart/cachedSelectionEnd
     */
    private fun updateSelectionIndicesForNewList(
        newList: List<MergedPageItem>,
        oldStartRef: Pair<Int, Int>?,  // (chapterIndex, pageIndex)
        oldEndRef: Pair<Int, Int>?     // (chapterIndex, pageIndex)
    ) {
        var newStartGlobal: Int? = null
        var newEndGlobal: Int? = null

        for (item in newList) {
            if (oldStartRef != null
                && item.chapterIndex == oldStartRef.first
                && item.pageIndex == oldStartRef.second
            ) {
                newStartGlobal = item.globalPageIndex
            }
            if (oldEndRef != null
                && item.chapterIndex == oldEndRef.first
                && item.pageIndex == oldEndRef.second
            ) {
                newEndGlobal = item.globalPageIndex
            }
            if (newStartGlobal != null && (oldEndRef == null || newEndGlobal != null)) {
                break
            }
        }

        // 更新缓存的 globalIndex，lineIndex/charIndex 不变
        cachedSelectionStart = cachedSelectionStart?.let { old ->
            newStartGlobal?.let {
                CachedSelectionPos(it, old.lineIndex, old.charIndex)
            }
        }
        cachedSelectionEnd = cachedSelectionEnd?.let { old ->
            newEndGlobal?.let { CachedSelectionPos(it, old.lineIndex, old.charIndex) }
        }
        // 若选区页面已不在列表中，清除选区
        if (cachedSelectionStart == null || cachedSelectionEnd == null) {
            Logger.d("updateSelectionIndicesForNewList: 选区页面不在新列表中，清除选区")
            clearSelectionCache()
        }
    }


    // ========== IDataSource 实现 ==========
    override fun upContent(relativePosition: Int, resetPageOffset: Boolean) {
        bumpAnnotationRevision()  // ← 单次 ++，无条件执行，放在 guard 之前
        if (isSelectionGestureActive) return
        Logger.i("ContinuousPageProvider: upContent:relativePosition=$relativePosition, resetPageOffset=$resetPageOffset")
        if (pageController.isBatchLoading) return

        // 捕获外部跳转目标（changeChapter、changeChapterAndPage 等触发 loadContent 后回调至此）
        // 仅当目标位置与当前滚动位置不同时才捕获，避免注释点击、refreshView 等
        // 非导航触发的 upContent 将用户拉回旧位置
        val targetGlobalIndex =
            findGlobalPageIndex(pageController.durChapterIndex, pageController.durPageIndex)
        if (targetGlobalIndex != currentScrollGlobalIndex) {
            pendingJumpTarget.set(Pair(pageController.durChapterIndex, pageController.durPageIndex))
        }

        refresh()
        pageController.screenOffTimerStart()
    }

    override fun upStyle() {
        Logger.d("ContinuousPageProvider: upStyle - clearing preloaded chapters")
        scrollLoadJob?.cancel()
        preloadNextState?.job?.cancel()
        preloadPrevState?.job?.cancel()
        preloadNextState = null
        preloadPrevState = null
        scrollLoadJob = null
        preloadedChapters.clear()
        //preloadedChapters清空了， 需要重置 range 到 currentChapter ±1（不取并集），然后在refresh中重新加载
        val currentChapter = pageController.durChapterIndex
        _loadedRange.value = Pair(
            maxOf(0, currentChapter - 1),
            minOf(pageController.chapterSize - 1, currentChapter + 1)
        )
        if (pageController.isLoadingContent) {
            // loadContent 正在运行 → 取消 pending rebuild，等 loadContent 回调触发
            refreshJob?.cancel()
            refreshJob = null
        } else {
            // 没有正在运行的 loadContent → 自己 rebuild（防御性兜底）
            refresh()
        }
    }

    override fun upTipStyle() {
        // do nothing
    }

    override fun upBg() {
        // do nothing
    }

    override fun cancelTextSelected() {
        val startPage = cachedSelectionStart?.globalIndex
        val endPage = cachedSelectionEnd?.globalIndex
        _hasActiveSelection = false
        clearSelectionCache()
        if (startPage != null && endPage != null) {
            bumpPageRevisionRange(startPage, endPage)
        }
    }

    override fun moveToPrevPage() {
        // do nothing
    }

    override fun moveToNextPage() {
        // do nothing
    }

    // ========== PageCallback 实现 ==========

    override fun loadChapterList(book: Book) {
        // 连续滚动模式不需要加载章节列表（由 ViewModel 处理）
        Logger.d("ContinuousPageProvider: loadChapterList - no op")
    }

    /***
     * 进入应用之后，PageViewController会执行loadContent(true) 加载三个章节，
     * 当前章节会回调 upView
     */
    override fun upView() {
        // 刷新视图（用于批量更新或样式修改后）
        Logger.d("ContinuousPageProvider: upView - triggering refresh")
        if (pageController.isBatchLoading) return
        refresh()
    }

    override fun clearBitmapCache() {
    }

    override fun contentLoadFinish() {
        // 内容加载完成，不需要特殊处理
        Logger.d("ContinuousPageProvider: contentLoadFinish - no op")
    }

    override fun upPageAnim() {
        // 不使用翻页动画
    }

    override fun upPageControl() {
        // 不使用页面控制更新
    }

    override fun getSelectedText(): String {
        val locator = pageController.getSelectionLocator() ?: return ""
        val sb = StringBuilder()
        var lastParagraphIndex = -1

        for (pageItem in _mergedPages.value) {
            if (pageItem.page.chapterIndex != locator.chapterIndex) continue
            for (line in pageItem.page.textLines) {
                if (line.isImage || line.isLine) continue
                if (line.paragraphIndex < locator.startParagraphIndex ||
                    line.paragraphIndex > locator.endParagraphIndex
                ) continue

                val lineSb = StringBuilder()
                for (i in line.textChars.indices) {
                    val offset = i + line.charStartOffset
                    if (isOffsetInTextSelection(locator, line.paragraphIndex, offset)) {
                        val ch = line.textChars[i]
                        if (!ch.isImage && ch.charData.isNotEmpty()) {
                            lineSb.append(ch.charData)
                        }
                    }
                }
                if (lineSb.isNotEmpty()) {
                    if (line.paragraphIndex != lastParagraphIndex && lastParagraphIndex >= 0) {
                        sb.append("\n")
                    }
                    lastParagraphIndex = line.paragraphIndex
                    sb.append(lineSb)
                }
            }
        }
        return sb.toString()
    }

    fun updateSelectionState(
        chapterIndex: Int,
        start: SelectionEndpoint,
        end: SelectionEndpoint,
        pageController: PageViewController
    ) {
        _hasActiveSelection = true

        pageController.setSelectionChapterIndex(chapterIndex)
        pageController.upSelectedStart(
            start.char.start,
            start.itemOffset + start.line.lineBottom,
            start.itemOffset + start.line.lineTop,
            start.line.paragraphIndex,
            start.line.charStartOffset + start.charIdx
        )
        pageController.upSelectedEnd(
            end.char.end,
            end.itemOffset + end.line.lineBottom,
            end.line.paragraphIndex,
            end.line.charStartOffset + end.charIdx
        )

        // 记录旧选区范围（在 cache 更新之前）
        val oldStartPage = cachedSelectionStart?.globalIndex
        val oldEndPage = cachedSelectionEnd?.globalIndex

        updateSelectionCache(
            start = CachedSelectionPos(start.globalIndex, start.lineIndex, start.charIdx),
            end = CachedSelectionPos(end.globalIndex, end.lineIndex, end.charIdx)
        )

        // bump 旧范围 ∪ 新范围内的所有页面
        // 旧范围页面需要重绘以清除已消失的选区背景
        // 新范围页面需要重绘以绘制新增的选区背景
        bumpPageRevisionRange(
            fromPage = minOf(oldStartPage ?: start.globalIndex, start.globalIndex),
            toPage = maxOf(oldEndPage ?: end.globalIndex, end.globalIndex)
        )

    }
}