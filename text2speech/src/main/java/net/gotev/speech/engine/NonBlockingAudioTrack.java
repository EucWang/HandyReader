package net.gotev.speech.engine;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTimestamp;
import android.media.AudioTrack;

import com.wxn.base.util.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class for playing audio by using audio track.
 * audioTrack.write methods will
 * block until all data has been written to system. In order to avoid blocking, this class
 * caculates available buffer size first then writes to audio sink.
 */
public class NonBlockingAudioTrack {
    private static final String TAG = NonBlockingAudioTrack.class.getSimpleName();
    private static final int MIN_TIMESTAMP_SAMPLE_INTERVAL_US = 250000;

    public static final int MAX_QUEUE_CAPACITY = 50; //最多阻塞50条数据
    public static final int MAX_QUEUE_BYTES = 3 * 1024 * 1024; //最多3M的缓冲数据

    static class QueueElement {
        String utteranceId;

        float[] data;

        int offset;
        int size;
    }

    private PlaySentenceCallback mPlaySentenceCallback;


    private AudioTrack mAudioTrack;
    private final int mSampleRate;
    private final AtomicInteger mNumBytesQueued = new AtomicInteger(0);
    private final BlockingQueue<QueueElement> mQueue = new LinkedBlockingQueue<QueueElement>(MAX_QUEUE_CAPACITY);

    private volatile boolean mStopped;
    private volatile boolean mRelease;

    private volatile boolean mHasInit;

    private Method getLatencyMethod;
    private long mLatencyUs;
    private long mLastTimestampSampleTimeUs;
    private boolean mAudioTimestampSet;
    private final AudioTimestamp mAudioTimestamp;

    public NonBlockingAudioTrack(int sampleRate, int channelCount) {
        int channelConfig = switch (channelCount) {
            case 1 -> AudioFormat.CHANNEL_OUT_MONO; //CHANNEL_OUT_MONO
            case 2 -> AudioFormat.CHANNEL_OUT_STEREO;
            case 6 -> AudioFormat.CHANNEL_OUT_5POINT1;
            default -> throw new IllegalArgumentException();
        };

        int minBufferSize =
                AudioTrack.getMinBufferSize(
                        sampleRate,
                        channelConfig,
                        AudioFormat.ENCODING_PCM_FLOAT);

        int bufferSize = 2 * minBufferSize;

        try {

            mAudioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    channelConfig,
                    AudioFormat.ENCODING_PCM_FLOAT, //ENCODING_PCM_FLOAT
                    bufferSize,
                    AudioTrack.MODE_STREAM);
            mHasInit = true;
        } catch (Exception ex) {
            Logger.INSTANCE.w("NonBlockingAudioTrack:create AudioTrack failed:" + ex.getMessage());
            mHasInit = false;
        }

        mSampleRate = sampleRate;
        mRelease = false;

        try {
            getLatencyMethod = AudioTrack.class.getMethod("getLatency", (Class<?>[]) null);
        } catch (NoSuchMethodException e) {
        }
        mLatencyUs = 0;
        mLastTimestampSampleTimeUs = 0;
        mAudioTimestamp = new AudioTimestamp();
    }

    public long getAudioTimeUs() {
        if (!isValid()) {
            Logger.INSTANCE.w("NonBlockingAudioTrack::getAudioTimeUs failed:: INVALID");
            return -1L;
        }
        long systemClockUs = System.nanoTime() / 1000;
        int numFramesPlayed = mAudioTrack.getPlaybackHeadPosition();
        if (systemClockUs - mLastTimestampSampleTimeUs >= MIN_TIMESTAMP_SAMPLE_INTERVAL_US) {
            mAudioTimestampSet = mAudioTrack.getTimestamp(mAudioTimestamp);
            if (getLatencyMethod != null) {
                try {
                    mLatencyUs = (Integer) getLatencyMethod.invoke(mAudioTrack, (Object[]) null) * 1000L / 2;
                    mLatencyUs = Math.max(mLatencyUs, 0);
                } catch (Exception e) {
                    getLatencyMethod = null;
                }
            }
            mLastTimestampSampleTimeUs = systemClockUs;
        }

        if (mAudioTimestampSet) {
            // Calculate the speed-adjusted position using the timestamp (which may be in the future).
            long elapsedSinceTimestampUs = System.nanoTime() / 1000 - (mAudioTimestamp.nanoTime / 1000);
            long elapsedSinceTimestampFrames = elapsedSinceTimestampUs * mSampleRate / 1000000L;
            long elapsedFrames = mAudioTimestamp.framePosition + elapsedSinceTimestampFrames;
            return (elapsedFrames * 1000000L) / mSampleRate;
        } else {
            return (numFramesPlayed * 1000000L) / mSampleRate - mLatencyUs;
        }
    }

    /****
     * @return 返回当前队列中待发送的字节数, 注意float = 4byte, 这里是记录的字节数,
     *          返回-1, 表示获取失败
     */
    public int getNumBytesQueued() {
        if (!isValid()) {
            Logger.INSTANCE.w("NonBlockingAudioTrack::getNumBytesQueued failed:: INVALID");
            return -1;
        }
        return mNumBytesQueued.get();
    }

    public boolean play() {
        if (!isValid()) {
            Logger.INSTANCE.w("NonBlockingAudioTrack::play failed:: INVALID");
            return false;
        }
        mStopped = false;
        mAudioTrack.play();
        return true;
    }

    public boolean stop() {
        if (!isValid()) {
            Logger.INSTANCE.w("NonBlockingAudioTrack::stop failed:: INVALID");
            return false;
        }
        if (mQueue.isEmpty()) {
            mAudioTrack.stop();
            mNumBytesQueued.set(0);
        }
        mStopped = true;
        return true;
    }

    public void pause() {
        if (!isValid()) {
            Logger.INSTANCE.w("NonBlockingAudioTrack::pause failed:: INVALID");
            return;
        }
        lastProcessUtteranceId = "";
        mAudioTrack.pause();
    }

    /***
     *
     * @return 返回是否有效
     */
    public boolean isValid() {
        return mHasInit && !mRelease && mAudioTrack != null;
    }

    /****
     * 清空缓冲队列,
     * 清理掉AudioTrack的未播放数据
     */
    public void flush() {
        if (!isValid()) {
            Logger.INSTANCE.w("NonBlockingAudioTrack::flush failed:: INVALID");
            return;
        }
        if (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            return;
        }
        mAudioTrack.flush();
        mQueue.clear();
        mNumBytesQueued.set(0);
        mStopped = false;
    }

    public void release() {
        if (!isValid()) {
            Logger.INSTANCE.w("NonBlockingAudioTrack::release failed:: INVALID");
            return;
        }

        mRelease = true;
        mQueue.clear();
        mNumBytesQueued.set(0);
        mLatencyUs = 0;
        mLastTimestampSampleTimeUs = 0;
        if (mAudioTrack != null) {
            mAudioTrack.release();
            mAudioTrack = null;
        }
        mStopped = false;
        mAudioTimestampSet = false;
    }

    private String lastProcessUtteranceId = "";


    /***
     * @return   返回>=0, 继续循环, 当前有写入数据
     *          返回-3, 当前AudioTrack已经无效
     *          返回-2, 当前执行中出现异常报错
     *          返回-1, 当前已被停止播放
     */
    public int process() {
        if (!isValid()) {
            Logger.INSTANCE.w("NonBlockingAudioTrack::process failed:: INVALID");
            lastProcessUtteranceId = "";
            return -3;
        }
        int ret = 1;
        if (!mStopped) {
            try {
                QueueElement element;
                int writeCount = 0;
                while ((element = mQueue.peek()) != null) { //获得顶部元素,但是不从队列中移除
                    if (!isValid()) {
                        Logger.INSTANCE.w("NonBlockingAudioTrack::process failed:: INVALID");
                        ret = -3;
                        break;
                    }

                    String utteranceId = element.utteranceId;
                    int written = mAudioTrack.write(element.data,
                            element.offset,
                            element.size,
                            AudioTrack.WRITE_NON_BLOCKING);
                    if (written < 0) {
                        throw new AudioWriteException("Audiotrack.write() failed.");
                    }
                    if (written == 0) {
                        break; // 下次 process 再试
                    }
                    writeCount += written;
                    if (!utteranceId.isEmpty() && !utteranceId.equals(lastProcessUtteranceId) && mPlaySentenceCallback != null) {
                        mPlaySentenceCallback.onStart(utteranceId);
                    }
                    lastProcessUtteranceId = utteranceId;

                    mNumBytesQueued.addAndGet(-written * 4);
                    element.size -= written;
                    element.offset += written;
                    if (element.size != 0) {
                        break;
                    }
                    mQueue.poll(); //移除顶部元素
                    if (!utteranceId.isEmpty() && mPlaySentenceCallback != null) {
                        mPlaySentenceCallback.onEnd(utteranceId);
                    }
                }

                if (mStopped) {  //stop 时的处理
                    ret = -1;
                    if (mAudioTrack != null && !mRelease) {
                        mAudioTrack.stop();
                    }
                    mNumBytesQueued.set(0);
                    mStopped = false;
                    lastProcessUtteranceId = "";
                } else {
                    ret = writeCount;
                }
            } catch (AudioWriteException ex) {
                Logger.INSTANCE.w("NonBlockingAudioTrack::" + ex.getMessage());
                ret = -2;
            } catch (Exception ex2) {
                Logger.INSTANCE.w("NonBlockingAudioTrack::" + ex2.getMessage());
                ret = -2;
            }
        } else {
            ret = -1;
        }

        if (ret < 0) {
            if (mAudioTrack != null && !mRelease) {
                mAudioTrack.stop();
            }
            mQueue.clear();
            mNumBytesQueued.set(0);
            mStopped = false;
            lastProcessUtteranceId = "";
        }

        return ret;
    }

    public int getPlayState() {
        if (!isValid()) {
            Logger.INSTANCE.w("NonBlockingAudioTrack::getPlayState failed:: INVALID");
            return 0;
        }
        return mAudioTrack.getPlayState();
    }

    /***
     * 如果队列已满或者缓冲量已满,则会阻塞或者写入失败, 超时则返回写入失败
     * @param data 写入的数据
     * @param size 写入数据量
     * @param timeoutMs 超时时长
     * @return true 写入成功, false: 写入失败
     */
    public boolean write(String utteranceId, float[] data, int size, long timeoutMs) {
        if (!isValid()) {
            Logger.INSTANCE.w("NonBlockingAudioTrack::write failed:: INVALID");
            return false;
        }

        if (mNumBytesQueued.get() > MAX_QUEUE_BYTES) {
            return false;
        }

        QueueElement element = new QueueElement();
        element.utteranceId = utteranceId;
        element.data = Arrays.copyOf(data, size);
        element.size = size;
        element.offset = 0;

        try {
            if (!mQueue.offer(element, timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        // accumulate size written to queue
        mNumBytesQueued.addAndGet(size * 4); //1个float 4个字节
        return true;
    }

    public static class AudioWriteException extends Exception {
        public AudioWriteException(String message) {
            super(message);
        }
    }

    public boolean canReceive() {
        return mNumBytesQueued.get() < MAX_QUEUE_BYTES &&
                mQueue.size() < MAX_QUEUE_CAPACITY;
    }

    public void setPlaySentenceCallback(PlaySentenceCallback callback) {
        mPlaySentenceCallback = callback;
    }
}

