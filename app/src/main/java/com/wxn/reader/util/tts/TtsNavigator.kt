package com.wxn.reader.util.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.wxn.base.util.Logger
import com.wxn.reader.data.model.AppLanguage
import net.gotev.speech.Speech
import net.gotev.speech.SpeechDelegate
import net.gotev.speech.SpeechRecognitionNotAvailable
import net.gotev.speech.TextToSpeechCallback
import java.util.UUID

class TtsNavigator(
    val context: Context,
    var speed: Float,// 语速
    var pitch: Float, //音调
    var language: AppLanguage
) {

    var initSuccess: Boolean = false

    var tts: TextToSpeech? = null

    fun skipToPreviousUtterance(): Boolean {
        if (!initSuccess || tts == null) {
            return false
        }

        //TODO

        return true
    }

    fun skipToNextUtterance(): Boolean {
        if (!initSuccess || tts == null) {
            return false
        }

        //TODO
        return true
    }

    fun play(): Boolean {
        if (!initSuccess || tts == null) {
            return false
        }

        tts?.setPitch(pitch)
        tts?.setSpeechRate(speed)
        val locale = language.locale
        tts?.setLanguage(locale)
        val engines = tts?.engines.orEmpty()
        for(engine in engines) {
            Logger.d("TtsNavigator::engine[${engine.toString()}]")
        }

        //LANG_AVAILABLE, LANG_COUNTRY_AVAILABLE, LANG_COUNTRY_VAR_AVAILABLE, LANG_MISSING_DATA and LANG_NOT_SUPPORTED.
        val isSuppport = tts?.isLanguageAvailable(locale)
        if (isSuppport == TextToSpeech.LANG_AVAILABLE ||
            isSuppport == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            isSuppport == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        ) {

            //        params – Parameters for the request. Can be null.
            //        Supported parameter names: TextToSpeech.Engine.KEY_PARAM_STREAM,
            //        TextToSpeech.Engine.KEY_PARAM_VOLUME,
            //        TextToSpeech.Engine.KEY_PARAM_PAN.
            //        Engine specific parameters may be passed in but the parameter keys must be prefixed by the name of the engine they are intended for.
            //        For example the keys "com.svox.pico_foo" and "com.svox.pico:bar" will be passed to the engine named "com.svox.pico" if it is being used.
            tts?.speak("when i was young, I listen to the radio, waiting for my favorite song.", TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {  // 开始播放
                    Logger.d("TtsNavigator::onStart:utteranceId=${utteranceId}")
                }

                override fun onDone(utteranceId: String?) {  // 播放完成
                    Logger.d("TtsNavigator::onDone:utteranceId=${utteranceId}")
                    //TODO
                }

                override fun onError(utteranceId: String?) {  // 播放出错
                    Logger.d("TtsNavigator::onError:utteranceId=${utteranceId}")
                    //TODO
                }

                override fun onError(utteranceId: String?, errorCode: Int) {  // 播放出错
                    Logger.d("TtsNavigator::onError:utteranceId=${utteranceId},errorCode=$errorCode")
                    //TODO
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    super.onStop(utteranceId, interrupted)
                    Logger.d("TtsNavigator::onStop:utteranceId=${utteranceId},interrupted=$interrupted")
                }

                override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                    super.onAudioAvailable(utteranceId, audio)
                    Logger.d("TtsNavigator::onAudioAvailable:utteranceId=${utteranceId},audio=${audio?.size}")
                }

                override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                    super.onBeginSynthesis(utteranceId, sampleRateInHz, audioFormat, channelCount)
                    Logger.d("TtsNavigator::onBeginSynthesis:utteranceId=${utteranceId},sampleRateInHz=${sampleRateInHz}, audioFormat=$audioFormat,channelCount=$channelCount")
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    super.onRangeStart(utteranceId, start, end, frame)
                    Logger.d("TtsNavigator::onRangeStart:utteranceId=${utteranceId},start=${start}, end=$end,frame=$frame")
                }
            })
        }
        return true
    }

    fun pause(): Boolean {
        if (!initSuccess || tts == null) {
            return false
        }

        tts?.stop()

        return true
    }

    fun onCrate() {
        tts = TextToSpeech(context, object : TextToSpeech.OnInitListener {
            override fun onInit(status: Int) {
                Logger.d("TtsNavigator::onCreate:onInit:status=$status")
                if (status == TextToSpeech.SUCCESS) {
                    val engines = tts?.engines.orEmpty()
                    for(engine in engines) {
                        Logger.d("TtsNavigator::engine[${engine.toString()}]")
                    }

                    initSuccess = true
                } else {
                    initSuccess = false
                }
            }
        }, "com.wxn.reader")
    }

    fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }

    init {
        onCrate()
    }

    //---------------------------

//    init {
//        Speech.init(context)
//    }
//
//    fun play() {
//        try {
//
//        Speech.getInstance().startListening(object: SpeechDelegate{
//            override fun onStartOfSpeech() {
//                Logger.d("TtsNavigator:onStartOfSpeech")
//            }
//
//            override fun onSpeechRmsChanged(value: Float) {
//                Logger.d("TtsNavigator:onSpeechRmsChanged:value=$value")
//            }
//
//            override fun onSpeechPartialResults(results: List<String?>?) {
//                Logger.d("TtsNavigator:onSpeechPartialResults:results=${results}")
//            }
//
//            override fun onSpeechResult(result: String?) {
//                Logger.d("TtsNavigator:onSpeechResult:result=${result}")
//            }
//        })
//        }catch(ex : SpeechRecognitionNotAvailable) {
//            Logger.e("TtsNavigator::$ex")
//        }
//
//        Speech.getInstance().setLocale(language.locale)
//        Speech.getInstance().setTextToSpeechRate(1.0f)
//        Speech.getInstance().setTextToSpeechPitch(1.0f)
//        Speech.getInstance().setTextToSpeechQueueMode(TextToSpeech.QUEUE_FLUSH)
//        Speech.getInstance().setGetPartialResults(true)
//        Speech.getInstance().say("When I was young, I listen to the radio, waiting for my favorite song",
//            object : TextToSpeechCallback{
//                override fun onStart() {
//                    Logger.d("TtsNavigator::say callback onStart")
//                }
//
//                override fun onCompleted() {
//                    Logger.d("TtsNavigator::say callback onCompleted")
//                }
//
//                override fun onError() {
//                    Logger.d("TtsNavigator::say callback onError")
//                }
//
//            })
//    }
//
//    fun onDestroy() {
//        Speech.getInstance().stopTextToSpeech()
//    }
}