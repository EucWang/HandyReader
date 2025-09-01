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
#include "util/app_ext.h"
#include "util/epub_util.h"
#include "util/fb2_util.h"
#include "util/file_searcher.h"
#include <memory>

std::shared_ptr<mobi_util> mobiutil = nullptr;
std::shared_ptr<epub_util> epubutil = nullptr;
std::shared_ptr<fb2_util> fb2util = nullptr;

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

void create_mobi_util(long book_id, const char *path) {
    if (mobiutil == nullptr) {
        long bookid = book_id;
        std::string bookpath = path;
        mobiutil = std::shared_ptr<mobi_util>(new mobi_util(bookid, bookpath));
    } else {
        if (mobiutil->bookid() != book_id || mobiutil->bookpath() != path) {
            mobiutil.reset(new mobi_util(book_id, path));
        }
    }
}

void create_epub_util(long book_id, const char *path) {
    if (epubutil == nullptr) {
        long bookid = book_id;
        std::string bookpath = path;
        epubutil = std::shared_ptr<epub_util>(new epub_util(bookid, bookpath));
    } else {
        if (epubutil->bookid() != book_id || epubutil->bookpath() != path) {
            epubutil.reset(new epub_util(book_id, path));
        }
    }
}

void create_fb2_util(long book_id, const char *path) {
    if (fb2util == nullptr) {
        long bookid = book_id;
        std::string bookpath = path;
        fb2util = std::shared_ptr<fb2_util>(new fb2_util(bookid, bookpath));
    } else {
        if (fb2util->bookid() != book_id || fb2util->bookpath() != path) {
            fb2util.reset(new fb2_util(book_id, path));
        }
    }
}


int create_util(long book_id, const char* path, int type) {
    if (type == 1) {
        create_mobi_util(book_id, path);
    } else if (type == 2) {
        create_epub_util(book_id, path);
    } else if (type == 3) {
        create_fb2_util(book_id, path);
    } else {
        LOGE("%s unknown type[%d]", __func__, type);
        return 0;
    }
    return 1;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_wxn_mobi_inative_NativeLib_loadMobi(
        JNIEnv *env,
        jobject thiz,
        jobject context,
        jstring path) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    if (app_ext::appFileDir.empty()) {
        jclass contextClass = env->GetObjectClass(context);
        jmethodID getFilesDirMethod = env->GetMethodID(contextClass, "getFilesDir", "()Ljava/io/File;");
        //call getFilesDir(), return File object
        jobject filesDirObj = env->CallObjectMethod(context, getFilesDirMethod);

        //call getAbsolutePath(), get full dir path
        jclass fileClass = env->FindClass("java/io/File");
        jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
        jstring pathStr = (jstring) env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
        const char *appFileDir = env->GetStringUTFChars(pathStr, NULL);
        app_ext::appFileDir = appFileDir;
        env->ReleaseStringUTFChars(pathStr, appFileDir);
    }
    if (app_ext::appCacheDir.empty()) {
        jclass contextClass = env->GetObjectClass(context);
        jmethodID getCacheDirMethod = env->GetMethodID(contextClass, "getCacheDir", "()Ljava/io/File;");
        //call getFilesDir(), return File object
        jobject filesDirObj = env->CallObjectMethod(context, getCacheDirMethod);

        //call getAbsolutePath(), get full dir path
        jclass fileClass = env->FindClass("java/io/File");
        jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
        jstring pathStr = (jstring) env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
        const char *appCacheDir = env->GetStringUTFChars(pathStr, NULL);
        app_ext::appCacheDir = appCacheDir;
        env->ReleaseStringUTFChars(pathStr, appCacheDir);
    }

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

    if (ret != SUCCESS) {
        return nullptr;
    }

    jclass infoClazz = env->FindClass("com/wxn/mobi/data/model/MetaInfo");
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


extern "C" JNIEXPORT jobject JNICALL
Java_com_wxn_mobi_inative_NativeLib_loadEpub(
        JNIEnv *env,
        jobject thiz,
        jobject context,
        jstring path) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    if (app_ext::appFileDir.empty()) {
        jclass contextClass = env->GetObjectClass(context);
        jmethodID getFilesDirMethod = env->GetMethodID(contextClass, "getFilesDir", "()Ljava/io/File;");
        //call getFilesDir(), return File object
        jobject filesDirObj = env->CallObjectMethod(context, getFilesDirMethod);

        //call getAbsolutePath(), get full dir path
        jclass fileClass = env->FindClass("java/io/File");
        jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
        jstring pathStr = (jstring) env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
        const char *appFileDir = env->GetStringUTFChars(pathStr, NULL);
        app_ext::appFileDir = appFileDir;
        env->ReleaseStringUTFChars(pathStr, appFileDir);
    }
    if (app_ext::appCacheDir.empty()) {
        jclass contextClass = env->GetObjectClass(context);
        jmethodID getCacheDirMethod = env->GetMethodID(contextClass, "getCacheDir", "()Ljava/io/File;");
        //call getFilesDir(), return File object
        jobject filesDirObj = env->CallObjectMethod(context, getCacheDirMethod);

        //call getAbsolutePath(), get full dir path
        jclass fileClass = env->FindClass("java/io/File");
        jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
        jstring pathStr = (jstring) env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
        const char *appCacheDir = env->GetStringUTFChars(pathStr, NULL);
        app_ext::appCacheDir = appCacheDir;
        env->ReleaseStringUTFChars(pathStr, appCacheDir);
    }
    std::string coverPath;

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

    int ret = epub_util::load_epub(nativeStr,
                                   coverPath,
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

    if (ret != 1) {
        return nullptr;
    }

    jclass infoClazz = env->FindClass("com/wxn/mobi/data/model/MetaInfo");
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
Java_com_wxn_mobi_inative_NativeLib_getChapters(JNIEnv *env, jobject thiz, jobject context, jlong book_id, jstring path, jint type) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    if (create_util(book_id, nativeStr, type) != 1) {
        return nullptr;
    }

    std::vector<NavPoint> vectors;
    int ret = 0;
    if (type == 1) {
        ret = mobiutil->getChapters(vectors);
    } else if (type == 2) {
        ret = epubutil->getChapters(vectors);
    } else if (type == 3) {
        ret = fb2util->getChapters(vectors);
    } else {
        LOGE("%s unknown type[%d]", __func__, type);
        return nullptr;
    }
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

    jmethodID constructor = env->GetMethodID(objClass, "<init>",
                                             "(JLjava/lang/String;Ljava/lang/String;JILjava/lang/String;JLjava/lang/String;JLjava/lang/String;Ljava/lang/String;IJJJF)V");
    if (constructor == nullptr) {
        LOGE("%s failed, BookChapter's constructor is null", __func__);
        return nullptr;
    }

    std::sort(vectors.begin(), vectors.end()); //根据序号自动排序

    int index = 0;
    for (auto point = vectors.begin(); point != vectors.end(); point++) {
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
                                      length,
                                      0L,
                                      0L,
                                      0L,
                                      0.0f
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
Java_com_wxn_mobi_inative_NativeLib_getChapter(JNIEnv *env, jobject thiz, jobject context, jstring path, jobject chapter, jint type) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    if (app_ext::appFileDir.empty()) {
        jclass contextClass = env->GetObjectClass(context);
        jmethodID getFilesDirMethod = env->GetMethodID(contextClass, "getFilesDir", "()Ljava/io/File;");
        //call getFilesDir(), return File object
        jobject filesDirObj = env->CallObjectMethod(context, getFilesDirMethod);
        //call getAbsolutePath(), get full dir path
        jclass fileClass = env->FindClass("java/io/File");
        jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
        jstring pathStr = (jstring) env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
        const char *appFileDir = env->GetStringUTFChars(pathStr, NULL);
        app_ext::appFileDir = appFileDir;
        env->ReleaseStringUTFChars(pathStr, appFileDir);
    }
    if (app_ext::appCacheDir.empty()) {
        jclass contextClass = env->GetObjectClass(context);
        jmethodID getCacheDirMethod = env->GetMethodID(contextClass, "getCacheDir", "()Ljava/io/File;");
        //call getFilesDir(), return File object
        jobject filesDirObj = env->CallObjectMethod(context, getCacheDirMethod);

        //call getAbsolutePath(), get full dir path
        jclass fileClass = env->FindClass("java/io/File");
        jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
        jstring pathStr = (jstring) env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
        const char *appCacheDir = env->GetStringUTFChars(pathStr, NULL);
        app_ext::appCacheDir = appCacheDir;
        env->ReleaseStringUTFChars(pathStr, appCacheDir);
    }
    jclass chapterClass = env->GetObjectClass(chapter);
    jfieldID fieldChapterId = env->GetFieldID(chapterClass, "chapterId", "Ljava/lang/String;");
    jfieldID fieldParentChapterId = env->GetFieldID(chapterClass, "parentChapterId", "Ljava/lang/String;");
    jfieldID fieldBookId = env->GetFieldID(chapterClass, "bookId", "J");
    jfieldID fieldChapterIndex = env->GetFieldID(chapterClass, "chapterIndex", "I");
    jfieldID fieldChapterName = env->GetFieldID(chapterClass, "chapterName", "Ljava/lang/String;");
    jfieldID fieldSrc = env->GetFieldID(chapterClass, "srcName", "Ljava/lang/String;");
    jfieldID fieldChapterSize = env->GetFieldID(chapterClass, "chaptersSize", "I");

    jstring chapterId = (jstring) env->GetObjectField(chapter, fieldChapterId);
    jstring parentChapterId = (jstring) env->GetObjectField(chapter, fieldParentChapterId);
    jlong bookId = env->GetLongField(chapter, fieldBookId);
    jint chapterIndex = env->GetIntField(chapter, fieldChapterIndex);
    jstring chapterName = (jstring) env->GetObjectField(chapter, fieldChapterName);
    jstring src = (jstring) env->GetObjectField(chapter, fieldSrc);
    jint chapterSize = env->GetIntField(chapter, fieldChapterSize);

    const char *chapterIdStr = env->GetStringUTFChars(chapterId, nullptr);
    const char *parentChapterIdStr = env->GetStringUTFChars(parentChapterId, nullptr);
    const char *chapterNameStr = env->GetStringUTFChars(chapterName, nullptr);
    const char *srcStr = env->GetStringUTFChars(src, nullptr);

    NavPoint point;
    point.id = chapterIdStr;
    point.text = chapterNameStr;
    point.playOrder = chapterIndex + 1;
    point.src = srcStr;
    point.parentId = parentChapterIdStr;
    long book_id = bookId;
    int chapter_size = chapterSize;
    LOGD("%s:chapterId=%s,text=%s,playOrder=%d,src=%s,book_id=%ld,chapter_size=%d", __func__, chapterIdStr, chapterNameStr, point.playOrder, srcStr, bookId,
         chapter_size);

    if (create_util(book_id, nativeStr, type) != 1) {
        return nullptr;
    }

    std::vector<DocText> docTexts;
    int ret = 0;
    if (type == 1) {
        ret = mobiutil->getChapter(env, book_id, nativeStr, point, docTexts);
    } else if (type == 2) {
        ret = epubutil->getChapter(env, book_id, nativeStr, point, docTexts);
    } else if (type == 3) {
        ret = fb2util->getChapter(env, book_id, nativeStr, point, docTexts);
    }
    if (ret != 1) {
        return nullptr;
    }

    if (docTexts.empty()) {
        return nullptr;
    }

    jclass objClass = env->FindClass("com/wxn/mobi/data/model/ParagraphData");
    if (objClass == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }

    int length = docTexts.size();
    jobjectArray result = env->NewObjectArray(length, objClass, nullptr);
    if (result == nullptr) {
        return nullptr;
    }
    jmethodID constructor = env->GetMethodID(objClass, "<init>", "([BLjava/util/List;)V");
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
    jmethodID textTagConstructor = env->GetMethodID(textTagClass, "<init>",
                                                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;Ljava/lang/String;)V");
    if (textTagConstructor == nullptr) {
        return nullptr;
    }

    for (int i = 0; i < length; i++) {
        auto item = docTexts[i];
        jobject list = env->NewObject(listClass, listConstructor);
        if (!item.tagInfos.empty()) {
            for (auto &tag: item.tagInfos) {
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

        const char* ch_text = item.text.c_str();
        size_t length = item.text.length();

        jbyteArray byteArray = env->NewByteArray(length);
        if (byteArray == nullptr) {
            return nullptr;
        }
        jbyte *bytes = env->GetByteArrayElements(byteArray, nullptr);
        if (bytes == nullptr) {
            return nullptr;
        }
        memcpy(bytes, ch_text, length);
        env->ReleaseByteArrayElements(byteArray, bytes, 0);

//        jobject readerText = env->NewObject(objClass, constructor,
//                                            clientStringFromStdString(env, item.text),
//                                            list);
        jobject paragraph_data = env->NewObject(objClass, constructor, byteArray, list);

        env->SetObjectArrayElement(result, i, paragraph_data);
    }

    env->ReleaseStringUTFChars(path, nativeStr);

    env->ReleaseStringUTFChars(chapterId, chapterIdStr);
    env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
    env->ReleaseStringUTFChars(chapterName, chapterNameStr);
    env->ReleaseStringUTFChars(src, srcStr);

    return result;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_wxn_mobi_inative_NativeLib_getCssInfo(JNIEnv *env,
                                               jobject thiz,
                                               jobject context,
                                               jlong book_id,
                                               jobjectArray css_names,
                                               jobjectArray tag_names,
                                               jobjectArray ids_names,
                                               jint type) {

    if (type == 1) {
        if (mobiutil == nullptr || mobiutil.use_count() == 0) {
            LOGE("%s failed, mobiutil is destroyed", __func__);
            return nullptr;
        }
        if (book_id != mobiutil->bookid()) {
            LOGE("%s:failed,is not the same bookid, param book_id[%ld],mobiutil.bookid[%ld]", __func__, book_id, mobiutil->bookid());
            return nullptr;
        }
    } else if (type == 2) {
        if (epubutil == nullptr || epubutil.use_count() == 0) {
            LOGE("%s failed, epubutil is destroyed", __func__);
            return nullptr;
        }
        if (book_id != epubutil->bookid()) {
            LOGE("%s:failed,is not the same bookid, param book_id[%ld],epubutil.bookid[%ld]", __func__, book_id, mobiutil->bookid());
            return nullptr;
        }
    }

    jsize length = env->GetArrayLength(css_names);
    std::vector<std::string> cssNames;
    if (length > 0) {
        for (jsize i = 0; i < length; ++i) {
            jstring jstr = static_cast<jstring>(env->GetObjectArrayElement(css_names, i));
            if (jstr == nullptr) {
                continue;
            }
            const char *str = env->GetStringUTFChars(jstr, nullptr);
            if (str != nullptr) {
                cssNames.push_back(std::string(str));
                env->ReleaseStringUTFChars(jstr, str);
            }
        }
    }

    jsize tagslength = env->GetArrayLength(tag_names);
    std::vector<std::string> tagNames;
    if (tagslength > 0) {
        for (jsize i = 0; i < tagslength; ++i) {
            jstring jstr = static_cast<jstring>(env->GetObjectArrayElement(tag_names, i));
            if (jstr == nullptr) {
                continue;
            }
            const char *str = env->GetStringUTFChars(jstr, nullptr);
            if (str != nullptr) {
                tagNames.push_back(std::string(str));
                env->ReleaseStringUTFChars(jstr, str);
            }
        }
    }

    jsize idslength = env->GetArrayLength(ids_names);
    std::vector<std::string> idNames;
    if (idslength > 0) {
        for (jsize i = 0; i < idslength; ++i) {
            jstring jstr = static_cast<jstring>(env->GetObjectArrayElement(ids_names, i));
            if (jstr == nullptr) {
                continue;
            }
            const char *str = env->GetStringUTFChars(jstr, nullptr);
            if (str != nullptr) {
                idNames.push_back(std::string(str));
                env->ReleaseStringUTFChars(jstr, str);
            }
        }
    }

    if (cssNames.empty() && tagNames.empty() && idNames.empty()) {
        LOGE("%s:failed empty cssNames", __func__);
        return nullptr;
    }

    std::vector<CssInfo> cssInfos;
    int ret = 0;
    if (type == 1) {
        ret = mobiutil->getCss(cssNames, tagNames, idNames, cssInfos);
    } else if (type == 2) {
        ret = epubutil->getCss(cssNames, tagNames, idNames, cssInfos);
    }
    if (ret != 1) {
        LOGE("%s:fail parse css info", __func__);
        return nullptr;
    }

    if (cssInfos.empty()) {
        LOGE("%s cssInfos is empty", __func__);
        return nullptr;
    }

    jclass cssInfoClass = env->FindClass("com/wxn/base/bean/CssInfo");
    jclass ruleDataClass = env->FindClass("com/wxn/base/bean/RuleData");
    if (cssInfoClass == nullptr || ruleDataClass == nullptr || env->ExceptionCheck()) {
        LOGE("%s failed to find class CssInfo or RuleData", __func__);
        return nullptr;
    }

    jmethodID cssInfoConstructor = env->GetMethodID(cssInfoClass, "<init>", "(Ljava/lang/String;IZLjava/util/List;)V");
    if (cssInfoConstructor == nullptr) {
        LOGE("%s cssInfo constructor is null", __func__);
        return nullptr;
    }
    jmethodID ruleDataConstructor = env->GetMethodID(ruleDataClass, "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (ruleDataConstructor == nullptr) {
        LOGE("%s ruleData constructor is null", __func__);
        return nullptr;
    }

    int cssInfoLength = cssInfos.size();
    jobjectArray result = env->NewObjectArray(cssInfoLength, cssInfoClass, nullptr);
    if (result == nullptr) {
        LOGE("%s failed to create result object array", __func__);
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

    for (int i = 0; i < cssInfoLength; i++) {
        auto item = cssInfos[i];

        jobject list = env->NewObject(listClass, listConstructor);
        if (!item.ruleDatas.empty()) {
            for (auto &data: item.ruleDatas) {
                jobject jdata = env->NewObject(ruleDataClass, ruleDataConstructor,
                                               env->NewStringUTF(data.name.c_str()),
                                               env->NewStringUTF(data.value.c_str()));
                env->CallBooleanMethod(list, listAdd, jdata);
            }
        }
        jobject cssInfo = env->NewObject(cssInfoClass, cssInfoConstructor,
                                         env->NewStringUTF(item.identifier.c_str()),
                                         item.weight,
                                         item.isBaseSelector,
                                         list);
        env->SetObjectArrayElement(result, i, cssInfo);
    }

    return result;
}
extern "C"
JNIEXPORT jobject JNICALL
Java_com_wxn_mobi_inative_NativeLib_getWordCount(JNIEnv *env, jobject thiz, jlong bookId, jstring path, jint type) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    create_util(bookId, nativeStr, type);

    int32_t total = 0;

    std::vector<ChapterCount> wordCount;
    if (type == 1) {
        total = mobiutil->getWordCount(wordCount);
    } else if (type== 2) {
        total = epubutil->getWordCount(wordCount);
    } else if (type == 3) {
        total = fb2util->getWordCount(wordCount);
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
    jclass pairClass = env->FindClass("com/wxn/mobi/data/model/CountPair");
    if (pairClass == nullptr || env->ExceptionCheck()) {
        return nullptr;
    }
    jmethodID pairConstructor = env->GetMethodID(pairClass, "<init>", "(III)V");

    jobject jlist = env->NewObject(listClass, listConstructor);
    for (auto &count: wordCount) {
        jobject item = env->NewObject(pairClass, pairConstructor, count.chapterOrder, count.words, count.pics);
        env->CallBooleanMethod(jlist, listAdd, item);
    }
    jobject total_item = env->NewObject(pairClass, pairConstructor, -1, total, 0);
    env->CallBooleanMethod(jlist, listAdd, total_item);

    env->ReleaseStringUTFChars(path, nativeStr);

    return jlist;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_wxn_mobi_inative_NativeLib_closeBook(JNIEnv *env, jobject thiz, jlong book_id, jstring path, jint type) {
    if (type == 1) {
        if (mobiutil != nullptr && mobiutil.use_count() > 0) {
            if (book_id == mobiutil->bookid()) {
                mobiutil = nullptr;
            }
        }
    } else if (type == 2) {
        if (epubutil != nullptr || epubutil.use_count() > 0) {
            if (book_id == epubutil->bookid()) {
                epubutil = nullptr;
            }
        }
    } else if (type == 3) {
        if (fb2util != nullptr || fb2util.use_count() > 0) {
            if (book_id == fb2util->bookid()) {
                fb2util = nullptr;
            }
        }
    }
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_wxn_mobi_inative_NativeLib_loadFb2(JNIEnv *env,
                                            jobject thiz,
                                            jobject context,
                                            jstring path) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    if (app_ext::appFileDir.empty()) {
        jclass contextClass = env->GetObjectClass(context);
        jmethodID getFilesDirMethod = env->GetMethodID(contextClass, "getFilesDir", "()Ljava/io/File;");
        //call getFilesDir(), return File object
        jobject filesDirObj = env->CallObjectMethod(context, getFilesDirMethod);

        //call getAbsolutePath(), get full dir path
        jclass fileClass = env->FindClass("java/io/File");
        jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
        jstring pathStr = (jstring) env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
        const char *appFileDir = env->GetStringUTFChars(pathStr, NULL);
        app_ext::appFileDir = appFileDir;
        env->ReleaseStringUTFChars(pathStr, appFileDir);
    }
    if (app_ext::appCacheDir.empty()) {
        jclass contextClass = env->GetObjectClass(context);
        jmethodID getCacheDirMethod = env->GetMethodID(contextClass, "getCacheDir", "()Ljava/io/File;");
        //call getFilesDir(), return File object
        jobject filesDirObj = env->CallObjectMethod(context, getCacheDirMethod);

        //call getAbsolutePath(), get full dir path
        jclass fileClass = env->FindClass("java/io/File");
        jmethodID getAbsolutePathMethod = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
        jstring pathStr = (jstring) env->CallObjectMethod(filesDirObj, getAbsolutePathMethod);
        const char *appCacheDir = env->GetStringUTFChars(pathStr, NULL);
        app_ext::appCacheDir = appCacheDir;
        env->ReleaseStringUTFChars(pathStr, appCacheDir);
    }


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

    int ret = fb2_util::load_fb2(nativeStr,
                                 coverPath,
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

    env->ReleaseStringUTFChars(path, nativeStr);

    if (ret != 1) {
        return nullptr;
    }

    jclass infoClazz = env->FindClass("com/wxn/mobi/data/model/MetaInfo");
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
extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_wxn_mobi_inative_NativeLib_searchFiles(JNIEnv *env, jobject thiz, jstring root,
                                                jobjectArray patterns) {
    const char *strRoot = env->GetStringUTFChars(root, NULL);

    jsize length = env->GetArrayLength(patterns);
    std::vector<std::string> strPatterns;
    if (length > 0) {
        for (jsize i = 0; i < length; ++i) {
            jstring jstr = static_cast<jstring>(env->GetObjectArrayElement(patterns, i));
            if (jstr == nullptr) {
                continue;
            }
            const char *str = env->GetStringUTFChars(jstr, nullptr);
            if (str != nullptr) {
                strPatterns.push_back(std::string(str));
                env->ReleaseStringUTFChars(jstr, str);
            }
        }
    }

    std::vector<std::string> ss = startSearch(strRoot, strPatterns, 2);

    env->ReleaseStringUTFChars(root, strRoot);

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr)
    {
        return nullptr;
    }
    length = ss.size();
    if (length <= 0) {
        return nullptr;
    }

    jobjectArray strArray = env->NewObjectArray(length, stringClass, nullptr);
    if (strArray == nullptr) {
        env->DeleteLocalRef(stringClass);
        return nullptr;
    }
    for(size_t i=0; i<length; i++) {
        jstring jstr = env->NewStringUTF(ss[i].c_str());
        if (jstr == nullptr) {
            continue;
        }
        env->SetObjectArrayElement(strArray, i, jstr);
        env->DeleteLocalRef(jstr);
    }
    return strArray;
}