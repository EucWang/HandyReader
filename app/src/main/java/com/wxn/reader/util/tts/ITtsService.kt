package com.wxn.reader.util.tts

import android.content.Context
import com.wxn.base.bean.EngineModelConfig
import com.wxn.base.bean.SpeakSentence
import com.wxn.base.bean.TtsConfig
import com.wxn.bookread.data.model.TextChapter
import com.wxn.bookread.data.model.TextPage
import com.wxn.reader.service.TtsEngineStatus
import net.gotev.speech.OnShutdownListener
import java.util.Locale


interface ITtsService {

    fun pause(context: Context)

    fun resume(context: Context)

    fun stop(context: Context)

    fun skipToPreviousUtterance(context: Context)

    fun skipToNextUtterance(context: Context)

    suspend fun setSpeakStartChapterAndPage(context: Context,
                                    chapter: TextChapter?,
                                    page: TextPage?,
                                    bookTitle: String = "",
                                    chapterTitle: String = "",
                                    bookCover: String? = null,
                                    bookUri: String = "",
                                    chapterSize: Int) : Boolean

    suspend fun setSpeakConfigsAndPlay(context: Context,
                                    chapter: TextChapter?,
                                    page: TextPage?,
                                    bookTitle: String = "",
                                    chapterTitle: String = "",
                                    bookCover: String? = null,
                                    bookUri: String = "",
                                    chapterSize: Int,
                                    ttsConfig: TtsConfig
                               ) : Boolean


    fun setSpeed(context: Context, speed: Float)

    fun setPitch(context: Context, pitch: Float)

    fun setLanguage(context: Context, newlocale: Locale)

    fun isServiceRunning(context: Context): Boolean

    fun getRunningModel(context: Context): String

    fun setPlayTime(context: Context, playTime: Float)

//    fun setTtsConfig(context: Context, ttsConfig: TtsConfig)

    fun setSpeakerIndex(context: Context, speakerIndex: Int)
}

interface ITtsNavigator {

    fun skipToPreviousUtterance(): Boolean

    fun skipToNextUtterance(): Boolean

    fun pause()

    fun resume()

    fun stop()

    fun shutdown(listener: OnShutdownListener? = null)

    fun setSpeakSentences(sentences: List<SpeakSentence>, startSentenceIndex: Int = 0)

    fun setSpeakCallback( callback: TtsNavigator.SuspendSpeakCallback?)

    fun play()

    fun setSpeed( speed: Float)

    fun setPitch( pitch: Float)

    fun setLanguage(newlocale: Locale , onLanguageChanged: (Boolean)->Unit)

    fun getSupportedLanguage(onDataCollect: (Set<Locale>)->Unit)

    fun setEngineInfo(engineType: Int,
                      modelConfig: EngineModelConfig?,
                      speed: Float,
                      pitch: Float,
                      language: Locale,
                      speakerIndex: Int,
                      onInit: (TtsEngineStatus)->Unit)

    fun setSpeakerIndex(index: Int)

}