package net.gotev.speech.engine;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import net.gotev.speech.OnShutdownListener;
import net.gotev.speech.TextToSpeechCallback;
import net.gotev.speech.TtsProgressListener;
import com.wxn.base.bean.EngineModelConfig;
import com.wxn.base.bean.SpeakSentence;
import com.wxn.base.util.Logger;

import java.util.*;

public class BaseTextToSpeechEngine implements TextToSpeechEngine {

    private TextToSpeech mTextToSpeech;
    private TextToSpeech.OnInitListener mTttsInitListener;
    private UtteranceProgressListener mTtsProgressListener;
    private float mTtsRate = 1.0f;
    private float mTtsPitch = 1.0f;
    private Locale mLocale = Locale.getDefault();
    private Voice voice;

    private int mTtsQueueMode = TextToSpeech.QUEUE_FLUSH;
    private int mAudioStream = TextToSpeech.Engine.DEFAULT_STREAM;

    private final Map<String, TextToSpeechCallback> mTtsCallbacks = new HashMap<>();

    public BaseTextToSpeechEngine() {

    }

    public BaseTextToSpeechEngine(float speed) {
        this.mTtsRate = speed;
    }

    public BaseTextToSpeechEngine(float speed , float pitch) {
        this.mTtsRate = speed;
        this.mTtsPitch = pitch;
    }

    public BaseTextToSpeechEngine(float speed, float pitch, Locale language) {
        this.mTtsRate = speed;
        this.mTtsPitch = pitch;
        this.mLocale = language;
    }

    @Override
    public void initTextToSpeech(Context context) {
        Logger.INSTANCE.i("BaseTextToSpeechEngine:init");
        if (mTextToSpeech != null) {
            Logger.INSTANCE.d("BaseTextToSpeechEngine:mTextToSpeech is not null");
            return;
        }
        mTextToSpeech = new TextToSpeech(context.getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                Logger.INSTANCE.d("BaseTextToSpeechEngine:initTextToSpeech:onInit:status=" + status);
                
                if (status == TextToSpeech.SUCCESS) {
                    // 检查请求的语言是否支持
                    int languageAvailable = mTextToSpeech.setLanguage(mLocale);
                    if (languageAvailable == TextToSpeech.LANG_MISSING_DATA || 
                        languageAvailable == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Logger.INSTANCE.w("BaseTextToSpeechEngine: Language " + mLocale + " not available, falling back to default");
                        // 尝试使用默认语言
                        Locale defaultLocale = mTextToSpeech.getDefaultLanguage();
                        if (defaultLocale != null) {
                            mLocale = defaultLocale;
                            mTextToSpeech.setLanguage(defaultLocale);
                        }
                    }
                    
                    mTtsProgressListener = new TtsProgressListener(context, mTtsCallbacks);
                    mTextToSpeech.setOnUtteranceProgressListener(mTtsProgressListener);
                    mTextToSpeech.setPitch(mTtsPitch);
                    mTextToSpeech.setSpeechRate(mTtsRate);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        if (voice == null) {
                            voice = mTextToSpeech.getDefaultVoice();
                        }
                        mTextToSpeech.setVoice(voice);
                    }
                }
                
                mTttsInitListener.onInit(status);
            }
        });


    }

    @Override
    public boolean isSpeaking() {
        if (mTextToSpeech == null) {
            return false;
        }

        return mTextToSpeech.isSpeaking();
    }

    public void setOnInitListener(TextToSpeech.OnInitListener onInitListener) {
        this.mTttsInitListener = onInitListener;
    }


    @Override
    public EngineModelConfig getConfig() {
        return null;
    }

    @Override
    public int setLocale(Locale locale) {
        mLocale = locale;
        if (mTextToSpeech != null) {
            return mTextToSpeech.setLanguage(locale);
        }
        return -1;
    }

    @Override
   public  Set<Locale> getAvailableLanguages() {
        if (mTextToSpeech != null) {
            return mTextToSpeech.getAvailableLanguages();
        } else {
            return null;
        }
    }

    @Override
    public void say(SpeakSentence message, TextToSpeechCallback callback) {
//        Logger.INSTANCE.d("BaseTextToSpeechEngine:say:message=" + message);
        final String utteranceId = message.getLocator().toUtteranceId();

        if (callback != null) {
            mTtsCallbacks.put(utteranceId, callback);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            final Bundle params = new Bundle();
            params.putString(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(mAudioStream));
            mTextToSpeech.speak(message.getSentence(), mTtsQueueMode, params, utteranceId);
        } else {
            final HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(mAudioStream));
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            mTextToSpeech.speak(message.getSentence(), mTtsQueueMode, params);
        }
    }

    @Override
    public void shutdown(OnShutdownListener listener) {
        if (mTextToSpeech != null) {
            try {
                mTtsCallbacks.clear();
                mTextToSpeech.stop();
                mTextToSpeech.shutdown();
            } catch (final Exception exc) {
                Logger.INSTANCE.e(getClass().getSimpleName() + "Warning while de-initing text to speech" + exc);
            } finally {
                if (listener != null) {
                    listener.onDone();
                }
            }
        }
    }

    @Override
    public void setTextToSpeechQueueMode(int mode) {
        mTtsQueueMode = mode;
    }

    @Override
    public void setTextToSpeechSpeakerIndex(int speakerIndex) {
        //do nothing
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
    public void stopAndWait() {
        if (mTextToSpeech != null) {
            mTextToSpeech.stop();
        }
    }

    @Override
    public int setPitch(float pitch) {
        mTtsPitch = pitch;
        if (mTextToSpeech != null) {
            return mTextToSpeech.setPitch(pitch);
        } else {
            return TextToSpeech.ERROR;
        }
    }

    @Override
    public int setSpeechRate(float rate) {
        mTtsRate = rate;
        if (mTextToSpeech != null) {
            return mTextToSpeech.setSpeechRate(rate);
        } else {
            return TextToSpeech.ERROR;
        }
    }

    @Override
    public int setVoice(Voice voice) {
        this.voice = voice;
        if (mTextToSpeech != null && Build.VERSION.SDK_INT >= 21) {
            return mTextToSpeech.setVoice(voice);
        } else {
            return TextToSpeech.ERROR;
        }
    }

    @Override
    public List<Voice> getSupportedVoices() {
        if (mTextToSpeech != null && Build.VERSION.SDK_INT >= 23) {
            Set<Voice> voices = mTextToSpeech.getVoices();
            ArrayList<Voice> voicesList = new ArrayList<>(voices.size());
            voicesList.addAll(voices);
            return voicesList;
        }

        return new ArrayList<>(1);
    }

    @Override
    public Voice getCurrentVoice() {
        if (mTextToSpeech != null && Build.VERSION.SDK_INT >= 23) {
            return mTextToSpeech.getVoice();
        }

        return null;
    }
}
