package com.wxn.reader.util.tts

object TtsChapterTransition {
    fun shouldLoadCurrentChapter(preloaded: Any?): Boolean = preloaded == null
}
