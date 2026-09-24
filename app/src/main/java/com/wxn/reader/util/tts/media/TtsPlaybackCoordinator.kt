package com.wxn.reader.util.tts.media

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TtsMediaPlaybackState {
    Idle,
    Playing,
    Paused,
}

enum class TtsMediaCommand {
    Play,
    Pause,
    Stop,
}

data class TtsMediaState(
    val bookId: Long? = null,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val progression: Double = 0.0,
    val estimatedDurationMs: Long = 0L,
    val estimatedPositionMs: Long = 0L,
    val estimatedRemainingMs: Long = 0L,
    val playbackState: TtsMediaPlaybackState = TtsMediaPlaybackState.Idle,
)

data class TtsPlaybackHandlers(
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onStop: () -> Unit,
)

class TtsPlaybackCoordinator {
    private val lock = Any()
    private var activeOwnerId: String? = null
    private var handlers: TtsPlaybackHandlers? = null
    private val _state = MutableStateFlow(TtsMediaState())
    val state: StateFlow<TtsMediaState> = _state.asStateFlow()

    fun register(ownerId: String, handlers: TtsPlaybackHandlers) {
        synchronized(lock) {
            activeOwnerId = ownerId
            this.handlers = handlers
        }
    }

    fun unregister(ownerId: String) {
        synchronized(lock) {
            if (activeOwnerId != ownerId) {
                return
            }
            activeOwnerId = null
            handlers = null
            _state.value = TtsMediaState()
        }
    }

    fun publish(ownerId: String, state: TtsMediaState) {
        synchronized(lock) {
            if (activeOwnerId == ownerId) {
                _state.value = state
            }
        }
    }

    fun dispatch(command: TtsMediaCommand) {
        val activeHandlers = synchronized(lock) {
            if (activeOwnerId == null || handlers == null) {
                _state.value = TtsMediaState()
                null
            } else if (_state.value.playbackState == TtsMediaPlaybackState.Idle) {
                null
            } else {
                handlers
            }
        } ?: return
        when (command) {
            TtsMediaCommand.Play -> activeHandlers.onPlay()
            TtsMediaCommand.Pause -> activeHandlers.onPause()
            TtsMediaCommand.Stop -> activeHandlers.onStop()
        }
    }
}
