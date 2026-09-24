package com.wxn.reader.util.tts.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.wxn.reader.MainActivity
import com.wxn.reader.R
import com.wxn.base.util.Logger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TtsMediaSessionService : MediaSessionService() {
    @Inject
    lateinit var playbackCoordinator: TtsPlaybackCoordinator

    private var narrationPlayer: TtsNarrationPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            setMediaNotificationProvider(createNotificationProvider())
            val player = TtsNarrationPlayer(
                playbackCoordinator = playbackCoordinator,
                textFormatter = AndroidTtsMediaTextFormatter(this),
            )
            val session = MediaSession.Builder(this, player)
                .setSessionActivity(createSessionActivity())
                .setMediaButtonPreferences(listOf(stopButton()))
                .setCallback(SessionCallback())
                .build()
            narrationPlayer = player
            mediaSession = session
            addSession(session)
        }.onFailure { error ->
            Logger.e("TtsMediaSessionService::onCreate failed: $error")
            mediaSession?.release()
            mediaSession = null
            narrationPlayer?.release()
            narrationPlayer = null
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (playbackCoordinator.state.value.bookId == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        mediaSession?.let { session ->
            if (isSessionAdded(session)) {
                removeSession(session)
            }
            session.release()
        }
        mediaSession = null
        narrationPlayer?.release()
        narrationPlayer = null
        super.onDestroy()
    }

    private fun createSessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_READER,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stopButton(): CommandButton {
        return CommandButton.Builder(CommandButton.ICON_STOP)
            .setSessionCommand(STOP_COMMAND)
            .setDisplayName(getString(R.string.tts_media_stop))
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()
    }

    private fun createNotificationProvider(): DefaultMediaNotificationProvider {
        return object : DefaultMediaNotificationProvider(this) {
            override fun addNotificationActions(
                mediaSession: MediaSession,
                mediaButtons: ImmutableList<CommandButton>,
                builder: NotificationCompat.Builder,
                actionFactory: MediaNotification.ActionFactory,
            ): IntArray {
                super.addNotificationActions(mediaSession, mediaButtons, builder, actionFactory)
                return (0 until mediaButtons.size)
                    .filter { index ->
                        val button = mediaButtons[index]
                        button.playerCommand == Player.COMMAND_PLAY_PAUSE ||
                            button.sessionCommand == STOP_COMMAND
                    }
                    .toIntArray()
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(STOP_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(session.player.availableCommands)
                .setMediaButtonPreferences(listOf(stopButton()))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand == STOP_COMMAND) {
                playbackCoordinator.dispatch(TtsMediaCommand.Stop)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    private companion object {
        const val REQUEST_CODE_OPEN_READER = 104
        const val STOP_ACTION = "com.wxn.reader.tts.media.STOP"
        val STOP_COMMAND = SessionCommand(STOP_ACTION, Bundle.EMPTY)
    }
}
