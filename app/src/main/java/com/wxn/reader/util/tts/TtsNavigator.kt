package com.wxn.reader.util.tts

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import com.wxn.base.bean.Locator
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.bookread.data.model.SpeakSentence
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.reader.util.LanguageUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.gotev.speech.Speech
import net.gotev.speech.TextToSpeechCallback
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class TtsNavigator(
    val context: Context,
    val ttsPreferencesUtil: TtsPreferencesUtil
) : ITtsNavigator {

    companion object {
        const val TTS_MIN_SPEED = 0.25f
        const val TTS_MAX_SPEED = 4f
        const val TTS_MIN_PITCH = 0.25f
        const val TTS_MAX_PITCH = 3f

        const val TTS_PLAY_MIN_TIMES = 0.0f
        const val TTS_PLAY_MAX_TIMES = 2.0f

        const val STATUS_NORMAL_FINISH = 0
        const val STATUS_NEXT_CHAPTER_REQUESTED = 1
        const val STATUS_ERROR = -1
    }

    enum class TtsSpeakStatus{
        Tts_Status_Playing,
        Tts_Status_Pause,
        Tts_Status_Stop
    }

    interface SuspendSpeakCallback {
        suspend fun onSpeakSentence(locator: Locator, sentenceIndex: Int): Boolean
        suspend fun onSpeakNextChapter(nextChapterIndex: Int):Boolean
        suspend fun onFinished(status: Int)
    }

    var status = TtsSpeakStatus.Tts_Status_Stop
    private var ttsLocale : Locale = LanguageUtil.LANG_EN.locale
    private var speed = 1.0f
    private var pitch = 1.0f
    private val curSentences = mutableListOf<SpeakSentence>()
    @Volatile
    private var playSentenceIndex: Int = 0
    private var callback : SuspendSpeakCallback? = null
    private val scope = Coroutines.mainScope() // 播放队列：无限制通道，外部向其中发送句子
    private val sentenceChannel = Channel<SpeakSentence>(UNLIMITED)
    private var playJob : Job? = null
    init {
        Speech.init(context)
    }

    override fun skipToPreviousUtterance(): Boolean {
        if (!Speech.getInstance().isSpeaking) {
            return false
        }
        val curIndex = playSentenceIndex
        if (curIndex <= 0) return false
        playSentenceIndex = curIndex - 1
        restartChannel()
        return true
    }

    override  fun skipToNextUtterance(): Boolean {
        if (!Speech.getInstance().isSpeaking) {
            return false
        }
        val lastIndex = curSentences.size - 1
        val curIndex = playSentenceIndex
        if (curIndex >= lastIndex) return false
        playSentenceIndex = curIndex + 1
        restartChannel()
        return true
    }

    override fun setSpeakSentences(sentences: List<SpeakSentence>, startSentenceIndex: Int) {
        Logger.i("TtsNavigator::setSpeakSentences:sentences.size=${sentences.size},startSentenceIndex=$startSentenceIndex")
        curSentences.clear()
        curSentences.addAll(sentences)
        playSentenceIndex = startSentenceIndex
        restartChannel(false)
    }

    override fun setSpeakCallback(callback: SuspendSpeakCallback?) {
        this.callback = callback
    }

    override  fun play() {
        Logger.i("TtsNavigator:play")

        playJob?.cancel()
        playJob = scope.launch {
            if (curSentences.isEmpty() && playSentenceIndex < 0 && playSentenceIndex >= curSentences.size) {
                callback?.onFinished(STATUS_ERROR)
                Logger.d("TtsNavigator:play:sentences is empty or playSentenceIndex overflow[$playSentenceIndex][${curSentences.size}]")
                return@launch
            }

            // 标记是否请求了下一章
            var chapterRequested = false
            Speech.getInstance().setTextToSpeechQueueMode(TextToSpeech.QUEUE_ADD)
            Speech.getInstance().setAudioStream(AudioManager.STREAM_MUSIC)

            for (sentence in sentenceChannel) {
                Logger.d("TtsNavigator::play:get sentence[$sentence], isActive=$isActive")
                // 检查是否被取消
                if (!isActive) break

                // 回调通知开始播放此句
                if (true != callback?.onSpeakSentence(sentence.locator, playSentenceIndex)) {
                    Logger.d("TtsNavigator::play:onSpeakSentence return false, over the time limit.")
                    // 超过播放时长限制,正常退出
                    callback?.onFinished(STATUS_NORMAL_FINISH)
                    break
                }
                // 播放句子（挂起直到播放完成）
                val result = innerPlay(sentence.sentence)

                Logger.d("TtsNavigator::play:play result=[$result],check agian:isActive=$isActive")
                if (!isActive) break // 播放过程中可能被取消（如暂停）

                when (result) {
                    1 -> {
                        // 播放成功，索引自增
                        playSentenceIndex++
                        chapterRequested = false

                        // 如果已到列表末尾，请求下一章
                        if (playSentenceIndex >= curSentences.size) {
                            val nextChapterIndex = sentence.locator.chapterIndex + 1
                            val shouldContinue = callback?.onSpeakNextChapter(nextChapterIndex) == true
                            if (!shouldContinue) {
                                // 没有下一章，正常结束
                                callback?.onFinished(STATUS_NORMAL_FINISH)
                                break
                            } else {
                                chapterRequested = true
                                // 等待外部调用 setSpeakSentences 追加新句子
                                // 此时通道为空，for 循环会挂起，直到新句子到来
                            }
                        }
                    }
                    else -> {
                        // 播放出错
                        callback?.onFinished(STATUS_ERROR)
                        break
                    }
                }
            }
        }
    }

    private suspend fun innerPlay(text: String) : Int {
        Logger.d("TtsNavigator::innerPlay:text[$text]")
        var start = System.currentTimeMillis()
        return suspendCoroutine { coroutination ->
            Speech.getInstance().say(text,
                object : TextToSpeechCallback{
                    override fun onStart() {
                        Logger.d("TtsNavigator::innerPlay say callback onStart")
                        start = System.currentTimeMillis()
                        status = TtsSpeakStatus.Tts_Status_Playing
                    }

                    override fun onCompleted() {
                        val duration = System.currentTimeMillis() - start
                        Logger.d("TtsNavigator::innerPlay say callback onCompleted duration[${duration}ms]")
                        coroutination.resume(1)
                    }

                    override fun onError() {
                        val duration = System.currentTimeMillis() - start
                        Logger.d("TtsNavigator::innerPlay say callback onError duration[${duration}ms]")
                        coroutination.resume(-1)
                    }
                })
        }
    }

    override  fun setSpeed(speed: Float) {
        Logger.i("TtsNavigator::setSpeed:speed=$speed, oldSpeed=${this.speed}")
        val speechSpeed = speed.coerceIn(TTS_MIN_SPEED, TTS_MAX_SPEED)
        if (speechSpeed != this.speed) {
            if (Speech.getInstance().setTextToSpeechRate(speechSpeed) == TextToSpeech.SUCCESS) {
                this.speed = speechSpeed
                restartChannel()
                scope.launch {
                    ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()?.let { preferences ->
                        preferences.speed = speechSpeed
                        ttsPreferencesUtil.updatePreferences(preferences)
                    }
                }
            } else {
                Logger.e("TtsNavigator::setSpeed:set speed failed.")
            }
        }
    }

    private fun restartChannel(needResume: Boolean = true) {
        val isPlaying = isPlaying()
        scope.launch {
            if (isPlaying && needResume) {
                pause()
            }
            while (sentenceChannel.tryReceive().isSuccess) { }
            val size = curSentences.size
            if (size > 0 && playSentenceIndex in 0 until size) {
                curSentences.drop(playSentenceIndex).forEach { sentenceChannel.trySend(it) }
            }
            if (isPlaying && needResume) {
                resume()
            }
        }
    }

    override  fun setPitch(pitch: Float) {
        Logger.i("TtsNavigator:setLanguage:pitch[$pitch,ttsPitch[${this.pitch}]]")

        val speechPitch = pitch.coerceIn(TTS_MIN_PITCH, TTS_MAX_PITCH)
        if (speechPitch != this.pitch) {
            if (Speech.getInstance().setTextToSpeechPitch(speechPitch) == TextToSpeech.SUCCESS) {
                this.pitch = speechPitch
                restartChannel()
                scope.launch {
                    ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()?.let { preferences ->
                        preferences.pitch = speechPitch
                        ttsPreferencesUtil.updatePreferences(preferences)
                    }
                }
            } else{
                Logger.e("TtsNavigator::setPitch:set pitch failed.")
            }
        }
    }

    override  fun getSupportedLanguage() : Set<Locale> {
        val locales = Speech.getInstance().supportedTtsLanguages
        val retLocales = hashSetOf<Locale>()
        for (locale in locales) {
            retLocales.add(Locale.forLanguageTag(locale.language))
            Logger.d("TtsNavigator::getSupportedLanguage:language=${locale}, ${locale.getDisplayName(locale)}")
        }
        return retLocales
    }

    override  fun setLanguage(newlocale: Locale): Boolean {
        Logger.i("TtsNavigator:setLanguage:newLocale[${newlocale.language},ttsLocale[${ttsLocale.language}]]")
        if (newlocale != this.ttsLocale) {
            val status = Speech.getInstance().setLocale(newlocale)
            if (status < 0) {
                Logger.e("TtsNavigator::setLanguage::language not support[${
                    when(status) {
                        -1 -> "LANG_MISSING_DATA"
                        -2 -> "LANG_NOT_SUPPORTED"
                        else -> "OTHER ISSUE"
                    }
                }]")
                return false
            }

            this.ttsLocale = newlocale
            restartChannel()

            Logger.d("TtsNavigator::setLanguage::newlocale[$newlocale], supportLanguage[$status]")
            scope.launch {
                ttsPreferencesUtil.ttsPreferencesFlow.firstOrNull()?.let { preferences ->
                    preferences.localeCode = newlocale.language
                    ttsPreferencesUtil.updatePreferences(preferences)
                }
            }
        }
        return true
    }

    override  fun pause() {
        Logger.i("TtsNavigator::pause")
        playJob?.cancel()
        playJob = null
        Speech.getInstance().stopTextToSpeech()
        status = TtsSpeakStatus.Tts_Status_Pause
    }

    override  fun resume() {
        Logger.i("TtsNavigator::resume")
        play()
    }

    override  fun stop() {
        Logger.i("TtsNavigator::stop")
        playJob?.cancel()
        playJob = null
        Speech.getInstance().stopTextToSpeech()
        status = TtsSpeakStatus.Tts_Status_Pause
        callback = null
        // 清空通道
        while (sentenceChannel.tryReceive().isSuccess) { }

        curSentences.clear()
        playSentenceIndex = 0
        status = TtsSpeakStatus.Tts_Status_Stop
    }

    fun isPlaying() : Boolean {
        val isPlaying = Speech.getInstance().isSpeaking &&  playJob?.isActive == true
        Logger.i("TtsNavigator::isPlaying[$isPlaying]")
        return isPlaying
    }
}