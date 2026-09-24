package com.wxn.reader.util.tts

import com.wxn.reader.presentation.bookReader.components.VoiceSettingsHeightMode
import com.wxn.reader.presentation.bookReader.components.VoiceSettingsLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceSelectorTest {

    @Test
    fun `the settings screen voice list fills the available height`() {
        assertEquals(
            VoiceSettingsHeightMode.FillAvailable,
            VoiceSettingsHeightMode.forLocation(VoiceSettingsLocation.SettingsScreen),
        )
    }

    @Test
    fun `voice settings stays loading until the TTS engine is ready`() {
        assertEquals(
            TtsVoicesUiState.Loading,
            TtsVoicesUiState.forEngine(TtsEngineState.Initializing),
        )
    }

    @Test
    fun `voice settings offers retry when the TTS engine initialization fails`() {
        assertEquals(
            TtsVoicesUiState.Error,
            TtsVoicesUiState.forEngine(TtsEngineState.Failed),
        )
    }

    @Test
    fun `playing narration requires stop before preview`() {
        assertEquals(
            TtsPreviewCommand.StopNarrationThenPreview,
            TtsPreviewPolicy.commandFor(isTtsPlaying = true),
        )
    }

    @Test
    fun `idle narration previews directly`() {
        assertEquals(
            TtsPreviewCommand.Preview,
            TtsPreviewPolicy.commandFor(isTtsPlaying = false),
        )
    }

    @Test
    fun `late completion from an older TTS session is ignored`() {
        val session = TtsReaderSession()
        val previousPlayback = session.begin()
        val currentPlayback = session.begin()

        assertFalse(session.finish(previousPlayback))
        assertTrue(session.finish(currentPlayback))
    }

    @Test
    fun `a selected rate is persisted even when it matches the stale engine cache`() {
        assertEquals(
            TtsSettingUpdate(applyToEngine = false, persist = true),
            TtsSettingUpdatePolicy.forValue(currentEngineValue = 1.0f, requestedValue = 1.0f),
        )
    }

    @Test
    fun `uses the book language while automatic language selection is enabled`() {
        assertEquals(
            "ru",
            TtsLanguageSelector.selectCode(
                bookLanguageCode = "ru",
                savedLanguageCode = "en",
                useBookLanguage = true,
            ),
        )
    }

    @Test
    fun `uses the saved language after the reader selects one manually`() {
        assertEquals(
            "ru",
            TtsLanguageSelector.selectCode(
                bookLanguageCode = "en",
                savedLanguageCode = "ru",
                useBookLanguage = false,
            ),
        )
    }

    @Test
    fun `a cancelled preview cannot restore a voice after a newer choice`() {
        val previewSession = TtsPreviewSession()
        val previewToken = previewSession.begin()

        assertTrue(previewSession.cancel())
        assertFalse(previewSession.finish(previewToken))
    }

    @Test
    fun `uses an installed saved voice before the automatic Russian default`() {
        val savedVoice = SystemTtsVoice(
            name = "ru-ru-x-rud-local",
            localeTag = "ru-RU",
            isNetworkRequired = false,
            isInstalled = true,
        )
        val preferredNetworkVoice = SystemTtsVoice(
            name = "ru-ru-x-rud-network",
            localeTag = "ru-RU",
            isNetworkRequired = true,
            isInstalled = true,
        )

        val selected = TtsVoiceSelector.select(
            voices = listOf(preferredNetworkVoice, savedVoice),
            savedVoiceName = savedVoice.name,
            requestedLanguage = "ru",
        )

        assertEquals(savedVoice, selected)
    }

    @Test
    fun `automatically prefers the installed Google Russian network voice for Russian text`() {
        val localVoice = SystemTtsVoice(
            name = "ru-ru-x-rud-local",
            localeTag = "ru-RU",
            isNetworkRequired = false,
            isInstalled = true,
        )
        val preferredNetworkVoice = SystemTtsVoice(
            name = "ru-ru-x-rud-network",
            localeTag = "ru-RU",
            isNetworkRequired = true,
            isInstalled = true,
        )

        val selected = TtsVoiceSelector.select(
            voices = listOf(localVoice, preferredNetworkVoice),
            savedVoiceName = "",
            requestedLanguage = "ru",
        )

        assertEquals(preferredNetworkVoice, selected)
    }

    @Test
    fun `does not select a voice that still needs to be downloaded`() {
        val unavailablePreferredVoice = SystemTtsVoice(
            name = "ru-ru-x-rud-network",
            localeTag = "ru-RU",
            isNetworkRequired = true,
            isInstalled = false,
        )

        val selected = TtsVoiceSelector.select(
            voices = listOf(unavailablePreferredVoice),
            savedVoiceName = "",
            requestedLanguage = "ru",
        )

        assertNull(selected)
    }
}
