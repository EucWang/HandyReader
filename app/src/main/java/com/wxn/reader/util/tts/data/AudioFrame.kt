package com.wxn.reader.util.tts.data

class AudioFrame(
    val data: ByteArray?,
    val textCompleted: Boolean = false,
    val audioCompleted: Boolean = false
) {
}