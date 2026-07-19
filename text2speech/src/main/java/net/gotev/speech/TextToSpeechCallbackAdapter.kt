package net.gotev.speech

import net.gotev.speech.engine.PlayErrorCode

open class TextToSpeechCallbackAdapter : TextToSpeechCallback {

    override fun onPrepare(utteranceId: String?) {
    }

    override fun onStart(utteranceId: String?) {
    }

    override fun onCompleted(utteranceId: String?) {
    }

    override fun onError(utteranceId: String?, code: PlayErrorCode) {
    }
}