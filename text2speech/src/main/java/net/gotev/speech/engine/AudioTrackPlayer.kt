package net.gotev.speech.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.AudioTrack.PLAYSTATE_PLAYING
import android.speech.tts.TextToSpeech
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.launchMain
import kotlinx.coroutines.delay
import net.gotev.speech.TextToSpeechCallback
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AudioTrackPlayer : IAudioPlayer {

    /***
     * Sherpa 写出音频wav数据是否停止, 如果不停止,则持续写,
     */
    private var stopped = AtomicBoolean(false)

    private var scope = Coroutines.mainScope()

    /***
     * 音频产出
     */
    private val trackRef = AtomicReference<AudioTrack?>()

    private @Volatile var initSuccess: Boolean = false

    private @Volatile var blockingWriting: Boolean = false

    override fun initAudioTrack(sampleRate: Int, initListener: TextToSpeech.OnInitListener?) {
        Logger.i("AudioTrackPlayer::initAudioTrack,sampleRate=$sampleRate")
        val bufLength = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        Logger.i("AudioTrackPlayer::initAudioTrack:buffLength: $bufLength")

        val attr = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(sampleRate)
            .build()

        scope.launchMain {
            try {
                trackRef.set(
                    AudioTrack(
                        attr, format, bufLength, AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                )

                try {
                    trackRef.get()?.play()
                } catch (ex: Exception) {
                    Logger.e(ex)
                }
                Logger.d("AudioTrackPlayer::initAudioTrack:success")
                initSuccess = true
                initListener?.onInit(TextToSpeech.SUCCESS)
            } catch (ex: Exception) {
                Logger.e("AudioTrackPlayer::initAudioTrack:failed:$ex")
                initSuccess = false
                initListener?.onInit(TextToSpeech.ERROR)
            }
        }
    }

    override fun isPlaying(): Boolean {
        val ret = isValid() && trackRef.get()?.playState == PLAYSTATE_PLAYING
        Logger.i("AudioTrackPlayer::isPlaying[$ret]")
        return ret
    }

    override suspend fun destroy() {
        Logger.i("AudioTrackPlayer::destroy,blockingWriting=$blockingWriting")
        var i = 1L
        var total = 0L
        var count = 0
        while (blockingWriting) {
            count ++
            delay(i)
            total += i
            i += 3
            i.coerceAtMost(15)
        }
        Logger.d("AudioTrackPlayer::destroy::delay for blocking write :cycler=$count, [$total]ms")
        val track = trackRef.getAndSet(null)
        if (track == null) {
            Logger.d("AudioTrackPlayer::destroySync: track already null")
            return
        }
        stopped.set(true)
        // 在主线程同步执行AudioTrack操作
        scope.launchMain {
            try {
                track.pause()
                track.flush()
                track.stop()
                Logger.i("AudioTrackPlayer::destroySync: AudioTrack released successfully")
            } catch (ex: Exception) {
                Logger.e("AudioTrackPlayer::destroySync: error releasing AudioTrack: $ex")
                // 即使出错，也要确保不再使用
            } finally {
                try {
                    track.release()
                } catch (e: Exception) {
                    Logger.e("AudioTrackPlayer::destroySync: second attempt to release failed: $e")
                }
            }
        }
    }

    override fun isValid(): Boolean {
        return trackRef.get() != null && initSuccess
    }

    /****
     * 阻塞等待设置播放状态,
     * 失败返回false
     */
    override fun prepare(): Boolean {
        Logger.i("AudioTrackPlayer::prepare")
        if (!isValid()) {
            Logger.d("AudioTrackPlayer::destroy:track is null")
            return false
        }

        if (isPlaying()) {
            return true
        }

        stopped.set(false)
        scope.launchMain {
            try {
                trackRef.get()?.apply {
                    pause()
                    flush()
                    play()
                }
            } catch (ex: Exception) {
                Logger.e("AudioTrackPlayer:resetAudioTrackSync: ${ex.message}")
            }
        }
        return true
    }

    /****
     * 运行在jni的线程中,
     * 接收音频字节数组,
     * 写出到设备的播放缓冲中, 即马上播放这些音频
     */
    override fun onStreaming(utteranceId : String, samples: FloatArray): Int {
        val track = trackRef.get()
        if (track == null || !initSuccess) {
            Logger.d("AudioTrackPlayer::destroy:track is not playing")
            return 0
        }

        if (!isValid()) {
            Logger.d("AudioTrackPlayer::destroy:track is not playing")
            return 0
        }
        if (stopped.get()) {
            Logger.d("AudioTrackPlayer::stopped is false, onStreaming is failed.")
            scope.launchMain {
                try {
                    track.stop()
                } catch (ex: Exception) {
                    Logger.e("AudioTrackPlayer::stop: $ex")
                }
            }
            return 0
        }
        return try {
            blockingWriting = true
            val track = trackRef.get()
            if (track == null || !initSuccess) {
                Logger.d("AudioTrackPlayer::destroy:track is not playing")
                return 0
            }
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            blockingWriting = false
            Logger.i("AudioTrackPlayer::onStreaming:write samples.size[${samples.size}]")
            1
        } catch (ex: IllegalStateException) {
            Logger.e("AudioTrackPlayer::onStreaming:IllegalStateException - ${ex.message}")
            0
        } catch (ex: Exception) {
            Logger.e("AudioTrackPlayer::onStreaming:${ex.javaClass.simpleName} - ${ex.message}")
            0
        }
    }

    override fun stop() {
        Logger.i("AudioTrackPlayer::stop")
        if (!isValid()) {
            Logger.d("AudioTrackPlayer::destroy:track is null")
            return
        }
        stopped.set(true)

        scope.launchMain {
            try {
                trackRef.get()?.pause()
                trackRef.get()?.flush()
                trackRef.get()?.stop()
            } catch (ex: Exception) {
                Logger.e("AudioTrackPlayer::stop: $ex")
            }
        }
    }

    override fun canReceive(): Boolean {
        return isValid()
    }

    override fun setPlaySentenceCallback(utteranceId: String, callback: TextToSpeechCallback)  {
        //do nothing
    }
}