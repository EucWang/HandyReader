package com.wxn.reader.util.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.TextLine
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.reader.util.LanguageInfo
import com.wxn.reader.util.LanguageUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import net.gotev.speech.Speech
import net.gotev.speech.SpeechRecognitionNotAvailable
import net.gotev.speech.TextToSpeechCallback
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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

    init {
        Speech.init(context)
    }

    private var ttsLocale : Locale = LanguageUtil.LANG_EN.locale
    private var speed = 1.0f
    private var pitch = 1.0f

    suspend fun play(textLines: List<TextLine>?, onReadLine: (List<TextLine>, Boolean)->Unit) : Int {
        Logger.i("TtsNavigator::play")
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

//                val localeSuppported = Speech.getInstance().setLocale(ttsLocale)
//                if (localeSuppported < 0) {
//                    return localeSuppported;
//                }
//                Logger.d("TtsNavigator::play[language[$ttsLocale]], localeSupported[$localeSuppported]")
                Speech.getInstance().setTextToSpeechRate(speed)
                Speech.getInstance().setTextToSpeechPitch(pitch)
                Speech.getInstance().setTextToSpeechQueueMode(TextToSpeech.QUEUE_ADD)
                Speech.getInstance().setGetPartialResults(true)

                var ret  = 1
                val textLineMap = hashMapOf<Int, ArrayList<TextLine>>()
                val texts = arrayListOf<StringBuilder>()
                if (!textLines.isNullOrEmpty()) {
                    for(textLine in textLines) {
                        if (!textLine.isImage && !textLine.isLine && textLine.text.isNotEmpty()) {
                            val paragraph = textLine.paragraphIndex
                            if (textLineMap.get(paragraph) == null) {
                                textLineMap.put(paragraph, arrayListOf(textLine))
                                texts.add(StringBuilder(textLine.text))
                            } else {
                                textLineMap[paragraph]?.add(textLine)
                                texts.lastOrNull()?.append(textLine.text)
                            }
                        }
                    }
                }

                val keys = textLineMap.keys.sorted()
                var index = 0
                for(key in keys) {
                    val lines = textLineMap[key] ?: continue
                    val text = texts.getOrNull(index++) ?: continue
                    with(Dispatchers.Main) {
                        onReadLine(lines, true)
                    }
                    ret = innerPlay(text.toString())
                    Logger.d("TtsNavigator::play::innerPlay result[$ret],text[$text], key=$key, index=$index, lines.size[${lines.size}]")
                    with(Dispatchers.Main) {
                        onReadLine(lines, false)
                    }
                    if (ret != 1) {
                        status = 0
                        break
                    }
                }
            }
        }catch(ex : SpeechRecognitionNotAvailable) {
            Logger.e("TtsNavigator::$ex")
            status = 0
        }
        return status
    }

    private suspend fun innerPlay(text: String) : Int {
        Logger.d("TtsNavigator::innerPlay:text[$text]")
        var start = System.currentTimeMillis()
        return suspendCoroutine { coroutination ->
            Speech.getInstance().say(text,
                object : TextToSpeechCallback{
                    override fun onStart() {
                        Logger.d("TtsNavigator::innerPlay say callback onStart")
                        start = System.currentTimeMillis()
                    }

                    override fun onCompleted() {
                        val duration = System.currentTimeMillis() - start
                        Logger.d("TtsNavigator::innerPlay say callback onCompleted duration[${duration}ms]")
                        coroutination.resume(1)
                    }

                    override fun onError() {
                        val duration = System.currentTimeMillis() - start
                        Logger.d("TtsNavigator::innerPlay say callback onError duration[${duration}ms]")
                        coroutination.resume(-1)
                    }
                })
        }
    }

    fun setSpeed(speed: Float) {
        Logger.i("TtsNavigator::setSpeed:speed=$speed")
        val speechSpeed = speed.coerceIn(0.25f, 2.0f)
        if (speechSpeed != this.speed) {
            this.speed = speechSpeed
            Speech.getInstance().setTextToSpeechRate(speechSpeed)

            Coroutines.scope().launch {
                ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()?.let { preferences ->
                    preferences.speed = speechSpeed
                    ttsPreferencesUtil.updatePreferences(preferences)
                }
            }
        }
    }

    fun setPitch(pitch: Float) {
        Logger.i("TtsNavigator::setPitch:pitch=$pitch")
        val speechPitch = pitch.coerceIn(0.25f, 2.0f)
        if (speechPitch != this.pitch) {
            this.pitch = speechPitch
            Speech.getInstance().setTextToSpeechPitch(speechPitch)
            Coroutines.scope().launch {
                ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()?.let { preferences ->
                    preferences.pitch = speechPitch
                    ttsPreferencesUtil.updatePreferences(preferences)
                }
            }
        }
    }
    fun setLanguage(language: LanguageInfo?): Boolean {
        Logger.i("TtsNavigator::setLanguage:language=$language")
        val newlocale = language?.locale ?: return false
        if (newlocale != this.ttsLocale) {
            this.ttsLocale = newlocale
            val supportLanguage = Speech.getInstance().setLocale(language.locale)
            if (supportLanguage < 0) {
                return false
            }
            Logger.d("TtsNavigator::setLanguage::language[$language], supportLanguage[$supportLanguage]")
            Coroutines.scope().launch {
                ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()?.let { preferences ->
                    preferences.localeCode = language.code
                    ttsPreferencesUtil.updatePreferences(preferences)
                }
            }
        }
        return true
    }

    fun stop() {
        Logger.i("TtsNavigator::stop")
        Speech.getInstance().stopTextToSpeech()
    }

    fun onDestroy() {
        Logger.i("TtsNavigator::onDestroy")
        stop()
    }
//
//    fun isPlaying() : Boolean {
//        val isPlaying = Speech.getInstance().isListening
//        Logger.i("TtsNavigator::isPlaying[$isPlaying]")
//        return isPlaying
//    }
}