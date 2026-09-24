package com.wxn.reader.util.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.LanguageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.gotev.speech.Speech
import net.gotev.speech.SpeechRecognitionNotAvailable
import net.gotev.speech.TextToSpeechCallback
import java.util.Locale

class TtsNavigator(
    val context: Context,
    val ttsPreferencesUtil: TtsPreferencesUtil
//    var speed: Float,// 语速
//    var pitch: Float, //音调
//    var language: AppLanguage
) {

//    var initSuccess: Boolean = false
//
//    var tts: TextToSpeech? = null
//
//    fun skipToPreviousUtterance(): Boolean {
//        if (!initSuccess || tts == null) {
//            return false
//        }
//
//        return true
//    }
//
//    fun skipToNextUtterance(): Boolean {
//        if (!initSuccess || tts == null) {
//            return false
//        }
//
//        return true
//    }
//
//    fun play(): Boolean {
//        if (!initSuccess || tts == null) {
//            return false
//        }
//
//        tts?.setPitch(pitch)
//        tts?.setSpeechRate(speed)
//        val locale = language.locale
//        tts?.setLanguage(locale)
//        val engines = tts?.engines.orEmpty()
//        for(engine in engines) {
//            Logger.d("TtsNavigator::engine[${engine.toString()}]")
//        }
//
//        //LANG_AVAILABLE, LANG_COUNTRY_AVAILABLE, LANG_COUNTRY_VAR_AVAILABLE, LANG_MISSING_DATA and LANG_NOT_SUPPORTED.
//        val isSuppport = tts?.isLanguageAvailable(locale)
//        if (isSuppport == TextToSpeech.LANG_AVAILABLE ||
//            isSuppport == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
//            isSuppport == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
//        ) {
//
//            //        params – Parameters for the request. Can be null.
//            //        Supported parameter names: TextToSpeech.Engine.KEY_PARAM_STREAM,
//            //        TextToSpeech.Engine.KEY_PARAM_VOLUME,
//            //        TextToSpeech.Engine.KEY_PARAM_PAN.
//            //        Engine specific parameters may be passed in but the parameter keys must be prefixed by the name of the engine they are intended for.
//            //        For example the keys "com.svox.pico_foo" and "com.svox.pico:bar" will be passed to the engine named "com.svox.pico" if it is being used.
//            tts?.speak("when i was young, I listen to the radio, waiting for my favorite song.", TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
//            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
//                override fun onStart(utteranceId: String?) {  // 开始播放
//                    Logger.d("TtsNavigator::onStart:utteranceId=${utteranceId}")
//                }
//
//                override fun onDone(utteranceId: String?) {  // 播放完成
//                    Logger.d("TtsNavigator::onDone:utteranceId=${utteranceId}")
//                }
//
//                override fun onError(utteranceId: String?) {  // 播放出错
//                    Logger.d("TtsNavigator::onError:utteranceId=${utteranceId}")
//                }
//
//                override fun onError(utteranceId: String?, errorCode: Int) {  // 播放出错
//                    Logger.d("TtsNavigator::onError:utteranceId=${utteranceId},errorCode=$errorCode")
//                }
//
//                override fun onStop(utteranceId: String?, interrupted: Boolean) {
//                    super.onStop(utteranceId, interrupted)
//                    Logger.d("TtsNavigator::onStop:utteranceId=${utteranceId},interrupted=$interrupted")
//                }
//
//                override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
//                    super.onAudioAvailable(utteranceId, audio)
//                    Logger.d("TtsNavigator::onAudioAvailable:utteranceId=${utteranceId},audio=${audio?.size}")
//                }
//
//                override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
//                    super.onBeginSynthesis(utteranceId, sampleRateInHz, audioFormat, channelCount)
//                    Logger.d("TtsNavigator::onBeginSynthesis:utteranceId=${utteranceId},sampleRateInHz=${sampleRateInHz}, audioFormat=$audioFormat,channelCount=$channelCount")
//                }
//
//                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
//                    super.onRangeStart(utteranceId, start, end, frame)
//                    Logger.d("TtsNavigator::onRangeStart:utteranceId=${utteranceId},start=${start}, end=$end,frame=$frame")
//                }
//            })
//        }
//        return true
//    }
//
//    fun pause(): Boolean {
//        if (!initSuccess || tts == null) {
//            return false
//        }
//
//        tts?.stop()
//
//        return true
//    }
//
//    fun onCrate() {
//        tts = TextToSpeech(context, object : TextToSpeech.OnInitListener {
//            override fun onInit(status: Int) {
//                Logger.d("TtsNavigator::onCreate:onInit:status=$status")
//                if (status == TextToSpeech.SUCCESS) {
//                    val engines = tts?.engines.orEmpty()
//                    for(engine in engines) {
//                        Logger.d("TtsNavigator::engine[${engine.toString()}]")
//                    }
//
//                    initSuccess = true
//                } else {
//                    initSuccess = false
//                }
//            }
//        }, "com.wxn.reader")
//    }
//
//    fun onDestroy() {
//        if (tts != null) {
//            tts?.stop()
//            tts?.shutdown()
//            tts = null
//        }
//    }
//
//    init {
//        onCrate()
//    }

    //---------------------------

    private var ttsLocale : Locale = LanguageUtil.LANG_EN.locale
    private var speed = 1.0f
    private var pitch = 1.0f
    @Volatile
    private var selectedVoiceNameOverride: String? = null
    private val previewSession = TtsPreviewSession()
    private val previewStateLock = Any()
    @Volatile
    private var activePreviewState: PreviewState? = null
    private val playbackStateLock = Any()
    private var activePlayback: TtsPlaybackSession<TtsParagraph>? = null
    private val _engineState = MutableStateFlow<TtsEngineState>(TtsEngineState.Initializing)
    val engineState: StateFlow<TtsEngineState> = _engineState.asStateFlow()

    init {
        initializeSpeech()
    }

    val preferencesFlow
        get() = ttsPreferencesUtil.ttsPreferencesFlow

    fun availableVoices(): List<SystemTtsVoice> {
        if (engineState.value != TtsEngineState.Ready) {
            return emptyList()
        }
        return platformVoices()
            .map { voice ->
                SystemTtsVoice(
                    name = voice.name,
                    localeTag = voice.locale.toLanguageTag(),
                    isNetworkRequired = voice.isNetworkConnectionRequired,
                    isInstalled = !voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED),
                    quality = voice.quality,
                    latency = voice.latency,
                )
            }
            .sortedWith(
                compareBy<SystemTtsVoice>(
                    { !it.isInstalled },
                    { it.localeTag.lowercase() },
                    { it.name.lowercase() },
                )
            )
    }

    suspend fun selectedVoiceName(): String {
        return selectedVoiceNameOverride ?: ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()?.voiceName.orEmpty()
    }

    fun setVoice(voiceName: String): Boolean {
        cancelActivePreview(restorePreviousState = true)
        if (voiceName.isBlank()) {
            if (Speech.getInstance().resetTextToSpeechVoice() != TextToSpeech.SUCCESS) {
                return false
            }
            saveVoiceName("")
            return true
        }

        val selectedVoice = availableVoices().firstOrNull {
            it.name == voiceName && it.isInstalled
        } ?: return false
        val platformVoice = platformVoices().firstOrNull { it.name == selectedVoice.name } ?: return false

        if (Speech.getInstance().setTextToSpeechVoice(platformVoice) != TextToSpeech.SUCCESS) {
            return false
        }
        saveVoiceName(selectedVoice.name)
        return true
    }

    fun previewVoice(
        voiceName: String,
        replaceCurrentSpeech: Boolean = false,
    ): Boolean {
        cancelActivePreview(restorePreviousState = true)
        if (Speech.getInstance().isSpeaking) {
            if (!replaceCurrentSpeech) {
                return false
            }
            Speech.getInstance().stopTextToSpeech()
        }

        val selectedVoice = availableVoices().firstOrNull {
            it.name == voiceName && it.isInstalled
        } ?: return false
        val platformVoice = platformVoices().firstOrNull { it.name == selectedVoice.name } ?: return false
        val previousVoice = Speech.getInstance().textToSpeechVoice
        val previousQueueMode = Speech.getInstance().textToSpeechQueueMode
        val previewToken = synchronized(previewStateLock) {
            previewSession.begin().also { token ->
                activePreviewState = PreviewState(token, previousVoice, previousQueueMode)
            }
        }

        if (Speech.getInstance().setTextToSpeechVoice(platformVoice) != TextToSpeech.SUCCESS) {
            synchronized(previewStateLock) {
                previewSession.cancel()
                activePreviewState = null
            }
            return false
        }
        Speech.getInstance().setTextToSpeechQueueMode(TextToSpeech.QUEUE_FLUSH)
        Speech.getInstance().say("Здравствуйте. Это пример выбранного голоса.",
            object : TextToSpeechCallback {
                override fun onStart() = Unit

                override fun onCompleted() {
                    finishPreview(previewToken)
                }

                override fun onError() {
                    finishPreview(previewToken)
                }
            })
        return true
    }

    suspend fun play(textLines: List<TextLine>?, onReadLine: (List<TextLine>, Boolean)->Unit) : Int =
        playPages(listOf(textLines.orEmpty()), onReadLine)

    suspend fun playPages(
        textPages: List<List<TextLine>>,
        onReadLine: (List<TextLine>, Boolean)->Unit,
    ): Int {
        Logger.i("TtsNavigator::play")
        if (!awaitEngineReady()) {
            return 0
        }
        cancelActivePreview(restorePreviousState = true)
        val playback = beginPlayback()
        var status = 1
        try {
            val ttsPreferences = ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()
            if (ttsPreferences == null) {
                Logger.e("TtsNavigator::play::ttsPreferences is null")
                status = 0
            } else {
//                ttsLocale = AppLanguage.fromCode(ttsPreferences.localeCode).locale
                speed = ttsPreferences.speed
                pitch = ttsPreferences.pitch

                applySelectedVoice(selectedVoiceNameOverride ?: ttsPreferences.voiceName)

//                val localeSuppported = Speech.getInstance().setLocale(ttsLocale)
//                if (localeSuppported < 0) {
//                    return localeSuppported;
//                }
//                Logger.d("TtsNavigator::play[language[$ttsLocale]], localeSupported[$localeSuppported]")
                Speech.getInstance().setTextToSpeechRate(speed)
                Speech.getInstance().setTextToSpeechPitch(pitch)
                Speech.getInstance().setTextToSpeechQueueMode(TextToSpeech.QUEUE_ADD)
                Speech.getInstance().setGetPartialResults(true)

                val textLineMap = linkedMapOf<Pair<Int, Int>, ArrayList<TextLine>>()
                val texts = linkedMapOf<Pair<Int, Int>, StringBuilder>()
                textPages.forEachIndexed { pageIndex, textLines ->
                    for (textLine in textLines) {
                        if (!textLine.isImage && !textLine.isLine && textLine.text.isNotEmpty()) {
                            val paragraphKey = pageIndex to textLine.paragraphIndex
                            textLineMap.getOrPut(paragraphKey) { arrayListOf() }.add(textLine)
                            texts.getOrPut(paragraphKey) { StringBuilder() }.append(textLine.text)
                        }
                    }
                }

                val paragraphs = textLineMap.keys.mapNotNull { key ->
                    val lines = textLineMap[key] ?: return@mapNotNull null
                    val text = texts[key]?.toString().orEmpty()
                    text.takeIf(String::isNotBlank)?.let { TtsParagraph(it, lines) }
                }
                status = playBufferedParagraphs(
                    paragraphs = paragraphs,
                    bufferSize = ttsPreferences.bufferedParagraphs,
                    onReadLine = onReadLine,
                    playback = playback,
                )
            }
        }catch(ex : SpeechRecognitionNotAvailable) {
            Logger.e("TtsNavigator::$ex")
            status = 0
        } finally {
            finishPlayback(playback)
        }
        return status
    }

    private suspend fun playBufferedParagraphs(
        paragraphs: List<TtsParagraph>,
        bufferSize: Int,
        onReadLine: (List<TextLine>, Boolean) -> Unit,
        playback: TtsPlaybackSession<TtsParagraph>,
    ): Int {
        if (paragraphs.isEmpty()) {
            return 1
        }

        val buffer = TtsParagraphBuffer(paragraphs, bufferSize)
        var queuedCount = 0

        fun enqueue(paragraph: TtsParagraph): Boolean {
            synchronized(playbackStateLock) {
                if (activePlayback !== playback) {
                    return false
                }
                queuedCount++
                Speech.getInstance().say(paragraph.text, object : TextToSpeechCallback {
                    override fun onStart() {
                        playback.started(paragraph)
                    }

                    override fun onCompleted() {
                        playback.completed(paragraph, succeeded = true)
                    }

                    override fun onError() {
                        playback.completed(paragraph, succeeded = false)
                    }
                })
                return true
            }
        }

        for (paragraph in buffer.initial()) {
            if (!enqueue(paragraph)) {
                return 0
            }
        }
        while (queuedCount > 0) {
            when (val event = playback.nextEvent() ?: return 0) {
                is TtsPlaybackEvent.Started -> withContext(Dispatchers.Main) {
                    onReadLine(event.value.lines, true)
                }

                is TtsPlaybackEvent.Completed -> {
                    queuedCount--
                    withContext(Dispatchers.Main) {
                        onReadLine(event.value.lines, false)
                    }
                    if (!event.succeeded) {
                        Speech.getInstance().stopTextToSpeech()
                        return 0
                    }
                    val nextParagraph = buffer.nextAfterCompletion()
                    if (nextParagraph != null && !enqueue(nextParagraph)) {
                        return 0
                    }
                }
            }
        }
        return 1
    }

    fun setSpeed(speed: Float) {
        Logger.i("TtsNavigator::setSpeed:speed=$speed")
        val speechSpeed = speed.coerceIn(0.25f, 2.0f)
        val update = TtsSettingUpdatePolicy.forValue(this.speed, speechSpeed)
        if (update.applyToEngine) {
            this.speed = speechSpeed
            Speech.getInstance().setTextToSpeechRate(speechSpeed)
        }

        if (update.persist) {
            Coroutines.scope().launch {
                ttsPreferencesUtil.updateSpeed(speechSpeed)
            }
        }
    }

    fun setPitch(pitch: Float) {
        Logger.i("TtsNavigator::setPitch:pitch=$pitch")
        val speechPitch = pitch.coerceIn(0.25f, 2.0f)
        val update = TtsSettingUpdatePolicy.forValue(this.pitch, speechPitch)
        if (update.applyToEngine) {
            this.pitch = speechPitch
            Speech.getInstance().setTextToSpeechPitch(speechPitch)
        }

        if (update.persist) {
            Coroutines.scope().launch {
                ttsPreferencesUtil.updatePitch(speechPitch)
            }
        }
    }

    fun setBufferedParagraphs(bufferedParagraphs: Int) {
        Coroutines.scope().launch {
            ttsPreferencesUtil.updateBufferedParagraphs(bufferedParagraphs)
        }
    }
    fun setLanguage(language: LanguageInfo?, useBookLanguage: Boolean = false): Boolean {
        Logger.i("TtsNavigator::setLanguage:language=$language")
        cancelActivePreview(restorePreviousState = true)
        val newlocale = language?.locale ?: return false
        if (newlocale != this.ttsLocale) {
            val supportLanguage = Speech.getInstance().setLocale(language.locale)
            if (supportLanguage < 0) {
                return false
            }
            this.ttsLocale = newlocale
            Logger.d("TtsNavigator::setLanguage::language[$language], supportLanguage[$supportLanguage]")
        }
        Coroutines.scope().launch {
            ttsPreferencesUtil.updateLanguage(language.code, useBookLanguage)
        }
        return true
    }

    fun useBookLanguage() {
        Coroutines.scope().launch {
            val preferences = ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull() ?: return@launch
            ttsPreferencesUtil.updateLanguage(
                localeCode = preferences.localeCode,
                useBookLanguage = true,
            )
        }
    }

    fun stop() {
        Logger.i("TtsNavigator::stop")
        cancelActivePreview(restorePreviousState = true)
        cancelActivePlayback()
        Speech.getInstance().stopTextToSpeech()
    }

    fun onDestroy() {
        Logger.i("TtsNavigator::onDestroy")
        stop()
    }

    fun retryEngine() {
        if (_engineState.value == TtsEngineState.Initializing) {
            return
        }

        _engineState.value = TtsEngineState.Initializing
        runCatching { Speech.getInstance().shutdown() }
            .onFailure { error -> Logger.e("TtsNavigator::retryEngine shutdown failed: $error") }
        initializeSpeech()
    }

    private fun initializeSpeech() {
        runCatching {
            Speech.init(context, null, TextToSpeech.OnInitListener { status ->
                _engineState.value = if (status == TextToSpeech.SUCCESS) {
                    TtsEngineState.Ready
                } else {
                    TtsEngineState.Failed
                }
            })
        }.onFailure { error ->
            Logger.e("TtsNavigator::initializeSpeech failed: $error")
            _engineState.value = TtsEngineState.Failed
        }
    }

    private suspend fun awaitEngineReady(): Boolean {
        return engineState.first { state -> state != TtsEngineState.Initializing } == TtsEngineState.Ready
    }

    private fun applySelectedVoice(savedVoiceName: String) {
        val voice = TtsVoiceSelector.select(
            voices = availableVoices(),
            savedVoiceName = savedVoiceName,
            requestedLanguage = ttsLocale.language,
        ) ?: run {
            if (savedVoiceName.isNotBlank()) {
                Speech.getInstance().resetTextToSpeechVoice()
            }
            return
        }
        platformVoices().firstOrNull { it.name == voice.name }?.let { platformVoice ->
            if (Speech.getInstance().setTextToSpeechVoice(platformVoice) != TextToSpeech.SUCCESS) {
                Speech.getInstance().resetTextToSpeechVoice()
            }
        }
    }

    private fun platformVoices(): List<Voice> {
        return runCatching { Speech.getInstance().supportedTextToSpeechVoices }.getOrDefault(emptyList())
    }

    private fun saveVoiceName(voiceName: String) {
        selectedVoiceNameOverride = voiceName
        Coroutines.scope().launch {
            ttsPreferencesUtil.updateVoiceName(voiceName)
        }
    }

    private data class TtsParagraph(
        val text: String,
        val lines: List<TextLine>,
    )

    private fun cancelActivePlayback() {
        synchronized(playbackStateLock) {
            activePlayback?.cancel()
            activePlayback = null
        }
    }

    private fun beginPlayback(): TtsPlaybackSession<TtsParagraph> {
        val playback = TtsPlaybackSession<TtsParagraph>()
        synchronized(playbackStateLock) {
            activePlayback?.cancel()
            activePlayback = playback
        }
        return playback
    }

    private fun finishPlayback(playback: TtsPlaybackSession<TtsParagraph>) {
        playback.cancel()
        synchronized(playbackStateLock) {
            if (activePlayback === playback) {
                activePlayback = null
            }
        }
    }

    private fun cancelActivePreview(restorePreviousState: Boolean) {
        synchronized(previewStateLock) {
            if (!previewSession.cancel()) {
                return
            }
            val previewState = activePreviewState
            activePreviewState = null
            Speech.getInstance().stopTextToSpeech()
            if (restorePreviousState && previewState != null) {
                restorePreviewState(previewState)
            }
        }
    }

    private fun finishPreview(token: Long) {
        synchronized(previewStateLock) {
            if (!previewSession.finish(token)) {
                return
            }
            val previewState = activePreviewState.takeIf { it?.token == token }
            activePreviewState = null
            if (previewState != null) {
                restorePreviewState(previewState)
            }
        }
    }

    private fun restorePreviewState(previewState: PreviewState) {
        if (previewState.previousVoice == null) {
            Speech.getInstance().resetTextToSpeechVoice()
        } else {
            Speech.getInstance().setTextToSpeechVoice(previewState.previousVoice)
        }
        Speech.getInstance().setTextToSpeechQueueMode(previewState.previousQueueMode)
    }

    private data class PreviewState(
        val token: Long,
        val previousVoice: Voice?,
        val previousQueueMode: Int,
    )
//
//    fun isPlaying() : Boolean {
//        val isPlaying = Speech.getInstance().isListening
//        Logger.i("TtsNavigator::isPlaying[$isPlaying]")
//        return isPlaying
//    }
}
