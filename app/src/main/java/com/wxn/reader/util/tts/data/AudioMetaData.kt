package com.wxn.reader.util.tts.data

data class AudioMetaData(
    val locale: String,
    val voiceName: String,
    val volume: String,
    val outputFormat: String,
    val pitch: String,
    val rate: String,
) {
    internal fun invalid(): Boolean {
        return locale.isEmpty() ||
                voiceName.isEmpty() ||
                volume.isEmpty() ||
                outputFormat.isEmpty() ||
                pitch.isEmpty() ||
                rate.isEmpty()
    }
}