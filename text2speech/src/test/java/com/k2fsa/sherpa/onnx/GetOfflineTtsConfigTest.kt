package com.k2fsa.sherpa.onnx

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归守卫：getOfflineTtsConfig 的 lexicon 拼接规则。
 *
 * 背景：vits/matcha 分支曾对空 lexicon 无条件拼接 "$modelDir/$lexicon"，生成指向目录的
 * 非法路径（如 .../matcha-icefall-en_US-ljspeech/），native OfflineTts 配置校验失败返回
 * 空指针，后续 sampleRate() 解引用导致 SIGSEGV（TTS 启动闪退）。守卫要求：空 lexicon
 * 原样传空串（native 跳过 lexicon 校验，走 espeak-ng data_dir 音素化），非空才拼接。
 */
class GetOfflineTtsConfigTest {

    private fun matchaConfig(lexicon: String) = getOfflineTtsConfig(
        modelDir = "/data/models/matcha-icefall-en_US-ljspeech",
        modelName = "",
        acousticModelName = "model-steps-3.onnx",
        vocoder = "/data/models/dependencies/vocos-22khz-univ.onnx",
        voices = "",
        lexicon = lexicon,
        dataDir = "/data/models/dependencies/espeak-ng-data",
        dictDir = "",
        ruleFsts = "",
        ruleFars = "",
    )

    private fun vitsConfig(lexicon: String) = getOfflineTtsConfig(
        modelDir = "/data/models/vits-piper-en_US-amy-low",
        modelName = "en_US-amy-low.onnx",
        acousticModelName = "",
        vocoder = "",
        voices = "",
        lexicon = lexicon,
        dataDir = "/data/models/dependencies/espeak-ng-data",
        dictDir = "",
        ruleFsts = "",
        ruleFars = "",
    )

    private fun kokoroConfig(lexicon: String) = getOfflineTtsConfig(
        modelDir = "/data/models/kokoro-en-v0_19",
        modelName = "model.onnx",
        acousticModelName = "",
        vocoder = "",
        voices = "voices.bin",
        lexicon = lexicon,
        dataDir = "",
        dictDir = "",
        ruleFsts = "",
        ruleFars = "",
    )

    /** U1：matcha 空 lexicon → 传空串（本次崩溃的原必崩场景） */
    @Test
    fun matcha_emptyLexicon_passesEmptyToNative() {
        val config = matchaConfig(lexicon = "")
        assertEquals("", config.model.matcha.lexicon)
    }

    /** U2：matcha 扫描到 lexicon.txt → 正常拼接（既有行为不回归，如 zh-baker） */
    @Test
    fun matcha_lexiconFound_joinsModelDir() {
        val config = matchaConfig(lexicon = "lexicon.txt")
        assertEquals(
            "/data/models/matcha-icefall-en_US-ljspeech/lexicon.txt",
            config.model.matcha.lexicon
        )
    }

    /** U3：vits 空 lexicon → 传空串 */
    @Test
    fun vits_emptyLexicon_passesEmptyToNative() {
        val config = vitsConfig(lexicon = "")
        assertEquals("", config.model.vits.lexicon)
    }

    /** U4：vits 扫描到 lexicon.txt → 正常拼接 */
    @Test
    fun vits_lexiconFound_joinsModelDir() {
        val config = vitsConfig(lexicon = "lexicon.txt")
        assertEquals(
            "/data/models/vits-piper-en_US-amy-low/lexicon.txt",
            config.model.vits.lexicon
        )
    }

    /** U5：kokoro 分支既有的空串守卫与多路径透传不受影响（上游行为保持） */
    @Test
    fun kokoro_emptyAndMultiPathLexicon_unchanged() {
        assertEquals("", kokoroConfig(lexicon = "").model.kokoro.lexicon)
        assertEquals(
            "/data/models/kokoro/lexicon-a.txt,/data/models/kokoro/lexicon-b.txt",
            kokoroConfig(lexicon = "/data/models/kokoro/lexicon-a.txt,/data/models/kokoro/lexicon-b.txt")
                .model.kokoro.lexicon
        )
    }
}
