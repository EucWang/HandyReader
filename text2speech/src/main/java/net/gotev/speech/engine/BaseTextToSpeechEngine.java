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
        mTextToSpeech = new TextToSpeech(context.getApplicationContext(), status -> onTtsInit(status, context));
    }

    void onTtsInit(int status, Context context) {
        int finalStatus = status;
        if (status == TextToSpeech.SUCCESS) {
            try {
                // 检查请求的语言是否支持
                int languageAvailable = mTextToSpeech.setLanguage(mLocale);
                if (!isLanguageAvailable(languageAvailable)) {
                    Logger.INSTANCE.w("BaseTextToSpeechEngine: Language " + mLocale + " not available, falling back to default");
                    // 尝试使用默认语言。已知缺陷（issue 680d19a3）：部分劣质 TTS 引擎返回含
                    // null 分量的数组，libcore 的 Locale 构造器对 null 分量直接抛 NPE，
                    // 该异常沿框架回调链逸出会导致崩溃，故必须在此防御。
                    Locale defaultLocale = null;
                    try {
                        defaultLocale = mTextToSpeech.getDefaultLanguage();
                    } catch (Exception e) {
                        Logger.INSTANCE.w("BaseTextToSpeechEngine: getDefaultLanguage threw, fallback to Locale.getDefault(): " + e);
                    }
                    // getDefaultLanguage 抛异常或返回 null 时，用系统默认语言兜底
                    if (defaultLocale == null) {
                        defaultLocale = Locale.getDefault();
                    }
                    mLocale = defaultLocale;
                    languageAvailable = mTextToSpeech.setLanguage(defaultLocale);
                }

                // 语言仍不可用 → 上报初始化失败，避免静默无声音
                if (!isLanguageAvailable(languageAvailable)) {
                    Logger.INSTANCE.e("BaseTextToSpeechEngine: no available TTS language for " + mLocale);
                    finalStatus = TextToSpeech.ERROR;
                } else {
                    mTtsProgressListener = new TtsProgressListener(context, mTtsCallbacks);
                    mTextToSpeech.setOnUtteranceProgressListener(mTtsProgressListener);
                    mTextToSpeech.setPitch(mTtsPitch);
                    mTextToSpeech.setSpeechRate(mTtsRate);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Voice defaultVoice = null;
                        try {
                            defaultVoice = mTextToSpeech.getDefaultVoice();
                        } catch (Exception e) {
                            Logger.INSTANCE.w("BaseTextToSpeechEngine: getDefaultVoice threw, skip setVoice: " + e);
                        }
                        voice = resolveVoiceToApply(voice, defaultVoice);
                        if (voice != null) {
                            mTextToSpeech.setVoice(voice);
                        } else {
                            Logger.INSTANCE.w("BaseTextToSpeechEngine: default voice is null, skip setVoice; using engine default");
                        }
                    }
                }
            } catch (Throwable t) {
                // 外层兜底：onTtsInit 由框架在主线程直接回调，任何异常逸出都会崩溃
                // （如 setLanguage 的 DeadObjectException、init/shutdown 竞态）。
                // 降级为 init 失败上报，保证 listener 恰好回调一次。
                Logger.INSTANCE.e("BaseTextToSpeechEngine: onTtsInit unexpected error: " + t);
                finalStatus = TextToSpeech.ERROR;
            }
        }

        if (mTttsInitListener != null) {
            mTttsInitListener.onInit(finalStatus);
        }
    }

    static boolean isLanguageAvailable(int languageAvailable) {
        return languageAvailable != TextToSpeech.LANG_MISSING_DATA
                && languageAvailable != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    static Voice resolveVoiceToApply(Voice currentVoice, Voice defaultVoice) {
        return currentVoice != null ? currentVoice : defaultVoice;
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
            mTtsCallbacks.clear();
            try {
                mTextToSpeech.stop();
            } catch (final Exception exc) {
                Logger.INSTANCE.e(getClass().getSimpleName() + "Warning while stopping text to speech" + exc);
            }
            try {
                mTextToSpeech.shutdown();
            } catch (final Exception exc) {
                Logger.INSTANCE.e(getClass().getSimpleName() + "Warning while shutting down text to speech" + exc);
            } finally {
                mTextToSpeech = null;
            }
        }
        if (listener != null) {
            listener.onDone();
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
            return TextToSpeech.SUCCESS;
        }
    }

    @Override
    public int setSpeechRate(float rate) {
        mTtsRate = rate;
        if (mTextToSpeech != null) {
            return mTextToSpeech.setSpeechRate(rate);
        } else {
            return TextToSpeech.SUCCESS;
        }
    }

    @Override
    public int setVoice(Voice voice) {
        if (voice != null) {
            this.voice = voice;
        }
        if (mTextToSpeech != null && Build.VERSION.SDK_INT >= 21 && this.voice != null) {
            return mTextToSpeech.setVoice(this.voice);
        } else {
            return TextToSpeech.ERROR;
        }
    }

    @Override
    public List<Voice> getSupportedVoices() {
        if (mTextToSpeech != null && Build.VERSION.SDK_INT >= 23) {
            Set<Voice> voices = mTextToSpeech.getVoices();
            if (voices == null) {
                // 部分引擎 getVoices() 返回 null，避免 NPE
                return new ArrayList<>(0);
            }
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
