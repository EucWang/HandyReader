package net.gotev.speech;

import net.gotev.speech.engine.PlayErrorCode;

/**
 * Contains the methods which are called to notify text to speech progress status.
 *
 * @author Aleksandar Gotev
 */
public interface TextToSpeechCallback {

    void onPrepare(final String utteranceId);

    void onStart(final String utteranceId);
    void onCompleted(final String utteranceId);
    void onError(final String utteranceId, PlayErrorCode code);
}

