/*
 * JNI bridge between Kotlin and SheenBidi (Unicode Bidirectional Algorithm).
 *
 * 接收 Java String（UTF-16，零拷贝传 SBCodepointSequence），返回视觉 run 列表：
 *  IntArray([run0_offset, run0_length, run0_level,
 *            run1_offset, run1_length, run1_level, ...])
 */
#include <jni.h>
#include <SheenBidi/SheenBidi.h>
#include <android/log.h>
#include <stdlib.h>

#define LOG_TAG "SheenBidiJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

/*
 * Class:     com_wxn_bookread_jni_SheenBidiNative
 * Method:    bidiRunsNative
 * Signature: (Ljava/lang/String;Z)[I
 *
 * @param text      段落文本（UTF-16）
 * @param baseRtl   true = 段落基方向 RTL (SBLevelDefaultRTL)
 *                  false = 段落基方向 LTR (SBLevelDefaultLTR)
 * @return IntArray，每 3 个元素一个 run（offset, length, level），视觉顺序。
 *         失败返回 length=0 的空数组。
 */
JNIEXPORT jintArray JNICALL
Java_com_wxn_bookread_jni_SheenBidiNative_bidiRunsNative(
        JNIEnv *env, jobject thiz, jstring text, jboolean baseRtl) {

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
        // SBParagraph：对整段应用 P1-P3 + X1-X9 + I1-I2，得到逐字 embedding level
        // baseLevel 用 SBLevelDefaultRTL/LTR 让库按 P2-P3 自动判定（除非全弱字符）
        SBLevel baseLevel = baseRtl ? SBLevelDefaultRTL : SBLevelDefaultLTR;
        SBParagraphRef paragraph = SBAlgorithmCreateParagraph(
                algorithm, 0, (SBUInteger)len, baseLevel);
        if (paragraph != nullptr) {
            SBUInteger paraLen = SBParagraphGetLength(paragraph);

            // SBLine：对整段（单行场景）应用 L1-L2，得到视觉 run 列表
            SBLineRef line = SBParagraphCreateLine(paragraph, 0, paraLen);
            if (line != nullptr) {
                SBUInteger runCount = SBLineGetRunCount(line);
                const SBRun *runs = SBLineGetRunsPtr(line);

                // 输出 IntArray：每 run 3 个 int
                SBUInteger outLen = runCount * 3;
                result = env->NewIntArray((jsize)outLen);
                if (result != nullptr && runs != nullptr &&  outLen <= 65535) {
                    jint *buf = (jint *)malloc(outLen * sizeof(jint));
                    if (buf != nullptr) {
                        for (SBUInteger i = 0; i < runCount; i++) {
                            buf[i * 3]     = (jint)runs[i].offset;
                            buf[i * 3 + 1] = (jint)runs[i].length;
                            buf[i * 3 + 2] = (jint)runs[i].level;
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
