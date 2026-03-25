package com.wxn.reader.presentation.mainReader

import android.content.Context
import android.widget.Toast
import com.wxn.base.bean.Book
import com.wxn.base.bean.Locator
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import com.wxn.base.util.launchMain
import com.wxn.bookread.data.model.SpeekBookStatus
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextPage
import com.wxn.reader.service.TtsError
import com.wxn.reader.service.TtsState
import com.wxn.reader.service.TtsStateHolder
import com.wxn.reader.service.toLocalizedString
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.TtsServiceController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

abstract class TTSController(
    open val context: Context,
    open var ttsStateHolder: TtsStateHolder,
    open var ttsServiceController: TtsServiceController,
    ) {

    open var book: Book? = null

    val scope = Coroutines.scope()

    //--------------------------TTS methods------------------------
    fun resumeTtsPlaying() =
        ttsServiceController.resume(context)

    fun pauseTtsPlaying() =
        ttsServiceController.pause(context)

    fun stopTts() {
        ttsServiceController.stop(context)
        stopTtsService()
        stopReadPageNew()
    }

    fun startTtsService() =
        ttsServiceController.startService(context)

    fun stopTtsService() =
        ttsServiceController.stopService(context)

    fun setTtsLanguage(lang: String) =
        ttsServiceController.setLanguage(context,Locale.forLanguageTag(lang) )

    fun setTtsLanguage(lang: LanguageInfo) =
        ttsServiceController.setLanguage(context,lang.locale)

    fun setTtsPitch(pitch: Float) =
        ttsServiceController.setPitch(context, pitch)

    fun setTtsSpeed(speed: Float) =
        ttsServiceController.setSpeed(context, speed)

    fun getTtsSupportedLanguages() =
        ttsServiceController.getSupportedLanguage(context)

    fun setTtsTimer(timer: Float) =
        ttsServiceController.setPlayTime(context, timer)

    fun skipToNextUtterance() =
        ttsServiceController.skipToNextUtterance(context)

    fun skipToPreviousUtterance() =
        ttsServiceController.skipToPreviousUtterance(context)

//    ----------------------------------
    @Volatile
    open var speakingStatus: SpeekBookStatus = SpeekBookStatus()

    open fun stopReadPageNew() {
        Logger.i("PageViewController::stopReadPageNew")

        ttsServiceController.stop(context)

        // 清理状态
        speakingStatus = SpeekBookStatus()
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


    open fun clear() {
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
        // 播放状态变化

        if (state.isPlaying != speakingStatus.isSpeaking) {
            speakingStatus = speakingStatus.copy(isSpeaking = state.isPlaying)
            refreshView()
        }

        // 错误处理
        state.error?.let { error ->
            handleTtsError(error)
            ttsStateHolder.clearError() // 消费错误
        }

        // 检测章节请求：当TTS请求的章节索引大于当前索引时，提供下一章
        val nextChapterIndex = state.currentChapterIndex
        if (nextChapterIndex > (speakingStatus.readBookLocator?.chapterIndex?:0) && state.isPlaying) {
            if (speakingStatus.readBookLocator != null) {
                Logger.i("检测到TTS请求章节 ${nextChapterIndex}，自动提供")
                scope.launchIO {
                    provideNextChapterToTts(nextChapterIndex)
                }
            }
        }

        // 进度更新
        state.currentLocator?.let { locator ->
            Logger.d("PageViewController::handleTtsStateChange::locator update[$locator]")
            if (locator.chapterIndex != durChapterPos() ||
                locator.startParagraphIndex != speakingStatus.readBookLocator?.startParagraphIndex ||
                locator.startTextOffset != speakingStatus.readBookLocator?.startTextOffset) {

                val newSentenceIndex = state.currentSentenceIndex
                speakingStatus = speakingStatus.copy(readBookLocator = locator, playSentenceIndex = newSentenceIndex)
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
     */
    suspend fun readPageNew(onFinished: (Boolean) -> Unit) {
        Logger.i("PageViewController::readPageNew")
        val curChapter = textChapter(0) ?: run {
            Logger.e("当前章节为空")
            onFinished(false)
            return
        }

        val curPage = currentPage()

        // 1. 启动TTS服务（如果未启动）
        if (!ttsServiceController.isServiceRunning(context)) {
            ttsServiceController.startService(context)
            // 等待服务启动
            delay(1000)
        }

        // 2. 设置播放数据
        val success = ttsServiceController.setSpeakStartChapterAndPage(
            context,
            curChapter,
            curPage,
            bookTitle = book?.title ?: "",
            chapterTitle = curChapter.title,
            bookCover = book?.coverImage,
            chapterSize = curChapter.chaptersSize,
        )

        if (!success) {
            Logger.e("设置播放数据失败")
            onFinished(false)
            return
        }

        // 3. 开始播放
        ttsServiceController.play(context)

        // 4. 监听播放完成
        scope.launch {
            ttsStateHolder.state
                .filter { !it.isPlaying && it.error == null }
                .first()
                .let {
                    onFinished(false)
                }
        }
        onFinished(true)
        initTtsStateListener()
    }


    /**
     * 更新阅读位置
     */
    private fun updateReadingPosition(locator: Locator) {
        Logger.d("PageViewController::updateReadingPosition:locator=$locator")
        val chapterIndex = locator.chapterIndex

        // 查找对应的页面
        val textChapter = when (chapterIndex) {
            textChapter(0)?.position -> textChapter(0)
            textChapter(-1)?.position -> textChapter(-1)
            textChapter(1)?.position -> textChapter(1)
            else -> null
        }

        textChapter?.let { chapter ->
            var speakingPageIndex = -1

            for (page in chapter.pages) {
                val linesInParagraph = page.textLines.filter { textLine ->
                    textLine.paragraphIndex == locator.startParagraphIndex
                }

                if (linesInParagraph.isNotEmpty()) {
                    for (line in linesInParagraph) {
                        if (line.paragraphIndex == locator.startParagraphIndex &&
                            locator.startTextOffset >= line.charStartOffset &&
                            locator.startTextOffset < line.charEndOffset) {
                            speakingPageIndex = page.index
                            break
                        }
                    }
                }

                if (speakingPageIndex >= 0) break
            }

            if (speakingPageIndex >= 0) {
                changeChapterAndPage(chapterIndex, speakingPageIndex)
            }
        }
    }


    private suspend fun provideNextChapterToTts(requestedChapterIndex: Int) {
        Logger.i("PageViewController::provideNextChapterToTts,$requestedChapterIndex")

        val durChapterIndex = durChapterPos()
        // 从缓存获取或加载章节
        val chapter = when (requestedChapterIndex) {
            durChapterIndex + 1 -> textChapter(1)
            durChapterIndex -> textChapter(0)
            durChapterIndex - 1 -> textChapter(-1)
            else -> null
        }

        if (chapter != null) {
            Logger.i("从缓存提供章节: index=${chapter.position}")
            // 通过TtsServiceController发送章节到服务
            ttsServiceController.setSpeakStartChapterAndPage(
                context, chapter,
                null,
                bookTitle = book?.title ?: "",
                chapterTitle = chapter.title,
                bookCover = book?.coverImage,
                chapterSize = chapter.chaptersSize
            )
        } else {
            Logger.w("缓存中没有章节 $requestedChapterIndex，尝试加载")
            try {
                loadChapter(requestedChapterIndex, upContent = false, resetPageOffset = false)
                // 等待加载完成后再次尝试提供
                delay(500)
                val loadedChapter = when (requestedChapterIndex) {
                    durChapterIndex + 1 -> textChapter(1)
                    durChapterIndex -> textChapter(0)
                    else -> null
                }
                if (loadedChapter != null) {
                    Logger.i("加载后提供章节: index=${loadedChapter.position}")
                    ttsServiceController.setSpeakStartChapterAndPage(
                        context, loadedChapter, null,
                        bookTitle = book?.title ?: "",
                        chapterTitle = loadedChapter.title,
                        bookCover = book?.coverImage,
                        chapterSize = loadedChapter.chaptersSize
                    )
                } else {
                    Logger.e("加载章节失败: $requestedChapterIndex")
                    ttsStateHolder.reportError(TtsError.ChapterLoadFailed(requestedChapterIndex), context)
                }
            } catch (e: Exception) {
                Logger.e("加载章节异常: ${e.message}")
                ttsStateHolder.reportError(TtsError.ChapterLoadFailed(requestedChapterIndex), context)
            }
        }
    }

    abstract fun refreshView()

    abstract suspend fun loadChapter(requestedChapterIndex: Int, upContent: Boolean = true, resetPageOffset: Boolean)

    abstract fun durChapterPos() : Int


    abstract suspend fun updateReadingTime()

    abstract fun changeChapterAndPage(newChapterIndex: Int, newPageIndex: Int, newProgress: Double = 0.0): Boolean

    abstract fun textChapter(chapterOnDur: Int): TextChapter?

    abstract fun currentPage() : TextPage?
}