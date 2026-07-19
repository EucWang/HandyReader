package net.gotev.speech.engine

import android.content.Context
import android.content.res.AssetManager
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.getOfflineTtsConfig
import com.wxn.base.bean.EngineModelConfig
import com.wxn.base.bean.MODEL_TYPE_KITTEN
import com.wxn.base.bean.MODEL_TYPE_KOKORO
import com.wxn.base.bean.MODEL_TYPE_MATCHA_ICEFALL
import com.wxn.base.bean.MODEL_TYPE_VITS_PIPER
import com.wxn.base.bean.SpeakSentence
import com.wxn.base.util.BreakParagraphUtil
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.SherpaOnnxDeviceChecker
import com.wxn.base.util.launchIO
import com.wxn.base.util.numReplacer.NumberReplaceHelper
import com.wxn.base.util.toLocale
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.gotev.speech.OnShutdownListener
import net.gotev.speech.TextToSpeechCallback
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class SherpaOnnxEngine constructor(
    private val engineModelConfig: EngineModelConfig,
    private var mTtsRate: Float = 1.0f,
    private var sidInt: Int = 0,
) : TextToSpeechEngine {
    private var initListener: TextToSpeech.OnInitListener? = null

    private val scope = Coroutines.scope()

    private var context: Context? = null

    private var currentGenerationJob: Job? = null
    private val isShuttingDown = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)  // 新增：初始化状态

    private val audioTrackPlayer = AudioStreamPlayer()


    override fun initTextToSpeech(context: Context?) {
        Logger.i("SherpaOnnxEngine:initTextToSpeech:context=${context != null}")
        // 检查是否正在关闭
        if (context == null) {
            Logger.w("SherpaOnnxEngine:initTextToSpeech: context is null")
            initListener?.onInit(TextToSpeech.ERROR)
            return
        }
        if (isShuttingDown.get()) {
            Logger.w("SherpaOnnxEngine::initTextToSpeech: cannot init while shutting down")
            initListener?.onInit(TextToSpeech.ERROR)
            return
        }

        // 重置关闭标志，允许重新初始化
        isShuttingDown.set(false)

        this.context = context
        tts = initTts(context)
        tts?.sampleRate()?.let { sampleRate ->
            audioSampleRate = sampleRate
            audioTrackPlayer.initAudioTrack(sampleRate, initListener)
        }
    }

    override fun isSpeaking(): Boolean {
        return audioTrackPlayer.isPlaying() // track?.playState == PLAYSTATE_PLAYING
    }

    override fun say(sentence: SpeakSentence, ttsCallback: TextToSpeechCallback?) {
//        Logger.i("SherpaOnnxEngine:say:message=${sentence}")
        generateAndPlay(sentence, ttsCallback)
    }

    override fun stop() {
        Logger.i("SherpaOnnxEngine:stop")
        currentGenerationJob?.cancel()
        stopPlay()
    }

    override fun stopAndWait() {
        Logger.i("SherpaOnnxEngine:stop")
        currentGenerationJob?.cancel()
        stopPlay()
        runBlocking {
            waitForGenerateComplete()
        }
    }

    override fun shutdown(listener: OnShutdownListener?) {
        Logger.i("SherpaOnnxEngine::shutdown invoke.")

        if (isShuttingDown.getAndSet(true)) {
            Logger.w("SherpaOnnxEngine::shutdown: already shutting down")
            listener?.onDone()
            return  // 防止重复调用
        }
        currentGenerationJob?.cancel()
        // 2. 异步等待协程结束（不阻塞主线程）
        val shutdownJob = scope.launchIO {
            val job = currentGenerationJob
            if (job?.isActive == true) {
                val startTime = System.currentTimeMillis()
                val timeout = 500L

                // 非阻塞等待，定期检查
                while (job.isActive && System.currentTimeMillis() - startTime < timeout) {
                    delay(10)  // 使用delay而不是Thread.sleep
                }

                if (job.isActive) {
                    Logger.w("SherpaOnnxEngine::shutdown: job timeout after ${timeout}ms")
                }
            }

            // 3. 异步销毁AudioTrack
            audioTrackPlayer.destroy()

            if (tts != null) {
                tts = null
            }

            // 5. 清理状态
            currentGenerationJob = null
            isInitialized.set(false)

            listener?.onDone()
            Logger.i("SherpaOnnxEngine::shutdown completed")
        }
    }

    override fun setTextToSpeechQueueMode(mode: Int) {
        Logger.i("SherpaOnnxEngine:setTextToSpeechQueueMode")
        //nothing need to do
    }

    override fun setTextToSpeechSpeakerIndex(speakerIndex: Int) {
        val index = speakerIndex.coerceIn(0, engineModelConfig.speakerNum)
        Logger.i("SherpaOnnxEngine:setTextToSpeechSpeakerIndex:speakerIndex=$speakerIndex, coerceIn->index=$index")
        sidInt = index
    }

    override fun setAudioStream(audioStream: Int) { /*do nothing*/
        Logger.i("SherpaOnnxEngine:setAudioStream")
        //nothing
    }

    override fun setOnInitListener(onInitListener: TextToSpeech.OnInitListener?) {
        Logger.i("SherpaOnnxEngine:setOnInitListener")
        initListener = onInitListener
    }

    override fun setPitch(pitch: Float): Int { /*do nothing*/
        Logger.i("SherpaOnnxEngine:setPitch:pitch=$pitch")
        if (tts == null) {
            Logger.d("SherpaOnnxEngine:setPitch:tts is null")
            return TextToSpeech.ERROR
        }
        if (!audioTrackPlayer.isValid()) {
            Logger.d("SherpaOnnxEngine:setPitch:audioTrackPlayer is invalid")
            return TextToSpeech.ERROR
        }
        return TextToSpeech.SUCCESS
    }

    override fun setSpeechRate(rate: Float): Int {
        if (!audioTrackPlayer.isValid() || tts == null) {
            return TextToSpeech.ERROR
        }
//        stopPlay()
        this.mTtsRate = rate
        return TextToSpeech.SUCCESS
    }

    override fun setLocale(locale: Locale?): Int {
        return TextToSpeech.SUCCESS
    }

    override fun setVoice(voice: Voice?): Int {
        return TextToSpeech.SUCCESS
    }

    override fun getSupportedVoices(): List<Voice?> {
        return emptyList()
    }

    override fun getCurrentVoice(): Voice? {
        return null
    }

    override fun getAvailableLanguages(): Set<Locale?> {
        return emptySet()
    }

    override fun getConfig(): EngineModelConfig? {
        return this.engineModelConfig
    }

    //..............................................................................................

    private var tts: OfflineTts? = null

    private fun initTts(context: Context): OfflineTts? {
        Logger.i("SherpaOnnxEngine:initTts")
        var modelDir: String?
        var modelName: String?
        var acousticModelName: String?
        var vocoder: String?
        var voices: String?
        var ruleFsts: String?
        var ruleFars: String?
        var lexicon: String?
        var dataDir: String?
//        var assets: AssetManager? = context.assets
        var assets: AssetManager? = null
        var isKitten = false

        // The purpose of such a design is to make the CI test easier
        // Please see
        // https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/apk/generate-tts-apk-script.py

        // VITS -- begin
        modelName = null
        // VITS -- end

        // Matcha -- begin
        acousticModelName = null
        vocoder = null
        // Matcha -- end

        // For Kokoro -- begin
        voices = null
        // For Kokoro -- end

        modelDir = null
        ruleFsts = null
        ruleFars = null
        lexicon = null
        dataDir = null

        //---------------------------------------------------------------------------
        setTextToSpeechSpeakerIndex(engineModelConfig.speaker)
        val ret = parseBaseModelConfig(engineModelConfig)
        dataDir = ret.first
        vocoder = ret.second
        Logger.d("SherpaOnnxEngine:dataDir=$dataDir,vocoder=$vocoder")
        //---------------------------------------------------------------------------

        modelDir = engineModelConfig.modelDir
        val engineModelDir = File(modelDir)

        Logger.d("SherpaOnnxEngine:::modelDirName=$modelDir")

        if (engineModelConfig.modelType == MODEL_TYPE_VITS_PIPER) {
            val files = engineModelDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.name.endsWith(".onnx")) {
                        modelName = file.name
                        break
                    }
                }
            }
        } else if (engineModelConfig.modelType == MODEL_TYPE_MATCHA_ICEFALL) {
            val files = engineModelDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.name == "lexicon.txt") {
                        lexicon = file.name
                    } else
                        if (file.name == "model-steps-3.onnx") {
                            acousticModelName = file.name
                        }
                }
            }
        } else if (engineModelConfig.modelType == MODEL_TYPE_KITTEN) {
            val files = engineModelDir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.name.endsWith(".onnx")) {
                        modelName = file.name
                    } else if (file.name == "voices.bin") {
                        voices = file.name
                    }
                }
            }
            isKitten = true
        } else if (engineModelConfig.modelType == MODEL_TYPE_KOKORO) {
            val files = engineModelDir.listFiles()
            val lexicons = StringBuilder()
            val fsts = StringBuilder()
            if (files != null) {
                for (file in files) {
                    if (file.name.endsWith(".onnx")) {
                        modelName = file.name
                    } else if (file.name == "voices.bin") {
                        voices = file.name
                    } else if (file.name.startsWith("lexicon") && file.name.endsWith(".txt")) {
                        if (lexicons.isNotEmpty()) {
                            lexicons.append(",")
                        }
                        lexicons.append(file.absolutePath)
                    } else if (file.name.endsWith(".fst")) {
                        if (fsts.isNotEmpty()) {
                            fsts.append(",")
                        }
                        fsts.append(file.absolutePath)
                    }
                }
            }
            lexicon = lexicons.toString()
            ruleFsts = fsts.toString()
        }

        if (!SherpaOnnxDeviceChecker.isDeviceSupported(context)) {
            Logger.e("SherpaOnnxEngine:initTts:device not supported for SherpaOnnx")
            initListener?.onInit(TextToSpeech.ERROR)
            return null
        }

        System.gc()

        try {
            val numThreads = if (SherpaOnnxDeviceChecker.isLowEndDevice()) 1 else null
            val config = getOfflineTtsConfig(
                modelDir = modelDir,
                modelName = modelName ?: "",
                acousticModelName = acousticModelName ?: "",
                vocoder = vocoder ?: "",
                voices = voices ?: "",
                lexicon = lexicon ?: "",
                dataDir = dataDir ?: "",
                dictDir = "",
                ruleFsts = ruleFsts ?: "",
                ruleFars = ruleFars ?: "",
                numThreads = numThreads,
                isKitten = isKitten,
            )

            return OfflineTts(assetManager = assets, config = config)

        } catch (ex: Exception) {
            initListener?.onInit(TextToSpeech.ERROR)
            Logger.e("SherpaOnnxEngine:initTts:ex=$ex")
        }
        return null
    }

    /***
     * 产出音频文件
     * 耗时操作
     */
    private fun generateAndPlay(sentence: SpeakSentence, ttsCallback: TextToSpeechCallback?) {
        val utteranceId = sentence.locator.toUtteranceId()
        //移除句末的标点, 防止TTS产生奇怪的噪音
        val textStr = NumberReplaceHelper.replace(BreakParagraphUtil.normalizeForTts(sentence.sentence), config?.language?.toLocale() ?: Locale.getDefault())
        Logger.i("SherpaOnnxEngine:generate::textStr=[$textStr], locator=${sentence.locator}")

        // 在启动前检查状态
        if (isShuttingDown.get()) {
            Logger.d("SherpaOnnxEngine::generateAndPlay: shutdown in progress, ignoring")
            ttsCallback?.onError(utteranceId,  PlayErrorCode.PlayErrorShuttingDown)
            return
        }
        if (tts == null) {
            ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorTtsIsNull)
            return
        }
        if (!audioTrackPlayer.isValid()) {
            ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorAudioTrackInvalid)
            return
        }

        val localTts = tts ?: return

        if (sidInt < 0) {
            ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorSpeakerIdInvalid)
            return
        }

        val speedFloat = mTtsRate
        if (speedFloat <= 0) {
            ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorSpeedInvalid)
            return
        }

        Logger.i("SherpaOnnxEngine:generate::start play")

        if (!audioTrackPlayer.prepare()) {
            ttsCallback?.onError(utteranceId, PlayErrorCode.PlayerErrorAudioTrackPrepareFail)
            return
        }

        currentGenerationJob = scope.launchIO {
            //block and run to callback()

            if (isShuttingDown.get() || currentGenerationJob?.isCancelled == true) {
                Logger.d("SherpaOnnxEngine:generate: cancelled during generation")
                audioTrackPlayer.stop()
                ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorStop)
                return@launchIO
            }

            var millis = 10L
            var totalWaiting = 0L
            while (!audioTrackPlayer.canReceive()) {  //生成太快了, 音频还没来得及播放,全部堆在缓冲区中,需要等待
                //等待时,防止任务被中断
                if (!audioTrackPlayer.isValid() || isShuttingDown.get() || currentGenerationJob?.isCancelled == true) {
                    Logger.d("SherpaOnnxEngine:generate: cancelled during generation")
                    audioTrackPlayer.stop()
                    ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorStop)
                    return@launchIO
                }

                totalWaiting += millis
                delay(millis)
                millis += 10
                millis.coerceAtMost(50)
            }
            Logger.d("SherpaOnnxEngine::generate::Waiting=$totalWaiting")

            val start = System.currentTimeMillis()
            Logger.d("SherpaOnnxEngine::generate:generateWithConfigAndCallback start")
            ttsCallback?.let {
                audioTrackPlayer.setPlaySentenceCallback(utteranceId, it)
            }

            if (isShuttingDown.get() || !isActive) {
                Logger.d("SherpaOnnxEngine::callback: shutting down or cancelled")
                audioTrackPlayer.stop()
                ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorStop)
                return@launchIO
            }

            if (textStr.isBlank() || textStr.isEmpty()) {
                val silence = FloatArray(audioSampleRate) // 默认值全为 0.0f = 静音
                audioTrackPlayer.onStreaming(utteranceId, silence)
            } else {
                val audio = try {
                    localTts.generateWithConfig(
                        text = textStr,
                        config = GenerationConfig(
                            sid = sidInt,
                            speed = speedFloat
                        ))
                } catch (ex: Exception) {
                    Logger.e("SherpaOnnxEngine:generate::ex=$ex")
                    ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorAudioGenerateFail)
                    null
                }

                Logger.d("SherpaOnnxEngine::generateAndPlay[${engineModelConfig.engineModel}]:generate done. spend[${System.currentTimeMillis() - start}]")

                val samplesArray = audio?.samples
                val totalSamples = samplesArray?.size ?: 0
                audioSampleRate = audio?.sampleRate ?: 0

                val ok = samplesArray != null && samplesArray.isNotEmpty() && totalSamples > 0 && audioSampleRate > 0
                if (!ok) { //音频生产成功
                    ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorAudioGenerateFail)
                    return@launchIO
                } else {

                    if (isShuttingDown.get() || !isActive) {
                        Logger.d("SherpaOnnxEngine::callback: shutting down or cancelled")
                        audioTrackPlayer.stop()
                        ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorStop)
                        return@launchIO
                    }

                    if (engineModelConfig.engineModel == "nano-en-v0_2-fp16") {
                        // 对音频尾部采样做 fade-out，消除末尾 click 噪声, 20ms
                        val fadeLen = (audioSampleRate * 0.02).toInt().coerceAtMost(totalSamples) // 20ms
                        for (i in 0 until fadeLen) {
                            samplesArray[totalSamples - fadeLen + i] *= 1f - i.toFloat() / fadeLen
                        }
                    }
                    audioTrackPlayer.onStreaming(utteranceId, samplesArray)
                }
            }
            if (isShuttingDown.get() || !isActive) {
                Logger.d("SherpaOnnxEngine::callback: shutting down or cancelled")
                ttsCallback?.onError(utteranceId, PlayErrorCode.PlayErrorStop)
                return@launchIO
            }
            ttsCallback?.onPrepare(utteranceId)
        }
        return
    }

    private var  audioSampleRate = 0
//    private @Volatile
//    var generatingUtteranceId: String = ""

    // this function is called from C++
//    private fun callback(samples: FloatArray): Int {
//        Logger.d("SherpaOnnxEngine::callback invoke: samples.size=${samples.size}, generatingUtteranceId=${generatingUtteranceId}")
//        return try {
//            // 合并检查，性能更好
//            if (isShuttingDown.get() || currentGenerationJob?.isCancelled == true) {
//                Logger.d("SherpaOnnxEngine::callback: shutting down or cancelled")
//                0
//            } else {
//
//                if (engineModelConfig.engineModel == "nano-en-v0_2-fp16") {
//                // 对音频尾部采样做 fade-out，消除末尾 click 噪声, 20ms
//                    val fadeLen = (audioSampleRate * 0.02).toInt().coerceAtMost(samples.size) // 20ms
//                    for (i in 0 until fadeLen) {
//                        samples[samples.size - fadeLen + i] *= 1f - i.toFloat() / fadeLen
//                    }
//                }
//
//                audioTrackPlayer.onStreaming(generatingUtteranceId, samples)
//            }
//        } catch (ex: Exception) {
//            Logger.w("SherpaOnnxEngine::callback: $ex")
//            0
//        }
//    }

    private fun stopPlay() {
        Logger.i("SherpaOnnxEngine::stopPlay")
        audioTrackPlayer.stop()
    }

    suspend fun waitForGenerateComplete(timeoutMs: Long = 500L) {
        val job = currentGenerationJob ?: return
        if (job.isCompleted) return
        try {
            withTimeout(timeoutMs) {
                job.join()
            }
        } catch (e: TimeoutCancellationException) {
            Logger.w("SherpaOnnxEngine::waitForGenerationComplete: timeout after ${timeoutMs}ms")
        }
    }

    private fun parseBaseModelConfig(engineModelConfig: EngineModelConfig): Pair<String, String> {
        val baseDatas = engineModelConfig.baseDatas
        if (baseDatas.isEmpty()) {
            return "" to ""
        }
        var espeakPath = ""
        var vocosPath = ""

        for (baseData in baseDatas) {
            if (baseData.first == "espeak-ng-data") {
                espeakPath = baseData.third
            } else if (baseData.first == "vocos-22khz-univ" ||
                baseData.first == "vocos-16khz-univ"
            ) {
                vocosPath = baseData.third
            }
        }
        return espeakPath to vocosPath
    }
}