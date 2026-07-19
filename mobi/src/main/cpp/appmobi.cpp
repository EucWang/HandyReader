#include <jni.h>
#include <string>
#include "util/string_ext.h"
#include <algorithm>
#include <vector>
#include <string>
#include <sstream>
#include "util/crc_util.h"
#include "util/json_builder.h"
#include "util/log.h"
#include "util/mobi_util.h"
#include "util/app_ext.h"
#include "util/epub_util.h"
#include "util/fb2_util.h"
#include "util/html_util.h"
#include "util/file_searcher.h"
#include "util/chapter_matcher.h"
#include <memory>
#include <mutex>

// 全局互斥锁，保护 util 对象的创建和销毁
static std::mutex g_util_mutex;

// ★ 2026-07-07 方案 A+:同一次 IO 同时算 CRC32 + SHA-256,
//   避免上层 Java ContentHashCalculator 二次全文件读。
//   失败时 crc=0, sha256=""。
struct FileCrcHash {
    uint32_t crc;
    std::string sha256;
};

static FileCrcHash compute_file_crc_and_hash(const std::string& filepath) {
    try {
        std::vector<std::string> paths = {filepath};
        CrcResults results;
        results.process_files_crc(paths, 0, 1);
        auto& res = results.get();
        if (!res.empty()) {
            return {res[0].crc, res[0].sha256};
        }
    } catch (...) {}
    return {0, ""};
}

std::shared_ptr<mobi_util> mobiutil = nullptr;
std::shared_ptr<epub_util> epubutil = nullptr;
std::shared_ptr<fb2_util> fb2util = nullptr;
std::shared_ptr<html_util> htmlutil = nullptr;

void create_mobi_util(long book_id, const char *path) {
    // 使用锁保护全局变量的访问
    std::lock_guard<std::mutex> lock(g_util_mutex);
    
    if (mobiutil == nullptr) {
        long bookid = book_id;
        std::string bookpath = path;
        mobiutil = std::shared_ptr<mobi_util>(new mobi_util(bookid, bookpath));
        LOGD("%s: created new mobiutil for book_id=%ld", __func__, book_id);
    } else {
        if (mobiutil->bookid() != book_id || mobiutil->bookpath() != path) {
            LOGD("%s: resetting mobiutil for new book_id=%ld (old=%ld)", 
                 __func__, book_id, mobiutil->bookid());
            mobiutil.reset(new mobi_util(book_id, path));
        }
    }
}

void create_epub_util(long book_id, const char *path) {
    std::lock_guard<std::mutex> lock(g_util_mutex);
    
    if (epubutil == nullptr) {
        long bookid = book_id;
        std::string bookpath = path;
        epubutil = std::shared_ptr<epub_util>(new epub_util(bookid, bookpath));
        LOGD("%s: created new epubutil for book_id=%ld", __func__, book_id);
    } else {
        if (epubutil->bookid() != book_id || epubutil->bookpath() != path) {
            LOGD("%s: resetting epubutil for new book_id=%ld (old=%ld)", 
                 __func__, book_id, epubutil->bookid());
            epubutil.reset(new epub_util(book_id, path));
        }
    }
}

void create_fb2_util(long book_id, const char *path) {
    std::lock_guard<std::mutex> lock(g_util_mutex);
    
    if (fb2util == nullptr) {
        long bookid = book_id;
        std::string bookpath = path;
        fb2util = std::shared_ptr<fb2_util>(new fb2_util(bookid, bookpath));
        LOGD("%s: created new fb2util for book_id=%ld", __func__, book_id);
    } else {
        if (fb2util->bookid() != book_id || fb2util->bookpath() != path) {
            LOGD("%s: resetting fb2util for new book_id=%ld (old=%ld)", 
                 __func__, book_id, fb2util->bookid());
            fb2util.reset(new fb2_util(book_id, path));
        }
    }
}

void create_html_util(long book_id, const char *path) {
    std::lock_guard<std::mutex> lock(g_util_mutex);

    if (htmlutil == nullptr) {
        long bookid = book_id;
        std::string bookpath = path;
        htmlutil = std::shared_ptr<html_util>(new html_util(bookid, bookpath));
        LOGD("%s: created new htmlutil for book_id=%ld", __func__, book_id);
    } else {
        if (htmlutil->bookid() != book_id || htmlutil->bookpath() != path) {
            LOGD("%s: resetting htmlutil for new book_id=%ld (old=%ld)",
                 __func__, book_id, htmlutil->bookid());
            htmlutil.reset(new html_util(book_id, path));
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
    } else if (type == 4) {
        create_html_util(book_id, path);
    } else {
        LOGE("%s unknown type[%d]", __func__, type);
        return 0;
    }
    return 1;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wxn_mobi_inative_NativeLib_loadMobiNative(
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

    if (ret != SUCCESS) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    FileCrcHash crcHash = compute_file_crc_and_hash(nativeStr);
    env->ReleaseStringUTFChars(path, nativeStr);
    std::string json = json_builder::build_meta_info(
        title, author, contributor, subject, publisher, date,
        description, review, imprint, copyright, isbn, asin,
        language, isEncrypted, coverPath, crcHash.crc, crcHash.sha256);
    return env->NewStringUTF(json.c_str());
}



extern "C" JNIEXPORT jstring JNICALL
Java_com_wxn_mobi_inative_NativeLib_loadEpubNative(
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

    if (ret != 1) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    FileCrcHash crcHash = compute_file_crc_and_hash(nativeStr);
    env->ReleaseStringUTFChars(path, nativeStr);
    std::string json = json_builder::build_meta_info(
        title, author, contributor, subject, publisher, date,
        description, review, imprint, copyright, isbn, asin,
        language, isEncrypted, coverPath, crcHash.crc, crcHash.sha256);
    return env->NewStringUTF(json.c_str());
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
Java_com_wxn_mobi_inative_NativeLib_getChaptersNative(JNIEnv *env, jobject thiz, jobject context, jlong book_id, jstring path, jint type) {
    LOGI("%s:bookload: book_id=%lld,type=%d", __func__, book_id, type);
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    if (create_util(book_id, nativeStr, type) != 1) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }

    std::vector<NavPoint> vectors;
    int ret = 0;

    std::shared_ptr<mobi_util> local_mobiutil;
    std::shared_ptr<epub_util> local_epubutil;
    std::shared_ptr<fb2_util> local_fb2util;
    std::shared_ptr<html_util> local_html_util;

    {
        std::lock_guard<std::mutex> lock(g_util_mutex);
        if (type == 1 && mobiutil) {
            local_mobiutil = mobiutil;
        } else if (type == 2 && epubutil) {
            local_epubutil = epubutil;
        } else if (type == 3 && fb2util) {
            local_fb2util = fb2util;
        } else if (type == 4 && htmlutil) {
            local_html_util = htmlutil;
        }
    }

    if ((type == 1 && !local_mobiutil) ||
        (type == 2 && !local_epubutil) ||
        (type == 3 && !local_fb2util) ||
        (type == 4 && !local_html_util)) {
        LOGE("%s: util object is null for type=%d", __func__, type);
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }

    if (type == 1) {
        ret = local_mobiutil->getChapters(vectors);
    } else if (type == 2) {
        ret = local_epubutil->getChapters(vectors);
    } else if (type == 3) {
        ret = local_fb2util->getChapters(vectors);
    } else if (type == 4) {
        ret = local_html_util->getChapters(vectors);
    } else {
        LOGE("%s unknown type[%d]", __func__, type);
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    if (ret != 1) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    jclass objClass = env->FindClass("com/wxn/base/bean/BookChapter");
    if (objClass == nullptr || env->ExceptionCheck()) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    int length = vectors.size();
    if (length <= 0) {
        LOGE("%s failed, vectors is empty", __func__);
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }

    jobjectArray result = env->NewObjectArray(length, objClass, nullptr);
    if (result == nullptr) {
        LOGE("%s failed, jArray is null", __func__);
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }

    jmethodID constructor = env->GetMethodID(objClass, "<init>",
                                             "(JLjava/lang/String;Ljava/lang/String;JILjava/lang/String;JLjava/lang/String;JLjava/lang/String;Ljava/lang/String;IJJJFII)V");
    if (constructor == nullptr) {
        LOGE("%s failed, BookChapter's constructor is null", __func__);
        env->ReleaseStringUTFChars(path, nativeStr);
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

        // 创建临时 jstring 变量
        jstring j_id = env->NewStringUTF(id.c_str());
        jstring j_parentId = env->NewStringUTF(parentId.c_str());
        jstring j_content = env->NewStringUTF(content.c_str());
        jstring j_src = env->NewStringUTF(src.c_str());
        jstring j_empty1 = env->NewStringUTF("");
        jstring j_empty2 = env->NewStringUTF("");
        // 检查空字符串
        if (j_id == nullptr || j_parentId == nullptr || j_content == nullptr ||
            j_src == nullptr || j_empty1 == nullptr || j_empty2 == nullptr) {
            LOGE("%s 创建字符串失败", __func__);
            // 清理已创建的字符串
            if (j_id) env->DeleteLocalRef(j_id);
            if (j_parentId) env->DeleteLocalRef(j_parentId);
            if (j_content) env->DeleteLocalRef(j_content);
            if (j_src) env->DeleteLocalRef(j_src);
            if (j_empty1) env->DeleteLocalRef(j_empty1);
            if (j_empty2) env->DeleteLocalRef(j_empty2);
            env->ReleaseStringUTFChars(path, nativeStr);
            return nullptr;
        }

        jvalue args[18];
        args[0].j = 0LL;
        args[1].l = j_id;
        args[2].l = j_parentId;
        args[3].j = book_id;
        args[4].i = playOrder - 1;
        args[5].l = j_content;
        args[6].j = 0LL;
        args[7].l = j_empty1;
        args[8].j = 0LL;
        args[9].l = j_empty2;
        args[10].l = j_src;
        args[11].i = length;
        args[12].j = 0LL;
        args[13].j = 0LL;
        args[14].j = 0LL;
        args[15].f = 0.0f;  // 直接使用 float，不会提升
        args[16].i = (*point).type;      // type
        args[17].i = (*point).splitSeq;  // splitSeq
        // 检查每个 NewStringUTF 返回值
//        for (int i = 1; i <= 10; i += 2) { // 检查所有 l 字段
//            if (args[i].l == nullptr) {
//                LOGE("%s Failed to create string at index %d", __func__, i);
//                // 清理已创建的字符串并返回
//                // 注意：需要释放之前创建的局部引用，但简单返回可能泄漏少量内存，建议用 PushLocalFrame
//                env->ReleaseStringUTFChars(path, nativeStr);
//                return nullptr;
//            }
//        }
        jobject item = env->NewObjectA(objClass, constructor, args);

        if (item == nullptr) {
            LOGE("%s create BookChapter failed", __func__);
            // 处理异常
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
            }
            env->DeleteLocalRef(j_id); // 清理字符串
            env->DeleteLocalRef(j_parentId);
            env->DeleteLocalRef(j_content);
            env->DeleteLocalRef(j_src);
            env->DeleteLocalRef(j_empty1);
            env->DeleteLocalRef(j_empty2);
            env->ReleaseStringUTFChars(path, nativeStr);
            return nullptr;
        }
        env->SetObjectArrayElement(result, index, item);

        //删除本地引用
        env->DeleteLocalRef(item);
        env->DeleteLocalRef(j_id); // 清理字符串
        env->DeleteLocalRef(j_parentId);
        env->DeleteLocalRef(j_content);
        env->DeleteLocalRef(j_src);
        env->DeleteLocalRef(j_empty1);
        env->DeleteLocalRef(j_empty2);

        index++;
    }

    env->ReleaseStringUTFChars(path, nativeStr);

    LOGD("%s:bookload: done", __func__ );
    return result;
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_wxn_mobi_inative_NativeLib_getChapterNative(JNIEnv *env, jobject thiz, jobject context, jstring path, jobject chapter, jint type) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);
    if (nativeStr == nullptr) {
        // path 字符串获取失败（OOM），尚未获取其他资源，直接返回
        return nullptr;
    }

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
    jfieldID fieldType = env->GetFieldID(chapterClass, "type", "I");
    jfieldID fieldSplitSeq = env->GetFieldID(chapterClass, "splitSeq", "I");

    jstring chapterId = (jstring) env->GetObjectField(chapter, fieldChapterId);
    jstring parentChapterId = (jstring) env->GetObjectField(chapter, fieldParentChapterId);
    jlong bookId = env->GetLongField(chapter, fieldBookId);
    jint chapterIndex = env->GetIntField(chapter, fieldChapterIndex);
    jstring chapterName = (jstring) env->GetObjectField(chapter, fieldChapterName);
    jstring src = (jstring) env->GetObjectField(chapter, fieldSrc);
    jint chapterSize = env->GetIntField(chapter, fieldChapterSize);
    jint typeVal = env->GetIntField(chapter, fieldType);
    jint splitSeqVal = env->GetIntField(chapter, fieldSplitSeq);

    const char *chapterIdStr = env->GetStringUTFChars(chapterId, nullptr);
    if (chapterIdStr == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    const char *parentChapterIdStr = env->GetStringUTFChars(parentChapterId, nullptr);
    if (parentChapterIdStr == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        return nullptr;
    }
    const char *chapterNameStr = env->GetStringUTFChars(chapterName, nullptr);
    if (chapterNameStr == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        return nullptr;
    }
    const char *srcStr = env->GetStringUTFChars(src, nullptr);
    if (srcStr == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        return nullptr;
    }

    NavPoint point;
    point.id = chapterIdStr;
    point.text = chapterNameStr;
    point.playOrder = chapterIndex + 1;
    point.src = srcStr;
    point.parentId = parentChapterIdStr;
    point.type = typeVal;
    point.splitSeq = splitSeqVal;
    long book_id = bookId;
    int chapter_size = chapterSize;
    LOGD("%s:chapterId=%s,text=%s,playOrder=%d,src=%s,book_id=%lld,chapter_size=%d", __func__, chapterIdStr, chapterNameStr, point.playOrder, srcStr, bookId,
         chapter_size);

    if (create_util(book_id, nativeStr, type) != 1) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }

    // 创建局部 shared_ptr 副本，防止在 getChapter 执行期间对象被 reset
    std::shared_ptr<mobi_util> local_mobiutil;
    std::shared_ptr<epub_util> local_epubutil;
    std::shared_ptr<fb2_util> local_fb2util;
    std::shared_ptr<html_util> local_html_util;

    {
        std::lock_guard<std::mutex> lock(g_util_mutex);
        if (type == 1 && mobiutil) {
            local_mobiutil = mobiutil;
        } else if (type == 2 && epubutil) {
            local_epubutil = epubutil;
        } else if (type == 3 && fb2util) {
            local_fb2util = fb2util;
        } else if (type == 4 && htmlutil) {
            local_html_util = htmlutil;
        }
    }
    
    if ((type == 1 && !local_mobiutil) ||
        (type == 2 && !local_epubutil) ||
        (type == 3 && !local_fb2util) ||
        (type == 4 && !local_html_util)) {
        LOGE("%s: util object is null for type=%d", __func__, type);
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }

    std::vector<DocText> docTexts;
    int ret = 0;
    if (type == 1) {
        ret = local_mobiutil->getChapter(env, book_id, nativeStr, point, docTexts);
    } else if (type == 2) {
        ret = local_epubutil->getChapter(env, book_id, nativeStr, point, docTexts);
    } else if (type == 3) {
        ret = local_fb2util->getChapter(env, book_id, nativeStr, point, docTexts);
    } else if (type == 4) {
        ret = local_html_util->getChapter(env, book_id, nativeStr, point, docTexts);
    }
    if (ret != 1) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }

    if (docTexts.empty()) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }

    jclass objClass = env->FindClass("com/wxn/mobi/data/model/ParagraphData");
    if (objClass == nullptr || env->ExceptionCheck()) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }

    int length = docTexts.size();
    jobjectArray result = env->NewObjectArray(length, objClass, nullptr);
    if (result == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }
    jmethodID constructor = env->GetMethodID(objClass, "<init>", "([BLjava/util/List;)V");
    if (constructor == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }

    jclass listClass = env->FindClass("java/util/ArrayList");
    if (listClass == nullptr || env->ExceptionCheck()) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
    if (listConstructor == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }
    jmethodID listAdd = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    if (listAdd == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }

    jclass textTagClass = env->FindClass("com/wxn/base/bean/TextTag");
    if (textTagClass == nullptr || env->ExceptionCheck()) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }
    jmethodID textTagConstructor = env->GetMethodID(textTagClass, "<init>",
                                                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IILjava/lang/String;Ljava/lang/String;)V");
    if (textTagConstructor == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        env->ReleaseStringUTFChars(chapterId, chapterIdStr);
        env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
        env->ReleaseStringUTFChars(chapterName, chapterNameStr);
        env->ReleaseStringUTFChars(src, srcStr);
        return nullptr;
    }

    for (int i = 0; i < length; i++) {
        auto item = docTexts[i];
        jobject list = env->NewObject(listClass, listConstructor);
        if (list == nullptr) {
            LOGE("%s: Failed to create ArrayList", __func__);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
            }
            env->ReleaseStringUTFChars(path, nativeStr);
            env->ReleaseStringUTFChars(chapterId, chapterIdStr);
            env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
            env->ReleaseStringUTFChars(chapterName, chapterNameStr);
            env->ReleaseStringUTFChars(src, srcStr);
            return nullptr;
        }
        if (!item.tagInfos.empty()) {
            for (auto &tag: item.tagInfos) {
                // 创建临时字符串（均为局部引用）
                jstring uuid = env->NewStringUTF(tag.uuid.c_str());
                jstring anchor_id = env->NewStringUTF(tag.anchor_id.c_str());
                jstring name = env->NewStringUTF(tag.name.c_str());
                jstring parent_uuid = env->NewStringUTF(tag.parent_uuid.c_str());
                jstring params = env->NewStringUTF(tag.params.c_str());

                // NewStringUTF 返回值检查（防御性：任何 jstring 为 null 则放弃当前段落）
                if (uuid == nullptr || anchor_id == nullptr || name == nullptr ||
                    parent_uuid == nullptr || params == nullptr) {
                    LOGE("%s: Failed to create jstring for TextTag", __func__);
                    if (uuid) env->DeleteLocalRef(uuid);
                    if (anchor_id) env->DeleteLocalRef(anchor_id);
                    if (name) env->DeleteLocalRef(name);
                    if (parent_uuid) env->DeleteLocalRef(parent_uuid);
                    if (params) env->DeleteLocalRef(params);
                    env->DeleteLocalRef(list);
                    env->ReleaseStringUTFChars(path, nativeStr);
                    env->ReleaseStringUTFChars(chapterId, chapterIdStr);
                    env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
                    env->ReleaseStringUTFChars(chapterName, chapterNameStr);
                    env->ReleaseStringUTFChars(src, srcStr);
                    return nullptr;
                }

                // 创建 TextTag 对象
                jobject textTag = env->NewObject(textTagClass, textTagConstructor,
                                                 uuid, anchor_id, name,
                                                 tag.startPos, tag.endPos,
                                                 parent_uuid, params);
                if (textTag) {
                    // 添加到 ArrayList
                    env->CallBooleanMethod(list, listAdd, textTag);
                    // 立即删除 TextTag 局部引用（ArrayList 已持有它）
                    env->DeleteLocalRef(textTag);
                }
                // 删除临时字符串局部引用（TextTag 构造函数已复制或持有引用）
                env->DeleteLocalRef(uuid);
                env->DeleteLocalRef(anchor_id);
                env->DeleteLocalRef(name);
                env->DeleteLocalRef(parent_uuid);
                env->DeleteLocalRef(params);
            }
        }

        const char* ch_text = item.text.c_str();
        size_t textLen = item.text.length();

        jbyteArray byteArray = env->NewByteArray(textLen);
        if (byteArray == nullptr) {
            LOGE("%s: Failed to create byteArray at index %d", __func__, i);
            env->DeleteLocalRef(list);
            env->ReleaseStringUTFChars(path, nativeStr);
            env->ReleaseStringUTFChars(chapterId, chapterIdStr);
            env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
            env->ReleaseStringUTFChars(chapterName, chapterNameStr);
            env->ReleaseStringUTFChars(src, srcStr);
            return nullptr;
        }
        jbyte *bytes = env->GetByteArrayElements(byteArray, nullptr);
        if (bytes == nullptr) {
            LOGE("%s: Failed to get byteArray elements at index %d", __func__, i);
            env->DeleteLocalRef(byteArray);
            env->DeleteLocalRef(list);
            env->ReleaseStringUTFChars(path, nativeStr);
            env->ReleaseStringUTFChars(chapterId, chapterIdStr);
            env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
            env->ReleaseStringUTFChars(chapterName, chapterNameStr);
            env->ReleaseStringUTFChars(src, srcStr);
            return nullptr;
        }
        memcpy(bytes, ch_text, textLen);
        env->ReleaseByteArrayElements(byteArray, bytes, 0);
        jobject paragraph_data = env->NewObject(objClass, constructor, byteArray, list);
        if (paragraph_data == nullptr) {
            LOGE("%s: Failed to create ParagraphData at index %d", __func__, i);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
            }
            env->DeleteLocalRef(byteArray);
            env->DeleteLocalRef(list);
            env->ReleaseStringUTFChars(path, nativeStr);
            env->ReleaseStringUTFChars(chapterId, chapterIdStr);
            env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
            env->ReleaseStringUTFChars(chapterName, chapterNameStr);
            env->ReleaseStringUTFChars(src, srcStr);
            return nullptr;
        }

        env->SetObjectArrayElement(result, i, paragraph_data);

        env->DeleteLocalRef(byteArray);
        env->DeleteLocalRef(list);
        env->DeleteLocalRef(paragraph_data);
    }

    env->ReleaseStringUTFChars(path, nativeStr);

    env->ReleaseStringUTFChars(chapterId, chapterIdStr);
    env->ReleaseStringUTFChars(parentChapterId, parentChapterIdStr);
    env->ReleaseStringUTFChars(chapterName, chapterNameStr);
    env->ReleaseStringUTFChars(src, srcStr);

    return result;
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_wxn_mobi_inative_NativeLib_getWordCountNative(JNIEnv *env, jobject thiz, jlong bookId, jstring path, jint type) {
    const char *nativeStr = env->GetStringUTFChars(path, NULL);

    create_util(bookId, nativeStr, type);

    int32_t total = 0;

    std::vector<ChapterCount> wordCount;

    std::shared_ptr<mobi_util> local_mobiutil;
    std::shared_ptr<epub_util> local_epubutil;
    std::shared_ptr<fb2_util> local_fb2util;
    std::shared_ptr<html_util> local_html_util;

    {
        std::lock_guard<std::mutex> lock(g_util_mutex);
        if (type == 1 && mobiutil) {
            local_mobiutil = mobiutil;
        } else if (type == 2 && epubutil) {
            local_epubutil = epubutil;
        } else if (type == 3 && fb2util) {
            local_fb2util = fb2util;
        } else if (type == 4 && htmlutil) {
            local_html_util = htmlutil;
        }
    }

    if ((type == 1 && !local_mobiutil) ||
        (type == 2 && !local_epubutil) ||
        (type == 3 && !local_fb2util) ||
        (type == 4 && !local_html_util)) {
        LOGE("%s: util object is null for type=%d", __func__, type);
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }

    if (type == 1) {
        total = local_mobiutil->getWordCount(wordCount);
    } else if (type == 2) {
        total = local_epubutil->getWordCount(wordCount);
    } else if (type == 3) {
        total = local_fb2util->getWordCount(wordCount);
    } else if (type == 4) {
        total = local_html_util->getWordCount(wordCount);
    }

    jclass listClass = env->FindClass("java/util/ArrayList");
    if (listClass == nullptr || env->ExceptionCheck()) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    jmethodID listConstructor = env->GetMethodID(listClass, "<init>", "()V");
    if (listConstructor == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    jmethodID listAdd = env->GetMethodID(listClass, "add", "(Ljava/lang/Object;)Z");
    if (listAdd == nullptr) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    jclass pairClass = env->FindClass("com/wxn/mobi/data/model/CountPair");
    if (pairClass == nullptr || env->ExceptionCheck()) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    jmethodID pairConstructor = env->GetMethodID(pairClass, "<init>", "(III)V");

    jobject jlist = env->NewObject(listClass, listConstructor);
    if (jlist == nullptr) {
        LOGE("%s: Failed to create ArrayList in getWordCount", __func__);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        // ★ 关键：返回空 ArrayList 而非 nullptr，避免 Kotlin 非空契约崩溃
        // （NativeLib.getWordCount 声明为非空 List<CountPair>，nullptr 会导致解引用 NPE）
        jlist = env->NewObject(listClass, listConstructor);
        if (jlist == nullptr) {
            // 极端 OOM，二次创建仍失败，最后兜底返回 nullptr
            // 此时 Kotlin 侧 NPE 风险存在，但属"系统级无内存"不可恢复场景
            LOGE("%s: Retry create ArrayList failed, returning nullptr", __func__);
            env->ReleaseStringUTFChars(path, nativeStr);
            return nullptr;
        }
        // 重试成功：jlist 为合法空 ArrayList，nativeStr 留待函数末尾统一释放（勿在此重复释放）
    }
    for (auto &count: wordCount) {
        jobject item = env->NewObject(pairClass, pairConstructor, count.chapterOrder, count.words, count.pics);
        if (item) {
            env->CallBooleanMethod(jlist, listAdd, item);
            env->DeleteLocalRef(item);
        }
    }
    jobject total_item = env->NewObject(pairClass, pairConstructor, -1, total, 0);
    if (total_item != nullptr) {
        env->CallBooleanMethod(jlist, listAdd, total_item);
        env->DeleteLocalRef(total_item);
    } else if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    env->ReleaseStringUTFChars(path, nativeStr);

    return jlist;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_wxn_mobi_inative_NativeLib_closeBookNative(JNIEnv *env, jobject thiz, jlong book_id, jstring path, jint type) {
    if (type == 1) {
        std::lock_guard<std::mutex> lock(g_util_mutex);
        if (mobiutil && book_id == mobiutil->bookid()) {
            mobiutil = nullptr;
        }
    } else if (type == 2) {
        std::lock_guard<std::mutex> lock(g_util_mutex);
        if (epubutil && book_id == epubutil->bookid()) {
            epubutil = nullptr;
        }
    } else if (type == 3) {
        std::lock_guard<std::mutex> lock(g_util_mutex);
        if (fb2util && book_id == fb2util->bookid()) {
            fb2util = nullptr;
        }
    } else if (type == 4) {
        std::lock_guard<std::mutex> lock(g_util_mutex);
        if (htmlutil && book_id == htmlutil->bookid()) {
            htmlutil = nullptr;
        }
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_wxn_mobi_inative_NativeLib_loadFb2Native(JNIEnv *env,
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

    if (ret != 1) {
        env->ReleaseStringUTFChars(path, nativeStr);
        return nullptr;
    }
    FileCrcHash crcHash = compute_file_crc_and_hash(nativeStr);
    env->ReleaseStringUTFChars(path, nativeStr);
    std::string json = json_builder::build_meta_info(
        title, author, contributor, subject, publisher, date,
        description, review, imprint, copyright, isbn, asin,
        language, isEncrypted, coverPath, crcHash.crc, crcHash.sha256);
    LOGD("%s:json=%s\n",__func__ , json.c_str());
    return env->NewStringUTF(json.c_str());
}

extern "C"
JNIEXPORT jobjectArray JNICALL
Java_com_wxn_mobi_inative_NativeLib_searchFilesNative(JNIEnv *env, jobject thiz, jstring root,
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
            env->DeleteLocalRef(jstr);  // 添加此行
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

// 章节标题匹配 JNI 入口
// 返回码：0=不匹配 / 1=匹配（章/节等） / 2=匹配且含"卷"（供 Kotlin dedup 章优先于卷判断）
extern "C"
JNIEXPORT jint JNICALL
Java_com_wxn_mobi_inative_NativeLib_nativeMatchChapterTitleNative(JNIEnv *env, jobject thiz, jstring title) {
    if (title == nullptr) return 0;
    const char *nativeStr = env->GetStringUTFChars(title, NULL);
    if (nativeStr == nullptr) return 0;
    std::string s(nativeStr);
    env->ReleaseStringUTFChars(title, nativeStr);

    if (chapter_matcher::matchChapterRe(s)) {
        // 中文"第X章/节/卷"：检查是否含"卷"(U+537B = E5 8D B7)，供 dedup 区分章/卷优先级
        return s.find(u8"\xe5\x8d\xb7") != std::string::npos ? 2 : 1;
    }
    if (chapter_matcher::matchSpecialRe(s)) return 1;
    if (chapter_matcher::matchEnRe(s)) return 1;
    if (chapter_matcher::matchEnSpecialRe(s)) return 1;
    if (chapter_matcher::matchJpRe(s)) return 1;
    if (chapter_matcher::matchKrRe(s)) return 1;
    if (chapter_matcher::matchLatinRe(s)) return 1;
    if (chapter_matcher::matchRussianRe(s)) return 1;
    if (chapter_matcher::matchHindiRe(s)) return 1;
    if (chapter_matcher::matchArabicRe(s)) return 1;
    return 0;
}
