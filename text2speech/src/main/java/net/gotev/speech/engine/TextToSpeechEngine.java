package net.gotev.speech.engine;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;

import net.gotev.speech.TextToSpeechCallback;

import java.util.List;
import java.util.Locale;

public interface TextToSpeechEngine {

    void initTextToSpeech(Context context);

    boolean isSpeaking();

    void say(String message, TextToSpeechCallback callback);

    void stop();

    void shutdown();

    void setTextToSpeechQueueMode(int mode);

    void setAudioStream(int audioStream);

    void setOnInitListener(TextToSpeech.OnInitListener onInitListener);

    void setPitch(float pitch);

    void setSpeechRate(float rate);

    /****
     *
     * @param locale
     * @return Code indicating the support status for the locale. See {@link #TextToSpeech.LANG_AVAILABLE},
     * {@link #TextToSpeech.LANG_COUNTRY_AVAILABLE}, {@link #TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE},
     * {@link #TextToSpeech.LANG_MISSING_DATA} and {@link #TextToSpeech.LANG_NOT_SUPPORTED}.
     */
    int setLocale(Locale locale);

    void setVoice(Voice voice);

    List<Voice> getSupportedVoices();

    Voice getCurrentVoice();
}
