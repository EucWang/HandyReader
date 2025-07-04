package com.wxn.reader.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.LANG_AVAILABLE
import android.speech.tts.TextToSpeech.LANG_COUNTRY_AVAILABLE
import android.speech.tts.TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
import android.speech.tts.UtteranceProgressListener
import com.wxn.reader.data.model.AppLanguage
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

        //LANG_AVAILABLE, LANG_COUNTRY_AVAILABLE, LANG_COUNTRY_VAR_AVAILABLE, LANG_MISSING_DATA and LANG_NOT_SUPPORTED.
        val isSuppport = tts?.isLanguageAvailable(locale)
        if (isSuppport == LANG_AVAILABLE ||
            isSuppport == LANG_COUNTRY_AVAILABLE ||
            isSuppport == LANG_COUNTRY_VAR_AVAILABLE
        ) {

            //        params – Parameters for the request. Can be null.
            //        Supported parameter names: TextToSpeech.Engine.KEY_PARAM_STREAM,
            //        TextToSpeech.Engine.KEY_PARAM_VOLUME,
            //        TextToSpeech.Engine.KEY_PARAM_PAN.
            //        Engine specific parameters may be passed in but the parameter keys must be prefixed by the name of the engine they are intended for.
            //        For example the keys "com.svox.pico_foo" and "com.svox.pico:bar" will be passed to the engine named "com.svox.pico" if it is being used.
            tts?.speak("", TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {  // 开始播放
                    //TODO
                }

                override fun onDone(utteranceId: String?) {  // 播放完成
                    //TODO
                }

                override fun onError(utteranceId: String?) {  // 播放出错
                    //TODO
                }

                override fun onError(utteranceId: String?, errorCode: Int) {  // 播放出错
                    //TODO
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    super.onStop(utteranceId, interrupted)
                }

                override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
                    super.onAudioAvailable(utteranceId, audio)
                }

                override fun onBeginSynthesis(utteranceId: String?, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                    super.onBeginSynthesis(utteranceId, sampleRateInHz, audioFormat, channelCount)
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    super.onRangeStart(utteranceId, start, end, frame)
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
                if (status == TextToSpeech.SUCCESS) {
                    initSuccess = true
                } else {
                    initSuccess = false
                }
            }
        })
    }

    fun onDestroy() {
        if (tts != null) {
            tts?.stop()
            tts?.shutdown()
            tts = null
        }
    }
}