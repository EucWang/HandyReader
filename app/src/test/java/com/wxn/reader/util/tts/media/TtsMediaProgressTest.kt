package com.wxn.reader.util.tts.media

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsMediaProgressTest {

    @Test
    fun `estimates thirty seconds remaining halfway through one thousand characters`() {
        val estimate = TtsMediaProgress.estimate(
            totalCharacters = 1_000,
            progression = 0.5,
            speechRate = 1f,
        )

        assertEquals(30_000L, estimate.estimatedRemainingMs)
        assertEquals(30_000L, estimate.estimatedPositionMs)
        assertEquals(60_000L, estimate.estimatedDurationMs)
    }

    @Test
    fun `clamps progress beyond the end of a book`() {
        val estimate = TtsMediaProgress.estimate(
            totalCharacters = 1_000,
            progression = 1.5,
            speechRate = 1f,
        )

        assertEquals(1.0, estimate.progression, 0.0)
        assertEquals(0L, estimate.estimatedRemainingMs)
    }
}
