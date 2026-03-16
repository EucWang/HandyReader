package com.wxn.reader.util.tts

import android.content.Context
import com.wxn.bookread.data.model.SpeakSentence
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextPage
import java.util.Locale


interface ITtsService {

    fun play(context: Context)

    fun pause(context: Context)

    fun resume(context: Context)

    fun stop(context: Context)

    fun skipToPreviousUtterance(context: Context): Boolean

    fun skipToNextUtterance(context: Context): Boolean

    fun setSpeakStartChapterAndPage(context: Context,
                                    chapter: TextChapter?,
                                    page: TextPage?,
                                    bookTitle: String = "",
                                    chapterTitle: String = "",
                                    bookCover: String? = null,
                                    chapterSize: Int) : Boolean

    fun setSpeed(context: Context, speed: Float)
    fun setSpeed(context: Context, speed: Float, onComplete: (Boolean)->Unit)

    fun setPitch(context: Context, pitch: Float)
    fun setPitch(context: Context, pitch: Float, onComplete: (Boolean) -> Unit)

//    fun getSupportedLanguage(context: Context, callback: (Set<Locale>)->Unit)
    fun getSupportedLanguage(context: Context) : Set<Locale>

    fun setLanguage(context: Context, newlocale: Locale): Boolean
    fun setLanguage(context: Context, newlocale: Locale, onComplete: (Boolean) -> Unit)

    fun isServiceRunning(context: Context): Boolean

    fun setPlayTime(context: Context, playTime: Float)
}

interface ITtsNavigator {

    fun skipToPreviousUtterance(): Boolean

    fun skipToNextUtterance(): Boolean

    fun pause()

    fun resume()

    fun stop()

    fun setSpeakSentences(sentences: List<SpeakSentence>, startSentenceIndex: Int = 0)

    fun setSpeakCallback( callback: TtsNavigator.SuspendSpeakCallback?)

    fun play()

    fun setSpeed( speed: Float)

    fun setPitch( pitch: Float)

    fun getSupportedLanguage() : Set<Locale>

    fun setLanguage(newlocale: Locale): Boolean
}