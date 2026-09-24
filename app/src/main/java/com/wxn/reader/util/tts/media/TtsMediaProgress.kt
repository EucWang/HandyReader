package com.wxn.reader.util.tts.media

import kotlin.math.roundToLong

data class TtsMediaProgressEstimate(
    val progression: Double,
    val estimatedDurationMs: Long,
    val estimatedPositionMs: Long,
    val estimatedRemainingMs: Long,
)

object TtsMediaProgress {
    private const val CHARACTERS_PER_MINUTE = 1_000.0
    private const val MINIMUM_SPEECH_RATE = 0.25
    private const val MAXIMUM_SPEECH_RATE = 2.0

    fun estimate(
        totalCharacters: Long,
        progression: Double,
        speechRate: Float,
    ): TtsMediaProgressEstimate {
        val safeProgression = progression.coerceIn(0.0, 1.0)
        val safeSpeechRate = speechRate.toDouble().coerceIn(MINIMUM_SPEECH_RATE, MAXIMUM_SPEECH_RATE)
        val estimatedDurationMs = (
            totalCharacters.coerceAtLeast(0).toDouble() / CHARACTERS_PER_MINUTE * 60_000 / safeSpeechRate
        ).roundToLong()
        val estimatedPositionMs = (estimatedDurationMs * safeProgression).roundToLong()

        return TtsMediaProgressEstimate(
            progression = safeProgression,
            estimatedDurationMs = estimatedDurationMs,
            estimatedPositionMs = estimatedPositionMs,
            estimatedRemainingMs = estimatedDurationMs - estimatedPositionMs,
        )
    }
}
