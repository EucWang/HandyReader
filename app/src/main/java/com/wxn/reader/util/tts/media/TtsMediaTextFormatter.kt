package com.wxn.reader.util.tts.media

import android.content.Context
import com.wxn.reader.R
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

fun interface TtsMediaTextFormatter {
    fun progressDescription(state: TtsMediaState): String
}

class AndroidTtsMediaTextFormatter(
    private val context: Context,
) : TtsMediaTextFormatter {
    override fun progressDescription(state: TtsMediaState): String {
        val percentage = (state.progression.coerceIn(0.0, 1.0) * 100).roundToInt()
        val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(state.estimatedRemainingMs)
        val remaining = if (remainingMinutes >= MINUTES_PER_HOUR) {
            context.getString(
                R.string.tts_media_remaining_hours_minutes,
                remainingMinutes / MINUTES_PER_HOUR,
                remainingMinutes % MINUTES_PER_HOUR,
            )
        } else {
            context.getString(R.string.tts_media_remaining_minutes, remainingMinutes)
        }
        return context.getString(R.string.tts_media_progress_description, percentage, remaining)
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60L
    }
}
