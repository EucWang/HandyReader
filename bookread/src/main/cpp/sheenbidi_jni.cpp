/*
 * JNI bridge between Kotlin and SheenBidi (Unicode Bidirectional Algorithm).
 *
 * 接收 Java String（UTF-16，零拷贝传 SBCodepointSequence），返回 [基级, run 列表]：
 *  IntArray([baseLevel,
 *            run0_offset, run0_length, run0_level,
 *            run1_offset, run1_length, run1_level, ...])
 * [0] = 段落基级（SBParagraphGetBaseLevel，P2-P3 解析结果，0=LTR / 1=RTL），run0 起始于下标 1。
 */
#include <jni.h>
#include <SheenBidi/SheenBidi.h>
#include <android/log.h>
#include <stdlib.h>

#define LOG_TAG "SheenBidiJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


/*
 * 共享主体：对整段文本（UTF-16 零拷贝）应用 Unicode Bidi Algorithm，
 * 返回 [基级, run0(offset,length,level), run1(...), ...]（视觉顺序）。
 *
 * baseLevel 三种取值语义（SBAlgorithm.h:93-95）：
 *   SBLevelDefaultLTR / SBLevelDefaultRTL —— 让库按 首强嗅探
 *       （无强字符时按 Default 兜底），返回
 *   0 / 1（具体级别）—— 强制基级，返回传入值。
 */
static jintArray bidiRunsImpl(JNIEnv *env, jstring text, SBLevel baseLevel) {
    if (text == nullptr) {
        return env->NewIntArray(0);
    }
    // ★ 零拷贝：Java String 内部就是 UTF-16 char[]，SBCodepointSequence UTF-16 模式
    //   直接消费 uint16_t*。GetStringChars 返回 jchar*（unsigned 16-bit），
    //   与 SBCodepointSequence 的 uint16_t* 二进制兼容。
    const jchar *chars = env->GetStringChars(text, nullptr);
    if (chars == nullptr) {
        LOGE("GetStringChars returned null");
        return env->NewIntArray(0);
    }
    const jsize len = env->GetStringLength(text);

    // 构造 SBCodepointSequence（UTF-16, native endianness）
    SBCodepointSequence sequence;
    sequence.stringEncoding = SBStringEncodingUTF16;
    sequence.stringBuffer   = chars;
    sequence.stringLength   = (SBUInteger)len;

    jintArray result = env->NewIntArray(0);

    // SBAlgorithm：计算逐字 Bidi 类型，识别段落边界
    SBAlgorithmRef algorithm = SBAlgorithmCreate(&sequence);

    if (algorithm != nullptr) {
        SBParagraphRef paragraph = SBAlgorithmCreateParagraph(
                algorithm, 0, (SBUInteger)len, baseLevel);
        if (paragraph != nullptr) {
            SBUInteger paraLen = SBParagraphGetLength(paragraph);

            // SBLine：对整段（单行场景）应用，得到视觉 run 列表
            SBLineRef line = SBParagraphCreateLine(paragraph, 0, paraLen);
            if (line != nullptr) {
                SBUInteger runCount = SBLineGetRunCount(line);
                const SBRun *runs = SBLineGetRunsPtr(line);

                // 输出 IntArray：[0]=基级，[1..] 每 run 3 个 int
                SBUInteger outLen = runCount * 3 + 1;
                result = env->NewIntArray((jsize)outLen);
                if (result != nullptr && runs != nullptr &&  outLen <= 65535) {
                    jint *buf = (jint *)malloc(outLen * sizeof(jint));
                    if (buf != nullptr) {
                        buf[0] = (jint)SBParagraphGetBaseLevel(paragraph);
                        for (SBUInteger i = 0; i < runCount; i++) {
                            buf[i * 3 + 1]     = (jint)runs[i].offset;
                            buf[i * 3 + 2] = (jint)runs[i].length;
                            buf[i * 3 + 3] = (jint)runs[i].level;
                        }
                        env->SetIntArrayRegion(result, 0, (jsize)outLen, buf);
                        free(buf);
                    } else {
                        LOGE("malloc failed for %zu ints", outLen);
                    }
                }
                SBLineRelease(line);
            } else {
                LOGE("SBParagraphCreateLine returned null");
            }
            SBParagraphRelease(paragraph);
        } else {
            LOGE("SBAlgorithmCreateParagraph returned null");
        }
        SBAlgorithmRelease(algorithm);
    } else {
        LOGE("SBAlgorithmCreate returned null");
    }

    env->ReleaseStringChars(text, chars);
    return result;
}

extern "C" {


/*
 * Class:     com_wxn_bookread_jni_SheenBidiNative
 * Method:    bidiRunsNative
 * Signature: (Ljava/lang/String;Z)[I
 *
 * @param text      段落文本（UTF-16）
 * @param baseRtl   true = 段落基方向 RTL (SBLevelDefaultRTL)
 *                  false = 段落基方向 LTR (SBLevelDefaultLTR)
 * @return IntArray：[0]=段落基级（P2-P3），[1..] 每 3 个元素一个 run（offset, length, level），视觉顺序。
 *         失败返回 length=0 的空数组。
 */
JNIEXPORT jintArray JNICALL
Java_com_wxn_bookread_jni_SheenBidiNative_bidiRunsNative(
        JNIEnv *env,
        jobject thiz,
        jstring text,
        jboolean baseRtl) {
    return bidiRunsImpl(env, text,
                        baseRtl ? SBLevelDefaultRTL : SBLevelDefaultLTR);
}

/*
 * Class:     com_wxn_bookread_jni_SheenBidiNative
 * Method:    nativeVersion
 * Signature: ()Ljava/lang/String;
 *
 * 返回 SheenBidi 版本号，用于启动期确认 .so 正确加载。
 */
JNIEXPORT jstring JNICALL
Java_com_wxn_bookread_jni_SheenBidiNative_nativeVersion(
        JNIEnv *env,  jobject thiz) {
    return env->NewStringUTF(SHEENBIDI_VERSION_STRING);
}

}  // extern "C"
