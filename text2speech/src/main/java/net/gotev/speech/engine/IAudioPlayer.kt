package net.gotev.speech.engine

import android.speech.tts.TextToSpeech
import net.gotev.speech.TextToSpeechCallback

interface IAudioPlayer {

    fun initAudioTrack(sampleRate: Int, initListener: TextToSpeech.OnInitListener?)

    fun isPlaying(): Boolean

    suspend fun destroy()

    fun isValid(): Boolean

    fun prepare(): Boolean

    /****
     * 运行在jni的线程中,
     * 接收音频字节数组,
     * 写出到设备的播放缓冲中
     */
    fun onStreaming(utteranceId : String, samples: FloatArray): Int

    fun stop()

    fun canReceive(): Boolean

    fun setPlaySentenceCallback(utteranceId: String, callback: TextToSpeechCallback)
}


interface PlaySentenceCallback {
    fun onStart(utteranceId: String?)

    fun onEnd(utteranceId: String?)
}