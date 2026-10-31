package net.gotev.speech.engine

import android.content.Context
import android.os.DeadObjectException
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * BaseTextToSpeechEngine.onTtsInit 的防御逻辑单测（方案：2026-09-11-plan-tts-init-default-language-npe-fix）。
 *
 * 运行于 Robolectric sdk=28（与崩溃设备 Android 9 对齐），保证 :87 的 SDK_INT >= LOLLIPOP 分支可达。
 * TextToSpeech 通过反射注入 mock，生产代码不新增测试 seam。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@Suppress("DEPRECATION") // getDefaultLanguage 是被测缺陷 API，本身已 @Deprecated
class BaseTextToSpeechEngineTest {

    private lateinit var context: Context
    private lateinit var tts: TextToSpeech
    private lateinit var engine: BaseTextToSpeechEngine
    private val initStatuses = mutableListOf<Int>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        tts = mock(TextToSpeech::class.java)
        engine = BaseTextToSpeechEngine().also {
            injectTts(it, tts)
            it.setOnInitListener { status -> initStatuses.add(status) }
        }
    }

    private fun injectTts(target: BaseTextToSpeechEngine, mockTts: TextToSpeech) {
        BaseTextToSpeechEngine::class.java.getDeclaredField("mTextToSpeech").apply {
            isAccessible = true
            set(target, mockTts)
        }
    }

    private fun assertSingleInitStatus(expected: Int) {
        assertEquals(listOf(expected), initStatuses)
    }

    /** 语言不可用 → 走回退分支：第一次 setLanguage 返回不支持，第二次（回退后）返回可用 */
    private fun stubLanguageFallbackThenAvailable() {
        `when`(tts.setLanguage(any(Locale::class.java)))
            .thenReturn(TextToSpeech.LANG_NOT_SUPPORTED)
            .thenReturn(TextToSpeech.LANG_COUNTRY_AVAILABLE)
    }

    /** 语言永远不可用（含回退后） */
    private fun stubLanguageNeverAvailable() {
        `when`(tts.setLanguage(any(Locale::class.java)))
            .thenReturn(TextToSpeech.LANG_NOT_SUPPORTED)
    }

    /** 语言可用（不进回退分支） */
    private fun stubLanguageAvailable() {
        `when`(tts.setLanguage(any(Locale::class.java)))
            .thenReturn(TextToSpeech.LANG_COUNTRY_AVAILABLE)
    }

    // T1：复现崩溃场景 —— getDefaultLanguage 在框架内抛 NPE（劣质引擎返回含 null 分量的数组）
    @Test
    fun onTtsInit_getDefaultLanguageThrowsNPE_fallsBackToSystemDefaultAndSucceeds() {
        stubLanguageFallbackThenAvailable()
        `when`(tts.getDefaultLanguage()).thenThrow(NullPointerException("libcore Locale"))

        engine.onTtsInit(TextToSpeech.SUCCESS, context)

        assertSingleInitStatus(TextToSpeech.SUCCESS)
        val captor = ArgumentCaptor.forClass(Locale::class.java)
        verify(tts, times(2)).setLanguage(captor.capture())
        assertEquals(Locale.getDefault(), captor.allValues[1])
    }

    // T2：getDefaultLanguage 抛异常且系统默认语言也不可用 → 走既有 ERROR 上报，不崩溃
    @Test
    fun onTtsInit_fallbackLanguageUnavailable_reportsError() {
        stubLanguageNeverAvailable()
        `when`(tts.getDefaultLanguage()).thenThrow(NullPointerException("libcore Locale"))

        engine.onTtsInit(TextToSpeech.SUCCESS, context)

        assertSingleInitStatus(TextToSpeech.ERROR)
        verify(tts, times(2)).setLanguage(any(Locale::class.java))
    }

    // T3：正常路径回归 —— 请求语言可用，不触发回退，默认 Voice 被应用
    @Test
    fun onTtsInit_requestedLanguageAvailable_appliesDefaultVoice() {
        stubLanguageAvailable()
        val defaultVoice = Voice("engine-default", Locale.US, Voice.QUALITY_HIGH, 200, false, emptySet())
        `when`(tts.getDefaultVoice()).thenReturn(defaultVoice)

        engine.onTtsInit(TextToSpeech.SUCCESS, context)

        assertSingleInitStatus(TextToSpeech.SUCCESS)
        verify(tts, times(1)).setLanguage(any(Locale::class.java))
        verify(tts, never()).getDefaultLanguage()
        verify(tts).setVoice(defaultVoice)
    }

    // T4：getDefaultVoice 抛异常 → 跳过 setVoice，初始化继续成功
    @Test
    fun onTtsInit_getDefaultVoiceThrows_skipsSetVoiceAndSucceeds() {
        stubLanguageAvailable()
        `when`(tts.getDefaultVoice()).thenThrow(RuntimeException("engine boom"))

        engine.onTtsInit(TextToSpeech.SUCCESS, context)

        assertSingleInitStatus(TextToSpeech.SUCCESS)
        verify(tts, never()).setVoice(any(Voice::class.java))
    }

    // T5：setLanguage 抛 DeadObjectException（引擎进程死亡）→ 外层兜底降级为 ERROR，不崩溃
    @Test
    fun onTtsInit_setLanguageThrowsDeadObject_reportsError() {
        // DeadObjectException 是受检异常，setLanguage 未声明，须用 thenAnswer 绕过 Mockito 校验
        `when`(tts.setLanguage(any(Locale::class.java))).thenAnswer { throw DeadObjectException() }

        engine.onTtsInit(TextToSpeech.SUCCESS, context)

        assertSingleInitStatus(TextToSpeech.ERROR)
        verify(tts, never()).getDefaultLanguage()
    }

    // T6：getVoices() 返回 null → getSupportedVoices 返回空列表，不 NPE（附带加固）
    @Test
    fun getSupportedVoices_nullVoicesFromEngine_returnsEmptyList() {
        `when`(tts.voices).thenReturn(null)

        val voices = engine.supportedVoices

        assertTrue(voices.isEmpty())
    }

    // T7：getDefaultLanguage 返回 null（框架正常返回 null 的情形）→ 回退系统默认语言
    @Test
    fun onTtsInit_getDefaultLanguageReturnsNull_fallsBackToSystemDefault() {
        stubLanguageFallbackThenAvailable()
        `when`(tts.getDefaultLanguage()).thenReturn(null)

        engine.onTtsInit(TextToSpeech.SUCCESS, context)

        assertSingleInitStatus(TextToSpeech.SUCCESS)
        val captor = ArgumentCaptor.forClass(Locale::class.java)
        verify(tts, times(2)).setLanguage(captor.capture())
        assertEquals(Locale.getDefault(), captor.allValues[1])
    }
// T8：setSpeechRate 未初始化时保存速率并返回 SUCCESS，初始化后生效
    @Test
    fun setSpeechRate_beforeInit_returnsSuccessAndAppliesOnInit() {
        val uninitEngine = BaseTextToSpeechEngine()
        val res = uninitEngine.setSpeechRate(0.85f)
        assertEquals(TextToSpeech.SUCCESS, res)

        injectTts(uninitEngine, tts)
        stubLanguageAvailable()
        `when`(tts.setSpeechRate(0.85f)).thenReturn(TextToSpeech.SUCCESS)

        uninitEngine.onTtsInit(TextToSpeech.SUCCESS, context)
        verify(tts).setSpeechRate(0.85f)
    }

    // T9：setSpeechRate 初始化后直接委托底层 TTS
    @Test
    fun setSpeechRate_afterInit_delegatesToTts() {
        `when`(tts.setSpeechRate(0.9f)).thenReturn(TextToSpeech.SUCCESS)
        val res = engine.setSpeechRate(0.9f)
        assertEquals(TextToSpeech.SUCCESS, res)
        verify(tts).setSpeechRate(0.9f)
    }

    // T10：setPitch 未初始化时保存音调并返回 SUCCESS，初始化后生效
    @Test
    fun setPitch_beforeInit_returnsSuccessAndAppliesOnInit() {
        val uninitEngine = BaseTextToSpeechEngine()
        val res = uninitEngine.setPitch(1.1f)
        assertEquals(TextToSpeech.SUCCESS, res)

        injectTts(uninitEngine, tts)
        stubLanguageAvailable()
        `when`(tts.setPitch(1.1f)).thenReturn(TextToSpeech.SUCCESS)

        uninitEngine.onTtsInit(TextToSpeech.SUCCESS, context)
        verify(tts).setPitch(1.1f)
    }
}
