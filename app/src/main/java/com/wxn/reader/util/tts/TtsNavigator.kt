package com.wxn.reader.util.tts

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.wxn.base.bean.EngineModelConfig
import com.wxn.base.bean.Locator
import com.wxn.base.bean.SpeakSentence
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.ToastUtil
import com.wxn.base.util.launchIO
import com.wxn.base.util.withMain
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.reader.service.TtsEngineStatus
import com.wxn.reader.ui.theme.stringResource
import com.wxn.reader.util.LanguageUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import net.gotev.speech.OnShutdownListener
import net.gotev.speech.Speech
import net.gotev.speech.TextToSpeechCallbackAdapter
import net.gotev.speech.engine.PlayErrorCode
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class TtsNavigator(
    val context: Context,
    val ttsPreferencesUtil: TtsPreferencesUtil
) : ITtsNavigator, DefaultLifecycleObserver {

    private data class EngineConfig(
        val engineType: Int,
        val modelConfig: EngineModelConfig?,
        val speed: Float,
        val pitch: Float,
        val language: Locale,
        val speakerIndex: Int
    )

    private var lastEngineConfig: EngineConfig? = null
    private var shutdownTimerJob: Job? = null
    private val INACTIVITY_TIMEOUT_MS = 60_000L // 60s

    companion object {
        const val TTS_MIN_SPEED = 0.25f
        const val TTS_MAX_SPEED = 3f
        const val TTS_MIN_PITCH = 0.25f
        const val TTS_MAX_PITCH = 3f

        const val TTS_PLAY_MIN_TIMES = 0.0f //
        const val TTS_PLAY_MAX_TIMES = 2.0f

        const val STATUS_NORMAL_FINISH = 0  //正常结束
        const val STATUS_ERROR = -1  //出错结束

        const val STATUS_TIMER_EXPIRED  = 2 // 定时器计时到期
    }

    interface SuspendSpeakCallback {

        fun onTimer() : Boolean

        fun onSpeakSentence(locator: Locator, sentenceIndex: Int)
        fun onSpeakNextChapter(nextChapterIndex: Int): Boolean
        fun onFinished(status: Int)
    }

    private var ttsLocale: Locale = LanguageUtil.LANG_EN.locale
    private var speed = 1.0f
    private var pitch = 1.0f
    private val curSentences = mutableListOf<SpeakSentence>()

    // * 这个是正在加载的位置, 而不是播放的位置,
    @Volatile
    private var playSentenceIndex: Int = 0

    // * 当前正在播放的位置
    @Volatile
    private var speechSentenceIndex: Int = 0

    private var callback: SuspendSpeakCallback? = null
    private val scope = Coroutines.mainScope() // 播放队列：无限制通道，外部向其中发送句子
    private val sentenceChannel = Channel<SpeakSentence>(UNLIMITED)
    private var playJob: Job? = null

    private val mutex = Mutex()


    private val engineInitDeferredRef = AtomicReference(CompletableDeferred<Unit>())

    private var speech: Speech? = null

    init {
        scope.launch {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this@TtsNavigator)
        }
    }

    private fun startShutdownTimer() {
        shutdownTimerJob?.cancel()
        shutdownTimerJob = scope.launchIO {
            Logger.d("TtsNavigator: starting shutdown timer for ${INACTIVITY_TIMEOUT_MS / 1000}s")
            delay(INACTIVITY_TIMEOUT_MS)
            if (speech != null && !isPlaying()) {
                Logger.i("TtsNavigator: inactivity timer expired, releasing engine")
                releaseEngine()
            }
        }
    }

    private fun resetShutdownTimer() {
        if (shutdownTimerJob != null) {
            startShutdownTimer()
        }
    }

    private fun cancelShutdownTimer() {
        shutdownTimerJob?.cancel()
        shutdownTimerJob = null
    }

    private fun releaseEngine() {
        Logger.i("TtsNavigator: releasing engine")
        speech?.shutdown(null)
        speech = null
        cancelShutdownTimer()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Logger.i("TtsNavigator: App went to background, releasing engine if not playing")
        if (!isPlaying()) {
            releaseEngine()
        }
    }

    private suspend fun ensureEngineInitialized(): Boolean {
        if (speech != null) return true
        val config = lastEngineConfig ?: return false
        
        Logger.i("TtsNavigator: re-initializing engine on demand")
        val deferred = CompletableDeferred<Unit>()
        engineInitDeferredRef.set(deferred)

        return suspendCancellableCoroutine { continuation ->
            scope.launchIO {
                if (config.engineType == 1) {
                    if (validTtsConfig(config.engineType, config.modelConfig)) {
                        speech = Speech.init(context, config.engineType, config.modelConfig, config.speed, config.pitch, config.language, config.speakerIndex) { status ->
                            handleInitResult(deferred, status, "SherpaOnnx")
                            if (continuation.isActive) continuation.resume(status == TextToSpeech.SUCCESS)
                        }
                    } else {
                        handleInitResult(deferred, TextToSpeech.ERROR, "SherpaOnnx")
                        if (continuation.isActive) continuation.resume(false)
                    }
                } else {
                    speech = Speech.init(context, config.speed, config.pitch, config.language) { status ->
                        handleInitResult(deferred, status, "BaseTTS")
                        if (continuation.isActive) continuation.resume(status == TextToSpeech.SUCCESS)
                    }
                }
            }
        }
    }

    private suspend fun <T> withState(block: suspend () -> T): T = mutex.withLock { block() }

    private suspend fun replaceSentences(sentences: List<SpeakSentence>, startIndex: Int) {
        Logger.d("TtsNavigator:replaceSentences:sentences.size=${sentences.size},startIndex=$startIndex")
        curSentences.clear()
        curSentences.addAll(sentences)
        speechSentenceIndex = startIndex
        playSentenceIndex = startIndex
    }

    override fun skipToPreviousUtterance(): Boolean {
        Logger.i("TtsNavigator:skipToPreviousUtterance")
        resetShutdownTimer()
        if (true != speech?.isSpeaking) {
            Logger.i("TtsNavigator:skipToPreviousUtterance:isNotSpeaking, pass")
            return false
        }
        if (speechSentenceIndex <= 0) {
            Logger.i("TtsNavigator:skipToPreviousUtterance:speechSentenceIndex<0, pass")
            return false
        }
        scope.launchIO {
            withState {
                val curIndex = speechSentenceIndex
                if (curIndex <= 0) return@withState
                speechSentenceIndex = curIndex - 1
                playSentenceIndex = curIndex - 1
            }
            restartChannel()
        }
        return true
    }

    override fun skipToNextUtterance(): Boolean {
        Logger.i("TtsNavigator:skipToNextUtterance")
        resetShutdownTimer()
        if (true != speech?.isSpeaking) {
            Logger.d("TtsNavigator:skipToNextUtterance:is not speaking, pass")
            return false
        }
        if (speechSentenceIndex >= curSentences.size - 1) {
            Logger.w("TtsNavigator:skipToNextUtterance:speechSentenceIndex >= curSentences.size, pass")
            return false
        }
        scope.launchIO {
            withState {
                val curIndex = speechSentenceIndex
                speechSentenceIndex = curIndex + 1
                playSentenceIndex = curIndex + 1
            }
            restartChannel()
        }
        return true
    }

    override fun setSpeakSentences(sentences: List<SpeakSentence>, startSentenceIndex: Int) {
        Logger.i("TtsNavigator::setSpeakSentences:sentences.size=${sentences.size},startSentenceIndex=$startSentenceIndex")
        resetShutdownTimer()
        scope.launchIO {
            withState {
                replaceSentences(sentences, startSentenceIndex)
            }
            restartChannel(false)
        }
    }

    override fun setSpeakCallback(callback: SuspendSpeakCallback?) {
        this.callback = callback
    }

    override fun play() {
        Logger.i("TtsNavigator:play")
        cancelShutdownTimer()
        playJob?.cancel()
        Logger.i("TtsNavigator:play:cancel old playJob")
        playJob = scope.launchIO {
            Logger.i("TtsNavigator:play:into io coroutine and then wait for init")
            if (!ensureEngineInitialized()) {
                Logger.e("TtsNavigator:play: engine initialization failed")
                callback?.onFinished(STATUS_ERROR)
                return@launchIO
            }
            waitForInit()
            Logger.i("TtsNavigator:play: engine init is success, then continue")

            val (canPlay, sentences, startIndex) = withState {
                if (curSentences.isEmpty() || playSentenceIndex < 0 || playSentenceIndex >= curSentences.size) {
                    Triple(false, emptyList<SpeakSentence>(), 0)
                } else {
                    Triple(true, curSentences, playSentenceIndex)
                }
            }
            Logger.d("TtsNavigator::canPlay=$canPlay,sentences.size=${sentences.size},startIndex=$startIndex")

            if (!canPlay) {
                callback?.onFinished(STATUS_ERROR)
                Logger.d("TtsNavigator:play:sentences is empty or playSentenceIndex overflow[$playSentenceIndex][${curSentences.size}]")
                return@launchIO
            }

            // 标记是否请求了下一章
            if (speech?.engineType == 0) {
                speech?.setTextToSpeechQueueMode(TextToSpeech.QUEUE_ADD)
                speech?.setAudioStream(AudioManager.STREAM_MUSIC)
            }

            for (sentence in sentenceChannel) {
                Logger.d("TtsNavigator::play:get sentence[$sentence], isActive=$isActive")
                // 检查是否被取消
                if (!isActive) {
                    callback?.onFinished(STATUS_NORMAL_FINISH)
                    break
                }

                if (callback?.onTimer() != true) {
                    Logger.d("TtsNavigator::play:onSpeakSentence return false, over the time limit.")
                    // 超过播放时长限制,正常退出
                    callback?.onFinished(STATUS_TIMER_EXPIRED)
                    break
                }

                // 播放句子（挂起直到播放完成）
                resetShutdownTimer()
                val result = innerPlay(sentence)

                Logger.d("TtsNavigator::play:play result=[$result],check agian:isActive=$isActive")
                if (!isActive) { // 播放过程中可能被取消（如暂停）
                    callback?.onFinished(STATUS_NORMAL_FINISH)
                    break
                }

                when (result) {
                    1 -> {
                        // 播放成功，索引自增
                        withState {
                            playSentenceIndex++
                        }
                    }
                    0 -> {
                        callback?.onFinished(STATUS_NORMAL_FINISH)
                        break
                    }
                    else -> {
                        // 播放出错
                        callback?.onFinished(STATUS_ERROR)
                        break
                    }
                }
            }
        }
    }

    /***
     * //更新播放位置, 当设置了播放时长控制,则检测是否已经达到或者超过了播放时长,是,则中断播放
     */
    private fun whenSentenceStart(utteranceId: String?) {
        Logger.i("TtsNavigator::whenSentenceStart:utteranceId=$utteranceId")
        if (!utteranceId.isNullOrEmpty()) {
            var targetSentence: SpeakSentence? = null
            var targetIndex: Int = -1

            val sentences = curSentences
            for ((index, sentence) in sentences.withIndex()) {
                if (sentence.locator.id == utteranceId) {
                    targetSentence = sentence
                    targetIndex = index
                    break
                }
            }
            if (targetIndex >= 0 && targetSentence != null) {
                Logger.i("TtsNavigator::whenSentenceStart: update play locator[${targetSentence}] and index[$targetIndex]")
                //更新播放位置,  让界面高亮当前播放位置
                // 当设置了播放时长控制,则检测是否已经达到或者超过了播放时长,是,则中断播放
                scope.launchIO {
                    withState {
                        speechSentenceIndex = targetIndex
                    }
                }
                callback?.onSpeakSentence(targetSentence.locator, targetIndex)
            }
        }
    }

    /****
     * 播放完成一句之后,
     * 检测是否到达了这一章节的最后一句, 如果是,返回true
     */
    private fun whenSentencePlayEnd(utteranceId: String?): Boolean {
        Logger.i("TtsNavigator::whenSentencePlayEnd:utteranceId=$utteranceId")

        if (utteranceId.isNullOrEmpty()) {
            return false
        }

        var targetSentence: SpeakSentence? = null
        var targetIndex: Int = -1
        val sentences = curSentences
        for ((index, sentence) in sentences.withIndex()) {
            if (sentence.locator.id == utteranceId) {
                targetSentence = sentence
                targetIndex = index
                break
            }
        }

        if (targetIndex < 0 || targetSentence == null) {
            return false
        }

        var chapterRequested = false
        // 如果已到列表末尾，请求下一章
        if (targetIndex >= sentences.size - 1) {
            val nextChapterIndex = targetSentence.locator.chapterIndex + 1
            //去尝试加载下一章节
            val shouldContinue = callback?.onSpeakNextChapter(nextChapterIndex) == true
            Logger.i("TtsNavigator::whenSentencePlayEnd: reach chapter end, shouldContinue=$shouldContinue")
            if (!shouldContinue) {
                // 没有下一章，正常结束
                callback?.onFinished(STATUS_NORMAL_FINISH)
            } else {
                chapterRequested = true
                // 等待外部调用 setSpeakSentences 追加新句子
                // 此时通道为空，for 循环会挂起，直到新句子到来
            }
        }
        return chapterRequested
    }

    private suspend fun innerPlay(text: SpeakSentence): Int {
        Logger.d("TtsNavigator::innerPlay:text[$text]")
        val start = System.currentTimeMillis()
        return suspendCancellableCoroutine { coroutination ->
            speech?.say(
                text,
                if (speech?.engineType == 0) {
                    object : TextToSpeechCallbackAdapter() {
                        override fun onStart(utteranceId: String?) {
                            Logger.d("TtsNavigator::innerPlay say callback onStart:$utteranceId")
                            whenSentenceStart(utteranceId)
                        }

                        override fun onCompleted(utteranceId: String?) {
                            val duration = System.currentTimeMillis() - start
                            Logger.d("TtsNavigator::innerPlay say callback onCompleted duration[${duration}ms], $utteranceId")
                            whenSentencePlayEnd(utteranceId)
                            coroutination.resume(1)
                        }

                        override fun onError(utteranceId: String?, code: PlayErrorCode) {
                            val duration = System.currentTimeMillis() - start
                            Logger.d("TtsNavigator::innerPlay say callback onError duration[${duration}ms], code=$code, $utteranceId")
                            coroutination.resume(0)
                        }
                    }
                } else {
                    object : TextToSpeechCallbackAdapter() {
                        override fun onPrepare(utteranceId: String?) {
                            Logger.d("TtsNavigator::innerPlay say callback onPrepare:$utteranceId")
                            coroutination.resume(1) //这个时候返回, 让外部继续执行,去将下一段文本转换成音频流
                        }

                        override fun onStart(utteranceId: String?) {
                            super.onStart(utteranceId)
                             whenSentenceStart(utteranceId)
                        }

                        override fun onCompleted(utteranceId: String?) {
                            super.onCompleted(utteranceId)
                            whenSentencePlayEnd(utteranceId)
                        }

                        override fun onError(utteranceId: String?, code: PlayErrorCode) {
                            super.onError(utteranceId, code)
                            Logger.w("TtsNavigator::innerPlay say callback onError, code=$code, $utteranceId")
                            val errCode = when(code) {
                                PlayErrorCode.PlayErrorShuttingDown,
                                    PlayErrorCode.PlayErrorStop,
                                    PlayErrorCode.PlayErrorTtsIsNull -> {
                                        0
                                    }
                                else -> -1
                            }
                            coroutination.resume(errCode)
                        }
                    }
                }
            )
        }
    }

    override fun setSpeed(speed: Float) {
        Logger.i("TtsNavigator::setSpeed:speed=$speed, oldSpeed=${this.speed}")
        resetShutdownTimer()
        val speechSpeed = speed.coerceIn(TTS_MIN_SPEED, TTS_MAX_SPEED)
        if (speechSpeed != this.speed) {
            scope.launchIO {
                Logger.d("TtsNavigator::setSpeed: try to wait engine init")
                if (!ensureEngineInitialized()) return@launchIO
                waitForInit()
                Logger.d("TtsNavigator::setSpeed: engine init done, continue")
                if (speech?.setTextToSpeechRate(speechSpeed) == TextToSpeech.SUCCESS) {
                    Logger.d("TtsNavigator::setSpeed: success")
                    this@TtsNavigator.speed = speechSpeed
                    restartChannel()
                    scope.launch {
                        ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()?.let { preferences ->
                            ttsPreferencesUtil.updatePreferences(
                                preferences.copy(
                                    speed = speechSpeed
                                )
                            )
                        }
                    }
                } else {
                    Logger.e("TtsNavigator::setSpeed:set speed failed.")
                }
            }
        }
    }

    private suspend fun restartChannel(needResume: Boolean = true, forceResume: Boolean = false) {
        Logger.i("TtsNavigator::restartChannel::needResume=$needResume")
        val isPlaying = isPlaying()
        if (forceResume || (isPlaying && needResume)) {
            pauseAndWait()
        }
        val (sentences, startIndex) = withState {
            playSentenceIndex = speechSentenceIndex
            Pair(curSentences.toList(), speechSentenceIndex)
        }
        // 清空通道
        while (sentenceChannel.tryReceive().isSuccess) {  /*do nothing*/ }

        val size = sentences.size
        if (size > 0 && startIndex in 0 until size) {
            sentences.drop(startIndex).forEach {
                sentenceChannel.trySend(it)
            }
        }
        if (forceResume || (isPlaying && needResume)) {
            play()
        }
    }

    override fun setPitch(pitch: Float) {
        Logger.i("TtsNavigator:setPitch:pitch[$pitch],ttsPitch[${this.pitch}]]")
        resetShutdownTimer()

        val speechPitch = pitch.coerceIn(TTS_MIN_PITCH, TTS_MAX_PITCH)
        if (speechPitch != this.pitch) {
            scope.launchIO {
                Logger.d("TtsNavigator:setPitch: try to wait engine init")
                if (!ensureEngineInitialized()) return@launchIO
                waitForInit()
                Logger.d("TtsNavigator:setPitch: engine init done, continue")
                if (speech?.setTextToSpeechPitch(speechPitch) == TextToSpeech.SUCCESS) {
                    Logger.d("TtsNavigator:setPitch success")
                    this@TtsNavigator.pitch = speechPitch
                    restartChannel()
                    scope.launch {
                        ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()?.let { preferences ->
                            ttsPreferencesUtil.updatePreferences(
                                preferences.copy(
                                    pitch = speechPitch
                                )
                            )
                        }
                    }
                } else {
                    Logger.e("TtsNavigator::setPitch:set pitch failed.")
                }
            }
        }
    }

    override fun getSupportedLanguage(onDataCollect: (Set<Locale>) -> Unit) {
        scope.launchIO {
            if (!ensureEngineInitialized()) {
                onDataCollect.invoke(emptySet())
                return@launchIO
            }
            waitForInit()
            val locales = speech?.supportedTtsLanguages
            onDataCollect.invoke(locales ?: emptySet<Locale>())
        }
    }

    override fun setLanguage(newlocale: Locale, onLanguageChanged: (Boolean) -> Unit) {
        Logger.i("TtsNavigator:setLanguage:newLocale[${newlocale.language}],ttsLocale[${ttsLocale.language}]]")
        resetShutdownTimer()
        scope.launchIO {
            Logger.i("TtsNavigator:setLanguage: try to wait engine init")
            if (!ensureEngineInitialized()) {
                onLanguageChanged.invoke(false)
                return@launchIO
            }
            waitForInit()
            Logger.i("TtsNavigator:setLanguage: engine init done, continue")
            if (newlocale != this@TtsNavigator.ttsLocale) {
                val status = speech?.setLocale(newlocale) ?: -1
                Logger.i("TtsNavigator:setLanguage done, status=$status, language=$newlocale")
                if (status < 0) {
                    Logger.e(
                        "TtsNavigator::setLanguage::language not support[${
                            when (status) {
                                -1 -> "LANG_MISSING_DATA"
                                -2 -> "LANG_NOT_SUPPORTED"
                                else -> "OTHER ISSUE"
                            }
                        }]"
                    )
                    onLanguageChanged.invoke(false)
                }

                this@TtsNavigator.ttsLocale = newlocale
                restartChannel()

                Logger.d("TtsNavigator::setLanguage::newlocale[$newlocale], supportLanguage[$status]")
                scope.launch {
                    ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()?.let { preferences ->
                        ttsPreferencesUtil.updatePreferences(
                            preferences.copy(
                                localeCode = newlocale.language
                            )
                        )
                    }
                }
            }
            onLanguageChanged.invoke(true)
        }
    }

    override fun setSpeakerIndex(index: Int) {
        Logger.i("TtsNavigator:setSpeakerIndex:index=$index")
        resetShutdownTimer()
        scope.launchIO {
            Logger.i("TtsNavigator:setSpeakerIndex: try to wait engine init")
            if (!ensureEngineInitialized()) return@launchIO
            waitForInit()
            Logger.i("TtsNavigator:setSpeakerIndex: engine init done, continue")
            speech?.setSpeakerIndex(index)
            restartChannel()
            Logger.d("TtsNavigator:setSpeakerIndex:done")
        }
    }

    override fun setEngineInfo(
        engineType: Int,
        modelConfig: EngineModelConfig?,
        speed: Float,
        pitch: Float,
        language: Locale,
        speakerIndex: Int,
        onInit: (TtsEngineStatus) -> Unit) {
        Logger.i("TtsNavigator:setEngineInfo:engineType=$engineType,config=$modelConfig," +
                "speed=$speed,pitch=$pitch,language=$language,speakerIndex=$speakerIndex")
        lastEngineConfig = EngineConfig(engineType, modelConfig, speed, pitch, language, speakerIndex)
        cancelShutdownTimer()
        scope.launchIO {  //切换到协程中执行
            val oldEngineType = speech?.engineType ?: -1
            val oldModelConfig = speech?.config
            if (oldEngineType != engineType) {  //系统引擎 <-->  Sherpa引擎  两者之间切换
                if (oldEngineType == -1 || speech == null) {  //还没有引擎 ,第一次初始化引擎
                    val newDeferred = CompletableDeferred<Unit>()
                    engineInitDeferredRef.set(newDeferred)

                    if (engineType == 1) {
                        if (validTtsConfig(engineType, modelConfig)) {
                            speech = Speech.init(context, engineType, modelConfig, speed, pitch, language, speakerIndex) { status ->
                                handleInitResult(newDeferred, status, "SherpaOnnx") //  通知等待者
                                onInit( if (status == TextToSpeech.SUCCESS) TtsEngineStatus.READY else TtsEngineStatus.FAILED)
                            }
                        } else {
                            onInit(TtsEngineStatus.NEED_MODEL)
                            ToastUtil.showLong(stringResource(com.wxn.reader.R.string.err_no_valid_engine_model))
                            handleInitResult(newDeferred, TextToSpeech.ERROR, "SherpaOnnx")
                        }
                    } else {
                        speech = Speech.init(context, speed, pitch, language) { status ->
                            handleInitResult(newDeferred, status, "BaseTTS")
                            onInit( if (status == TextToSpeech.SUCCESS) TtsEngineStatus.READY else TtsEngineStatus.FAILED)
                        }
                    }
                } else {  //有其他引擎, 先关闭其他引擎
                    if (isPlaying()) {
                        pause()         // 会停止speech ,但是不会清空 sentences
                    }

                    val oldDeferred = engineInitDeferredRef.get()
                    // 尝试取消旧的初始化（如果还在进行中）
                    if (!oldDeferred.isCompleted) {
                        oldDeferred.completeExceptionally(
                            CancellationException("Engine switching from $oldEngineType to $engineType")
                        )
                    }
                    val newDeferred = CompletableDeferred<Unit>()
                    engineInitDeferredRef.set(newDeferred)

                    val oldSpeech = speech
                    speech = null    // 先断开引用，防止其他线程访问
                    oldSpeech?.shutdown {
                        scope.launchIO {
                            delay(30)
                            Logger.i("TtsNavigator:setEngineInfo:wait shutdown last Engine then start new Engine.")

                            if (engineType == 1) {
                                if (validTtsConfig(engineType, modelConfig)) {
                                    speech = Speech.init(context, engineType, modelConfig, speed, pitch, language, speakerIndex) { status ->
                                        handleInitResult(newDeferred, status, "SherpaOnnx") //  通知等待者
                                        onInit( if (status == TextToSpeech.SUCCESS) TtsEngineStatus.READY else TtsEngineStatus.FAILED)
                                    }
                                } else {
                                    onInit(TtsEngineStatus.NEED_MODEL)
                                    ToastUtil.showLong(stringResource(com.wxn.reader.R.string.err_no_valid_engine_model))
                                    handleInitResult(newDeferred, TextToSpeech.ERROR, "SherpaOnnx")
                                }
                            } else {
                                speech = Speech.init(context, speed, pitch, language) { status ->
                                    handleInitResult(newDeferred, status, "BaseTTS")
                                    onInit( if (status == TextToSpeech.SUCCESS) TtsEngineStatus.READY else TtsEngineStatus.FAILED)
                                }
                            }
                        }
                    }
                }
            } else if (engineType == 1 &&
                oldModelConfig?.engineModel != modelConfig?.engineModel &&
                !modelConfig?.engineModel.isNullOrEmpty()
            ) { //Sherpa引擎  模型切换

                if (isPlaying()) {
                    pause()
                }

                val oldDeferred = engineInitDeferredRef.get()
                // 尝试取消旧的初始化（如果还在进行中）
                if (!oldDeferred.isCompleted) {
                    oldDeferred.completeExceptionally(
                        CancellationException("Engine switching from $oldEngineType to $engineType")
                    )
                }

                val newDeferred = CompletableDeferred<Unit>()
                engineInitDeferredRef.set(newDeferred)

                Logger.i("TtsNavigator:setEngineInfo: shutdown last SherpaEngine then start new Engine.")

                val oldSpeech = speech
                speech = null
                oldSpeech?.shutdown {
                    scope.launchIO {
                        delay(30)

                        if (validTtsConfig(engineType, modelConfig)) {
                            speech = Speech.init(context, engineType, modelConfig, speed, pitch, language, speakerIndex) { status ->
                                handleInitResult(newDeferred, status, "SherpaOnnx") //  通知等待者
                                onInit( if (status == TextToSpeech.SUCCESS) TtsEngineStatus.READY else TtsEngineStatus.FAILED)
                            }
                        } else {
                            onInit(TtsEngineStatus.NEED_MODEL)
                            ToastUtil.showLong(stringResource(com.wxn.reader.R.string.err_no_valid_engine_model))
                            handleInitResult(newDeferred, TextToSpeech.ERROR, "SherpaOnnx")
                        }
                    }
                }
            } else {  //相同的引擎,不用操作,直接返回
                onInit(TtsEngineStatus.READY)
            }
        }
    }

    /***
     * 检测, 引擎,模型,语音 是否匹配
     */
    private fun validTtsConfig(engineType: Int, engineModelConfig: EngineModelConfig?): Boolean {
        engineModelConfig ?: return false
        if (engineType == 1 &&
            (engineModelConfig.engineModel.isEmpty() ||
                    engineModelConfig.modelType.isEmpty() ||
                    engineModelConfig.modelDir.isEmpty())
        ) {
            Logger.d(
                "MainReadViewModel:validTtsConfig:engineModel=${engineModelConfig.engineModel}," +
                        "modelType=${engineModelConfig.modelType}, maybe null##"
            )
            return false
        }

        val baseDatas = engineModelConfig.baseDatas
        var ret = true
        for (baseData in baseDatas) {
            if (baseData.first.isEmpty() || baseData.second.isEmpty()) {
                ret = false
                break
            }
        }
        return ret
    }

    private fun handleInitResult(
        deferred: CompletableDeferred<Unit>,
        status: Int,
        engineName: String
    ) {
        if (status == TextToSpeech.SUCCESS) {
            Logger.d("TtsNavigator::initSuccess: $engineName")
            deferred.complete(Unit)
        } else {
            Logger.e("TtsNavigator::initFailed: $engineName, status=$status")
            val errorMessage = when (status) {
                TextToSpeech.ERROR -> "TTS引擎初始化失败，请检查系统TTS设置"
                else -> "TTS初始化失败: $engineName 返回状态 $status"
            }
            deferred.completeExceptionally(
                RuntimeException(errorMessage)
            )
        }
    }

    override fun pause() {
        Logger.i("TtsNavigator::pause")
        playJob?.cancel()
        playJob = null
        speech?.stopTextToSpeech()
        startShutdownTimer()
    }

    private suspend fun pauseAndWait() {
        Logger.i("TtsNavigator::pauseAndWait")
        playJob?.cancel()
        playJob = null
        speech?.stopAndWait()
        startShutdownTimer()
    }

    override fun resume() {
        Logger.i("TtsNavigator::resume: playSentenceIndex=$playSentenceIndex, speechSentenceIndex=$speechSentenceIndex")
        if (playSentenceIndex > speechSentenceIndex) {
            playSentenceIndex = speechSentenceIndex //同步TTS加载序列号 和 播放序列号
        }
        scope.launchIO {
            restartChannel(false, true) //强制刷新并开始播放
        }
    }

    override fun stop() {
        Logger.i("TtsNavigator::stop")
        playJob?.cancel()
        playJob = null
        speech?.stopTextToSpeech()
        startShutdownTimer()

        scope.launchIO {
            withState {
                // 清空通道
                while (sentenceChannel.tryReceive().isSuccess) { /*do nothing*/ }

                curSentences.clear()
                playSentenceIndex = 0
                speechSentenceIndex = 0
            }
        }
    }

    override fun shutdown(listener: OnShutdownListener?) {
        Logger.i("TtsNavigator:shutdown")
        playJob?.cancel()
        cancelShutdownTimer()

        // 关闭 Channel
        try {
            sentenceChannel.close()
        } catch (e: Exception) {
            Logger.w("Failed to close sentence channel: ${e.message}")
        }
        //  清理状态
        scope.launchIO {
            withState {
                curSentences.clear()
                playSentenceIndex = 0
                speechSentenceIndex = 0
                callback = null
            }
        }
        speech?.shutdown(listener)
        speech = null
        Logger.i("TtsNavigator:shutdown completed")
    }

    fun isPlaying(): Boolean {
        val isPlaying = speech?.isSpeaking == true && playJob?.isActive == true
        Logger.i("TtsNavigator::isPlaying[$isPlaying]")
        return isPlaying
    }

    private suspend fun waitForInit(timeoutMs: Long = 5000L) {
        val deferred = engineInitDeferredRef.get()
        try {
            withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            Logger.e("TTS initialization timeout after ${timeoutMs}ms")
            throw RuntimeException("TTS initialization timeout", e)
        } catch (e: CancellationException) {
            Logger.w("TTS initialization cancelled: ${e.message}")
            throw e
        }
    }
}