package com.wxn.reader.util.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsChapterTransitionTest {

    @Test
    fun `loads a narration chapter when prefetch did not provide it`() {
        assertTrue(TtsChapterTransition.shouldLoadCurrentChapter(null))
    }

    @Test
    fun `uses a prefetched narration chapter without loading it again`() {
        assertFalse(TtsChapterTransition.shouldLoadCurrentChapter(Any()))
    }
}
