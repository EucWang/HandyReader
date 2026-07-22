package net.gotev.speech;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import com.wxn.base.bean.EngineModelConfig;
import com.wxn.base.bean.SpeakSentence;
import com.wxn.base.util.Logger;

import net.gotev.speech.engine.BaseTextToSpeechEngine;
import net.gotev.speech.engine.DummyOnInitListener;
import net.gotev.speech.engine.SherpaOnnxEngine;
import net.gotev.speech.engine.TextToSpeechEngine;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

public class Speech {

    private static Speech instance = null;
    private int engineType;

    private TextToSpeechEngine textToSpeechEngine;

    private Speech(final Context context, TextToSpeech.OnInitListener onInitListener, TextToSpeechEngine textToSpeechEngine) {
        if (textToSpeechEngine == null) {
            throw new RuntimeException("textToSpeechEngine cannot be null");
        }

        if (textToSpeechEngine instanceof BaseTextToSpeechEngine) {
            engineType = 0;
        } else if (textToSpeechEngine instanceof SherpaOnnxEngine) {
            engineType = 1;
        } else {
            engineType = -1;
        }

        this.textToSpeechEngine = textToSpeechEngine;
        this.textToSpeechEngine.setOnInitListener(onInitListener);
        this.textToSpeechEngine.initTextToSpeech(context);
    }

    /**
     * Initializes speech recognition.
     *
     * @param context application context
     * @return speech instance
     */
    public static Speech init(final Context context,
                              final float speed,
                              final float pitch,
                              final Locale language,
                              final TextToSpeech.OnInitListener initListener) {
        instance = null;
        TextToSpeech.OnInitListener listener;
        if (initListener == null) {
            listener = new DummyOnInitListener();
        } else {
            listener = initListener;
        }
        instance = new Speech(context, listener, new BaseTextToSpeechEngine(speed, pitch, language));
        return instance;
    }

    /**
     * Initializes speech recognition.
     *
     * @param context application context
     * @return speech instance
     */
    public static Speech init(final Context context,
                              final int engineType,
                              final EngineModelConfig engineModel,
                              final float speed,
                              final float pitch,
                              final Locale language,
                              final int speakerIndex,
                              final TextToSpeech.OnInitListener initListener) {
        instance = null;

        TextToSpeech.OnInitListener listener;
        if (initListener == null) {
            listener = new DummyOnInitListener();
        } else {
            listener = initListener;
        }
        if (engineType == 0) {
            instance = new Speech(context, listener, new BaseTextToSpeechEngine(speed, pitch, language));
        } else {
            instance = new Speech(context, listener, new SherpaOnnxEngine(engineModel,
                    speed,
                    speakerIndex));
        }
        return instance;
    }

    /**
     * Must be called inside Activity's onDestroy.
     */
    public synchronized void shutdown(final OnShutdownListener shutdownListener) {
        if (textToSpeechEngine != null) {
            textToSpeechEngine.shutdown(new OnShutdownListener() {
                @Override
                public void onDone() {
                    instance = null;
                    engineType = -1;
                    if (shutdownListener != null) {
                        shutdownListener.onDone();
                    }
                }
            });
        } else {
            instance = null;
            engineType = -1;
            if (shutdownListener != null) {
                shutdownListener.onDone();
            }
        }
        textToSpeechEngine = null;
        Logger.INSTANCE.d("Speech:shutdown:invoked");
    }

    /**
     * Check if text to speak is currently speaking.
     *
     * @return true if the text to speak is speaking, false otherwise
     */
    public boolean isSpeaking() {
        if (textToSpeechEngine != null) {
            return textToSpeechEngine.isSpeaking();
        } else {
            return false;
        }
    }

    /**
     * Uses text to speech to transform a written message into a sound.
     *
     * @param message  message to play
     * @param callback callback which will receive progress status of the operation
     */
    public void say(final SpeakSentence message, final TextToSpeechCallback callback) {
        if (textToSpeechEngine != null) {
            textToSpeechEngine.say(message, callback);
        } else {
            Logger.INSTANCE.w("Speech:say:textToSpeechEngine is null");
        }
    }

    /**
     * Stops text to speech.
     */
    public void stopTextToSpeech() {
        if (textToSpeechEngine != null) {
            textToSpeechEngine.stop();
        }
    }


    public void stopAndWait() {
        if (textToSpeechEngine != null) {
            textToSpeechEngine.stopAndWait();
        }
    }

    /**
     * Sets text to speech and recognition language.
     * Defaults to device language setting.
     *
     * @param locale new locale
     * @return speech instance
     */
    public int setLocale(final Locale locale) {
        if (textToSpeechEngine != null) {
            return textToSpeechEngine.setLocale(locale);
        } else {
            return TextToSpeech.ERROR;
        }
    }

    /**
     * Sets the speech rate. This has no effect on any pre-recorded speech.
     *
     * @param rate Speech rate. 1.0 is the normal speech rate, lower values slow down the speech
     *             (0.5 is half the normal speech rate), greater values accelerate it
     *             (2.0 is twice the normal speech rate).
     * @return speech instance
     */
    public int setTextToSpeechRate(final float rate) {
        if (textToSpeechEngine != null) {
            return textToSpeechEngine.setSpeechRate(rate);
        } else {
            return TextToSpeech.ERROR;
        }
    }

    /**
     * Sets the speech pitch for the TextToSpeech engine.
     * This has no effect on any pre-recorded speech.
     *
     * @param pitch Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the
     *              synthesized voice, greater values increase it.
     * @return speech instance
     */
    public int setTextToSpeechPitch(final float pitch) {
        if (textToSpeechEngine != null) {
            return textToSpeechEngine.setPitch(pitch);
        } else {
            return TextToSpeech.ERROR;
        }
    }

    /**
     * Sets the text to speech queue mode.
     * By default is TextToSpeech.QUEUE_FLUSH, which is faster, because it clears all the
     * messages before speaking the new one. TextToSpeech.QUEUE_ADD adds the last message
     * to speak in the queue, without clearing the messages that have been added.
     *
     * @param mode It can be either TextToSpeech.QUEUE_ADD or TextToSpeech.QUEUE_FLUSH.
     * @return speech instance
     */
    public Speech setTextToSpeechQueueMode(final int mode) {
        if (textToSpeechEngine != null) {
            textToSpeechEngine.setTextToSpeechQueueMode(mode);
        }
        return this;
    }

    public Speech setSpeakerIndex(final int speakerIndex) {
        if (textToSpeechEngine != null) {
            textToSpeechEngine.setTextToSpeechSpeakerIndex(speakerIndex);
        }
        return this;
    }

    /**
     * Sets the audio stream type.
     * By default is TextToSpeech.Engine.DEFAULT_STREAM, which is equivalent to
     * AudioManager.STREAM_MUSIC.
     *
     * @param audioStream A constant from AudioManager.
     *                    e.g. {@link android.media.AudioManager#STREAM_VOICE_CALL}
     * @return speech instance
     */
    public Speech setAudioStream(final int audioStream) {
        if (textToSpeechEngine != null) {
            textToSpeechEngine.setAudioStream(audioStream);
        }
        return this;
    }

    public Set<Locale> getSupportedTtsLanguages() {
        if (textToSpeechEngine != null) {
            return textToSpeechEngine.getAvailableLanguages();
        }
        return Collections.emptySet();
    }

    /****
     *
     * @return 0 : 系统默认引擎;  1: SherpaOnnx TTS引擎; -1 : 未知引擎
     */
    public int getEngineType() {
        return engineType;
    }

    public EngineModelConfig getConfig() {
        if (textToSpeechEngine != null) {
            return textToSpeechEngine.getConfig();
        } else {
            return null;
        }
    }
}
