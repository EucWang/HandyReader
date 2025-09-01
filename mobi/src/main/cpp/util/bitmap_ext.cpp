//
// Created by MAC on 2025/5/29.
//

#include "bitmap_ext.h"

int bitmap_ext::getImageOption(JNIEnv *jniEnv, const char *path, int *width, int *height) {
    int outWidth = 0;
    int outHeight = 0;
//    if (getVersion() >= 30) {
//        int fd = open(path, O_RDONLY);
//        if (fd == -1) {
//            close(fd);
//            LOGE("%s:failed,open path[%s] failed", __func__, path);
//            return 0;
//        }
//        AImageDecoder* decoder;
//        int ret = AImageDecoder_createFromFd(fd, &decoder);
//        if (ret != ANDROID_IMAGE_DECODER_SUCCESS) {
//            LOGE("%s:failed,decode image[%s] error", __func__, path);
//            close(fd);
//            return 0;
//        }
//        const AImageDecoderHeaderInfo* info = AImageDecoder_getHeaderInfo(decoder);
//        if (info != nullptr) {
//            outWidth = AImageDecoderHeaderInfo_getWidth(info);
//            outHeight = AImageDecoderHeaderInfo_getHeight(info);
//            AImageDecoder_delete(decoder);
//        }
//        close(fd);
//    } else {
    if (jniEnv == nullptr) {
        return 0;
    }
    jclass optionClass = jniEnv->FindClass("android/graphics/BitmapFactory$Options");
    if (optionClass == nullptr) {
        return 0;
    }
    jmethodID constructor = jniEnv->GetMethodID(optionClass, "<init>", "()V");
    if (constructor == nullptr) {
        return 0;
    }
    jobject objOption = jniEnv->NewObject(optionClass, constructor);
    if (objOption == nullptr) {
        return 0;
    }
    jfieldID fieldInJustDecodeBounds = jniEnv->GetFieldID(optionClass, "inJustDecodeBounds", "Z");
    jfieldID fieldInSampleSize = jniEnv->GetFieldID(optionClass, "inSampleSize", "I");
    jniEnv->SetBooleanField(objOption, fieldInJustDecodeBounds, true);
    jniEnv->SetIntField(objOption, fieldInSampleSize, 2);

    jclass factoryClass = jniEnv->FindClass("android/graphics/BitmapFactory");
    jmethodID decodeFileMethod = jniEnv->GetStaticMethodID(factoryClass, "decodeFile",
                                                           "(Ljava/lang/String;Landroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;");
    jobject objBitmap = jniEnv->CallStaticObjectMethod(factoryClass, decodeFileMethod,
                                                       jniEnv->NewStringUTF(path),
                                                       objOption);
    jfieldID fieldWidth = jniEnv->GetFieldID(optionClass, "outWidth", "I");
    jfieldID fieldHeight = jniEnv->GetFieldID(optionClass, "outHeight", "I");
    outWidth = jniEnv->GetIntField(objOption, fieldWidth);
    outHeight = jniEnv->GetIntField(objOption, fieldHeight);
//    }
    LOGD("%s:get image[%s] width=%d,height=%d", __func__, path, outWidth, outHeight);
    *width = outWidth;
    *height = outHeight;
    return 1;
}
