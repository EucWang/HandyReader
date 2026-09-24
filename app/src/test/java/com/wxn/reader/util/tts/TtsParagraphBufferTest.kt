package com.wxn.reader.util.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.coroutines.runBlocking

class TtsParagraphBufferTest {

    @Test
    fun `keeps three paragraphs queued and adds one after each completion`() {
        val buffer = TtsParagraphBuffer(listOf("one", "two", "three", "four", "five"), 3)

        assertEquals(listOf("one", "two", "three"), buffer.initial())
        assertEquals("four", buffer.nextAfterCompletion())
        assertEquals("five", buffer.nextAfterCompletion())
        assertNull(buffer.nextAfterCompletion())
    }

    @Test
    fun `limits the requested buffer to the supported three to seven range`() {
        assertEquals(
            listOf("one", "two", "three"),
            TtsParagraphBuffer(listOf("one", "two", "three", "four"), 1).initial(),
        )
        assertEquals(
            listOf("one", "two", "three", "four"),
            TtsParagraphBuffer(listOf("one", "two", "three", "four"), 99).initial(),
        )
    }

    @Test
    fun `cancelling a playback session unblocks a waiting reader`() = runBlocking {
        val session = TtsPlaybackSession<String>()

        session.completed("already finished", succeeded = true)
        session.cancel()

        assertNull(session.nextEvent())
    }

    @Test
    fun `delivers a paragraph start before its completion`() = runBlocking {
        val session = TtsPlaybackSession<String>()

        session.started("paragraph")
        session.completed("paragraph", succeeded = true)

        assertEquals(TtsPlaybackEvent.Started("paragraph"), session.nextEvent())
        assertEquals(TtsPlaybackEvent.Completed("paragraph", succeeded = true), session.nextEvent())
    }

    @Test
    fun `includes paragraphs from following pages in the playback window`() {
        val pages = TtsPageBuffer(
            pages = listOf(listOf("one", "two"), listOf("three"), listOf("four", "five")),
            startPageIndex = 1,
        )

        assertEquals(listOf("three", "four", "five"), pages.items())
        assertEquals(listOf(listOf("three"), listOf("four", "five")), pages.pages())
        assertEquals(2, pages.pageIndexFor(listOf("five")))
    }
}
