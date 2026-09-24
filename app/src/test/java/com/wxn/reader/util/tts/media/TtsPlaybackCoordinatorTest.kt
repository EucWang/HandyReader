package com.wxn.reader.util.tts.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPlaybackCoordinatorTest {

    @Test
    fun `dispatches a play command to the registered reader`() {
        val coordinator = TtsPlaybackCoordinator()
        var played = false

        coordinator.register(
            ownerId = "reader",
            handlers = TtsPlaybackHandlers(
                onPlay = { played = true },
                onPause = {},
                onStop = {},
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
        coordinator.dispatch(TtsMediaCommand.Play)

        assertTrue(played)
    }

    @Test
    fun `unregistering the active reader clears media state and rejects stale commands`() {
        val coordinator = TtsPlaybackCoordinator()
        var played = false

        coordinator.register(
            ownerId = "reader",
            handlers = TtsPlaybackHandlers(
                onPlay = { played = true },
                onPause = {},
                onStop = {},
            ),
        )
        coordinator.publish(
            ownerId = "reader",
            state = TtsMediaState(playbackState = TtsMediaPlaybackState.Playing),
        )
        coordinator.unregister("reader")
        coordinator.dispatch(TtsMediaCommand.Play)

        assertFalse(played)
        assertEquals(TtsMediaPlaybackState.Idle, coordinator.state.value.playbackState)
    }

    @Test
    fun `terminal state rejects a late play command while the reader remains registered`() {
        val coordinator = TtsPlaybackCoordinator()
        var played = false

        coordinator.register(
            ownerId = "reader",
            handlers = TtsPlaybackHandlers(
                onPlay = { played = true },
                onPause = {},
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
        coordinator.publish(ownerId = "reader", state = TtsMediaState())

        coordinator.dispatch(TtsMediaCommand.Play)

        assertFalse(played)
        assertEquals(TtsMediaPlaybackState.Idle, coordinator.state.value.playbackState)
    }
}
