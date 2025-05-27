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
    jstring pathStr = (jstring) env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
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
}

//extern "C"
//JNIEXPORT jstring JNICALL
//Java_com_wxn_mobi_inative_NativeLib_convertToEpub(JNIEnv *env,
//                                                  jobject thiz,
//                                                  jobject context,
//                                                  jstring path) {
//    const char *nativeStr = env->GetStringUTFChars(path, NULL);
//    jclass contextClass = env->GetObjectClass(context);
//    jmethodID getCacheDirMethod = env->GetMethodID(contextClass, "getCacheDir", "()Ljava/io/File;");
//    //call getCacheDir(), return File object
//    jobject cacheDirObj = env->CallObjectMethod(context, getCacheDirMethod);
//
//    //call getAbsolutePath(), get full dir path
//    jclass fileClass = env->FindClass("java/io/File");
//    jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
//    jstring pathStr = (jstring)env->CallObjectMethod(cacheDirObj, getAbsolutePathMethod);
//    const char *appCacheDir = env->GetStringUTFChars(pathStr, NULL);
//
//    std::string epub_path;
//    mobi_util::convertToEpub(nativeStr, appCacheDir, epub_path);
//    LOGD("convertToEpub:target epub_path is [%s]", epub_path.c_str());
//
//    env->ReleaseStringUTFChars(path, nativeStr);
//    env->ReleaseStringUTFChars(pathStr, appCacheDir);
//
//    return env->NewStringUTF(epub_path.c_str());
//}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_wxn_mobi_inative_NativeLib_getChapters(JNIEnv *env, jobject thiz, jobject context, jlong book_id, jstring path) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    std::vector<NavPoint> vectors;
    int ret = mobi_util::getChapters(env, book_id, nativeStr, vectors);
    if (ret != 1) {
        return nullptr;
    }
    jclass objClass = env->FindClass("com/wxn/base/bean/BookChapter");
    if (objClass == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    int length = vectors.size();
    if (length <= 0) {
        LOGE("%s failed, vectors is empty", __func__);
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(length, objClass, nullptr);
    if (result == nullptr) {
        LOGE("%s failed, jArray is null", __func__);
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(objClass, "<init>", "(JLjava/lang/String;Ljava/lang/String;JILjava/lang/String;JLjava/lang/String;JLjava/lang/String;Ljava/lang/String;I)V");
    if (constructor == nullptr) {
        LOGE("%s failed, BookChapter's constructor is null", __func__);
        return nullptr;
    }

    std::sort(vectors.begin(), vectors.end()); //根据序号自动排序

    int index = 0;
    for(auto point = vectors.begin(); point != vectors.end(); point++) {
        std::string id = (*point).id;
        int playOrder = (*point).playOrder;
        std::string content = (*point).text;
        std::string src = (*point).src;
        std::string parentId = (*point).parentId;

        jobject item = env->NewObject(objClass, constructor,
                       0L,
                       env->NewStringUTF(id.c_str()),
                       env->NewStringUTF(parentId.c_str()),
                       book_id,
                       playOrder - 1,
                        env->NewStringUTF(content.c_str()),
                        0L,
                        env->NewStringUTF(""),
                        0L,
                        env->NewStringUTF(""),
                        env->NewStringUTF(src.c_str()),
                        length
                       );
        if (item == nullptr) {
            LOGE("%s create BookChapter failed", __func__);
            return nullptr;
        }
        env->SetObjectArrayElement(result, index, item);
        index++;
    }

    env->ReleaseStringUTFChars(path, nativeStr);

    return result;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_wxn_mobi_inative_NativeLib_getChapter(JNIEnv *env, jobject thiz, jobject context, jstring path, jobject chapter) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    jclass contextClass = env->GetObjectClass(context);
    jmethodID getFilesDirMethod = env->GetMethodID(contextClass, "getFilesDir", "()Ljava/io/File;");
    //call getFilesDir(), return File object
    jobject filesDirObj = env->CallObjectMethod(context, getFilesDirMethod);
    //call getAbsolutePath(), get full dir path
    jclass fileClass = env->FindClass("java/io/File");
    jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    jstring pathStr = (jstring) env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
    const char *appFileDir = env->GetStringUTFChars(pathStr, NULL);

    jclass chapterClass = env->GetObjectClass(chapter);
    jfieldID fieldChapterId = env->GetFieldID(chapterClass, "chapterId", "Ljava/lang/String;");
    jfieldID fieldParentChapterId =env->GetFieldID(chapterClass, "parentChapterId", "Ljava/lang/String;");
    jfieldID fieldBookId =env->GetFieldID(chapterClass, "bookId", "J");
    jfieldID fieldChapterIndex =env->GetFieldID(chapterClass, "chapterIndex", "I");
    jfieldID fieldChapterName =env->GetFieldID(chapterClass, "chapterName", "Ljava/lang/String;");
    jfieldID fieldSrc =env->GetFieldID(chapterClass, "srcName", "Ljava/lang/String;");
    jfieldID fieldChapterSize =env->GetFieldID(chapterClass, "chaptersSize", "I");

    jstring chapterId = (jstring)env->GetObjectField(chapter, fieldChapterId);
    jstring parentChapterId = (jstring)env->GetObjectField(chapter, fieldParentChapterId);
    jlong bookId = env->GetLongField(chapter, fieldBookId);
    jint chapterIndex = env->GetIntField(chapter, fieldChapterIndex);
    jstring chapterName = (jstring)env->GetObjectField(chapter, fieldChapterName);
    jstring src = (jstring)env->GetObjectField(chapter, fieldSrc);
    jint chapterSize = env->GetIntField(chapter, fieldChapterSize);

    const char* chapterIdStr = env->GetStringUTFChars(chapterId, nullptr);
    const char* parentChapterIdStr = env->GetStringUTFChars(parentChapterId, nullptr);
    const char* chapterNameStr = env->GetStringUTFChars(chapterName, nullptr);
    const char* srcStr = env->GetStringUTFChars(src, nullptr);

    NavPoint point;
    point.id = chapterIdStr;
    point.text = chapterNameStr;
    point.playOrder = chapterIndex + 1;
    point.src = srcStr;
    point.parentId = parentChapterIdStr;
    long book_id = bookId;
    int chapter_size = chapterSize;
    LOGD("%s:chapterId=%s,text=%s,playOrder=%d,src=%s,book_id=%ld,chapter_size=%d", __func__, chapterIdStr, chapterNameStr, point.playOrder, srcStr, bookId, chapter_size);

    std::vector<DocText> docTexts;
    if (1 != mobi_util::getChapter(env, book_id, nativeStr, appFileDir, point, docTexts)) {
        return nullptr;
    }

    if (docTexts.empty()) {
        return nullptr;
    }

    jclass objClass = env->FindClass("com/wxn/base/bean/ReaderText$Text");
    if (objClass == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    int length = docTexts.size();
    jobjectArray result = env->NewObjectArray(length, objClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }
    jmethodID constructor = env->GetMethodID(objClass, "<init>", "(Ljava/lang/String;Ljava/util/List;)V");
    if (constructor == nullptr) {
        return nullptr;
    }

    jclass listClass = env->FindClass("java/util/ArrayList");
    if (listClass == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
    if (listConstructor == nullptr) {
        return nullptr;
    }
    jmethodID listAdd = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    if (listAdd == nullptr) {
        return nullptr;
    }

    jclass textTagClass = env->FindClass("com/wxn/base/bean/TextTag");
    if (textTagClass == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    jmethodID textTagConstructor = env->GetMethodID(textTagClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;Ljava/lang/String;)V");
    if (textTagConstructor == nullptr) {
        return nullptr;
    }

    for(int i=0; i< length; i++) {
        auto item = docTexts[i];
        jobject list = env->NewObject(listClass, listConstructor);
        if(!item.tagInfos.empty()) {
            for(auto& tag : item.tagInfos) {
                jobject textTag = env->NewObject(textTagClass, textTagConstructor,
                                                 env->NewStringUTF(tag.uuid.c_str()),
                                                 env->NewStringUTF(tag.anchor_id.c_str()),
                                                 env->NewStringUTF(tag.name.c_str()),
                                                 tag.startPos,
                                                 tag.endPos,
                                                 env->NewStringUTF(tag.parent_uuid.c_str()),
                                                 env->NewStringUTF(tag.params.c_str())
                                                 );
                env->CallBooleanMethod(list, listAdd, textTag);
            }
        }
        jobject readerText = env->NewObject(objClass, constructor,
                       env->NewStringUTF(item.text.c_str()),
                       list);
        env->SetObjectArrayElement(result, i, readerText);
    }

    env->ReleaseStringUTFChars(path, nativeStr);
    env->ReleaseStringUTFChars(pathStr, appFileDir);

    env->ReleaseStringUTFChars(chapterId, chapterIdStr);
    env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
    env->ReleaseStringUTFChars(chapterName, chapterNameStr);
    env->ReleaseStringUTFChars(src, srcStr);

    return result;
}