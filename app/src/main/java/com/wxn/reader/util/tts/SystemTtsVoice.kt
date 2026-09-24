package com.wxn.reader.util.tts

import java.util.Locale
import kotlinx.coroutines.channels.Channel

data class SystemTtsVoice(
    val name: String,
    val localeTag: String,
    val isNetworkRequired: Boolean,
    val isInstalled: Boolean,
    val quality: Int = 0,
    val latency: Int = 0,
) {
    val localeDisplayName: String
        get() = Locale.forLanguageTag(localeTag).displayName

    val details: String
        get() = listOf(
            localeDisplayName,
            if (isNetworkRequired) "Network" else "Local",
            if (isInstalled) "Installed" else "Download required",
        ).joinToString(" · ")
}

object TtsVoiceSelector {
    const val PREFERRED_RUSSIAN_NETWORK_VOICE = "ru-ru-x-rud-network"

    fun select(
        voices: List<SystemTtsVoice>,
        savedVoiceName: String,
        requestedLanguage: String,
    ): SystemTtsVoice? {
        val installedVoices = voices.filter(SystemTtsVoice::isInstalled)

        return installedVoices.firstOrNull { it.name == savedVoiceName }
            ?: installedVoices.firstOrNull {
                requestedLanguage.equals("ru", ignoreCase = true) &&
                    it.name == PREFERRED_RUSSIAN_NETWORK_VOICE
            }
    }
}

sealed interface TtsEngineState {
    data object Initializing : TtsEngineState

    data object Ready : TtsEngineState

    data object Failed : TtsEngineState
}

sealed interface TtsVoicesUiState {
    data object Loading : TtsVoicesUiState

    data class Available(val voices: List<SystemTtsVoice>) : TtsVoicesUiState

    data object Error : TtsVoicesUiState

    companion object {
        fun forEngine(engineState: TtsEngineState): TtsVoicesUiState {
            return when (engineState) {
                TtsEngineState.Initializing -> Loading
                TtsEngineState.Ready -> Available(emptyList())
                TtsEngineState.Failed -> Error
            }
        }
    }
}

object TtsLanguageSelector {
    fun selectCode(
        bookLanguageCode: String?,
        savedLanguageCode: String,
        useBookLanguage: Boolean,
    ): String {
        return if (useBookLanguage) {
            bookLanguageCode?.takeIf(String::isNotBlank) ?: savedLanguageCode
        } else {
            savedLanguageCode
        }
    }
}

enum class TtsPreviewCommand {
    Preview,
    StopNarrationThenPreview,
}

object TtsPreviewPolicy {
    fun commandFor(isTtsPlaying: Boolean): TtsPreviewCommand {
        return if (isTtsPlaying) {
            TtsPreviewCommand.StopNarrationThenPreview
        } else {
            TtsPreviewCommand.Preview
        }
    }
}

class TtsReaderSession {
    private var nextToken = 0L
    private var activeToken: Long? = null

    @Synchronized
    fun begin(): Long {
        val token = ++nextToken
        activeToken = token
        return token
    }

    @Synchronized
    fun cancel() {
        activeToken = null
    }

    @Synchronized
    fun isActive(token: Long): Boolean = activeToken == token

    @Synchronized
    fun finish(token: Long): Boolean {
        if (activeToken != token) {
            return false
        }
        activeToken = null
        return true
    }
}

data class TtsSettingUpdate(
    val applyToEngine: Boolean,
    val persist: Boolean,
)

object TtsSettingUpdatePolicy {
    fun forValue(currentEngineValue: Float, requestedValue: Float): TtsSettingUpdate {
        return TtsSettingUpdate(
            applyToEngine = currentEngineValue != requestedValue,
            persist = true,
        )
    }
}

class TtsPreviewSession {
    private var nextToken = 0L
    private var activeToken: Long? = null

    @Synchronized
    fun begin(): Long {
        val token = ++nextToken
        activeToken = token
        return token
    }

    @Synchronized
    fun cancel(): Boolean {
        if (activeToken == null) {
            return false
        }
        activeToken = null
        return true
    }

    @Synchronized
    fun finish(token: Long): Boolean {
        if (activeToken != token) {
            return false
        }
        activeToken = null
        return true
    }
}

class TtsParagraphBuffer<T>(
    private val paragraphs: List<T>,
    requestedSize: Int,
) {
    private val bufferSize = requestedSize.coerceIn(MIN_SIZE, MAX_SIZE)
    private var nextIndex = 0
    private var initialized = false

    fun initial(): List<T> {
        if (initialized) {
            return emptyList()
        }
        initialized = true
        return takeAvailable(bufferSize)
    }

    fun nextAfterCompletion(): T? = takeAvailable(1).firstOrNull()

    private fun takeAvailable(count: Int): List<T> {
        val endIndex = (nextIndex + count).coerceAtMost(paragraphs.size)
        val result = paragraphs.subList(nextIndex, endIndex)
        nextIndex = endIndex
        return result
    }

    companion object {
        const val MIN_SIZE = 3
        const val MAX_SIZE = 7
    }
}

sealed interface TtsPlaybackEvent<out T> {
    val value: T

    data class Started<T>(override val value: T) : TtsPlaybackEvent<T>

    data class Completed<T>(
        override val value: T,
        val succeeded: Boolean,
    ) : TtsPlaybackEvent<T>
}

class TtsPlaybackSession<T> {
    private val events = Channel<TtsPlaybackEvent<T>>(Channel.UNLIMITED)

    fun started(value: T) {
        events.trySend(TtsPlaybackEvent.Started(value))
    }

    fun completed(value: T, succeeded: Boolean) {
        events.trySend(TtsPlaybackEvent.Completed(value, succeeded))
    }

    fun cancel() {
        events.cancel()
    }

    suspend fun nextEvent(): TtsPlaybackEvent<T>? = events.receiveCatching().getOrNull()
}

class TtsPageBuffer<T>(
    pages: List<List<T>>,
    startPageIndex: Int,
) {
    private val firstPageIndex = startPageIndex.coerceIn(0, pages.size)
    private val remainingPages = pages.drop(firstPageIndex)

    fun items(): List<T> = remainingPages.flatten()

    fun pages(): List<List<T>> = remainingPages

    fun pageIndexFor(items: List<T>): Int? {
        val pageOffset = remainingPages.indexOfFirst { page ->
            page.any { pageItem -> items.any { item -> pageItem === item } }
        }
        return (firstPageIndex + pageOffset).takeIf { pageOffset >= 0 }
    }
}
