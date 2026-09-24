package com.wxn.reader.util.tts.media

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.SimpleBasePlayer.PositionSupplier
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TtsNarrationPlayer(
    private val playbackCoordinator: TtsPlaybackCoordinator,
    private val textFormatter: TtsMediaTextFormatter,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val availableCommands = Player.Commands.Builder()
        .add(Player.COMMAND_PLAY_PAUSE)
        .add(Player.COMMAND_STOP)
        .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
        .add(Player.COMMAND_GET_TIMELINE)
        .add(Player.COMMAND_GET_METADATA)
        .build()

    @Volatile
    private var mediaState = playbackCoordinator.state.value

    init {
        scope.launch {
            playbackCoordinator.state.collect { state ->
                mediaState = state
                invalidateState()
            }
        }
    }

    override fun getState(): State {
        val state = mediaState
        if (state.bookId == null) {
            return State.Builder()
                .setAvailableCommands(availableCommands)
                .setPlayWhenReady(
                    false,
                    Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                )
                .setPlaybackState(Player.STATE_IDLE)
                .build()
        }
        val builder = State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlayWhenReady(
                state.playbackState == TtsMediaPlaybackState.Playing,
                Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
            .setPlaybackState(
                if (state.playbackState == TtsMediaPlaybackState.Idle) {
                    Player.STATE_IDLE
                } else {
                    Player.STATE_READY
                },
            )

        val progressDescription = textFormatter.progressDescription(state)
        val notificationContentText = listOf(state.chapterTitle, progressDescription)
            .filter(String::isNotBlank)
            .joinToString(" · ")
        val metadata = MediaMetadata.Builder()
            .setTitle(state.bookTitle)
            .setArtist(notificationContentText)
            .setSubtitle(state.chapterTitle)
            .setDescription(progressDescription)
            .setDurationMs(state.estimatedDurationMs)
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(state.bookId.toString())
            .setMediaMetadata(metadata)
            .build()
        val itemData = MediaItemData.Builder(mediaItem.mediaId)
            .setMediaItem(mediaItem)
            .setMediaMetadata(metadata)
            .setIsSeekable(false)
            .setDurationUs(TimeUnit.MILLISECONDS.toMicros(state.estimatedDurationMs))
            .build()

        return builder
            .setPlaylist(listOf(itemData))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(PositionSupplier.getConstant(state.estimatedPositionMs))
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        playbackCoordinator.dispatch(
            if (playWhenReady) TtsMediaCommand.Play else TtsMediaCommand.Pause,
        )
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        playbackCoordinator.dispatch(TtsMediaCommand.Stop)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return Futures.immediateVoidFuture()
    }

}
