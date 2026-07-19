package net.gotev.speech.engine

import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchIO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import net.gotev.speech.TextToSpeechCallback

class AudioStreamPlayer : IAudioPlayer {

    private val scope = Coroutines.scope()

    private var mAudioTrack: NonBlockingAudioTrack? = null

    private var looperJob: Job? = null

    private val listeners: HashMap<String, TextToSpeechCallback> = hashMapOf()

    override fun initAudioTrack(
        sampleRate: Int,
        initListener: TextToSpeech.OnInitListener?
    ) {
        mAudioTrack = NonBlockingAudioTrack(sampleRate, 1)
        if (true != mAudioTrack?.isValid) {
            mAudioTrack = null
            initListener?.onInit(TextToSpeech.ERROR)
        } else {
            initListener?.onInit(TextToSpeech.SUCCESS)
        }

        mAudioTrack?.setPlaySentenceCallback(object : PlaySentenceCallback {
            override fun onStart(utteranceId: String?) {
                Logger.d("SherpaOnnxEngine::PlaySentenceCallback::onStart:utteraneId=$utteranceId")
                listeners[utteranceId]?.onStart(utteranceId)
            }

            override fun onEnd(utteranceId: String?) {
                Logger.d("SherpaOnnxEngine::PlaySentenceCallback::onEnd:utteraneId=$utteranceId")
                if (!utteranceId.isNullOrEmpty()) {
                    listeners[utteranceId]?.onCompleted(utteranceId)
                    listeners.remove(utteranceId)
                }
            }
        })
    }

    override fun isPlaying(): Boolean {
        return true == mAudioTrack?.isValid && mAudioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
    }

    override suspend fun destroy() {
        stop()
        mAudioTrack?.release()
    }

    override fun isValid(): Boolean {
        return mAudioTrack?.isValid == true
    }

    override fun prepare(): Boolean {
        val ret = mAudioTrack?.play() == true

        if (ret) {
            if (looperJob == null || true != looperJob?.isActive) {
                looperJob?.cancel()
                looperJob = null
                val job = scope.launchIO {
                    while (isActive) {
                        val processRet = mAudioTrack?.process() ?: 0
                        if (processRet > 0) {
                            delay(2)   //写入成功,延迟下次继续写数据
                        }else if (processRet == 0) {  //audioTrack的环形缓冲区已满,需等待更久时间
                            delay(8)
                        } else {
                            break
                        }
                    }
                }
                looperJob = job
            }
        }

        return ret
    }

    override fun onStreaming(utteranceId : String, samples: FloatArray): Int {
        return if (true == mAudioTrack?.write(utteranceId, samples, samples.size, 10)) {
            1
        } else {
            0
        }
    }

    override fun stop() {
        looperJob?.cancel()
        looperJob = null
        mAudioTrack?.pause()   // 先暂停，使 flush 能够执行
        mAudioTrack?.flush()
        mAudioTrack?.stop()
        listeners.clear()
    }

    override fun canReceive(): Boolean {
        return (mAudioTrack?.isValid ?: false) && (mAudioTrack?.canReceive() ?: false)
    }

    override fun setPlaySentenceCallback(utteranceId: String, callback: TextToSpeechCallback) {
        if (utteranceId.isNotEmpty()) {
            listeners[utteranceId] = callback
        }
    }
}
