package com.wxn.reader.util.tts.media

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class TtsNarrationPlayerTest {

    private val textFormatter = TtsMediaTextFormatter { state ->
        "${(state.progression * 100).toInt()}% · ~remaining"
    }

    @Test
    fun `maps system play and stop to narration commands without exposing seek`() {
        val coordinator = TtsPlaybackCoordinator()
        val commands = mutableListOf<TtsMediaCommand>()
        coordinator.register(
            ownerId = "reader",
            handlers = TtsPlaybackHandlers(
                onPlay = { commands += TtsMediaCommand.Play },
                onPause = { commands += TtsMediaCommand.Pause },
                onStop = { commands += TtsMediaCommand.Stop },
            ),
        )
        coordinator.publish(
            ownerId = "reader",
            state = TtsMediaState(
                bookId = 1L,
                bookTitle = "Book",
                playbackState = TtsMediaPlaybackState.Paused,
            ),
        )
        val player = TtsNarrationPlayer(coordinator, textFormatter)

        player.setPlayWhenReady(true)
        player.stop()

        assertEquals(listOf(TtsMediaCommand.Play, TtsMediaCommand.Stop), commands)
        assertTrue(player.availableCommands.contains(Player.COMMAND_GET_TIMELINE))
        assertTrue(player.availableCommands.contains(Player.COMMAND_GET_CURRENT_MEDIA_ITEM))
        assertTrue(player.availableCommands.contains(Player.COMMAND_GET_METADATA))
        assertFalse(player.availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        player.release()
    }

    @Test
    fun `maps system pause to the narration pause command`() {
        val coordinator = TtsPlaybackCoordinator()
        val commands = mutableListOf<TtsMediaCommand>()
        coordinator.register(
            ownerId = "reader",
            handlers = TtsPlaybackHandlers(
                onPlay = {},
                onPause = { commands += TtsMediaCommand.Pause },
                onStop = {},
            ),
        )
        coordinator.publish(
            ownerId = "reader",
            state = TtsMediaState(
                bookId = 1L,
                bookTitle = "Book",
                playbackState = TtsMediaPlaybackState.Playing,
            ),
        )
        val player = TtsNarrationPlayer(coordinator, textFormatter)

        player.setPlayWhenReady(false)

        assertEquals(listOf(TtsMediaCommand.Pause), commands)
        player.release()
    }

    @Test
    fun `keeps media player idle until a book is published`() {
        val coordinator = TtsPlaybackCoordinator()
        coordinator.register(
            ownerId = "reader",
            handlers = TtsPlaybackHandlers(
                onPlay = {},
                onPause = {},
                onStop = {},
            ),
        )
        coordinator.publish(
            ownerId = "reader",
            state = TtsMediaState(playbackState = TtsMediaPlaybackState.Playing),
        )
        val player = TtsNarrationPlayer(coordinator, textFormatter)

        assertEquals(Player.STATE_IDLE, player.playbackState)

        player.release()
    }

    @Test
    fun `reports the published chapter and does not advance factual progress by clock time`() {
        val coordinator = TtsPlaybackCoordinator()
        coordinator.register(
            ownerId = "reader",
            handlers = TtsPlaybackHandlers(
                onPlay = {},
                onPause = {},
                onStop = {},
            ),
        )
        coordinator.publish(
            ownerId = "reader",
            state = TtsMediaState(
                bookId = 1L,
                bookTitle = "Book",
                chapterTitle = "Chapter 3",
                progression = 0.42,
                estimatedDurationMs = 100_000L,
                estimatedPositionMs = 42_000L,
                estimatedRemainingMs = 58_000L,
                playbackState = TtsMediaPlaybackState.Playing,
            ),
        )
        val player = TtsNarrationPlayer(coordinator, textFormatter)

        assertTrue(player.mediaMetadata.artist.toString().contains("Chapter 3"))
        assertTrue(player.mediaMetadata.artist.toString().contains("42%"))
        assertEquals(42_000L, player.currentPosition)
        ShadowSystemClock.advanceBy(30, TimeUnit.SECONDS)
        assertEquals(42_000L, player.currentPosition)

        player.release()
    }
}
