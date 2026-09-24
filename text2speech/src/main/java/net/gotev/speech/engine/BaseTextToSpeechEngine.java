package net.gotev.speech.engine;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import net.gotev.speech.TextToSpeechCallback;
import net.gotev.speech.TtsProgressListener;
import com.wxn.base.util.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BaseTextToSpeechEngine implements TextToSpeechEngine {

    private TextToSpeech mTextToSpeech;
    private TextToSpeech.OnInitListener mTttsInitListener;
    private UtteranceProgressListener mTtsProgressListener;
    private float mTtsRate = 1.0f;
    private float mTtsPitch = 1.0f;
    private Locale mLocale = Locale.getDefault();
    private Voice voice;
    private boolean mIsInitialized;

    private int mTtsQueueMode = TextToSpeech.QUEUE_FLUSH;
    private int mAudioStream = TextToSpeech.Engine.DEFAULT_STREAM;

    private final Map<String, TextToSpeechCallback> mTtsCallbacks = new ConcurrentHashMap<>();

    @Override
    public void initTextToSpeech(Context context) {
        if (mTextToSpeech != null) {
            return;
        }

        mTtsProgressListener = new TtsProgressListener(context, mTtsCallbacks);
        mTextToSpeech = new TextToSpeech(
                context.getApplicationContext(),
                this::onTextToSpeechInitialized
        );
        mTextToSpeech.setOnUtteranceProgressListener(mTtsProgressListener);
    }

    private void onTextToSpeechInitialized(int status) {
        if (status == TextToSpeech.SUCCESS) {
            mIsInitialized = true;
            mTextToSpeech.setLanguage(mLocale);
            mTextToSpeech.setPitch(mTtsPitch);
            mTextToSpeech.setSpeechRate(mTtsRate);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (voice == null) {
                    voice = mTextToSpeech.getDefaultVoice();
                }
                mTextToSpeech.setVoice(voice);
            }
        }

        if (mTttsInitListener != null) {
            mTttsInitListener.onInit(status);
        }
    }

    @Override
    public boolean isSpeaking() {
        if (mTextToSpeech == null || !mIsInitialized) {
            return false;
        }

        return mTextToSpeech.isSpeaking();
    }

    public void setOnInitListener(TextToSpeech.OnInitListener onInitListener) {
        this.mTttsInitListener = onInitListener;
    }


    @Override
    public int setLocale(Locale locale) {
        mLocale = locale;
        if (mTextToSpeech != null && mIsInitialized) {
            return mTextToSpeech.setLanguage(locale);
        }
        return TextToSpeech.LANG_AVAILABLE;
    }

    @Override
    public void say(String message, TextToSpeechCallback callback) {
        if (!mIsInitialized) {
            if (callback != null) {
                callback.onError();
            }
            return;
        }

        final String utteranceId = UUID.randomUUID().toString();

        if (callback != null) {
            mTtsCallbacks.put(utteranceId, callback);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(mAudioStream));
            mTextToSpeech.speak(message, mTtsQueueMode, params, utteranceId);
        } else {
            final HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(mAudioStream));
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            mTextToSpeech.speak(message, mTtsQueueMode, params);
        }
    }

    @Override
    public void shutdown() {
        if (mTextToSpeech != null) {
            try {
                mTtsCallbacks.clear();
                mTextToSpeech.stop();
                mTextToSpeech.shutdown();
                mIsInitialized = false;
            } catch (final Exception exc) {
                Logger.INSTANCE.e(getClass().getSimpleName() + "Warning while de-initing text to speech" + exc);
            }
        }
    }

    @Override
    public void setTextToSpeechQueueMode(int mode) {
        mTtsQueueMode = mode;
    }

    @Override
    public int getTextToSpeechQueueMode() {
        return mTtsQueueMode;
    }

    @Override
    public void setAudioStream(int audioStream) {
        mAudioStream = audioStream;
    }

    @Override
    public void stop() {
        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
        }
    }

    @Override
    public void setPitch(float pitch) {
        mTtsPitch = pitch;
        if (mTextToSpeech != null && mIsInitialized) {
            mTextToSpeech.setPitch(pitch);
        }
    }

    @Override
    public void setSpeechRate(float rate) {
        mTtsRate = rate;
        if (mTextToSpeech != null && mIsInitialized) {
            mTextToSpeech.setSpeechRate(rate);
        }
    }

    @Override
    public int setVoice(Voice voice) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return TextToSpeech.ERROR;
        }

        if (mTextToSpeech == null || !mIsInitialized) {
            this.voice = voice;
            return TextToSpeech.SUCCESS;
        }

        final int result = mTextToSpeech.setVoice(voice);
        if (result == TextToSpeech.SUCCESS) {
            this.voice = voice;
        }
        return result;
    }

    @Override
    public int resetVoice() {
        voice = null;
        if (mTextToSpeech == null || !mIsInitialized) {
            return TextToSpeech.SUCCESS;
        }

        final int result = mTextToSpeech.setLanguage(mLocale);
        if (result >= TextToSpeech.LANG_AVAILABLE) {
            return TextToSpeech.SUCCESS;
        }
        return TextToSpeech.ERROR;
    }

    @Override
    public List<Voice> getSupportedVoices() {
        if (mTextToSpeech != null && mIsInitialized && Build.VERSION.SDK_INT >= 23) {
            Set<Voice> voices = mTextToSpeech.getVoices();
            ArrayList<Voice> voicesList = new ArrayList<>(voices.size());
            voicesList.addAll(voices);
            return voicesList;
        }

        return new ArrayList<>(1);
    }

    @Override
    public Voice getCurrentVoice() {
        if (mTextToSpeech != null && mIsInitialized && Build.VERSION.SDK_INT >= 23) {
            return mTextToSpeech.getVoice();
        }

        return null;
    }
}
