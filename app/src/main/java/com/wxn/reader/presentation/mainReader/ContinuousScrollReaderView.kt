package com.wxn.reader.presentation.mainReader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withClip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.wxn.base.bean.Book
import com.wxn.base.bean.CssVerticalAlign
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.bean.InlineCssProps
import com.wxn.base.bean.InlineStyle
import com.wxn.base.bean.ReaderText
import com.wxn.base.bean.TextCssInfo
import com.wxn.base.bean.TextTag
import com.wxn.base.bean.TtsPlaybackStatus
import com.wxn.base.ext.isCJKChar
import com.wxn.base.ext.isPunctuation
import com.wxn.base.ext.toColor
import com.wxn.base.ext.toComposeColor
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.bookread.data.model.TextChar
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.model.TextPage
import com.wxn.bookread.data.model.arrayIndexAt
import com.wxn.bookread.data.model.preference.ReaderPreferences
import com.wxn.bookread.data.model.textIndexAt
import com.wxn.bookread.data.model.visualSpan
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.provider.ImageProvider
import com.wxn.bookread.provider.TableRenderProvider
import com.wxn.bookread.ui.ListDotRenderer
import com.wxn.bookread.ui.RenderResources
import com.wxn.bookread.ui.TextPageFactory
import com.wxn.reader.presentation.mainReader.models.ScrollSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/***
 * 连续垂直滚动模式下的阅读页面
 */
@Composable
fun ContinuousScrollReaderView(viewModel: MainReadViewModel) {

    val context = LocalContext.current
    val readerPreferences by viewModel.readerPreferences.collectAsStateWithLifecycle()
    val book by viewModel.book.collectAsStateWithLifecycle()

    val pageProvider by viewModel::pageProvider

    //屏幕的宽高。Activity 因 configChanges 旋转不重建，需要主动观察配置变化：
    //用 LocalConfiguration 的 orientation/screenWidthDp/screenHeightDp 作为 remember 的 key，
    //任一变化（旋转/分屏/折叠屏展开）→ screenSize 重算 → LaunchedEffect 重触发。
    val configuration = LocalConfiguration.current
    val screenSize = remember(
        configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp
    ) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        android.util.Size(metrics.widthPixels, metrics.heightPixels)
    }

    //记录上次已应用尺寸，区分「冷启动首次」(prev==null) 与「旋转/尺寸变化」(prev!=null && prev!=screenSize)
    var lastAppliedSize by remember { mutableStateOf<android.util.Size?>(null) }

    //检测屏幕的宽高的变化
    LaunchedEffect(screenSize) {
        val w = screenSize.width
        val h = screenSize.height
        val prev = lastAppliedSize
        val sizeChanged = (prev != null && prev != screenSize)

        if (sizeChanged) {
            //===== 旋转/尺寸变化分支（镜像 PageView.onSizeChanged）=====

            //旋转重排前主动取消文本选区，防止选区缓存指向失效的旧 TextPage
            if (pageProvider.hasSelection() || viewModel.showTextToolbar.value) {
                viewModel.textToolbarOpen(false)
                viewModel.cancelTextSelected()
            }

            //同步更新度量（含等比缩放边距），不先 setViewSize（否则 synchronouslyUpdateLayout
            //会因 w==viewWidth 早返回失效）
            ChapterProvider.synchronouslyUpdateLayout(w, h, prev!!.width, prev!!.height)

            //重排前主动设置 pendingJump，确保 rebuildMergedPages 完成后滚动定位
            pageProvider.setPendingJump(
                viewModel.pageController.durChapterIndex,
                viewModel.pageController.durPageIndex
            )

            //复用既有唯一重排触发器：upStyle → loadContent(重分页3槽位) → callBack.upStyle(清preloaded)
            viewModel.pageController.updatePageViews(resetPageOffset = false)
        } else {
            //===== 冷启动首次分支（prev==null）或尺寸未变 =====
            //更新ChapterProvider
            ChapterProvider.setViewSize(context, w, h)
            viewModel.pageController.clickListener = viewModel
            viewModel.pageController.callBack = pageProvider
            viewModel.pageController.pageFactory =
                TextPageFactory(pageProvider, viewModel.pageController)

            viewModel.pageController.navigationLoadingListener = object: PageViewController.OnNavigationLoadingListener {
                override fun onNavigationLoadingStart(
                    targetChapterIndex: Int,
                    immediate: Boolean) {
                    viewModel.viewModelScope.launch(Dispatchers.Main.immediate) {
                        viewModel.handleNavigationLoadingStart(targetChapterIndex, immediate)
                    }
                }

                override fun onNavigationLoadingComplete(chapterIndex: Int) {
                    viewModel.viewModelScope.launch(Dispatchers.Main.immediate) {
                        viewModel.handleNavigationLoadingComplete(chapterIndex)
                    }
                }

                override fun onNavigationLoadingError(chapterIndex: Int) {
                    viewModel.viewModelScope.launch(Dispatchers.Main.immediate) {
                        viewModel.handleNavigationLoadingError(chapterIndex)
                    }
                }
            }

            // pageFactory 就绪后触发文本层重绘：
            // 导航返回时 onDispose 已将 pageFactory 置 null，重建是异步的，
            // 首帧绘制时 pageFactory 可能为 null 导致 getPagesAnnotation 返回空 tags。
            // 此处 bump 确保所有可见 TextPageCanvas 的 Layer 2 在 pageFactory 就绪后重绘。
            pageProvider.bumpContentRenderRevision()

            //兜底刷新（首次冷启动或异常恢复）
            if (pageProvider.mergedPages.value.isEmpty()
                && viewModel.pageController.isInitFinish
                && viewModel.pageController.curTextChapter != null) {
                pageProvider.upContent(relativePosition = 0, resetPageOffset = false)
            }
        }

        lastAppliedSize = screenSize
    }

    //加载的页面集合
    val mergedPages by pageProvider.mergedPages.collectAsStateWithLifecycle()
    val showContinuousLoading by viewModel.continuousScrollLoading.collectAsStateWithLifecycle()
    //背景图
    val backgroundImageFile = remember(readerPreferences.backgroundImage) {
        val imgPath = readerPreferences.backgroundImage
        if (imgPath.isEmpty()) return@remember null

        val file = when {
            imgPath.startsWith("/") -> File(imgPath)
            else -> File(PathUtil.getDownloadFilePath(context, DownloadFileType.BG_IMAGE, imgPath, imgPath))
        }
        if (file.exists() && file.canRead()) file else null
    }

    //背景颜色
    val bgColor = remember(readerPreferences.backgroundColor) {
        readerPreferences.backgroundColor.toComposeColor()
    }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    var isHandleDragging by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onContinuousScrollLoadingChanged(false)
            if (pageProvider.hasSelection()) {
                viewModel.textToolbarOpen(false)
                viewModel.cancelTextSelected()
            }
            if (viewModel.pageController.callBack === pageProvider) {
                viewModel.pageController.callBack = null
                viewModel.pageController.pageFactory = null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(mergedPages) {
                val longPressTimeout = 600L
                val slop = viewConfiguration.touchSlop
                val handleSlopPx = slop * 4
                val density = this@pointerInput.density
                val handleRadiusPx = with(density) { 10.dp.toPx() }
                val handleLineHeightPx = with(density) { 24.dp.toPx() }
                Logger.d("ContinuousScrollReaderView: pointerInput####")

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    down.consume()

                    //TextToolbar操作过程中，不处理事件
                    if (viewModel.isToolbarTouchActive.value) {
                        return@awaitEachGesture
                    }

                    //已经处于文本选择状态下的操作
                    if (pageProvider.hasSelection()) {
                        Logger.d("ContinuousScrollReaderView: hasSelection() true##")
                        val handleMode = detectHandleHit(
                            downPos.x, downPos.y,
                            pageProvider, lazyListState, mergedPages,
                            handleSlopPx, handleRadiusPx, handleLineHeightPx
                        )
                        Logger.d("ContinuousScrollReaderView: handleMode=$handleMode")
                        //没有点击到开始结束的icon
                        if (handleMode != HandleDragMode.NONE) {
                            isHandleDragging = true
                            pageProvider.setSelectionGestureActive(true)
                            try {
                                //处理开始结束icon的拖拽操作， 这里是一个循环操作
                                handleDragLoop(
                                    handleMode, downPos,
                                    viewModel, pageProvider, lazyListState, mergedPages,
                                    handleRadiusPx,
                                    handleLineHeightPx
                                )
                            } finally {
                                pageProvider.setSelectionGestureActive(false)
                                isHandleDragging = false
                            }
                            return@awaitEachGesture
                        }
                    }

                    //抬起，取消，移动的操作类型判断
                    val upResult = withTimeoutOrNull(longPressTimeout) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                                ?: return@withTimeoutOrNull "cancel"
                            if (change.changedToUpIgnoreConsumed()) return@withTimeoutOrNull "up"
                            val dx = abs(change.position.x - downPos.x)
                            val dy = abs(change.position.y - downPos.y)
                            if (dx > slop || dy > slop) {
                                return@withTimeoutOrNull "move"
                            }
                        }
                        "cancel"
                    }

                    when {
                        upResult == "up" -> { //手势抬起
                            Logger.d("ContinuousScrollReaderView: upResult=up")
                            if (pageProvider.hasSelection() || viewModel.showTextToolbar.value) {
                                //当处于文字选择状态或标注编辑弹窗打开时点击都会取消
                                viewModel.textToolbarOpen(false)
                                viewModel.cancelTextSelected()
                            } else {
                                //处理点击操作
                                handleTap(
                                    this@pointerInput, downPos, viewModel,
                                    readerPreferences, lazyListState,
                                    pageProvider, mergedPages
                                )
                            }
                        }

                        upResult == "move" -> {            // 移动时处理
                            Logger.d("ContinuousScrollReaderView: upResult=move")
                            if (pageProvider.hasSelection() || viewModel.showTextToolbar.value) {
                                //当处于文字选择状态或标注编辑弹窗打开时移动都会取消
                                viewModel.textToolbarOpen(false)
                                viewModel.cancelTextSelected()
                            }
                        }

                        upResult == null -> {
                            Logger.d("ContinuousScrollReaderView: upResult=null")
                            pageProvider.setSelectionGestureActive(true)
                            isHandleDragging = true
                            try {
                                performLongPress(
                                    downPos, viewModel, pageProvider,
                                    lazyListState, mergedPages, hapticFeedback
                                )
                                // 等待手指抬起，消耗剩余事件防止 MOVE 泄漏到 LazyColumn 导致误滚动
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    change.consume()
                                    if (change.changedToUpIgnoreConsumed()) break
                                }
                            } finally {
                                pageProvider.setSelectionGestureActive(false)
                                isHandleDragging = false
                            }
                        }

                        else -> {
                            Logger.d("ContinuousScrollReaderView: else -> upResult")
                            // "cancel" 或其他意外值：取消选区
                            if (pageProvider.hasSelection() || viewModel.showTextToolbar.value) {
                                viewModel.textToolbarOpen(false)
                                viewModel.cancelTextSelected()
                            }
                        }
                    }
                }
            }
    ) {
        if (backgroundImageFile != null) {
            AsyncImage(
                model = backgroundImageFile,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
            )
        }

        if (mergedPages.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                ContinuousScrollContent(
                    context = context,
                    pageProvider = pageProvider,
                    viewModel = viewModel,
                    lazyListState = lazyListState,
                    isHandleDragging = isHandleDragging,
                )

                AnimatedVisibility(
                    visible = showContinuousLoading,
                    enter = fadeIn(animationSpec = tween(200)),
                    exit = fadeOut(animationSpec = tween(200)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = androidx.compose.ui.graphics.Color.Transparent,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(com.wxn.reader.R.string.loading_chapters),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

/***
 * 纯书籍数据的渲染显示
 */
@OptIn(FlowPreview::class)
@Composable
fun ContinuousScrollContent(
    context: Context,
    pageProvider: ContinuousPageProvider,
    viewModel: MainReadViewModel,
    lazyListState: LazyListState,
    isHandleDragging: Boolean,
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val mergedPages by pageProvider.mergedPages.collectAsStateWithLifecycle()

    // 当前可见的章节索引
    var currentVisibleChapter by remember { mutableIntStateOf(-1) }
    //当前可见的页面索引
    var currentVisiblePage by remember { mutableIntStateOf(-1) }
    //是否滚动到初始位置？
    var hasScrolledToInitialPosition by remember { mutableStateOf(false) }

    val lastSaveReadTime = remember { AtomicLong(System.currentTimeMillis()) }

    // 滚动时取消选区（独立流，无 debounce）
    LaunchedEffect(lazyListState) {
        var lastIndex = lazyListState.firstVisibleItemIndex
        var lastOffset = lazyListState.firstVisibleItemScrollOffset

        snapshotFlow {
            Triple(
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset,
                System.currentTimeMillis()
            )
        }.debounce(100)  // 避免滚动过程中频繁取消
            .collect { (index, offset, _) ->
                if ((index != lastIndex || abs(offset - lastOffset) > 50)
                    && !pageProvider.isSelectionGestureActive
                ) {
                    if (pageProvider.hasSelection() || viewModel.showTextToolbar.value) {
                        viewModel.textToolbarOpen(false)
                        viewModel.cancelTextSelected()
                    }
                }

                lastIndex = index
                lastOffset = offset
            }
    }

    //观察页面数量的变化
    LaunchedEffect(mergedPages.size) {
        if (mergedPages.isNotEmpty() && !hasScrolledToInitialPosition) {
            //当前章节索引
            val durChapterIndex = pageProvider.pageController.durChapterIndex
            //当前章节下的页面索引
            val durPageIndex = pageProvider.pageController.durPageIndex

            // 在 _mergedPages 中的索引，找到上次的位置，
            val globalPageIndex = pageProvider.findGlobalPageIndex(durChapterIndex, durPageIndex)
            // 并滚动到该位置
            if (globalPageIndex >= 0) {
                lazyListState.scrollToItem(globalPageIndex)
            }
            hasScrolledToInitialPosition = true
        }
    }

    // 观察外部章节跳转请求（章节列表点击、书签导航等触发）
    LaunchedEffect(pageProvider) {
        pageProvider.scrollTarget.collect { target ->
            if (target != null
                && target.globalIndex >= 0
                && target.globalIndex < pageProvider.getPageCount()
            ) {
                Logger.d("ContinuousScrollContent: scrollToItem → ${target.globalIndex}")
                try {
                    lazyListState.scrollToItem(target.globalIndex)
                    pageProvider.currentScrollGlobalIndex = target.globalIndex
                } finally {
                    pageProvider.clearPendingJump() //滚动完成后清除，恢复观察者正常工作
                }
            }
        }
    }

    // 统一滚动状态监听：合并章节检测、阈值预加载、兜底预加载为单一数据流
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            ScrollSnapshot(
                firstVisibleIndex = lazyListState.firstVisibleItemIndex,
                canScrollForward = lazyListState.canScrollForward,
                canScrollBackward = lazyListState.canScrollBackward,
                pageCount = mergedPages.size  // dirty flag: mergedPages 变化时重新触发评估
            )
        }
            .debounce(100)
            .collect { snapshot ->
                if (mergedPages.isEmpty()) return@collect

                val currentPageItem = mergedPages.getOrNull(snapshot.firstVisibleIndex)
                val newChapterIndex = currentPageItem?.chapterIndex ?: -1
                val newPageIndex = currentPageItem?.pageIndex ?: -1
                pageProvider.currentScrollGlobalIndex = snapshot.firstVisibleIndex
                Logger.d("ContinuousScrollContent: firstVisibleIndex=${snapshot.firstVisibleIndex}, chapter=$newChapterIndex, page=$newPageIndex")

                if (newChapterIndex >= 0 && newPageIndex >= 0) {
                    val oldChapterIndex = pageProvider.pageController.durChapterIndex
                    val oldPageIndex = pageProvider.pageController.durPageIndex
                    Logger.d("ContinuousScrollContent: oldChapterIndex=$oldChapterIndex, oldPageIndex=$oldPageIndex")

                    val chapterIndexChanged = (newChapterIndex != oldChapterIndex)
                    val pageIndexChanged = (newPageIndex != oldPageIndex)

                    val isBatchLoading = pageProvider.pageController.isBatchLoading
                    if (chapterIndexChanged && !pageProvider.hasPendingJump() && !isBatchLoading) {
                        pageProvider.loadChaptersForScroll(newChapterIndex)
                    }
                    if (!pageProvider.hasPendingJump() && !isBatchLoading) {
                        pageProvider.pageController.durPageIndex = newPageIndex
                    }

                    val newProgression = if (pageProvider.pageController.curTextChapter != null
                        && pageProvider.pageController.curTextChapter?.position == newChapterIndex
                    ) {
                        pageProvider.pageController.progression
                    } else {
                        viewModel.readProgression.value
                    }
                    viewModel.updateProgressionFromScroll(
                        newProgression, newChapterIndex, newPageIndex,
                        currentPageItem?.chapterTitle
                    )

                    if (chapterIndexChanged || pageIndexChanged) {
                        pageProvider.pageController.clickListener?.onPageChange()
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastSaveReadTime.get() >= 5000) {
                        pageProvider.pageController.saveRead()
                        lastSaveReadTime.set(now)
                    }
                    currentVisibleChapter = newChapterIndex
                    currentVisiblePage = newPageIndex
                }
                // 预加载评估：不论章节是否变化都执行
                pageProvider.evaluatePreload(
                    firstVisibleIndex = snapshot.firstVisibleIndex,
                    canScrollForward = snapshot.canScrollForward,
                    canScrollBackward = snapshot.canScrollBackward
                )
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (currentVisibleChapter >= 0) {
                pageProvider.pageController.durChapterIndex = currentVisibleChapter
                pageProvider.pageController.durPageIndex = currentVisiblePage
                pageProvider.pageController.saveRead()
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !isHandleDragging
    ) {
        itemsIndexed(
            items = mergedPages,
            key = { _, item -> "${item.chapterIndex}_${item.pageIndex}" },
            contentType = { _, item ->
                if (item.isChapterStart && item.chapterTitle.isNotEmpty()) "chapterStart" else "mergedPage"
            }
        ) { index, mergedPageItem ->
            TextPageCanvas(
                textPage = mergedPageItem.page,
                book = book,
                pageProvider = pageProvider,
                context = context,
                globalPageIndex = index
            )
        }

        if (mergedPages.isNotEmpty()) {
            val lastPage = mergedPages.lastOrNull()
            if (lastPage != null && lastPage.isChapterEnd
                && lastPage.chapterIndex >= pageProvider.pageController.chapterSize - 1
            ) {
                item(key = "end_of_book") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(com.wxn.reader.R.string.end_of_book),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun checkTagInLineRect(
    tag: TextTag,
    line: TextLine,
    clickX: Float,
    localY: Float,
    padding: Float
): Boolean {
    Logger.i("ContinuousScrollReaderView::checkTagInLineRect:tag=$tag,line=$line, clickX=$clickX, localY=$localY,padding=$padding")
    var startIdx = -1
    var endIdx = -1

    if (tag.start >= line.charStartOffset && tag.start <= line.charEndOffset) {
        // 文本口径 → 数组口径（图片占数组位不占文本位，M2-③）
        startIdx = line.arrayIndexAt(tag.start - line.charStartOffset)
    } else if (tag.start < line.charStartOffset) {
        startIdx = 0
    }
    Logger.d("ContinuousScrollReaderView::checkTagInLineRect:startIdx=$startIdx")

    if (tag.end >= line.charStartOffset && tag.end <= line.charEndOffset) {
        endIdx = line.arrayIndexAt(tag.end - line.charStartOffset)
    } else if (tag.end > line.charEndOffset) {
        endIdx = line.textChars.size - 1
    }
    Logger.d("ContinuousScrollReaderView::checkTagInLineRect:endIdx=$endIdx")

    if (startIdx !in 0 until line.textChars.size) return false
    if (endIdx !in 1 .. line.textChars.size) return false

    val startChar = line.textChars[startIdx]
    val endChar = if (endIdx < line.textChars.size) line.textChars[endIdx]
                  else line.textChars.lastOrNull() ?: return false
    Logger.d("ContinuousScrollReaderView::startChar=$startChar,endChar=$endChar")

    val lineRect = RectF(
        startChar.start - padding,
        line.lineTop - padding,
        endChar.end + padding,
        line.lineBottom + padding
    )

    Logger.d("ContinuousScrollReaderView::lineRect=$lineRect, clickX=$clickX,clickY=$localY")
    return lineRect.contains(clickX, localY)
}

/**
 * 高亮注释范围并返回屏幕矩形。
 *
 * 约束：当匹配的注释 tag 分布在多个非连续段落时，Locator 会覆盖中间段落，
 * 导致过度高亮。实际调用场景中注释几乎总在同一段落，此行为可接受。
 */
private fun highlightAnnotationAndGetRect(
    pageProvider: ContinuousPageProvider,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>,
    globalIndex: Int,
    tagUuids: List<String>,
    tagNames: List<String>,
    itemOffset: Int,
    padding: Float
): RectF? {
    Logger.i("ContinuousScrollReaderView::highlightAnnotationAndGetRect:globalIndex=$globalIndex")
    val pageItem = mergedPages.getOrNull(globalIndex) ?: return null
    val textPage = pageItem.page
    val chapterIndex = textPage.chapterIndex
    val pageFactory = pageProvider.pageController.pageFactory ?: return null

    pageProvider.cancelTextSelected()

    var startChar: TextChar? = null
    var startLine: TextLine? = null
    var startCharIdx = -1
    var startLineIdx = -1
    var endChar: TextChar? = null
    var endLine: TextLine? = null
    var endCharIdx = -1
    var endLineIdx = -1

    for ((lIdx, line) in textPage.textLines.withIndex()) {
        if (line.isImage || line.isLine) continue
        val lineOffset = if (line.isTableCell) line.rowLineOffset else 0

        val (tags, _) = pageFactory.getPagesAnnotation(
            chapterIndex,
            line.paragraphIndex,
            line.charStartOffset + lineOffset,
            line.charEndOffset + lineOffset
        )

        val matchingTags = tags.filter {
            tagUuids.contains(it.uuid) && tagNames.contains(it.name)
        }
        if (matchingTags.isEmpty()) continue

        for ((idx, ch) in line.textChars.withIndex()) {
            val charOffset = line.charStartOffset + line.textIndexAt(idx)
            val inRange = matchingTags.any { tag ->
                charOffset >= tag.start && charOffset < tag.end
            }
            if (inRange && !ch.isImage && ch.charData.isNotEmpty()) {
                if (startChar == null) {
                    startChar = ch
                    startLine = line
                    startCharIdx = idx
                    startLineIdx = lIdx
                }
                endChar = ch
                endLine = line
                endCharIdx = idx
                endLineIdx = lIdx
            }
        }
    }

    if (startChar == null || endChar == null || startLine == null || endLine == null) return null

    pageProvider.updateSelectionState(
        chapterIndex = chapterIndex,
        start = ContinuousPageProvider.SelectionEndpoint(
            startChar!!, startLine!!, itemOffset, startCharIdx, globalIndex, startLineIdx
        ),
        end = ContinuousPageProvider.SelectionEndpoint(
            endChar!!, endLine!!, itemOffset, endCharIdx, globalIndex, endLineIdx
        ),
        pageController = pageProvider.pageController
    )

    return RectF(
        startChar!!.start - padding,
        itemOffset + startLine!!.lineTop,
        endChar!!.end + padding,
        itemOffset + endLine!!.lineBottom
    )
}

private fun handleAnnotationTap(
    offset: Offset,
    viewModel: MainReadViewModel,
    pageProvider: ContinuousPageProvider,
    lazyListState: LazyListState,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>
): Boolean {
    Logger.i("ContinuousScrollReaderView::handleAnnotationTap:offset=$offset")
    val clickX = offset.x
    val clickY = offset.y
    val padding = 10f
    val res = RenderResources

    var pageItem: ContinuousPageProvider.MergedPageItem? = null
    var globalIndex = -1
    var itemOffset = 0

    for (itemInfo in lazyListState.layoutInfo.visibleItemsInfo) {
        val itemTop = itemInfo.offset
        val itemBottom = itemTop + itemInfo.size
        if (clickY >= itemTop && clickY < itemBottom) {
            pageItem = mergedPages.getOrNull(itemInfo.index)
            globalIndex = itemInfo.index
            itemOffset = itemTop
            break
        }
    }
    if (pageItem == null) {
        Logger.d("handleAnnotationTap::pageItem is null")
        return false
    }

    val textPage = pageItem.page
    val chapterIndex = textPage.chapterIndex
    val localY = clickY - itemOffset
    val pageFactory = pageProvider.pageController.pageFactory
    Logger.d("handleAnnotationTap::chapterIndex=$chapterIndex,localY=$localY,itemOffset=$itemOffset")

    if (clickX <= 3 * res.dp12 && pageFactory != null) {
        Logger.d("handleAnnotationTap::clickX <= 3 * res.dp12")
        for (line in textPage.textLines) {
            if (line.isImage || line.isLine) continue
            val dy = abs(localY - line.lineTop)
            if (dy > res.dp12 * 1.5f) continue

            val lineOffset = if (line.isTableCell) line.rowLineOffset else 0
            val (tags, _) = pageFactory.getPagesAnnotation(
                chapterIndex,
                line.paragraphIndex,
                line.charStartOffset + lineOffset,
                line.charEndOffset + lineOffset
            )
            val noteTag = tags.firstOrNull {
                it.name == "note" && it.params.isNotEmpty() && it.params.contains("color")
            }
            if (noteTag != null) {
                Logger.d("handleAnnotationTap::noteTag not null, uuid=${noteTag}, then invoke onCheckedNote")
                val rect = highlightAnnotationAndGetRect(
                    pageProvider, mergedPages, globalIndex,
                    listOf(noteTag.uuid), listOf("note"), itemOffset, padding
                )
                viewModel.onCheckedNote(noteTag.uuid, rect ?: RectF())
                return true
            }
        }
    }

    var clickLine: TextLine? = null
    for (line in textPage.textLines) {
        if (localY >= line.lineTop && localY <= line.lineBottom) {
            clickLine = line
            break
        }
    }
    val line = clickLine ?: run {
        Logger.d("handleAnnotationTap::clickLine is null")
        return false
    }
    Logger.d("handleAnnotationTap::line[$line]")

    val lineOffset = if (line.isTableCell) line.rowLineOffset else 0
    val (tags, _) = pageFactory?.getPagesAnnotation(
        chapterIndex,
        line.paragraphIndex,
        line.charStartOffset + lineOffset,
        line.charEndOffset + lineOffset
    ) ?: (emptyList<TextTag>() to null)

    val filterTags = tags.filter { item ->
        (item.name == "a" && item.params.isNotEmpty() && item.params.contains("href")) ||
        ((item.name == "underline" || item.name == "highlight") &&
         item.params.isNotEmpty() && item.params.contains("color")) ||
        (item.name == "note" && item.params.isNotEmpty() && item.params.contains("color"))
    }
    if (filterTags.isEmpty()) {
        Logger.d("handleAnnotationTap::filterTags is empty")
        return false
    }
    Logger.d("handleAnnotationTap::filterTags[$filterTags]")

    val noteTag = filterTags.firstOrNull { it.name == "note" }
    if (noteTag != null) {
        Logger.d("handleAnnotationTap::noteLine not null,${noteTag}, then invoke onCheckedNote")
        val rect = highlightAnnotationAndGetRect(
            pageProvider, mergedPages, globalIndex,
            listOf(noteTag.uuid), listOf("note"), itemOffset, padding
        )
        viewModel.onCheckedNote(noteTag.uuid, rect ?: RectF())
        return true
    }

    val annoIds = arrayListOf<String>()
    for (itemTag in filterTags) {
        if (itemTag.name == "note") continue

        if (checkTagInLineRect(itemTag, line, clickX, localY, padding)) {
            if (itemTag.name == "a") {
                Logger.d("handleAnnotationTap::link hit, href=${itemTag.params}")
                viewModel.pageController.clickLink(itemTag, clickX, clickY)
                return true
            } else if (itemTag.name == "underline" || itemTag.name == "highlight") {
                annoIds.add(itemTag.uuid)
            }
        }
    }

    if (annoIds.isNotEmpty()) {
        Logger.d("handleAnnotationTap::annotation hit, ids=$annoIds")
        val rect = highlightAnnotationAndGetRect(
            pageProvider, mergedPages, globalIndex,
            annoIds, listOf("highlight", "underline"), itemOffset, padding
        )
        if (rect != null) {
            viewModel.onContinuousScrollCheckedAnnotation(annoIds, rect)
        }
        return true
    }

    return false
}

/****
 * 页面上的点击操作，已经排除了 长按，以及文本选择模式
 */
private fun handleTap(
    scope: PointerInputScope,
    offset: Offset,
    viewModel: MainReadViewModel,
    readerPreferences: ReaderPreferences,
    lazyListState: LazyListState,
    pageProvider: ContinuousPageProvider,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>
) {
    if (handleAnnotationTap(offset, viewModel, pageProvider, lazyListState, mergedPages)) {
        return
    }

    val clickAreaMode = readerPreferences.clickAreaMode
    val size = scope.size
    val width = size.width
    val height = size.height
    val centerRectF = RectF(width * 0.33f, height * 0.33f, width * 0.66f, height * 0.66f)
    val topRectF = RectF(0f, 0f, width.toFloat(), height * 0.4f)
    val clickX = offset.x
    val clickY = offset.y

    val isClickMenuArea = when (clickAreaMode) {
        0 -> centerRectF.contains(clickX, clickY)
        1 -> topRectF.contains(clickX, clickY)
        else -> centerRectF.contains(clickX, clickY)
    }
    if (isClickMenuArea) {
        viewModel.pageController.clickCenter()
    }
}

@Composable
fun TextPageCanvas(
    textPage: TextPage,
    book: Book?,
    pageProvider: ContinuousPageProvider,
    context: Context,
    globalPageIndex: Int,
) {
    val density = LocalDensity.current
    val pageHeight = remember(textPage.height) {
        with(density) { maxOf(textPage.height, 1f).toDp() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(pageHeight)
    ) {
        // Layer 1: 选区背景（底层，文字之下）
        // 依赖 per-page revision，仅当前页选区变化时此层重绘（轻量）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    pageProvider.pageSelectionRevisions[globalPageIndex]
                    drawSelectionBackgrounds(
                        this.drawContext.canvas.nativeCanvas,
                        textPage,
                        pageProvider,
                        RenderResources.selectedPaint
                    )
                })

        //Layer 1.5: 注释背景（高亮/下划线/笔记）
        Box( modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                pageProvider.annotationRevision
                //绘制书籍内的搜索高亮
                drawSearchResultsBg(
                    this.drawContext.canvas.nativeCanvas,
                    textPage,
                    pageProvider,
                )
                //绘制高亮/下划线/笔记的高亮
                drawAnnotationBackgrounds(
                    this.drawContext.canvas.nativeCanvas,
                    textPage,
                    pageProvider
                )
                //绘制TTS的高亮
                drawTtsReadAloudBg(
                    this.drawContext.canvas.nativeCanvas,
                    textPage,
                    pageProvider
                )
            })

        // Layer 2: 文本内容（中层）
        // 不依赖 revision，选区变化时不重绘（避免昂贵的 drawTextChars）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    pageProvider.contentRenderRevision
                    drawPageContent(this, context, book, textPage, pageProvider)
                })

        // Layer 3: 选区手柄（顶层，文字之上）
        // 依赖 per-page revision，仅当前页选区变化时此层重绘（轻量）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    pageProvider.pageSelectionRevisions[globalPageIndex]
                    drawSelectionHandles(
                        this, textPage, globalPageIndex,
                        pageProvider,
                        RenderResources.handlePaint,
                        RenderResources.handleStrokePaint,
                        RenderResources.handleRadiusPx,
                        RenderResources.handleLineHeightPx
                    )
                })
    }
}

private fun drawSelectionBackgrounds(
    canvas: Canvas,
    textPage: TextPage,
    pageProvider: ContinuousPageProvider,
    selectedPaint: Paint
) {
    if (!pageProvider.hasActiveSelection) return
    val locator = pageProvider.pageController.getSelectionLocator() ?: return
    if (locator.chapterIndex != textPage.chapterIndex) return

    val clipLeft = ChapterProvider.paddingHorizontal.toFloat()
    val clipTop = ChapterProvider.paddingVertical.toFloat()
    val clipRight = ChapterProvider.visibleRight.toFloat()
    val clipBottom = (ChapterProvider.paddingVertical + ChapterProvider.visibleHeight).toFloat()

    canvas.withClip(clipLeft, clipTop, clipRight, clipBottom) {
        for (line in textPage.textLines) {
            if (line.isImage || line.isLine) continue
            if (line.paragraphIndex < locator.startParagraphIndex ||
                line.paragraphIndex > locator.endParagraphIndex) continue

            val (start, end) = line.textChars.visualSpan { i ->
                ContinuousPageProvider.isOffsetInTextSelection(
                    locator, line.paragraphIndex, line.textIndexAt(i) + line.charStartOffset
                )
            } ?: continue
            canvas.drawRect(start, line.lineTop, end, line.lineBottom, selectedPaint)
        }
    }
}

/***
 * 书籍页面渲染
 */
private fun drawPageContent(
    drawScope: DrawScope,
    context: Context,
    book: Book?,
    textPage: TextPage,
    pageProvider: ContinuousPageProvider,
) {
    val canvas = drawScope.drawContext.canvas.nativeCanvas
    val chapterIndex = textPage.chapterIndex

    val clipLeft = ChapterProvider.paddingHorizontal.toFloat()
    val clipTop = ChapterProvider.paddingVertical.toFloat()
    val clipRight = ChapterProvider.visibleRight.toFloat()
    val clipBottom = (ChapterProvider.paddingVertical + ChapterProvider.visibleHeight).toFloat()

    var lastParagraphIndex = -1

    canvas.withClip(clipLeft, clipTop, clipRight, clipBottom) {
        textPage.textLines.forEachIndexed { index, textLine ->
            val (marginTop, marginBottom) = getLineMargin(index, textLine, textPage)
            val paragraphIndex = textLine.paragraphIndex
            if (lastParagraphIndex != paragraphIndex) {
                lastParagraphIndex = paragraphIndex
                RenderResources.shapedRunBuffer.clear()
            }
            val offset = if (textLine.isTableCell) textLine.rowLineOffset else 0

            val (tags, cssInfo) = pageProvider.pageController.pageFactory?.getPagesAnnotation(
                chapterIndex,
                paragraphIndex,
                textLine.charStartOffset + offset,
                textLine.charEndOffset + offset
            ) ?: (emptyList<TextTag>() to null)

            // 新增:每行预算一次 inlineFontSizes(避免每字符查 getReaderText)
            val pageFactory = pageProvider.pageController.pageFactory
            val inlineStyles = (pageFactory?.getReaderText(chapterIndex, paragraphIndex)
                    as? ReaderText.Text)?.inlineStyles

            if (textLine.isImage) {
                textLine.textChars.forEach { ch ->
                    canvas.drawRect(
                        ch.start,
                        textLine.lineTop,
                        ch.end,
                        textLine.lineBottom,
                        RenderResources.imagePlaceholderPaint
                    )
                    val rectF = RectF(ch.start, textLine.lineTop, ch.end, textLine.lineBottom)
                    val bmp = ImageProvider.getImage(
                        imgSrc = ch.charData,
                        targetWidth = rectF.width().toInt().coerceAtLeast(1),
                        targetHeight = rectF.height().toInt().coerceAtLeast(1)
                    )
                    if (bmp != null && !bmp.isRecycled) {
                        try {
                            canvas.drawBitmap(bmp, null, rectF, null)
                        } catch (e: RuntimeException) {
                            Logger.e(
                                "ContinuousScrollReaderView::drawImage(inline) failed bmpSize=${bmp.width}x${bmp.height}",
                                e
                            )
                        }
                    }
                }
            } else if (textLine.isLine) {
                // 主题感知边框（S12）：与 ContentTextView 同口径——正文文字色派生，
                // 不取 lineColor 固定色（深色主题下 #333333 不可辨）
                RenderResources.linePaint.color =
                    TableRenderProvider.borderColorFor(ChapterProvider.contentPaint.color)
                RenderResources.linePaint.strokeWidth = if (textLine.lineBorder > 0) textLine.lineBorder else 1f
                canvas.drawLine(
                    textLine.lineStart.first, textLine.lineStart.second,
                    textLine.lineEnd.first, textLine.lineEnd.second,
                    RenderResources.linePaint
                )
            } else {
                drawTextChars(
                    canvas,
                    context,
                    book,
                    textLine,
                    tags,
                    cssInfo,
                    isTitle = textLine.isTitle,
                    inlineStyles
                )
            }
        }
    }

    if (textPage.bookmarkId >= 0) {
        val left = ChapterProvider.viewWidth - RenderResources.dp21
        RenderResources.bookmarkPath.reset()
        RenderResources.bookmarkPath.moveTo(left, 0f)
        RenderResources.bookmarkPath.lineTo(left + RenderResources.dp21, 0f)
        RenderResources.bookmarkPath.lineTo(left + RenderResources.dp21, RenderResources.dp21 * 2f)
        RenderResources.bookmarkPath.lineTo(left + RenderResources.dp21 * 0.5f, RenderResources.dp21 * 1.5f)
        RenderResources.bookmarkPath.lineTo(left, RenderResources.dp21 * 2f)
        RenderResources.bookmarkPath.close()
        canvas.drawPath(RenderResources.bookmarkPath, RenderResources.bookmarkPaint)
    }
}

private fun drawTextChars(
    canvas: Canvas,
    context: Context,
    book: Book?,
    textLine: TextLine,
    textTags: List<TextTag>,
    textCssInfo: TextCssInfo?,
    isTitle: Boolean,
    inlineStyles: List<InlineStyle>? = null
) {
    var lineTextTag: TextTag? = null

    var defaultTextPaint: TextPaint? = null
    if (isTitle) {
        defaultTextPaint = ChapterProvider.titlePaint
    } else {
        if (textTags.isEmpty()) {
            defaultTextPaint = ChapterProvider.contentPaint
        } else if (textTags.size == 1) {
            val tagStart = textTags[0].start
            val tagEnd = textTags[0].end
            val lineStartIndex = textLine.charStartOffset
            val lineEndIndex = textLine.charEndOffset

            if (tagEnd <= lineStartIndex || tagStart >= lineEndIndex) {
                defaultTextPaint = ChapterProvider.contentPaint
            } else if (lineStartIndex >= tagStart && lineEndIndex <= tagEnd) {
                lineTextTag = textTags[0]
                val tagName = textTags[0].name
                if (tagName != "highlight" && tagName != "underline") {
                    defaultTextPaint = ChapterProvider.getPaintByTagName(lineTextTag)
                }
            }
        }
    }

    ListDotRenderer.draw(canvas, textLine)

    var textOnlyIdx = 0
    textLine.textChars.forEachIndexed { index, ch ->
        var isHighlight = false
        var isBold = false
        var isSmall = false
        // 文本口径下标（图片占数组位、不占文本位，M2-③；UTF-16 码元口径，M3 §3.4）：
        // 标签/inlineStyle 匹配专用；ShapedRunBuffer 相邻探测（index+1）仍用数组口径 index。
        val textIdx = textOnlyIdx
        if (!ch.isImage) textOnlyIdx += ch.charData.length
        val charIndex = textLine.charStartOffset + textIdx

        val parentPaint = if (defaultTextPaint != null) defaultTextPaint else {
            val texttag = if (textTags.size == 1) {
                val tag = textTags[0]
                if (tag.start <= charIndex && charIndex < tag.end) {
                    when (tag.name) {
                        "highlight" -> {
                            isHighlight = true
                        }

                        in arrayOf("h1", "h2", "h3", "h4", "a") -> {}
                        in arrayOf("strong", "b", "big") -> {
                            isBold = true
                        }

                        "small" -> {
                            isSmall = true
                        }
                    }
                    tag
                } else null
            } else {
                val tags = arrayListOf<TextTag>()
                for (tag in textTags) {
                    if (tag.start <= charIndex && charIndex < tag.end) {
                        when (tag.name) {
                            in arrayOf("h1", "h2", "h3", "h4", "a") -> tags.add(tag)

                            "highlight" -> {
                                isHighlight = true
                            }

                            in arrayOf("strong", "b", "big") -> isBold = true
                            "small" -> isSmall = true
                        }
                    }
                }
                tags.firstOrNull()
            }
            if (isHighlight) {
                ChapterProvider.contentPaint
            } else {
                ChapterProvider.getPaintByTagName(texttag)
            }
        }

        RenderResources.drawingPaint.set(parentPaint)
        // N-Q1 渲染侧防御（与 ContentTextView.drawChars 同源同型，审查 R13）：见彼处注释。
        if (textLine.letterSpacingZeroed) {
            RenderResources.drawingPaint.letterSpacing = 0f
        }

        val resolved = if (!isTitle && !ch.isImage && !textLine.isTableCell) {
            val charOffsetInParagraph = textLine.charStartOffset + textIdx
            InlineStyle.resolve(inlineStyles, charOffsetInParagraph)
        } else {
            InlineCssProps()
        }
        val inlineScale = resolved.fontScale ?: 1.0f
        val inlineColor = resolved.color
        val inlineVerticalAlign = resolved.verticalAlign
        RenderResources.applyCharPaint(ch, isTitle, isBold, isSmall, textCssInfo, inlineScale, inlineColor)

        // 计算 sup/sub 垂直偏移（在 applyCharPaint 之后，textSize 已是最终值）
        val baselineOffset = if (!ch.isImage && inlineVerticalAlign != null
            && inlineVerticalAlign != CssVerticalAlign.CssVerticalAlignBaseLine) {
            val parentSize = RenderResources.drawingPaint.textSize / inlineScale.coerceAtLeast(0.01f)  // 还原父字号
            when (inlineVerticalAlign) {
                CssVerticalAlign.CssVerticalAlignSuper -> -parentSize * 0.34f   // 上移（y 减小）
                CssVerticalAlign.CssVerticalAlignSub   ->  parentSize * 0.20f   // 下移（y 增大）
                else -> 0f
            }
        } else 0f

        if (ch.isImage) {
            drawImage(canvas, ch, textLine.lineTop, textLine.lineBottom)
        } else {
            RenderResources.shapedRunBuffer.draw(
                canvas,
                ch,
                textLine.lineBase + baselineOffset,
                RenderResources.drawingPaint,
                textLine.textChars.getOrNull(index + 1)
            )
        }
    }
}

private fun drawImage(
    canvas: Canvas,
    textChar: TextChar,
    lineTop: Float,
    lineBottom: Float
) {
    val rectF = RectF(textChar.start, lineTop, textChar.end, lineBottom)
    val bmp = ImageProvider.getImage(
        imgSrc = textChar.charData,
        targetWidth = rectF.width().toInt().coerceAtLeast(1),
        targetHeight = rectF.height().toInt().coerceAtLeast(1)
    )
    if (bmp != null && !bmp.isRecycled) {
        try {
            canvas.drawBitmap(bmp, null, rectF, null)
        } catch (e: RuntimeException) {
            Logger.e(
                "ContinuousScrollReaderView::drawImage(private) failed bmpSize=${bmp.width}x${bmp.height}",
                e
            )
        }
    }
}

private fun getLineMargin(index: Int, line: TextLine, page: TextPage): Pair<Int, Int> {
    val mt = if (index > 0) (page.textLines[index - 1].lineBottom - line.lineTop).toInt()
        .coerceAtLeast(0) else 0
    val mb =
        if (index < page.textLines.size - 1) (line.lineBottom - page.textLines[index + 1].lineTop).toInt()
            .coerceAtLeast(0) else 0
    return mt to mb
}

// --- Text Selection Data Classes & Functions ---

private data class HitResult(
    val pageItem: ContinuousPageProvider.MergedPageItem,
    val globalItemIndex: Int,
    val itemOffset: Int,
    val textLine: TextLine,
    val lineIndex: Int,
    val charIndex: Int
)

private data class SelectionResult(
    val screenStartX: Float,
    val screenStartY: Float,
    val screenStartLineBottom: Float,
    val screenEndX: Float,
    val screenEndY: Float,
    val paragraphIndex: Int,
    val startInnerTextOffset: Int,
    val endInnerTextOffset: Int,
    val startLineIndex: Int,
    val startCharIndex: Int,
    val endLineIndex: Int,
    val endCharIndex: Int
)

private fun findTextCharAtPosition(
    x: Float,
    y: Float,
    lazyListState: LazyListState,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>
): HitResult? {
    for (itemInfo in lazyListState.layoutInfo.visibleItemsInfo) {
        val itemTop = itemInfo.offset
        val itemBottom = itemTop + itemInfo.size
        if (y < itemTop || y >= itemBottom) continue

        val localY = y - itemTop
        val globalIndex = itemInfo.index
        val pageItem = mergedPages.getOrNull(globalIndex) ?: continue

        for ((lineIndex, textLine) in pageItem.page.textLines.withIndex()) {
            if (localY < textLine.lineTop || localY >= textLine.lineBottom) continue
            for ((charIndex, textChar) in textLine.textChars.withIndex()) {
                if (x >= textChar.start && x < textChar.end) {
                    if (!textChar.isImage && textChar.charData.isNotEmpty()) {
                        return HitResult(
                            pageItem, globalIndex, itemTop,
                            textLine, lineIndex, charIndex
                        )
                    }
                    return null
                }
            }
            return null
        }
        return null
    }
    return null
}

private fun selectWordAtChar(hitResult: HitResult): SelectionResult? {
    val page = hitResult.pageItem.page
    val textLine = hitResult.textLine
    val targetParagraphIndex = textLine.paragraphIndex

    data class CharInfo(val data: String, val lineIdx: Int, val charIdx: Int)

    val allChars = mutableListOf<CharInfo>()
    var pressedGlobalIndex = -1

    for ((lIdx, line) in page.textLines.withIndex()) {
        if (line.paragraphIndex != targetParagraphIndex) continue
        for ((cIdx, ch) in line.textChars.withIndex()) {
            allChars.add(CharInfo(ch.charData, lIdx, cIdx))
            if (lIdx == hitResult.lineIndex && cIdx == hitResult.charIndex) {
                pressedGlobalIndex = allChars.size - 1
            }
        }
    }
    if (pressedGlobalIndex < 0) return null

    val pressedChar = allChars[pressedGlobalIndex].data.firstOrNull() ?: return null
    val isCJK = pressedChar.isCJKChar()

    var startGlobal: Int
    var endGlobal: Int

    if (isCJK) {
        startGlobal = pressedGlobalIndex
        endGlobal = pressedGlobalIndex
    } else {
        startGlobal = pressedGlobalIndex
        for (i in (pressedGlobalIndex - 1) downTo 0) {
            val c = allChars[i].data.firstOrNull() ?: continue
            if (c.isWhitespace() || c.isPunctuation()) {
                startGlobal = i + 1
                break
            }
            if (i == 0) startGlobal = 0
        }

        endGlobal = pressedGlobalIndex
        for (i in (pressedGlobalIndex + 1) until allChars.size) {
            val c = allChars[i].data.firstOrNull() ?: continue
            if (c.isWhitespace() || c.isPunctuation()) {
                endGlobal = i - 1
                break
            }
            if (i == allChars.size - 1) endGlobal = allChars.size - 1
        }
    }

    if (startGlobal !in allChars.indices || endGlobal !in allChars.indices) return null

    val startInfo = allChars[startGlobal]
    val endInfo = allChars[endGlobal]

    val startLine = page.textLines.getOrNull(startInfo.lineIdx) ?: return null
    val startChar = startLine.textChars.getOrNull(startInfo.charIdx) ?: return null
    val endLine = page.textLines.getOrNull(endInfo.lineIdx) ?: return null
    val endChar = endLine.textChars.getOrNull(endInfo.charIdx) ?: return null
    val itemOffset = hitResult.itemOffset

    return SelectionResult(
        screenStartX = startChar.start,
        screenStartY = itemOffset + startLine.lineTop,
        screenStartLineBottom = itemOffset + startLine.lineBottom,
        screenEndX = endChar.end,
        screenEndY = itemOffset + endLine.lineBottom,
        paragraphIndex = targetParagraphIndex,
        // 文本口径（图片占数组位不占文本位，M2-③）：写入 Locator 的段内文本偏移
        startInnerTextOffset = startLine.charStartOffset + startLine.textIndexAt(startInfo.charIdx),
        endInnerTextOffset = endLine.charStartOffset + endLine.textIndexAt(endInfo.charIdx),
        startLineIndex = startInfo.lineIdx,
        startCharIndex = startInfo.charIdx,     // 视觉手柄锚点，保持数组口径
        endLineIndex = endInfo.lineIdx,
        endCharIndex = endInfo.charIdx
    )
}

/***
 * 长按时，触发文本选中模式，显示TextToolbar
 */
private fun performLongPress(
    offset: Offset,
    viewModel: MainReadViewModel,
    pageProvider: ContinuousPageProvider,
    lazyListState: LazyListState,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>,
    hapticFeedback: HapticFeedback
) {
    val hit = findTextCharAtPosition(offset.x, offset.y, lazyListState, mergedPages)
    if (hit == null) {
        Logger.d("ContinuousScroll: long press hit nothing")
        return
    }

    if (pageProvider.hasSelection()) {
        viewModel.textToolbarOpen(false)
        viewModel.cancelTextSelected()
    }

    val selection = selectWordAtChar(hit)
    if (selection == null) {
        Logger.d("ContinuousScroll: selectWordAtChar returned null")
        return
    }

    val startLine = hit.pageItem.page.textLines.getOrNull(selection.startLineIndex) ?: return
    val startChar = startLine.textChars.getOrNull(selection.startCharIndex) ?: return
    val endLine = hit.pageItem.page.textLines.getOrNull(selection.endLineIndex) ?: return
    val endChar = endLine.textChars.getOrNull(selection.endCharIndex) ?: return

    pageProvider.updateSelectionState(
        chapterIndex = hit.pageItem.chapterIndex,
        start = ContinuousPageProvider.SelectionEndpoint(
            startChar, startLine, hit.itemOffset, selection.startCharIndex,
            hit.globalItemIndex, selection.startLineIndex
        ),
        end = ContinuousPageProvider.SelectionEndpoint(
            endChar, endLine, hit.itemOffset, selection.endCharIndex,
            hit.globalItemIndex, selection.endLineIndex
        ),
        pageController = viewModel.pageController
    )

    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

    viewModel.onContinuousScrollTextSelected(
        selection.screenStartX, selection.screenStartY,
        selection.screenEndX, selection.screenEndY
    )

    Logger.d("ContinuousScroll: selection done, chapter=${hit.pageItem.chapterIndex}, paragraph=${selection.paragraphIndex}")
}

// --- Handle Drag ---

private enum class HandleDragMode { NONE, START, END }


/***
 * 根据按下的位置，确定是否是按在了选择区域的开始/结束位置
 */
private fun detectHandleHit(
    x: Float, y: Float,
    pageProvider: ContinuousPageProvider,
    lazyListState: LazyListState,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>,
    handleSlopPx: Float,
    handleRadiusPx: Float,
    handleLineHeightPx: Float
): HandleDragMode {
    val positions = getSelectionHandlePositions(
        pageProvider, lazyListState, mergedPages,
        handleRadiusPx, handleLineHeightPx
    ) ?: run {
        Logger.d("detectHandleHit::未找到手柄位置 (null)")
        return HandleDragMode.NONE
    }

    val (startPos, endPos) = positions
    val slopSq = handleSlopPx * handleSlopPx

    val startDistSq = (x - startPos.first) * (x - startPos.first) +
            (y - startPos.second) * (y - startPos.second)
    val endDistSq = (x - endPos.first) * (x - endPos.first) +
            (y - endPos.second) * (y - endPos.second)

    Logger.d(
        "detectHandleHit: touch=($x,$y), start=${startPos}, end=${endPos}, " +
                "startDist=$startDistSq, endDist=$endDistSq, slopSq=$slopSq"
    )

    return when {
        startDistSq < slopSq && startDistSq <= endDistSq -> HandleDragMode.START
        endDistSq < slopSq -> HandleDragMode.END
        else -> HandleDragMode.NONE
    }
}

/****
 * 找到当前显示的文本选择区域的开始，结束icon的位置。
 * 从 cachedSelectionStart/End 缓存读取，O(1) 查找。
 */
private fun getSelectionHandlePositions(
    pageProvider: ContinuousPageProvider,
    lazyListState: LazyListState,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>,
    handleRadiusPx: Float,
    handleLineHeightPx: Float
): Pair<Pair<Float, Float>, Pair<Float, Float>>? {
    val cachedStart = pageProvider.getCachedSelectionStart() ?: return null
    val cachedEnd = pageProvider.getCachedSelectionEnd() ?: return null

    val startItem = mergedPages.getOrNull(cachedStart.globalIndex) ?: return null
    val startLine = startItem.page.textLines.getOrNull(cachedStart.lineIndex) ?: return null
    val startChar = startLine.textChars.getOrNull(cachedStart.charIndex) ?: return null
    val startOffset = lazyListState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == cachedStart.globalIndex }?.offset ?: return null

    val endItem = mergedPages.getOrNull(cachedEnd.globalIndex) ?: return null
    val endLine = endItem.page.textLines.getOrNull(cachedEnd.lineIndex) ?: return null
    val endChar = endLine.textChars.getOrNull(cachedEnd.charIndex) ?: return null
    val endOffset = lazyListState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == cachedEnd.globalIndex }?.offset ?: return null

    val sY = startOffset + startLine.lineBottom + handleLineHeightPx - handleRadiusPx
    val eY = endOffset + endLine.lineBottom + handleLineHeightPx - handleRadiusPx
    return Pair(
        Pair(startChar.start, sY),
        Pair(endChar.end, eY)
    )
}

/****
 * 处理文本选中区域的开始，结束icon的拖动操作 的循环处理
 */
private suspend fun AwaitPointerEventScope.handleDragLoop(
    initialMode: HandleDragMode,
    downPos: Offset,
    viewModel: MainReadViewModel,
    pageProvider: ContinuousPageProvider,
    lazyListState: LazyListState,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>,
    handleRadiusPx: Float,        //
    handleLineHeightPx: Float     //
) {
    Logger.d("ContinuousScrollReaderView::handleDragLoop::TouchEvent::drag")
//    viewModel.textToolbarOpen(false)
    var hasMoved = false
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull() ?: break
        change.consume()
        Logger.d("ContinuousScrollReaderView::handleDragLoop::loop::TouchEvent::drag::change=$change")

        //抬起事件
        if (change.changedToUpIgnoreConsumed()) {
            Logger.d("ContinuousScrollReaderView::handleDragLoop::TouchEvent::tap up##")
            if(hasMoved) { // 真正拖拽结束 → 更新选区
                updateSelectionFromHandles(viewModel, pageProvider, lazyListState, mergedPages)

                val startPos = pageProvider.getCachedSelectionStart()
                val endPos = pageProvider.getCachedSelectionEnd()
                if (startPos != null && endPos != null) {
                    val startPageItem = mergedPages.getOrNull(startPos.globalIndex)
                    val startLine = startPageItem?.page?.textLines?.getOrNull(startPos.lineIndex)
                    val startChar = startLine?.textChars?.getOrNull(startPos.charIndex)
                    val startOffset = lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == startPos.globalIndex }?.offset ?: 0

                    val endPageItem = mergedPages.getOrNull(endPos.globalIndex)
                    val endLine = endPageItem?.page?.textLines?.getOrNull(endPos.lineIndex)
                    val endChar = endLine?.textChars?.getOrNull(endPos.charIndex)
                    val endOffset = lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == endPos.globalIndex }?.offset ?: 0

                    if (startChar != null && startLine != null && endChar != null && endLine != null) {
                        viewModel.onContinuousScrollTextSelected(
                            startChar.start, startOffset + startLine.lineTop,
                            endChar.end, endOffset + endLine.lineBottom
                        )
                    }
                }
            }
            // 纯点击（无 MOVE）→ 不做任何修改，直接退出
            break
        }

        //按下事件，跳过，进入下一个循环，则只会有移动或者抬起事件的处理
        if (change.changedToDownIgnoreConsumed()) continue

        // 首次 MOVE → 才隐藏工具栏，启动拖拽
        if (!hasMoved) {
            viewModel.textToolbarOpen(false)
            hasMoved = true
        }

        val pos = change.position
        Logger.d("ContinuousScrollReaderView::handleDragLoop::pos=$pos")
        val handleYCompensation = handleLineHeightPx - handleRadiusPx

        when (initialMode) {
            HandleDragMode.START -> {
                val compensatedY = pos.y - handleYCompensation
                moveSelectionStart(
                    pos.x,
                    compensatedY,
                    viewModel,
                    pageProvider,
                    lazyListState,
                    mergedPages
                )
            }

            HandleDragMode.END -> {
                val compensatedY = pos.y - handleYCompensation
                moveSelectionEnd(
                    pos.x,
                    compensatedY,
                    viewModel,
                    pageProvider,
                    lazyListState,
                    mergedPages
                )
            }

            HandleDragMode.NONE -> break
        }
    }
}

private fun moveSelectionStart(
    x: Float, y: Float,
    viewModel: MainReadViewModel,
    pageProvider: ContinuousPageProvider,
    lazyListState: LazyListState,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>
) {
    Logger.d("moveSelectionStart:x=$x,y=$y")
    val hit = findTextCharAtPosition(x, y, lazyListState, mergedPages) ?: return

    val endInfo = pageProvider.getCachedSelectionEnd() ?: return

    if (hit.globalItemIndex > endInfo.globalIndex) return
    if (hit.globalItemIndex == endInfo.globalIndex && hit.lineIndex > endInfo.lineIndex) return
    if (hit.globalItemIndex == endInfo.globalIndex && hit.lineIndex == endInfo.lineIndex
        && hit.charIndex >= endInfo.charIndex
    ) return

    val endPageItem = mergedPages.getOrNull(endInfo.globalIndex) ?: return
    val endLine = endPageItem.page.textLines.getOrNull(endInfo.lineIndex) ?: return
    val endChar = endLine.textChars.getOrNull(endInfo.charIndex) ?: return
    val endItemOffset = lazyListState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == endInfo.globalIndex }?.offset ?: 0

    val startLine = hit.textLine
    val startChar = startLine.textChars.getOrNull(hit.charIndex) ?: return

    pageProvider.updateSelectionState(
        chapterIndex = hit.pageItem.chapterIndex,
        start = ContinuousPageProvider.SelectionEndpoint(
            startChar, startLine, hit.itemOffset, hit.charIndex,
            hit.globalItemIndex, hit.lineIndex
        ),
        end = ContinuousPageProvider.SelectionEndpoint(
            endChar, endLine, endItemOffset, endInfo.charIndex,
            endInfo.globalIndex, endInfo.lineIndex
        ),
        pageController = viewModel.pageController
    )
}

private fun moveSelectionEnd(
    x: Float, y: Float,
    viewModel: MainReadViewModel,
    pageProvider: ContinuousPageProvider,
    lazyListState: LazyListState,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>
) {
    Logger.d("moveSelectionEnd:x=$x,y=$y")
    val hit = findTextCharAtPosition(x, y, lazyListState, mergedPages) ?: return

    val startInfo = pageProvider.getCachedSelectionStart() ?: return

    if (hit.globalItemIndex < startInfo.globalIndex) return
    if (hit.globalItemIndex == startInfo.globalIndex && hit.lineIndex < startInfo.lineIndex) return
    if (hit.globalItemIndex == startInfo.globalIndex && hit.lineIndex == startInfo.lineIndex
        && hit.charIndex <= startInfo.charIndex
    ) return

    val startPageItem = mergedPages.getOrNull(startInfo.globalIndex) ?: return
    val startLine = startPageItem.page.textLines.getOrNull(startInfo.lineIndex) ?: return
    val startChar = startLine.textChars.getOrNull(startInfo.charIndex) ?: return
    val startItemOffset = lazyListState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == startInfo.globalIndex }?.offset ?: 0

    val endLine = hit.textLine
    val endChar = endLine.textChars.getOrNull(hit.charIndex) ?: return

    pageProvider.updateSelectionState(
        chapterIndex = startPageItem.chapterIndex,
        start = ContinuousPageProvider.SelectionEndpoint(
            startChar, startLine, startItemOffset, startInfo.charIndex,
            startInfo.globalIndex, startInfo.lineIndex
        ),
        end = ContinuousPageProvider.SelectionEndpoint(
            endChar, endLine, hit.itemOffset, hit.charIndex,
            hit.globalItemIndex, hit.lineIndex
        ),
        pageController = viewModel.pageController
    )
}

private fun updateSelectionFromHandles(
    viewModel: MainReadViewModel,
    pageProvider: ContinuousPageProvider,
    lazyListState: LazyListState,
    mergedPages: List<ContinuousPageProvider.MergedPageItem>
) {
    val startPos = pageProvider.getCachedSelectionStart() ?: return
    val endPos = pageProvider.getCachedSelectionEnd() ?: return

    val startPageItem = mergedPages.getOrNull(startPos.globalIndex) ?: return
    val startLine = startPageItem.page.textLines.getOrNull(startPos.lineIndex) ?: return
    val startChar = startLine.textChars.getOrNull(startPos.charIndex) ?: return
    val startItemOffset = lazyListState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == startPos.globalIndex }?.offset ?: 0

    val endPageItem = mergedPages.getOrNull(endPos.globalIndex) ?: return
    val endLine = endPageItem.page.textLines.getOrNull(endPos.lineIndex) ?: return
    val endChar = endLine.textChars.getOrNull(endPos.charIndex) ?: return
    val endItemOffset = lazyListState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == endPos.globalIndex }?.offset ?: 0

    pageProvider.updateSelectionState(
        chapterIndex = startPageItem.chapterIndex,
        start = ContinuousPageProvider.SelectionEndpoint(
            startChar, startLine, startItemOffset, startPos.charIndex,
            startPos.globalIndex, startPos.lineIndex
        ),
        end = ContinuousPageProvider.SelectionEndpoint(
            endChar, endLine, endItemOffset, endPos.charIndex,
            endPos.globalIndex, endPos.lineIndex
        ),
        pageController = viewModel.pageController
    )
}

// --- Drawing Selection Handles ---

private fun drawSelectionHandles(
    drawScope: DrawScope,
    textPage: TextPage,
    globalPageIndex: Int,
    pageProvider: ContinuousPageProvider,
    handlePaint: android.graphics.Paint,
    handleStrokePaint: android.graphics.Paint,
    handleRadiusPx: Float,
    handleLineHeightPx: Float
) {
    if (!pageProvider.hasActiveSelection) return
    val startPos = pageProvider.getCachedSelectionStart() ?: return
    val endPos = pageProvider.getCachedSelectionEnd() ?: return
    val startLine = textPage.textLines.getOrNull(startPos.lineIndex)
    val endLine = textPage.textLines.getOrNull(endPos.lineIndex)

    if (globalPageIndex == startPos.globalIndex && startLine != null) {
        val startChar = startLine.textChars.getOrNull(startPos.charIndex)
        if (startChar != null) {
            drawHandle(
                drawScope,
                startChar.start,
                startLine.lineBottom,
                handlePaint,
                handleStrokePaint,
                handleRadiusPx,
                handleLineHeightPx
            )
        }
    }

    if (globalPageIndex == endPos.globalIndex && endLine != null) {
        val endChar = endLine.textChars.getOrNull(endPos.charIndex)
        if (endChar != null) {
            drawHandle(
                drawScope,
                endChar.end,
                endLine.lineBottom,
                handlePaint,
                handleStrokePaint,
                handleRadiusPx,
                handleLineHeightPx
            )
        }
    }
}

private fun drawHandle(
    drawScope: DrawScope,
    x: Float,
    lineBottom: Float,
    handlePaint: android.graphics.Paint,
    handleStrokePaint: android.graphics.Paint,
    handleRadiusPx: Float,
    handleLineHeightPx: Float
) {
    val canvas = drawScope.drawContext.canvas.nativeCanvas
    val r = handleRadiusPx
    val h = handleLineHeightPx
    val cy = lineBottom + h - r
    canvas.drawLine(x, lineBottom, x, cy, handlePaint)
    canvas.drawCircle(x, cy, r, handlePaint)
    canvas.drawCircle(x, cy, r, handleStrokePaint)
}

/**
 * 绘制 TTS 朗读高亮背景层。
 * 逻辑参照 ContentTextView.tryDrawReadAloudBg()（非连续模式）。
 *
 * 注意：speakingStatus 非 Compose State，此处重绘依赖 Layer 1.5 的
 * annotationRevision 快照状态被 bump。TTS 句子推进通过 refreshView() →
 * upContent() → bumpAnnotationRevision() 触发重绘。
 */
private fun drawTtsReadAloudBg(
    canvas: Canvas,
    textPage: TextPage,
    pageProvider: ContinuousPageProvider,
) {
    val speakBookStatus = pageProvider.pageController.getSpeakBookStatus()
    if (speakBookStatus.speakingStatus != TtsPlaybackStatus.PLAYING) return
    val locator = speakBookStatus.readBookLocator ?: return

    val curChapterIndex = textPage.chapterIndex

    for (textLine in textPage.textLines) {
        if (textLine.isImage || textLine.isLine) continue
        if (locator.chapterIndex != curChapterIndex) continue
        if (locator.startParagraphIndex != textLine.paragraphIndex) continue

        val lineStartOffset = textLine.charStartOffset
        val lineEndOffset = textLine.charEndOffset
        val start = locator.startTextOffset
        val end = locator.endTextOffset

        if (lineStartOffset > end || lineEndOffset < start) continue

        val (left, right) = textLine.textChars.visualSpan { index ->
            (index + lineStartOffset) in start until end
        } ?: continue
        val top = textLine.lineTop - RenderResources.dp4
        val bottom = textLine.lineBottom + RenderResources.dp4

        RenderResources.readAloudBgPaint.color = Color.YELLOW
        RenderResources.readAloudBgPaint.alpha = (0.4f * 255).toInt()
        RenderResources.readAloudBgRect.set(left, top, right, bottom)
        canvas.drawRect(RenderResources.readAloudBgRect, RenderResources.readAloudBgPaint)
    }
}

/**
 * 绘制搜索结果高亮背景。
 *
 * 逻辑参照 ContentTextView.tryDrawSearchResultsBg()（非连续模式）。
 *
 * 注意：searchedLocators 非 Compose State，此处重绘依赖 Layer 1.5 的
 * annotationRevision 快照状态被 bump。所有修改 searchedLocators 的路径
 * （addSearchHighlight / clearSearchHighlights）均通过 upContent() 触发
 * bumpAnnotationRevision()，确保重绘生效。
 */
private fun drawSearchResultsBg(
    canvas: Canvas,
    textPage: TextPage,
    pageProvider: ContinuousPageProvider,
) {
    val highlights = pageProvider.pageController.getSearchHighlights()
    if (highlights.isEmpty()) return

    val curChapterIndex = textPage.chapterIndex

    for (textLine in textPage.textLines) {
        if (textLine.isImage || textLine.isLine) continue
        val curLineParagraphIndex = textLine.paragraphIndex

        for (locator in highlights) {
            if (locator.chapterIndex != curChapterIndex) continue
            if (locator.startParagraphIndex != curLineParagraphIndex) continue

            val lineStartOffset = textLine.charStartOffset
            val lineEndOffset = textLine.charEndOffset
            val matchStart = locator.startTextOffset
            val matchEnd = locator.endTextOffset

            if (lineStartOffset >= matchEnd || lineEndOffset <= matchStart) continue

            val (left, right) = textLine.textChars.visualSpan { index ->
                (index + lineStartOffset) in matchStart until matchEnd
            } ?: continue
            val top = textLine.lineTop - RenderResources.dp4
            val bottom = textLine.lineBottom + RenderResources.dp4

            canvas.drawRoundRect(
                RectF(left, top, right, bottom), 4f, 4f,
                RenderResources.searchHighlightPaint
            )
        }
    }
}

/**
 * 绘制注释背景层（高亮、下划线、笔记背景）。
 * 类似 drawSelectionBackgrounds，是轻量级 overlay，独立于文本绘制（Layer 2）。
 *
 * 绘制顺序：
 * 1. 笔记背景（整行宽度矩形）
 * 2. 逐字符高亮矩形（RoundRect）
 * 3. 逐字符下划线（Line）
 */
private fun drawAnnotationBackgrounds(
    canvas: Canvas,
    textPage: TextPage,
    pageProvider: ContinuousPageProvider,
) {
    val chapterIndex = textPage.chapterIndex
    val noteIds = mutableSetOf<String>()

    textPage.textLines.forEachIndexed { index, textLine ->
        // 图片行和分隔线行不含可选中文本字符，跳过
        if (textLine.isImage || textLine.isLine) return@forEachIndexed

        val (marginTop, marginBottom) = getLineMargin(index, textLine, textPage)
        val paragraphIndex = textLine.paragraphIndex
        val offset = if (textLine.isTableCell) textLine.rowLineOffset else 0

        val (tags, _) = pageProvider.pageController.pageFactory?.getPagesAnnotation(
            chapterIndex,
            paragraphIndex,
            textLine.charStartOffset + offset,
            textLine.charEndOffset + offset
        ) ?: (emptyList<TextTag>() to null)

        // 1. 笔记背景 + 图标（行跨度，空行回退全页宽——对齐 ContentTextView.tryDrawNote）
        tags.firstOrNull { it.name == "note" }?.let { noteTag ->
            val colorStr = noteTag.paramsPairs().firstOrNull { it.first == "color" }
                ?.second ?: RenderResources.NOTE_DEFAULT_COLOR_HEX
            RenderResources.noteBgPaint.color = colorStr.toColor() ?: Color.YELLOW
            RenderResources.noteBgPaint.alpha = RenderResources.NOTE_BG_ALPHA

            val span = textLine.textChars.visualSpan()
            canvas.drawRect(
                span?.first ?: 0f, textLine.lineTop - marginTop / 2f,
                span?.second ?: ChapterProvider.viewWidth.toFloat(),
                textLine.lineBottom + marginBottom / 2f,
                RenderResources.noteBgPaint
            )

            // 笔记图标——锚定阅读方向起点（同 ContentTextView.tryDrawNote / R1 N1-a）
            if (!noteIds.contains(noteTag.uuid)) {
                RenderResources.noteIconBmp?.let { noteIcon ->
                    // M6：方向判定收敛为 isRtl（统一引擎下文本字符 renderGroup 恒 ≥1，
                    // 不能再用 renderGroup>0 推断方向，否则 LTR 行图标误贴右缘）
                    val lineRtl = textLine.isRtl
                    val iconDiameter = 2 * RenderResources.dp12
                    val iconLeft = when {
                        span == null -> 0f                        // 空行兜底，维持旧行为
                        lineRtl -> span.second - iconDiameter     // RTL：贴右缘
                        else -> span.first                        // LTR：贴左缘
                    }
                    val iconTop = textLine.lineTop - RenderResources.dp12
                    RenderResources.noteCirclePaint.color = colorStr.toColor() ?: Color.YELLOW
                    canvas.drawCircle(
                        iconLeft + RenderResources.dp12,
                        iconTop + RenderResources.dp12,
                        RenderResources.dp12,
                        RenderResources.noteCirclePaint
                    )
                    RenderResources.noteIconRect.set(
                        iconLeft + RenderResources.dp6, iconTop + RenderResources.dp6,
                        iconLeft + 3 * RenderResources.dp6, iconTop + 3 * RenderResources.dp6
                    )
                    canvas.drawBitmap(noteIcon, null, RenderResources.noteIconRect, null)
                    noteIds.add(noteTag.uuid)
                }
            }
        }

        // 2. 逐字符绘制高亮背景和下划线
        var textOnlyIdx = 0
        textLine.textChars.forEachIndexed { charIdx, ch ->
            // 文本口径（图片占数组位不占文本位，M2-③）：标签区间匹配专用
            val textIdx = textOnlyIdx
            if (!ch.isImage) textOnlyIdx += ch.charData.length
            val charIndex = textLine.charStartOffset + textIdx
            for (tag in tags) {
                if (tag.start <= charIndex && charIndex < tag.end) {
                    when (tag.name) {
                        "highlight" -> {
                            val colorStr = tag.paramsPairs()
                                .firstOrNull { it.first == "color" }?.second
                                ?: "#FFFFFF00"
                            RenderResources.highlightPaint.color =
                                colorStr.toColor() ?: Color.YELLOW
                            canvas.drawRoundRect(
                                RectF(
                                    ch.start - 1f,
                                    textLine.lineTop - 10f,
                                    ch.end + 1f,
                                    textLine.lineBottom + 10f
                                ),
                                1f, 1f, RenderResources.highlightPaint
                            )
                        }
                        "underline" -> {
                            val colorStr = tag.paramsPairs()
                                .firstOrNull { it.first == "color" }?.second
                                ?: "#FF575757"
                            colorStr.toColor()?.let { color ->
                                RenderResources.underlinePaint.color = color
                            }
                            RenderResources.underlinePaint.strokeWidth = 3f
                            canvas.drawLine(
                                ch.start, textLine.lineBottom,
                                ch.end, textLine.lineBottom,
                                RenderResources.underlinePaint
                            )
                        }
                    }
                }
            }
        }
    }
}
