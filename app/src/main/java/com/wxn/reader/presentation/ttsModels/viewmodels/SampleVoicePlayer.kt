package com.wxn.reader.presentation.ttsModels.viewmodels

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Coroutines.scope
import com.wxn.base.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SampleVoicePlayer @Inject constructor(
    private val context: Context
) {

    private val scope = Coroutines.scope()

    private var exoPlayer: ExoPlayer? = null
    private var listener: androidx.media3.common.Player.Listener? = null
    private val mutex = Mutex()  // 添加并发保护
    private val _currentlyPlayingUrl = MutableStateFlow<String?>(null)
    val currentlyPlayingUrl: StateFlow<String?> = _currentlyPlayingUrl.asStateFlow()
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    suspend fun play(url: String) = mutex.withLock {
        try {
            // 停止当前播放
            stopInternal()
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                // 移除旧监听器
                listener?.let { removeListener(it) }
                // 创建新监听器
                listener = object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == androidx.media3.common.Player.STATE_ENDED ||
                            playbackState == androidx.media3.common.Player.STATE_IDLE) {
                            scope.launch {  // 使用正确的 scope
                                _currentlyPlayingUrl.value = null
                                _isPlaying.value = false
                            }
                        }
                    }
                    override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                        scope.launch {
                            _isPlaying.value = isPlayingNow
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Logger.e("Playback error: ${error?.message}")
                        scope.launch {
                            _isPlaying.value = false
                        }
                    }
                }
                listener?.let { addListener(it) }
                play()
            }
            _currentlyPlayingUrl.value = url
            _isPlaying.value = true
            Logger.d("SampleVoicePlayer: Playing $url")
        } catch (e: Exception) {
            Logger.e("SampleVoicePlayer: Error playing audio: ${e.message}")
            stopInternal()
        }
    }
    suspend fun pause() = mutex.withLock {
        exoPlayer?.takeIf { it.isPlaying }?.pause()
        _isPlaying.value = false
    }
    suspend fun resume() = mutex.withLock {
        exoPlayer?.takeIf { !it.isPlaying }?.play()
        _isPlaying.value = true
    }
    private fun stopInternal() {
        listener?.let { exoPlayer?.removeListener(it) }
        listener = null
        exoPlayer?.release()
        exoPlayer = null
        _currentlyPlayingUrl.value = null
        _isPlaying.value = false
    }
    suspend fun stop() = mutex.withLock {
        stopInternal()
    }
    fun isPlayingUrl(url: String): Boolean {
        return _currentlyPlayingUrl.value == url && _isPlaying.value
    }
}