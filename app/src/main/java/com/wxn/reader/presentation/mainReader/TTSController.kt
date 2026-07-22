package com.wxn.reader.presentation.mainReader

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.wxn.base.bean.Book
import com.wxn.base.bean.Locator
import com.wxn.base.bean.TtsConfig
import com.wxn.base.bean.TtsPlaybackStatus
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.base.util.launchMain
import com.wxn.base.util.toLocale
import com.wxn.bookread.data.model.SpeekBookStatus
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextPage
import com.wxn.reader.R
import com.wxn.reader.service.TtsEngineStatus
import com.wxn.reader.service.TtsError
import com.wxn.reader.service.TtsState
import com.wxn.reader.service.TtsStateHolder
import com.wxn.reader.service.toLocalizedString
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.TtsServiceController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
abstract class TTSController(
    open val context: Context,
    open var ttsStateHolder: TtsStateHolder,
    open var ttsServiceController: TtsServiceController,
) {

    open var book: Book? = null

    val scope = Coroutines.scope()

    //--------------------------TTS methods------------------------
    @OptIn(UnstableApi::class)
    fun resumeTtsPlaying(): Boolean {
        val hasInit = (ttsStateHolder.state.value.ttsEngineStatus == TtsEngineStatus.READY)
        Logger.d("TTSController::resumeTtsPlaying::hasInit=$hasInit")
        return if (hasInit) {
            ttsServiceController.resume(context)
            true
        } else {
            // 检查TTS是否可用
            if (!isTtsAvailable()) {
                showErrorToast(context.getString(R.string.tts_error_unavailable))
                return false
            }
            false
        }
    }
    
    private fun isTtsAvailable(): Boolean {
        return try {
            // Query installed TTS engines through PackageManager rather than constructing a
            // TextToSpeech. The TextToSpeech constructor binds to the default engine
            // asynchronously; calling shutdown() on the next line (before onInit fires) races
            // the bind and can leave an orphaned ServiceConnection that keeps the system TTS
            // engine alive with nothing left to release it. The manifest already declares a
            // <queries> entry for android.intent.action.TTS_SERVICE, so these services stay
            // visible under package-visibility filtering (API 30+).
            val intent = android.content.Intent(android.speech.tts.TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            context.packageManager.queryIntentServices(intent, 0).isNotEmpty()
        } catch (e: Exception) {
            Logger.e("检查TTS可用性失败: ${e.message}")
            false
        }
    }
    @OptIn(UnstableApi::class)
    fun pauseTtsPlaying() =
        ttsServiceController.pause(context)
    @OptIn(UnstableApi::class)
    fun stopTts() {
        stopReadPageNew()
    }

    @OptIn(UnstableApi::class)
    fun startTtsService() = ttsServiceController.startService(context)

    @OptIn(UnstableApi::class)
    fun setTtsLanguage(lang: LanguageInfo) =
        ttsServiceController.setLanguage(context, lang.locale)

    @OptIn(UnstableApi::class)
    fun setTtsPitch(pitch: Float) =
        ttsServiceController.setPitch(context, pitch)

    @OptIn(UnstableApi::class)
    fun setTtsSpeed(speed: Float) =
        ttsServiceController.setSpeed(context, speed)

    fun setTtsBookInfo(book: Book) {
        ttsStateHolder.update {
            it.copy(
                bookTitle = book.title,
                bookCover = book.coverImage,
                bookLocale = book.language?.toLocale()
            )
        }
    }

    @OptIn(UnstableApi::class)
    fun setTtsTimer(timer: Float) =
        ttsServiceController.setPlayTime(context, timer)
    @OptIn(UnstableApi::class)
    fun skipToNextUtterance() =
        ttsServiceController.skipToNextUtterance(context)
    @OptIn(UnstableApi::class)
    fun skipToPreviousUtterance() =
        ttsServiceController.skipToPreviousUtterance(context)
    @OptIn(UnstableApi::class)
    fun setSpeakerIndex(index: Int) =
        ttsServiceController.setSpeakerIndex(context, index)

    //    ----------------------------------
    @Volatile
    open var speakingStatus: SpeekBookStatus = SpeekBookStatus()
    @OptIn(UnstableApi::class)
    open fun stopReadPageNew() {
        Logger.i("PageViewController::stopReadPageNew")
        ttsServiceController.stop(context)
    }

    fun showErrorToast(msg: String) {
        scope.launchMain {
            try {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Logger.e("显示错误提示失败: ${e.message}")
            }
        }
    }

    private var ttsStateCollector: Job? = null

    /**
     * 初始化TTS状态监听
     */
    private fun initTtsStateListener() {
        // 取消现有的监听
        ttsStateCollector?.cancel()

        ttsStateCollector = scope.launchIO {
            ttsStateHolder.state.collect { state ->
                handleTtsStateChange(state)
            }
        }
    }


    open fun clear(ownerToken: Long? = null) {
        stopTts()
        ttsStateCollector?.cancel()
        ttsStateCollector = null
        Logger.i("PageViewController:clear()")
    }

    /**
     * 处理TTS状态变化
     */
    private fun handleTtsStateChange(state: TtsState) {
        Logger.i("PageViewController::handleTtsStateChange")

        //计时器计时结束
        if (state.timerExpired) {
            onTimerExpired()
            ttsStateHolder.resetTimerElapsed()
        }

        // 播放状态变化
        if (state.ttsPlayerStatus != speakingStatus.speakingStatus) {
            Logger.d("TTSController::handleTtsStateChange::new ttsPlayerStatus update[${state.ttsPlayerStatus}]")
            speakingStatus =  if (state.ttsPlayerStatus != TtsPlaybackStatus.IDLE) {
                 speakingStatus.copy(speakingStatus = state.ttsPlayerStatus)
            } else {
                SpeekBookStatus()
            }
            syncTtsPlayStatus(state.ttsPlayerStatus)
            refreshView()
        }

        // 错误处理
        state.error?.let { error ->
            handleTtsError(error)
            ttsStateHolder.clearError() // 消费错误
        }

        // 检测章节请求：当TTS请求的章节索引大于当前索引时，提供下一章
        val nextChapterIndex = state.currentChapterIndex
        val locator = speakingStatus.readBookLocator
        if (locator != null && nextChapterIndex > locator.chapterIndex && state.isPlaying) {
            Logger.i("检测到TTS请求章节 ${nextChapterIndex}，自动提供")
            speakingStatus = speakingStatus.copy(
                readBookLocator = locator.copy(chapterIndex = nextChapterIndex)
            )
            scope.launchIO {
                provideNextChapterToTts(nextChapterIndex)
            }
        }

        // 进度更新
        state.currentLocator?.let { locator ->
            Logger.d("PageViewController::handleTtsStateChange::locator update[$locator]")
            if (locator.chapterIndex !=  durChapterIndex ||
                locator.startParagraphIndex != speakingStatus.readBookLocator?.startParagraphIndex ||
                locator.startTextOffset != speakingStatus.readBookLocator?.startTextOffset
            ) {

                val newSentenceIndex = state.currentSentenceIndex
                speakingStatus = speakingStatus.copy(
                    readBookLocator = locator,
                    playSentenceIndex = newSentenceIndex
                )
                refreshView()
                updateReadingPosition(locator)
                scope.launch {
                    updateReadingTime()
                }
            }
        }
    }


    /**
     * 处理TTS错误
     */
    private fun handleTtsError(error: TtsError) {
        Logger.e("TTS错误: ${error.toLocalizedString(context)}")

        when (error) {
            is TtsError.ChapterLoadFailed -> {
                showErrorToast(error.toLocalizedString(context))
                stopReadPageNew()
            }

            is TtsError.LanguageNotSupported -> {
                showErrorToast(error.toLocalizedString(context))
            }

            is TtsError.PlaybackFailed -> {
                showErrorToast(error.toLocalizedString(context))
                stopReadPageNew()
            }

            is TtsError.ServiceNotStarted -> {
                showErrorToast(error.toLocalizedString(context))
            }

            is TtsError.EngineNotReady -> {
                showErrorToast(error.toLocalizedString(context))
            }

            is TtsError.NetworkError -> {
                showErrorToast(error.toLocalizedString(context))
            }

//            else -> {
//                showErrorToast(context.getString(R.string.tts_error_generic))
//            }
        }
    }

    /**
     * 开始朗读页面（新版本）
     * @param onFinished  回调状态: -1: 章节没有内容;
     *                             -2: 设置播放数据失败;
     *                             -3: 播放被停止了,
     *                             -4: 引擎初始化失败
     *                             -5: 引擎需要加载模型
     *                             1: 引擎初始化成功
     */
    @OptIn(UnstableApi::class)
    suspend fun readPageNew(ttsConfig: TtsConfig, onFinished: (StartTtsFinishedStatus) -> Unit) {
        Logger.i("PageViewController::readPageNew")
        val curChapter = textChapter(0) ?: run {
            Logger.e("当前章节为空")
            onFinished(StartTtsFinishedStatus.NoChapterData)
            return
        }

        val curPage = currentPage()

        // 2. 设置播放数据
        val success = ttsServiceController.setSpeakConfigsAndPlay(
            context,
            curChapter,
            curPage,
            bookTitle = book?.title ?: "",
            chapterTitle = curChapter.title,
            bookCover = book?.coverImage,
            bookUri = book?.filePath ?: "",
            chapterSize = curChapter.chaptersSize,
            ttsConfig
        )

        if (!success) {
            Logger.e("设置播放数据失败")
            onFinished(StartTtsFinishedStatus.SetDataFail)
            return
        }

        scope.launch {
            ttsStateHolder.state
                .filter { it.ttsEngineStatus != TtsEngineStatus.IDLE &&
                            it.ttsEngineStatus != TtsEngineStatus.INITIALIZING
                }  //监听引擎初始化状态
                .first()
                .let {
                    if(it.ttsEngineStatus == TtsEngineStatus.READY) {
                        onFinished(StartTtsFinishedStatus.EngineInitSuccess)
                    } else if (it.ttsEngineStatus == TtsEngineStatus.FAILED) {
                        onFinished(StartTtsFinishedStatus.EngineInitFail)
                    } else if (it.ttsEngineStatus == TtsEngineStatus.NEED_MODEL) {
                        onFinished(StartTtsFinishedStatus.EngineFailByNeedModel)
                    }
                }
        }
        // 4. 监听播放完成
        scope.launch {
            ttsStateHolder.state
                .filter { !it.isPlaying && it.error == null }
                .first()
                .let {
                    Logger.d("TTSController::readPageNew:TTS play done.")
                    onFinished(StartTtsFinishedStatus.PlayStopFail)//播放被停止了,
                }
        }
        initTtsStateListener()
    }

    /**
     * 更新阅读位置
     */
    private fun updateReadingPosition(locator: Locator) {
        Logger.d("PageViewController::updateReadingPosition:locator=$locator")
        val chapterIndex = locator.chapterIndex

        // 查找对应的页面
        val textChapter = getCachedChapter(chapterIndex) ?: return
        var speakingPageIndex = -1
        for (page in textChapter.pages) {
            val linesInParagraph = page.textLines.filter { textLine ->
                textLine.paragraphIndex == locator.startParagraphIndex
            }

            if (linesInParagraph.isNotEmpty()) {
                for (line in linesInParagraph) {
                    if (line.paragraphIndex == locator.startParagraphIndex &&
                        locator.startTextOffset >= line.charStartOffset &&
                        locator.startTextOffset < line.charEndOffset
                    ) {
                        speakingPageIndex = page.index
                        break
                    }
                }
            }

            if (speakingPageIndex >= 0) break
        }

        if (speakingPageIndex >= 0) {
            changeChapterAndPage(chapterIndex, speakingPageIndex, newProgress = -1.0)
        }
    }

    fun getCachedChapter(targetChapterIndex: Int): TextChapter? {
        val nextChapter = textChapter(1)
        val currentChapter = textChapter(0)
        val prevChapter = textChapter(-1)
        return when (targetChapterIndex) {
            nextChapter?.position -> nextChapter
            currentChapter?.position -> currentChapter
            prevChapter?.position -> prevChapter
            else -> null
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun provideNextChapterToTts(requestedChapterIndex: Int) {
        Logger.i("PageViewController::provideNextChapterToTts,$requestedChapterIndex")

        // 从缓存获取或加载章节
        val chapter = getCachedChapter(requestedChapterIndex)

        if (chapter != null) {
            Logger.i("从缓存提供章节: index=${chapter.position}")
            // 通过TtsServiceController发送章节到服务
            ttsServiceController.setSpeakStartChapterAndPage(
                context, chapter,
                null,
                bookTitle = book?.title ?: "",
                chapterTitle = chapter.title,
                bookCover = book?.coverImage,
                bookUri = book?.filePath ?: "",
                chapterSize = chapter.chaptersSize
            )
        } else {
            Logger.w("缓存中没有章节 $requestedChapterIndex，尝试加载")
            try {
                val loadedChapter =
                    loadChapter(requestedChapterIndex, upContent = false, resetPageOffset = false)
                // 等待加载完成后再次尝试提供
                if (loadedChapter != null) {
                    Logger.i("加载后提供章节: index=${loadedChapter.position}")
                    ttsServiceController.setSpeakStartChapterAndPage(
                        context, loadedChapter, null,
                        bookTitle = book?.title ?: "",
                        chapterTitle = loadedChapter.title,
                        bookCover = book?.coverImage,
                        bookUri = book?.filePath ?: "",
                        chapterSize = loadedChapter.chaptersSize
                    )
                } else {
                    Logger.e("加载章节失败: $requestedChapterIndex")
                    ttsStateHolder.reportError(
                        TtsError.ChapterLoadFailed(requestedChapterIndex),
                        context
                    )
                }
            } catch (e: Exception) {
                Logger.e("加载章节异常: ${e.message}")
                ttsStateHolder.reportError(
                    TtsError.ChapterLoadFailed(requestedChapterIndex),
                    context
                )
            }
        }
    }

    abstract fun refreshView()

    abstract suspend fun loadChapter(
        requestedChapterIndex: Int,
        upContent: Boolean = true,
        resetPageOffset: Boolean,
        assignToSlot: Boolean = true
    ): TextChapter?


    /***
     * 当前章节索引
     */
    abstract var durChapterIndex: Int

    /***
     * 当前章节中正在显示的页面的索引
     */
    abstract fun durChapterPos(): Int


    abstract suspend fun updateReadingTime()

    abstract fun changeChapterAndPage(
        newChapterIndex: Int,
        newPageIndex: Int,
        newProgress: Double = 0.0
    ): Boolean

    abstract fun textChapter(chapterOnDur: Int): TextChapter?

    abstract fun currentPage(): TextPage?

    abstract fun syncTtsPlayStatus(ttsPlaybackStatus: TtsPlaybackStatus)

    abstract fun onTimerExpired()
}