//
// Created by MAC on 2025/5/29.
//

#ifndef U_READER2_BITMAP_EXT_H
#define U_READER2_BITMAP_EXT_H

#include "log.h"
#include <android/bitmap.h>
#include <android/imagedecoder.h>

class bitmap_ext {

public:
    /****
     * 获得图片的宽高信息
     * @param env [in] JNIEnv *
     * @param path [in] 图片绝对路径
     * @param width [out] 输出图片宽度
     * @param height [out] 输出图片高度
     * @return return 0 if error， 1 success
     */
    static int getImageOption(JNIEnv *jniEnv, const char *path, int *width, int *height);
};


#endif //U_READER2_BITMAP_EXT_H
