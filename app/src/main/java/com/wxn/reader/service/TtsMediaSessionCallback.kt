package com.wxn.reader.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_STOP
import androidx.media3.common.Rating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wxn.base.util.Logger

@UnstableApi
class TtsMediaSessionCallback(
    val context: Context,
    val ttsStateHolder: TtsStateHolder,
    val service: TtsPlaybackService
) : MediaSession.Callback {

    // 默认实现即可，如需处理复杂的列表添加逻辑可在此完善
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        return super.onAddMediaItems(mediaSession, controller, mediaItems)
    }

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
            .add(SessionCommand("stop", Bundle.EMPTY))
            .build()
        // 添加 Player Commands 以支持 skip 按钮显示
        val playerCommands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(COMMAND_STOP)
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        super.onDisconnected(session, controller)
    }

    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent
    ): Boolean {
        return super.onMediaButtonEvent(session, controllerInfo, intent)
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val player = mediaSession.player
        val mediaItems = mutableListOf<MediaItem>()
        for (i in 0 until player.mediaItemCount) {
            mediaItems.add(player.getMediaItemAt(i))
        }

        val mediaItemsWithStartPosition = MediaSession.MediaItemsWithStartPosition(
            mediaItems,
            player.currentMediaItemIndex,
            player.currentPosition
        )
        return Futures.immediateFuture(mediaItemsWithStartPosition)
    }

    override fun onPlayerInteractionFinished(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        playerCommands: Player.Commands
    ) {
        super.onPlayerInteractionFinished(session, controllerInfo, playerCommands)
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        super.onPostConnect(session, controller)
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        return super.onSetMediaItems(
            mediaSession,
            controller,
            mediaItems,
            startIndex,
            startPositionMs
        )
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaId: String,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        return super.onSetRating(session, controller, mediaId, rating)
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        return super.onSetRating(session, controller, rating)
    }

    // 处理标准媒体控制命令 (拦截并同步 TTS 导航器)
    override fun onPlayerCommandRequest(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        playerCommand: Int
    ): Int {
        Logger.i("TtsMediaSessionCallback::onPlayerCommandRequest: playerCommand = $playerCommand,${
            when(playerCommand) {
                Player.COMMAND_PLAY_PAUSE -> "Player.COMMAND_PLAY_PAUSE"
                Player.COMMAND_STOP -> "Player.COMMAND_STOP"
                else -> ""
            }
        }, session.player.isPlaying=${session.player.isPlaying}")
        return when (playerCommand) {
            Player.COMMAND_PLAY_PAUSE -> {
                val state = ttsStateHolder.state.value
                val isPlaying = state.isPlaying && !state.isPaused
                Logger.i("TtsMediaSessionCallback::onPlayerCommandRequest:COMMAND_PLAY_PAUSE:isPlaying=${state.isPlaying}, isPaused=${state.isPaused}")
                if (isPlaying) {
                    service.pauseTts()
                } else {
                    service.resumeTts()
                }
                SessionResult.RESULT_SUCCESS
            }
            Player.COMMAND_STOP -> {
                service.stopTts()
                SessionResult.RESULT_SUCCESS
            }
            else -> super.onPlayerCommandRequest(session, controller, playerCommand)
        }
    }

    // 支持播放控制命令
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        Logger.i("TtsMediaSessionCallback::onCustomCommand: customCommand = ${customCommand.customAction}")
        return when (customCommand.customAction) {
            "stop" -> {
                service.stopTts()
                Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            else -> super.onCustomCommand(session, controller, customCommand, args)
        }
    }
}
