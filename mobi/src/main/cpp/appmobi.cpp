#include <jni.h>
#include <string>
#include "util/string_ext.h"
#include <algorithm>
#include <vector>
#include <string>
#include <sstream>
#include "util/crc_util.h"
#include "util/log.h"
#include "util/mobi_util.h"

//extern "C" JNIEXPORT jstring JNICALL Java_com_wxn_mobi_NativeLib_stringFromJNI(
//        JNIEnv *env,
//        jobject /* this */) {
//    std::string hello = "Hello from C++";
//    return env->NewStringUTF(hello.c_str());
//}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_wxn_mobi_inative_NativeLib_nativeFilesCrc(
        JNIEnv *env,
        jobject thiz,
        jobjectArray paths
) {
    if (!paths || env->GetArrayLength(paths) == 0) {
        return nullptr;
    }

    jsize len = env->GetArrayLength(paths);
    std::vector<std::string> results;
    for (int i = 0; i < len; i++) {
        jstring str = (jstring) (env->GetObjectArrayElement(paths, i));
        if (str != nullptr) {
            const char *nativeStr = env->GetStringUTFChars(str, NULL);
            if (nativeStr != nullptr) {
                results.push_back(nativeStr);

                env->ReleaseStringUTFChars(str, nativeStr);
            }
            env->DeleteLocalRef(str);
        }
    }

    CrcResults crcs;
    crcs.process_files_crc(results, 0, results.size());
//    for (auto crc: crcs.get()) {
//        LOGD("%s file=[%s],crc is [%X]", __func__, crc.filepath.c_str(), crc.crc);
//    }

    jclass objClass = env->FindClass("com/wxn/mobi/data/model/FileCrc");
    if (objClass == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    int length = crcs.get().size();
    jobjectArray result = env->NewObjectArray(length, objClass, nullptr);

    if (result == nullptr) {
        return nullptr;
    }

    for (int i = 0; i < length; i++) {
        jmethodID constructor = env->GetMethodID(objClass, "<init>", "(Ljava/lang/String;I)V");
        if (constructor == nullptr) {
            break;
        }
        std::string path = crcs.get().at(i).filepath;
//        uint32_t crc = crcs.get().at(i).crc;
        int crc = static_cast<int>(crcs.get().at(i).crc);

        jobject item = env->NewObject(objClass, constructor, env->NewStringUTF(path.c_str()), crc);

        if (item == nullptr) {
            break;
        }

        env->SetObjectArrayElement(result, i, item);
    }

    return result;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_wxn_mobi_inative_NativeLib_loadMobi(
        JNIEnv *env,
        jobject thiz,
        jobject context,
        jstring path) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    jclass contextClass = env->GetObjectClass(context);
    jmethodID getFilesDirMethod = env->GetMethodID(contextClass, "getFilesDir", "()Ljava/io/File;");
    //call getFilesDir(), return File object
    jobject filesDirObj = env->CallObjectMethod(context, getFilesDirMethod);

    //call getAbsolutePath(), get full dir path
    jclass fileClass = env->FindClass("java/io/File");
    jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    jstring pathStr = (jstring)env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
    const char *appFileDir = env->GetStringUTFChars(pathStr, NULL);

    std::string coverPath;
//    std::string epubPath;

    std::string title;
    std::string author;
    std::string contributor;

    std::string subject;
    std::string publisher;
    std::string date;

    std::string description;
    std::string review;
    std::string imprint;

    std::string copyright;
    std::string isbn;
    std::string asin;
    std::string language;
    std::string identifier;
    bool isEncrypted = false;


    int ret = mobi_util::loadMobi(nativeStr,
                                  appFileDir,
                                  coverPath,
//                                  epubPath,
                                  title,
                                  author,
                                  contributor,
                                  subject,
                                  publisher,
                                  date,
                                  description,
                                  review,
                                  imprint,
                                  copyright,
                                  isbn,
                                  asin,
                                  language,
                                  identifier,
                                  isEncrypted);
//    LOGD("%s:load mobi cover[%s], epub[%s].", __func__, coverPath.c_str(), epubPath.c_str());
    env->ReleaseStringUTFChars(path, nativeStr);
    env->ReleaseStringUTFChars(pathStr, appFileDir);

    if (ret != SUCCESS) {
        return nullptr;
    }

    jclass infoClazz = env->FindClass("com/wxn/mobi/data/model/MobiInfo");
    jmethodID constructor = env->GetMethodID(infoClazz, "<init>",
                                             "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" \
                                          "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" \
                                          "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" \
                                          "Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;" \
                                          "Ljava/lang/String;ZLjava/lang/String;)V");
    if (constructor == nullptr) {
        return nullptr;
    }

    // 4. 调用构造函数创建对象
    jobject mobiInfoObj = env->NewObject(
            infoClazz,
            constructor,
            env->NewStringUTF(title.c_str()),
            env->NewStringUTF(author.c_str()),
            env->NewStringUTF(contributor.c_str()),

            env->NewStringUTF(subject.c_str()),
            env->NewStringUTF(publisher.c_str()),
            env->NewStringUTF(date.c_str()),

            env->NewStringUTF(description.c_str()),
            env->NewStringUTF(review.c_str()),
            env->NewStringUTF(imprint.c_str()),

            env->NewStringUTF(copyright.c_str()),
            env->NewStringUTF(isbn.c_str()),
            env->NewStringUTF(asin.c_str()),

            env->NewStringUTF(language.c_str()),
            isEncrypted,
            env->NewStringUTF(coverPath.c_str())
    );
    return mobiInfoObj;

//    std::vector<std::string> params;
//    params.push_back(coverPath);
////    params.push_back(epubPath);
//
//    params.push_back(title);
//    params.push_back(author);
//    params.push_back(contributor);
//
//    params.push_back(subject);
//    params.push_back(publisher);
//    params.push_back(date);
//
//    params.push_back(description);
//    params.push_back(review);
//    params.push_back(imprint);
//
//    params.push_back(copyright);
//    params.push_back(isbn);
//    params.push_back(asin);
//
//    params.push_back(language);
//    params.push_back(identifier);
//    if (isEncrypted) {
//        params.push_back("true");
//    } else {
//        params.push_back("false");
//    }
//
//    //构造返回的字符串数组
//    int length = 17;
//    jclass stringClass = env->FindClass("java/lang/String");
//    jobjectArray stringArray = env->NewObjectArray(length, stringClass, NULL);
//    for (int i = 0; i < length; i++) {
//        jstring element = env->NewStringUTF(params[i].c_str());
//        env->SetObjectArrayElement(stringArray, i, element);
//        env->DeleteLocalRef(element);
//    }
//    return stringArray;
}