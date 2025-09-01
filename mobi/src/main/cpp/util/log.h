//
// Created by wxn on 2023/8/27.
//

#ifndef DEMOSIMPLEPLAYER2_LOG_H
#define DEMOSIMPLEPLAYER2_LOG_H
#include <android/log.h>

//#define LOGD(format,...) __android_log_print(ANDROID_LOG_DEBUG, LOGTAG, "%s " format, __func__, ##__VA_ARGS__)
//
//#define LOGD(format,...) printf(LOGTAG " %s "format, __FUNCTION__, ##__VA_ARGS__)

#define TAG "HandyReader"

#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,   TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,    TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,    TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,   TAG, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,   TAG, __VA_ARGS__)

#endif //DEMOSIMPLEPLAYER2_LOG_H
